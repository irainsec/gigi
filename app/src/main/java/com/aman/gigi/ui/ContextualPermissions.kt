package com.aman.gigi.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * A single feature-gated permission. Instead of asking for everything up front,
 * each entry is requested the moment the user actually reaches a feature that needs it,
 * accompanied by an animated rationale popup that explains *why*.
 */
enum class FeaturePermission(
    val title: String,
    val rationale: String,
    val icon: ImageVector,
    val accent: Color,
    val manifestPermission: String?,
    val isSpecial: Boolean = false
) {
    NOTIFICATIONS(
        title = "Stay in the loop",
        rationale = "Allow notifications so you never miss a scribble, love card, or sweet message from your partner the instant it arrives.",
        icon = Icons.Default.NotificationsActive,
        accent = Color(0xFF7C3AED),
        manifestPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.POST_NOTIFICATIONS else null
    ),
    OVERLAY(
        title = "Reveal on your lockscreen",
        rationale = "Moonlight paints your partner's scribbles right over your lockscreen. To do that, Gigi needs permission to draw over other apps.",
        icon = Icons.Default.Layers,
        accent = Color(0xFF6366F1),
        manifestPermission = null,
        isSpecial = true
    ),
    BATTERY(
        title = "Keep the connection alive",
        rationale = "So messages arrive instantly even when your screen is off, allow Gigi to keep running in the background without battery restrictions.",
        icon = Icons.Default.BatteryChargingFull,
        accent = Color(0xFF10B981),
        manifestPermission = null,
        isSpecial = true
    ),
    CAMERA(
        title = "Capture the moment",
        rationale = "To snap and send a photo scribble to your partner, Gigi needs access to your camera. It's only used while you're composing.",
        icon = Icons.Default.CameraAlt,
        accent = Color(0xFFF59E0B),
        manifestPermission = Manifest.permission.CAMERA
    ),
    MICROPHONE(
        title = "Send a voice note",
        rationale = "To record audio for shared alarms and voice messages, Gigi needs access to your microphone — only while you're recording.",
        icon = Icons.Default.Mic,
        accent = Color(0xFFEC4899),
        manifestPermission = Manifest.permission.RECORD_AUDIO
    ),
    LOCATION(
        title = "Share where you are",
        rationale = "To let your partner see your location when you choose to share it, Gigi needs location access. You're always in control of when it's shared.",
        icon = Icons.Default.LocationOn,
        accent = Color(0xFF3B82F6),
        manifestPermission = Manifest.permission.ACCESS_FINE_LOCATION
    );
}

/** True when the given feature's permission is already held. */
fun isFeatureGranted(context: Context, feature: FeaturePermission): Boolean {
    return when (feature) {
        FeaturePermission.NOTIFICATIONS -> {
            val perm = feature.manifestPermission ?: return true
            ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        FeaturePermission.OVERLAY -> Settings.canDrawOverlays(context)
        FeaturePermission.BATTERY -> {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        FeaturePermission.LOCATION -> {
            // Approximate location is enough for the partner-distance badge; on
            // Android 12+ the user may grant only coarse when fine is requested.
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        else -> {
            val perm = feature.manifestPermission ?: return true
            ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}

private fun specialPermissionIntent(context: Context, feature: FeaturePermission): Intent? {
    return when (feature) {
        FeaturePermission.OVERLAY ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        FeaturePermission.BATTERY ->
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        else -> null
    }
}

/**
 * Controller handed to the UI via [LocalPermissionFlow]. Call [request] from any feature
 * entry point; if the permission is already granted the action runs immediately, otherwise
 * the animated rationale popup appears and the system request follows once the user agrees.
 */
@Stable
class PermissionFlowController internal constructor(private val appContext: Context) {
    var activeFeature by mutableStateOf<FeaturePermission?>(null)
        internal set

    internal var pendingCallback: (() -> Unit)? = null
    internal var awaitingSpecial: FeaturePermission? = null
    private val promptedOnce = mutableSetOf<FeaturePermission>()

    /** Request [feature]; runs [onGranted] immediately if held, else shows the rationale popup. */
    fun request(feature: FeaturePermission, onGranted: () -> Unit = {}) {
        if (isFeatureGranted(appContext, feature)) {
            onGranted()
        } else {
            pendingCallback = onGranted
            activeFeature = feature
        }
    }

    /**
     * Like [request], but only ever prompts once per app session for this feature, so entering
     * a screen repeatedly doesn't nag the user after they declined.
     */
    fun requestOnce(feature: FeaturePermission, onGranted: () -> Unit = {}) {
        if (isFeatureGranted(appContext, feature)) {
            onGranted()
            return
        }
        if (feature in promptedOnce) return
        promptedOnce.add(feature)
        pendingCallback = onGranted
        activeFeature = feature
    }

    internal fun dismiss() {
        activeFeature = null
        pendingCallback = null
    }
}

val LocalPermissionFlow = staticCompositionLocalOf<PermissionFlowController> {
    error("PermissionFlowHost is missing from the composition")
}

/**
 * Hosts the permission launcher + animated rationale popup and exposes a [PermissionFlowController]
 * to everything inside [content] via [LocalPermissionFlow].
 */
@Composable
fun PermissionFlowHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val controller = remember { PermissionFlowController(context.applicationContext) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val cb = controller.pendingCallback
        controller.pendingCallback = null
        if (granted) cb?.invoke()
    }

    // Special permissions resolve in system Settings, so re-check on resume.
    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val feat = controller.awaitingSpecial
                if (feat != null && isFeatureGranted(context, feat)) {
                    controller.awaitingSpecial = null
                    val cb = controller.pendingCallback
                    controller.pendingCallback = null
                    cb?.invoke()
                }
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalPermissionFlow provides controller) {
        content()
    }

    PermissionRationaleDialog(
        feature = controller.activeFeature,
        onAllow = { feature ->
            controller.activeFeature = null
            when {
                feature.isSpecial -> {
                    controller.awaitingSpecial = feature
                    specialPermissionIntent(context, feature)?.let { runCatching { context.startActivity(it) } }
                }
                feature.manifestPermission != null -> runtimeLauncher.launch(feature.manifestPermission)
                else -> {
                    val cb = controller.pendingCallback
                    controller.pendingCallback = null
                    cb?.invoke()
                }
            }
        },
        onDismiss = { controller.dismiss() }
    )
}

@Composable
private fun PermissionRationaleDialog(
    feature: FeaturePermission?,
    onAllow: (FeaturePermission) -> Unit,
    onDismiss: () -> Unit
) {
    if (feature == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Drive a spring-y enter animation as soon as the dialog mounts.
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    initialScale = 0.82f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(tween(220)),
                exit = scaleOut(targetScale = 0.9f, animationSpec = tween(150)) + fadeOut(tween(150))
            ) {
                RationaleCard(
                    feature = feature,
                    onAllow = { onAllow(feature) },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun RationaleCard(
    feature: FeaturePermission,
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 28.dp)
            .widthIn(max = 380.dp)
            // Swallow taps so clicking the card doesn't dismiss via the scrim.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF15102E)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1C1340), Color(0xFF120B2A))
                    )
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Glowing icon halo
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(feature.accent.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(feature.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = null,
                        tint = feature.accent,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = feature.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = feature.rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onAllow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = feature.accent)
            ) {
                Text(
                    "Allow",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(6.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Not now",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
