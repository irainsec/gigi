package com.aman.gigi.ui.memories

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aman.gigi.model.Connection
import com.aman.gigi.model.MemberIdentity
import com.aman.gigi.model.Scribble
import com.aman.gigi.ui.components.TELEGRAM_EMOJIS
import com.aman.gigi.utils.ImageUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Cosmic Memories Space:
 * Full-screen in-tree overlay with instant zero-lag notch insets, rich celestial sanctuary,
 * magnetic drag-to-partner interaction, and living animated starlight timeline.
 */
@Composable
fun MemoriesSpaceScreen(
    identity: MemberIdentity?,
    connections: List<Connection>,
    selectedConnection: Connection?,
    sparkles: List<Scribble>,
    onSelectConnection: (Connection) -> Unit,
    onBack: () -> Unit,
    onReplayScribble: (String) -> Unit = {},
    onSendSparkle: (Connection) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // In-tree back handling
    BackHandler {
        if (selectedConnection != null && selectedConnection.connectionId.isNotBlank()) {
            onSelectConnection(selectedConnection.copy(connectionId = ""))
        } else {
            onBack()
        }
    }

    val imageLoader = remember {
        coil.ImageLoader.Builder(context).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }.build()
    }

    // Reactively track emoji_self directly from SharedPreferences (exact same as GalaxyView)
    val prefs = remember { context.getSharedPreferences("galaxy_orbits", Context.MODE_PRIVATE) }
    var emojiSelfPref by remember { mutableStateOf(prefs.getString("emoji_self", null)) }
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "emoji_self") emojiSelfPref = sp.getString("emoji_self", null)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val userAvatarUrl = if (identity?.avatarMode == "TWIGI" && !identity.twigiRenderUrl.isNullOrBlank()) {
        identity.twigiRenderUrl
    } else {
        emojiSelfPref?.takeIf { it.isNotBlank() }
            ?: identity?.profileEmojiUrl?.takeIf { it.isNotBlank() }
            ?: identity?.avatarUrl?.takeIf { it.isNotBlank() }
            ?: "file:///android_asset/galaxy/emoji/jack_o_lantern.png"
    }

    // Detail modal for viewing full-screen media lightbox
    var selectedMediaScribble by remember { mutableStateOf<Scribble?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFF1F0F3D),
                        Color(0xFF120726),
                        Color(0xFF070210)
                    )
                )
            )
    ) {
        // Ambient Cosmic Glowing Dust & Auroras
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFEC4899).copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.25f, h * 0.28f),
                    radius = w * 0.70f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF8B5CF6).copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(w * 0.75f, h * 0.68f),
                    radius = w * 0.75f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .displayCutoutPadding()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF281845).copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.45f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            if (selectedConnection != null && selectedConnection.connectionId.isNotBlank()) {
                                onSelectConnection(selectedConnection.copy(connectionId = ""))
                            } else {
                                onBack()
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (selectedConnection != null && selectedConnection.connectionId.isNotBlank()) {
                                Icons.AutoMirrored.Filled.ArrowBack
                            } else {
                                Icons.Default.Close
                            },
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Title Pill
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF1E1035).copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.45f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🌌", fontSize = 14.sp)
                        Text(
                            text = if (selectedConnection != null && selectedConnection.connectionId.isNotBlank()) {
                                "${selectedConnection.partnerName}'s Memories"
                            } else {
                                "Memories Space"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("✨", fontSize = 14.sp)
                    }
                }

                // Total count badge or balance spacer
                if (selectedConnection != null && selectedConnection.connectionId.isNotBlank()) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEC4899),
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${sparkles.size}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.size(40.dp))
                }
            }

            // Main Stage: Sanctuary Drag & Drop OR Animated Timeline View
            AnimatedContent(
                targetState = selectedConnection,
                transitionSpec = {
                    fadeIn(tween(350)) + scaleIn(tween(350, easing = EaseOutBack), initialScale = 0.92f) togetherWith
                            fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 1.05f)
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "memoriesStageTransition"
            ) { conn ->
                if (conn == null || conn.connectionId.isBlank()) {
                    // Stage 1: Rich Celestial Sanctuary Stage
                    MemoriesSanctuaryStage(
                        connections = connections,
                        userAvatarUrl = userAvatarUrl,
                        imageLoader = imageLoader,
                        onConnect = { targetConn ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSelectConnection(targetConn)
                        }
                    )
                } else {
                    // Stage 2: Living Animated Starlight Timeline
                    AnimatedMemoriesTimeline(
                        partner = conn,
                        userAvatarUrl = userAvatarUrl,
                        sparkles = sparkles,
                        imageLoader = imageLoader,
                        onReplayScribble = onReplayScribble,
                        onOpenMedia = { selectedMediaScribble = it },
                        onSendSparkle = { onSendSparkle(conn) }
                    )
                }
            }
        }

        // Lightbox Modal for Photo/Video Sparkle
        if (selectedMediaScribble != null) {
            val sc = selectedMediaScribble!!
            Dialog(
                onDismissRequest = { selectedMediaScribble = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f))
                        .clickable { selectedMediaScribble = null },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val resolvedMedia = sc.mediaUrl?.takeIf { it.isNotBlank() } ?: sc.mediaBase64
                        if (!resolvedMedia.isNullOrBlank()) {
                            val parsed = ImageUtils.parseEmojiModel(resolvedMedia)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(2.dp, Color(0xFFEC4899).copy(alpha = 0.75f)),
                                shadowElevation = 24.dp
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(parsed).crossfade(true).build(),
                                    imageLoader = imageLoader,
                                    contentDescription = "Sparkle",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .aspectRatio(0.80f)
                                        .clip(RoundedCornerShape(20.dp))
                                )
                            }
                        }

                        if (!sc.secretMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1E1035).copy(alpha = 0.92f),
                                border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth(0.92f)
                            ) {
                                Text(
                                    text = "💌 ${sc.secretMessage}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }

                        Text(
                            text = "Tap anywhere to close",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Rich, vibrant, adorable celestial sanctuary:
 * Uses Alignment.TopStart on BoxWithConstraints so that Canvas and Composables match coordinates.
 */
@Composable
private fun MemoriesSanctuaryStage(
    connections: List<Connection>,
    userAvatarUrl: String,
    imageLoader: coil.ImageLoader,
    onConnect: (Connection) -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    var userDragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "sanctuaryPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val portalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "portalRotation"
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        val stageWidthPx = with(density) { maxWidth.toPx() }
        val stageHeightPx = with(density) { maxHeight.toPx() }

        // Center orbit hub in the upper 42% of the screen
        val centerX = stageWidthPx / 2f
        val centerY = stageHeightPx * 0.42f
        val orbitRadius = min(stageWidthPx, stageHeightPx) * 0.32f

        // Resting position for User's Avatar Cradle (bottom center)
        val userRestXPx = centerX
        val userRestYPx = stageHeightPx * 0.74f

        val currentUserXPx = userRestXPx + userDragOffset.x
        val currentUserYPx = userRestYPx + userDragOffset.y

        // 1. Constellation Connection Canvas (Orbit paths, nexus glow, starlight links)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Central Memory Nexus Halo
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFC084FC).copy(alpha = 0.28f), Color(0xFF8B5CF6).copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = orbitRadius * 1.35f
                ),
                center = Offset(centerX, centerY),
                radius = orbitRadius * 1.35f
            )

            // Dotted Celestial Orbit Path
            drawCircle(
                color = Color(0xFFC084FC).copy(alpha = 0.35f),
                center = Offset(centerX, centerY),
                radius = orbitRadius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f), 0f)
                )
            )

            // Connecting starlight thread from user star to partner nodes
            connections.forEachIndexed { i, _ ->
                val angle = if (connections.size == 1) {
                    -PI.toFloat() / 2f // Top center if 1 partner
                } else {
                    -PI.toFloat() * 0.85f + (i * (PI.toFloat() * 0.70f / max(1, connections.size - 1)))
                }
                val nodeXPx = centerX + orbitRadius * cos(angle)
                val nodeYPx = centerY + orbitRadius * sin(angle)

                drawLine(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFDE68A).copy(alpha = if (isDragging) 0.7f else 0.3f), Color(0xFFEC4899).copy(alpha = if (isDragging) 0.7f else 0.3f)),
                        start = Offset(currentUserXPx, currentUserYPx),
                        end = Offset(nodeXPx, nodeYPx)
                    ),
                    start = Offset(currentUserXPx, currentUserYPx),
                    end = Offset(nodeXPx, nodeYPx),
                    strokeWidth = if (isDragging) 3.dp.toPx() else 1.5.dp.toPx()
                )
            }
        }

        // 2. Center Celestial Nexus Core (Rotating Emblem & Status Message)
        val nexusSizeDp = 86.dp
        val nexusSizePx = with(density) { nexusSizeDp.toPx() }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (centerX - nexusSizePx / 2).roundToInt(),
                        (centerY - nexusSizePx / 2).roundToInt()
                    )
                }
                .size(nexusSizeDp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E1035).copy(alpha = 0.88f),
                border = BorderStroke(1.5.dp, Color(0xFFC084FC).copy(alpha = 0.40f)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = portalRotation }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("💫", fontSize = 28.sp)
                }
            }
        }

        // Status text below the center nexus
        Column(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (centerX - with(density) { 140.dp.toPx() }).roundToInt(),
                        (centerY + nexusSizePx / 2 + with(density) { 12.dp.toPx() }).roundToInt()
                    )
                }
                .width(280.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF180B2E).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.35f))
            ) {
                Text(
                    text = if (connections.size == 1) {
                        "✨ ${connections.first().partnerName}'s Constellation 💖"
                    } else if (connections.size > 1) {
                        "✨ Constellation of ${connections.size} Connections 💖"
                    } else {
                        "✨ Connect with a partner to unlock memories ✨"
                    },
                    color = Color(0xFFF9A8D4),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // 3. Partner Celestial Spheres
        connections.forEachIndexed { i, conn ->
            val angle = if (connections.size == 1) {
                -PI.toFloat() / 2f
            } else {
                -PI.toFloat() * 0.85f + (i * (PI.toFloat() * 0.70f / max(1, connections.size - 1)))
            }
            val nodeXPx = centerX + orbitRadius * cos(angle)
            val nodeYPx = centerY + orbitRadius * sin(angle)

            val partnerAvatar = conn.partnerTwigiUrl?.takeIf { it.isNotBlank() }
                ?: conn.partnerEmojiUrl?.takeIf { it.isNotBlank() }
                ?: conn.partnerAvatarUrl?.takeIf { it.isNotBlank() }
                ?: TELEGRAM_EMOJIS.first()

            val dist = hypot(currentUserXPx - nodeXPx, currentUserYPx - nodeYPx)
            val isTargeted = isDragging && dist < with(density) { 72.dp.toPx() }

            val nodeScale by animateFloatAsState(
                targetValue = if (isTargeted) 1.28f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "nodeScale"
            )

            val nodeSizeDp = 76.dp
            val nodeSizePx = with(density) { nodeSizeDp.toPx() }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (nodeXPx - nodeSizePx / 2).roundToInt(),
                            (nodeYPx - nodeSizePx / 2).roundToInt()
                        )
                    }
                    .scale(nodeScale)
                    .clickable { onConnect(conn) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(nodeSizeDp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (isTargeted) Color(0xFFF43F5E) else Color(0xFFEC4899).copy(alpha = 0.90f),
                                        Color(0xFF8B5CF6).copy(alpha = 0.6f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val parsed = ImageUtils.parseEmojiModel(partnerAvatar)
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(parsed).crossfade(true).build(),
                            imageLoader = imageLoader,
                            contentDescription = conn.partnerName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF160D2E))
                        )
                    }

                    // Partner Name Pill
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF1E1035).copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, if (isTargeted) Color(0xFFF43F5E) else Color(0xFFC084FC).copy(alpha = 0.45f)),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = conn.partnerName.takeIf { it.isNotBlank() } ?: "Partner",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("💖", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // 4. User Draggable Star & Starlight Cradle
        val userStarSizeDp = 82.dp
        val userStarSizePx = with(density) { userStarSizeDp.toPx() }

        // Pedestal halo at rest position
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (userRestXPx - userStarSizePx / 2).roundToInt(),
                        (userRestYPx - userStarSizePx / 2).roundToInt()
                    )
                }
                .size(userStarSizeDp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF281845).copy(alpha = 0.45f),
                border = BorderStroke(1.5.dp, Color(0xFFFDE68A).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxSize()
            ) {}
        }

        // Draggable Star
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (currentUserXPx - userStarSizePx / 2).roundToInt(),
                        (currentUserYPx - userStarSizePx / 2).roundToInt()
                    )
                }
                .scale(if (isDragging) 1.18f else pulseScale)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            userDragOffset += dragAmount
                        },
                        onDragEnd = {
                            isDragging = false
                            // Check collision with any partner node
                            var matched: Connection? = null
                            connections.forEachIndexed { i, conn ->
                                val angle = if (connections.size == 1) {
                                    -PI.toFloat() / 2f
                                } else {
                                    -PI.toFloat() * 0.85f + (i * (PI.toFloat() * 0.70f / max(1, connections.size - 1)))
                                }
                                val nodeXPx = centerX + orbitRadius * cos(angle)
                                val nodeYPx = centerY + orbitRadius * sin(angle)
                                val dist = hypot(currentUserXPx - nodeXPx, currentUserYPx - nodeYPx)
                                if (dist < with(density) { 76.dp.toPx() }) {
                                    matched = conn
                                }
                            }
                            if (matched != null) {
                                onConnect(matched!!)
                            }
                            userDragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            isDragging = false
                            userDragOffset = Offset.Zero
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(userStarSizeDp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFFFDE68A).copy(alpha = 0.95f),
                                    Color(0xFFF59E0B).copy(alpha = 0.75f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val parsed = ImageUtils.parseEmojiModel(userAvatarUrl)
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(parsed).crossfade(true).build(),
                        imageLoader = imageLoader,
                        contentDescription = "You",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1436))
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFF59E0B),
                    shadowElevation = 10.dp
                ) {
                    Text(
                        text = if (isDragging) {
                            "Drop on partner! ✨"
                        } else {
                            val partnerFirst = connections.firstOrNull()?.partnerName?.takeIf { it.isNotBlank() } ?: "partner"
                            "Drag to $partnerFirst ✨"
                        },
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Living Animated Starlight Timeline View:
 * Vertical timeline thread with milestone date headers, alternating memory cards,
 * interactive stroke drawing replay, and high-res lightbox previews.
 */
@Composable
private fun AnimatedMemoriesTimeline(
    partner: Connection,
    userAvatarUrl: String,
    sparkles: List<Scribble>,
    imageLoader: coil.ImageLoader,
    onReplayScribble: (String) -> Unit,
    onOpenMedia: (Scribble) -> Unit,
    onSendSparkle: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("ALL") }

    val partnerAvatar = partner.partnerTwigiUrl?.takeIf { it.isNotBlank() }
        ?: partner.partnerEmojiUrl?.takeIf { it.isNotBlank() }
        ?: partner.partnerAvatarUrl?.takeIf { it.isNotBlank() }
        ?: TELEGRAM_EMOJIS.first()

    val filteredSparkles = remember(sparkles, selectedFilter) {
        when (selectedFilter) {
            "PHOTOS" -> sparkles.filter { !it.mediaUrl.isNullOrBlank() || !it.mediaBase64.isNullOrBlank() }
            "DOODLES" -> sparkles.filter { it.strokes.isNotEmpty() }
            "NOTES" -> sparkles.filter { !it.secretMessage.isNullOrBlank() }
            else -> sparkles
        }
    }

    val dateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Hero Bridge Banner
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E1238).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Starlight Bridge Avatars
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFF59E0B).copy(alpha = 0.35f))) {
                            val parsed = ImageUtils.parseEmojiModel(userAvatarUrl)
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(parsed).crossfade(true).build(),
                                imageLoader = imageLoader,
                                contentDescription = "You",
                                modifier = Modifier.fillMaxSize().padding(3.dp).clip(CircleShape)
                            )
                        }

                        Text(" 💫💖💫 ", fontSize = 12.sp)

                        Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFEC4899).copy(alpha = 0.35f))) {
                            val parsed = ImageUtils.parseEmojiModel(partnerAvatar)
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(parsed).crossfade(true).build(),
                                imageLoader = imageLoader,
                                contentDescription = partner.partnerName,
                                modifier = Modifier.fillMaxSize().padding(3.dp).clip(CircleShape)
                            )
                        }
                    }

                    // Send Sparkle CTA
                    Button(
                        onClick = onSendSparkle,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("✨", fontSize = 12.sp)
                            Text("New Sparkle", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "✨ All (${sparkles.size})",
                    "PHOTOS" to "📸 Photos",
                    "DOODLES" to "🎨 Doodles",
                    "NOTES" to "💌 Notes"
                ).forEach { (code, label) ->
                    val isSelected = selectedFilter == code
                    Surface(
                        onClick = { selectedFilter = code },
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSelected) Color(0xFFEC4899) else Color(0xFF1E1436).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFF472B6) else Color(0xFFC084FC).copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Timeline Feed
            if (filteredSparkles.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🌌 ✨", fontSize = 42.sp)
                        Text(
                            text = "No sparkles in this category yet!",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Share photos, drawings, and notes with ${partner.partnerName} to build your living galaxy timeline ✨",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(filteredSparkles, key = { _, sc -> sc.scribbleId }) { idx, sc ->
                        val isFirstOfDay = idx == 0 ||
                                dayFormat.format(Date(sc.createdAt)) != dayFormat.format(Date(filteredSparkles[idx - 1].createdAt))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isFirstOfDay) {
                                // Milestone Date Header
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFF281845).copy(alpha = 0.9f),
                                    border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.35f)),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text("📅", fontSize = 11.sp)
                                        Text(
                                            text = dayFormat.format(Date(sc.createdAt)),
                                            color = Color(0xFFFDE68A),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Timeline Entry Card with Branch Line
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Timeline Node Starlight Gem
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(top = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(if (sc.isSent) Color(0xFFF59E0B) else Color(0xFFEC4899))
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                }

                                // Card Content
                                TimelineCard(
                                    scribble = sc,
                                    timeStr = dateFormat.format(Date(sc.createdAt)),
                                    imageLoader = imageLoader,
                                    onReplay = { onReplayScribble(sc.scribbleId) },
                                    onOpen = { onOpenMedia(sc) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Timeline Card in the Animated Memories Stream.
 */
@Composable
private fun TimelineCard(
    scribble: Scribble,
    timeStr: String,
    imageLoader: coil.ImageLoader,
    onReplay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasPhoto = !scribble.mediaUrl.isNullOrBlank() || !scribble.mediaBase64.isNullOrBlank()
    val isDoodle = scribble.strokes.isNotEmpty()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1E1238).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, if (scribble.isSent) Color(0xFFF59E0B).copy(alpha = 0.45f) else Color(0xFFEC4899).copy(alpha = 0.45f)),
        shadowElevation = 10.dp,
        modifier = modifier.clickable {
            if (hasPhoto) onOpen()
            else if (isDoodle) onReplay()
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Info: Sender Badge & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (scribble.isSent) Color(0xFFF59E0B).copy(alpha = 0.22f) else Color(0xFFEC4899).copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, if (scribble.isSent) Color(0xFFFDE68A).copy(alpha = 0.4f) else Color(0xFFF472B6).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (scribble.isSent) "You ✨" else "Partner 💖",
                        color = if (scribble.isSent) Color(0xFFFDE68A) else Color(0xFFF9A8D4),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = timeStr,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Media Preview or Doodle Card
            if (hasPhoto) {
                val media = scribble.mediaUrl?.takeIf { it.isNotBlank() } ?: scribble.mediaBase64
                val parsed = ImageUtils.parseEmojiModel(media)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.25f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0C0618)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(parsed).crossfade(true).build(),
                        imageLoader = imageLoader,
                        contentDescription = "Sparkle Memory",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Enlarge",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp).padding(3.dp)
                        )
                    }
                }
            } else if (isDoodle) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF140B26),
                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🎨", fontSize = 24.sp)
                            Column {
                                Text(
                                    text = "Handwritten Doodle",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${scribble.strokes.size} strokes recorded ✨",
                                    color = Color(0xFFC084FC),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Button(
                            onClick = onReplay,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("▶", fontSize = 11.sp)
                                Text("Replay", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Note Text / Message Caption
            if (!scribble.secretMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF140A28).copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💌 ${scribble.secretMessage}",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
