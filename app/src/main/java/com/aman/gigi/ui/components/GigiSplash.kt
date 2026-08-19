package com.aman.gigi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.composed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private val Ink = Color(0xFF0E0B18)
private val Deep = Color(0xFF1B1630)
private val Lav = Color(0xFFB9A6FF)
private val Peach = Color(0xFFFFB4A2)
private val Rose = Color(0xFFFF7EB6)

/**
 * The opening animation: two little lights find each other.
 *
 * The app is about two people and a shared galaxy, so the splash is that idea and
 * nothing else — one lavender spark and one peach spark drift in from opposite edges,
 * meet in the middle, and bloom into a heart that settles into the wordmark. It runs
 * about two seconds and can be tapped away; [onFinished] also fires on its own so a
 * slow first launch never leaves someone staring at it.
 */
@Composable
fun GigiSplash(onFinished: () -> Unit) {
    // One shared clock, 0f..1f. Every element reads a slice of it, which keeps the
    // timing readable instead of scattered across a dozen independent animations.
    val t = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }

    fun finish() {
        if (!done) { done = true; onFinished() }
    }

    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(2000, easing = LinearEasing))
        finish()
    }

    // Stars are fixed for the life of the splash — regenerating them each frame makes
    // the sky crawl, which reads as noise rather than depth.
    val stars = remember {
        val rng = Random(7)
        List(110) {
            Triple(
                rng.nextFloat(),                    // x
                rng.nextFloat(),                    // y
                0.25f + rng.nextFloat() * 0.75f     // brightness / size
            )
        }
    }

    val p = t.value

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Deep, Ink), radius = 1400f))
            .clickableNoRipple { finish() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // ── stars: fade in first, then twinkle ──────────────────────────
            val starIn = segment(p, 0f, 0.30f)
            stars.forEach { (sx, sy, mag) ->
                val twinkle = 0.65f + 0.35f * sin((p * 8f + sx * 12f + sy * 7f) * PI).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = 0.55f * mag * starIn * twinkle),
                    radius = 1.1f + mag * 1.9f,
                    center = Offset(sx * size.width, sy * size.height)
                )
            }

            // ── the two sparks travelling in ────────────────────────────────
            val travel = ease(segment(p, 0.10f, 0.48f))
            val meetAt = segment(p, 0.44f, 0.56f)          // overlap window
            val startX = size.width * 0.10f
            val endX = size.width * 0.90f
            val leftX = lerp(startX, cx, travel)
            val rightX = lerp(endX, cx, travel)
            // a gentle arc so they swing toward each other rather than sliding
            val arc = (1f - travel) * size.height * 0.06f
            val sparkAlpha = (1f - meetAt).coerceIn(0f, 1f)

            if (sparkAlpha > 0f) {
                drawSpark(Offset(leftX, cy - arc), Lav, 1f - travel * 0.25f, sparkAlpha)
                drawSpark(Offset(rightX, cy + arc), Peach, 1f - travel * 0.25f, sparkAlpha)
            }

            // ── the bloom where they meet ───────────────────────────────────
            val bloom = segment(p, 0.44f, 0.62f)
            if (bloom > 0f && bloom < 1f) {
                val r = lerp(10f, size.minDimension * 0.42f, ease(bloom))
                drawCircle(
                    color = Rose.copy(alpha = 0.34f * (1f - bloom)),
                    radius = r, center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f * (1f - bloom)),
                    radius = r * 0.55f, center = Offset(cx, cy)
                )
            }

            // ── the heart, popping in on a spring-ish curve ─────────────────
            val heartIn = segment(p, 0.48f, 0.70f)
            if (heartIn > 0f) {
                val overshoot = overshootScale(heartIn)
                val beat = 1f + 0.04f * sin((p * 14f) * PI).toFloat() * segment(p, 0.70f, 1f)
                val heartSize = size.minDimension * 0.20f * overshoot * beat
                scale(1f, 1f, pivot = Offset(cx, cy)) {
                    drawHeart(
                        center = Offset(cx, cy - heartSize * 0.08f),
                        size = heartSize,
                        brush = Brush.verticalGradient(
                            listOf(Rose, Peach),
                            startY = cy - heartSize, endY = cy + heartSize
                        ),
                        alpha = heartIn.coerceIn(0f, 1f)
                    )
                }
            }

            // ── the ring sweeping outward at the end ────────────────────────
            val ring = segment(p, 0.62f, 1f)
            if (ring > 0f && ring < 1f) {
                drawCircle(
                    color = Lav.copy(alpha = 0.30f * (1f - ring)),
                    radius = lerp(size.minDimension * 0.14f, size.minDimension * 0.72f, ease(ring)),
                    center = Offset(cx, cy),
                    style = Stroke(width = lerp(6f, 1f, ring))
                )
            }
        }

        // Wordmark rises under the heart once it has landed.
        val nameIn = segment(p, 0.62f, 0.86f)
        if (nameIn > 0f) {
            Text(
                text = "Gigi",
                color = Color.White.copy(alpha = nameIn),
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (86 + (1f - ease(nameIn)) * 14f).dp)
            )
        }
        val tagIn = segment(p, 0.72f, 0.95f)
        if (tagIn > 0f) {
            Text(
                text = "for the people you keep close",
                color = Color.White.copy(alpha = 0.55f * tagIn),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).offset(y = 128.dp)
            )
        }
    }
}

/* ── little helpers ──────────────────────────────────────────────────────── */

/** Maps the global clock onto a sub-window, clamped to 0..1. */
private fun segment(t: Float, from: Float, to: Float): Float =
    ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun ease(x: Float): Float = FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))

private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

/** Pops past 1 then settles — a spring without the state machinery. */
private fun overshootScale(x: Float): Float {
    val e = ease(x)
    return e * (1f + 0.22f * sin(PI * x).toFloat())
}

private fun DrawScope.drawSpark(at: Offset, color: Color, scale: Float, alpha: Float) {
    drawCircle(color.copy(alpha = 0.14f * alpha), radius = 34f * scale, center = at)
    drawCircle(color.copy(alpha = 0.28f * alpha), radius = 18f * scale, center = at)
    drawCircle(color.copy(alpha = 0.95f * alpha), radius = 7f * scale, center = at)
    drawCircle(Color.White.copy(alpha = 0.9f * alpha), radius = 3f * scale, center = at)
}

/**
 * A heart from two top lobes and a V. Drawn rather than using the ❤ glyph so it can
 * be tinted, scaled and beaten without depending on the system emoji font.
 */
private fun DrawScope.drawHeart(center: Offset, size: Float, brush: Brush, alpha: Float) {
    val w = size
    val h = size
    val path = Path().apply {
        moveTo(center.x, center.y + h * 0.75f)
        cubicTo(
            center.x - w * 1.35f, center.y - h * 0.30f,
            center.x - w * 0.45f, center.y - h * 1.05f,
            center.x, center.y - h * 0.35f
        )
        cubicTo(
            center.x + w * 0.45f, center.y - h * 1.05f,
            center.x + w * 1.35f, center.y - h * 0.30f,
            center.x, center.y + h * 0.75f
        )
        close()
    }
    drawPath(path, brush, alpha = alpha)
}

/** Tap-to-skip without a ripple splashing across the artwork. */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
