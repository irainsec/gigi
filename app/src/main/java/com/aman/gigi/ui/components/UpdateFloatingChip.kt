package com.aman.gigi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.data.update.AppUpdateManager
import com.aman.gigi.data.update.DownloadStatus

/**
 * A small floating pill that follows an update downloading in the background.
 *
 * Hiding the update dialog used to mean losing sight of the download entirely — the
 * only trace was a system notification, and there was no way back into the installer
 * from inside the app. Tapping this reopens the dialog, and it turns into a green
 * "Ready — tap to install" once the APK has been verified.
 */
@Composable
fun UpdateFloatingChip(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by AppUpdateManager.downloadProgress.collectAsState()
    val active = progress.status == DownloadStatus.DOWNLOADING ||
        progress.status == DownloadStatus.VERIFYING ||
        progress.status == DownloadStatus.WAITING_FOR_NETWORK ||
        progress.status == DownloadStatus.COMPLETED

    AnimatedVisibility(
        visible = visible && active,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        val done = progress.status == DownloadStatus.COMPLETED
        val paused = progress.status == DownloadStatus.WAITING_FOR_NETWORK
        val accent = when {
            done -> Color(0xFF34D399)
            paused -> Color(0xFFFBBF24)   // amber: nothing is wrong, it's just waiting
            else -> Color(0xFFB9A6FF)
        }
        val pct by animateFloatAsState(
            targetValue = progress.progressPercent / 100f,
            animationSpec = tween(400),
            label = "updatePct"
        )
        // A gentle breath so a finished download asks to be tapped.
        val breathe by rememberInfiniteTransition(label = "chip").animateFloat(
            initialValue = 0.94f, targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "breathe"
        )
        val scale = if (done) breathe else 1f

        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xF2221C33), Color(0xF22B2342))
                    )
                )
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size((26 * scale).dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.14f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = if (done) 360f else 360f * pct.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )
                }
                Text(
                    when {
                        done -> "✓"
                        paused -> "⏸"
                        else -> "${progress.progressPercent}"
                    },
                    color = accent,
                    fontSize = if (done || paused) 12.sp else 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when (progress.status) {
                    DownloadStatus.COMPLETED -> "Ready · tap to install"
                    DownloadStatus.VERIFYING -> "Checking…"
                    DownloadStatus.WAITING_FOR_NETWORK ->
                        progress.waitingReason ?: "Paused · waiting"
                    else -> "Updating…"
                },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
