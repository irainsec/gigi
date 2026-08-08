package com.aman.gigi.ui.screensaver.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A safe alternative to cloudy/blur effect that uses gradients and transparency
 * to simulate glassmorphism without triggering PixelCopy crashes.
 */
@Composable
fun SafeGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    borderWidth: Dp = 1.5.dp,
    content: @Composable () -> Unit
) {
    val isDark = false
    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.6f),
                        if (isDark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = shape
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.15f),
                        if (isDark) Color.White.copy(alpha = 0.01f) else Color.White.copy(alpha = 0.05f)
                    )
                )
            )
    ) {
        // Inner subtle shine
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
        )

        content()
    }
}
