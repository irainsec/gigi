package com.aman.gigi.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.model.Connection
import com.aman.gigi.model.MemberIdentity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val GALAXY_PREFS = "galaxy_orbits"
private const val ORBIT_COUNT = 4

// Cute closeness rings (0 = nearest to you).
private val ORBIT_NAMES = listOf("My Heart", "Close Ones", "Dear Stars", "Faraway")
private val ORBIT_EMOJI = listOf("💖", "🌸", "✨", "🌙")

private class ShootingStar(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val len: Float)
private class FloatThing(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val emoji: String, val size: Float)
private class Asteroid(var angle: Float, val radiusMul: Float, val size: Float, val speed: Float)
private class Nebula(val ox: Float, val oy: Float, val radius: Float, val color: Color)

/** Galaxy camera state. Held by the ViewModel so zoom/rotation survive tab switches,
 *  and backed by SharedPreferences so it also survives app restarts. */
class GalaxyCamera {
    var rotY = -0.5f
    var tilt = 0.42f
    var zoom = 1f
    var panX = 0f
    var panY = 0f
    var loaded = false
}

/** A cute surprise hidden out in the galaxy, discovered by exploring (panning). */
private class Gift(val fx: Float, val fy: Float, val emoji: String, val message: String)

private class GMoon(var angle: Float, val radius: Float, val speed: Float)
private class GPlanet(
    val id: String,
    val name: String,
    val isGroup: Boolean,



























































            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }.build()
    }

    val sunBmp = remember { loadAsset(context, "galaxy/tex/sun.jpg") }
    val moonBmp = remember { loadAsset(context, "galaxy/tex/moon.jpg") }
    val planetBmps = remember { PLANET_TEX.map { loadAsset(context, "galaxy/tex/${it.file}") } }

    // Precomputed starfield (stable across recompositions).
    val stars = remember {
        val rnd = java.util.Random(42)
        List(220) {
            floatArrayOf(
                rnd.nextFloat() * 6.2832f,                // angle
                0.2f + rnd.nextFloat() * 0.8f,            // radius fraction
                0.6f + rnd.nextFloat() * 1.8f,            // size
                if (rnd.nextFloat() < 0.18f) 1f else 0f   // pink flag
            )
        }
    }

    val centerLabel = identity?.displayName?.takeIf { it.isNotBlank() } ?: "You"

    val planets = remember(connections, groupSizes) {
        connections.mapIndexed { i, c ->
            val group = isGroupConn(c)
            val texIdx = hashStr(c.connectionId.ifBlank { "p$i" }) % PLANET_TEX.size
            // Default to a mid orbit (not 0) so planets sit clear of the sun's glow.
            val orbit = prefs.getInt(c.connectionId, 1 + i % (ORBIT_COUNT - 1)).coerceIn(0, ORBIT_COUNT - 1)
            val moons = if (group) {
                val size = (groupSizes[c.connectionId] ?: 0)
                val count = (if (size > 1) size - 1 else (2 + hashStr(c.connectionId) % 3)).coerceIn(2, 6)
                List(count) { m -> GMoon((m.toFloat() / count) * 6.2832f, 1.55f + m * 0.5f, 0.7f + (m % 3) * 0.25f) }
            } else emptyList()
            GPlanet(
                id = c.connectionId,
                name = c.partnerName.ifBlank { if (group) "Group" else "Partner" },
                isGroup = group,
                online = c.partnerPresence.equals("ONLINE", ignoreCase = true),
                bitmap = planetBmps[texIdx],
                sizeMul = PLANET_TEX[texIdx].sizeMul,
                ring = PLANET_TEX[texIdx].ring,
                orbit = orbit,
                angle = (hashStr(c.connectionId.ifBlank { "p$i" }) % 628) / 100f,
                speed = 0.18f / (orbit + 1.3f) + 0.03f,
                moons = moons
            )
        }
    }

    // Load the saved camera once per session (survives app restarts), then seed local state.
    // The in-memory holder survives tab switches; SharedPreferences survives full app restarts.
    if (!camera.loaded) {
        camera.loaded = true
        camera.rotY = prefs.getFloat("g_rotY", camera.rotY)
        camera.tilt = prefs.getFloat("g_tilt", camera.tilt)
        camera.zoom = prefs.getFloat("g_zoom", camera.zoom)
        camera.panX = prefs.getFloat("g_panX", camera.panX)
        camera.panY = prefs.getFloat("g_panY", camera.panY)
    }
    var rotY by remember { mutableFloatStateOf(camera.rotY) }
    var tilt by remember { mutableFloatStateOf(camera.tilt) }
    var zoom by remember { mutableFloatStateOf(camera.zoom) }
    var panX by remember { mutableFloatStateOf(camera.panX) }
    var panY by remember { mutableFloatStateOf(camera.panY) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var surpriseMsg by remember { mutableStateOf<String?>(null) }
    var frame by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        onDispose {















































































                        shootingStars.add(ShootingStar(sx, sy, cos(ang) * sp, sin(ang) * sp, 1f, w * 0.16f))
                    }
                    val sit = shootingStars.iterator()
                    while (sit.hasNext()) {
                        val s = sit.next(); s.x += s.vx * dt; s.y += s.vy * dt; s.life -= dt * 1.1f
                        if (s.life <= 0f || s.x > w + 100 || s.y > h + 100) sit.remove()
                    }
                    // floating hearts / sparkles
                    floatTimer -= dt
                    if (floatTimer <= 0f && floaters.size < 14) {
                        floatTimer = 0.7f + rnd.nextFloat() * 1.1f
                        floaters.add(
                            FloatThing(
                                x = w * (0.08f + rnd.nextFloat() * 0.84f), y = h * 1.02f,
                                vx = (rnd.nextFloat() - 0.5f) * h * 0.02f, vy = -h * (0.04f + rnd.nextFloat() * 0.04f),
                                life = 1f, emoji = cuteEmojis[rnd.nextInt(cuteEmojis.size)], size = h * (0.018f + rnd.nextFloat() * 0.016f)
                            )
                        )
                    }
                    val fit = floaters.iterator()
                    while (fit.hasNext()) {
                        val f = fit.next(); f.x += f.vx * dt; f.y += f.vy * dt; f.life -= dt * 0.32f
                        if (f.life <= 0f || f.y < -50) fit.remove()
                    }
                }
                frame = now
            }
        }
    }

    }

    fun orbitRadiusPx(i: Int, minDim: Float) = minDim * (0.17f + i * 0.115f) * zoom

    Box(modifier = modifier) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(planets) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val w = size.width.toFloat(); val h = size.height.toFloat()
                val cx = w / 2f; val cy = h / 2f
                val pcx = cx + panX; val pcy = cy + panY
                val minDim = min(w, h)
                val pr = minDim * 0.052f * zoom

                // Hit-test planets at current (panned) projection.
                var hit: GPlanet? = null
                var hitD = Float.MAX_VALUE
                planets.forEach { p ->
                    val ea = p.angle + rotY
                    val R = orbitRadiusPx(p.orbit, minDim)
                    val px = pcx + R * cos(ea)
                    val py = pcy + R * tilt * sin(ea)
                    val scl = 0.66f + 0.42f * ((sin(ea) + 1f) / 2f)
                    val rad = pr * p.sizeMul * scl + 16f
                    val d = hypot(down.position.x - px, down.position.y - py)
                    if (d < rad && d < hitD) { hitD = d; hit = p }
                }
                // Hit-test cute gifts out in the galaxy (only when no planet is under the finger).
                var giftHit: Gift? = null
                if (hit == null) {
                    var gd = Float.MAX_VALUE
                    gifts.forEach { g ->
                        val gxp = pcx + g.fx * minDim * zoom
                        val gyp = pcy + g.fy * minDim * zoom
                        val d = hypot(down.position.x - gxp, down.position.y - gyp)
                        if (d < minDim * 0.06f && d < gd) { gd = d; giftHit = g }
                    }
                }
                if (hit != null) draggingId = hit!!.id
                var totalMove = 0f
                var prevPinch = -1f

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2 && hit == null) {
                        val d = hypot(
                            pressed[0].position.x - pressed[1].position.x,
                            pressed[0].position.y - pressed[1].position.y
                        )
                        if (prevPinch > 0f) zoom = (zoom * (d / prevPinch)).coerceIn(0.55f, 2.6f)
                        prevPinch = d
                        camera.zoom = zoom
                        pressed.forEach { it.consume() }
                    } else {
                        val ch = pressed[0]
                        val delta = ch.positionChange()
                        totalMove += hypot(delta.x, delta.y)
                        val hp = hit
                        if (hp != null) {
                            val ux = (ch.position.x - pcx) / zoom
                            val uy = (ch.position.y - pcy) / (zoom * tilt)
                            val dist = hypot(ux, uy)
                            var best = 0; var bd = Float.MAX_VALUE
                            for (oi in 0 until ORBIT_COUNT) {
                                val rr = (0.17f + oi * 0.115f) * minDim
                                val dd = abs(rr - dist)
                                if (dd < bd) { bd = dd; best = oi }
                            }
                            hp.orbit = best
                            hp.angle = kotlin.math.atan2(uy, ux) - rotY
                        } else {
                            // Drag to explore — pan the camera across the vast galaxy.
                            val lim = minDim * 4f
                            panX = (panX + delta.x).coerceIn(-lim, lim)
                            panY = (panY + delta.y).coerceIn(-lim, lim)
                            camera.panX = panX; camera.panY = panY
                        }
                        pressed.forEach { it.consume() }
                    }
                }

                val hp = hit
                if (hp != null) {
                    if (totalMove < 16f) onOpenConnection(hp.id)
                    else prefs.edit().putInt(hp.id, hp.orbit).apply()
                    draggingId = null
                } else if (totalMove < 16f && giftHit != null) {
                    surpriseMsg = giftHit!!.message   // tapped a hidden gift → reveal the surprise
                } else {
                    // Persist camera after a pan/zoom gesture so it survives even a hard kill.
                    camera.panX = panX; camera.panY = panY; camera.zoom = zoom
                    prefs.edit().putFloat("g_panX", panX).putFloat("g_panY", panY).putFloat("g_zoom", zoom).apply()
                }
            }
        }
    ) {
        frame // read so the canvas redraws every animation frame
        val w = size.width; val h = size.height
        val cx = w / 2f; val cy = h / 2f
        val pcx = cx + panX; val pcy = cy + panY   // panned solar-system center
        val minDim = min(w, h)
        lastW = w; lastH = h

        // Background space gradient (the bright core follows the sun as you explore)
        drawRect(
            brush = Brush.radialGradient(
                radius = maxOf(w, h) * 0.85f
            )
        )

        // Soft nebula clouds (part of the explorable world)
        nebulae.forEach { n ->
            glow(pcx + n.ox * w, pcy + n.oy * h, n.radius * minDim, n.color, 0.13f)
        }

        // Stars
        stars.forEach { s ->
            val a = s[0] + rotY * 0.15f
            val rr = s[1] * maxOf(w, h) * 0.6f
            val sx = cx + rr * cos(a)
            val sy = cy + rr * tilt * sin(a) * 0.7f
            drawCircle(
                color = if (s[3] == 1f) Color(0xFFFFB3E6) else Color.White,
                radius = s[2],
                center = Offset(sx, sy),
                alpha = 0.7f
            )
        }

        // Orbit rings + cute names
        for (i in 0 until ORBIT_COUNT) {
            val R = orbitRadiusPx(i, minDim)
            withTransform({ scale(1f, tilt, pivot = Offset(pcx, pcy)) }) {
                drawCircle(
                    color = Color(0xFFB59BFF).copy(alpha = 0.16f),
                    radius = R,

















































        items.add(Item(null, 0f)) // sun
        items.sortBy { it.depth }

        val pr = minDim * 0.052f * zoom
        items.forEach { it ->
            val p = it.planet
            if (p == null) drawSun(pcx, pcy, minDim, zoom, sunBmp, centerLabel, frame)
            else drawPlanet(p, pcx, pcy, minDim, zoom, tilt, rotY, pr, moonBmp) { mi ->
            if (p == null) drawSun(pcx, pcy, minDim, zoom, sunBmp, centerLabel, frame)
            else drawPlanet(p, pcx, pcy, minDim, zoom, tilt, rotY, pr, moonBmp) { mi ->
                if (!p.isGroup) false
                else !groupMemberEmojis[p.id]?.getOrNull(mi).isNullOrBlank()
            }
        }
    }

    // 💫 EMOJI LAYER: animated 3D fluent emojis overlay (sun + planets + moons) 💫
    // Rendered as Compose Boxes on top of the Canvas so GIF/WebP animations work.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val emojiFor = { key: String, fallback: String ->
        prefs.getString("emoji_$key", null)
            ?.takeIf { it.startsWith("file:///") || it.startsWith("http") }
            ?: fallback
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val bw = constraints.maxWidth.toFloat()
        val bh = constraints.maxHeight.toFloat()
        val bcx = bw / 2f
        val bcy = bh / 2f
        val bpr = bMinDim * 0.052f * zoom

        // Sun
        val sunR = bpr * 1.5f
        val sunUrl = emojiFor("self", "file:///android_asset/galaxy/emoji/sun_with_face.png")
        Box(
            modifier = Modifier
                .offset { IntOffset((bpcx - sunR).toInt(), (bpcy - sunR).toInt()) }
                .size(with(density) { (sunR * 2f).toDp() })
        ) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(context).data(sunUrl).build(),
                imageLoader = emojiLoader,
                contentDescription = null,
                error = null,
                placeholder = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Planets + moons
        planets.forEach { p ->
            val ea = p.angle + rotY
            val R = bMinDim * (0.17f + p.orbit * 0.115f) * zoom
            val px = bpcx + R * kotlin.math.cos(ea)
            val py = bpcy + R * tilt * kotlin.math.sin(ea)
            val scl = 0.66f + 0.42f * ((kotlin.math.sin(ea) + 1f) / 2f)
            val pr2 = bpr * p.sizeMul * scl
            val planetUrl = emojiFor(p.id, "file:///android_asset/galaxy/emoji/full_moon_face.png")

            Box(
                modifier = Modifier
                    .offset { IntOffset((px - pr2).toInt(), (py - pr2).toInt()) }
                    .size(with(density) { (pr2 * 2f).toDp() })
            ) {
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(context).data(planetUrl).build(),
                    imageLoader = emojiLoader,
                    contentDescription = null,
                    error = null,
                    placeholder = null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Draw animated emoji moons for group members
            p.moons.forEachIndexed { mi, m ->
                val rawMoonUrl = groupMemberEmojis[p.id]?.getOrNull(mi)
                // Only render if URL is a real loadable path (not a raw key/empty string)
                val moonUrl = rawMoonUrl?.takeIf {
                    it.startsWith("file:///") || it.startsWith("http") || it.startsWith("content://")
                }
                if (moonUrl != null) {
                    val mx = px + pr2 * m.radius * kotlin.math.cos(m.angle)
                    val my = py + pr2 * m.radius * tilt * kotlin.math.sin(m.angle)
                    val mr = pr2 * 0.32f
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((mx - mr).toInt(), (my - mr).toInt()) }
                            .size(with(density) { (mr * 2f).toDp() })
                    ) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(context).data(moonUrl).build(),
                            imageLoader = emojiLoader,
                            contentDescription = null,
                            error = null,
                            placeholder = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(start = 16.dp, top = 70.dp)
        ) {
            Surface(
                onClick = { panX = 0f; panY = 0f; camera.panX = 0f; camera.panY = 0f
                    prefs.edit().putFloat("g_panX", 0f).putFloat("g_panY", 0f).apply() },
                shape = CircleShape,
                color = Color(0xFF8B5CF6).copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                shape = CircleShape,
                color = Color(0xFF8B5CF6).copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🏠", fontSize = 14.sp)
                    Text("Recenter", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Surprise popup when a hidden gift is tapped.
        val msg = surpriseMsg
        AnimatedVisibility(
            visible = msg != null,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
        ) {
            if (msg != null) {
                LaunchedEffect(msg) { kotlinx.coroutines.delay(3200); surpriseMsg = null }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1C1340),
                    modifier = Modifier.widthIn(max = 300.dp).padding(24.dp)
                        .clickable { surpriseMsg = null }
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text("You found a surprise!", color = Color(0xFFFFD27D), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text(msg, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

private fun DrawScope.circleImage(bmp: ImageBitmap?, x: Float, y: Float, r: Float, fallback: Color) {
    if (bmp == null) {
        drawCircle(fallback, r, Offset(x, y))
        return
    }
    clipPath(Path().apply { addOval(Rect(Offset(x - r, y - r), Size(r * 2, r * 2))) }) {
        drawImage(
            image = bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bmp.width, bmp.height),
            dstOffset = IntOffset((x - r).roundToInt(), (y - r).roundToInt()),
            dstSize = IntSize((r * 2).roundToInt(), (r * 2).roundToInt())
        )
    }
private fun DrawScope.glow(x: Float, y: Float, r: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = Offset(x, y),
            radius = r
        ),
        radius = r,
        center = Offset(x, y)
    )
}

private fun DrawScope.label(text: String, x: Float, y: Float, sizePx: Float, color: Int = android.graphics.Color.WHITE) {
    val shown = if (text.length > 16) text.take(15) + "…" else text
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            this.color = color
            textSize = sizePx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
            setShadowLayer(6f, 0f, 1f, android.graphics.Color.BLACK)
        }
        drawText(shown, x, y, paint)
    }
}

/** Cute kawaii face (eyes, smile, rosy cheeks) + glossy shine, billboarded to the viewer. */
private fun DrawScope.cuteFace(x: Float, y: Float, r: Float) {
    if (r < 9f) return // too small to bother
    val eyeDx = r * 0.34f
    val eyeY = y - r * 0.06f
    val eyeR = r * 0.15f
    // glossy shine
    glow(x - r * 0.32f, y - r * 0.38f, r * 0.6f, Color.White, 0.30f)
    // rosy cheeks
    drawCircle(Color(0xFFFF8FB0).copy(alpha = 0.55f), r * 0.16f, Offset(x - r * 0.52f, y + r * 0.16f))
    drawCircle(Color(0xFFFF8FB0).copy(alpha = 0.55f), r * 0.16f, Offset(x + r * 0.52f, y + r * 0.16f))
    // eyes (white + dark pupil with a sparkle)
    drawCircle(Color.White, eyeR, Offset(x - eyeDx, eyeY))
    drawCircle(Color.White, eyeR, Offset(x + eyeDx, eyeY))
    drawCircle(Color(0xFF20142E), eyeR * 0.6f, Offset(x - eyeDx, eyeY + eyeR * 0.1f))
    drawCircle(Color(0xFF20142E), eyeR * 0.6f, Offset(x + eyeDx, eyeY + eyeR * 0.1f))
    drawCircle(Color.White, eyeR * 0.22f, Offset(x - eyeDx + eyeR * 0.2f, eyeY - eyeR * 0.15f))
    drawCircle(Color.White, eyeR * 0.22f, Offset(x + eyeDx + eyeR * 0.2f, eyeY - eyeR * 0.15f))
    // smile
    val sw = r * 0.34f
    val path = Path().apply {
        moveTo(x - sw, y + r * 0.26f)
        quadraticBezierTo(x, y + r * 0.5f, x + sw, y + r * 0.26f)
    }
    drawPath(path, Color(0xFF20142E), style = Stroke(width = maxOf(1.5f, r * 0.07f)))
}

private fun DrawScope.drawSun(
    cx: Float, cy: Float, minDim: Float, zoom: Float,
    sunBmp: ImageBitmap?, centerLabel: String, frame: Long
) {
    val base = minDim * 0.072f * zoom
    val pulse = 1f + sin(frame / 600_000_000f) * 0.04f
    glow(cx, cy, base * 2.6f * pulse, Color(0xFFFFB45A), 0.5f)
    glow(cx, cy, base * 1.6f * pulse, Color(0xFFFFE196), 0.85f)
    circleImage(sunBmp, cx, cy, base, Color(0xFFFFB45A))

private fun DrawScope.drawPlanet(
    p: GPlanet, cx: Float, cy: Float, minDim: Float, zoom: Float, tilt: Float, rotY: Float,
    pr: Float, moonBmp: ImageBitmap?,
    hasAnimatedEmoji: (Int) -> Boolean = { false }
) {
    val ea = p.angle + rotY
    val R = minDim * (0.17f + p.orbit * 0.115f) * zoom
    val x = cx + R * cos(ea)
    val y = cy + R * tilt * sin(ea)
    val scl = 0.66f + 0.42f * ((sin(ea) + 1f) / 2f)
    val r = pr * p.sizeMul * scl
    val halo = if (p.isGroup) Color(0xFF6366F1) else Color(0xFFEC4899)

    // online dot
    drawCircle(Color.White, r * 0.26f + 2f, Offset(x + r * 0.78f, y - r * 0.78f))
    drawCircle(
        if (p.online) Color(0xFF22C55E) else Color(0xFF94A3B8),
        r * 0.26f, Offset(x + r * 0.78f, y - r * 0.78f)
    )

    label(p.name, x, y + r + 16f, maxOf(11f * 2.4f, 13f * scl * 2.4f).coerceAtMost(minDim * 0.034f))
}

    }

    glow(x, y, r * 2.2f, halo, 0.55f)
    circleImage(p.bitmap, x, y, r, Color(0xFF555555))
    if (p.ring) {
        withTransform({ rotate(-28f, pivot = Offset(x, y)); scale(1f, 0.4f, pivot = Offset(x, y)) }) {
            drawCircle(
                color = Color(0xFFE6D2AA).copy(alpha = 0.8f),
                radius = r * 1.7f,
                center = Offset(x, y),
                style = Stroke(width = maxOf(2f, r * 0.18f))
            )
        }
    }
    // cuteFace(x, y, r)

    // moons in front
    p.moons.forEachIndexed { mi, m ->
        if (sin(m.angle) >= 0f && !hasAnimatedEmoji(mi)) {
            val mx = x + r * m.radius * cos(m.angle)
            val my = y + r * m.radius * tilt * sin(m.angle)
            circleImage(moonBmp, mx, my, r * 0.28f, Color(0xFF888888))
        }
    }

    // online dot
    drawCircle(Color.White, r * 0.26f + 2f, Offset(x + r * 0.78f, y - r * 0.78f))
    drawCircle(
        if (p.online) Color(0xFF22C55E) else Color(0xFF94A3B8),
        r * 0.26f, Offset(x + r * 0.78f, y - r * 0.78f)
    )

    label(p.name, x, y + r + 16f, maxOf(11f * 2.4f, 13f * scl * 2.4f).coerceAtMost(minDim * 0.034f))
}

