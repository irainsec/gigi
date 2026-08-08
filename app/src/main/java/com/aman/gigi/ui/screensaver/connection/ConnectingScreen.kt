package com.aman.gigi.ui.screensaver.connection

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.Cloudy

/**
 * Screen shown while connecting to partner
 */
@Composable
fun ConnectingScreen(
    modifier: Modifier = Modifier
) {
    val isDark = false
    val infiniteTransition = rememberInfiniteTransition(label = "connecting")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val backgroundBrush = if (isDark) {
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pulsing circle with progress indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.scale(pulseScale)
            ) {
                // Background circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .border(3.dp, if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.4f), CircleShape)
                ) {
                    Cloudy(radius = 25) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.15f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color(0xFF8B5CF6).copy(alpha = if (isDark) 0.3f else 0.2f))
                    )
                }

                // Progress indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(100.dp),
                    color = Color(0xFF8B5CF6),
                    strokeWidth = 4.dp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Connecting text
            Text(
                text = "Connecting...",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8B5CF6)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status text
            Text(
                text = "Establishing secure connection",
                fontSize = 16.sp,
                color = if (isDark) Color(0xFFB0A8D0) else Color.Black.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
