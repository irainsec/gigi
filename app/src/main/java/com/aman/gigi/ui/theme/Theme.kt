package com.aman.gigi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


private val DarkColorScheme = darkColorScheme(
    primary = GlassPrimaryDark,
    secondary = GlassSecondaryDark,
    tertiary = GlassTertiaryDark,
    surface = Color(0xFF111827),
    background = Color(0xFF0F172A),
    onSurface = Color.White,
    onBackground = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = GlassPrimary,
    secondary = GlassSecondary,
    tertiary = GlassTertiary,
    surface = Color(0xFFF8FAFC),
    background = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),
    onBackground = Color(0xFF1E293B)
)

@Composable
fun RemindMeTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
