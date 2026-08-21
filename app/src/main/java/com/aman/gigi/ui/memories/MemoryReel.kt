package com.aman.gigi.ui.memories

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aman.gigi.model.Scribble
import com.aman.gigi.utils.SparkleMedia
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Memory Reel — your archive as an auto-playing story.
 *
 * Plays oldest → newest so it reads as a little journey. Photos drift with a slow
 * Ken Burns push, doodles redraw themselves stroke by stroke, and notes type
 * themselves out. Tap the right side to skip, the left to go back, hold anywhere to
 * pause, swipe down to leave.
 */
@Composable
internal fun MemoryReel(
    memories: List<Scribble>,
    partnerName: String,
    userAvatarUrl: String,
    partnerAvatarUrl: String,
    imageLoader: coil.ImageLoader,
    onDismiss: () -> Unit
) {
    if (memories.isEmpty()) return

    // Newest-first everywhere else; a story wants to start at the beginning.
    val reel = remember(memories) { memories.sortedBy { it.createdAt } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ReelPlayer(
            reel = reel,
            partnerName = partnerName,
            userAvatarUrl = userAvatarUrl,
            partnerAvatarUrl = partnerAvatarUrl,
            imageLoader = imageLoader,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun ReelPlayer(
    reel: List<Scribble>,
    partnerName: String,
    userAvatarUrl: String,
    partnerAvatarUrl: String,
    imageLoader: coil.ImageLoader,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // index == reel.size is the closing card.
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    BackHandler { onDismiss() }

    val atEnd = index >= reel.size
    val current = reel.getOrNull(index)

    // Drive the active slide. Cancelling on `paused` freezes it mid-way; resuming
    // animates only the remaining slice, so holding really does hold.
    LaunchedEffect(index, paused) {
        if (current == null) return@LaunchedEffect
        if (paused) return@LaunchedEffect
        if (progress.value >= 1f) progress.snapTo(0f)
        val remaining = ((1f - progress.value) * slideDuration(current)).roundToInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
        index += 1
        progress.snapTo(0f)
    }

    fun goTo(target: Int) {
        index = target.coerceIn(0, reel.size)
        paused = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF120726), Color(0xFF07030F))))
    ) {
        ReelSparkles()

        // ── Stage ──
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (fadeIn(tween(280)) + scaleIn(tween(340, easing = FastOutSlowInEasing), initialScale = 0.94f)) togetherWith
                    fadeOut(tween(180))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 108.dp, bottom = 118.dp)
                .padding(horizontal = 18.dp),
            label = "reelSlide"
        ) { slide ->
            val memory = reel.getOrNull(slide)
            if (memory == null) {
                ReelEndCard(
                    count = reel.size,
                    partnerName = partnerName,
                    onReplay = { goTo(0) },
                    onDismiss = onDismiss
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        SparkleMedia.hasMedia(memory) -> PhotoSlide(memory, imageLoader, progress)
                        SparkleMedia.isDoodle(memory) -> DoodleSlide(memory, progress)
                        else -> NoteSlide(memory, progress)
                    }
                }
            }
        }

        // ── Gesture layer, above the stage so skipping always wins ──
        // Drag sits last in the chain on purpose: the innermost pointer node sees the
        // event first, and vertical-drag detection doesn't consume the down, so taps
        // still land. Reverse the order and the tap detector swallows every swipe.
        if (!atEnd) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(index) {
                        detectTapGestures(
                            onPress = {
                                paused = true
                                tryAwaitRelease()
                                paused = false
                            },
                            onTap = { offset ->
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                if (offset.x < size.width * 0.32f) goTo(index - 1) else goTo(index + 1)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onVerticalDrag = { _, amount -> dragged += amount },
                            onDragEnd = { if (dragged > 170f) onDismiss() },
                            onDragCancel = { dragged = 0f }
                        )
                    }
            )
        }

        // ── Top chrome ──
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReelProgressBar(
                total = reel.size,
                index = index,
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val sender = current
                ReelAvatar(
                    model = if (sender?.isSent == false) partnerAvatarUrl else userAvatarUrl,
                    imageLoader = imageLoader,
                    ring = if (sender?.isSent == false) ReelTheirs else ReelMine
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            current == null -> "Your reel"
                            current.isSent -> "You"
                            else -> partnerName
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = current?.let { reelStamp.format(Date(it.createdAt)) } ?: "${reel.size} memories",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                if (paused) {
                    Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.16f)) {
                        Text(
                            "paused",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.size(34.dp).clickable { onDismiss() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Close reel", tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }

        // ── Bottom chrome ──
        if (!atEnd) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                current?.let { memory ->
                    // A note-only memory already types itself out on the slide; only a
                    // photo needs its caption pinned underneath.
                    if (SparkleMedia.hasNote(memory) && SparkleMedia.hasMedia(memory)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                            modifier = Modifier.padding(horizontal = 26.dp)
                        ) {
                            Text(
                                text = "💌 ${memory.secretMessage}",
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.10f)) {
                    Text(
                        text = "${index + 1} of ${reel.size}  ·  tap to skip · hold to pause",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ── Slides ────────────────────────────────────────────────────────────────────

/** Photo with a slow Ken Burns push, driven straight from the slide clock. */
@Composable
private fun PhotoSlide(memory: Scribble, imageLoader: coil.ImageLoader, progress: Animatable<Float, *>) {
    val drift = remember(memory.scribbleId) { if (memory.scribbleId.hashCode() % 2 == 0) 1f else -1f }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF0C0618),
        border = BorderStroke(1.5.dp, (if (memory.isSent) ReelMine else ReelTheirs).copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.78f)) {
            ReelImage(
                memory = memory,
                imageLoader = imageLoader,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = progress.value
                        val scale = 1.06f + t * 0.13f
                        scaleX = scale
                        scaleY = scale
                        translationX = drift * t * size.width * 0.035f
                        translationY = -t * size.height * 0.02f
                    }
            )
        }
    }
}

/** The doodle redraws itself, with a glowing pen tip riding the last point. */
@Composable
private fun DoodleSlide(memory: Scribble, progress: Animatable<Float, *>) {
    val totalPoints = remember(memory.scribbleId) { memory.strokes.sumOf { it.points.size }.coerceAtLeast(1) }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF150B2B),
        border = BorderStroke(1.5.dp, (if (memory.isSent) ReelMine else ReelTheirs).copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.86f)) {
            Canvas(modifier = Modifier.fillMaxSize().padding(22.dp)) {
                // Ease the reveal a touch so the last strokes don't race.
                val revealed = (progress.value.coerceIn(0f, 1f) * 1.12f).coerceAtMost(1f) * totalPoints
                var consumed = 0
                var penTip: Offset? = null

                memory.strokes.forEach { stroke ->
                    if (consumed >= revealed) return@forEach
                    val budget = (revealed - consumed).roundToInt()
                    val take = min(stroke.points.size, budget)
                    if (take >= 2) {
                        val path = Path()
                        for (i in 0 until take) {
                            val point = stroke.points[i]
                            val x = point.x * size.width
                            val y = point.y * size.height
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            if (i == take - 1) penTip = Offset(x, y)
                        }
                        drawPath(
                            path = path,
                            color = Color(stroke.color).copy(alpha = stroke.opacity.coerceIn(0.2f, 1f)),
                            style = Stroke(
                                width = (stroke.width * min(size.width, size.height) / 300f).coerceIn(2f, 16f),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    consumed += stroke.points.size
                }

                penTip?.let { tip ->
                    if (progress.value < 0.97f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                                center = tip,
                                radius = 26f
                            ),
                            radius = 26f,
                            center = tip
                        )
                    }
                }
            }

            Text(
                text = "🎨",
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)
            )
        }
    }
}

