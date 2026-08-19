package com.aman.gigi.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.skydoves.cloudy.Cloudy

/**
 * Defines a required permission and its metadata for the UI checklist.
 */
data class AppPermission(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val manifestPermission: String? = null,
    val isSpecial: Boolean = false,
    val minApi: Int = 0
)

/**
 * Utility to check if all mandatory permissions are granted.
 */
fun checkAllPermissionsGranted(context: Context): Boolean {
    return getInitialPermissionStates(context).values.all { it }
}

@Composable
fun PermissionGuardScreen(
    onAllGranted: () -> Unit
) {
    val isDark = false
    val context = LocalContext.current
    var permissionStates by remember { mutableStateOf(getInitialPermissionStates(context)) }
    val missingPermissions = permissionStates.filter { !it.value }

    // Standard runtime permission launcher
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Update states after standard dialog
        permissionStates = getInitialPermissionStates(context)
    }

    // Effect to check if we can proceed
    LaunchedEffect(permissionStates) {
        if (permissionStates.values.all { it }) {
            onAllGranted()
        }
    }

    // Poll for special permissions when returning to app
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionStates = getInitialPermissionStates(context)
            }
        }
        val lifecycle = (context as? androidx.activity.ComponentActivity)?.lifecycle
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D0F1A),
                            Color(0xFF161927),
                            Color(0xFF1A1530)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF0F4F8),
                            Color(0xFFE6E0FF),
                            Color(0xFFF3E5F5)
                        )
                    )
                }
            )
    ) {
        // Frosted glass overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.45f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFF6200EE),
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFE8E0FF) else Color(0xFF1A237E),
                textAlign = TextAlign.Center
            )

            Text(
                text = "To provide a seamless experience, Gigi needs the following permissions. We use these only for app features like lockscreen scribbles and remote syncing.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDark) Color(0xFFB0A8D0) else Color(0xFF546E7A),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Checklist of missing permissions
            ALL_REQUIRED_PERMISSIONS.forEach { permission ->
                val isGranted = permissionStates[permission.id] ?: false
                PermissionItem(
                    permission = permission,
                    isGranted = isGranted,
                    isDark = isDark,
                    onClick = {
                        handlePermissionClick(context, permission, runtimePermissionLauncher)
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (missingPermissions.isEmpty()) {
                Button(
                    onClick = onAllGranted,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                ) {
                    Text("Continue to Gigi", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(
                    text = "${missingPermissions.size} permission(s) remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFEF5350),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(
    permission: AppPermission,
    isGranted: Boolean,
    isDark: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = !isGranted,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.5f),
                RoundedCornerShape(20.dp)
            ),
        color = if (isDark) {
            if (isGranted) Color(0xFF1A1A2E).copy(alpha = 0.6f) else Color(0xFF1A1A2E).copy(alpha = 0.85f)
        } else {
            Color.White.copy(alpha = if (isGranted) 0.3f else 0.6f)
        },
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isGranted) {
                            if (isDark) Color(0xFF1B5E20).copy(alpha = 0.3f) else Color(0xFFE8F5E9)
                        } else {
                            if (isDark) Color(0xFF3D1A8F).copy(alpha = 0.4f) else Color(0xFFEDE7F6)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else permission.icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF43A047) else Color(0xFF6200EE),
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = permission.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isGranted) {
                        if (isDark) Color(0xFF43A047).copy(alpha = 0.7f) else Color(0xFF1B5E20).copy(alpha = 0.6f)
                    } else {
                        if (isDark) Color(0xFFE8E0FF) else Color(0xFF1A237E)
                    }
                )
                Text(
                    text = if (isGranted) "Permission granted" else permission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGranted) Color(0xFF43A047).copy(alpha = 0.5f) else {
                        if (isDark) Color(0xFFB0A8D0) else Color(0xFF546E7A)
                    }
                )
            }

            if (!isGranted) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF6200EE).copy(alpha = 0.4f)
                )
            }
        }
    }
}

private fun handlePermissionClick(
    context: Context,
    permission: AppPermission,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    if (permission.isSpecial) {
        val intent = when (permission.id) {
            "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            "storage_manage" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                } else null
            }
            "notif_listener" -> Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            "battery_opt" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            else -> null
        }
        intent?.let { context.startActivity(it) }
    } else {
        permission.manifestPermission?.let { launcher.launch(arrayOf(it)) }
    }
}

private fun getInitialPermissionStates(context: Context): Map<String, Boolean> {
    return ALL_REQUIRED_PERMISSIONS.associate { permission ->
        val granted = when {
            permission.id == "overlay" -> Settings.canDrawOverlays(context)
            permission.id == "storage_manage" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
            }
            permission.id == "notif_listener" -> {
                val pkgName = context.packageName
                val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                flat?.contains(pkgName) == true
            }
            permission.id == "battery_opt" -> {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            }
            permission.manifestPermission != null -> {
                ContextCompat.checkSelfPermission(context, permission.manifestPermission) == PackageManager.PERMISSION_GRANTED
            }
            else -> true
        }
        permission.id to granted
    }
}

private val ALL_REQUIRED_PERMISSIONS = listOf(
    AppPermission(
        id = "camera",
        title = "Camera Access",
        description = "Required to capture and send scribbles.",
        icon = Icons.Default.CameraAlt,
        manifestPermission = Manifest.permission.CAMERA
    ),
    AppPermission(
        id = "mic",
        title = "Microphone",
        description = "Required for shared audio alarms.",
        icon = Icons.Default.Mic,
        manifestPermission = Manifest.permission.RECORD_AUDIO
    ),
    AppPermission(
        id = "location",
        title = "Location",
        description = "Essential for smart context sharing.",
        icon = Icons.Default.LocationOn,
        manifestPermission = Manifest.permission.ACCESS_FINE_LOCATION
    ),
    AppPermission(
        id = "overlay",
        title = "Display Over Other Apps",
        description = "CRITICAL for lockscreen reveal and scribbles.",
        icon = Icons.Default.Layers,
        isSpecial = true
    ),
    AppPermission(
        id = "storage_manage",
        title = "All Files Access",
        description = "Needed for robust remote file explorer features.",
        icon = Icons.Default.Storage,
        isSpecial = true
    ),
    AppPermission(
        id = "notif_listener",
        title = "Notification Access",
        description = "Required to mirror important alerts to your partner.",
        icon = Icons.Default.NotificationsActive,
        isSpecial = true
    ),
    AppPermission(
        id = "battery_opt",
        title = "Always-On Performance",
        description = "Required to keep Gigi alive in the background for instant sync.",
        icon = Icons.Default.BatteryChargingFull,
        isSpecial = true
    ),
    AppPermission(
        id = "post_notif",
        title = "Notifications",
        description = "Required to show download progress and partner alerts.",
        icon = Icons.Default.Notifications,
        manifestPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null
    )
).filter { it.minApi <= Build.VERSION.SDK_INT }
