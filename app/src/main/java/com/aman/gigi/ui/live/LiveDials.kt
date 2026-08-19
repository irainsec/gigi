package com.aman.gigi.ui.live

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/* ── value ranges ─────────────────────────────────────────────────────────────
   Both dials map the arc logarithmically: turning near the start moves in small
   steps (50 m, 5 min) and near the end in big ones (km, hours), which is how
   people actually think about "nearby" and "a while".                          */

const val MIN_RADIUS_M = 200
const val MAX_RADIUS_M = 10_000
const val MIN_DURATION_MIN = 5
const val MAX_DURATION_MIN = 300

private fun logValue(fraction: Float, min: Int, max: Int): Double =
    min * (max.toDouble() / min).pow(fraction.toDouble().coerceIn(0.0, 1.0))

private fun logFraction(value: Int, min: Int, max: Int): Float =
    (ln(value.toDouble() / min) / ln(max.toDouble() / min)).toFloat().coerceIn(0f, 1f)

/** Snap to numbers a person would actually say out loud. */
fun snapRadius(raw: Double): Int {
    val step = when {
        raw < 500 -> 25
        raw < 1000 -> 50
        raw < 3000 -> 100
        else -> 250
    }
    return ((raw / step).roundToInt() * step).coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
}

fun snapDuration(raw: Double): Int {
    val step = when {
        raw < 30 -> 5
        raw < 60 -> 10
        raw < 180 -> 15
        else -> 30
    }
    return ((raw / step).roundToInt() * step).coerceIn(MIN_DURATION_MIN, MAX_DURATION_MIN)
}

fun radiusLabel(m: Int): String =
    if (m < 1000) "$m m"
    else (m / 1000.0).let { km ->
        if (km == floor(km)) "${km.toInt()} km" else String.format("%.1f km", km)
    }

fun durationLabel(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} hr"
    else -> "${min / 60} hr ${min % 60}"
}

private fun radiusHint(m: Int): String = when {
    m <= 300 -> "just here"
    m <= 800 -> "around the corner"
    m <= 2000 -> "a short walk"
    m <= 5000 -> "worth the ride"
    else -> "anywhere in town"
}

/* ── ring geometry ────────────────────────────────────────────────────────── */

private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

private fun angleFraction(touch: Offset, center: Offset): Float? {
    val dx = touch.x - center.x
    val dy = touch.y - center.y
    // ignore the dead zone in the middle where the angle is meaningless
    if (abs(dx) < 12f && abs(dy) < 12f) return null
    val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    val rel = ((deg + 360f) % 360f - ARC_START + 360f) % 360f
    if (rel > ARC_SWEEP) return if (rel - ARC_SWEEP < 360f - rel) 1f else 0f
    return rel / ARC_SWEEP
}

private fun DrawScope.knobAt(fraction: Float, radius: Float, color: Color) {
    val rad = Math.toRadians((ARC_START + fraction * ARC_SWEEP).toDouble())
    val p = Offset(
        center.x + radius * cos(rad).toFloat(),
        center.y + radius * sin(rad).toFloat()
    )
    drawCircle(color.copy(alpha = 0.25f), radius = 30f, center = p)
    drawCircle(Color.White, radius = 17f, center = p)
    drawCircle(color, radius = 11f, center = p)
}

/* ── distance ─────────────────────────────────────────────────────────────── */

/**
 * A live OpenStreetMap disc with your reach drawn on it, ringed by a draggable
 * selector. Any value from 200 m to 10 km.
 *
 * The map zoom is derived from the radius so the circle always lands at a
 * readable size, which means the map itself zooms as you turn the dial — the
 * motion people expect from a radius picker.
 */
