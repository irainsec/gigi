package com.aman.gigi.ui.live

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/** Look and feel for one vibe: the accent tint and the little things that drift about. */
data class Vibe(
    val emoji: String,
    val tint: Color,
    val particles: List<String>,
    val blurb: String
)

val VIBES: Map<String, Vibe> = mapOf(
    "coffee" to Vibe("☕", Color(0xFFC08457), listOf("☕", "♨️", "✨", "🫖"), "chai o'clock"),
    "walk" to Vibe("🚶", Color(0xFF7FB77E), listOf("🍃", "🌿", "👟", "✨"), "let's stretch our legs"),
    "food" to Vibe("🍜", Color(0xFFE08A5D), listOf("🍜", "🥟", "🍕", "✨"), "something tasty"),
    "study" to Vibe("📚", Color(0xFF6C8AE4), listOf("📚", "✏️", "💡", "✨"), "heads down together"),
    "sport" to Vibe("🏸", Color(0xFF56B8A0), listOf("🏸", "⚡", "💨", "✨"), "let's move"),
    "movie" to Vibe("🎬", Color(0xFF9B6BD6), listOf("🎬", "🍿", "⭐", "✨"), "screen time"),
    "help" to Vibe("🤝", Color(0xFFE0699F), listOf("💗", "🤝", "✨", "💫"), "a hand would be lovely"),
    "other" to Vibe("✨", Color(0xFFB9A6FF), listOf("✨", "💫", "⭐", "🌙"), "just because")
)

fun vibeOf(category: String?): Vibe = VIBES[category] ?: VIBES.getValue("other")

/**
 * The soft animated backdrop behind a Live card.
 *
 * Cards with one short line of text looked bare, so each vibe gets a faint tint wash
 * and a few of its own emoji drifting upward on staggered loops. It's decoration only —
 * everything sits at low alpha behind the content and never intercepts touches.
 */
@Composable
fun VibeBackdrop(category: String?, modifier: Modifier = Modifier) {
    val vibe = vibeOf(category)
    val t = rememberInfiniteTransition(label = "vibe")

    Box(
        modifier.background(
            Brush.horizontalGradient(
                listOf(
                    vibe.tint.copy(alpha = 0.14f),
                    vibe.tint.copy(alpha = 0.05f),
                    Color.Transparent
                )
            )
        )
    ) {
        // Each particle gets its own duration and phase so they never march in step.
        vibe.particles.forEachIndexed { i, glyph ->
            val duration = 5200 + i * 1400
            val progress by t.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(duration, delayMillis = i * 700, easing = LinearEasing),
                    RepeatMode.Restart
                ),
                label = "drift$i"
            )
            // rise from below the card, sway gently, fade at both ends
            val rise = (1f - progress) * 92f
            val sway = sin((progress * 2f * Math.PI + i).toFloat()) * 9f
            val fade = (1f - kotlin.math.abs(progress - 0.5f) * 2f).coerceIn(0f, 1f)

            Text(
                glyph,
                fontSize = (11 + (i % 3) * 3).sp,
                color = Color.White.copy(alpha = 0.55f * fade),
                modifier = Modifier.offset(
                    x = (208 + i * 34).dp + sway.dp,
                    y = rise.dp
                )
            )
        }
    }
}

/** "1h 42m left" — turns an abstract expiry into something you can act on. */
fun timeLeftLabel(expiresAt: Long?): String? {
    expiresAt ?: return null
    val ms = expiresAt - System.currentTimeMillis()
    if (ms <= 0) return null
    val mins = (ms / 60_000L).toInt()
    return when {
        mins < 1 -> "ending now"
        mins < 60 -> "${mins}m left"
        mins % 60 == 0 -> "${mins / 60}h left"
        else -> "${mins / 60}h ${mins % 60}m left"
    }
}
