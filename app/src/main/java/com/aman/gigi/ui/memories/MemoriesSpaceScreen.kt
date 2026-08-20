package com.aman.gigi.ui.memories

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.aman.gigi.viewmodel.ScreensaverViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Full-screen Cosmic Memories Space:
 * An ethereal sanctuary where you drag your avatar to your connections to reveal
 * all shared sparkles, photos, scribbles, and memories in a living animated gallery.
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
    val density = LocalDensity.current

    val imageLoader = remember {
        coil.ImageLoader.Builder(context).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }.build()
    }

    val userAvatarUrl = if (identity?.avatarMode == "TWIGI" && !identity.twigiRenderUrl.isNullOrBlank()) {
        identity.twigiRenderUrl
    } else {
        identity?.avatarUrl?.takeIf { it.isNotBlank() } ?: TELEGRAM_EMOJIS.first()
    }

    // Detail modal for viewing full-screen media
    var selectedMediaScribble by remember { mutableStateOf<Scribble?>(null) }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF1E1038),
                            Color(0xFF110724),
                            Color(0xFF070210)
                        )
                    )
                )
        ) {
            // Ambient Cosmic Dust & Auroras
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Nebula glowing clouds
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFEC4899).copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(w * 0.25f, h * 0.35f),
                        radius = w * 0.65f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF8B5CF6).copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(w * 0.75f, h * 0.65f),
                        radius = w * 0.7f
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
                        color = Color(0xFF281845).copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                if (selectedConnection != null) {
                                    onSelectConnection(selectedConnection.copy(connectionId = ""))
                                } else {
                                    onBack()
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (selectedConnection != null) Icons.Default.ArrowBack else Icons.Default.Close,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🌌", fontSize = 16.sp)
                            Text(
                                text = if (selectedConnection != null) "${selectedConnection.partnerName}'s Sparkles" else "Memories Space",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("✨", fontSize = 16.sp)
                        }
                        Text(
                            text = if (selectedConnection != null) "${sparkles.size} moments preserved" else "Drag your star to unlock shared memories",
                            color = Color(0xFFF472B6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Balance spacer
                    Spacer(Modifier.size(40.dp))
                }

                // Main Content: Celestial Drag Stage OR Shared Sparkles Gallery
                AnimatedContent(
                    targetState = selectedConnection,
                    transitionSpec = {
                        fadeIn(tween(350)) + scaleIn(tween(350, easing = EaseOutBack), initialScale = 0.9f) togetherWith
                                fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 1.05f)
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "memoriesTransition"
                ) { conn ->
                    if (conn == null || conn.connectionId.isBlank()) {
                        // Stage 1: Celestial Drag & Drop Sanctuary
                        MemoriesSanctuaryStage(
                            identity = identity,
                            connections = connections,
                            userAvatarUrl = userAvatarUrl,
                            imageLoader = imageLoader,
                            onConnect = { targetConn ->
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onSelectConnection(targetConn)
                            }
                        )
                    } else {
                        // Stage 2: Shared Sparkles & Memories Gallery
                        SharedSparklesGallery(
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
                            .background(Color.Black.copy(alpha = 0.94f))
                            .clickable { selectedMediaScribble = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val resolvedMedia = sc.mediaUrl?.takeIf { it.isNotBlank() } ?: sc.mediaBase64
                            if (!resolvedMedia.isNullOrBlank()) {
                                val parsed = ImageUtils.parseEmojiModel(resolvedMedia)
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.5.dp, Color(0xFFEC4899).copy(alpha = 0.7f)),
                                    shadowElevation = 24.dp
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(parsed).crossfade(true).build(),
                                        imageLoader = imageLoader,
                                        contentDescription = "Sparkle",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .aspectRatio(0.75f)
                                            .clip(RoundedCornerShape(20.dp))
                                    )
                                }
                            }

                            if (!sc.secretMessage.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF1E1035).copy(alpha = 0.9f),
                                    border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth(0.9f)
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
}

/**
 * Celestial Sanctuary Stage where user drags their central star to connect with partner nodes.
 */
@Composable
private fun MemoriesSanctuaryStage(
    identity: MemberIdentity?,
    connections: List<Connection>,
    userAvatarUrl: String,
    imageLoader: coil.ImageLoader,
    onConnect: (Connection) -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    var userOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val stageWidthPx = with(density) { maxWidth.toPx() }
        val stageHeightPx = with(density) { maxHeight.toPx() }
        val centerX = stageWidthPx / 2f
        val centerY = stageHeightPx / 2f

        val orbitRadius = min(stageWidthPx, stageHeightPx) * 0.36f

        // 1. Constellation Connection Lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw celestial orbit halo
            drawCircle(
                color = Color(0xFFC084FC).copy(alpha = 0.12f),
                center = Offset(centerX, centerY),
                radius = orbitRadius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f))
            )

            // Draw line from user to center or dragged position
            val userPos = Offset(centerX + userOffset.x, centerY + userOffset.y)
            connections.forEachIndexed { i, _ ->
                val angle = i * (2f * PI.toFloat() / max(1, connections.size)) - (PI.toFloat() / 2f)
                val nodeX = centerX + orbitRadius * cos(angle)
                val nodeY = centerY + orbitRadius * sin(angle)
                drawLine(
                    color = Color(0xFFEC4899).copy(alpha = if (isDragging) 0.35f else 0.15f),
                    start = userPos,
                    end = Offset(nodeX, nodeY),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        // 2. Partner Connection Nodes
        connections.forEachIndexed { i, conn ->
            val angle = i * (2f * PI.toFloat() / max(1, connections.size)) - (PI.toFloat() / 2f)
            val nodeXPx = centerX + orbitRadius * cos(angle)
            val nodeYPx = centerY + orbitRadius * sin(angle)

            val partnerAvatar = conn.partnerTwigiUrl?.takeIf { it.isNotBlank() }
                ?: conn.partnerEmojiUrl?.takeIf { it.isNotBlank() }
                ?: conn.partnerAvatarUrl?.takeIf { it.isNotBlank() }
                ?: TELEGRAM_EMOJIS.first()

            val nodeDist = hypot((centerX + userOffset.x) - nodeXPx, (centerY + userOffset.y) - nodeYPx)
            val isTargeted = isDragging && nodeDist < with(density) { 60.dp.toPx() }

            val nodeScale by animateFloatAsState(
                targetValue = if (isTargeted) 1.25f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "nodeScale"
            )

            val nodeSizeDp = 64.dp
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(nodeSizeDp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (isTargeted) Color(0xFFF43F5E) else Color(0xFFEC4899).copy(alpha = 0.85f),
                                        Color(0xFF8B5CF6).copy(alpha = 0.5f),
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
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF160D2E))
                        )
                    }

                    // Partner Name Tag
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF1E1035).copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, if (isTargeted) Color(0xFFF43F5E) else Color(0xFFC084FC).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = conn.partnerName.takeIf { it.isNotBlank() } ?: "Partner",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. User Central Draggable Star
        val userStarSizeDp = 72.dp
        val userStarSizePx = with(density) { userStarSizeDp.toPx() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (centerX + userOffset.x - userStarSizePx / 2).roundToInt(),
                        (centerY + userOffset.y - userStarSizePx / 2).roundToInt()
                    )
                }
                .scale(if (isDragging) 1.15f else ambientPulse)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            userOffset += dragAmount
                        },
                        onDragEnd = {
                            isDragging = false
                            // Check if dropped near any connection node
                            var matched: Connection? = null
                            connections.forEachIndexed { i, conn ->
                                val angle = i * (2f * PI.toFloat() / max(1, connections.size)) - (PI.toFloat() / 2f)
                                val nodeXPx = centerX + orbitRadius * cos(angle)
                                val nodeYPx = centerY + orbitRadius * sin(angle)
                                val dist = hypot((centerX + userOffset.x) - nodeXPx, (centerY + userOffset.y) - nodeYPx)
                                if (dist < with(density) { 68.dp.toPx() }) {
                                    matched = conn
                                }
                            }
                            if (matched != null) {
                                onConnect(matched!!)
                            }
                            userOffset = Offset.Zero
                        },
                        onDragCancel = {
                            isDragging = false
                            userOffset = Offset.Zero
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(userStarSizeDp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFFFDE68A).copy(alpha = 0.95f),
                                    Color(0xFFF59E0B).copy(alpha = 0.6f),
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
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1436))
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFF59E0B),
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = if (isDragging) "Drop on partner!" else "Drag Me ✨",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Shared Sparkles & Memories Gallery showing photos, scribbles, camera captures, and quotes.
 */
