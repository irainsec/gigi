package com.aman.gigi.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** One pin on the map. [id] must be stable so a moving pin animates instead of jumping. */
data class MapPin(
    val id: String,
    val lat: Double,
    val lng: Double,
    val label: String,
    val color: Color,
    val isSelf: Boolean = false,
    val avatarUrl: String? = null,
    val isHost: Boolean = false
)

private const val SOLO_ZOOM = 17.5f
private const val MAX_FIT_ZOOM = 18f
private const val MIN_ZOOM = 3f
private const val MAX_ZOOM = 19f

/** Where the camera is. Kept in one object so a gesture is a single state write. */
private data class Cam(val lat: Double, val lng: Double, val zoom: Float)

/**
 * A pannable, zoomable OpenStreetMap view.
 *
 * The performance work here is all about *which Compose phase* a gesture invalidates.
 * The first version read the camera during composition, so every frame of a pinch
 * recomposed the whole subtree — tile window, every pin, the lot — and it crawled.
 * Now the camera is only ever read inside the `Canvas` draw lambda and inside
 * `Modifier.offset { }` lambdas, which restricts a gesture to the draw and layout
 * phases. Composition happens only when the visible tile grid actually changes.
 *
 * The other half was `pointerInput(zoom, scale)`: changing those keys tore down and
 * rebuilt the gesture detector mid-pinch, losing pointer events. It's keyed on Unit now.
 */
