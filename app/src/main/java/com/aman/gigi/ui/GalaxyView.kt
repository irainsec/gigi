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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlin.math.pow
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

private data class NebulaCloudPuff(
    val relX: Float,
    val relY: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float,
    val driftMul: Float,
    val pulsePhase: Float
)

private data class NebulaStar(
    val relX: Float,
    val relY: Float,
    val size: Float,
    val color: Color,
    val tier: Int, // 0 = micro, 1 = medium, 2 = bright, 3 = core giant
    val phase: Float,
    val speed: Float
)

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

private class MoteShootingStar(
    var startX: Float,
    var startY: Float,
    var targetX: Float,
    var targetY: Float,
    var progress: Float = 0f,
    val avatarUrl: String?,
    val name: String
)

/** Smooth wandering harmonic drift for public users roaming freely across the Cosmic Nebula. */
private fun computeNebulaMoteOffset(seed: Int, timeSec: Float, minDim: Float, zoom: Float): Offset {
    val s = abs(seed)
    val speed = 0.045f + (s % 5) * 0.012f
    val phaseA = (s % 360) * (3.14159f / 180f)
    val phaseB = ((s / 10) % 360) * (3.14159f / 180f)
    
    val baseSpanX = minDim * (0.34f + (s % 7) * 0.08f) * zoom
    val baseSpanY = minDim * (0.22f + (s % 6) * 0.07f) * zoom
    
    val x = cos(timeSec * speed + phaseA) * baseSpanX + sin(timeSec * speed * 0.6f + phaseB) * (baseSpanX * 0.35f)
    val y = sin(timeSec * speed * 0.85f + phaseB) * baseSpanY + cos(timeSec * speed * 0.45f + phaseA) * (baseSpanY * 0.3f)
    return Offset(x, y)
}

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
    nebulaMotes: List<com.aman.gigi.model.NebulaMember> = emptyList(),
    incomingInvites: List<com.aman.gigi.model.IncomingNebulaInvite> = emptyList(),
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    pendingGhostInvites: Set<String> = emptySet(),
    onInviteMote: (com.aman.gigi.model.NebulaMember) -> Unit = {},
    onAcceptInvite: (com.aman.gigi.model.IncomingNebulaInvite) -> Unit = {},
    onDeclineInvite: (com.aman.gigi.model.IncomingNebulaInvite) -> Unit = {},
    onBlockMote: (String) -> Unit = {},
    onReportMote: (String, String, String?) -> Unit = { _, _, _ -> },
    camera: GalaxyCamera,
    onOpenConnection: (String) -> Unit,
    onSunClick: () -> Unit,
    onOpenMemories: () -> Unit = {},
    onNowPlayingClick: (com.aman.gigi.data.nowplaying.NowPlaying) -> Unit = {},
    /** Shown as the call-to-action when the galaxy has no planets yet. */
    onInvite: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = remember {
        coil.ImageLoader.Builder(context).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }.build()
    }
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

    var selectedMote by remember { mutableStateOf<com.aman.gigi.model.NebulaMember?>(null) }
    var reportMoteTarget by remember { mutableStateOf<com.aman.gigi.model.NebulaMember?>(null) }
    var reportReason by remember { mutableStateOf("INAPPROPRIATE") }

    val curMote = selectedMote
    if (curMote != null) {
        val isSent = curMote.inviteStatus == "SENT" || pendingGhostInvites.contains(curMote.memberId)
        val moteAvatar = curMote.twigiRenderUrl?.takeIf { it.isNotBlank() }
            ?: curMote.avatarUrl?.takeIf { it.isNotBlank() }
            ?: curMote.profileEmojiUrl?.takeIf { it.isNotBlank() }
            ?: com.aman.gigi.ui.components.TELEGRAM_EMOJIS.first()

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { selectedMote = null }
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF160D2E),
                border = BorderStroke(1.5.dp, Color(0xFFC084FC).copy(alpha = 0.6f)),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF3B1D6B)
                        ) {
                            Text(
                                "✨ Cosmic Mote",
                                color = Color(0xFFE9D5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        androidx.compose.material3.IconButton(
                            onClick = { selectedMote = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("✕", color = Color(0xFFC084FC), fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFFEC4899), Color(0xFF7C3AED), Color.Transparent)
                                )
                            )
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF241442)),
                        contentAlignment = Alignment.Center
                    ) {
                        val parsedMoteAvatar = com.aman.gigi.utils.ImageUtils.parseEmojiModel(moteAvatar)
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(parsedMoteAvatar).crossfade(true).build(),
                            contentDescription = curMote.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = curMote.displayName.ifBlank { "Cosmic Soul" },
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "@${curMote.handle}",
                            color = Color(0xFFC084FC),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!curMote.bio.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF25184B),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "“${curMote.bio}”",
                                color = Color(0xFFF3E8FF),
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (curMote.isRecentlyActive) Color(0xFF22C55E) else Color(0xFFA855F7))
                        )
                        Text(
                            text = if (curMote.isRecentlyActive) "Active Recently in Nebula" else "Drifting in Nebula",
                            color = Color(0xFFD8B4FE),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    val isSelf = (curMote.memberId == identity?.memberId)

                    if (isSelf) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFFBBF24).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "🌟 Your Public Nebula Profile",
                                color = Color(0xFFFDE68A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }

                        androidx.compose.material3.Button(
                            onClick = {
                                selectedMote = null
                                onSunClick()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B5CF6)
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                text = "✏️ Edit Cosmic Profile",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        androidx.compose.material3.Button(
                            onClick = {
                                if (!isSent) {
                                    onInviteMote(curMote)
                                    selectedMote = null
                                }
                            },
                            enabled = !isSent,
                            shape = RoundedCornerShape(14.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (isSent) Color(0xFF4C1D95) else Color(0xFFEC4899),
                                disabledContainerColor = Color(0xFF3B1D6B)
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                text = if (isSent) "💌 Invitation Sent" else "✨ Invite to My Galaxy",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    onBlockMote(curMote.memberId)
                                    selectedMote = null
                                }
                            ) {
                                Text("🚫 Block", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    reportMoteTarget = curMote
                                    selectedMote = null
                                }
                            ) {
                                Text("⚠️ Report", color = Color(0xFFF87171), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    val repTarget = reportMoteTarget
    if (repTarget != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { reportMoteTarget = null },
            title = { androidx.compose.material3.Text("Report @${repTarget.handle}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Text("Select a reason:", fontSize = 13.sp)
                    listOf("INAPPROPRIATE" to "Inappropriate content / bio", "HARASSMENT" to "Harassment / Spam", "IMPERSONATION" to "Impersonation", "OTHER" to "Other").forEach { (code, lbl) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportReason = code }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = reportReason == code,
                                onClick = { reportReason = code }
                            )
                            Spacer(Modifier.width(6.dp))
                            androidx.compose.material3.Text(lbl, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onReportMote(repTarget.memberId, reportReason, null)
                        reportMoteTarget = null
                    }
                ) {
                    androidx.compose.material3.Text("Submit Report", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { reportMoteTarget = null }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    var selectedIncomingInvite by remember { mutableStateOf<com.aman.gigi.model.IncomingNebulaInvite?>(null) }
    val curInvite = selectedIncomingInvite
    if (curInvite != null) {
        val inviteAvatar = if (curInvite.avatarMode == "TWIGI" && !curInvite.twigiRenderUrl.isNullOrBlank()) {
            curInvite.twigiRenderUrl
        } else {
            curInvite.profileEmojiUrl?.takeIf { it.isNotBlank() }
                ?: curInvite.avatarUrl?.takeIf { it.isNotBlank() }
                ?: com.aman.gigi.ui.components.TELEGRAM_EMOJIS.first()
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { selectedIncomingInvite = null }
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF160D2E),
                border = BorderStroke(1.5.dp, Color(0xFFEC4899).copy(alpha = 0.7f)),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFFEC4899).copy(alpha = 0.85f),
                                        Color(0xFFC084FC).copy(alpha = 0.45f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val parsed = com.aman.gigi.utils.ImageUtils.parseEmojiModel(inviteAvatar)
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(parsed).crossfade(true).build(),
                            imageLoader = imageLoader,
                            contentDescription = curInvite.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F0728))
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = curInvite.displayName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${curInvite.handle.replace("@", "")}",
                            color = Color(0xFFF472B6),
                            fontSize = 12.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1F123B),
                        border = BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = curInvite.bio?.takeIf { it.isNotBlank() } ?: "Sent you a galaxy invitation! ✨",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                onDeclineInvite(curInvite)
                                selectedIncomingInvite = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.7f)),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("✕ Decline", color = Color(0xFFEF4444), fontSize = 13.sp)
                        }

                        androidx.compose.material3.Button(
                            onClick = {
                                onAcceptInvite(curInvite)
                                selectedIncomingInvite = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("💖 Accept", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
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

    val allNebulaMotes = remember(nebulaMotes, identity, emojiSelfPref) {
        if (identity?.discoverable == true) {
            val myId = identity.memberId.takeIf { it.isNotBlank() } ?: "self_user"
            val alreadyHasMe = nebulaMotes.any { it.memberId == myId }
            if (!alreadyHasMe) {
                val myEmoji = emojiSelfPref?.takeIf { it.isNotBlank() }
                    ?: identity.profileEmojiUrl?.takeIf { it.isNotBlank() }
                    ?: sunEmojiUrl
                val myMote = com.aman.gigi.model.NebulaMember(
                    memberId = myId,
                    handle = identity.handle ?: "you",
                    displayName = identity.displayName.takeIf { !it.isNullOrBlank() } ?: "You",
                    avatarUrl = identity.avatarUrl,
                    twigiRenderUrl = if (identity.avatarMode == "TWIGI") identity.twigiRenderUrl else null,
                    profileEmojiUrl = myEmoji,
                    avatarMode = identity.avatarMode ?: "EMOJI",
                    bio = identity.bio ?: "Exploring the cosmic realm ✨",
                    nebulaSeed = myId.hashCode(),
                    isRecentlyActive = true,
                    inviteStatus = "SELF"
                )
                listOf(myMote) + nebulaMotes
            } else {
                nebulaMotes
            }
        } else {
            nebulaMotes
        }
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

    // Procedural Multi-Octave FBM Volumetric Nebula Cloud Puffs (Stable across frames)
    val fbmNebulaPuffs = remember {
        val rnd = java.util.Random(2026)
        val puffs = ArrayList<NebulaCloudPuff>(120)

        // 1. North Electric Cyan/Sapphire Reflection Cloud (Upper lobe, center: x = -40f, y = -360f)
        val blueColors = listOf(
            Color(0xFF0A1931), // Deep space sapphire
            Color(0xFF0369A1), // Deep ocean blue
            Color(0xFF0284C7), // Intense cerulean
            Color(0xFF06B6D4), // Electric cyan
            Color(0xFF38BDF8), // Radiant sky blue
            Color(0xFF22D3EE), // Luminous turquoise
            Color(0xFFE0F2FE), // Ionized white-blue stellar core
            Color(0xFFFFFFFF)  // Pure light center
        )
        repeat(55) {
            val u = rnd.nextFloat()
            val ang = rnd.nextFloat() * 6.28318f
            val oct1 = (u.pow(0.72f)) * 540f
            val oct2 = (sin(ang * 3.3f) * 70f)
            val oct3 = (cos(ang * 5.7f) * 35f)
            val dist = (oct1 + oct2 + oct3).coerceAtLeast(0f)
            val px = -40f + dist * cos(ang) * 1.15f
            val py = -360f + dist * sin(ang) * 0.92f
            val radius = (190f + rnd.nextFloat() * 340f) * (1f - (dist / 680f).coerceIn(0f, 0.55f))
            val colorIdx = ((1f - (dist / 620f).coerceIn(0f, 1f)) * (blueColors.size - 1)).roundToInt()
            val col = blueColors[colorIdx]
            val alpha = (0.07f + rnd.nextFloat() * 0.26f) * (1f - (dist / 680f).coerceIn(0f, 0.65f))
            puffs.add(NebulaCloudPuff(px, py, radius, col, alpha, 0.5f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 6.28f))
        }

        // 2. South Hydrogen-Alpha Rose/Magenta/Crimson Emission Cloud (Lower lobe, center: x = +30f, y = +160f)
        val pinkColors = listOf(
            Color(0xFF2E021B), // Deep space boundary
            Color(0xFF4C0519), // Deep ruby wine
            Color(0xFF881337), // Crimson
            Color(0xFFBE123C), // Rose red
            Color(0xFFE11D48), // Radiant magenta
            Color(0xFFF43F5E), // Vibrant neon rose
            Color(0xFFFB7185), // Soft coral
            Color(0xFFFDA4AF), // Peach-pink
            Color(0xFFFFE4E6), // Ionized glowing peach
            Color(0xFFFFFFFF)  // Blazing central stellar core
        )
        repeat(65) {
            val u = rnd.nextFloat()
            val ang = rnd.nextFloat() * 6.28318f
            val oct1 = (u.pow(0.68f)) * 650f
            val oct2 = (sin(ang * 2.7f + 1.2f) * 85f)
            val oct3 = (cos(ang * 6.1f) * 45f)
            val dist = (oct1 + oct2 + oct3).coerceAtLeast(0f)
            val px = 30f + dist * cos(ang) * 1.22f
            val py = 160f + dist * sin(ang) * 0.95f
            val radius = (210f + rnd.nextFloat() * 400f) * (1f - (dist / 780f).coerceIn(0f, 0.55f))
            val colorIdx = ((1f - (dist / 720f).coerceIn(0f, 1f)) * (pinkColors.size - 1)).roundToInt()
            val col = pinkColors[colorIdx]
            val alpha = (0.08f + rnd.nextFloat() * 0.30f) * (1f - (dist / 780f).coerceIn(0f, 0.65f))
            puffs.add(NebulaCloudPuff(px, py, radius, col, alpha, 0.6f + rnd.nextFloat() * 0.7f, rnd.nextFloat() * 6.28f))
        }

        // 3. Central Translucent Wisps linking the two lobes
        repeat(25) {
            val t = rnd.nextFloat()
            val px = -40f + (30f - (-40f)) * t + (rnd.nextFloat() - 0.5f) * 240f
            val py = -360f + (160f - (-360f)) * t + (rnd.nextFloat() - 0.5f) * 200f
            val radius = 220f + rnd.nextFloat() * 280f
            val col = if (rnd.nextBoolean()) Color(0xFF818CF8) else Color(0xFFC084FC)
            puffs.add(NebulaCloudPuff(px, py, radius, col, 0.07f + rnd.nextFloat() * 0.12f, 0.4f + rnd.nextFloat() * 0.5f, rnd.nextFloat() * 6.28f))
        }

        puffs
    }

    // Natural Star Population for the Cosmic Nebula
    val fbmNebulaStars = remember {
        val rnd = java.util.Random(777)
        val starsList = ArrayList<NebulaStar>(190)
        // 150 Tiny background stars
        repeat(150) {
            val ang = rnd.nextFloat() * 6.28318f
            val dist = (rnd.nextFloat().pow(0.6f)) * 1100f
            val px = dist * cos(ang)
            val py = (dist * sin(ang) * 0.95f) - 100f
            val sz = 0.8f + rnd.nextFloat() * 1.6f
            val col = when (rnd.nextInt(5)) {
                0 -> Color(0xFFBAE6FD)
                1 -> Color(0xFFFFFFFF)
                2 -> Color(0xFFFEF08A)
                3 -> Color(0xFFFECDD3)
                else -> Color(0xFFDDD6FE)
            }
            starsList.add(NebulaStar(px, py, sz, col, 0, rnd.nextFloat() * 6.28f, 1f + rnd.nextFloat() * 2f))
        }
        // 35 Medium diamond stars with soft halos
        repeat(35) {
            val ang = rnd.nextFloat() * 6.28318f
            val dist = (rnd.nextFloat().pow(0.7f)) * 900f
            val px = dist * cos(ang)
            val py = (dist * sin(ang) * 0.95f) - 100f
            val sz = 2.4f + rnd.nextFloat() * 2.0f
            val col = when (rnd.nextInt(4)) {
                0 -> Color(0xFF38BDF8)
                1 -> Color(0xFFFFFFFF)
                2 -> Color(0xFFFBBF24)
                else -> Color(0xFFF472B6)
            }
            starsList.add(NebulaStar(px, py, sz, col, 1, rnd.nextFloat() * 6.28f, 0.8f + rnd.nextFloat() * 1.5f))
        }
        // 4 Bright internal illuminating stars inside the gas clouds
        starsList.add(NebulaStar(-40f, -360f, 6.5f, Color(0xFFBAE6FD), 2, 0f, 0.5f))
        starsList.add(NebulaStar(-120f, -220f, 4.5f, Color(0xFFE0F2FE), 2, 1.2f, 0.7f))
        starsList.add(NebulaStar(30f, 160f, 8.5f, Color(0xFFFFE4E6), 3, 0.5f, 0.4f)) // Central rose giant
        starsList.add(NebulaStar(120f, 260f, 5.0f, Color(0xFFFDA4AF), 2, 2.1f, 0.6f))
        starsList
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

    val moteShootingStars = remember { mutableListOf<MoteShootingStar>() }
    var draggingMote by remember { mutableStateOf<com.aman.gigi.model.NebulaMember?>(null) }
    var dragMotePos by remember { mutableStateOf(Offset.Zero) }

    fun orbitRadiusPx(i: Int, minDim: Float) = minDim * (0.17f + i * 0.115f) * zoom

    Box(modifier = modifier) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(planets, nebulaMotes) {
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

                // Hit-test Nebula public motes
                var hitMote: com.aman.gigi.model.NebulaMember? = null
                var hitMoteD = Float.MAX_VALUE
                val isNebulaEnabled = !com.aman.gigi.utils.AppConfig.settings.killCosmicNebula
                if (!hitSun && hit == null && isNebulaEnabled && allNebulaMotes.isNotEmpty()) {
                    val nebulaCenterY = pcy + 1800f * zoom + 150f * zoom
                    val timeSec = frame / 1_000_000_000f
                    allNebulaMotes.forEach { m ->
                        val offset = computeNebulaMoteOffset(m.nebulaSeed, timeSec, minDim, zoom)
                        val mx = pcx + offset.x
                        val my = nebulaCenterY + offset.y
                        val rad = pr * 1.6f + 36f
                        val d = hypot(down.position.x - mx, down.position.y - my)
                        if (d < rad && d < hitMoteD) {
                            hitMoteD = d
                            hitMote = m
                        }
                    }
                }

                if (hit != null) draggingId = hit!!.id
                if (hitMote != null) {
                    draggingMote = hitMote
                    dragMotePos = down.position
                }
                var totalMove = 0f
                var prevPinch = -1f

                // Arm long-press for this planet / the sun (timer runs in composition).
                longPressFired = false
                pressTarget = hit?.id ?: if (hitSun) "sun" else null

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2 && hit == null && !hitSun && draggingMote == null) {
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
                        val hm = draggingMote
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
                        } else if (hm != null) {
                            dragMotePos = ch.position
                        } else if (!hitSun) {
                            // Drag to explore — pan the camera across the vast galaxy & nebula.
                            val limX = minDim * 4f
                            val limY = minDim * 6f
                            panX = (panX + delta.x).coerceIn(-limX, limX)
                            panY = (panY + delta.y).coerceIn(-limY, limY)
                            camera.panX = panX; camera.panY = panY
                        }
                        pressed.forEach { it.consume() }
                    }
                }

                pressTarget = null            // finger up → disarm the hold timer
                val hp = hit
                val hm = draggingMote
                if (hp != null) {
                    if (longPressFired) Unit          // the customizer opened; don't also open the chat
                    else if (totalMove < 16f) onOpenConnection(hp.id)
                    else prefs.edit().putInt(hp.id, hp.orbit).apply()
                    draggingId = null
                } else if (hm != null) {
                    if (totalMove < 16f) {
                        selectedMote = hm
                        val isSelf = (hm.memberId == identity?.memberId || hm.inviteStatus == "SELF")
                        val isInsideGalaxy = dragMotePos.y < (pcy + 900f * zoom)
                        if (isInsideGalaxy && !isSelf) {
                            val ux = (dragMotePos.x - pcx) / zoom
                            val uy = (dragMotePos.y - pcy) / (zoom * tilt)
                            val dist = hypot(ux, uy)
                            var bestOrbit = 3
                            var bd = Float.MAX_VALUE
                            for (oi in 0 until ORBIT_COUNT) {
                                val rr = (0.17f + oi * 0.115f) * minDim
                                val dd = abs(rr - dist)
                                if (dd < bd) { bd = dd; bestOrbit = oi }
                            }

                            val rndAng = (rnd.nextFloat() * 6.2832f)
                            val targetR = orbitRadiusPx(bestOrbit, minDim)
                            val targetX = pcx + targetR * cos(rndAng)
                            val targetY = pcy + targetR * tilt * sin(rndAng)
                            val avatar = if (hm.avatarMode == "TWIGI" && !hm.twigiRenderUrl.isNullOrBlank()) {
                                hm.twigiRenderUrl
                            } else {
                                hm.profileEmojiUrl?.takeIf { it.isNotBlank() } ?: hm.avatarUrl
                            }

                            moteShootingStars.add(
                                MoteShootingStar(
                                    startX = dragMotePos.x,
                                    startY = dragMotePos.y,
                                    targetX = targetX,
                                    targetY = targetY,
                                    avatarUrl = avatar,
                                    name = hm.displayName
                                )
                            )
                            onInviteMote(hm)
                        }
                    }
                    draggingMote = null
                } else if (hitSun) {
                    if (!longPressFired && totalMove < 16f) onSunClick()
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
        val pr = minDim * 0.052f * zoom
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

        // ── COSMIC NEBULA REGION (GPU Procedural Multi-Octave FBM Cloud & Starfield) ──
        val isNebulaEnabled = !com.aman.gigi.utils.AppConfig.settings.killCosmicNebula
        if (isNebulaEnabled && zoom > 0.01f) {
            val nebulaScreenX = pcx
            val nebulaScreenY = pcy + 1800f * zoom + 150f * zoom

            val tSec = frame / 1_000_000_000f

            // 1. Deep Space Ambient Space Glow
            val glowRadius = (1350f * zoom).coerceAtLeast(1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1E1035).copy(alpha = 0.55f), Color(0xFF0A0416).copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(nebulaScreenX, nebulaScreenY - 100f * zoom),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(nebulaScreenX, nebulaScreenY - 100f * zoom)
            )

            // 2. Procedural FBM Volumetric Gas Clouds (Safe Alpha Blending)
            fbmNebulaPuffs.forEach { puff ->
                val driftX = sin(tSec * 0.18f * puff.driftMul + puff.pulsePhase) * 18f * zoom
                val driftY = cos(tSec * 0.15f * puff.driftMul + puff.pulsePhase) * 14f * zoom
                val pulse = 0.88f + 0.12f * sin(tSec * 0.4f * puff.driftMul + puff.pulsePhase)
                val center = Offset(nebulaScreenX + (puff.relX * zoom) + driftX, nebulaScreenY + (puff.relY * zoom) + driftY)
                val r = (puff.radius * zoom * pulse).coerceAtLeast(1f)
                val alpha = (puff.alpha * pulse).coerceIn(0f, 1f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(puff.color.copy(alpha = alpha), puff.color.copy(alpha = alpha * 0.35f), Color.Transparent),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )
            }

            // 3. Multi-Tier Star Population (Tiny, Medium, Bright, and Core Giants with Cross Spikes)
            fbmNebulaStars.forEach { s ->
                val sx = nebulaScreenX + s.relX * zoom
                val sy = nebulaScreenY + s.relY * zoom
                val twinkle = (0.65f + 0.35f * sin(tSec * s.speed + s.phase)).coerceIn(0f, 1f)
                val r = (s.size * zoom).coerceAtLeast(0.5f)

                when (s.tier) {
                    0 -> { // Tiny background pinprick
                        drawCircle(s.color.copy(alpha = 0.85f * twinkle), r, Offset(sx, sy))
                    }
                    1 -> { // Medium star with glowing halo
                        val haloR = (r * 4.5f).coerceAtLeast(1f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(s.color.copy(alpha = 0.45f * twinkle), Color.Transparent),
                                center = Offset(sx, sy),
                                radius = haloR
                            ),
                            radius = haloR,
                            center = Offset(sx, sy)
                        )
                        drawCircle(s.color.copy(alpha = twinkle), r, Offset(sx, sy))
                        drawCircle(Color.White.copy(alpha = twinkle), (r * 0.5f).coerceAtLeast(0.5f), Offset(sx, sy))
                    }
                    2, 3 -> { // Massive Core Giants with 4-point cross diffraction spikes
                        // Large luminous halo
                        val haloR = (r * 7f).coerceAtLeast(1f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(s.color.copy(alpha = 0.65f * twinkle), s.color.copy(alpha = 0.2f * twinkle), Color.Transparent),
                                center = Offset(sx, sy),
                                radius = haloR
                            ),
                            radius = haloR,
                            center = Offset(sx, sy)
                        )
                        // Core diamond
                        drawCircle(s.color.copy(alpha = twinkle), r, Offset(sx, sy))
                        drawCircle(Color.White, (r * 0.6f).coerceAtLeast(0.5f), Offset(sx, sy))

                        // Cross diffraction spikes
                        val spikeLen = (if (s.tier == 3) 58f else 38f) * zoom * twinkle
                        val spikeW = (if (s.tier == 3) 2.0f else 1.5f) * zoom
                        drawLine(Color.White.copy(alpha = 0.9f * twinkle), Offset(sx - spikeLen, sy), Offset(sx + spikeLen, sy), strokeWidth = spikeW)
                        drawLine(Color.White.copy(alpha = 0.9f * twinkle), Offset(sx, sy - spikeLen), Offset(sx, sy + spikeLen), strokeWidth = spikeW)
                        // Diagonal flare
                        val diagLen = spikeLen * 0.45f
                        drawLine(s.color.copy(alpha = 0.55f * twinkle), Offset(sx - diagLen, sy - diagLen), Offset(sx + diagLen, sy + diagLen), strokeWidth = 1.0f * zoom)
                        drawLine(s.color.copy(alpha = 0.55f * twinkle), Offset(sx - diagLen, sy + diagLen), Offset(sx + diagLen, sy - diagLen), strokeWidth = 1.0f * zoom)
                    }
                }
            }

            // 4. Luminous Header Banner at the top of Cosmic Nebula
            label(
                "🌌 THE COSMIC NEBULA",
                nebulaScreenX, nebulaScreenY - 490f * zoom,
                minDim * 0.046f,
                color = android.graphics.Color.argb(255, 253, 164, 175)
            )
            label(
                "Drifting in open space ✨",
                nebulaScreenX, nebulaScreenY - 445f * zoom,
                minDim * 0.024f,
                color = android.graphics.Color.argb(210, 216, 180, 254)
            )
        }

        // Header Banner for My Galaxy (Matches Cosmic Nebula aesthetics)
        label(
            "✨ MY GALAXY",
            pcx, pcy - 480f * zoom,
            minDim * 0.046f,
            color = android.graphics.Color.argb(255, 253, 230, 138)
        )
        label(
            "Sanctuary of Hearts 💖",
            pcx, pcy - 435f * zoom,
            minDim * 0.024f,
            color = android.graphics.Color.argb(210, 244, 114, 182)
        )

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

        // Constellations: faint lines from a group's planet to each of its moons
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

        // Faraway Orbit Ring: Ghost Planets (Pending Invites)
        if (pendingGhostInvites.isNotEmpty()) {
            val farR = orbitRadiusPx(3, minDim)
            pendingGhostInvites.forEachIndexed { idx, _ ->
                val gAngle = (frame / 15_000_000_000f) + (idx * 6.2832f / pendingGhostInvites.size)
                val gx = pcx + farR * cos(gAngle + rotY)
                val gy = pcy + farR * tilt * sin(gAngle + rotY)
                drawCircle(
                    color = Color(0xFFEC4899).copy(alpha = 0.25f),
                    radius = pr * 1.3f,
                    center = Offset(gx, gy)
                )
                drawCircle(
                    color = Color(0xFFC084FC).copy(alpha = 0.8f),
                    radius = pr * 1.3f,
                    center = Offset(gx, gy),
                    style = Stroke(width = 2.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                )
                label("Invited ✨", gx, gy + pr * 1.8f, minDim * 0.022f, color = android.graphics.Color.argb(220, 255, 180, 230))
            }
        }

        // Mote Shooting Stars (Flying starlight arc when invited)
        val msIt = moteShootingStars.iterator()
        while (msIt.hasNext()) {
            val ms = msIt.next()
            val t = ms.progress
            val arcX = ms.startX + (ms.targetX - ms.startX) * t
            val arcHeight = -250f * zoom * sin(t * 3.14159f)
            val arcY = ms.startY + (ms.targetY - ms.startY) * t + arcHeight

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFEC4899), Color(0xFF7C3AED), Color.Transparent),
                    center = Offset(arcX, arcY),
                    radius = 24f * zoom
                ),
                radius = 24f * zoom,
                center = Offset(arcX, arcY)
            )
            drawCircle(Color.White, 5f * zoom, Offset(arcX, arcY))

            ms.progress += 0.025f
            if (ms.progress >= 1f) {
                // Spark burst on arrival
                drawCircle(Color(0xFFFFD6F2), 35f * zoom, Offset(ms.targetX, ms.targetY), style = Stroke(width = 3f))
                msIt.remove()
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

                // ♫ Now Playing
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

                // 💬 Sweet quote
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
                    val moonUrl = m.emojiUrl ?: com.aman.gigi.ui.components.TELEGRAM_EMOJIS[0]
                    val mRad = item.r * 0.35f
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

            // ── Overlay Public Motes in the Cosmic Nebula ──
            val isNebulaOverlayEnabled = !com.aman.gigi.utils.AppConfig.settings.killCosmicNebula

            if (isNebulaOverlayEnabled && allNebulaMotes.isNotEmpty()) {
                val nebulaCenterY = pcy + 1800f * zoom + 150f * zoom
                val timeSec = currentFrame / 1_000_000_000f
                allNebulaMotes.forEach { mote ->
                    val isSelf = (mote.memberId == identity?.memberId)
                    val offset = computeNebulaMoteOffset(mote.nebulaSeed, timeSec, minDim, zoom)

                    val isMatch = searchQuery.isBlank() ||
                        mote.displayName.contains(searchQuery, ignoreCase = true) ||
                        mote.handle.contains(searchQuery, ignoreCase = true) ||
                        (mote.bio?.contains(searchQuery, ignoreCase = true) == true)

                    val moteAlpha = if (isMatch) 1.0f else 0.18f
                    val isDraggingThis = draggingMote?.memberId == mote.memberId
                    val moteScale = if (isDraggingThis) 1.35f else if (searchQuery.isNotBlank() && isMatch) 1.25f else if (isSelf) 1.15f else 1.0f

                    val mx = if (isDraggingThis) dragMotePos.x else pcx + offset.x
                    val my = if (isDraggingThis) dragMotePos.y else nebulaCenterY + offset.y

                    val moteRad = pr * 1.35f * moteScale
                    val moteSizeDp = with(density) { (moteRad * 2).toDp() }

                    val isSent = mote.inviteStatus == "SENT" || pendingGhostInvites.contains(mote.memberId)
                    val moteAvatar = if (mote.avatarMode == "TWIGI" && !mote.twigiRenderUrl.isNullOrBlank()) {
                        mote.twigiRenderUrl
                    } else {
                        mote.profileEmojiUrl?.takeIf { it.isNotBlank() }
                            ?: mote.avatarUrl?.takeIf { it.isNotBlank() }
                            ?: if (isSelf) sunEmojiUrl else com.aman.gigi.ui.components.TELEGRAM_EMOJIS.first()
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(mx.roundToInt() - moteRad.roundToInt(), my.roundToInt() - moteRad.roundToInt()) }
                            .size(moteSizeDp)
                    ) {
                        // Glowing Halo
                        val haloColors = when {
                            isDraggingThis -> listOf(Color(0xFFF472B6).copy(alpha = 0.9f), Color(0xFFC084FC).copy(alpha = 0.6f), Color.Transparent)
                            isSelf -> listOf(Color(0xFFFBBF24).copy(alpha = 0.75f * moteAlpha), Color(0xFFC084FC).copy(alpha = 0.35f * moteAlpha), Color.Transparent)
                            isSent -> listOf(Color(0xFFC084FC).copy(alpha = 0.6f * moteAlpha), Color.Transparent)
                            else -> listOf(Color(0xFFEC4899).copy(alpha = 0.6f * moteAlpha), Color.Transparent)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Brush.radialGradient(haloColors))
                        )

                        // Avatar image inside bubble
                        val parsedUrl = com.aman.gigi.utils.ImageUtils.parseEmojiModel(moteAvatar)
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(parsedUrl).crossfade(true).build(),
                            imageLoader = imageLoader,
                            contentDescription = mote.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1035).copy(alpha = 0.85f * moteAlpha))
                        )

                        // Active Indicator for others
                        if (!isSelf && mote.isRecentlyActive) {
                            val dotSize = (moteSizeDp * 0.16f).coerceIn(7.dp, 11.dp)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(dotSize)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(1.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                        }
                    }

                    // Mote Label (Name + @handle)
                    val mLabelY = my + moteRad + (3f * zoom)
                    val mLabelWidthDp = 180.dp
                    val mLabelWidthPx = with(density) { mLabelWidthDp.roundToPx() }
                    val mFontSizeSp = (9.5f * zoom).coerceIn(6.5f, 13f).sp

                    Column(
                        modifier = Modifier
                            .offset { IntOffset(mx.roundToInt() - mLabelWidthPx / 2, mLabelY.roundToInt()) }
                            .width(mLabelWidthDp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isSelf) "✨ You" else mote.displayName.ifBlank { "@${mote.handle}" },
                            color = if (isSelf) Color(0xFFFDE68A) else Color.White.copy(alpha = moteAlpha),
                            fontSize = mFontSizeSp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 6f
                                )
                            )
                        )
                        Text(
                            text = "@${mote.handle}",
                            color = if (isSelf) Color(0xFFFCD34D) else Color(0xFFC084FC).copy(alpha = 0.9f * moteAlpha),
                            fontSize = (mFontSizeSp.value * 0.82f).sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        if (isSent) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFF7C3AED).copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "💌 Sent",
                                    color = Color.White,
                                    fontSize = (mFontSizeSp.value * 0.72f).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Overlay Incoming Galaxy Invitations (Cosmic Visitors) ──
            if (incomingInvites.isNotEmpty()) {
                val visitorOrbitR = orbitRadiusPx(3, minDim) + with(density) { 48.dp.toPx() } * zoom
                incomingInvites.forEachIndexed { idx, invite ->
                    val angleOffset = (idx.toFloat() * (6.2831853f / incomingInvites.size.coerceAtLeast(1).toFloat())) + (currentFrame.toFloat() / 15_000_000_000f)
                    val vx = pcx + visitorOrbitR * kotlin.math.cos(angleOffset)
                    val vy = pcy + visitorOrbitR * tilt * kotlin.math.sin(angleOffset)
                    val vRad = pr * 1.4f
                    val vSizeDp = with(density) { (vRad * 2f).toDp() }

                    val avatar = if (invite.avatarMode == "TWIGI" && !invite.twigiRenderUrl.isNullOrBlank()) {
                        invite.twigiRenderUrl
                    } else {
                        invite.profileEmojiUrl?.takeIf { it.isNotBlank() } ?: invite.avatarUrl ?: com.aman.gigi.ui.components.TELEGRAM_EMOJIS.first()
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(vx.roundToInt() - vRad.roundToInt(), vy.roundToInt() - vRad.roundToInt()) }
                            .size(vSizeDp)
                            .clip(CircleShape)
                            .clickable { selectedIncomingInvite = invite }
                    ) {
                        // Pulsing Cosmic Visitor Halo
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFFEC4899).copy(alpha = 0.95f),
                                            Color(0xFFF472B6).copy(alpha = 0.55f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        val parsedUrl = com.aman.gigi.utils.ImageUtils.parseEmojiModel(avatar)
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(parsedUrl).crossfade(true).build(),
                            imageLoader = imageLoader,
                            contentDescription = invite.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1035))
                        )

                        // Notification Badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFEC4899))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("💖", fontSize = 8.sp)
                        }
                    }

                    // Label: "✨ Invite: Name"
                    val vLabelY = vy + vRad + (3f * zoom)
                    val vLabelWidthDp = 180.dp
                    val vLabelWidthPx = with(density) { vLabelWidthDp.roundToPx() }
                    Column(
                        modifier = Modifier
                            .offset { IntOffset(vx.roundToInt() - vLabelWidthPx / 2, vLabelY.roundToInt()) }
                            .width(vLabelWidthDp)
                            .clickable { selectedIncomingInvite = invite },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF1E1035).copy(alpha = 0.92f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("✨", fontSize = 10.sp)
                                Text(
                                    text = invite.displayName,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Navigation Controls (Segmented Switcher + Stacked Nebula Search Bar)
        val isViewingNebula = panY <= -900f

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 58.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!com.aman.gigi.utils.AppConfig.settings.killCosmicNebula) {
                // Segmented Realm Switcher [ 🏠 My Galaxy | 🌌 Nebula ]
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF1E1035).copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.45f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                panX = 0f; panY = 0f; camera.panX = 0f; camera.panY = 0f
                                prefs.edit().putFloat("g_panX", 0f).putFloat("g_panY", 0f).apply()
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = if (!isViewingNebula) Color(0xFF8B5CF6) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("🏠", fontSize = 13.sp)
                                Text("My Galaxy", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            onClick = {
                                panX = 0f; panY = -1800f; camera.panX = 0f; camera.panY = -1800f
                                prefs.edit().putFloat("g_panX", 0f).putFloat("g_panY", -1800f).apply()
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = if (isViewingNebula) Color(0xFFEC4899) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("🌌", fontSize = 13.sp)
                                Text("Nebula", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            onClick = { onOpenMemories() },
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("✨", fontSize = 13.sp)
                                Text("Memories", color = Color(0xFFFDE68A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Animated Nebula Search Bar - Appears ONLY when Nebula is selected/viewed, right below the switcher!
                AnimatedVisibility(
                    visible = isViewingNebula,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(0.92f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF1E1035).copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.55f)),
                        shadowElevation = 10.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔍", fontSize = 15.sp)
                            Spacer(Modifier.width(10.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text("Search Nebula by @handle or name...", color = Color(0xFFA78BFA), fontSize = 13.sp)
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFFC084FC),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Private Mode: Standard Recenter button
                AnimatedVisibility(
                    visible = panX != 0f || panY != 0f,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Surface(
                        onClick = {
                            panX = 0f; panY = 0f; camera.panX = 0f; camera.panY = 0f
                            prefs.edit().putFloat("g_panX", 0f).putFloat("g_panY", 0f).apply()
                        },
                        shape = CircleShape,
                        color = Color(0xFF8B5CF6).copy(alpha = 0.85f),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🏠", fontSize = 14.sp)
                            Text("Recenter", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
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

private fun DrawScope.label(
    text: String,
    x: Float,
    y: Float,
    sizePx: Float,
    color: Int = android.graphics.Color.WHITE,
    maxChars: Int = 0
) {
    val shown = if (maxChars > 0 && text.length > maxChars) text.take(maxChars - 1) + "…" else text
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            this.color = color
            textSize = sizePx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.argb(200, 0, 0, 0))
        }
        drawText(shown, x, y, paint)
    }
}

