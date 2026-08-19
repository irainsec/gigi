package com.aman.gigi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aman.gigi.data.update.AppUpdateManager
import com.aman.gigi.data.update.DownloadStatus
import com.aman.gigi.data.update.NetworkQuality
import com.aman.gigi.data.update.UpdateInfo
import com.aman.gigi.data.update.UpdatePrefs

@Composable
fun CuteUpdateDialog(
    updateInfo: UpdateInfo?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val progressState by AppUpdateManager.downloadProgress.collectAsState()

    var isHiddenByUser by remember { mutableStateOf(false) }
    var wifiOnly by remember { mutableStateOf(UpdatePrefs.wifiOnly(context)) }

    // Sampled once when the dialog appears — enough to warn about mobile data without
    // holding a network callback open for the life of a dialog.
    val onMobileData = remember { NetworkQuality.of(context).let { it.online && it.metered } }

    val isDownloading = progressState.status == DownloadStatus.DOWNLOADING ||
        progressState.status == DownloadStatus.VERIFYING
    val isWaiting = progressState.status == DownloadStatus.WAITING_FOR_NETWORK
    val isCompleted = progressState.status == DownloadStatus.COMPLETED

    val shouldShow =
        (updateInfo != null || isDownloading || isWaiting || isCompleted) && !isHiddenByUser

    if (shouldShow) {
        Dialog(onDismissRequest = {
            isHiddenByUser = true
            onDismiss()
        }) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF1E1638),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFA855F7).copy(alpha = 0.5f)),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Icon Badge
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFEC4899), Color(0xFFA855F7))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                isCompleted -> "📦"
                                isWaiting -> "⏸️"
                                isDownloading -> "🚀"
                                else -> "✨"
                            },
                            fontSize = 34.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when {
                            isCompleted -> "Update Ready to Install! 📦"
                            isWaiting -> "Paused for now ⏸️"
                            isDownloading -> "Downloading Gigi Update 🚀"
                            else -> "New Gigi ${updateInfo?.versionName ?: ""} is Here! ✨"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when {
                            isCompleted -> "Tap below to install the update directly."
                            isWaiting -> (progressState.waitingReason ?: "Waiting for a connection") +
                                " — we kept what already downloaded, so it picks up right where it stopped."
                            isDownloading -> "High-speed background download... You can close or keep using Gigi! 💕"
                            else -> updateInfo?.releaseNotes ?: "New features and stability improvements."
                        },
                        fontSize = 12.sp,
                        color = Color(0xFFD8B4FE),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    if (!isDownloading && !isWaiting && !isCompleted) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable {
                                    wifiOnly = !wifiOnly
                                    UpdatePrefs.setWifiOnly(context, wifiOnly)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📶", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Only on Wi-Fi",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (onMobileData) {
                                        "You're on mobile data right now"
                                    } else {
                                        "Saves your data for big updates"
                                    },
                                    fontSize = 10.sp,
                                    color = Color(0xFFC4B5FD)
                                )
                            }
                            Switch(
                                checked = wifiOnly,
                                onCheckedChange = {
                                    wifiOnly = it
                                    UpdatePrefs.setWifiOnly(context, it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF8B5CF6)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Real-Time Download Progress Bar
                    if (isDownloading) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${progressState.progressPercent}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                val downloadedMb = String.format("%.1f", progressState.downloadedBytes / (1024f * 1024f))
                                val totalMb = String.format("%.1f", progressState.totalBytes / (1024f * 1024f))
                                Text(
                                    text = if (progressState.totalBytes > 0) "$downloadedMb MB / $totalMb MB" else "$downloadedMb MB",
                                    fontSize = 12.sp,
                                    color = Color(0xFFC4B5FD)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressState.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = Color(0xFFEC4899),
                                trackColor = Color.White.copy(alpha = 0.15f)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!isDownloading && !isWaiting && !isCompleted) {
                            TextButton(
                                onClick = {
                                    // Remember which build was waved away, so the next
                                    // launch doesn't open this dialog all over again.
                                    updateInfo?.let { UpdatePrefs.deferVersion(context, it.versionCode) }
                                    isHiddenByUser = true
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Later", color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold)
                            }
                        } else if (isDownloading || isWaiting) {
                            OutlinedButton(
                                onClick = {
                                    isHiddenByUser = true
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text("Hide & Use App", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = {
                                when {
                                    isCompleted -> {
                                        progressState.fileUri?.let { uri ->
                                            AppUpdateManager.installApk(context, uri)
                                        }
                                    }
                                    isWaiting -> {
                                        // "Download anyway" — an explicit tap overrides
                                        // the Wi-Fi-only preference for this download.
                                        AppUpdateManager.cancelDownload(context)
                                        updateInfo?.let { info ->
                                            AppUpdateManager.startDownload(
                                                context, info.downloadUrl, info.versionName,
                                                info.sha256, respectWifiOnly = false
                                            )
                                        }
                                    }
                                    isDownloading -> {
                                        isHiddenByUser = true
                                        onDismiss()
                                    }
                                    else -> {
                                        updateInfo?.let { info ->
                                            UpdatePrefs.clearDeferral(context)
                                            AppUpdateManager.startDownload(
                                                context, info.downloadUrl, info.versionName, info.sha256
                                            )
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B5CF6)
                            ),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.weight(1.2f).height(46.dp)
                        ) {
                            Text(
                                text = when {
                                    isCompleted -> "Install Now 🚀"
                                    isWaiting -> "Download anyway 📱"
                                    isDownloading -> "Background ⏱️"
                                    else -> "Update Now 🚀"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
