package com.aman.gigi.ui.components

import androidx.compose.foundation.background
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
import com.aman.gigi.data.update.UpdateInfo

@Composable
fun CuteUpdateDialog(
    updateInfo: UpdateInfo?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val progressState by AppUpdateManager.downloadProgress.collectAsState()

    var isHiddenByUser by remember { mutableStateOf(false) }

    val isDownloading = progressState.status == DownloadStatus.DOWNLOADING
    val isCompleted = progressState.status == DownloadStatus.COMPLETED

    val shouldShow = (updateInfo != null || isDownloading || isCompleted) && !isHiddenByUser

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
                            text = if (isCompleted) "📦" else if (isDownloading) "🚀" else "✨",
                            fontSize = 34.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when {
                            isCompleted -> "Update Ready to Install! 📦"
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
                            isDownloading -> "High-speed background download... You can close or keep using Gigi! 💕"
                            else -> updateInfo?.releaseNotes ?: "New features and stability improvements."
                        },
                        fontSize = 12.sp,
                        color = Color(0xFFD8B4FE),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

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
                        if (!isDownloading && !isCompleted) {
                            TextButton(
                                onClick = {
                                    isHiddenByUser = true
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Later", color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold)
                            }
                        } else if (isDownloading) {
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
                                    isDownloading -> {
                                        isHiddenByUser = true
                                        onDismiss()
                                    }
                                    else -> {
                                        updateInfo?.let { info ->
                                            AppUpdateManager.startDownload(context, info.downloadUrl, info.versionName)
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
