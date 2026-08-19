package com.aman.gigi.ui.live

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aman.gigi.repository.LivePost

private val Ink = Color(0xFF15121F)
private val Card1 = Color(0xFF221C33)
private val Lav = Color(0xFFB9A6FF)
private val Mint = Color(0xFF8FE3C6)
private val Peach = Color(0xFFFFB4A2)

/** Category → the emoji people actually scan for. */
val LIVE_CATEGORIES = listOf(
    "coffee" to "☕", "walk" to "🚶", "food" to "🍜", "study" to "📚",
    "sport" to "🏸", "movie" to "🎬", "help" to "🤝", "other" to "✨"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    viewModel: LiveViewModel = hiltViewModel(),
    onOpenMap: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var composerOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    // Only the first entry fetches; later visits reuse what the ViewModel already
    // holds, so switching tabs is instant.
    LaunchedEffect(Unit) {
        if (viewModel.hasLocationPermission()) viewModel.loadOnce()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink, Color(0xFF1B1630))))
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp, 18.dp, 20.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Live", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "who's free near you, right now",
                        color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp
                    )
                }
                IconButton(onClick = {
                    historyOpen = true
                    viewModel.loadHistory()
                }) {
                    Icon(Icons.Default.History, "Your Live history", tint = Lav)
                }
                IconButton(onClick = { viewModel.refresh(silent = true) }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Lav)
                }
            }

            state.joinRequests.forEach { req ->
                JoinRequestBanner(
                    name = req.name,
                    onAccept = { viewModel.respond(req, true) },
                    onDecline = { viewModel.respond(req, false) }
                )
            }

            when {
                state.permissionDenied || !viewModel.hasLocationPermission() ->
                    PermissionPane {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }

                // Skeletons only on a genuine cold start; otherwise the cached list
                // stays on screen while we revalidate behind it.
                state.loading && state.posts.isEmpty() -> SkeletonFeed()

                state.posts.isEmpty() && !state.loading -> EmptyPane { composerOpen = true }

                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh(silent = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.posts, key = { it.postId }) { post ->
                            LivePostCard(
                                post = post,
                                onJoin = { viewModel.askToJoin(post.postId, null) },
                                onOpenMap = { onOpenMap(post.postId) },
                                onDone = { viewModel.markDone(post.postId) },
                                onLeave = { viewModel.leave(post.postId) }
                            )
                        }
                    }
                }
            }
        }

        // Shown only when the socket reports a change, so the list is never yanked
        // out from under someone mid-scroll.
        AnimatedVisibility(
            visible = state.pendingEvents > 0 && !state.refreshing,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp)
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Lav)
                    .clickable { viewModel.refresh(silent = true) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u2193", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.pendingEvents == 1) "1 new update \u00b7 pull to refresh"
                    else state.pendingEvents.toString() + " new updates \u00b7 pull to refresh",
                    color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }

        AnimatedVisibility(
            visible = !state.permissionDenied,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp, 0.dp, 24.dp, 110.dp)
        ) {
            FloatingActionButton(
                onClick = { composerOpen = true },
                containerColor = Lav, contentColor = Ink, shape = CircleShape
            ) { Icon(Icons.Default.Add, "Go live") }
        }

        state.error?.let { message ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp, 0.dp, 16.dp, 110.dp),
                action = { TextButton(onClick = { viewModel.clearError() }) { Text("OK", color = Lav) } },
                containerColor = Card1
            ) { Text(message, color = Color.White) }
        }
    }

    if (historyOpen) {
        LiveHistorySheet(
            history = state.history,
            loading = state.historyLoading,
            onDelete = { viewModel.deletePost(it) },
            onDismiss = { historyOpen = false }
        )
    }

    if (composerOpen) {
        ComposerSheet(
            myLat = state.myLat,
            myLng = state.myLng,
            onDismiss = { composerOpen = false },
            onPost = { text, category, radius, duration, visibility, cap ->
                viewModel.createPost(text, category, null, radius, duration, visibility, cap)
                composerOpen = false
            }
        )
    }
}

/** Mirrors the real cards so a cold start never shows a blank screen. */
@Composable
private fun SkeletonFeed() {
    Column(
        Modifier.fillMaxSize().padding(16.dp, 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) { i ->
            val shimmer = rememberInfiniteTransition(label = "sk")
            val alpha by shimmer.animateFloat(
                initialValue = 0.05f, targetValue = 0.13f,
                animationSpec = infiniteRepeatable(
                    tween(900, delayMillis = i * 120), RepeatMode.Reverse
                ),
                label = "skAlpha"
            )
            val bar = Color.White.copy(alpha = alpha)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Card1)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(bar))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Box(
                            Modifier.height(12.dp).width(110.dp)
                                .clip(RoundedCornerShape(6.dp)).background(bar)
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.height(9.dp).width(70.dp)
                                .clip(RoundedCornerShape(6.dp)).background(bar)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(12.dp)
                        .clip(RoundedCornerShape(6.dp)).background(bar)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth(0.6f).height(12.dp)
                        .clip(RoundedCornerShape(6.dp)).background(bar)
                )
            }
        }
    }
}