/** A note that types itself onto a love-letter card. */
@Composable
private fun NoteSlide(memory: Scribble, progress: Animatable<Float, *>) {
    val full = memory.secretMessage.orEmpty()
    // Reads the slide clock during composition on purpose — the text itself changes,
    // so a draw-phase trick wouldn't help here.
    val typed = (progress.value.coerceIn(0f, 1f) * 1.35f * full.length).roundToInt()
    val shown = full.take(typed.coerceIn(0, full.length))
    val caretOn = (progress.value * 14f).toInt() % 2 == 0

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = BorderStroke(1.5.dp, (if (memory.isSent) ReelMine else ReelTheirs).copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF3B1651), Color(0xFF1B0C31))))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("💌", fontSize = 30.sp)
                Text(
                    text = shown + if (caretOn && shown.length < full.length) "▌" else "",
                    color = Color.White,
                    fontSize = 19.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ReelEndCard(
    count: Int,
    partnerName: String,
    onReplay: () -> Unit,
    onDismiss: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "endPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "endGlow"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "✨",
                fontSize = 54.sp,
                modifier = Modifier.graphicsLayer { scaleX = glow; scaleY = glow }
            )
            Text(
                text = "That's $count ${if (count == 1) "memory" else "memories"}",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "with $partnerName, and counting.",
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Button(
                onClick = onReplay,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ReelTheirs),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play again", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = onDismiss) {
                Text("Back to memories", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
    }
}

// ── Chrome pieces ─────────────────────────────────────────────────────────────

/** Story segments. Drawn, not composed, so the ticking clock never recomposes. */
@Composable
private fun ReelProgressBar(
    total: Int,
    index: Int,
    progress: Animatable<Float, *>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (total <= 0) return@Canvas
        val gap = 3.dp.toPx()
        val segment = ((size.width - gap * (total - 1)) / total).coerceAtLeast(1f)
        val radius = size.height / 2f

        for (i in 0 until total) {
            val left = i * (segment + gap)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(left, 0f),
                size = Size(segment, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
            )
            val fill = when {
                i < index -> 1f
                i == index -> progress.value.coerceIn(0f, 1f)
                else -> 0f
            }
            if (fill > 0f) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(left, 0f),
                    size = Size(segment * fill, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
                )
            }
        }
    }
}