@Composable
fun RadiusDial(
    radiusM: Int,
    onRadiusChange: (Int) -> Unit,
    lat: Double?,
    lng: Double?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val discDp = 190.dp
    val discPx = with(density) { discDp.toPx() }

    // circle grows gently across the range; the map does the rest of the work
    val valueFraction = logFraction(radiusM, MIN_RADIUS_M, MAX_RADIUS_M)
    val circleFrac = 0.44f + 0.20f * valueFraction
    val circlePx = circleFrac * discPx / 2f

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(discDp).clip(CircleShape).background(Color(0xFF2A2340)),
                contentAlignment = Alignment.Center
            ) {
                if (lat != null && lng != null) {
                    MapDisc(lat, lng, radiusM, circlePx, discPx, accent)
                } else {
                    StylizedDisc(circlePx, accent)
                }
            }
            Dial(
                valueFraction = valueFraction,
                accent = accent,
                onFraction = { f -> onRadiusChange(snapRadius(logValue(f, MIN_RADIUS_M, MAX_RADIUS_M))) }
            )
        }
        // outside the ring, so it never sits on top of the map
        Text(
            radiusLabel(radiusM),
            color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold
        )
        Text(radiusHint(radiusM), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}

@Composable
private fun MapDisc(
    lat: Double, lng: Double, radiusM: Int,
    circlePx: Float, discPx: Float, accent: Color
) {
    // zoom that puts `radiusM` exactly at `circlePx`
    val mpp = radiusM / circlePx
    val zoomF = (ln(156543.03392 * cos(Math.toRadians(lat)) / mpp) / ln(2.0))
        .coerceIn(1.0, 19.0)
    val zoom = zoomF.roundToInt().coerceIn(1, 19)
    val scale = 2.0.pow(zoomF - zoom).toFloat()

    val (worldX, worldY) = remember(lat, lng, zoom) { OsmTiles.project(lat, lng, zoom) }
    val halfWorld = (discPx / 2f) / scale

    val minTx = floor((worldX - halfWorld) / 256.0).toInt()
    val maxTx = floor((worldX + halfWorld) / 256.0).toInt()
    val minTy = floor((worldY - halfWorld) / 256.0).toInt()
    val maxTy = floor((worldY + halfWorld) / 256.0).toInt()

    val window = TileWindow(zoom, minTx, maxTx, minTy, maxTy)
    val tiles = rememberTiles(window)
    val loadedAny = tiles.keys.any { it.startsWith("$zoom/") }

    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF2C2542))
        drawTileGrid(window, tiles, worldX, worldY, scale)
        // OSM tiles are bright; a wash keeps the dial in the app's palette
        drawRect(Color(0xFF1B1630).copy(alpha = if (loadedAny) 0.12f else 0f))
        reachOverlay(circlePx, accent)
    }

    if (loadedAny) {
        Text(
            "© OpenStreetMap",
            color = Color.White.copy(alpha = 0.45f), fontSize = 7.sp,
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        )
    }
}

@Composable
private fun StylizedDisc(circlePx: Float, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF2C2542))
        val w = size.width
        val h = size.height
        listOf(
            Triple(0.06f, 0.10f, 0.26f), Triple(0.40f, 0.06f, 0.22f),
            Triple(0.70f, 0.12f, 0.24f), Triple(0.08f, 0.44f, 0.20f),
            Triple(0.72f, 0.46f, 0.22f), Triple(0.10f, 0.74f, 0.24f),
            Triple(0.44f, 0.76f, 0.20f), Triple(0.72f, 0.72f, 0.22f)
        ).forEachIndexed { i, (x, y, s) ->
            drawRect(
                if (i % 3 == 0) Color(0xFF3A3358) else Color(0xFF352E50),
                Offset(x * w, y * h), Size(s * w, s * h * 0.8f)
            )
        }
        drawRect(Color(0xFF3A5745), Offset(0.40f * w, 0.40f * h), Size(0.26f * w, 0.21f * h))
        listOf(0.36f, 0.70f).forEach { drawRect(Color(0xFF453D66), Offset(0f, it * h), Size(w, h * 0.035f)) }
        listOf(0.34f, 0.68f).forEach { drawRect(Color(0xFF453D66), Offset(it * w, 0f), Size(w * 0.035f, h)) }
        reachOverlay(circlePx, accent)
    }
}