@Composable
private fun JoinRequestBanner(name: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth().padding(16.dp, 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Mint.copy(alpha = 0.14f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$name wants to join 🙋", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDecline) { Text("No", color = Color.White.copy(alpha = 0.6f)) }
        Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
            contentPadding = PaddingValues(16.dp, 6.dp)
        ) { Text("Let them in", fontSize = 13.sp) }
    }
}

@Composable
private fun LivePostCard(
    post: LivePost,
    onJoin: () -> Unit,
    onOpenMap: () -> Unit,
    onDone: () -> Unit,
    onLeave: () -> Unit
) {
    val vibe = vibeOf(post.category)
    val emoji = vibe.emoji
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Card1)
            .clickable(enabled = post.preciseLocation) { onOpenMap() }
    ) {
    VibeBackdrop(post.category, Modifier.matchParentSize())
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(vibe.tint.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (post.isMine) "You" else post.authorName,
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    buildString {
                        post.distanceM?.let { append(prettyDistance(it)).append(" away") }
                        timeLeftLabel(post.expiresAt)?.let {
                            if (isNotEmpty()) append(" · ")
                            append(it)
                        }
                    },
                    color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(post.text, color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp)
        Text(
            vibe.blurb,
            color = vibe.tint.copy(alpha = 0.85f),
            fontSize = 11.sp, fontWeight = FontWeight.Medium
        )

        if (post.participants.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarStack(post.participants)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (post.isFull) "full \u2728"
                    else if (post.maxJoiners != null)
                        "${post.acceptedCount}/${post.maxJoiners} in"
                    else "${post.participants.size} in",
                    color = if (post.isFull) Peach else Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = if (post.isFull) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!post.preciseLocation) {
                Text(
                    "📍 approximate area",
                    color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    "📍 exact spot · tap for map",
                    color = Mint.copy(alpha = 0.8f), fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            when {
                post.isMine -> TextButton(onClick = onDone) { Text("Done", color = Peach) }
                post.preciseLocation -> TextButton(onClick = onLeave) {
                    Text("Leave", color = Color.White.copy(alpha = 0.5f))
                }
                // Once the host's headcount is reached the button becomes a state,
                // not a dead control — tapping a disabled "I'm in" is just annoying.
                post.isFull -> Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Peach.copy(alpha = 0.18f))
                        .padding(16.dp, 7.dp)
                ) {
                    Text(
                        "Full \u2728", color = Peach,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
                else -> Button(
                    onClick = onJoin,
                    colors = ButtonDefaults.buttonColors(containerColor = Lav, contentColor = Ink),
                    contentPadding = PaddingValues(18.dp, 6.dp)
                ) { Text("I'm in", fontSize = 13.sp) }
            }
        }
    }
    }
}