@Composable
private fun ReelAvatar(model: String?, imageLoader: coil.ImageLoader, ring: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(ring.copy(alpha = 0.7f), ring.copy(alpha = 0.15f)))),
        contentAlignment = Alignment.Center
    ) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(com.aman.gigi.utils.ImageUtils.parseEmojiModel(model))
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(3.dp).clip(CircleShape).background(Color(0xFF160D2E))
        )
    }
}

@Composable
private fun ReelImage(memory: Scribble, imageLoader: coil.ImageLoader, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val model = remember(memory.scribbleId, memory.mediaUrl, memory.mediaBase64) { SparkleMedia.resolve(memory) }

    if (model == null) {
        Box(modifier = modifier.background(Color(0xFF1A1033)), contentAlignment = Alignment.Center) {
            Text("✨", fontSize = 26.sp)
        }
        return
    }

    coil.compose.AsyncImage(
        model = coil.request.ImageRequest.Builder(context)
            .data(model)
            .memoryCacheKey("sparkle:${memory.scribbleId}")
            .crossfade(true)
            .build(),
        imageLoader = imageLoader,
        contentDescription = "Memory",
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

/** Slow drifting sparkles behind the stage. Twelve of them, all draw-phase. */
@Composable
private fun ReelSparkles() {
    val seeds = remember {
        val rnd = java.util.Random(4212L)
        List(12) { Triple(rnd.nextFloat(), rnd.nextFloat(), 1.2f + rnd.nextFloat() * 2.4f) }
    }
    val transition = rememberInfiniteTransition(label = "reelDust")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "reelPhase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        seeds.forEachIndexed { i, (fx, fy, r) ->
            val wobble = sin(phase + i) * size.width * 0.02f
            val rise = ((fy - phase / (2 * Math.PI).toFloat()) % 1f + 1f) % 1f
            drawCircle(
                color = Color.White.copy(alpha = 0.10f + (r / 3.6f) * 0.16f),
                radius = r,
                center = Offset(fx * size.width + wobble, rise * size.height)
            )
        }
    }
}

// ── Bits ──────────────────────────────────────────────────────────────────────

private val ReelMine = Color(0xFFFBBF24)
private val ReelTheirs = Color(0xFFF472B6)
private val reelStamp = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())

private fun slideDuration(memory: Scribble): Int = when {
    SparkleMedia.isDoodle(memory) -> 4400
    SparkleMedia.hasMedia(memory) -> 3800
    else -> (2200 + (memory.secretMessage?.length ?: 0) * 45).coerceIn(2600, 6500)
}

/** The floating "Play reel" pill that opens the story. */
@Composable
internal fun MemoryReelFab(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "fabPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fabScale"
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "fabShimmer"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        shadowElevation = 14.dp,
        modifier = modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFEC4899),
                            lerpColor(Color(0xFF8B5CF6), Color(0xFFC084FC), shimmer)
                        )
                    )
                )
                .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFFEC4899),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text("Play reel", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = "$count ${if (count == 1) "memory" else "memories"}",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = 1f
)