@Composable
private fun SharedSparklesGallery(
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Bridge Banner
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1238).copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Avatars Bridge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF59E0B).copy(alpha = 0.3f))) {
                        val parsed = ImageUtils.parseEmojiModel(userAvatarUrl)
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(parsed).crossfade(true).build(),
                            imageLoader = imageLoader,
                            contentDescription = "You",
                            modifier = Modifier.fillMaxSize().padding(3.dp).clip(CircleShape)
                        )
                    }

                    Text(" ✨💖✨ ", fontSize = 13.sp)

                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFEC4899).copy(alpha = 0.3f))) {
                        val parsed = ImageUtils.parseEmojiModel(partnerAvatar)
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(parsed).crossfade(true).build(),
                            imageLoader = imageLoader,
                            contentDescription = partner.partnerName,
                            modifier = Modifier.fillMaxSize().padding(3.dp).clip(CircleShape)
                        )
                    }
                }

                // Send Sparkle CTA Button
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
                        Text("Send Sparkle", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    color = if (isSelected) Color(0xFFEC4899) else Color(0xFF1E1436).copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFF472B6) else Color(0xFFC084FC).copy(alpha = 0.3f))
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

        // Sparkles Grid
        if (filteredSparkles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🌌", fontSize = 42.sp)
                    Text(
                        text = "No sparkles shared yet!",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Send a live doodle or photo sparkle to ignite your shared galaxy ✨",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(filteredSparkles, key = { it.scribbleId }) { sc ->
                    SparkleMemoryCard(
                        scribble = sc,
                        dateFormat = dateFormat,
                        imageLoader = imageLoader,
                        onReplay = { onReplayScribble(sc.scribbleId) },
                        onOpen = { onOpenMedia(sc) }
                    )
                }
            }
        }
    }
}

