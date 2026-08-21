package com.aman.gigi.ui.memories

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.aman.gigi.utils.SparkleMedia
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

// ── Palette ───────────────────────────────────────────────────────────────────
private val NightTop = Color(0xFF17092F)
private val NightMid = Color(0xFF0E0620)
private val NightBottom = Color(0xFF07030F)
private val Card = Color(0xFF1A1033)
private val CardSoft = Color(0xFF150C2B)
private val Hairline = Color(0x1FFFFFFF)
private val Violet = Color(0xFFC084FC)
private val Mine = Color(0xFFFBBF24)
private val Theirs = Color(0xFFF472B6)
private val TextPrimary = Color(0xFFF5F3FF)
private val TextSecondary = Color(0xFFA79FC4)

private enum class MemoryFilter(val label: String, val emoji: String) {
    ALL("All", "✨"),
    PHOTOS("Photos", "📸"),
    DOODLES("Doodles", "🎨"),
    NOTES("Notes", "💌")
}

private enum class VaultLayout { GRID, TIMELINE }

/**
 * Memories — the shared archive behind Sweet Corner.
 *
 * Two surfaces:
 *  1. the hub, one shelf per person, showing how much you've collected together
 *  2. the vault, everything shared with one person, as a photo grid or a conversation
 *     timeline, with a swipeable lightbox on top.
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
    memoryCounts: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isVaultOpen = selectedConnection != null && selectedConnection.connectionId.isNotBlank()

    BackHandler {
        if (isVaultOpen) {
            onSelectConnection(selectedConnection!!.copy(connectionId = ""))
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

    // Reactively track emoji_self directly from SharedPreferences (same source as GalaxyView)
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

    // Lightbox: the filtered list it opened from, plus which item to start on.
    var lightbox by remember { mutableStateOf<Pair<List<Scribble>, Int>?>(null) }
    LaunchedEffect(selectedConnection?.connectionId) { lightbox = null }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightTop, NightMid, NightBottom)))
    ) {
        NightSky()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .displayCutoutPadding()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            MemoriesTopBar(
                title = if (isVaultOpen) {
                    selectedConnection!!.partnerName.takeIf { it.isNotBlank() } ?: "Partner"
                } else {
                    "Memories"
                },
                subtitle = if (isVaultOpen) {
                    "${sparkles.size} ${if (sparkles.size == 1) "memory" else "memories"} together"
                } else {
                    "Everything you've shared"
                },
                isNested = isVaultOpen,
                onNavigate = {
                    if (isVaultOpen) onSelectConnection(selectedConnection!!.copy(connectionId = ""))
                    else onBack()
                }
            )

            AnimatedContent(
                targetState = isVaultOpen,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(tween(280)) { it / 6 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(220)) { -it / 8 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(tween(280)) { -it / 6 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(220)) { it / 8 } + fadeOut(tween(160)))
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "memoriesStage"
            ) { vaultOpen ->
                if (!vaultOpen) {
                    MemoriesHub(
                        connections = connections,
                        userAvatarUrl = userAvatarUrl,
                        memoryCounts = memoryCounts,
                        imageLoader = imageLoader,
                        onOpen = { conn ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSelectConnection(conn)
                        }
                    )
                } else {
                    val vaultPartner = selectedConnection!!
                    MemoryVault(
                        partner = vaultPartner,
                        userAvatarUrl = userAvatarUrl,
                        sparkles = sparkles,
                        imageLoader = imageLoader,
                        onReplayScribble = onReplayScribble,
                        onOpenLightbox = { list, index -> lightbox = list to index },
                        onSendSparkle = { onSendSparkle(vaultPartner) }
                    )
                }
            }
        }

        lightbox?.let { (items, startIndex) ->
            MemoryLightbox(
                memories = items,
                startIndex = startIndex,
                partnerName = selectedConnection?.partnerName?.takeIf { it.isNotBlank() } ?: "Partner",
                imageLoader = imageLoader,
                onReplay = onReplayScribble,
                onDismiss = { lightbox = null }
            )
        }
    }
}

// ── Chrome ────────────────────────────────────────────────────────────────────

/** A still, deterministic star field. Cheap: drawn once per size, never animated per-frame. */
@Composable
private fun NightSky() {
    val stars = remember {
        val rnd = java.util.Random(20260821L)
        List(56) { Triple(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat() * 1.6f) }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Theirs.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.18f, size.height * 0.16f),
                radius = size.width * 0.85f
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Violet.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 0.86f, size.height * 0.74f),
                radius = size.width * 0.9f
            )
        )
        stars.forEach { (fx, fy, r) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.10f + (r / 2.2f) * 0.22f),
                radius = r,
                center = Offset(fx * size.width, fy * size.height)
            )
        }
    }
}

