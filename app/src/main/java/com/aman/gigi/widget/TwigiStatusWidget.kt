package com.aman.gigi.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aman.gigi.model.Connection
import com.aman.gigi.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Calendar

/**
 * Your person, on your home screen.
 *
 * Shows their Twigi with a line about what they're up to. Twigi avatars are rendered
 * server-side (/twigi/preview), so this only has to fetch a URL — but Glance cannot
 * load remote images itself, so the bitmap is downloaded here and handed over directly.
 *
 * Note on the spec: Android phones have had no lockscreen widgets since API 21, and
 * Android 14 restored them only for tablets. The lockscreen half of this idea is served
 * by an ongoing notification instead, not by a widget.
 */
class TwigiStatusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val connection = loadConnection(context)
        val status = statusFor(connection)
        val avatar = connection?.partnerTwigiUrl?.let { fetchBitmap(it, status.animPose) }
        provideContent { Content(connection, avatar, status) }
    }

    /**
     * A widget is instantiated by the framework, not by Hilt, so the DAO has to be
     * pulled from the graph explicitly rather than injected.
     */
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface WidgetEntryPoint {
        fun connectionDao(): com.aman.gigi.db.ConnectionDao
    }

    private suspend fun loadConnection(context: Context): Connection? = withContext(Dispatchers.IO) {
        runCatching {
            dagger.hilt.android.EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .connectionDao()
                .getActiveConnectionsOnce()
                .firstOrNull { connection -> !connection.isGroup }
        }.onFailure { Log.w(TAG, "connection lookup failed: ${it.message}") }.getOrNull()
    }

    private suspend fun fetchBitmap(url: String, animPose: String? = null): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val fullUrl = if (!animPose.isNullOrBlank() && url.contains("/twigi/preview")) {
                if (url.contains("anim=")) url.replace(Regex("anim=[a-z]+"), "anim=$animPose")
                else "$url&anim=$animPose"
            } else url
            URL(fullUrl).openStream().use { BitmapFactory.decodeStream(it) }
        }.onFailure { Log.w(TAG, "twigi fetch failed: ${it.message}") }.getOrNull()
    }

    @Composable
    private fun Content(connection: Connection?, avatar: Bitmap?, status: Status = statusFor(connection)) {
        val context = androidx.glance.LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
                .cornerRadius(24.dp)
                .background(ColorProvider(Color(0xFFFDECEF)))
                .clickable(
                    actionStartActivity(
                        android.content.Intent(context, MainActivity::class.java)
                    )
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (avatar != null) {
                Image(
                    provider = ImageProvider(avatar),
                    contentDescription = connection?.partnerName ?: "Partner",
                    modifier = GlanceModifier.size(64.dp)
                )
            } else {
                Text(status.emoji, style = TextStyle(fontSize = 34.sp()))
            }

            Spacer(GlanceModifier.height(6.dp))

            Text(
                text = connection?.partnerName?.takeIf { it.isNotBlank() } ?: "No one yet",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp(),
                    color = ColorProvider(Color(0xFF4C1D95)),
                    textAlign = TextAlign.Center
                )
            )
            Text(
                text = status.label,
                style = TextStyle(
                    fontSize = 11.sp(),
                    color = ColorProvider(Color(0xFF7C6BA8)),
                    textAlign = TextAlign.Center
                )
            )
        }
    }

    private data class Status(val emoji: String, val label: String, val animPose: String = "idle")

    /**
     * Infer partner's current activity pose from connection state.
     */
    private fun statusFor(connection: Connection?): Status {
        if (connection == null) return Status("🌱", "Connect someone to see them here", "idle")

        val online = connection.partnerPresence.equals("ONLINE", ignoreCase = true)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val quietHours = hour >= 23 || hour < 6

        return when {
            online && quietHours -> Status("🌙", "Up late", "idle")
            online -> Status("✨", "Around right now", "idle")
            quietHours -> Status("😴", "Probably asleep", "sit")
            else -> Status("💭", lastSeenLabel(connection.lastSeenAt), "idle")
        }
    }

    private fun lastSeenLabel(lastSeenAt: Long?): String {
        val seen = lastSeenAt ?: return "Away"
        val minutes = (System.currentTimeMillis() - seen) / 60_000
        return when {
            minutes < 2 -> "Just now"
            minutes < 60 -> "Seen ${minutes}m ago"
            minutes < 60 * 24 -> "Seen ${minutes / 60}h ago"
            else -> "Away a while"
        }
    }

    companion object {
        private const val TAG = "TwigiStatusWidget"

        /**
         * Repaints every placed copy. Call when partner presence changes — from the FCM
         * handler or the socket — since a widget cannot observe a Flow on its own.
         */
        suspend fun refresh(context: Context) {
            runCatching { TwigiStatusWidget().updateAll(context) }
                .onFailure { Log.w(TAG, "widget refresh failed: ${it.message}") }
        }
    }
}

/** Glance text sizes are TextUnit; this keeps the call sites readable. */
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp
)

class TwigiStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TwigiStatusWidget()
}