/**
 * Individual memory card inside the Sparkles Gallery.
 */
@Composable
private fun SparkleMemoryCard(
    scribble: Scribble,
    dateFormat: SimpleDateFormat,
    imageLoader: coil.ImageLoader,
    onReplay: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val hasPhoto = !scribble.mediaUrl.isNullOrBlank() || !scribble.mediaBase64.isNullOrBlank()
    val isDoodle = scribble.strokes.isNotEmpty()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1C1135).copy(alpha = 0.9f),
        border = BorderStroke(1.dp, if (scribble.isSent) Color(0xFFF59E0B).copy(alpha = 0.4f) else Color(0xFFEC4899).copy(alpha = 0.4f)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (hasPhoto) onOpen()
                else if (isDoodle) onReplay()
            }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Media Preview or Doodle Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0D061A)),
                contentAlignment = Alignment.Center
            ) {
                if (hasPhoto) {
                    val media = scribble.mediaUrl?.takeIf { it.isNotBlank() } ?: scribble.mediaBase64
                    val parsed = ImageUtils.parseEmojiModel(media)
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(parsed).crossfade(true).build(),
                        imageLoader = imageLoader,
                        contentDescription = "Sparkle Memory",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isDoodle) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🎨 ✨", fontSize = 26.sp)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.85f),
                            modifier = Modifier.clickable { onReplay() }
                        ) {
                            Text(
                                text = "▶ Replay",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                } else {
                    Text("💌", fontSize = 32.sp)
                }

                // Type Badge
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                ) {
                    Text(
                        text = if (hasPhoto) "📸 Photo" else if (isDoodle) "🎨 Doodle" else "💌 Note",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Secret Message / Note text if present
            if (!scribble.secretMessage.isNullOrBlank()) {
                Text(
                    text = scribble.secretMessage,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }

            // Timestamp & Sender
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormat.format(Date(scribble.createdAt)),
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp
                )

                Text(
                    text = if (scribble.isSent) "You ✨" else "Partner 💖",
                    color = if (scribble.isSent) Color(0xFFFDE68A) else Color(0xFFF472B6),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
