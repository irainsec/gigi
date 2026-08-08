package com.aman.gigi.ui

import android.content.Context

import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

private class GMoon(var angle: Float, val radius: Float, val speed: Float, val emojiUrl: String?)
private class GPlanet(
    val id: String,
    val name: String,
    val isGroup: Boolean,
    val online: Boolean,
    val emojiUrl: String,
    var orbit: Int,
    var angle: Float,
    val speed: Float,
    val moons: List<GMoon>
)

private fun hashStr(s: String): Int {
    var h = 0
    for (c in s) h = h * 31 + c.code
    return abs(h)
}

private fun loadAsset(context: Context, path: String): ImageBitmap? = runCatching {
    context.assets.open(path).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
}.getOrNull()

private fun isGroupConn(c: Connection) =
    c.isGroup || c.relationshipType.equals("GROUP", ignoreCase = true)

/**
 * Full-screen 3D-style galaxy rendered natively with Compose Canvas (no WebView): you are the
 * sun at the center, each connection is a planet (real textures) orbiting on tilted rings, groups
 * carry moons. Drag to rotate/tilt, pinch to zoom, drag a planet to another ring (closeness),
 * tap a planet to open it.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GalaxyView(
    identity: MemberIdentity?,
    connections: List<Connection>,
    groupMemberEmojis: Map<String, List<String?>>,
    // connectionId → what that person is listening to right now (any music app)
    nowPlaying: Map<String, com.aman.gigi.data.nowplaying.NowPlaying> = emptyMap(),
    myNowPlaying: com.aman.gigi.data.nowplaying.NowPlaying? = null,
    // connectionId → a sweet quote just sent to / received from that person
    quotes: Map<String, String> = emptyMap(),
    camera: GalaxyCamera,
    onOpenConnection: (String) -> Unit,
    onSunClick: () -> Unit,
    onNowPlayingClick: (com.aman.gigi.data.nowplaying.NowPlaying) -> Unit = {},
    /** Shown as the call-to-action when the galaxy has no planets yet. */
    onInvite: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(GALAXY_PREFS, Context.MODE_PRIVATE) }

    // Reactively track emoji_self from SharedPreferences so galaxy updates immediately when user picks a new emoji
    var emojiSelfPref by remember { mutableStateOf(prefs.getString("emoji_self", null)) }
    DisposableEffect(context) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "emoji_self") emojiSelfPref = sp.getString("emoji_self", null)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val moonBmp = remember { loadAsset(context, "galaxy/tex/moon.jpg") }
    var showScaleDialogFor by remember { mutableStateOf<String?>(null) }
    var currentScale by remember { mutableStateOf(1f) }
    var selectedOrbit by remember { mutableStateOf(0) }
    var scaleBump by remember { mutableStateOf(0) }   // bumped on save → re-reads scale prefs

    // Long-press detection lives in the COMPOSITION scope (the gesture scope is a
    // restricted suspend scope where delay/withTimeout aren't allowed). The gesture
    // sets pressTarget on touch-down and clears it on move/lift; if it survives the
    // hold delay, we open the size + orbit customizer for that planet.
    var pressTarget by remember { mutableStateOf<String?>(null) }
    var longPressFired by remember { mutableStateOf(false) }
    LaunchedEffect(pressTarget) {
        val t = pressTarget ?: return@LaunchedEffect
        kotlinx.coroutines.delay(450)
        if (pressTarget == t) {
            currentScale = if (t == "sun") prefs.getFloat("emoji_self_scale", 1f)
                           else prefs.getFloat("scale_$t", 1f)
            showScaleDialogFor = t
            longPressFired = true
        }
    }

    val targetId = showScaleDialogFor
    if (targetId != null) {
        val isSelf = targetId == "sun"
        LaunchedEffect(targetId) {
            if (!isSelf) {
                selectedOrbit = prefs.getInt(targetId, 0).coerceIn(0, ORBIT_COUNT - 1)
            }
        }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showScaleDialogFor = null },
            title = { androidx.compose.material3.Text("Customize Planet") },
            text = {
                Column {
                    androidx.compose.material3.Text("Emoji Size", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.material3.Slider(
                        value = currentScale,
                        onValueChange = { currentScale = it },
                        valueRange = 0.5f..2.5f
                    )

                    if (!isSelf) {
                        Spacer(Modifier.height(16.dp))
                        androidx.compose.material3.Text("Orbit Ring", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ORBIT_NAMES.indices.forEach { oi ->
                                Surface(
                                    onClick = { selectedOrbit = oi },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selectedOrbit == oi) Color(0xFF8B5CF6).copy(alpha = 0.25f) else Color.Transparent,
                                    border = BorderStroke(1.dp, if (selectedOrbit == oi) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.15f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${ORBIT_EMOJI[oi]} ${ORBIT_NAMES[oi]}", color = Color.White, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val key = if (isSelf) "emoji_self_scale" else "scale_$targetId"
                    prefs.edit().putFloat(key, currentScale).apply()
                    if (!isSelf) {
                        prefs.edit().putInt(targetId, selectedOrbit).apply()
                    }
                    scaleBump++          // force the avatar layer to re-read the new size
                    showScaleDialogFor = null
                }) {
                    androidx.compose.material3.Text("Save")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showScaleDialogFor = null }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    // emoji_self prefs key wins (user's latest pick); fall back to identity fields
    val sunEmojiUrl = if (identity?.avatarMode == "TWIGI" && !identity.twigiRenderUrl.isNullOrBlank()) {
        identity.twigiRenderUrl
    } else {
        emojiSelfPref?.takeIf { it.isNotBlank() }
            ?: identity?.avatarUrl?.takeIf { it.isNotBlank() }
            ?: identity?.profileEmojiUrl?.takeIf { it.isNotBlank() }
            ?: com.aman.gigi.ui.components.TELEGRAM_EMOJIS.first()
    }

    // Precomputed starfield (stable across recompositions).
    // A real galaxy: stars laid along logarithmic SPIRAL ARMS around a bright core,
    // in three PARALLAX depth layers, tinted by colour temperature (blue-white hot →
    // amber → red) instead of flat white.
    //   [0] angle  [1] radius frac  [2] size  [3] colour idx  [4] depth (0 far…1 near)
    //   [5] twinkle phase
    val stars = remember {
        val rnd = java.util.Random(42)
        val arms = 3
        val out = ArrayList<FloatArray>(560)
        repeat(560) { i ->
            val depth = when {
                i % 5 == 0 -> 1.0f          // near layer — moves most when you pan
                i % 5 == 1 -> 0.6f
                else -> 0.28f               // far layer — nearly fixed, feels distant
            }
            // logarithmic spiral: r = a·e^(b·θ), scattered around one of the arms
            val arm = rnd.nextInt(arms)
            val t = rnd.nextFloat().let { it * it }             // denser toward the core
            val theta = t * 5.4f + arm * (6.2832f / arms)
            val spread = (rnd.nextFloat() - 0.5f) * (0.30f + t * 0.55f)
            val r = (0.06f + 0.62f * t) * (1f + (rnd.nextFloat() - 0.5f) * 0.12f)
            out.add(
                floatArrayOf(
                    theta + spread,
                    r,
                    0.5f + rnd.nextFloat() * 1.9f,
                    rnd.nextFloat(),                             // colour temperature
                    depth,
                    rnd.nextFloat() * 6.2832f
                )
            )
        }
        // a sparse halo of far field stars so the edges aren't empty
        repeat(120) {
            out.add(
                floatArrayOf(
                    rnd.nextFloat() * 6.2832f,
                    0.55f + rnd.nextFloat() * 0.75f,
                    0.4f + rnd.nextFloat() * 1.1f,
                    rnd.nextFloat(),
                    0.2f,
                    rnd.nextFloat() * 6.2832f
                )
            )
        }
        out
    }

    /** Star colour by temperature: hot blue-white → white → warm amber → cool red. */
    fun starColor(t: Float): Color = when {
        t < 0.16f -> Color(0xFFBFD4FF)   // hot blue-white
        t < 0.55f -> Color(0xFFFFFFFF)   // white
        t < 0.78f -> Color(0xFFFFF0C9)   // yellow-white
        t < 0.92f -> Color(0xFFFFD9A0)   // amber
        else -> Color(0xFFFFB3E6)        // the app's signature pink giants
    }

    val centerLabel = identity?.displayName?.takeIf { it.isNotBlank() } ?: "You"

    // Keeps each planet's live orbit angle across list rebuilds. `connections` is a Room
    // Flow that re-emits on every presence heartbeat / lastSyncedAt write, which rebuilt
    // the planet list and snapped every planet back to its seed angle — so nothing ever
    // completed a lap. Seeding from these stores lets the orbit resume instead of reset.
    val angleStore = remember { HashMap<String, Float>() }
    val moonAngleStore = remember { HashMap<String, FloatArray>() }

    val planets = remember(connections, groupMemberEmojis) {
        connections.mapIndexed { i, c ->
            val group = isGroupConn(c)
            // Default to a mid orbit (not 0) so planets sit clear of the sun's glow.
            val orbit = prefs.getInt(c.connectionId, 1 + i % (ORBIT_COUNT - 1)).coerceIn(0, ORBIT_COUNT - 1)
            val moons = if (group) {
                val memberEmojis = groupMemberEmojis[c.connectionId] ?: emptyList()
                val count = memberEmojis.size
                val savedMoons = moonAngleStore[c.connectionId]
                List(count) { m -> 
                    GMoon(
                        angle = savedMoons?.getOrNull(m)
                            ?: (m.toFloat() / count.coerceAtLeast(1)) * 6.2832f, 
                        radius = 1.35f + m * 0.15f, 
                        speed = 0.7f + (m % 3) * 0.25f,
                        emojiUrl = memberEmojis[m]
                    ) 
                }
            } else emptyList()
            val savedEmoji = prefs.getString("emoji_${c.connectionId.lowercase()}", null)?.takeIf { it.isNotBlank() }
            val emojiUrl = if (c.partnerAvatarMode == "TWIGI" && !c.partnerTwigiUrl.isNullOrBlank()) {
                c.partnerTwigiUrl
            } else {
                savedEmoji
                    ?: c.partnerEmojiUrl?.takeIf { it.isNotBlank() }
                    ?: c.partnerEmoji?.takeIf { it.isNotBlank() }
                    ?: c.partnerAvatarUrl?.takeIf { it.isNotBlank() }
                    ?: com.aman.gigi.ui.components.TELEGRAM_EMOJIS[hashStr(c.connectionId.ifBlank { "p$i" }) % com.aman.gigi.ui.components.TELEGRAM_EMOJIS.size]
            }
            GPlanet(
                id = c.connectionId,
                name = c.partnerName.ifBlank { if (group) "Group" else "Partner" },
                isGroup = group,
                online = c.partnerPresence.equals("ONLINE", ignoreCase = true),
                emojiUrl = emojiUrl,
                orbit = orbit,
                angle = angleStore[c.connectionId]
                    ?: (hashStr(c.connectionId.ifBlank { "p$i" }) % 628) / 100f,
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
            camera.rotY = rotY; camera.tilt = tilt; camera.zoom = zoom; camera.panX = panX; camera.panY = panY
            prefs.edit().putFloat("g_rotY", rotY).putFloat("g_tilt", tilt).putFloat("g_zoom", zoom)
                .putFloat("g_panX", panX).putFloat("g_panY", panY).apply()
        }
    }

    // Cute surprises scattered out in the vast galaxy — drag to explore and find them.
    val gifts = remember {
        val r = java.util.Random(7)
        val defs = listOf(
            "🎁" to "A little gift, just because 💝",
            "🌟" to "Make a wish on this star ✨",
            "🍭" to "A sweet treat for a sweet you 🍬",
            "🎈" to "Happy thoughts, floating your way 🎈",
            "💌" to "A love note drifting in space 💕",
            "🦄" to "A magical visitor says hi! 🦄",
            "🌈" to "Somewhere over the cosmic rainbow 🌈",
            "🪐" to "A tiny secret planet, all yours 🪐",
            "🚀" to "Zooming through your galaxy! 🚀",
            "🧸" to "A cuddly little space buddy 🧸",
            "🍀" to "Lucky find — good things are coming 🍀",
            "👽" to "Beep boop… a friendly alien! 👽"
        )
        defs.mapIndexed { i, (emoji, msg) ->
            val ang = (i.toFloat() / defs.size) * 6.2832f + r.nextFloat() * 0.5f
            val dist = 1.35f + r.nextFloat() * 2.0f
            Gift(cos(ang) * dist, sin(ang) * dist, emoji, msg)
        }
    }

    // ── Ambient galaxy elements ──
    val rnd = remember { java.util.Random() }
    val shootingStars = remember { mutableListOf<ShootingStar>() }
    val floaters = remember { mutableListOf<FloatThing>() }
    val asteroids = remember {
        List(46) {
            Asteroid(
                angle = rnd.nextFloat() * 6.2832f,
                radiusMul = 0.40f + rnd.nextFloat() * 0.05f,   // a belt just inside the far ring
                size = 1.4f + rnd.nextFloat() * 2.6f,
                speed = 0.04f + rnd.nextFloat() * 0.03f
            )
        }
    }
    val nebulae = remember {
        listOf(
            Nebula(-0.28f, -0.22f, 0.55f, Color(0xFF7C3AED)),
            Nebula(0.30f, 0.18f, 0.62f, Color(0xFFEC4899)),
            Nebula(0.10f, -0.32f, 0.42f, Color(0xFF38BDF8))
        )
    }
    val cuteEmojis = remember { listOf("💕", "✨", "💫", "🌟", "💖") }

    var lastW by remember { mutableFloatStateOf(0f) }
    var lastH by remember { mutableFloatStateOf(0f) }

    // Animation clock (advances orbits + ambient elements + drives redraw).
    LaunchedEffect(planets) {
        var last = 0L
        var shootTimer = 1.2f
        var floatTimer = 0.6f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                planets.forEach { p ->
                    if (p.id != draggingId) p.angle += p.speed * dt
                    p.moons.forEach { it.angle += it.speed * dt }
                    // persist so the next list rebuild resumes this orbit
                    angleStore[p.id] = p.angle
                    if (p.moons.isNotEmpty()) {
                        moonAngleStore[p.id] = FloatArray(p.moons.size) { p.moons[it].angle }
                    }
                }
                asteroids.forEach { it.angle += it.speed * dt }

                val w = lastW; val h = lastH
                if (w > 0f && h > 0f) {
                    // shooting stars
                    shootTimer -= dt
                    if (shootTimer <= 0f) {
                        shootTimer = 2.5f + rnd.nextFloat() * 4f
                        val sx = w * (0.1f + rnd.nextFloat() * 0.7f)
                        val sy = h * (0.05f + rnd.nextFloat() * 0.25f)
                        val ang = 0.5f + rnd.nextFloat() * 0.5f
                        val sp = (w + h) * 0.5f
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

                // Check Sun hit first
                val sunRad = pr * 1.6f + 24f
                val sunDist = hypot(down.position.x - pcx, down.position.y - pcy)
                var hitSun = (sunDist < sunRad)

                // Hit-test planets at current (panned) projection with generous touch area.
                var hit: GPlanet? = null
                var hitD = Float.MAX_VALUE
                if (!hitSun) {
                    planets.forEach { p ->
                        val ea = p.angle + rotY
                        val R = orbitRadiusPx(p.orbit, minDim)
                        val px = pcx + R * cos(ea)
                        val py = pcy + R * tilt * sin(ea)
                        val scl = 0.66f + 0.42f * ((sin(ea) + 1f) / 2f)
                        val rad = pr * 1.5f * scl + 28f
                        val d = hypot(down.position.x - px, down.position.y - py)
                        if (d < rad && d < hitD) { hitD = d; hit = p }
                    }
                }
                // Hit-test cute gifts out in the galaxy (only when no planet is under the finger).
                var giftHit: Gift? = null
                if (false) {                      // surprises removed from the galaxy
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

                // Arm long-press for this planet / the sun (timer runs in composition).
                longPressFired = false
                pressTarget = hit?.id ?: if (hitSun) "sun" else null

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2 && hit == null && !hitSun) {
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
                        if (totalMove > 18f) pressTarget = null   // moved → it's a drag, not a hold
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
                        } else if (!hitSun) {
                            // Drag to explore — pan the camera across the vast galaxy.
                            val lim = minDim * 4f
                            panX = (panX + delta.x).coerceIn(-lim, lim)
                            panY = (panY + delta.y).coerceIn(-lim, lim)
                            camera.panX = panX; camera.panY = panY
                        }
                        pressed.forEach { it.consume() }
                    }
                }

                pressTarget = null            // finger up → disarm the hold timer
                val hp = hit
                if (hp != null) {
                    if (longPressFired) Unit          // the customizer opened; don't also open the chat
                    else if (totalMove < 16f) onOpenConnection(hp.id)
                    else prefs.edit().putInt(hp.id, hp.orbit).apply()
                    draggingId = null
                } else if (hitSun) {
                    if (!longPressFired && totalMove < 16f) onSunClick()
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
                colors = listOf(Color(0xFF1B1140), Color(0xFF0C0722), Color(0xFF05030F)),
                center = Offset(pcx, pcy),
                radius = maxOf(w, h) * 0.85f
            )
        )

        // Soft nebula clouds (part of the explorable world)
        nebulae.forEach { n ->
            glow(pcx + n.ox * w, pcy + n.oy * h, n.radius * minDim, n.color, 0.13f)
        }

        // Bright galactic core + a dark dust lane across it, like a real spiral galaxy
        glow(pcx, pcy, minDim * 0.42f, Color(0xFFFFE7B8), 0.10f)
        glow(pcx, pcy, minDim * 0.20f, Color(0xFFFFF3D6), 0.13f)
        withTransform({ rotate(-18f, Offset(pcx, pcy)) }) {
            drawOval(
                color = Color(0xFF120B26).copy(alpha = 0.5f),
                topLeft = Offset(pcx - minDim * 0.62f, pcy - minDim * 0.035f),
                size = androidx.compose.ui.geometry.Size(minDim * 1.24f, minDim * 0.07f)
            )
        }

        // Stars — spiral arms, parallax by depth, colour temperature, gentle twinkle
        stars.forEach { s ->
            val depth = s[4]
            val a = s[0] + rotY * (0.06f + depth * 0.16f)
            val rr = s[1] * maxOf(w, h) * 0.62f
            // near layers track the pan more strongly → real sense of depth
            val sx = cx + panX * depth + rr * cos(a)
            val sy = cy + panY * depth + rr * tilt * sin(a) * 0.7f
            val tw = 0.55f + 0.45f * ((sin(frame / 1_000_000_000f * 1.6f + s[5]) + 1f) / 2f)
            drawCircle(
                color = starColor(s[3]),
                radius = s[2] * (0.7f + depth * 0.6f),
                center = Offset(sx, sy),
                alpha = (0.25f + depth * 0.6f) * tw
            )
        }

        // Comet-style trail behind each planet, fading along its recent path — makes
        // the orbits feel like real motion instead of icons sliding on a ring.
        planets.forEach { p ->
            val R = minDim * (0.17f + p.orbit * 0.115f) * zoom
            val steps = 16
            for (i in 1..steps) {
                val back = i * 0.035f
                val ta = p.angle - back + rotY
                val tx = pcx + R * cos(ta)
                val ty = pcy + R * tilt * sin(ta)
                val fade = (1f - i / steps.toFloat())
                val depth = 0.6f + 0.4f * ((sin(ta) + 1f) / 2f)
                drawCircle(
                    color = if (p.isGroup) Color(0xFF8B8CF6) else Color(0xFFEC9BC5),
                    radius = (minDim * 0.010f) * fade * depth * zoom,
                    center = Offset(tx, ty),
                    alpha = 0.30f * fade
                )
            }
        }

        // Constellations: faint lines from a group's planet to each of its moons, so a
        // group literally reads as a little constellation of its people.
        planets.filter { it.isGroup && it.moons.isNotEmpty() }.forEach { p ->
            val R = minDim * (0.17f + p.orbit * 0.115f) * zoom
            val ea = p.angle + rotY
            val px = pcx + R * cos(ea)
            val py = pcy + R * tilt * sin(ea)
            val scl = 0.66f + 0.42f * ((sin(ea) + 1f) / 2f)
            val pr2 = minDim * 0.052f * zoom * scl * 1.5f
            p.moons.forEach { m ->
                val mx = px + pr2 * m.radius * cos(m.angle)
                val my = py + pr2 * m.radius * tilt * sin(m.angle)
                drawLine(
                    color = Color(0xFFB59BFF).copy(alpha = 0.22f),
                    start = Offset(px, py), end = Offset(mx, my), strokeWidth = 1.2f
                )
            }
        }

        // Orbit rings + cute names (glowing active target ring during drag)
        val activeDragPlanet = planets.find { it.id == draggingId }
        for (i in 0 until ORBIT_COUNT) {
            val R = orbitRadiusPx(i, minDim)
            val isHighlighted = (activeDragPlanet != null && activeDragPlanet.orbit == i)
            val ringColor = if (isHighlighted) Color(0xFFEC4899) else Color(0xFFB59BFF)
            val strokeWidth = if (isHighlighted) 5.5f else 2.5f
            val ringAlpha = if (isHighlighted) 0.85f else 0.35f

            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(ringColor.copy(alpha = 0.08f), ringColor.copy(alpha = ringAlpha)),
                    startY = pcy - R * tilt,
                    endY = pcy + R * tilt
                ),
                topLeft = Offset(pcx - R, pcy - R * tilt),
                size = Size(R * 2f, R * tilt * 2f),
                style = Stroke(width = strokeWidth)
            )
            // ring name floats just above the top of each ellipse
            if (i < ORBIT_NAMES.size) {
                val labelColor = if (isHighlighted) android.graphics.Color.argb(240, 255, 180, 255) else android.graphics.Color.argb(150, 210, 196, 255)
                label(
                    "${ORBIT_EMOJI[i]} ${ORBIT_NAMES[i]}",
                    pcx, pcy - R * tilt - 8f, minDim * (if (isHighlighted) 0.032f else 0.026f),
                    color = labelColor
                )
            }
        }

        // Asteroid belt
        asteroids.forEach { a ->
            val R = minDim * a.radiusMul * zoom
            val ea = a.angle + rotY
            val ax = pcx + R * cos(ea)
            val ay = pcy + R * tilt * sin(ea)
            val depthA = 0.6f + 0.4f * ((sin(ea) + 1f) / 2f)
            drawCircle(Color(0xFF9C8C7A).copy(alpha = 0.7f), a.size * depthA, Offset(ax, ay))
        }

        // (The scattered surprise trinkets were removed — the galaxy now reads as a
        // real starfield rather than a treasure hunt.)

        // Shooting stars (streaks)
        shootingStars.forEach { s ->
            val mag = hypot(s.vx, s.vy).coerceAtLeast(1f)
            val tx = s.x - s.vx / mag * s.len
            val ty = s.y - s.vy / mag * s.len
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = s.life.coerceIn(0f, 1f)), Color.Transparent),
                    start = Offset(s.x, s.y), end = Offset(tx, ty)
                ),
                start = Offset(s.x, s.y), end = Offset(tx, ty),
                strokeWidth = 3.5f
            )
            drawCircle(Color.White.copy(alpha = s.life.coerceIn(0f, 1f)), 3.5f, Offset(s.x, s.y))
        }

        // Shadows removed by request

        // Floating hearts & sparkles
        floaters.forEach { f ->
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    textSize = f.size
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    alpha = (f.life.coerceIn(0f, 1f) * 230f).toInt()
                }
                drawText(f.emoji, f.x, f.y, paint)
            }
        }
    }


        // Overlay Planets and Sun with Animated Emojis using Compose
        val w = lastW; val h = lastH
        if (w > 0f && h > 0f) {
            val cx = w / 2f; val cy = h / 2f
            val pcx = cx + panX; val pcy = cy + panY
            val minDim = min(w, h)
            val pr = minDim * 0.052f * zoom
            val currentFrame = frame // trigger recomposition when frame changes

            data class Item(val planet: GPlanet?, val depth: Float, val px: Float, val py: Float, val r: Float, val halo: Color)
            val items = ArrayList<Item>(planets.size + 1)
            planets.forEach { p ->
                val ea = p.angle + rotY
                val R = minDim * (0.17f + p.orbit * 0.115f) * zoom
                val px = pcx + R * cos(ea)
                val py = pcy + R * tilt * sin(ea)
                val scl = 0.66f + 0.42f * ((sin(ea) + 1f) / 2f)
                val halo = if (p.isGroup) Color(0xFF6366F1) else Color(0xFFEC4899)
                items.add(Item(p, sin(ea), px, py, pr * scl * 1.5f, halo))
            }
            items.add(Item(null, 0f, pcx, pcy, pr * 1.8f, Color(0xFFFFB45A)))
            items.sortBy { it.depth }

            val density = androidx.compose.ui.platform.LocalDensity.current
            val imageLoader = remember {
                coil.ImageLoader.Builder(context).components {
                    if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
                    else add(coil.decode.GifDecoder.Factory())
                }.build()
            }

            items.forEach { item ->
                val p = item.planet
                val url = p?.emojiUrl ?: sunEmojiUrl
                val label = p?.name ?: centerLabel
                val isOnline = p?.online ?: true

                scaleBump // observe: re-read sizes right after the customizer saves
                val scaleFactor = if (p != null) prefs.getFloat("scale_${p.id}", 1f) else prefs.getFloat("emoji_self_scale", 1f)
                val scaledR = item.r * scaleFactor
                val sizeDp = with(density) { (scaledR * 2).toDp() }
                
                Box(modifier = Modifier
                    .offset { IntOffset(item.px.roundToInt() - scaledR.roundToInt(), item.py.roundToInt() - scaledR.roundToInt()) }
                    .size(sizeDp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(
                        Brush.radialGradient(listOf(item.halo.copy(alpha = 0.55f), Color.Transparent))
                    ))
                    
                    val is3D = url != null && (
                        url.endsWith(".glb", ignoreCase = true) ||
                        url.endsWith(".gltf", ignoreCase = true) ||
                        url.endsWith(".vrm", ignoreCase = true) ||
                        url.startsWith("models/")
                    )

                    if (is3D) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF7C3AED), Color(0xFF1E1035))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✨ 3D", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    } else {
                        val parsedUrl = com.aman.gigi.utils.ImageUtils.parseEmojiModel(url)
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context).data(parsedUrl).crossfade(true).build(),
                            imageLoader = imageLoader,
                            contentDescription = label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                    }
                    
                    if (p != null) {
                        val dotSize = (sizeDp * 0.14f).coerceIn(8.dp, 12.dp)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(1.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color(0xFF22C55E) else Color(0xFF94A3B8))
                        )
                    }
                }
                
                val labelY = item.py + scaledR + (4f * zoom)
                val labelWidthDp = 220.dp
                val labelWidthPx = with(density) { labelWidthDp.roundToPx() }
                val fontSizeSp = (10.5f * zoom).coerceIn(7f, 15f).sp
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = fontSizeSp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .offset { IntOffset(item.px.roundToInt() - labelWidthPx / 2, labelY.roundToInt()) }
                        .width(labelWidthDp),
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.85f),
                            offset = Offset(0f, 2f),
                            blurRadius = 6f
                        )
                    )
                )

                // ♫ Now Playing — a soft pill under the name when a connection is listening to something.
                val np = if (p != null) nowPlaying[p.id.lowercase()] else null
                if (np != null && np.isPlaying) {
                    val npWidthDp = 200.dp
                    val npWidthPx = with(density) { npWidthDp.roundToPx() }
                    val npY = labelY + with(density) { (fontSizeSp.value + 6f).dp.toPx() }
                    Row(
                        modifier = Modifier
                            .offset { IntOffset(item.px.roundToInt() - npWidthPx / 2, npY.roundToInt()) }
                            .width(npWidthDp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { onNowPlayingClick(np) },
                            shape = CircleShape,
                            color = Color(0xFF1DB954).copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "♫ ${np.label}",
                                color = Color(0xFFD9FFE6),
                                fontSize = (fontSizeSp.value * 0.78f).coerceIn(6f, 11f).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 💬 Sweet quote — a soft cloud above the planet when a quote was just
                // exchanged with this person (either direction). Fades after ~30s.
                val quoteText = if (p != null) quotes[p.id.lowercase()] else null
                if (!quoteText.isNullOrBlank()) {
                    val qWidthDp = 190.dp
                    val qWidthPx = with(density) { qWidthDp.roundToPx() }
                    val qY = item.py - scaledR - with(density) { 34.dp.toPx() }
                    Row(
                        modifier = Modifier
                            .offset { IntOffset(item.px.roundToInt() - qWidthPx / 2, qY.roundToInt()) }
                            .width(qWidthDp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.92f),
                            shadowElevation = 6.dp
                        ) {
                            Text(
                                text = "“$quoteText”",
                                color = Color(0xFF3B2A6B),
                                fontSize = (fontSizeSp.value * 0.8f).coerceIn(7f, 12f).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Render Moons for this item if it's a planet
                p?.moons?.forEach { m ->
                    val moonUrl = m.emojiUrl ?: com.aman.gigi.ui.components.TELEGRAM_EMOJIS[0] // fallback emoji
                    val mRad = item.r * 0.35f
                    // Moon position relative to planet, considering camera tilt
                    val mx = item.px + item.r * m.radius * cos(m.angle)
                    val my = item.py + item.r * m.radius * tilt * sin(m.angle)
                    val mSizeDp = with(density) { (mRad * 2).toDp() }
                    
                    Box(modifier = Modifier
                        .offset { IntOffset(mx.roundToInt() - mRad.roundToInt(), my.roundToInt() - mRad.roundToInt()) }
                        .size(mSizeDp)
                    ) {
                        val parsedMoonUrl = com.aman.gigi.utils.ImageUtils.parseEmojiModel(moonUrl)
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context).data(parsedMoonUrl).crossfade(true).build(),
                            imageLoader = imageLoader,
                            contentDescription = "Group Member",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(alpha=0.1f)).padding(2.dp)
                        )
                    }
                }
            }
        }

        // Recenter button — fly back to your solar system when exploring far.
        AnimatedVisibility(
            visible = panX != 0f || panY != 0f,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
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