@Composable
private fun MemoriesTopBar(
    title: String,
    subtitle: String,
    isNested: Boolean,
    onNavigate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Card.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, Hairline),
            modifier = Modifier.size(40.dp).clickable { onNavigate() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isNested) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                    contentDescription = if (isNested) "Back" else "Close",
                    tint = TextPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Hub: one shelf per person ─────────────────────────────────────────────────

@Composable
private fun MemoriesHub(
    connections: List<Connection>,
    userAvatarUrl: String,
    memoryCounts: Map<String, Int>,
    imageLoader: coil.ImageLoader,
    onOpen: (Connection) -> Unit
) {
    if (connections.isEmpty()) {
        EmptyState(
            emoji = "🌱",
            title = "No shelves yet",
            body = "Connect with someone in your galaxy — every doodle, photo and note you trade lands here.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val total = connections.sumOf { memoryCounts[it.connectionId] ?: 0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hub-hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Card.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, Hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AvatarBubble(
                        model = userAvatarUrl,
                        imageLoader = imageLoader,
                        size = 48.dp,
                        ring = Mine
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (total > 0) "$total ${if (total == 1) "memory" else "memories"} kept" else "Your archive is waiting",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Open a shelf to look back through everything.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        items(connections, key = { it.connectionId }) { conn ->
            PartnerShelf(
                connection = conn,
                count = memoryCounts[conn.connectionId] ?: 0,
                imageLoader = imageLoader,
                onOpen = { onOpen(conn) }
            )
        }
    }
}

@Composable
private fun PartnerShelf(
    connection: Connection,
    count: Int,
    imageLoader: coil.ImageLoader,
    onOpen: () -> Unit
) {
    val avatar = connection.partnerTwigiUrl?.takeIf { it.isNotBlank() }
        ?: connection.partnerEmojiUrl?.takeIf { it.isNotBlank() }
        ?: connection.partnerAvatarUrl?.takeIf { it.isNotBlank() }
        ?: TELEGRAM_EMOJIS.first()

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(22.dp),
        color = Card.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, Hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AvatarBubble(model = avatar, imageLoader = imageLoader, size = 54.dp, ring = Theirs)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = connection.partnerName.takeIf { it.isNotBlank() } ?: "Partner",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        count == 0 -> "Nothing shared yet"
                        count == 1 -> "1 memory"
                        else -> "$count memories"
                    },
                    color = if (count == 0) TextSecondary else Theirs,
                    fontSize = 12.sp,
                    fontWeight = if (count == 0) FontWeight.Normal else FontWeight.SemiBold
                )
            }

            Surface(shape = CircleShape, color = CardSoft, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open",
                        tint = Violet,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ── Vault: one person's archive ───────────────────────────────────────────────

@Composable
private fun MemoryVault(
    partner: Connection,
    userAvatarUrl: String,
    sparkles: List<Scribble>,
    imageLoader: coil.ImageLoader,
    onReplayScribble: (String) -> Unit,
    onOpenLightbox: (List<Scribble>, Int) -> Unit,
    onSendSparkle: () -> Unit
) {
    var filter by remember(partner.connectionId) { mutableStateOf(MemoryFilter.ALL) }
    var layout by remember(partner.connectionId) { mutableStateOf(VaultLayout.GRID) }

    val partnerAvatar = partner.partnerTwigiUrl?.takeIf { it.isNotBlank() }
        ?: partner.partnerEmojiUrl?.takeIf { it.isNotBlank() }
        ?: partner.partnerAvatarUrl?.takeIf { it.isNotBlank() }
        ?: TELEGRAM_EMOJIS.first()

    val photoCount = remember(sparkles) { sparkles.count { SparkleMedia.hasMedia(it) } }
    val doodleCount = remember(sparkles) { sparkles.count { SparkleMedia.isDoodle(it) } }
    val noteCount = remember(sparkles) { sparkles.count { SparkleMedia.hasNote(it) } }

    val visible = remember(sparkles, filter) {
        when (filter) {
            MemoryFilter.ALL -> sparkles
            MemoryFilter.PHOTOS -> sparkles.filter { SparkleMedia.hasMedia(it) }
            MemoryFilter.DOODLES -> sparkles.filter { SparkleMedia.isDoodle(it) }
            MemoryFilter.NOTES -> sparkles.filter { SparkleMedia.hasNote(it) }
        }.sortedByDescending { it.createdAt }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Bridge card: the two of you + what's inside
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Card.copy(alpha = 0.86f),
            border = BorderStroke(1.dp, Hairline),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarBubble(model = userAvatarUrl, imageLoader = imageLoader, size = 40.dp, ring = Mine)
                    Text("💫", fontSize = 13.sp)
                    AvatarBubble(model = partnerAvatar, imageLoader = imageLoader, size = 40.dp, ring = Theirs)

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = onSendSparkle,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Theirs),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("✨", fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("New sparkle", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("📸", photoCount, "photos")
                    StatChip("🎨", doodleCount, "doodles")
                    StatChip("💌", noteCount, "notes")
                }
            }
        }

        // Filters + layout switch
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                MemoryFilter.entries.forEach { option ->
                    val count = when (option) {
                        MemoryFilter.ALL -> sparkles.size
                        MemoryFilter.PHOTOS -> photoCount
                        MemoryFilter.DOODLES -> doodleCount
                        MemoryFilter.NOTES -> noteCount
                    }
                    FilterPill(
                        label = "${option.emoji} ${option.label}",
                        count = count,
                        selected = filter == option,
                        onClick = { filter = option }
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = CardSoft,
                border = BorderStroke(1.dp, Hairline)
            ) {
                Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    LayoutToggle(
                        selected = layout == VaultLayout.GRID,
                        icon = Icons.Default.GridView,
                        description = "Grid",
                        onClick = { layout = VaultLayout.GRID }
                    )
                    LayoutToggle(
                        selected = layout == VaultLayout.TIMELINE,
                        icon = Icons.Default.ViewAgenda,
                        description = "Timeline",
                        onClick = { layout = VaultLayout.TIMELINE }
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            EmptyState(
                emoji = if (sparkles.isEmpty()) "🌌" else filter.emoji,
                title = if (sparkles.isEmpty()) "Nothing here yet" else "No ${filter.label.lowercase()} yet",
                body = if (sparkles.isEmpty()) {
                    "Send ${partner.partnerName.ifBlank { "them" }} a sparkle, a doodle or a secret note — it will live here forever."
                } else {
                    "Switch filters to see the rest of your archive."
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            when (layout) {
                VaultLayout.GRID -> MemoryGrid(
                    memories = visible,
                    imageLoader = imageLoader,
                    onOpen = { index -> onOpenLightbox(visible, index) },
                    onReplay = onReplayScribble,
                    modifier = Modifier.weight(1f)
                )
                VaultLayout.TIMELINE -> MemoryTimeline(
                    memories = visible,
                    partnerName = partner.partnerName.ifBlank { "Partner" },
                    imageLoader = imageLoader,
                    onOpen = { index -> onOpenLightbox(visible, index) },
                    onReplay = onReplayScribble,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatChip(emoji: String, count: Int, label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardSoft,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(emoji, fontSize = 11.sp)
            Text("$count", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Theirs else CardSoft,
        border = BorderStroke(1.dp, if (selected) Theirs else Hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                color = if (selected) Color.White else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
            if (count > 0) {
                Text(
                    text = "$count",
                    color = if (selected) Color.White.copy(alpha = 0.85f) else Violet,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LayoutToggle(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Violet.copy(alpha = 0.22f) else Color.Transparent,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (selected) Violet else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ── Grid layout ───────────────────────────────────────────────────────────────

private sealed interface GridEntry {
    val key: String

    data class Header(val label: String) : GridEntry {
        override val key: String get() = "h:$label"
    }

    data class Cell(val memory: Scribble, val index: Int) : GridEntry {
        override val key: String get() = "c:${memory.scribbleId}"
    }
}

@Composable
private fun MemoryGrid(
    memories: List<Scribble>,
    imageLoader: coil.ImageLoader,
    onOpen: (Int) -> Unit,
    onReplay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = remember(memories) {
        buildList {
            var lastDay: String? = null
            memories.forEachIndexed { index, memory ->
                val day = dayKey(memory.createdAt)
                if (day != lastDay) {
                    add(GridEntry.Header(dayLabel(memory.createdAt)))
                    lastDay = day
                }
                add(GridEntry.Cell(memory, index))
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        gridItems(
            items = entries,
            key = { it.key },
            span = { entry -> if (entry is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
        ) { entry ->
            when (entry) {
                is GridEntry.Header -> DayHeader(entry.label, modifier = Modifier.padding(top = 6.dp))
                is GridEntry.Cell -> MemoryTile(
                    memory = entry.memory,
                    imageLoader = imageLoader,
                    onClick = {
                        if (SparkleMedia.hasMedia(entry.memory)) onOpen(entry.index)
                        else if (SparkleMedia.isDoodle(entry.memory)) onReplay(entry.memory.scribbleId)
                        else onOpen(entry.index)
                    }
                )
            }
        }
    }
}

@Composable
private fun MemoryTile(
    memory: Scribble,
    imageLoader: coil.ImageLoader,
    onClick: () -> Unit
) {
    val accent = if (memory.isSent) Mine else Theirs

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = CardSoft,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        modifier = Modifier.aspectRatio(0.82f)
    ) {
        Box {
            when {
                SparkleMedia.hasMedia(memory) -> MemoryImage(
                    memory = memory,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                SparkleMedia.isDoodle(memory) -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF221241), Color(0xFF120A24))))
                ) {
                    DoodleCanvas(memory, modifier = Modifier.fillMaxSize().padding(10.dp))
                }

                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF2A1440), Color(0xFF160C2A))))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = memory.secretMessage.orEmpty(),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom scrim carrying the sender + time so tiles stay readable over any photo
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent))
                Text(
                    text = timeLabel(memory.createdAt),
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                if (SparkleMedia.isDoodle(memory)) Text("🎨", fontSize = 10.sp)
                if (SparkleMedia.hasNote(memory) && SparkleMedia.hasMedia(memory)) Text("💌", fontSize = 10.sp)
            }
        }
    }
}

// ── Timeline layout ───────────────────────────────────────────────────────────

@Composable
private fun MemoryTimeline(
    memories: List<Scribble>,
    partnerName: String,
    imageLoader: coil.ImageLoader,
    onOpen: (Int) -> Unit,
    onReplay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(count = memories.size, key = { memories[it].scribbleId }) { index ->
            val memory = memories[index]
            val isFirstOfDay = index == 0 ||
                dayKey(memories[index - 1].createdAt) != dayKey(memory.createdAt)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isFirstOfDay) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DayHeader(dayLabel(memory.createdAt), modifier = Modifier.padding(top = 6.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (memory.isSent) Arrangement.End else Arrangement.Start
                ) {
                    MemoryBubble(
                        memory = memory,
                        senderLabel = if (memory.isSent) "You" else partnerName,
                        imageLoader = imageLoader,
                        onOpen = { onOpen(index) },
                        onReplay = { onReplay(memory.scribbleId) },
                        modifier = Modifier.fillMaxWidth(0.86f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryBubble(
    memory: Scribble,
    senderLabel: String,
    imageLoader: coil.ImageLoader,
    onOpen: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (memory.isSent) Mine else Theirs

    Surface(
        shape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = if (memory.isSent) 20.dp else 6.dp,
            bottomEnd = if (memory.isSent) 6.dp else 20.dp
        ),
        color = Card.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent))
                Text(senderLabel, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(timeLabel(memory.createdAt), color = TextSecondary, fontSize = 11.sp)
            }

            if (SparkleMedia.hasMedia(memory)) {
                MemoryImage(
                    memory = memory,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpen() }
                )
            }

            if (SparkleMedia.isDoodle(memory)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF160C2A),
                    border = BorderStroke(1.dp, Hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        DoodleCanvas(
                            memory,
                            modifier = Modifier.fillMaxWidth().height(140.dp).padding(10.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${memory.strokes.size} ${if (memory.strokes.size == 1) "stroke" else "strokes"}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onReplay, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Violet, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Replay", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (SparkleMedia.hasNote(memory)) {
                Text(
                    text = memory.secretMessage.orEmpty(),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

// ── Lightbox ──────────────────────────────────────────────────────────────────

@Composable
private fun MemoryLightbox(
    memories: List<Scribble>,
    startIndex: Int,
    partnerName: String,
    imageLoader: coil.ImageLoader,
    onReplay: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (memories.isEmpty()) return
    val safeStart = startIndex.coerceIn(0, memories.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeStart) { memories.size }
    val stamp = remember { SimpleDateFormat("EEEE, MMM d · h:mm a", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.97f))) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val memory = memories[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 56.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when {
                        SparkleMedia.hasMedia(memory) -> MemoryImage(
                            memory = memory,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(20.dp))
                        )

                        SparkleMedia.isDoodle(memory) -> Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF120A24),
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.9f)
                        ) {
                            DoodleCanvas(memory, modifier = Modifier.fillMaxSize().padding(18.dp))
                        }

                        else -> Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Card,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = memory.secretMessage.orEmpty(),
                                color = TextPrimary,
                                fontSize = 18.sp,
                                lineHeight = 26.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (SparkleMedia.hasNote(memory) && SparkleMedia.hasMedia(memory)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Card.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, Hairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💌 ${memory.secretMessage}",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Text(
                        text = "${if (memory.isSent) "You" else partnerName} · ${stamp.format(Date(memory.createdAt))}",
                        color = if (memory.isSent) Mine else Theirs,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    if (SparkleMedia.isDoodle(memory)) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onReplay(memory.scribbleId); onDismiss() },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Watch it drawn", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Counter + close
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.12f)) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${memories.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.size(38.dp).clickable { onDismiss() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

// ── Shared pieces ─────────────────────────────────────────────────────────────

/**
 * Renders a sparkle's picture. Goes through [SparkleMedia] so inline base64, absolute
 * URLs and relative server asset paths all resolve — handing Coil the raw column value
 * is what left the old gallery full of empty cards.
 */
@Composable
private fun MemoryImage(
    memory: Scribble,
    imageLoader: coil.ImageLoader,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val model = remember(memory.scribbleId, memory.mediaUrl, memory.mediaBase64) {
        SparkleMedia.resolve(memory)
    }

    if (model == null) {
        Box(modifier = modifier.background(CardSoft), contentAlignment = Alignment.Center) {
            Text("✨", fontSize = 22.sp)
        }
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            // ByteArray payloads have no natural cache key — anchor it to the row id.
            .memoryCacheKey("sparkle:${memory.scribbleId}")
            .crossfade(true)
            .build(),
        imageLoader = imageLoader,
        contentDescription = "Memory",
        contentScale = contentScale,
        modifier = modifier
    )
}

/** Draws a doodle's strokes directly from its normalised points — no bitmap needed. */
@Composable
private fun DoodleCanvas(memory: Scribble, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        memory.strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            val path = Path()
            stroke.points.forEachIndexed { i, point ->
                val x = point.x * size.width
                val y = point.y * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = Color(stroke.color).copy(alpha = stroke.opacity.coerceIn(0.15f, 1f)),
                style = Stroke(
                    width = (stroke.width * min(size.width, size.height) / 320f).coerceIn(1.5f, 14f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun AvatarBubble(
    model: String?,
    imageLoader: coil.ImageLoader,
    size: Dp,
    ring: Color
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(ring.copy(alpha = 0.55f), ring.copy(alpha = 0.12f), Color.Transparent))),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(ImageUtils.parseEmojiModel(model))
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.10f)
                .clip(CircleShape)
                .background(Color(0xFF160D2E))
        )
    }
}

@Composable
private fun DayHeader(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = CardSoft,
        border = BorderStroke(1.dp, Hairline),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = Violet,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(emoji, fontSize = 44.sp)
            Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(body, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        }
    }
}

// ── Date helpers ──────────────────────────────────────────────────────────────

private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val dayFullFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
private val dayWithYearFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

private fun dayKey(millis: Long): String = dayKeyFormat.format(Date(millis))

private fun timeLabel(millis: Long): String = timeFormat.format(Date(millis))

private fun dayLabel(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = if (sameYear) now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) else Int.MAX_VALUE
    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear && abs(dayDiff) < 7 -> dayFullFormat.format(Date(millis))
        sameYear -> dayFullFormat.format(Date(millis))
        else -> dayWithYearFormat.format(Date(millis))
    }
}