private fun DrawScope.reachOverlay(circlePx: Float, accent: Color) {
    drawCircle(accent.copy(alpha = 0.18f), radius = circlePx, center = center)
    drawCircle(accent, radius = circlePx, center = center, style = Stroke(width = 3.5f))
    drawCircle(accent.copy(alpha = 0.30f), radius = 16f, center = center)
    drawCircle(Color.White, radius = 7f, center = center)
    drawCircle(accent, radius = 7f, center = center, style = Stroke(width = 3f))
}

/* ── duration ─────────────────────────────────────────────────────────────── */

/** 5 minutes to 5 hours on the same ring. */
@Composable
fun DurationDial(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    accent: Color,
    endsAtLabel: String,
    modifier: Modifier = Modifier
) {
    val valueFraction = logFraction(minutes, MIN_DURATION_MIN, MAX_DURATION_MIN)
    Box(modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(190.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(Color(0xFF2E2745), Color(0xFF221C33)))
            )
        )
        Dial(
            valueFraction = valueFraction,
            accent = accent,
            ticks = 9,
            onFraction = { f ->
                onMinutesChange(snapDuration(logValue(f, MIN_DURATION_MIN, MAX_DURATION_MIN)))
            }
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                durationLabel(minutes),
                color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(endsAtLabel, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

/* ── the ring both dials share ─────────────────────────────────────────────── */

/**
 * While a finger is down the knob follows it directly — no spring in the loop,
 * which is what made the first version feel laggy. The spring only runs on
 * release, to settle onto the snapped value.
 */
@Composable
private fun Dial(
    valueFraction: Float,
    accent: Color,
    ticks: Int = 0,
    onFraction: (Float) -> Unit
) {
    val density = LocalDensity.current
    val view = LocalView.current
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var lastTickBucket by remember { mutableIntStateOf(-1) }

    val settled by animateFloatAsState(
        targetValue = valueFraction,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "dialFraction"
    )
    val shown = dragFraction ?: settled

    fun apply(f: Float) {
        onFraction(f)
        // one tick per ~2% of travel — present but not a buzzsaw
        val bucket = (f * 50f).toInt()
        if (bucket != lastTickBucket) {
            lastTickBucket = bucket
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        angleFraction(pos, Offset(size.width / 2f, size.height / 2f))?.let {
                            dragFraction = it
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            apply(it)
                        }
                    },
                    onDragEnd = { dragFraction = null },
                    onDragCancel = { dragFraction = null }
                ) { change, _ ->
                    change.consume()
                    angleFraction(change.position, Offset(size.width / 2f, size.height / 2f))
                        ?.let { dragFraction = it; apply(it) }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    angleFraction(pos, Offset(size.width / 2f, size.height / 2f))?.let(::apply)
                }
            }
    ) {
        val ringR = with(density) { 105.dp.toPx() }
        val box = Offset(center.x - ringR, center.y - ringR)
        val sz = Size(ringR * 2, ringR * 2)

        drawArc(
            color = Color.White.copy(alpha = 0.07f),
            startAngle = ARC_START, sweepAngle = ARC_SWEEP, useCenter = false,
            topLeft = box, size = sz, style = Stroke(width = 12f, cap = StrokeCap.Round)
        )
        drawArc(
            color = accent,
            startAngle = ARC_START,
            sweepAngle = (ARC_SWEEP * shown).coerceAtLeast(0.5f),
            useCenter = false,
            topLeft = box, size = sz, style = Stroke(width = 12f, cap = StrokeCap.Round)
        )

        if (ticks > 0) {
            for (i in 0 until ticks) {
                val f = i / (ticks - 1).toFloat()
                val rad = Math.toRadians((ARC_START + f * ARC_SWEEP).toDouble())
                val outer = ringR - 15f
                val inner = ringR - 23f
                drawLine(
                    color = Color.White.copy(alpha = if (f <= shown) 0.45f else 0.15f),
                    start = Offset(
                        center.x + inner * cos(rad).toFloat(),
                        center.y + inner * sin(rad).toFloat()
                    ),
                    end = Offset(
                        center.x + outer * cos(rad).toFloat(),
                        center.y + outer * sin(rad).toFloat()
                    ),
                    strokeWidth = 3f, cap = StrokeCap.Round
                )
            }
        }
        knobAt(shown, ringR, accent)
    }
}