@Composable
fun OsmMapView(
    pins: List<MapPin>,
    fallbackLat: Double?,
    fallbackLng: Double?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cam = remember {
        mutableStateOf(Cam(fallbackLat ?: 0.0, fallbackLng ?: 0.0, SOLO_ZOOM))
    }
    var following by remember { mutableStateOf(true) }

    val selfPulse by rememberInfiniteTransition(label = "self").animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "selfPulse"
    )

    BoxWithConstraints(modifier) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        LaunchedEffect(pins, following, wPx, hPx) {
            if (!following || wPx <= 0f || hPx <= 0f) return@LaunchedEffect
            if (pins.isEmpty()) {
                if (fallbackLat != null && fallbackLng != null) {
                    cam.value = cam.value.copy(lat = fallbackLat, lng = fallbackLng)
                }
            } else {
                cam.value = fitCamera(pins, wPx, hPx)
            }
        }

        // Recomposes only when the visible tile rectangle changes, not on every frame.
        val window by remember(wPx, hPx) {
            derivedStateOf {
                val c = cam.value
                val z = c.zoom.roundToInt().coerceIn(2, 19)
                val sc = 2.0.pow((c.zoom - z).toDouble()).toFloat()
                val (wx, wy) = OsmTiles.project(c.lat, c.lng, z)
                val hw = (wPx / 2f) / sc
                val hh = (hPx / 2f) / sc
                TileWindow(
                    z,
                    floor((wx - hw) / 256.0).toInt(), floor((wx + hw) / 256.0).toInt(),
                    floor((wy - hh) / 256.0).toInt(), floor((wy + hh) / 256.0).toInt()
                )
            }
        }
        val tiles = rememberTiles(window)

        Canvas(
            Modifier
                .fillMaxSize()
                // Keyed on Unit: rebuilding this mid-gesture drops pointer events.
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val c = cam.value
                        var zoom = c.zoom
                        if (gestureZoom != 1f) {
                            zoom = (zoom + kotlin.math.log2(gestureZoom.toDouble()).toFloat())
                                .coerceIn(MIN_ZOOM, MAX_ZOOM)
                        }
                        var lat = c.lat
                        var lng = c.lng
                        if (pan != Offset.Zero) {
                            val z = c.zoom.roundToInt().coerceIn(2, 19)
                            val sc = 2.0.pow((c.zoom - z).toDouble()).toFloat()
                            val (wx, wy) = OsmTiles.project(c.lat, c.lng, z)
                            val (la, ln) = OsmTiles.unproject(
                                wx - pan.x / sc, wy - pan.y / sc, z
                            )
                            lat = la.coerceIn(-85.0, 85.0); lng = ln
                        }
                        if (gestureZoom != 1f || pan != Offset.Zero) following = false
                        cam.value = Cam(lat, lng, zoom)
                    }
                }
        ) {
            // Reading `cam` here confines a gesture to the draw phase.
            val c = cam.value
            val z = c.zoom.roundToInt().coerceIn(2, 19)
            val sc = 2.0.pow((c.zoom - z).toDouble()).toFloat()
            val (wx, wy) = OsmTiles.project(c.lat, c.lng, z)

            drawRect(Color(0xFF2C2542))
            drawTileGrid(TileWindow(
                z,
                floor((wx - (size.width / 2f) / sc) / 256.0).toInt(),
                floor((wx + (size.width / 2f) / sc) / 256.0).toInt(),
                floor((wy - (size.height / 2f) / sc) / 256.0).toInt(),
                floor((wy + (size.height / 2f) / sc) / 256.0).toInt()
            ), tiles, wx, wy, sc)
            drawRect(Color(0xFF1B1630).copy(alpha = 0.10f))
        }

        pins.forEach { pin ->
            key(pin.id) {
                // Deliberately NOT read with `by` — the value is sampled inside the
                // offset lambda so movement animates in the layout phase instead of
                // recomposing this pin on every animation frame.
                val aLat = animateFloatAsState(
                    pin.lat.toFloat(),
                    spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow),
                    label = "lat"
                )
                val aLng = animateFloatAsState(
                    pin.lng.toFloat(),
                    spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow),
                    label = "lng"
                )
                Column(
                    Modifier.offset {
                        val c = cam.value
                        val z = c.zoom.roundToInt().coerceIn(2, 19)
                        val sc = 2.0.pow((c.zoom - z).toDouble()).toFloat()
                        val (cx, cy) = OsmTiles.project(c.lat, c.lng, z)
                        val (px, py) = OsmTiles.project(
                            aLat.value.toDouble(), aLng.value.toDouble(), z
                        )
                        IntOffset(
                            (((px - cx) * sc + wPx / 2f) - 30.dp.toPx()).roundToInt(),
                            (((py - cy) * sc + hPx / 2f) - 26.dp.toPx()).roundToInt()
                        )
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        if (pin.isSelf) {
                            Box(
                                Modifier
                                    .size((52 * selfPulse).dp)
                                    .clip(CircleShape)
                                    .background(pin.color.copy(alpha = 0.16f))
                            )
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF221C33))
                                .border(2.5.dp, pin.color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (pin.avatarUrl != null) {
                                AsyncImage(
                                    model = pin.avatarUrl,
                                    contentDescription = pin.label,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(
                                    pin.label.take(1).uppercase(),
                                    color = Color.White, fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xE6221C33))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (pin.isHost) pin.label + " ⭐" else pin.label,
                            color = Color.White, fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !following,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 74.dp, end = 14.dp)
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xF2221C33))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable { following = true },
                contentAlignment = Alignment.Center
            ) { Text("◎", color = Color(0xFFB9A6FF), fontSize = 20.sp) }
        }

        Text(
            "© OpenStreetMap",
            color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
        )
    }
}

/**
 * Centre and zoom that frames every pin. Vertical padding is tighter than horizontal
 * because the meet-up card covers the lower half of the screen.
 */
private fun fitCamera(pins: List<MapPin>, wPx: Float, hPx: Float): Cam {
    val minLat = pins.minOf { it.lat }
    val maxLat = pins.maxOf { it.lat }
    val minLng = pins.minOf { it.lng }
    val maxLng = pins.maxOf { it.lng }
    val centerLat = (minLat + maxLat) / 2
    val centerLng = (minLng + maxLng) / 2
    if (pins.size == 1) return Cam(centerLat, centerLng, SOLO_ZOOM)

    val (x1, y1) = OsmTiles.project(maxLat, minLng, 0)
    val (x2, y2) = OsmTiles.project(minLat, maxLng, 0)
    val spanX = abs(x2 - x1).coerceAtLeast(1e-6)
    val spanY = abs(y2 - y1).coerceAtLeast(1e-6)

    val zx = ln(wPx * 0.70 / spanX) / ln(2.0)
    val zy = ln(hPx * 0.45 / spanY) / ln(2.0)
    val z = min(zx, zy).toFloat().coerceIn(MIN_ZOOM, MAX_FIT_ZOOM)
    return Cam(centerLat, centerLng, z)
}
