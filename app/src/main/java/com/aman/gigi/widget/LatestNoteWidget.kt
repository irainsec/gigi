package com.aman.gigi.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aman.gigi.model.Connection
import com.aman.gigi.model.Scribble
import com.aman.gigi.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Interactive Home Screen Scribble & Note Widget (Locket / noteit style for Gigi).
 *
 * Displays your partner's live real-time doodles, drawings, photos, and secret notes
 * directly on your home screen with 1-tap quick reply.
 */
class LatestNoteWidget : GlanceAppWidget() {

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface WidgetEntryPoint {
        fun connectionDao(): com.aman.gigi.db.ConnectionDao
        fun scribbleDao(): com.aman.gigi.db.ScribbleDao
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = runCatching {
            dagger.hilt.android.EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        }.getOrNull()

        val connection = loadConnection(entryPoint)
        val scribble = loadLatestScribble(entryPoint, connection?.connectionId)
        val partnerAvatar = connection?.partnerTwigiUrl?.let { fetchBitmap(it) }
        val scribbleBitmap = withContext(Dispatchers.Default) {
            ScribbleBitmapRenderer.render(scribble, width = 480, height = 480)
        }

        provideContent {
            WidgetContent(context, connection, scribble, partnerAvatar, scribbleBitmap)
        }
    }

    private suspend fun loadConnection(entryPoint: WidgetEntryPoint?): Connection? = withContext(Dispatchers.IO) {
        runCatching {
            entryPoint?.connectionDao()?.getActiveConnectionsOnce()?.firstOrNull { !it.isGroup }
        }.onFailure { Log.w(TAG, "Connection lookup failed: ${it.message}") }.getOrNull()
    }

    private suspend fun loadLatestScribble(entryPoint: WidgetEntryPoint?, connectionId: String?): Scribble? = withContext(Dispatchers.IO) {
        runCatching {
            if (!connectionId.isNullOrBlank()) {
                entryPoint?.scribbleDao()?.getLatestScribbleForConnection(connectionId)
            } else {
                entryPoint?.scribbleDao()?.getLatestScribbleOnce()
            }
        }.onFailure { Log.w(TAG, "Scribble lookup failed: ${it.message}") }.getOrNull()
    }

    private suspend fun fetchBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            URL(url).openStream().use { BitmapFactory.decodeStream(it) }
        }.onFailure { Log.w(TAG, "Avatar fetch failed: ${it.message}") }.getOrNull()
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        connection: Connection?,
        scribble: Scribble?,
        avatar: Bitmap?,
        scribbleBitmap: Bitmap?
    ) {
        val hasContent = scribble != null && (scribble.strokes.isNotEmpty() || !scribble.mediaBase64.isNullOrBlank())
        val isSentByMe = scribble?.isSent == true
        val partnerName = connection?.partnerName?.takeIf { it.isNotBlank() } ?: "Your Love"
        val timeAgo = scribble?.let { formatTimeAgo(it.createdAt) } ?: "Waiting for doodle"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(10.dp)
                .cornerRadius(22.dp)
                .background(ColorProvider(Color(0xFF140A26))) // Deep cosmic night violet
                .clickable(actionStartActivity(openAppIntent)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header: Partner Info & Time ──
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (avatar != null) {
                    Image(
                        provider = ImageProvider(avatar),
                        contentDescription = partnerName,
                        modifier = GlanceModifier.size(24.dp).cornerRadius(12.dp)
                    )
                } else {
                    Text(
                        text = "💖",
                        style = TextStyle(fontSize = 14.sp())
                    )
                }

                Spacer(GlanceModifier.width(6.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = if (isSentByMe) "You ✨" else partnerName,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp(),
                            color = ColorProvider(Color(0xFFFDF4FF))
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = timeAgo,
                        style = TextStyle(
                            fontSize = 10.sp(),
                            color = ColorProvider(Color(0xFFC084FC))
                        ),
                        maxLines = 1
                    )
                }

                Text(
                    text = "🎨 Quick Draw",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp(),
                        color = ColorProvider(Color(0xFFF472B6))
                    )
                )
            }

            Spacer(GlanceModifier.height(6.dp))

            // ── Canvas: Live Scribble / Photo Artwork ──
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .cornerRadius(16.dp)
                    .background(ColorProvider(Color(0xFF22123B))),
                contentAlignment = Alignment.Center
            ) {
                if (hasContent && scribbleBitmap != null) {
                    Image(
                        provider = ImageProvider(scribbleBitmap),
                        contentDescription = "Latest Scribble",
                        modifier = GlanceModifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = GlanceModifier.padding(12.dp)
                    ) {
                        Text(
                            text = "✨ 🎨 ✨",
                            style = TextStyle(fontSize = 24.sp())
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = "Tap to draw a sweet scribble for $partnerName",
                            style = TextStyle(
                                fontSize = 11.sp(),
                                color = ColorProvider(Color(0xFFE9D5FF)),
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }

            // ── Optional Secret Message / Note Footer ──
            val note = scribble?.secretMessage?.takeIf { it.isNotBlank() }
            if (note != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "💬 “$note”",
                    style = TextStyle(
                        fontSize = 11.sp(),
                        color = ColorProvider(Color(0xFFFDE047)),
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 60 * 24 -> "${minutes / 60}h ago"
            else -> "${minutes / (60 * 24)}d ago"
        }
    }

    companion object {
        private const val TAG = "LatestNoteWidget"

        suspend fun refresh(context: Context) {
            runCatching {
                LatestNoteWidget().updateAll(context)
            }.onFailure { Log.w(TAG, "LatestNoteWidget refresh failed: ${it.message}") }
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp
)

class LatestNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LatestNoteWidget()
}