/**
 * Three little steps rather than one long form — the dials need room to breathe,
 * and "say it / pick your reach / pick your window" is a nicer rhythm than a wall
 * of chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerSheet(
    myLat: Double?,
    myLng: Double?,
    onDismiss: () -> Unit,
    onPost: (String, String, Int, Int, String, Int?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("coffee") }
    var radius by remember { mutableStateOf(500) }
    var minutes by remember { mutableStateOf(120) }
    var fofOpen by remember { mutableStateOf(false) }
    var spots by remember { mutableStateOf(0) }   // 0 = no limit
    var step by remember { mutableStateOf(0) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Card1,
        dragHandle = { StepDots(step) }
    ) {
        Column(
            Modifier.padding(20.dp, 4.dp, 20.dp, 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "composerStep"
            ) { current ->
                when (current) {
                    0 -> StepSay(
                        text = text, onText = { if (it.length <= 180) text = it },
                        category = category, onCategory = { category = it }
                    )
                    1 -> StepReach(
                        radius = radius, onRadius = { radius = it },
                        lat = myLat, lng = myLng,
                        fofOpen = fofOpen, onFof = { fofOpen = it },
                        spots = spots, onSpots = { spots = it }
                    )
                    else -> StepWindow(minutes = minutes, onMinutes = { minutes = it })
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(54.dp)
                    ) { Text("Back", color = Color.White.copy(alpha = 0.7f)) }
                }
                CuteButton(
                    label = if (step < 2) "Next" else "Go live ✨",
                    enabled = step > 0 || text.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (step < 2) step++
                    else onPost(
                        text.trim(), category, radius, minutes,
                        if (fofOpen) "FOF" else "CONNECTIONS",
                        spots.takeIf { it > 0 }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val width by animateDpAsState(if (i == step) 22.dp else 7.dp, label = "dot$i")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(7.dp).width(width)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i == step) Lav else Color.White.copy(alpha = 0.15f))
            )
        }
    }
}

@Composable
private fun StepSay(
    text: String, onText: (String) -> Unit,
    category: String, onCategory: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("What are you up to?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "keep it small and honest — that's the charm",
            color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text, onValueChange = onText,
            placeholder = { Text("chai + a rant, anyone? ☕", color = Color.White.copy(alpha = 0.3f)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Lav, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                cursorColor = Lav,
                focusedContainerColor = Color.White.copy(alpha = 0.04f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
            ),
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(), minLines = 2
        )
        Spacer(Modifier.height(18.dp))
        Label("pick a vibe")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LIVE_CATEGORIES.forEach { (key, emoji) ->
                VibeChip(emoji, key == category) { onCategory(key) }
            }
        }
    }
}

@Composable
private fun StepReach(
    radius: Int, onRadius: (Int) -> Unit,
    lat: Double?, lng: Double?,
    fofOpen: Boolean, onFof: (Boolean) -> Unit,
    spots: Int, onSpots: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("How far should it reach?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "turn the ring — 200 m to 10 km, your call",
            color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        RadiusDial(radiusM = radius, onRadiusChange = onRadius, lat = lat, lng = lng, accent = Lav)
        Spacer(Modifier.height(10.dp))
        Label("how many can come?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1, 2, 3, 5).forEach { n ->
                SpotChip(
                    label = if (n == 0) "\u221e" else n.toString(),
                    selected = n == spots
                ) { onSpots(n) }
            }
        }
        Text(
            if (spots == 0) "as many as want to come"
            else if (spots == 1) "just one person \u00b7 fills up after they're in"
            else "$spots people \u00b7 fills up once they're in",
            color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(14.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = fofOpen, onCheckedChange = onFof,
                colors = SwitchDefaults.colors(checkedTrackColor = Lav, checkedThumbColor = Ink)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Friends of friends too", color = Color.White, fontSize = 13.sp)
                Text(
                    if (fofOpen) "a slightly wider circle" else "only your people",
                    color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun StepWindow(minutes: Int, onMinutes: (Int) -> Unit) {
    val endsAt = remember(minutes) {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.MINUTE, minutes)
        "until " + java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(cal.time)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("How long are you free?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "anything from 5 minutes to 5 hours",
            color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        DurationDial(
            minutes = minutes, onMinutesChange = onMinutes,
            accent = Peach, endsAtLabel = endsAt
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "🔒  Your exact spot stays hidden until you let someone in.",
            color = Mint.copy(alpha = 0.75f), fontSize = 11.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CuteButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (enabled) 1f else 0.98f, label = "btnScale")
    var lastClick by remember { mutableStateOf(0L) }
    Box(
        modifier
            .scale(scale)
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(Lav, Color(0xFFD7A6FF)))
                else Brush.horizontalGradient(listOf(Color(0xFF3A3350), Color(0xFF3A3350)))
            )
            .clickable(enabled = enabled) {
                // the step slide takes ~300ms; a second press inside it skipped a step
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastClick > 450L) { lastClick = now; onClick() }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) Ink else Color.White.copy(alpha = 0.3f),
            fontSize = 16.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(), color = Color.White.copy(alpha = 0.35f),
        fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

/** Round chip for the headcount picker. */
@Composable
private fun SpotChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "spotScale"
    )
    Box(
        Modifier
            .scale(scale)
            .size(46.dp)
            .clip(CircleShape)
            .background(if (selected) Lav else Color.White.copy(alpha = 0.06f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Ink else Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Overlapping avatars of everyone in a meet-up. Each one pops in on arrival, so a new
 * joiner is something you notice rather than a silently incremented number.
 */
@Composable
private fun AvatarStack(
    participants: List<com.aman.gigi.repository.LiveParticipant>,
    max: Int = 5
) {
    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
        participants.take(max).forEachIndexed { i, p ->
            val appear = remember(p.memberId) { Animatable(0.4f) }
            LaunchedEffect(p.memberId) {
                appear.animateTo(
                    1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)
                )
            }
            Box(
                Modifier
                    .scale(appear.value)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (p.isHost) Peach.copy(alpha = 0.35f) else Card1)
                    .border(1.5.dp, if (p.isHost) Peach else Lav.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (p.avatarUrl != null) {
                    AsyncImage(
                        model = p.avatarUrl,
                        contentDescription = p.name,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        p.name.take(1).uppercase(),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (participants.size > max) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Card1)
                    .border(1.5.dp, Lav.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+" + (participants.size - max),
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Bounces a little when picked — small reward, no cost. */
@Composable
private fun VibeChip(emoji: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "vibeScale"
    )
    Box(
        Modifier
            .scale(scale)
            .size(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Lav else Color.White.copy(alpha = 0.06f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = 24.sp) }
}

@Composable
private fun PermissionPane(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📍", fontSize = 52.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "See who's free nearby",
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Gigi needs your location to show plans around you. " +
                "Nothing is shared until you post — and even then, only a rough area.",
            color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = Lav, contentColor = Ink),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Allow location") }
    }
}

@Composable
private fun EmptyPane(onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🌙", fontSize = 52.sp)
        Spacer(Modifier.height(16.dp))
        Text("Quiet around here", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Nobody's posted nearby yet. Be the one who starts it.",
            color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onCreate,
            colors = ButtonDefaults.buttonColors(containerColor = Lav, contentColor = Ink),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Post something") }
    }
}

private fun prettyDistance(meters: Int): String =
    if (meters < 1000) "${meters} m" else String.format("%.1f km", meters / 1000f)
