@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class
)

package com.aman.gigi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.draw.blur


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.hapticfeedback.HapticFeedbackType as UiHapticFeedbackType

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.ImageLoader
import coil.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.ui.screensaver.components.GifPickerTray

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import androidx.compose.ui.zIndex
import com.aman.gigi.model.Connection
import com.aman.gigi.model.LoveCardDeck
import com.aman.gigi.model.LoveCardDeckItem
import com.aman.gigi.model.LoveCardDraftItem
import com.aman.gigi.model.LoveCardDraftResponse
import com.aman.gigi.model.LoveCardStickerPlacement
import com.aman.gigi.model.LoveCardStackStatus
import com.aman.gigi.model.LoveCardType
import com.skydoves.cloudy.Cloudy
import kotlin.math.roundToInt

object CardSoundEngine {
    private var soundPool: android.media.SoundPool? = null
    private var flipSoundId = 0
    private var revealSoundId = 0
    private var sendSoundId = 0
    private var isInitialized = false

    fun init(context: android.content.Context) {
        if (isInitialized) return
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = android.media.SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()
        
        flipSoundId = soundPool?.load(context, com.aman.gigi.R.raw.card_flip, 1) ?: 0
        revealSoundId = soundPool?.load(context, com.aman.gigi.R.raw.card_reveal, 1) ?: 0
        sendSoundId = soundPool?.load(context, com.aman.gigi.R.raw.card_send, 1) ?: 0
        isInitialized = true
    }

    fun playFlip() { soundPool?.play(flipSoundId, 1f, 1f, 1, 0, 1f) }
    fun playReveal() { soundPool?.play(revealSoundId, 1f, 1f, 1, 0, 1f) }
    fun playSend() { soundPool?.play(sendSoundId, 1f, 1f, 1, 0, 1f) }
}

@Composable
fun LoveCardsSection(
    currentPartner: Connection?,
    decks: List<LoveCardDeck>,
    onOpenGallery: () -> Unit,
    onOpenComposer: () -> Unit,
    onCreateStack: (title: String, cards: List<LoveCardDraftItem>) -> Unit = { _, _ -> },
    onOpenDeck: (LoveCardDeck) -> Unit,
    onAnswerDeck: (LoveCardDeck, List<LoveCardDraftResponse>) -> Unit,
    onShowDeck: (LoveCardDeck) -> Unit
) {
    val partnerDecks = remember(decks, currentPartner?.connectionId) {
        decks.filter { it.stack.connectionId == currentPartner?.connectionId }
    }
    val isDark = false

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        CardSoundEngine.init(context)
    }
    val answeredCount = remember(partnerDecks) {
        partnerDecks.count { it.stack.status == LoveCardStackStatus.ANSWERED.name }
    }

    FrostedLoveCardSurface(
        fillColor = if (isDark) Color(0xFF1A1030).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FrostedLoveCardSurface(
                shape = RoundedCornerShape(28.dp),
                fillColor = if (isDark) Color(0xFF201438).copy(alpha = 0.70f) else Color.White.copy(alpha = 0.18f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = if (isDark) Color(0xFF3D1A2E) else Color(0xFFFFD8EA).copy(alpha = 0.88f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = if (currentPartner == null) "Sweet little stacks" else "Stack, swipe, reply",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFFFB3D1) else Color(0xFFC14A8A)
                        )
                    }

                    Text(
                        text = "Love Cards",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) Color(0xFFD0AAFF) else Color(0xFF5A23D4)
                    )
                    Text(
                        text = if (currentPartner == null) {
                            "Connect with someone sweet to start sending little card decks."
                        } else {
                            "Make playful decks, sweet questions, and animated reply stacks for ${currentPartner.partnerName}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color(0xFFB0A8CC) else Color(0xFF685C82)
                    )

                    Button(
                        onClick = onOpenComposer,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF6B3FCC) else Color(0xFF8455FF),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (currentPartner == null) "Connect to unlock Love Cards"
                            else "Create card stack"
                        )
                    }
                }
            }

            FrostedLoveCardSurface(
                modifier = Modifier.clickable(onClick = onOpenGallery),
                shape = RoundedCornerShape(28.dp),
                fillColor = if (isDark) Color(0xFF1A1030).copy(alpha = 0.60f) else Color.White.copy(alpha = 0.16f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoveCardPosterTeaser(
                            accent = Color(0xFFFF8FBF),
                            background = listOf(Color(0xFFFFAFCF), Color(0xFFFFE0EF))
                        )
                        LoveCardPosterTeaser(
                            modifier = Modifier.offset(y = 8.dp),
                            accent = Color(0xFF6E60FF),
                            background = listOf(Color(0xFF3250FF), Color(0xFF29D6F6))
                        )
                        LoveCardPosterTeaser(
                            modifier = Modifier.rotate(8f),
                            accent = Color(0xFFFFC75D),
                            background = listOf(Color(0xFFFFB554), Color(0xFFFFE29F))
                        )
                    }
                    val galleryMessage = if (partnerDecks.isEmpty()) {
                        "Tap here or swipe right to open your Love Cards gallery. Once you send a deck, it will live there with all the replies."
                    } else {
                        "${partnerDecks.size} decks saved here, $answeredCount answered. Tap here or swipe right to open Sent with love and Answered."
                    }
                    Text(
                        text = galleryMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color(0xFFB0A8CC) else Color(0xFF6F6488)
                    )
                }
            }
        }
    }

    // No local Dialog anymore. It's moved to the root container in Developer.kt
    // (though for simplicity, we could also just wrap everything in this section with a Box)
}

@Composable
fun LoveCardGalleryDialog(
    currentPartner: Connection?,
    decks: List<LoveCardDeck>,
    onDismiss: () -> Unit,
    onOpenDeck: (LoveCardDeck) -> Unit,
    onShowDeck: (LoveCardDeck) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val partnerDecks = remember(decks, currentPartner?.connectionId) {
        decks.filter { it.stack.connectionId == currentPartner?.connectionId }
    }
    val visibleDecks = remember(partnerDecks, selectedTab) {
        when (selectedTab) {
            0 -> partnerDecks.filter { !it.stack.isIncoming }
            else -> partnerDecks.filter { it.stack.status == LoveCardStackStatus.ANSWERED.name }
        }
    }

    val isDarkGallery = false
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDarkGallery) Color(0xFF0D0F1A).copy(alpha = 0.85f)
                    else Color(0xFFF3ECFF).copy(alpha = 0.46f)
                )
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.84f),
                shape = RoundedCornerShape(34.dp),
                color = if (isDarkGallery) Color(0xFF1A1530).copy(alpha = 0.97f) else Color(0xFFF6F1FF).copy(alpha = 0.95f),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Love card gallery",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDarkGallery) Color(0xFFD8C8FF) else Color(0xFF4E21CA)
                            )
                            Text(
                                text = currentPartner?.partnerName?.let { "A little deck room for $it." }
                                    ?: "Choose a partner to start collecting decks.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDarkGallery) Color(0xFFB0A8D0) else Color(0xFF72678A)
                            )
                        }
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            label = { Text("Sent with love") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            label = { Text("Answered") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (visibleDecks.isEmpty()) {
                        EmptyLoveCardsState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            item {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    visibleDecks.forEach { deck ->
                                        LoveCardDeckPreview(
                                            deck = deck,
                                            onClick = {
                                                if (deck.stack.isIncoming && deck.stack.status == LoveCardStackStatus.SENT.name) {
                                                    onOpenDeck(deck)
                                                }
                                                onShowDeck(deck)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLoveCardsState() {
    val isDark = false
    FrostedLoveCardSurface(
        shape = RoundedCornerShape(28.dp),
        fillColor = if (isDark) Color(0xFF1A1030).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LoveCardPosterTeaser(
                    modifier = Modifier.rotate(-8f),
                    accent = Color(0xFFFF8FBF),
                    background = listOf(Color(0xFFFFAFCF), Color(0xFFFFE0EF))
                )
                LoveCardPosterTeaser(
                    modifier = Modifier.offset(y = 8.dp),
                    accent = Color(0xFF6E60FF),
                    background = listOf(Color(0xFF3250FF), Color(0xFF29D6F6))
                )
                LoveCardPosterTeaser(
                    modifier = Modifier.rotate(8f),
                    accent = Color(0xFFFFC75D),
                    background = listOf(Color(0xFFFFB554), Color(0xFFFFE29F))
                )
            }
            Text(
                text = "No card decks yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDark) Color(0xFFD0AAFF) else Color(0xFF36215F)
            )
            Text(
                text = "Start with a cute note, a question game, or a tiny gift-card deck. Your partner can swipe through the stack and answer each one.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color(0xFFB0A8CC) else Color(0xFF72698C)
            )

        }
    }
}

@Composable
private fun LoveCardDeckPreview(
    deck: LoveCardDeck,
    onClick: () -> Unit
) {
    val float = rememberInfiniteTransition(label = "love-card-float")
    val bob by float.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "deck-bob"
    )

    val cardType = enumValueOfSafe(deck.items.firstOrNull()?.card?.type ?: LoveCardType.CUTE_NOTE.name)
    val theme = loveCardThemeFor(cardType, deck.stack.theme ?: deck.items.firstOrNull()?.card?.theme)
    Column(
        modifier = Modifier
            .widthIn(min = 118.dp, max = 132.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = bob.dp)
            ) {
                StackedDreamLayer(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 8.dp, y = 6.dp)
                        .rotate(5f),
                    color = theme.backLayerTwo
                )
                StackedDreamLayer(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 4.dp, y = 2.dp)
                        .rotate(-3f),
                    color = theme.backLayerOne
                )
                Surface(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(30.dp),
                    color = Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                Brush.verticalGradient(colors = theme.background)
                            )
                    ) {
                        DreamyCardBackground(
                            modifier = Modifier.matchParentSize(),
                            theme = theme
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = theme.badgeFill
                        ) {
                            Text(
                                text = "${deck.totalCount} cards",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.badgeText
                            )
                        }
                        Text(
                            text = deck.stack.title,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 14.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = theme.primaryText
                        )
                    }
                }
            }
        }

        DeckStatusChip(deck)
        val deckIsDark = false
        Text(
            text = deck.stack.previewText ?: "Swipe through the little stack and answer with love.",
            style = MaterialTheme.typography.bodySmall,
            color = if (deckIsDark) Color(0xFFB0A8CC) else Color(0xFF6F6488)
        )
        Text(
            text = "${deck.answeredCount} answered",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7A46FF)
        )
    }
}

@Composable
private fun LoveCardPosterTeaser(
    modifier: Modifier = Modifier,
    accent: Color,
    background: List<Color>
) {
    Surface(
        modifier = modifier.size(width = 78.dp, height = 112.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(background))
                .border(1.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(24.dp))
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = accent.copy(alpha = 0.42f),
                    radius = size.minDimension * 0.28f,
                    center = Offset(size.width * 0.74f, size.height * 0.22f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.92f),
                    radius = size.minDimension * 0.12f,
                    center = Offset(size.width * 0.32f, size.height * 0.24f)
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.92f),
                    topLeft = Offset(size.width * 0.2f, size.height * 0.24f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.08f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f)
                )
            }
        }
    }
}

@Composable
private fun FrostedLoveCardSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(32.dp),
    fillColor: Color = Color.White.copy(alpha = 0.16f),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = false
    Box(
        modifier = modifier
            .clip(shape)
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.28f),
                shape
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = if (isDark) listOf(
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent
                        ) else listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        center = Offset(240f, 120f),
                        radius = 520f
                    )
                )
        )
        Cloudy(radius = 22) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (isDark) listOf(
                                Color(0xFF180C30).copy(alpha = 0.55f),
                                Color(0xFF1A1030).copy(alpha = 0.40f)
                            ) else listOf(
                                Color.White.copy(alpha = 0.14f),
                                Color(0xFFF8F1FF).copy(alpha = 0.1f)
                            )
                        )
                    )
            )
        }
        Surface(
            color = fillColor,
            shape = shape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun LoveCardMiniPreview(
    item: LoveCardDeckItem,
    modifier: Modifier = Modifier
) {
    val palette = remember(item.card.type) { paletteFor(item.card.type) }
    Surface(
        modifier = modifier.size(width = 82.dp, height = 110.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(palette.first, palette.second)
                    )
                )
                .padding(12.dp)
        ) {
            Icon(
                imageVector = iconFor(item.card.type),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Text(
                text = item.card.prompt.take(44),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
private fun DeckStatusChip(deck: LoveCardDeck) {
    val (text, tint) = when {
        deck.stack.status == LoveCardStackStatus.ANSWERED.name -> "Answered" to Color(0xFF15A06E)
        deck.stack.isIncoming && deck.answeredCount == 0 -> "Waiting on you" to Color(0xFFEC6A8F)
        deck.stack.isIncoming -> "In progress" to Color(0xFF8C5CFF)
        else -> "Sent with love" to Color(0xFF6D4CFF)
    }
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

@Composable
fun LoveCardComposerOverlay(
    partnerName: String,
    onDismiss: () -> Unit,
    onSend: (String, Long?, List<LoveCardDraftItem>) -> Unit
) {
    var title by remember { mutableStateOf("A little deck for $partnerName") }
    val cards = remember {
        mutableStateListOf(
            LoveCardDraftItem(
                type = LoveCardType.CUTE_NOTE,
                prompt = "A tiny note to start us off",
                theme = "blush",
                decorations = emptyList()
            )
        )
    }
    var selectedIndex by remember { mutableStateOf(0) }
    var showStickerPalette by remember { mutableStateOf(false) }
    var selectedStickerId by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var isTimeCapsule by remember { mutableStateOf(false) }
    var unlockDate by remember { mutableStateOf<Long?>(null) }
    val activeCard = cards.getOrNull(selectedIndex)
    var showTypeGallery by remember { mutableStateOf(false) }
    val canSend = title.isNotBlank() && cards.all { it.prompt.isNotBlank() }
    
    // Keyboard visibility detection
    val isKeyboardOpen = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    fun updateActiveCard(transform: (LoveCardDraftItem) -> LoveCardDraftItem) {
        val current = cards.getOrNull(selectedIndex) ?: return
        cards[selectedIndex] = transform(current)
    }

    val isDarkComposer = false
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDarkComposer) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D0820).copy(alpha = 0.96f),
                            Color(0xFF160D30).copy(alpha = 0.96f),
                            Color(0xFF0D0820).copy(alpha = 0.96f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF0F6).copy(alpha = 0.92f),
                            Color(0xFFF4EEFF).copy(alpha = 0.92f),
                            Color(0xFFFFF0F6).copy(alpha = 0.92f)
                        )
                    )
                }
            )
            .clickable(enabled = false) { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            AnimatedVisibility(
                visible = !isKeyboardOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.zIndex(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        LoveCardTextField(
                            value = title,
                            onValueChange = { title = it.take(48) },
                            placeholder = "Give it a title...",
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                color = if (isDarkComposer) Color(0xFFD8C8FF) else Color(0xFF5120CC),
                                fontWeight = FontWeight.ExtraBold
                            ),
                            cursorBrush = SolidColor(if (isDarkComposer) Color(0xFFB0A0FF) else Color(0xFF5120CC))
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Card ${selectedIndex + 1} of ${cards.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDarkComposer) Color(0xFF9A8FBD) else Color(0xFF6C6882)
                            )
                            if (isTimeCapsule) {
                                Text(
                                    text = "• Unlocks in 24h",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF8455FF)
                                )
                            }
                        }
                    }
                    
                    Surface(
                        modifier = Modifier.padding(end = 12.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = if (isTimeCapsule) Color(0xFF8455FF) else Color(0xFFEFE5FF),
                        onClick = {
                            isTimeCapsule = !isTimeCapsule
                            unlockDate = if (isTimeCapsule) System.currentTimeMillis() + 24 * 60 * 60 * 1000L else null
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isTimeCapsule) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isTimeCapsule) Color.White else Color(0xFF8455FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Time Capsule",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isTimeCapsule) Color.White else Color(0xFF8455FF)
                            )
                        }
                    }
                }
            }


            // Main Card Area
            Box(
                modifier = (if (isKeyboardOpen) {
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                } else {
                    Modifier
                        .fillMaxSize()
                }).padding(top = if (isKeyboardOpen) 0.dp else 75.dp).zIndex(100f),
                contentAlignment = Alignment.Center
            ) {
                if (activeCard != null) {
                    LoveCard2DCarousel(
                        item = activeCard,
                        onTypeChange = { newType -> 
                            updateActiveCard { current ->
                                val defaults = defaultDraftCard(newType)
                                current.copy(
                                    type = newType,
                                    prompt = defaults.prompt,
                                    choices = defaults.choices
                                )
                            }
                        },
                        onThemeChange = { newTheme -> updateActiveCard { it.copy(theme = newTheme) } },
                        onAnimationStyleChange = { newStyle -> updateActiveCard { it.copy(animationStyle = newStyle) } },
                        onSelectSticker = { selectedStickerId = it },
                        onMoveSticker = { id, dx, dy ->
                            updateActiveCard { current ->
                                val updatedDecorations = current.decorations.map { decoration ->
                                    if (decoration.id == id) {
                                        decoration.copy(
                                            normalizedX = (decoration.normalizedX + dx).coerceIn(0f, 1f),
                                            normalizedY = (decoration.normalizedY + dy).coerceIn(0f, 1f)
                                        )
                                    } else decoration
                                }
                                current.copy(decorations = updatedDecorations)
                            }
                        },
                        onScaleSticker = { id, scaleMultiplier ->
                            updateActiveCard { current ->
                                val updatedDecorations = current.decorations.map { decoration ->
                                    if (decoration.id == id) {
                                        decoration.copy(
                                            scale = (decoration.scale * scaleMultiplier).coerceIn(0.2f, 5.0f)
                                        )
                                    } else decoration
                                }
                                current.copy(decorations = updatedDecorations)
                            }
                        },
                        onShowStickerPalette = { showStickerPalette = true },
                        selectedStickerId = selectedStickerId,
                        onDeleteSticker = { id -> updateActiveCard { it.copy(decorations = it.decorations.filter { d -> d.id != id }) } },
                        onMediaAttached = { mediaType, uri ->
                            updateActiveCard { current ->
                                val existing = current.decorations.firstOrNull { it.style == mediaType }
                                if (existing != null) {
                                    val updated = current.decorations.map { if (it.id == existing.id) it.copy(mediaUrl = uri) else it }
                                    current.copy(decorations = updated)
                                } else {
                                    current.copy(decorations = current.decorations + LoveCardStickerPlacement(content = "", normalizedX = 0.5f, normalizedY = 0.5f, style = mediaType, mediaUrl = uri))
                                }
                            }
                        },
                        onChoicesChange = { newChoices -> updateActiveCard { it.copy(choices = newChoices) } },
                        onPromptChange = { newPrompt -> updateActiveCard { it.copy(prompt = newPrompt) } }
                    )
                }


                // Thumbnail Rail (Inside Main Box align bottom center)
                if (!isKeyboardOpen) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 96.dp)
                            .background(if (isDarkComposer) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                            .border(1.dp, if (isDarkComposer) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (index in cards.indices) {
                            val item = cards[index]
                            val selected = index == selectedIndex
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 46.dp else 38.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Brush.verticalGradient(loveCardThemeFor(item.type, item.theme).background))
                                    .border(if (selected) 2.dp else 0.dp, Color(0xFF8455FF), RoundedCornerShape(14.dp))
                                    .clickable { selectedIndex = index }
                            )
                        }

                        IconButton(
                            onClick = {
                                val plan = com.aman.gigi.utils.AppConfig.userPlan
                                if (cards.size >= plan.maxCardsPerStack) {
                                    showUpgradeSheet = true
                                } else {
                                    val allTypes = LoveCardType.values()
                                    val nextType = allTypes[cards.size % allTypes.size]
                                    cards += defaultDraftCard(nextType)
                                    selectedIndex = cards.lastIndex
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF8455FF))
                        }
                    }
                }


            }
        }

        // Floating Emoji/Sticker Trigger (Fixed Page Position)
        val isKeyboardOpenInside = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
        AnimatedVisibility(
            visible = !isKeyboardOpenInside,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = if (!isKeyboardOpen) 110.dp else 20.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .clickable(onClick = { showStickerPalette = true })
                    .shadow(12.dp, RoundedCornerShape(999.dp)),
                shape = RoundedCornerShape(999.dp),
                color = if (isDarkComposer) Color(0xFF2A2040).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.9f),
                border = BorderStroke(2.dp, if (isDarkComposer) Color.White.copy(alpha = 0.25f) else Color.White)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✨", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }

        // Dedicated Floating Icon for Effects
        var showEffectsMenu by remember { mutableStateOf(false) }
        AnimatedVisibility(
            visible = !isKeyboardOpenInside,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = if (!isKeyboardOpen) 110.dp else 20.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box {
                val effectActive = (activeCard?.animationStyle ?: "none") != "none"
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clickable(onClick = { showEffectsMenu = !showEffectsMenu })
                        .shadow(12.dp, RoundedCornerShape(999.dp)),
                    shape = RoundedCornerShape(999.dp),
                    color = if (effectActive) Color(0xFF8B5CF6).copy(alpha = 0.9f)
                           else if (isDarkComposer) Color(0xFF2A2040).copy(alpha = 0.9f)
                           else Color.White.copy(alpha = 0.9f),
                    border = BorderStroke(2.dp, if (effectActive) Color(0xFF7C3AED) else if (isDarkComposer) Color.White.copy(alpha = 0.25f) else Color.White)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🪄", style = MaterialTheme.typography.headlineMedium)
                    }
                }

                DropdownMenu(
                    expanded = showEffectsMenu,
                    onDismissRequest = { showEffectsMenu = false },
                    modifier = Modifier.background(
                        if (isDarkComposer) Color(0xFF1A1530) else Color.White,
                        shape = RoundedCornerShape(16.dp)
                    )
                ) {
                    val styles = listOf(
                        "none" to "❌ None",
                        "burst" to "💖 Burst",
                        "sparkle" to "✨ Spark",
                        "ocean" to "🫧 Ocean",
                        "nature" to "🐝 Nature"
                    )
                    styles.forEach { (styleKey, label) ->
                        val isCurrentStyle = styleKey == (activeCard?.animationStyle ?: "none")
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = if (isCurrentStyle) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = if (isCurrentStyle) Color(0xFF6B3BF0) else Color(0xFF4C248D)
                                    )
                                    if (isCurrentStyle) {
                                        Text(
                                            text = "✓",
                                            color = Color(0xFF6B3BF0),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                showEffectsMenu = false
                                updateActiveCard { current ->
                                    current.copy(animationStyle = styleKey)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Send Button
        AnimatedVisibility(
            visible = !isKeyboardOpen,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).padding(horizontal = 16.dp),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Button(
                onClick = { 
                    if (!isSending) {
                        isSending = true
                        CardSoundEngine.playSend()
                        onSend(title, unlockDate, cards.toList())
                    }
                },
                enabled = canSend && !isSending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF699E),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFFF699E).copy(alpha = 0.5f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("❤️", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Send this stack with love",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }




        // Type Gallery Overlay
        AnimatedVisibility(
            visible = showTypeGallery,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            LoveCardTypeGallery(
                onTypeSelected = { selectedType ->
                    updateActiveCard { current ->
                        val defaults = defaultDraftCard(selectedType)
                        current.copy(
                            type = selectedType,
                            prompt = defaults.prompt,
                            choices = defaults.choices
                        )
                    }
                    showTypeGallery = false
                },
                onDismiss = { showTypeGallery = false }
            )
        }

        if (showStickerPalette && activeCard != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f) // Absolute top layering
                    .background(Color.Black.copy(alpha = 0.08f))
            ) {
                LoveCardStickerPalette(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 18.dp, vertical = 20.dp)
                        .safeDrawingPadding(),
                    onDismiss = { showStickerPalette = false },
                    onSelect = { sticker ->
                        val placement = LoveCardStickerPlacement(
                            id = java.util.UUID.randomUUID().toString(),
                            content = sticker.content,
                            normalizedX = 0.5f,
                            normalizedY = 0.5f,
                            style = sticker.style,
                            mediaUrl = sticker.mediaUrl
                        )
                        updateActiveCard { current ->
                            current.copy(decorations = (current.decorations + placement).take(8))
                        }
                        selectedStickerId = placement.id
                    }
                )
            }
        }

        // Close Button - Final 'Premium' style
        AnimatedVisibility(
            visible = !isKeyboardOpen,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 20.dp, end = 20.dp)
                    .size(44.dp)
                    .background(if (isDarkComposer) Color(0xFF2A2040) else Color.White, RoundedCornerShape(999.dp))
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(999.dp), ambientColor = Color(0xFF6C3CF0).copy(alpha = 0.2f), spotColor = Color(0xFF6C3CF0).copy(alpha = 0.2f), clip = false),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF8455FF),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    if (showUpgradeSheet) {
        com.aman.gigi.ui.components.UpgradeSheet(
            featureName = "More Cards per Stack",
            featureDescription = "Add up to ${if (com.aman.gigi.utils.AppConfig.userPlan.upgradeTarget == "Plus") "5" else "10"} cards in a single love card stack.",
            onDismiss = { showUpgradeSheet = false }
        )
    }
}

@Composable
private fun LoveCard2DCarousel(
    item: LoveCardDraftItem,
    onTypeChange: (LoveCardType) -> Unit,
    onThemeChange: (String) -> Unit,
    onSelectSticker: (String?) -> Unit,
    onMoveSticker: (String, Float, Float) -> Unit,
    onShowStickerPalette: () -> Unit,
    selectedStickerId: String?,
    onMediaAttached: (String, String) -> Unit = { _, _ -> },
    onChoicesChange: (List<String>) -> Unit = {},
    onAnimationStyleChange: (String) -> Unit = {},
    onScaleSticker: (String, Float) -> Unit = { _, _ -> },
    onDeleteSticker: (String) -> Unit = {},
    onPromptChange: (String) -> Unit = {}
) {
    val allTypes = LoveCardType.values()
    val themes = storyThemeOptions()
    
    val virtualTypeCount = 10000 
    val virtualThemeCount = 10000
    
    val initialTypePage = (virtualTypeCount / 2) - ((virtualTypeCount / 2) % allTypes.size) + allTypes.indexOf(item.type).coerceAtLeast(0)
    val typePagerState = rememberPagerState(initialPage = initialTypePage) { virtualTypeCount }
    val isKeyboardOpenInside = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    LaunchedEffect(typePagerState.currentPage) {

        val nextType = allTypes[typePagerState.currentPage % allTypes.size]
        if (nextType != item.type) {
            onTypeChange(nextType)
        }
    }

    Box(
        modifier = if (isKeyboardOpenInside) Modifier.fillMaxWidth().wrapContentHeight() else Modifier.fillMaxSize(),
        contentAlignment = if (isKeyboardOpenInside) Alignment.TopCenter else Alignment.Center
    ) {
        HorizontalPager(
            state = typePagerState,
            modifier = if (isKeyboardOpenInside) Modifier.fillMaxWidth().wrapContentHeight() else Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (isKeyboardOpenInside) 0.dp else 42.dp),
            pageSpacing = 0.dp,
            beyondViewportPageCount = 1
        ) { typePageIndex ->
            val pageType = allTypes[typePageIndex % allTypes.size]
            
            val localInitialThemePage = remember { (virtualThemeCount / 2) - ((virtualThemeCount / 2) % themes.size) + themes.indexOfFirst { it.key == item.theme }.coerceAtLeast(0) }
            val localThemePagerState = rememberPagerState(initialPage = localInitialThemePage) { virtualThemeCount }
            
            LaunchedEffect(localThemePagerState.currentPage, typePagerState.currentPage == typePageIndex) {
                if (typePagerState.currentPage == typePageIndex) {
                    val nextTheme = themes[localThemePagerState.currentPage % themes.size].key
                    if (nextTheme != item.theme) {
                        onThemeChange(nextTheme)
                    }
                }
            }
            
            LaunchedEffect(item.theme) {
                val currentThemeKey = themes[localThemePagerState.currentPage % themes.size].key
                if (currentThemeKey != item.theme) {
                    val targetPage = (virtualThemeCount / 2) - ((virtualThemeCount / 2) % themes.size) + themes.indexOfFirst { it.key == item.theme }.coerceAtLeast(0)
                    localThemePagerState.scrollToPage(targetPage)
                }
            }
            
            var isThemeUnlocked by remember { mutableStateOf(false) }
            var isCardLong by remember { mutableStateOf(false) }

            VerticalPager(

                state = localThemePagerState,
                modifier = if (isKeyboardOpenInside) Modifier.fillMaxWidth().wrapContentHeight() else Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = if (isKeyboardOpenInside) 0.dp else 42.dp),
                pageSpacing = 0.dp,
                beyondViewportPageCount = 1,
                userScrollEnabled = !isKeyboardOpenInside

            ) { themePageIndex ->

                val pageThemeKey = themes[themePageIndex % themes.size].key
                
                val displayItem = remember(item, pageType, pageThemeKey) {
                    item.copy(type = pageType, theme = pageThemeKey)
                }
                
                val typeOffset = (typePageIndex - typePagerState.currentPage) - typePagerState.currentPageOffsetFraction
                val themeOffset = (themePageIndex - localThemePagerState.currentPage) - localThemePagerState.currentPageOffsetFraction
                
                val absTypeOffset = kotlin.math.abs(typeOffset)
                val absThemeOffset = kotlin.math.abs(themeOffset)
                val activeOffset = if (absTypeOffset > absThemeOffset) typeOffset else themeOffset
                val absActiveOffset = kotlin.math.abs(activeOffset)

                val scale = 1f - (absActiveOffset * 0.15f)
                val alpha = if (isKeyboardOpenInside && absActiveOffset > 0.01f) 0f else 1f - (absActiveOffset * 0.3f)
                val rotation = activeOffset * 9f


                val scrollState = rememberScrollState()
                val hapticManager = androidx.compose.ui.platform.LocalHapticFeedback.current

                
                LaunchedEffect(scrollState.maxValue) {
                    isCardLong = scrollState.maxValue > 0
                }
                
                LaunchedEffect(localThemePagerState.currentPage) {
                    isThemeUnlocked = false
                }
                
                val currentTheme = loveCardThemeFor(pageType, pageThemeKey)

                Box(
                    modifier = if (isKeyboardOpenInside) Modifier.fillMaxWidth().wrapContentHeight() else Modifier.fillMaxSize().padding(vertical = 40.dp)
                ) {

                    Column(
                        modifier = if (isKeyboardOpenInside) Modifier.fillMaxWidth().wrapContentHeight().verticalScroll(scrollState) else Modifier.fillMaxSize().verticalScroll(scrollState),

                        verticalArrangement = if (isKeyboardOpenInside) Arrangement.Top else Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LoveCardEditorCanvas(
                            item = displayItem,
                            modifier = Modifier
                                .fillMaxWidth(0.90f) 
                                .graphicsLayer {
                                    scaleX = scale.coerceAtLeast(0.7f)
                                    scaleY = scale.coerceAtLeast(0.7f)
                                    this.alpha = alpha.coerceIn(0f, 1f)
                                    rotationZ = rotation
                                },
                            selectedStickerId = selectedStickerId,
                            onSelectSticker = onSelectSticker,
                            onMoveSticker = onMoveSticker,
                            onScaleSticker = onScaleSticker,
                            onShowStickerPalette = onShowStickerPalette,
                            onMediaAttached = onMediaAttached,
                            onPromptChange = onPromptChange,
                            onChoicesChange = onChoicesChange,
                            onAnimationStyleChange = onAnimationStyleChange,
                            onDeleteSticker = onDeleteSticker
                        )
                        


                    }



                }

            }
        }
        
        if (!isKeyboardOpenInside) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("←", color = Color(0xFF8455FF).copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("→", color = Color(0xFF8455FF).copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("↑", color = Color(0xFF8455FF).copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("↓", color = Color(0xFF8455FF).copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
private fun ComposerCardThumb(
    item: LoveCardDraftItem,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val theme = loveCardThemeFor(item.type, item.theme)
    Surface(
        modifier = Modifier
            .size(width = 80.dp, height = 104.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) Color(0xFF8A5BFF) else Color.White.copy(alpha = 0.7f)
        ),
        shadowElevation = if (selected) 10.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(theme.background))
                .padding(10.dp)
        ) {
            LoveCardStickerRender(
                decorations = item.decorations.take(2),
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.34f),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = theme.primaryText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = item.prompt.take(30),
                modifier = Modifier.align(Alignment.BottomStart),
                color = theme.primaryText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun StoryCardEditorPanel(
    item: LoveCardDraftItem,
    onPromptChange: (String) -> Unit,
    onChoiceChange: (Int, String) -> Unit,
    onOpenStickerPalette: () -> Unit
) {
    FrostedLoveCardSurface(
        fillColor = Color.White.copy(alpha = 0.2f),
        shape = RoundedCornerShape(30.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Card details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4F22CE)
                    )
                    Text(
                        text = "Everything you edit here updates the card live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF706684)
                    )
                }
                Surface(
                    modifier = Modifier.clickable(onClick = onOpenStickerPalette),
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFFFEAF4),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✨", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Stickers",
                            color = Color(0xFFC04A88),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            OutlinedTextField(
                value = item.prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Prompt on card") },
                shape = RoundedCornerShape(22.dp)
            )

            if (item.type == LoveCardType.MULTIPLE_CHOICE) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val choices = item.choices.ifEmpty { listOf("Yes", "Maybe", "Definitely", "Tell me more") }
                    repeat(4) { index ->
                        OutlinedTextField(
                            value = choices.getOrNull(index).orEmpty(),
                            onValueChange = { onChoiceChange(index, it) },
                            modifier = Modifier.widthIn(min = 140.dp, max = 180.dp),
                            label = { Text("Choice ${index + 1}") },
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.52f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = when (item.type) {
                            LoveCardType.CUTE_NOTE -> "This card opens like a dreamy little love note with floating sticker vibes."
                            LoveCardType.QUESTION -> "This one asks a sweet question and leaves space for an answer."
                            LoveCardType.ANIMATED_GIFT -> "Your partner gets a cute ribbon gift box and taps it open for the surprise."
                            else -> "Make it sweet."
                        },
                        modifier = Modifier.padding(14.dp),
                        color = Color(0xFF6F6488),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryTypeRail(
    item: LoveCardDraftItem,
    onTypeSelected: (LoveCardType) -> Unit,
    onThemeSelected: (String) -> Unit,
    onAddCard: () -> Unit,
    onDuplicateCard: () -> Unit,
    onDeleteCard: () -> Unit
) {
    FrostedLoveCardSurface(
        modifier = Modifier.width(94.dp),
        fillColor = Color.White.copy(alpha = 0.18f),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Type",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6D38E8)
            )
                LoveCardType.values().forEach { type ->
                val selected = item.type == type
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTypeSelected(type) },
                    shape = RoundedCornerShape(24.dp),
                    color = if (selected) Color(0xFF8455FF) else Color.White.copy(alpha = 0.52f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.0f else 0.72f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = iconFor(type),
                            contentDescription = null,
                            tint = if (selected) Color.White else Color(0xFF6F3DF0)
                        )
                        Text(
                            text = typeLabel(type).replace(" ", "\n"),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else Color(0xFF6F3DF0)
                        )
                    }
                }
            }

            Text(
                text = "Theme",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6D38E8)
            )
            storyThemeOptions().forEach { option ->
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { onThemeSelected(option.key) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        if (item.theme == option.key) 2.dp else 1.dp,
                        if (item.theme == option.key) Color(0xFF7C49FF) else Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(option.swatch))
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            StoryRailAction("＋", onAddCard)
            StoryRailAction("⧉", onDuplicateCard)
            StoryRailAction("−", onDeleteCard)
        }
    }
}

@Composable
private fun StoryRailAction(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color(0xFF6F3DF0),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun LoveCardEditorCanvas(
    item: LoveCardDraftItem,
    selectedStickerId: String? = null,
    onSelectSticker: (String?) -> Unit = {},
    onMoveSticker: (String, Float, Float) -> Unit = { _, _, _ -> },
    onPromptChange: (String) -> Unit = {},
    onChoicesChange: (List<String>) -> Unit = {},
    onShowStickerPalette: () -> Unit = {},
    onSwipeHorizontal: (Float) -> Unit = {},
    onSwipeVertical: (Float) -> Unit = {},
    onMediaAttached: (String, String) -> Unit = { _, _ -> },
    editable: Boolean = true,
    canAnswer: Boolean = true,
    answeringResponse: LoveCardDraftResponse? = null,
    onResponseChange: (LoveCardDraftResponse) -> Unit = {},
    onAnimationStyleChange: (String) -> Unit = {},
    onScaleSticker: (String, Float) -> Unit = { _, _ -> },
    onDeleteSticker: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = loveCardThemeFor(item.type, item.theme)


    Box(
        modifier = modifier
            .widthIn(max = 340.dp)
            .wrapContentHeight()
            .defaultMinSize(minHeight = 440.dp)

            .background(Color.Transparent), // Explicitly transparent
        contentAlignment = Alignment.Center
    ) {
        StackedDreamLayer(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 12.dp, y = 10.dp)
                .rotate(6f),
            color = theme.backLayerTwo.copy(alpha = 0.8f)
        )
        StackedDreamLayer(
            modifier = Modifier
                .matchParentSize()
                .offset(x = (-10).dp, y = 4.dp)
                .rotate(-6f),
            color = theme.backLayerOne.copy(alpha = 0.85f)
        )

        Surface(
            modifier = Modifier.wrapContentHeight().fillMaxWidth(),
            shape = RoundedCornerShape(40.dp),
            color = Color.Transparent,
            shadowElevation = 0.dp
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(40.dp))
                    .background(Brush.verticalGradient(theme.background))
            ) {

                DreamyCardBackground(
                    modifier = Modifier.matchParentSize(),
                    theme = theme
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight() // Allow natural growth
                        .padding(horizontal = 22.dp, vertical = 24.dp)
                        .defaultMinSize(minHeight = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp) // Spaced for stacking
                ) {

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = theme.badgeFill,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
                            ) {
                                Text(
                                    text = typeLabel(item.type),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    color = theme.badgeText,
                                    fontWeight = FontWeight.Bold
                                )
                            }




                        // STABLE KEY: Use Only the type.name to ensure the editor surface is stable
                        // across text/decoration updates, preventing the keyboard from closing on every keystroke.
                        key(item.type.name) {
                        AnimatedContent(
                            targetState = item.type,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                            label = "CardTypeTransition"
                        ) { targetType ->
                            if (answeringResponse != null) {
                                // Unified Answering View
                                LoveCardAnsweringInternal(
                                    type = targetType,
                                    theme = theme,
                                    prompt = item.prompt,
                                    choices = item.choices,
                                    response = answeringResponse,
                                    onResponseChange = onResponseChange,
                                    canAnswer = canAnswer
                                )
                            } else {
                                // Standard Composer/View
                                when (targetType) {
                                    LoveCardType.ANIMATED_GIFT -> ComposerGiftBox(theme = theme, prompt = item.prompt, onPromptChange = onPromptChange)
                                    LoveCardType.MULTIPLE_CHOICE -> ComposerChoiceCard(theme = theme, prompt = item.prompt, choices = item.choices, onPromptChange = onPromptChange, onChoicesChange = onChoicesChange)
                                    LoveCardType.QUESTION -> ComposerQuestionCard(theme = theme, prompt = item.prompt, choices = item.choices, onPromptChange = onPromptChange, onChoicesChange = onChoicesChange)

                                    LoveCardType.CUTE_NOTE -> ComposerNoteCard(theme = theme, prompt = item.prompt, choices = item.choices, onPromptChange = onPromptChange, onChoicesChange = onChoicesChange)
                                    LoveCardType.PHOTO_MEMORY -> ComposerPhotoCard(
                                        theme = theme, 
                                        prompt = item.prompt, 
                                        onPromptChange = onPromptChange,
                                        mediaUrl = item.decorations.firstOrNull { it.style == "photo_url" }?.mediaUrl,
                                        onMediaUrlChange = { uri -> onMediaAttached("photo_url", uri) },
                                        editable = editable
                                    )
                                    LoveCardType.COUPON -> ComposerCouponCard(theme = theme, prompt = item.prompt, onPromptChange = onPromptChange)
                                    LoveCardType.MUSIC_DEDICATION -> ComposerMusicCard(
                                        theme = theme, 
                                        prompt = item.prompt, 
                                        onPromptChange = onPromptChange,
                                        mediaUrl = item.decorations.firstOrNull { it.style == "music_url" }?.mediaUrl,
                                        onMediaUrlChange = { uri -> onMediaAttached("music_url", uri) },
                                        editable = editable
                                    )
                                    LoveCardType.COUNTDOWN -> ComposerCountdownCard(theme = theme, prompt = item.prompt, onPromptChange = onPromptChange)
                                    LoveCardType.VOICE_MESSAGE -> ComposerVoiceCard(
                                        theme = theme, 
                                        prompt = item.prompt, 
                                        onPromptChange = onPromptChange,
                                        mediaUrl = item.decorations.firstOrNull { it.style == "voice_url" }?.mediaUrl,
                                        onMediaUrlChange = { uri -> onMediaAttached("voice_url", uri) },
                                        editable = editable
                                    )
                                    LoveCardType.SCRATCH_REVEAL -> ComposerScratchCard(theme = theme, prompt = item.prompt, onPromptChange = onPromptChange)
                                }
                            }
                        }
                    }
                }

                LoveCardStickerRender(
                    decorations = item.decorations,
                    editable = editable,
                    selectedStickerId = selectedStickerId,
                    onSelectSticker = onSelectSticker,
                    onMoveSticker = onMoveSticker,
                    onScaleSticker = onScaleSticker,
                    onDeleteSticker = onDeleteSticker,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

@Composable
private fun LoveCardStackComposer(
    item: LoveCardDraftItem,
    onTypeChange: (LoveCardType) -> Unit,
    onThemeChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onShowStickerPalette: () -> Unit,
    selectedStickerId: String?,
    onSelectSticker: (String?) -> Unit,
    onMoveSticker: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val offsetY = remember { androidx.compose.animation.core.Animatable(0f) }
    
    // Track the "next" states for the background cards
    val nextType = remember(item.type) {
        val types = LoveCardType.values()
        types[(item.type.ordinal + 1) % types.size]
    }
    val nextTheme = remember(item.theme) {
        val themes = storyThemeOptions().map { it.key }
        val currentIndex = themes.indexOf(item.theme)
        themes[(currentIndex + 1) % themes.size]
    }

    // Swiper Logic
    val swipeThreshold = 180f // Lowered from 300f for better sensitivity
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .pointerInput(item.type, item.theme, item.prompt) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val dragEvent = event.changes.firstOrNull()
                        
                        // We use this to correctly detect when children (like text fields)
                        // should allow the parent to take control.
                        if (dragEvent != null && dragEvent.pressed) {
                        }
                    }
                }
            }
            .pointerInput(item.type, item.theme, item.prompt) {
                detectDragGestures(
                    onDragStart = { 
                        // Reset offsets just in case to ensure fresh start
                    },
                    onDrag = { change, dragAmount ->
                        // Only consume if it's clearly a deliberate swipe to allow stickers to handle smaller drags
                        val isFling = kotlin.math.abs(dragAmount.x) > 10f || kotlin.math.abs(dragAmount.y) > 10f
                        if (isFling) {
                            change.consume()
                        }
                        
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    },
                    onDragEnd = {
                        val totalDx = offsetX.value
                        val totalDy = offsetY.value
                        
                        scope.launch {
                            if (kotlin.math.abs(totalDx) > swipeThreshold) {
                                // Swipe horizontal -> Change Type
                                val direction = if (totalDx > 0) 1 else -1
                                offsetX.animateTo(direction * 1500f, tween(400))
                                
                                val types = LoveCardType.values()
                                val nextIndex = if (totalDx > 0) {
                                    (item.type.ordinal - 1 + types.size) % types.size
                                } else {
                                    (item.type.ordinal + 1) % types.size
                                }
                                onTypeChange(types[nextIndex])
                                
                                offsetX.snapTo(0f)
                                offsetY.snapTo(0f)
                            } else if (kotlin.math.abs(totalDy) > swipeThreshold) {
                                // Swipe vertical -> Change Theme
                                val direction = if (totalDy > 0) 1 else -1
                                offsetY.animateTo(direction * 1500f, tween(400))
                                
                                val themes = storyThemeOptions().map { it.key }
                                val currentIndex = themes.indexOf(item.theme)
                                val nextIndex = if (totalDy > 0) {
                                    (currentIndex - 1 + themes.size) % themes.size
                                } else {
                                    (currentIndex + 1) % themes.size
                                }
                                onThemeChange(themes[nextIndex])
                                
                                offsetX.snapTo(0f)
                                offsetY.snapTo(0f)
                            } else {
                                // Snap back
                                launch { offsetX.animateTo(0f, spring()) }
                                launch { offsetY.animateTo(0f, spring()) }
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Calculate progress for background animations (0 to 1)
        val progress = remember(offsetX.value, offsetY.value) {
            val dist = kotlin.math.sqrt(offsetX.value * offsetX.value + offsetY.value * offsetY.value)
            (dist / (swipeThreshold * 1.5f)).coerceIn(0f, 1f)
        }

        // Background Card 2
        LoveCardEditorCanvas(
            item = item.copy(type = nextType, theme = nextTheme),
            selectedStickerId = null,
            onSelectSticker = {},
            onMoveSticker = { _, _, _ -> },
            onPromptChange = {},
            editable = false,
            modifier = Modifier
                .graphicsLayer {
                    alpha = progress * 0.4f
                    scaleX = 0.85f + (progress * 0.05f)
                    scaleY = 0.85f + (progress * 0.05f)
                    translationY = 40.dp.toPx() * (1f - progress)
                }
        )

        // Background Card 1
        LoveCardEditorCanvas(
            item = if (kotlin.math.abs(offsetX.value) > kotlin.math.abs(offsetY.value)) 
                      item.copy(type = nextType) else item.copy(theme = nextTheme),
            selectedStickerId = null,
            onSelectSticker = {},
            onMoveSticker = { _, _, _ -> },
            onPromptChange = {},
            editable = false,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = 0.9f + (progress * 0.1f)
                    scaleY = 0.9f + (progress * 0.1f)
                    translationY = 20.dp.toPx() * (1f - progress)
                    rotationZ = if (kotlin.math.abs(offsetX.value) > kotlin.math.abs(offsetY.value)) 
                                    (offsetX.value / 60f) * (1f - progress) else 0f
                }
        )

        // Top Card (Active)
        LoveCardEditorCanvas(
            item = item,
            selectedStickerId = selectedStickerId,
            onSelectSticker = onSelectSticker,
            onMoveSticker = onMoveSticker,
            onPromptChange = onPromptChange,
            onShowStickerPalette = onShowStickerPalette,
            modifier = Modifier
                .graphicsLayer {
                    translationX = offsetX.value
                    translationY = offsetY.value
                    rotationZ = (offsetX.value / 25f)
                }
        )
    }
}

@Composable
private fun LoveCardStackViewer(
    cards: List<LoveCardDeckItem>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    answers: Map<String, LoveCardDraftResponse>,
    onResponseChange: (String, LoveCardDraftResponse) -> Unit,
    canAnswer: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val offsetY = remember { androidx.compose.animation.core.Animatable(0f) }
    val hasBurst = remember { booleanArrayOf(false) }

    val currentCard = cards.getOrNull(currentIndex)
    val nextCard = if (cards.isNotEmpty()) cards[(currentIndex + 1) % cards.size] else null
    val prevCard = if (cards.isNotEmpty()) cards[(currentIndex - 1 + cards.size) % cards.size] else null

    val swipeThreshold = 300f 

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .pointerInput(cards.size, currentIndex) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                         if (kotlin.math.abs(dragAmount.x) > 2f || kotlin.math.abs(dragAmount.y) > 2f) {
                            change.consume()
                        }
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    },
                    onDragEnd = {
                        val totalDx = offsetX.value
                        val totalDy = offsetY.value
                        scope.launch {
                            if (kotlin.math.abs(totalDx) > swipeThreshold) {
                                val direction = if (totalDx > 0) 1 else -1
                                offsetX.animateTo(direction * 1500f, tween(400))
                                
                                val nextIndex = if (totalDx > 0) {
                                    (currentIndex - 1 + cards.size) % cards.size
                                } else {
                                    (currentIndex + 1) % cards.size
                                }
                                CardSoundEngine.playFlip()
                                onIndexChange(nextIndex)
                                
                                offsetX.snapTo(0f)
                                offsetY.snapTo(0f)
                            } else {
                                launch { offsetX.animateTo(0f, spring()) }
                                launch { offsetY.animateTo(0f, spring()) }
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val progress = remember(offsetX.value, offsetY.value) {
            val dist = kotlin.math.abs(offsetX.value)
            (dist / (swipeThreshold * 1.5f)).coerceIn(0f, 1f)
        }

        // Background Card (Next or Previous)
        val bgCard = if (offsetX.value > 0) prevCard else nextCard
        if (bgCard != null && cards.size > 1) {
            LoveCardEditorCanvas(
                item = LoveCardDraftItem(
                    type = enumValueOfSafe(bgCard.card.type),
                    prompt = bgCard.card.prompt,
                    choices = bgCard.choices(),
                    theme = bgCard.card.theme ?: "blush",
                    decorations = bgCard.decorations()
                ),
                editable = false,
                canAnswer = canAnswer,
                answeringResponse = answers[bgCard.card.cardId] ?: bgCard.response?.let {
                    LoveCardDraftResponse(
                        cardId = bgCard.card.cardId,
                        answerText = it.answerText,
                        selectedChoice = it.selectedChoice,
                        emojiReaction = it.emojiReaction
                    )
                } ?: LoveCardDraftResponse(cardId = bgCard.card.cardId),
                onResponseChange = { },
                onSelectSticker = { },
                onMoveSticker = { _, _, _ -> },
                onPromptChange = { },
                modifier = Modifier
                    .graphicsLayer {
                        alpha = progress * 0.6f
                        scaleX = 0.9f + (progress * 0.1f)
                        scaleY = 0.9f + (progress * 0.1f)
                        translationY = with(density) { 20.dp.toPx() } * (1f - progress)
                    }
            )
        }

        // Active Card
        if (currentCard != null) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = offsetY.value
                        rotationZ = (offsetX.value / 25f)
                    },
                contentAlignment = Alignment.Center
            ) {
                LoveCardEditorCanvas(
                    item = LoveCardDraftItem(
                        type = enumValueOfSafe(currentCard.card.type),
                        prompt = currentCard.card.prompt,
                        choices = currentCard.choices(),
                        theme = currentCard.card.theme ?: "blush",
                        decorations = currentCard.decorations()
                    ),
                    editable = false,
                    canAnswer = canAnswer,
                    answeringResponse = answers[currentCard.card.cardId] ?: currentCard.response?.let {
                        LoveCardDraftResponse(
                            cardId = currentCard.card.cardId,
                            answerText = it.answerText,
                            selectedChoice = it.selectedChoice,
                            emojiReaction = it.emojiReaction
                        )
                    } ?: LoveCardDraftResponse(cardId = currentCard.card.cardId),
                    onResponseChange = { onResponseChange(currentCard.card.cardId, it) },
                    onSelectSticker = { },
                    onMoveSticker = { _, _, _ -> },
                    onPromptChange = { },
                    modifier = Modifier.fillMaxSize()
                )
                BurstingEmojiOverlay(
                    cardId = currentCard.card.cardId,
                    animationStyle = currentCard.card.animationStyle,
                    hasBurst = hasBurst,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun LoveCardAnsweringInternal(
    type: LoveCardType,
    theme: LoveCardVisualTheme,
    prompt: String,
    choices: List<String>,
    response: LoveCardDraftResponse,
    onResponseChange: (LoveCardDraftResponse) -> Unit,
    canAnswer: Boolean = true
) {
    var showGifPicker by remember { mutableStateOf(false) }
    
    if (showGifPicker) {
        GifPickerTray(
            onGifSelected = { url ->
                onResponseChange(response.copy(emojiReaction = url))
                showGifPicker = false
            },
            onLocalGifSelected = { uri ->
                onResponseChange(response.copy(emojiReaction = uri.toString()))
                showGifPicker = false
            },
            onDismiss = { showGifPicker = false },
            recentGifs = emptyList() // Not hooked to ViewModel here, but could be
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.glassFill,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.titleLarge,
                color = theme.primaryText,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            when (type) {
                LoveCardType.MULTIPLE_CHOICE -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        choices.forEach { choice ->
                            val selected = response.selectedChoice == choice
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = canAnswer) {
                                        onResponseChange(response.copy(selectedChoice = choice))
                                    },
                                color = if (selected) theme.badgeFill else Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) theme.accent else Color.White.copy(alpha = 0.4f)
                                )
                            ) {
                                Text(
                                    text = choice,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    color = theme.primaryText,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                LoveCardType.CUTE_NOTE -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        choices.forEach { note ->
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = note,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    color = theme.primaryText,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                    EmojiRow(
                        selected = response.emojiReaction,
                        onSelect = { onResponseChange(response.copy(emojiReaction = it)) },
                        onGifClick = { showGifPicker = true }
                    )
                }
                LoveCardType.ANIMATED_GIFT, 
                LoveCardType.PHOTO_MEMORY, LoveCardType.VOICE_MESSAGE, 
                LoveCardType.MUSIC_DEDICATION, LoveCardType.COUPON, 
                LoveCardType.COUNTDOWN, LoveCardType.SCRATCH_REVEAL -> {
                    EmojiRow(
                        selected = response.emojiReaction,
                        onSelect = { onResponseChange(response.copy(emojiReaction = it)) },
                        onGifClick = { showGifPicker = true }
                    )
                }
                else -> {}
            }

            if (canAnswer) {
                LoveCardTextField(
                    value = response.answerText.orEmpty(),
                    onValueChange = { onResponseChange(response.copy(answerText = it.take(180))) },
                    placeholder = "Reply with love...",
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = theme.primaryText
                    ),
                    cursorBrush = SolidColor(theme.primaryText),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                        .padding(12.dp)
                )
            } else {
                // View Only Response
                val isGif = response.emojiReaction?.let { it.startsWith("http") || it.startsWith("content://") } == true

                val replyText = listOf(
                    response.selectedChoice,
                    response.answerText,
                    if (isGif) null else response.emojiReaction
                ).firstOrNull { !it.isNullOrBlank() && !it.equals("null", ignoreCase = true) } ?: if (isGif) "Sent a GIF reaction" else "No reply yet"

                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Reply",
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.primaryText.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        if (replyText != "Sent a GIF reaction") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = replyText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = theme.primaryText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (isGif) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AsyncImage(
                                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(response.emojiReaction)
                                    .decoderFactory(GifDecoder.Factory())
                                    .build(),
                                contentDescription = "Reaction GIF",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerNoteCard(
    theme: LoveCardVisualTheme,
    prompt: String,
    choices: List<String>,
    onPromptChange: (String) -> Unit,
    onChoicesChange: (List<String>) -> Unit = {}
) {
    val internalNotes = choices 

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.glassFill,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Prompt
            LoveCardTextField(
                value = prompt,
                onValueChange = { onPromptChange(it.take(180)) },
                placeholder = "Write a sweet note...",
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.primaryText
                ),
                cursorBrush = SolidColor(theme.primaryText),
                modifier = Modifier.fillMaxWidth()
            )

            // Additional Note Boxes
            internalNotes.forEachIndexed { index, note ->
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoveCardTextField(
                            value = note,
                            onValueChange = { newText ->
                                val newNotes = internalNotes.toMutableList()
                                newNotes[index] = newText.take(180)
                                onChoicesChange(newNotes)
                            },
                            placeholder = "Another note...",
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = theme.primaryText
                            ),
                            cursorBrush = SolidColor(theme.primaryText),
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                        IconButton(onClick = {
                            val newNotes = internalNotes.toMutableList()
                            newNotes.removeAt(index)
                            onChoicesChange(newNotes)
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = theme.primaryText.copy(alpha=0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Add Note Button
            if (internalNotes.size < 10) {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, theme.primaryText.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().clickable {
                        val newNotes = internalNotes.toMutableList()
                        newNotes.add("")
                        onChoicesChange(newNotes)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add box", tint = theme.primaryText, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add another box", color = theme.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerQuestionCard(
    theme: LoveCardVisualTheme, 
    prompt: String, 
    choices: List<String> = emptyList(),
    onPromptChange: (String) -> Unit,
    onChoicesChange: (List<String>) -> Unit = {}
) {
    val internalQuestions = choices

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.glassFill,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Question
            LoveCardTextField(
                value = prompt,
                onValueChange = { onPromptChange(it.take(180)) },
                placeholder = "Ask something cute...",
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.primaryText
                ),
                cursorBrush = SolidColor(theme.primaryText),
                modifier = Modifier.fillMaxWidth()
            )

            // Additional Questions Boxes (Stacking)
            internalQuestions.forEachIndexed { index, q ->
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoveCardTextField(
                            value = q,
                            onValueChange = { newText ->
                                val newQs = internalQuestions.toMutableList()
                                newQs[index] = newText.take(180)
                                onChoicesChange(newQs)
                            },
                            placeholder = "Another question...",
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = theme.primaryText
                            ),
                            cursorBrush = SolidColor(theme.primaryText),
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                        IconButton(onClick = {
                            val newQs = internalQuestions.toMutableList()
                            newQs.removeAt(index)
                            onChoicesChange(newQs)
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = theme.primaryText.copy(alpha=0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Add Question Button
            if (internalQuestions.size < 8) {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, theme.primaryText.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().clickable {
                        val newQs = internalQuestions.toMutableList()
                        newQs.add("")
                        onChoicesChange(newQs)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add box", tint = theme.primaryText, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add another box", color = theme.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
private fun ComposerChoiceCard(
    theme: LoveCardVisualTheme,
    prompt: String,
    choices: List<String>,
    onPromptChange: (String) -> Unit,
    onChoicesChange: (List<String>) -> Unit = {}
) {
    val internalChoices = choices.ifEmpty { listOf("Yes", "Maybe") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.glassFill,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LoveCardTextField(
                value = prompt,
                onValueChange = { onPromptChange(it.take(180)) },
                placeholder = "Pick our next little mood...",
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.primaryText
                ),
                cursorBrush = SolidColor(theme.primaryText),
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                internalChoices.forEachIndexed { index, choice ->
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoveCardTextField(
                                value = choice,
                                onValueChange = { newText ->
                                    val newChoices = internalChoices.toMutableList()
                                    newChoices[index] = newText.take(50)
                                    onChoicesChange(newChoices)
                                },
                                placeholder = "Choice...",
                                textStyle = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.primaryText
                                ),
                                cursorBrush = SolidColor(theme.primaryText),
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp)
                            )
                            if (internalChoices.size > 1) {
                                IconButton(onClick = {
                                    val newChoices = internalChoices.toMutableList()
                                    newChoices.removeAt(index)
                                    onChoicesChange(newChoices)
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = theme.primaryText.copy(alpha=0.6f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                if (internalChoices.size < 8) {
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, theme.primaryText.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().clickable {
                            val newChoices = internalChoices.toMutableList()
                            newChoices.add("")
                            onChoicesChange(newChoices)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = theme.primaryText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add option", color = theme.primaryText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerGiftBox(theme: LoveCardVisualTheme, prompt: String, onPromptChange: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(width = 180.dp, height = 170.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .align(Alignment.BottomCenter),
                color = Color(0xFFFFB7D2),
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight()
                            .width(22.dp)
                            .background(Color(0xFFFFF1F7))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(22.dp)
                            .background(Color(0xFFFFF1F7))
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .align(Alignment.TopCenter),
                color = Color(0xFFFF8DBB),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight()
                            .width(22.dp)
                            .background(Color(0xFFFFF1F7))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(Color(0xFFFFF1F7))
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 6.dp),
                color = theme.badgeFill,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "Tap-to-open surprise",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = theme.badgeText,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.glassFill,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
        ) {
            LoveCardTextField(
                value = prompt,
                onValueChange = { onPromptChange(it.take(180)) },
                placeholder = "What's inside?",
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.primaryText
                ),
                cursorBrush = SolidColor(theme.primaryText),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun ComposerPhotoCard(
    theme: LoveCardVisualTheme, 
    prompt: String, 
    onPromptChange: (String) -> Unit,
    mediaUrl: String?,
    onMediaUrlChange: (String) -> Unit,
    editable: Boolean = true,
    viewModel: com.aman.gigi.viewmodel.ScreensaverViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isUploading by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && editable) {
            isUploading = true
            viewModel.uploadLoveCardMedia(context, uri) { uploadedUrl ->
                isUploading = false
                if (uploadedUrl != null) {
                    onMediaUrlChange(uploadedUrl)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { 
                if (editable && !isUploading) {
                    launcher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                } 
            },
            color = Color.White.copy(alpha = 0.86f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = theme.backLayerOne.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (mediaUrl != null) {
                        coil.compose.AsyncImage(
                            model = mediaUrl,
                            contentDescription = "Photo Memory",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else if (isUploading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = theme.primaryText
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate, 
                            contentDescription = "Add Photo", 
                            tint = theme.primaryText.copy(alpha = 0.4f), 
                            modifier = Modifier.align(Alignment.Center).size(56.dp)
                        )
                    }
                }
            }
        }
        Surface(color = theme.glassFill, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.White.copy(alpha=0.3f))) {
            LoveCardTextField(
                value = prompt, onValueChange = { onPromptChange(it.take(80)) }, placeholder = "Memory caption...",
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = theme.primaryText, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(theme.primaryText), modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = editable
            )
        }
    }
}

@Composable
private fun ComposerCouponCard(theme: LoveCardVisualTheme, prompt: String, onPromptChange: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(), color = theme.glassFill, shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, theme.badgeFill)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("♥ LOVE COUPON ♥", color = theme.primaryText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp)
            LoveCardTextField(
                value = prompt, onValueChange = { onPromptChange(it.take(100)) }, placeholder = "Valid for...",
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = theme.primaryText, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(theme.primaryText), modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

data class ServerSong(val title: String, val artist: String, val url: String)

val predefinedServerSongs = listOf(
    ServerSong("A Thousand Years", "Christina Perri", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
    ServerSong("Perfect", "Ed Sheeran", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
    ServerSong("All of Me", "John Legend", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"),
    ServerSong("Can't Help Falling in Love", "Elvis Presley", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3")
)

@Composable
private fun ComposerMusicCard(
    theme: LoveCardVisualTheme, 
    prompt: String, 
    onPromptChange: (String) -> Unit,
    mediaUrl: String?,
    onMediaUrlChange: (String) -> Unit,
    editable: Boolean = true
) {
    var showSongPicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var isPlaying by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var mediaPlayer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.media.MediaPlayer?>(null) }
    
    val selectedSong = predefinedServerSongs.find { it.url == mediaUrl }

    fun togglePlayback() {
        if (mediaUrl == null) return
        if (isPlaying) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
        } else {
            try {
                val player = android.media.MediaPlayer()
                player.setDataSource(mediaUrl)
                player.setOnCompletionListener {
                    isPlaying = false
                    it.release()
                    mediaPlayer = null
                }
                player.prepareAsync()
                player.setOnPreparedListener { 
                    it.start()
                    isPlaying = true
                }
                mediaPlayer = player
            } catch (e: Exception) {
                android.util.Log.e("MusicCard", "Playback failed", e)
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(mediaUrl) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    val rotationAngle = rememberInfiniteTransition().animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "vinyl_rotate"
    )

    if (showSongPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSongPicker = false },
            title = { Text("Select a Song") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    items(predefinedServerSongs) { song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onMediaUrlChange(song.url)
                                showSongPicker = false
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(song.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showSongPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(160.dp).clickable {
                if (mediaUrl != null) {
                    togglePlayback()
                } else if (editable) {
                    showSongPicker = true
                }
            }.graphicsLayer(rotationZ = if (isPlaying) rotationAngle.value else 0f), 
            shape = RoundedCornerShape(999.dp), color = Color(0xFF222226), border = BorderStroke(6.dp, Color.White.copy(alpha = 0.85f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                repeat(4) { i ->
                    Surface(modifier = Modifier.size((140 - i * 24).dp), shape = RoundedCornerShape(999.dp), color = Color.Transparent, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {}
                }
                Surface(modifier = Modifier.size(54.dp), shape = RoundedCornerShape(999.dp), color = theme.accent) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Surface(modifier = Modifier.size(12.dp), shape = RoundedCornerShape(999.dp), color = Color.Black) {}
                Surface(modifier = Modifier.size(4.dp), shape = RoundedCornerShape(999.dp), color = Color.White) {}
            }
        }

        if (editable && mediaUrl == null) {
            Text("Tap vinyl to select song", style = MaterialTheme.typography.labelSmall, color = theme.primaryText.copy(alpha=0.6f))
        }

        if (selectedSong != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(selectedSong.title, color = theme.primaryText, fontWeight = FontWeight.Black)
                Text(selectedSong.artist, color = theme.primaryText.copy(alpha=0.7f), style = MaterialTheme.typography.bodySmall)
            }
            if (editable && !isPlaying) {
                androidx.compose.material3.TextButton(onClick = { showSongPicker = true }) {
                    Text("Change Song", color = theme.primaryText)
                }
            }
        }

        Surface(color = theme.glassFill, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.White.copy(alpha=0.4f))) {
            LoveCardTextField(
                value = prompt, onValueChange = { onPromptChange(it.take(100)) }, placeholder = "Song dedication...",
                textStyle = MaterialTheme.typography.titleLarge.copy(color = theme.primaryText, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(theme.primaryText), modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = editable
            )
        }
    }
}

@Composable
private fun ComposerCountdownCard(theme: LoveCardVisualTheme, prompt: String, onPromptChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("0", "0").forEach { Text(text = it, color = theme.primaryText, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black) }
            Text(text = "Days", color = theme.primaryText.copy(alpha = 0.8f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Surface(color = theme.glassFill, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.White.copy(alpha=0.4f))) {
            LoveCardTextField(
                value = prompt, onValueChange = { onPromptChange(it.take(100)) }, placeholder = "Countdown to...",
                textStyle = MaterialTheme.typography.titleLarge.copy(color = theme.primaryText, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(theme.primaryText), modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }
}

@Composable
private fun ComposerVoiceCard(
    theme: LoveCardVisualTheme, 
    prompt: String, 
    onPromptChange: (String) -> Unit,
    mediaUrl: String?,
    onMediaUrlChange: (String) -> Unit,
    editable: Boolean = true,
    viewModel: com.aman.gigi.viewmodel.ScreensaverViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isRecording by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var isUploading by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var mediaRecorder by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.media.MediaRecorder?>(null) }
    var outputFile by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<java.io.File?>(null) }
    
    var isPlaying by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var mediaPlayer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.media.MediaPlayer?>(null) }

    val permissionFlow = com.aman.gigi.ui.LocalPermissionFlow.current

    fun startRecording() {
        try {
            val file = java.io.File(context.cacheDir, "lovecard_voice_${System.currentTimeMillis()}.m4a")
            outputFile = file
            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            isRecording = true
        } catch (e: Exception) {
            android.util.Log.e("VoiceCard", "Failed to start recording", e)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.release()
            mediaPlayer?.release()
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            
            if (outputFile != null && outputFile!!.exists()) {
                isUploading = true
                viewModel.uploadLoveCardMediaFile(outputFile!!) { uploadedUrl ->
                    isUploading = false
                    if (uploadedUrl != null) {
                        onMediaUrlChange(uploadedUrl)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceCard", "Failed to stop recording", e)
        }
    }

    fun togglePlayback() {
        if (mediaUrl == null) return
        if (isPlaying) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
        } else {
            try {
                val player = android.media.MediaPlayer()
                player.setDataSource(mediaUrl)
                player.setOnCompletionListener {
                    isPlaying = false
                    it.release()
                    mediaPlayer = null
                }
                player.prepareAsync()
                player.setOnPreparedListener { 
                    it.start()
                    isPlaying = true
                }
                mediaPlayer = player
            } catch (e: Exception) {
                android.util.Log.e("VoiceCard", "Playback failed", e)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(), color = theme.glassFill, shape = RoundedCornerShape(32.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            LoveCardTextField(
                value = prompt, onValueChange = { onPromptChange(it.take(100)) }, placeholder = "Voice note caption...",
                textStyle = MaterialTheme.typography.titleMedium.copy(color = theme.primaryText, textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold),
                cursorBrush = SolidColor(theme.primaryText), modifier = Modifier.fillMaxWidth(),
                enabled = editable
            )
            Row(modifier = Modifier.fillMaxWidth().clickable(
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                indication = null
            ) { 
                if (mediaUrl != null) {
                    togglePlayback()
                } else if (editable && !isUploading) {
                    if (isRecording) {
                        stopRecording()
                    } else {
                        permissionFlow.request(com.aman.gigi.ui.FeaturePermission.MICROPHONE) {
                            startRecording()
                        }
                    }
                }
            }, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                if (isUploading) {
                    androidx.compose.material3.CircularProgressIndicator(color = theme.primaryText, modifier = Modifier.size(46.dp).padding(10.dp), strokeWidth = 3.dp)
                } else {
                    Surface(
                        color = if (isRecording) Color.Red.copy(alpha=0.15f) else theme.badgeFill, 
                        shape = RoundedCornerShape(999.dp), modifier = Modifier.size(46.dp)
                    ) {
                        val iconVec = if (isRecording) Icons.Default.Stop else (if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow)
                        Icon(iconVec, contentDescription = "Play/Record", tint = if (isRecording) Color.Red else theme.primaryText, modifier = Modifier.padding(8.dp))
                    }
                }
                
                val heights = listOf(14, 26, 12, 34, 18, 28, 14, 20)
                val infiniteTransition = rememberInfiniteTransition()
                repeat(8) { i ->
                    val h by infiniteTransition.animateFloat(
                        initialValue = heights[i].toFloat(),
                        targetValue = if (isRecording || isPlaying) heights[(i + 3) % 8].toFloat() * 1.5f else heights[i].toFloat(),
                        animationSpec = infiniteRepeatable(tween(400, delayMillis = i * 50), RepeatMode.Reverse)
                    )
                    Box(modifier = Modifier.width(6.dp).height(h.dp).background(theme.primaryText.copy(alpha = if (mediaUrl != null || isRecording) 1f else 0.3f), RoundedCornerShape(999.dp)))
                }
            }
            if (editable && mediaUrl == null && !isUploading) {
                Text(if (isRecording) "Tap to stop" else "Tap to record", style = MaterialTheme.typography.labelSmall, color = theme.primaryText.copy(alpha=0.6f))
            }
        }
    }
}

@Composable
private fun ComposerScratchCard(theme: LoveCardVisualTheme, prompt: String, onPromptChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(140.dp), color = theme.backLayerTwo, shape = RoundedCornerShape(20.dp), border = BorderStroke(2.dp, theme.backLayerOne)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("SCRATCH ME ✨", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = theme.badgeText.copy(alpha=0.6f))
            }
        }
        Surface(color = theme.glassFill, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.White.copy(alpha=0.3f))) {
            LoveCardTextField(
                value = prompt, onValueChange = { onPromptChange(it.take(80)) }, placeholder = "Hidden secret text...",
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = theme.primaryText, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(theme.primaryText), modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }
}

@Composable
private fun LoveCardTypeGallery(
    onTypeSelected: (LoveCardType) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        FrostedLoveCardSurface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(20.dp),
            shape = RoundedCornerShape(32.dp),
            fillColor = Color.White.copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pick a card mood",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF5A23D4)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF5A23D4))
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LoveCardType.values().forEach { type ->
                        val theme = loveCardThemeFor(type, "blush")
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.75f)
                                .clickable { onTypeSelected(type) },
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(theme.background))
                                    .padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = iconFor(type),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.TopStart).size(24.dp)
                                )
                                Text(
                                    text = typeLabel(type),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.align(Alignment.BottomStart)
                                )
                            }
                        }
                    }
                }
                
                TextButton(onClick = onDismiss) {
                    Text("Close gallery", color = Color(0xFF5A23D4))
                }
            }
        }
    }
}

@Composable
private fun LoveCardStickerPalette(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onSelect: (ComposerStickerOption) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("love_card_recent", android.content.Context.MODE_PRIVATE) }
    val recentEmojis = remember {
        val saved = sharedPrefs.getStringSet("emojis", emptySet()) ?: emptySet()
        mutableStateListOf<String>().apply {
            addAll(saved.take(12))
            if (isEmpty()) addAll(listOf("💌", "💖", "🫶", "🌸", "🎀", "🌙", "☁️", "✨", "🦋", "🧸"))
        }
    }
    val recentGifs = remember {
        val saved = sharedPrefs.getStringSet("gifs", emptySet()) ?: emptySet()
        mutableStateListOf<ComposerStickerOption>().apply {
            addAll(saved.map {
                val parts = it.split("|")
                ComposerStickerOption(parts.getOrElse(0) { "GIF" }, "gif", parts.getOrElse(1) { "" })
            }.filter { it.mediaUrl?.isNotBlank() == true })
        }
    }

    val GIPHY_API_KEY = "UoQUMmYUtknfjqewBqX4BRKbgIjP5e34"
    var giphyQuery by remember { mutableStateOf("") }
    var giphyResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearchingGiphy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var showEmojiMoreDialog by remember { mutableStateOf(false) }

    fun addRecentEmoji(emoji: String) {
        if (!recentEmojis.contains(emoji)) {
            recentEmojis.add(0, emoji)
            if (recentEmojis.size > 12) recentEmojis.removeLast()
            sharedPrefs.edit().putStringSet("emojis", recentEmojis.toSet()).apply()
        }
    }

    fun addRecentGif(option: ComposerStickerOption) {
        if (!recentGifs.any { it.mediaUrl == option.mediaUrl }) {
            recentGifs.add(0, option)
            if (recentGifs.size > 8) recentGifs.removeLast()
            val set = recentGifs.map { "${it.content}|${it.mediaUrl}" }.toSet()
            sharedPrefs.edit().putStringSet("gifs", set).apply()
        }
    }

    fun searchGiphy(query: String) {
        if (query.isBlank()) {
            giphyResults = emptyList()
            return
        }
        scope.launch {
            isSearchingGiphy = true
            try {
                kotlinx.coroutines.delay(400)
                if (giphyQuery != query) return@launch
                val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient()
                    val url = "https://api.giphy.com/v1/gifs/search?api_key=${GIPHY_API_KEY}&q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=8&rating=g"
                    val request = okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) return@withContext emptyList<String>()
                    val json = org.json.JSONObject(body)
                    val data = json.getJSONArray("data")
                    val urls = mutableListOf<String>()
                    for (i in 0 until data.length()) {
                        val gif = data.getJSONObject(i)
                        val images = gif.getJSONObject("images")
                        val imageObj = images.optJSONObject("fixed_height") ?: images.optJSONObject("downsized")
                        if (imageObj != null) {
                            urls.add(imageObj.getString("url"))
                        }
                    }
                    urls
                }
                giphyResults = results
            } catch (e: Exception) {
                giphyResults = emptyList()
            } finally {
                isSearchingGiphy = false
            }
        }
    }

    val defaultCuteGifs = remember {
        mutableStateListOf(
            ComposerStickerOption("Love You", "gif", "https://media.giphy.com/media/X86mKms4sURIDH9vBf/giphy.gif"),
            ComposerStickerOption("Cute", "gif", "https://media.giphy.com/media/vG69O11QY5j3T4Xw5O/giphy.gif"),
            ComposerStickerOption("XOXO", "gif", "https://media.giphy.com/media/26u49K7xO76RUpRWo/giphy.gif"),
            ComposerStickerOption("Heart", "gif", "https://media.giphy.com/media/l4pT7ZzGIn0HjRRE4/giphy.gif")
        )
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val url = "https://api.giphy.com/v1/gifs/search?api_key=${GIPHY_API_KEY}&q=love&limit=6&rating=g"
                val request = okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = org.json.JSONObject(body)
                    val data = json.getJSONArray("data")
                    val loaded = mutableListOf<ComposerStickerOption>()
                    for (i in 0 until data.length()) {
                        val gif = data.getJSONObject(i)
                        val images = gif.getJSONObject("images")
                        val imageObj = images.optJSONObject("fixed_height") ?: images.optJSONObject("downsized")
                        if (imageObj != null) {
                            loaded.add(ComposerStickerOption("Love", "gif", imageObj.getString("url")))
                        }
                    }
                    if (loaded.isNotEmpty()) {
                        defaultCuteGifs.clear()
                        defaultCuteGifs.addAll(loaded)
                    }
                }
            } catch (e: Exception) {
                // Ignore fallback
            }
        }
    }

    FrostedLoveCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 540.dp)
            .shadow(16.dp, RoundedCornerShape(30.dp)),
        shape = RoundedCornerShape(30.dp),
        fillColor = Color.White.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Emoji & gif stickers",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4F22CE)
                    )
                    Text(
                        text = "Search Giphy or choose recent emojis below",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6F6488)
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.wrapContentWidth()) { Text("Done") }
            }

            OutlinedTextField(
                value = giphyQuery,
                onValueChange = {
                    giphyQuery = it
                    searchGiphy(it)
                },
                placeholder = { Text("Search animated GIFs on Giphy... 🔍") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8A5BFF),
                    unfocusedBorderColor = Color.LightGray
                )
            )

            if (isSearchingGiphy) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color(0xFFC04A88))
                }
            } else if (giphyResults.isNotEmpty()) {
                Text(
                    text = "Giphy Search Results",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC04A88)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(giphyResults.size) { i ->
                        val url = giphyResults[i]
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF9F5FF))
                                .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                .clickable {
                                    val opt = ComposerStickerOption("GIF", "gif", url)
                                    addRecentGif(opt)
                                    onSelect(opt)
                                    onDismiss()
                                }
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                    }
                }
            } else {
                val displayedGifs = (recentGifs + defaultCuteGifs).distinctBy { it.mediaUrl }.take(8)
                if (displayedGifs.isNotEmpty()) {
                    Text(
                        text = "Recent & Cute GIFs",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC04A88)
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(displayedGifs.size) { i ->
                            val gifOpt = displayedGifs[i]
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF9F5FF))
                                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        addRecentGif(gifOpt)
                                        onSelect(gifOpt)
                                        onDismiss()
                                    }
                            ) {
                                AsyncImage(
                                    model = gifOpt.mediaUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Recent Emojis",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC04A88)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recentEmojis.forEach { emoji ->
                    StickerPaletteChip(
                        option = ComposerStickerOption(emoji, "emoji"),
                        onSelect = {
                            addRecentEmoji(emoji)
                            onSelect(it)
                            onDismiss()
                        }
                    )
                }

                Surface(
                    modifier = Modifier
                        .height(44.dp)
                        .clickable { showEmojiMoreDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFEFF6),
                    border = BorderStroke(1.dp, Color(0xFFFFCCE4))
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("+ More", color = Color(0xFFD81B60), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showEmojiMoreDialog) {
        val allEmojisList = remember {
            listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "☺️", "😊", "😇", "😍", "🥰", "😘", "😋", "😛", "😜", "🤪", "😎", "🤩", "🥳", "🥺", "😭", "😠", "😡",
                "❤️", "🩷", "🧡", "💛", "💚", "💙", "🩵", "💜", "🖤", "🩶", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟",
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🦁", "🐵", "🐒", "🐔", "🐧", "🐦", "🐤", "🦋", "🐝", "🐌", "🐞", "🐢", "🐳", "🐬", "🦭",
                "🍏", "🍎", "🍐", "🍊", "🍌", "🍉", "🍇", "🍓", "🫐", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍕", "🍔", "🍟", "🥞", "🍦", "🍧", "🍩", "🍪", "🎂",
                "✨", "🌟", "⭐", "💫", "🔥", "💥", "🌈", "☀️", "🌤️", "🌥️", "☁️", "🌧️", "❄️", "☃️", "🍃", "🌿", "🌱", "🪴", "🌸", "🌹", "🥀", "🌺", "🌻", "🌼", "🌷"
            )
        }
        var emojiSearchQuery by remember { mutableStateOf("") }
        val filteredEmojis = allEmojisList.filter { it.contains(emojiSearchQuery) || emojiSearchQuery.isBlank() }

        androidx.compose.ui.window.Dialog(onDismissRequest = { showEmojiMoreDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Search Emojis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF4F22CE))
                        IconButton(onClick = { showEmojiMoreDialog = false }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                        }
                    }

                    OutlinedTextField(
                        value = emojiSearchQuery,
                        onValueChange = { emojiSearchQuery = it },
                        placeholder = { Text("Search emojis...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8A5BFF),
                            unfocusedBorderColor = Color.LightGray
                        )
                    )

                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        items(filteredEmojis.size) { i ->
                            val emoji = filteredEmojis[i]
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        addRecentEmoji(emoji)
                                        onSelect(ComposerStickerOption(emoji, "emoji"))
                                        showEmojiMoreDialog = false
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 28.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerPaletteChip(
    option: ComposerStickerOption,
    onSelect: (ComposerStickerOption) -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onSelect(option) },
        shape = RoundedCornerShape(18.dp),
        color = if (option.style == "gif") Color(0xFFEFE3FF) else Color(0xFFFFEAF4),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f))
    ) {
        Text(
            text = option.content,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (option.style == "gif") Color(0xFF6A3DF0) else Color(0xFFC04A88),
            fontWeight = FontWeight.Bold,
            style = if (option.style == "gif") MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun EditableLoveCardDraft(
    item: LoveCardDraftItem,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUpdate: (LoveCardDraftItem) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val palette = paletteFor(item.type.name)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = palette.first.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(iconFor(item.type.name), contentDescription = null, tint = palette.first, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${index + 1}. ${typeLabel(item.type)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4C248D)
                        )
                    }
                }

                Row {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Move up")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Move down")
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFD04D78))
                    }
                }
            }

            OutlinedTextField(
                value = item.prompt,
                onValueChange = { onUpdate(item.copy(prompt = it.take(180))) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Prompt or message") },
                shape = RoundedCornerShape(20.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Bursting Emoji Theme",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B3BF0)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val styles = listOf("none" to "❌ None", "burst" to "💖 Burst", "sparkle" to "✨ Sparkle", "ocean" to "🫧 Ocean", "nature" to "🐝 Nature")
                    styles.forEach { (styleKey, label) ->
                        val isSelected = (item.animationStyle ?: "none") == styleKey
                        Surface(
                            modifier = Modifier.clickable { onUpdate(item.copy(animationStyle = styleKey)) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF7C3AED) else Color.White.copy(alpha = 0.8f))
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (isSelected) Color.White else Color(0xFF4C248D),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }


            if (item.type == LoveCardType.MULTIPLE_CHOICE) {
                val choices = remember(item) {
                    mutableStateListOf(*(item.choices.ifEmpty { listOf("Yes", "Maybe", "Definitely") }.toTypedArray()))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEachIndexed { choiceIndex, choice ->
                        OutlinedTextField(
                            value = choice,
                            onValueChange = { updated ->
                                choices[choiceIndex] = updated.take(40)
                                onUpdate(item.copy(choices = choices.filter { it.isNotBlank() }))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Choice ${choiceIndex + 1}") },
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoveCardDeckDialog(
    deck: LoveCardDeck,
    onDismiss: () -> Unit,
    onAnswer: (List<LoveCardDraftResponse>) -> Unit
) {
    val cards = deck.items
    var currentIndex by remember { mutableStateOf(0) }
    val answers = remember(deck.stack.stackId) { mutableStateMapOf<String, LoveCardDraftResponse>() }
    val activeCard = cards.getOrNull(currentIndex)
    val canAnswer = deck.stack.isIncoming && deck.stack.status != LoveCardStackStatus.ANSWERED.name
    var isSubmitting by remember { mutableStateOf(false) }
    
    val unlockDate = deck.stack.unlockDate
    var isLocked by remember { mutableStateOf(unlockDate != null && unlockDate > System.currentTimeMillis()) }
    var remainingTime by remember { mutableStateOf("") }
    
    LaunchedEffect(unlockDate) {
        if (unlockDate != null) {
            while (true) {
                val now = System.currentTimeMillis()
                if (now >= unlockDate) {
                    isLocked = false
                    break
                }
                val diffSeconds = (unlockDate - now) / 1000
                val hours = diffSeconds / 3600
                val minutes = (diffSeconds % 3600) / 60
                val seconds = diffSeconds % 60
                remainingTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    LaunchedEffect(Unit) {
        CardSoundEngine.playReveal()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEFE8FF), // Soft Lavender
                        Color(0xFFE3DBFF),
                        Color(0xFFF6EEFF)
                    )
                )
            )
            .safeDrawingPadding()
            .clickable(enabled = false) { }, // Consume clicks
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = deck.stack.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF5120CC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Card ${currentIndex + 1} of ${cards.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF6C6882)
                            )
                        }
                        Surface(
                            modifier = Modifier.clickable(onClick = onDismiss),
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.72f),
                            shadowElevation = 0.dp,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF6C3CF0),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Close",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6C3CF0)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLocked) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                LoveCardStackViewer(
                                    cards = cards,
                                    currentIndex = 0,
                                    onIndexChange = {},
                                    answers = answers,
                                    onResponseChange = { _, _ -> },
                                    canAnswer = false,
                                    modifier = Modifier.fillMaxSize().blur(24.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(32.dp),
                                    color = Color.White.copy(alpha = 0.85f),
                                    border = BorderStroke(2.dp, Color.White),
                                    shadowElevation = 16.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = Color(0xFF6C3CF0),
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Text(
                                            text = "Time Capsule",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF6C3CF0)
                                        )
                                        Text(
                                            text = "Unlocks in\n$remainingTime",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF8455FF),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            LoveCardStackViewer(
                                cards = cards,
                                currentIndex = currentIndex,
                                onIndexChange = { currentIndex = it },
                                answers = answers,
                                onResponseChange = { id, resp -> answers[id] = resp },
                                canAnswer = canAnswer,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(30.dp),
                        color = Color.White.copy(alpha = 0.58f),
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.44f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.clickable(
                                    enabled = currentIndex > 0,
                                    onClick = { currentIndex = (currentIndex - 1).coerceAtLeast(0) }
                                ),
                                shape = RoundedCornerShape(999.dp),
                                color = if (currentIndex > 0) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.3f)
                            ) {
                                Text(
                                    text = "Previous",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    color = if (currentIndex > 0) Color(0xFF6B3BF0) else Color(0xFFAA9FD0),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(cards.size) { index ->
                                    val isActive = index == currentIndex
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(width = if (isActive) 18.dp else 8.dp, height = 8.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (isActive) Color(0xFF8C62FF)
                                                else Color.White.copy(alpha = 0.5f)
                                            )
                                    )
                                }
                            }

                            if (canAnswer && currentIndex == cards.lastIndex) {
                                val allAnswered = cards.all { item ->
                                    val response = answers[item.card.cardId] ?: item.response?.let {
                                        LoveCardDraftResponse(
                                            cardId = item.card.cardId,
                                            answerText = it.answerText,
                                            selectedChoice = it.selectedChoice,
                                            emojiReaction = it.emojiReaction
                                        )
                                    }
                                    responseCompletes(item, response)
                                }
                                Button(
                                    onClick = {
                                        if (!isSubmitting) {
                                            isSubmitting = true
                                            onAnswer(cards.map { item ->
                                                answers[item.card.cardId]
                                                    ?: LoveCardDraftResponse(cardId = item.card.cardId)
                                            })
                                        }
                                    },
                                    enabled = allAnswered && !isSubmitting,
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSubmitting) Color(0xFF8455FF).copy(alpha = 0.5f) else Color(0xFF8455FF),
                                        contentColor = Color.White,
                                        disabledContainerColor = Color(0xFFD7CCF7),
                                        disabledContentColor = Color.White.copy(alpha = 0.8f)
                                    )
                                ) {
                                    Text("Send back with love")
                                }
                            } else if (!isLocked) {
                                Surface(
                                    modifier = Modifier.clickable(
                                        enabled = currentIndex < cards.lastIndex,
                                        onClick = { currentIndex = (currentIndex + 1).coerceAtMost(cards.lastIndex) }
                                    ),
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (currentIndex < cards.lastIndex) Color(0xFFE8DEFF) else Color.White.copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        text = "Next",
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                        color = if (currentIndex < cards.lastIndex) Color(0xFF6B3BF0) else Color(0xFFAA9FD0),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoveCardPage(
    deckItem: LoveCardDeckItem,
    existingResponse: LoveCardDraftResponse?,
    canAnswer: Boolean,
    onResponseChange: (LoveCardDraftResponse) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardType = enumValueOfSafe(deckItem.card.type)
    val theme = loveCardThemeFor(cardType, deckItem.card.theme)
    val float = rememberInfiniteTransition(label = "card-float")
    val cardFloat by float.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "card-float-value"
    )
    var giftOpened by rememberSaveable(
        deckItem.card.cardId,
        canAnswer,
        existingResponse?.answerText,
        existingResponse?.selectedChoice,
        existingResponse?.emojiReaction
    ) {
        mutableStateOf(cardType != LoveCardType.ANIMATED_GIFT || !canAnswer || existingResponse != null)
    }
    val shouldShowResponseEditor = cardType != LoveCardType.ANIMATED_GIFT || giftOpened

    Surface(
        modifier = modifier
            .offset(y = cardFloat.dp),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            LoveCardEditorCanvas(
                item = LoveCardDraftItem(
                    type = cardType,
                    prompt = deckItem.card.prompt,
                    choices = deckItem.choices(),
                    theme = deckItem.card.theme ?: "blush",
                    decorations = deckItem.decorations()
                ),
                editable = false,
                canAnswer = canAnswer,
                answeringResponse = existingResponse ?: LoveCardDraftResponse(cardId = deckItem.card.cardId),
                onResponseChange = onResponseChange,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            )
            BurstingEmojiOverlay(
                cardId = deckItem.card.cardId,
                animationStyle = deckItem.card.animationStyle,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private data class LoveCardVisualTheme(
    val background: List<Color>,
    val backLayerOne: Color,
    val backLayerTwo: Color,
    val badgeFill: Color,
    val badgeText: Color,
    val primaryText: Color,
    val glassFill: Color,
    val blossomColor: Color,
    val accent: Color,
    val mood: String
)

@Composable
private fun StackedDreamLayer(
    modifier: Modifier,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(40.dp)) // Match main card
            .background(color)
    )
}

@Composable
private fun DreamyCardBackground(
    modifier: Modifier = Modifier,
    theme: LoveCardVisualTheme
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        fun drawCloud(cx: Float, cy: Float, scale: Float = 1f) {
            val cloudColor = Color.White.copy(alpha = 0.94f)
            drawCircle(cloudColor, radius = width * 0.07f * scale, center = Offset(cx, cy))
            drawCircle(cloudColor, radius = width * 0.055f * scale, center = Offset(cx + width * 0.08f * scale, cy - height * 0.018f))
            drawCircle(cloudColor, radius = width * 0.06f * scale, center = Offset(cx + width * 0.13f * scale, cy + height * 0.01f))
            drawRoundRect(
                color = cloudColor,
                topLeft = Offset(cx - width * 0.08f * scale, cy),
                size = androidx.compose.ui.geometry.Size(width * 0.26f * scale, height * 0.06f * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f, 40f)
            )
        }

        fun drawBlossom(center: Offset, petalRadius: Float, petalColor: Color, coreColor: Color = Color.White.copy(alpha = 0.95f)) {
            val offsets = listOf(
                Offset(0f, -petalRadius),
                Offset(petalRadius, 0f),
                Offset(0f, petalRadius),
                Offset(-petalRadius, 0f)
            )
            offsets.forEach { offset ->
                drawCircle(
                    color = petalColor,
                    radius = petalRadius * 0.78f,
                    center = center + offset
                )
            }
            drawCircle(
                color = coreColor,
                radius = petalRadius * 0.46f,
                center = center
            )
        }

        fun drawHeart(center: Offset, size: Float, color: Color) {
            val path = Path().apply {
                moveTo(center.x, center.y + size * 0.34f)
                cubicTo(
                    center.x - size, center.y - size * 0.1f,
                    center.x - size * 0.88f, center.y - size,
                    center.x, center.y - size * 0.38f
                )
                cubicTo(
                    center.x + size * 0.88f, center.y - size,
                    center.x + size, center.y - size * 0.1f,
                    center.x, center.y + size * 0.34f
                )
                close()
            }
            drawPath(path, color = color)
        }

        when (theme.mood) {
            "blush" -> {
                drawCircle(theme.blossomColor.copy(alpha = 0.22f), radius = width * 0.22f, center = Offset(width * 0.18f, height * 0.2f))
                drawCircle(theme.accent.copy(alpha = 0.16f), radius = width * 0.18f, center = Offset(width * 0.82f, height * 0.76f))
                drawHeart(Offset(width * 0.18f, height * 0.2f), width * 0.03f, Color.White.copy(alpha = 0.9f))
                drawHeart(Offset(width * 0.82f, height * 0.26f), width * 0.024f, Color(0xFFFFF2FA))
                drawBlossom(Offset(width * 0.18f, height * 0.8f), width * 0.034f, theme.blossomColor.copy(alpha = 0.78f))
                drawBlossom(Offset(width * 0.86f, height * 0.18f), width * 0.028f, Color.White.copy(alpha = 0.85f))
                drawBlossom(Offset(width * 0.9f, height * 0.82f), width * 0.03f, theme.blossomColor.copy(alpha = 0.68f))
            }

            "question" -> {
                val stars = listOf(
                    Offset(width * 0.12f, height * 0.12f),
                    Offset(width * 0.26f, height * 0.1f),
                    Offset(width * 0.42f, height * 0.08f),
                    Offset(width * 0.62f, height * 0.1f),
                    Offset(width * 0.78f, height * 0.14f),
                    Offset(width * 0.86f, height * 0.2f),
                    Offset(width * 0.18f, height * 0.24f)
                )
                stars.forEachIndexed { index, offset ->
                    drawCircle(
                        color = if (index % 2 == 0) Color(0xFFFFD85A) else Color.White.copy(alpha = 0.82f),
                        radius = if (index % 2 == 0) 4f else 3f,
                        center = offset
                    )
                }
                drawCircle(Color(0xFFFFD63B), radius = width * 0.11f, center = Offset(width * 0.74f, height * 0.16f))
                drawCircle(theme.background.first(), radius = width * 0.095f, center = Offset(width * 0.78f, height * 0.15f))
                drawCloud(width * 0.28f, height * 0.22f)
                drawCloud(width * 0.84f, height * 0.3f, scale = 1.06f)
                val hillBack = Path().apply {
                    moveTo(0f, height * 0.58f)
                    quadraticTo(width * 0.14f, height * 0.5f, width * 0.34f, height * 0.58f)
                    quadraticTo(width * 0.52f, height * 0.64f, width * 0.7f, height * 0.56f)
                    quadraticTo(width * 0.88f, height * 0.48f, width, height * 0.54f)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(hillBack, color = theme.backLayerOne.copy(alpha = 0.64f))
                val hillFront = Path().apply {
                    moveTo(0f, height * 0.74f)
                    quadraticTo(width * 0.22f, height * 0.6f, width * 0.46f, height * 0.7f)
                    quadraticTo(width * 0.7f, height * 0.8f, width, height * 0.66f)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(hillFront, color = theme.backLayerTwo.copy(alpha = 0.78f))
            }

            "meadow" -> {
                drawCircle(theme.accent.copy(alpha = 0.18f), radius = width * 0.2f, center = Offset(width * 0.14f, height * 0.16f))
                drawCircle(Color.White.copy(alpha = 0.86f), radius = width * 0.09f, center = Offset(width * 0.78f, height * 0.16f))
                drawCloud(width * 0.2f, height * 0.2f, scale = 0.92f)
                drawBlossom(Offset(width * 0.18f, height * 0.82f), width * 0.032f, Color.White.copy(alpha = 0.9f), coreColor = Color(0xFFFFD25E))
                drawBlossom(Offset(width * 0.78f, height * 0.72f), width * 0.04f, Color(0xFFFFF5F8), coreColor = Color(0xFFFFD25E))
                drawBlossom(Offset(width * 0.9f, height * 0.24f), width * 0.025f, theme.blossomColor.copy(alpha = 0.9f), coreColor = Color.White)
                val meadowBack = Path().apply {
                    moveTo(0f, height * 0.64f)
                    quadraticTo(width * 0.24f, height * 0.5f, width * 0.46f, height * 0.62f)
                    quadraticTo(width * 0.68f, height * 0.76f, width, height * 0.58f)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(meadowBack, color = theme.backLayerOne.copy(alpha = 0.68f))
                val meadowFront = Path().apply {
                    moveTo(0f, height * 0.78f)
                    quadraticTo(width * 0.22f, height * 0.66f, width * 0.42f, height * 0.8f)
                    quadraticTo(width * 0.72f, height * 0.96f, width, height * 0.74f)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(meadowFront, color = theme.backLayerTwo.copy(alpha = 0.84f))
            }

            "blossom" -> {
                drawCircle(theme.accent.copy(alpha = 0.15f), radius = width * 0.25f, center = Offset(width * 0.15f, height * 0.15f))
                repeat(12) { i ->
                    drawBlossom(
                        center = Offset(width * (0.1f + (i * 0.3f) % 0.8f), height * (0.1f + (i * i * 0.15f) % 0.8f)),
                        petalRadius = width * (0.02f + (i % 3) * 0.008f),
                        petalColor = theme.blossomColor.copy(alpha = 0.6f + (i % 3) * 0.1f),
                        coreColor = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            "ocean" -> {
                drawCircle(theme.accent.copy(alpha = 0.2f), radius = width * 0.3f, center = Offset(width * 0.8f, height * 0.8f))
                val waveBack = Path().apply {
                    moveTo(0f, height * 0.6f)
                    quadraticTo(width * 0.25f, height * 0.5f, width * 0.5f, height * 0.6f)
                    quadraticTo(width * 0.75f, height * 0.7f, width, height * 0.55f)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(waveBack, color = theme.backLayerOne.copy(alpha = 0.6f))
                val waveFront = Path().apply {
                    moveTo(0f, height * 0.7f)
                    quadraticTo(width * 0.25f, height * 0.8f, width * 0.5f, height * 0.7f)
                    quadraticTo(width * 0.8f, height * 0.6f, width, height * 0.75f)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(waveFront, color = theme.backLayerTwo.copy(alpha = 0.8f))
                repeat(8) { i ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = width * (0.02f + (i % 3) * 0.01f),
                        center = Offset(width * (0.2f + (i * 0.4f) % 0.6f), height * (0.3f + (i * i * 0.2f) % 0.5f)),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }
            }
            "lavender" -> {
                drawCircle(theme.accent.copy(alpha = 0.3f), radius = width * 0.2f, center = Offset(width * 0.5f, height * 0.3f))
                repeat(15) { i ->
                    drawCircle(
                        color = theme.blossomColor.copy(alpha = 0.8f),
                        radius = width * (0.01f + (i % 2) * 0.005f),
                        center = Offset(width * (0.1f + (i * 0.35f) % 0.8f), height * (0.1f + (i * 0.25f) % 0.8f))
                    )
                }
            }
            "honey" -> {
                drawCircle(theme.accent.copy(alpha = 0.15f), radius = width * 0.22f, center = Offset(width * 0.2f, height * 0.8f))
                drawCircle(theme.blossomColor.copy(alpha = 0.3f), radius = width * 0.18f, center = Offset(width * 0.8f, height * 0.2f))
                repeat(8) { i ->
                    val cx = width * (0.15f + (i * 0.23f) % 0.75f)
                    val cy = height * (0.15f + (i * 0.31f) % 0.75f)
                    val hex = Path().apply {
                        val r = width * 0.045f
                        moveTo(cx, cy - r)
                        lineTo(cx + r * 0.866f, cy - r * 0.5f)
                        lineTo(cx + r * 0.866f, cy + r * 0.5f)
                        lineTo(cx, cy + r)
                        lineTo(cx - r * 0.866f, cy + r * 0.5f)
                        lineTo(cx - r * 0.866f, cy - r * 0.5f)
                        close()
                    }
                    drawPath(hex, color = theme.backLayerOne.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                }
            }
            "stardust" -> {
                drawCircle(theme.backLayerTwo.copy(alpha = 0.4f), radius = width * 0.15f, center = Offset(width * 0.8f, height * 0.2f))
                repeat(20) { i ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f + (i % 3) * 0.1f),
                        radius = width * (0.005f + (i % 2) * 0.005f),
                        center = Offset(width * (0.05f + (i * 0.47f) % 0.9f), height * (0.05f + (i * 0.61f) % 0.9f))
                    )
                }
                val constellation = Path().apply {
                    moveTo(width * 0.2f, height * 0.3f)
                    lineTo(width * 0.35f, height * 0.25f)
                    lineTo(width * 0.45f, height * 0.4f)
                    lineTo(width * 0.6f, height * 0.35f)
                }
                drawPath(constellation, color = theme.accent.copy(alpha = 0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                drawCircle(Color.White, radius = width * 0.012f, center = Offset(width * 0.2f, height * 0.3f))
                drawCircle(Color.White, radius = width * 0.012f, center = Offset(width * 0.35f, height * 0.25f))
                drawCircle(Color.White, radius = width * 0.012f, center = Offset(width * 0.45f, height * 0.4f))
                drawCircle(Color.White, radius = width * 0.012f, center = Offset(width * 0.6f, height * 0.35f))
            }
            else -> {
                drawCircle(theme.accent.copy(alpha = 0.22f), radius = width * 0.18f, center = Offset(width * 0.2f, height * 0.18f))
                drawCircle(theme.blossomColor.copy(alpha = 0.16f), radius = width * 0.22f, center = Offset(width * 0.78f, height * 0.76f))
                drawBlossom(Offset(width * 0.16f, height * 0.22f), width * 0.026f, Color.White.copy(alpha = 0.84f))
                drawBlossom(Offset(width * 0.82f, height * 0.18f), width * 0.03f, theme.blossomColor.copy(alpha = 0.7f))
                drawHeart(Offset(width * 0.82f, height * 0.26f), width * 0.028f, Color(0xFFFFF0F7))
                repeat(6) { index ->
                    drawCircle(
                        color = if (index % 2 == 0) Color.White.copy(alpha = 0.9f) else theme.blossomColor.copy(alpha = 0.8f),
                        radius = width * 0.01f,
                        center = Offset(width * (0.14f + index * 0.12f), height * (0.12f + (index % 3) * 0.06f))
                    )
                }
            }
        }

        drawCircle(
            color = theme.accent.copy(alpha = 0.16f),
            radius = width * 0.22f,
            center = Offset(width * 0.18f, height * 0.82f)
        )
    }
}

@Composable
private fun AnimatedGiftRevealCard(
    prompt: String,
    theme: LoveCardVisualTheme,
    isOpened: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedDistance by remember { mutableStateOf(0f) }
    val lidLift by animateFloatAsState(
        targetValue = if (isOpened) -26f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 220f),
        label = "gift-lid-lift"
    )
    val noteReveal by animateFloatAsState(
        targetValue = if (isOpened) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 180f),
        label = "gift-note-reveal"
    )

    Box(
        modifier = modifier
            .clickable(enabled = !isOpened, onClick = onOpen)
            .pointerInput(isOpened) {
                if (!isOpened) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            draggedDistance += -dragAmount
                            if (draggedDistance > 24f) {
                                draggedDistance = 0f
                                onOpen()
                            }
                        },
                        onDragEnd = { draggedDistance = 0f },
                        onDragCancel = { draggedDistance = 0f }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .offset(y = (-18 * noteReveal).dp)
                .alpha(noteReveal),
            color = Color.White.copy(alpha = 0.76f),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = prompt,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF6B2DD6)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .align(Alignment.BottomCenter),
                color = Color(0xFFFFB7D2),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.54f))
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight()
                            .width(24.dp)
                            .background(Color(0xFFFFF1F7))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(Color(0xFFFFF1F7))
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = lidLift.dp),
                color = Color(0xFFFF92BD),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.56f))
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight()
                            .width(24.dp)
                            .background(Color(0xFFFFF1F7))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(Color(0xFFFFF1F7))
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (8 + lidLift).dp)
                    .alpha(if (isOpened) 0.86f else 1f),
                shape = RoundedCornerShape(999.dp),
                color = theme.badgeFill,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.48f))
            ) {
                Text(
                    text = if (isOpened) "Opened with love" else "Tap or pull ribbon",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = theme.badgeText
                )
            }
        }
    }
}

private fun dreamyAccentFor(type: String): Pair<Color, Color> {
    val theme = loveCardThemeFor(enumValueOfSafe(type))
    return theme.backLayerOne to theme.backLayerTwo
}

@Composable
private fun LoveCardStickerRender(
    decorations: List<LoveCardStickerPlacement>,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    selectedStickerId: String? = null,
    onSelectSticker: (String?) -> Unit = {},
    onMoveSticker: (String, Float, Float) -> Unit = { _, _, _ -> },
    onScaleSticker: (String, Float) -> Unit = { _, _ -> },
    onDeleteSticker: (String) -> Unit = {}
) {
    if (decorations.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier
            .zIndex(100f)
            .fillMaxSize()
    ) {
        if (selectedStickerId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(editable) {
                        if (!editable) return@pointerInput
                        detectTapGestures {
                            onSelectSticker(null)
                        }
                    }
            )
        }

        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = androidx.compose.ui.platform.LocalDensity.current

        decorations.forEach { decoration ->
            val isSelected = editable && decoration.id == selectedStickerId
            val baseWidth = when (decoration.style) {
                "emoji" -> 64.dp * decoration.scale
                else -> 80.dp * decoration.scale
            }
            val baseHeight = when (decoration.style) {
                "emoji" -> 64.dp * decoration.scale
                else -> 70.dp * decoration.scale
            }

            key("sticker-item-${decoration.id}") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset {
                            val x = (decoration.normalizedX * widthPx - with(density) { baseWidth.toPx() } / 2f)
                            val y = (decoration.normalizedY * heightPx - with(density) { baseHeight.toPx() } / 2f)
                            IntOffset(x.roundToInt(), y.roundToInt())
                        }
                        .width(baseWidth)
                        .height(baseHeight)
                        .zIndex(if (isSelected) 50f else 10f)
                        .pointerInput(editable, decoration.id, widthPx, heightPx, isSelected) {
                            if (!editable) return@pointerInput
                            if (isSelected) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onMoveSticker(decoration.id, dragAmount.x / widthPx, dragAmount.y / heightPx)
                                }
                            }
                        }
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            onSelectSticker(decoration.id)
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LoveCardStickerBubble(
                            decoration = decoration,
                            selected = isSelected,
                            onScaleSticker = onScaleSticker,
                            onDeleteSticker = onDeleteSticker,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun LoveCardStickerBubble(
    decoration: LoveCardStickerPlacement,
    selected: Boolean,
    onScaleSticker: (String, Float) -> Unit = { _, _ -> },
    onDeleteSticker: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val float = rememberInfiniteTransition(label = "sticker-float-${decoration.id}")
    val drift by float.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sticker-drift"
    )
    val scalePulse by float.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sticker-scale"
    )

    val bubbleColors = when (decoration.style) {
        "gif" -> if (!decoration.mediaUrl.isNullOrBlank()) listOf(Color.Transparent, Color.Transparent) else listOf(Color(0xFFF9EEFF), Color(0xFFE8E0FF))
        "caption" -> listOf(Color(0xFFFFEFF6), Color(0xFFFFD9EA))
        "emoji" -> listOf(Color.Transparent, Color.Transparent)
        else -> listOf(Color.White.copy(alpha = 0.74f), Color.White.copy(alpha = 0.42f))
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.wrapContentSize()) {
            Surface(
                modifier = Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        rotationZ = decoration.rotation
                        scaleX = scalePulse
                        scaleY = scalePulse
                        translationY = drift
                    },
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (decoration.style == "emoji" || (decoration.style == "gif" && !decoration.mediaUrl.isNullOrBlank())) {
                        if (selected) Color(0xFF8A5BFF).copy(alpha = 0.6f) else Color.Transparent
                    } else {
                        if (selected) Color(0xFF8A5BFF) else Color.White.copy(alpha = 0.5f)
                    }
                ),
                shadowElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .background(Brush.linearGradient(bubbleColors))
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    if (decoration.style == "gif" && !decoration.mediaUrl.isNullOrBlank()) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val imageLoader = remember {
                            ImageLoader.Builder(context)
                                .components {
                                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                                        add(coil.decode.ImageDecoderDecoder.Factory())
                                    } else {
                                        add(coil.decode.GifDecoder.Factory())
                                    }
                                }
                                .build()
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(decoration.mediaUrl)
                                .crossfade(true)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    } else {
                        val baseSize = when (decoration.style) {
                            "emoji" -> 36.sp
                            "caption" -> 22.sp
                            else -> 24.sp
                        }
                        Text(
                            text = decoration.content,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = baseSize * decoration.scale
                            ),
                            color = when (decoration.style) {
                                "gif" -> Color(0xFF6F3DF0)
                                "caption" -> Color(0xFFC14A8A)
                                else -> Color(0xFF5B22D4)
                            }
                        )
                    }
                }
            }

            if (selected) {
                // Delete button anchored to top end of the content size
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color.Red),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(26.dp)
                        .zIndex(10f)
                        .clickable { onDeleteSticker(decoration.id) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✕", color = Color.Red, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                // Resize handle anchored to bottom end of the content size
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFF8A5BFF)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 6.dp)
                        .size(26.dp)
                        .zIndex(10f)
                        .pointerInput(decoration.id) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val delta = dragAmount.x + dragAmount.y
                                val scaleMultiplier = 1f + (delta / 80f)
                                onScaleSticker(decoration.id, scaleMultiplier)
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⤡", color = Color(0xFF8A5BFF), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class ComposerStickerOption(
    val content: String,
    val style: String = "emoji",
    val mediaUrl: String? = null
)

private data class StoryThemeOption(
    val key: String,
    val label: String,
    val swatch: List<Color>
)

private fun stickerOptions() = listOf(
    ComposerStickerOption("❤️"), ComposerStickerOption("✨"), ComposerStickerOption("🌸"),
    ComposerStickerOption("🦋"), ComposerStickerOption("🧸"), ComposerStickerOption("🍭"),
    ComposerStickerOption("🎀"), ComposerStickerOption("🌈"), ComposerStickerOption("🌙"),
    ComposerStickerOption("☁️"), ComposerStickerOption("🍀"), ComposerStickerOption("🍩"),
    ComposerStickerOption("Love", "caption"), ComposerStickerOption("xoxo", "caption"),
    ComposerStickerOption("Always", "caption"), ComposerStickerOption("Cute", "caption")
)

private fun storyThemeOptions(): List<StoryThemeOption> = listOf(
    StoryThemeOption("blush", "Blush", listOf(Color(0xFFFFB0D0), Color(0xFFFFCCE4), Color(0xFFFFF0F8))),
    StoryThemeOption("moon", "Moon", listOf(Color(0xFF2442FF), Color(0xFF2754FF), Color(0xFF3AD8FF))),
    StoryThemeOption("meadow", "Meadow", listOf(Color(0xFFFFBE67), Color(0xFFFFD98B), Color(0xFFFFF3D7))),
    StoryThemeOption("gift", "Gift", listOf(Color(0xFFFF9ABF), Color(0xFFFFC5D6), Color(0xFFF2E9FF))),
    StoryThemeOption("berry", "Berry", listOf(Color(0xFFF46BB9), Color(0xFFFFB7D8), Color(0xFFFFF4FB))),
    StoryThemeOption("sunset", "Sunset", listOf(Color(0xFFFFC067), Color(0xFFFF9A80), Color(0xFFFFE5B9))),
    StoryThemeOption("blossom", "Blossom", listOf(Color(0xFFFFC8CD), Color(0xFFFFE2E5), Color(0xFFFFF0F2))),
    StoryThemeOption("ocean", "Ocean", listOf(Color(0xFF00C9FF), Color(0xFF92FE9D), Color(0xFFE0FFF1))),
    StoryThemeOption("lavender", "Lavender", listOf(Color(0xFFB19CD9), Color(0xFFD8B4E2), Color(0xFFF4E4FA))),
    StoryThemeOption("honey", "Honey", listOf(Color(0xFFFFD15C), Color(0xFFFFE79A), Color(0xFFFFF6D3))),
    StoryThemeOption("stardust", "Stardust", listOf(Color(0xFF0A0F45), Color(0xFF2C1362), Color(0xFF4C2A85)))
)

private fun loveCardThemeFor(type: LoveCardType, themeKey: String? = null): LoveCardVisualTheme = when ((themeKey ?: "").lowercase()) {
    "moon" -> LoveCardVisualTheme(
        background = listOf(Color(0xFF2442FF), Color(0xFF2754FF), Color(0xFF3AD8FF)),
        backLayerOne = Color(0xFFED59A1),
        backLayerTwo = Color(0xFFFFBE49),
        badgeFill = Color.White.copy(alpha = 0.22f),
        badgeText = Color.White,
        primaryText = Color.White,
        glassFill = Color.White.copy(alpha = 0.22f),
        blossomColor = Color(0xFF97A4FF),
        accent = Color(0xFF6B73FF),
        mood = "question"
    )
    "meadow" -> LoveCardVisualTheme(
        background = listOf(Color(0xFFFFBE67), Color(0xFFFFD98B), Color(0xFFFFF3D7)),
        backLayerOne = Color(0xFF7ED87D),
        backLayerTwo = Color(0xFF4CC69E),
        badgeFill = Color.White.copy(alpha = 0.34f),
        badgeText = Color(0xFF925D00),
        primaryText = Color(0xFF6D4600),
        glassFill = Color.White.copy(alpha = 0.38f),
        blossomColor = Color(0xFFFFF6FF),
        accent = Color(0xFFFFDE76),
        mood = "meadow"
    )
    "gift" -> LoveCardVisualTheme(
        background = listOf(Color(0xFFFF9ABF), Color(0xFFFFC5D6), Color(0xFFF2E9FF)),
        backLayerOne = Color(0xFF52E0BF),
        backLayerTwo = Color(0xFFFFD76A),
        badgeFill = Color.White.copy(alpha = 0.36f),
        badgeText = Color(0xFFB24586),
        primaryText = Color(0xFF8A2767),
        glassFill = Color.White.copy(alpha = 0.44f),
        blossomColor = Color(0xFFFFD6EC),
        accent = Color(0xFFFF83B8),
        mood = "gift"
    )
    "berry" -> LoveCardVisualTheme(
        background = listOf(Color(0xFFF46BB9), Color(0xFFFFB7D8), Color(0xFFFFF4FB)),
        backLayerOne = Color(0xFFA95CFF),
        backLayerTwo = Color(0xFFFFE07F),
        badgeFill = Color.White.copy(alpha = 0.38f),
        badgeText = Color(0xFFA52F77),
        primaryText = Color(0xFF7A255D),
        glassFill = Color.White.copy(alpha = 0.42f),
        blossomColor = Color(0xFFFF9BD0),
        accent = Color(0xFFFF6DB8),
        mood = "blush"
    )
    "sunset" -> LoveCardVisualTheme(
        background = listOf(Color(0xFFFFC067), Color(0xFFFF9A80), Color(0xFFFFE5B9)),
        backLayerOne = Color(0xFFFE6F91),
        backLayerTwo = Color(0xFFFFE36E),
        badgeFill = Color.White.copy(alpha = 0.34f),
        badgeText = Color(0xFF8C4B00),
        primaryText = Color(0xFF6A3200),
        glassFill = Color.White.copy(alpha = 0.38f),
        blossomColor = Color(0xFFFFF2F5),
        accent = Color(0xFFFFAD62),
        mood = "gift"
    )
    "blush" -> LoveCardVisualTheme(
        background = listOf(Color(0xFFFFB0D0), Color(0xFFFFCCE4), Color(0xFFFFF0F8)),
        backLayerOne = Color(0xFFFFC0DE),
        backLayerTwo = Color(0xFFFFE4F0),
        badgeFill = Color.White.copy(alpha = 0.4f),
        badgeText = Color(0xFFB04188),
        primaryText = Color(0xFF6A2761),
        glassFill = Color.White.copy(alpha = 0.42f),
        blossomColor = Color(0xFFFF8EC5),
        accent = Color(0xFFFF7BAA),
        mood = "blush"
    )
    "blossom" -> LoveCardVisualTheme(
        background = listOf(Color(0xFFFFC8CD), Color(0xFFFFE2E5), Color(0xFFFFF0F2)),
        backLayerOne = Color(0xFFFF8FA3),
        backLayerTwo = Color(0xFFFFB3C6),
        badgeFill = Color.White.copy(alpha = 0.44f),
        badgeText = Color(0xFFC9184A),
        primaryText = Color(0xFF800F2F),
        glassFill = Color.White.copy(alpha = 0.38f),
        blossomColor = Color(0xFFFF5D8F),
        accent = Color(0xFFFF6B8B),
        mood = "blossom"
    )
    "ocean" -> LoveCardVisualTheme(
        background = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D), Color(0xFFE0FFF1)),
        backLayerOne = Color(0xFF00A2D3),
        backLayerTwo = Color(0xFF56D195),
        badgeFill = Color.White.copy(alpha = 0.38f),
        badgeText = Color(0xFF00587A),
        primaryText = Color(0xFF003851),
        glassFill = Color.White.copy(alpha = 0.35f),
        blossomColor = Color(0xFFB3FFF0),
        accent = Color(0xFF00E1C9),
        mood = "ocean"
    )
    "lavender" -> LoveCardVisualTheme(
        background = listOf(Color(0xFFB19CD9), Color(0xFFD8B4E2), Color(0xFFF4E4FA)),
        backLayerOne = Color(0xFF9A7BC4),
        backLayerTwo = Color(0xFFC0A2DD),
        badgeFill = Color.White.copy(alpha = 0.38f),
        badgeText = Color(0xFF5C33A2),
        primaryText = Color(0xFF38156E),
        glassFill = Color.White.copy(alpha = 0.36f),
        blossomColor = Color(0xFFFFEBFF),
        accent = Color(0xFF8358C8),
        mood = "lavender"
    )
    "honey" -> LoveCardVisualTheme(
        background = listOf(Color(0xFFFFD15C), Color(0xFFFFE79A), Color(0xFFFFF6D3)),
        backLayerOne = Color(0xFFFFB347),
        backLayerTwo = Color(0xFFFFD166),
        badgeFill = Color.White.copy(alpha = 0.4f),
        badgeText = Color(0xFFA66000),
        primaryText = Color(0xFF6B3A00),
        glassFill = Color.White.copy(alpha = 0.42f),
        blossomColor = Color(0xFFFFFCCC),
        accent = Color(0xFFFFAA00),
        mood = "honey"
    )
    "stardust" -> LoveCardVisualTheme(
        background = listOf(Color(0xFF0A0F45), Color(0xFF2C1362), Color(0xFF4C2A85)),
        backLayerOne = Color(0xFFD13397),
        backLayerTwo = Color(0xFF8A30B8),
        badgeFill = Color.White.copy(alpha = 0.16f),
        badgeText = Color(0xFFFFD7F4),
        primaryText = Color(0xFFFFFFFF),
        glassFill = Color.White.copy(alpha = 0.16f),
        blossomColor = Color(0xFFFFADF0),
        accent = Color(0xFFFF489A),
        mood = "stardust"
    )
    else -> when (type) {
        LoveCardType.CUTE_NOTE -> loveCardThemeFor(type, "blush")
        LoveCardType.QUESTION -> loveCardThemeFor(type, "moon")
        LoveCardType.MULTIPLE_CHOICE -> loveCardThemeFor(type, "meadow")
        LoveCardType.ANIMATED_GIFT -> loveCardThemeFor(type, "gift")
        LoveCardType.PHOTO_MEMORY -> loveCardThemeFor(type, "blossom")
        LoveCardType.COUPON -> loveCardThemeFor(type, "honey")
        LoveCardType.MUSIC_DEDICATION -> loveCardThemeFor(type, "lavender")
        LoveCardType.COUNTDOWN -> loveCardThemeFor(type, "sunset")
        LoveCardType.VOICE_MESSAGE -> loveCardThemeFor(type, "ocean")
        LoveCardType.SCRATCH_REVEAL -> loveCardThemeFor(type, "stardust")
    }
}

private fun loveCardThemeFor(type: LoveCardType): LoveCardVisualTheme = loveCardThemeFor(type, null)

@Composable
private fun LoveCardAnswerEditor(
    deckItem: LoveCardDeckItem,
    response: LoveCardDraftResponse,
    onResponseChange: (LoveCardDraftResponse) -> Unit
) {
    val draftItem = remember(deckItem) {
        LoveCardDraftItem(
            type = enumValueOfSafe(deckItem.card.type),
            prompt = deckItem.card.prompt,
            choices = deckItem.choices(),
            theme = deckItem.card.theme ?: "blush",
            decorations = deckItem.decorations()
        )
    }

    LoveCardEditorCanvas(
        item = draftItem,
        selectedStickerId = null,
        onSelectSticker = {},
        onMoveSticker = { _, _, _ -> },
        onPromptChange = {},
        editable = false,
        answeringResponse = response,
        onResponseChange = onResponseChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

@Composable
private fun EmojiRow(
    selected: String?,
    onSelect: (String) -> Unit,
    onGifClick: () -> Unit = {}
) {
    val emojis = listOf("💖", "🥹", "😘", "🌙", "🌸", "💌")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            emojis.forEach { emoji ->
                Surface(
                    modifier = Modifier.clickable { onSelect(emoji) },
                    color = if (selected == emoji) Color.White.copy(alpha = 0.28f) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = emoji,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            Surface(
                modifier = Modifier.clickable { onGifClick() },
                color = if (selected?.startsWith("http") == true || selected?.startsWith("content://") == true) Color.White.copy(alpha = 0.28f) else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "GIF",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }
        
        val isGif = selected?.let { it.startsWith("http") || it.startsWith("content://") } == true
        if (isGif) {
            Spacer(modifier = Modifier.height(8.dp))
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(selected)
                    .decoderFactory(GifDecoder.Factory())
                    .build(),
                contentDescription = "Selected GIF",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

private fun typeLabel(type: LoveCardType): String = when (type) {
    LoveCardType.CUTE_NOTE -> "Cute Note"
    LoveCardType.QUESTION -> "Question"
    LoveCardType.MULTIPLE_CHOICE -> "Multiple Choice"
    LoveCardType.ANIMATED_GIFT -> "Animated Gift"
    LoveCardType.PHOTO_MEMORY -> "Photo Memory"
    LoveCardType.COUPON -> "Love Coupon"
    LoveCardType.MUSIC_DEDICATION -> "Dedicated Song"
    LoveCardType.COUNTDOWN -> "Countdown"
    LoveCardType.VOICE_MESSAGE -> "Voice Note"
    LoveCardType.SCRATCH_REVEAL -> "Scratch Reveal"
}

private fun typeLabel(type: String): String = typeLabel(enumValueOfSafe(type))

// Ensure we fall back nicely if standard icons aren't explicitly loaded; using placeholders where needed
private fun iconFor(type: LoveCardType) = when (type) {
    LoveCardType.CUTE_NOTE -> Icons.Outlined.EditNote
    LoveCardType.QUESTION -> Icons.Default.QuestionAnswer
    LoveCardType.MULTIPLE_CHOICE -> Icons.Default.Sell
    LoveCardType.ANIMATED_GIFT -> Icons.Default.CardGiftcard
    LoveCardType.PHOTO_MEMORY -> Icons.Default.Style
    LoveCardType.COUPON -> Icons.Default.CardGiftcard
    LoveCardType.MUSIC_DEDICATION -> Icons.Default.AutoAwesome
    LoveCardType.COUNTDOWN -> Icons.Default.Style
    LoveCardType.VOICE_MESSAGE -> Icons.Default.QuestionAnswer
    LoveCardType.SCRATCH_REVEAL -> Icons.Default.AutoAwesome
}

private fun iconFor(type: String) = iconFor(enumValueOfSafe(type))

private fun paletteFor(type: String): Pair<Color, Color> = when (enumValueOfSafe(type)) {
    LoveCardType.CUTE_NOTE -> Color(0xFFFF90C0) to Color(0xFFFFD7E7)
    LoveCardType.QUESTION -> Color(0xFF8D6BFF) to Color(0xFFD9CCFF)
    LoveCardType.MULTIPLE_CHOICE -> Color(0xFFFFA06C) to Color(0xFFFFE0C4)
    LoveCardType.ANIMATED_GIFT -> Color(0xFFFF6B8C) to Color(0xFFFFC1D4)
    LoveCardType.PHOTO_MEMORY -> Color(0xFFFFA0C0) to Color(0xFFFFF0F5)
    LoveCardType.COUPON -> Color(0xFFFFD166) to Color(0xFFFFF5D1)
    LoveCardType.MUSIC_DEDICATION -> Color(0xFFB19CD9) to Color(0xFFE6DDF2)
    LoveCardType.COUNTDOWN -> Color(0xFFFFC067) to Color(0xFFFFE5B9)
    LoveCardType.VOICE_MESSAGE -> Color(0xFF00C9FF) to Color(0xFFDDF5FF)
    LoveCardType.SCRATCH_REVEAL -> Color(0xFF4C2A85) to Color(0xFFE2DDF0)
}

private fun defaultDraftCard(type: LoveCardType): LoveCardDraftItem = when (type) {
    LoveCardType.CUTE_NOTE -> LoveCardDraftItem(
        type = type,
        prompt = "A tiny sweet note just for you",
        theme = "blush",
        decorations = emptyList()
    )
    LoveCardType.QUESTION -> LoveCardDraftItem(
        type = type,
        prompt = "What made you smile today?",
        theme = "moon",
        decorations = emptyList()
    )
    LoveCardType.MULTIPLE_CHOICE -> LoveCardDraftItem(
        type = type,
        prompt = "Pick our next little mood",
        choices = listOf("Date night", "Movie cuddle", "Late call", "Sweet nap"),
        theme = "meadow",
        decorations = emptyList()
    )
    LoveCardType.ANIMATED_GIFT -> LoveCardDraftItem(
        type = type,
        prompt = "Open this little animated gift from me",
        theme = "gift",
        decorations = emptyList()
    )
    LoveCardType.PHOTO_MEMORY -> LoveCardDraftItem(
        type = type,
        prompt = "Look at this cute memory of us",
        theme = "blossom",
        decorations = emptyList()
    )
    LoveCardType.COUPON -> LoveCardDraftItem(
        type = type,
        prompt = "Valid for one big squeezy hug",
        theme = "honey",
        decorations = emptyList()
    )
    LoveCardType.MUSIC_DEDICATION -> LoveCardDraftItem(
        type = type,
        prompt = "This song reminded me of you",
        theme = "lavender",
        decorations = emptyList()
    )
    LoveCardType.COUNTDOWN -> LoveCardDraftItem(
        type = type,
        prompt = "Counting down to us being together",
        theme = "sunset",
        decorations = emptyList()
    )
    LoveCardType.VOICE_MESSAGE -> LoveCardDraftItem(
        type = type,
        prompt = "Listen to my voice!",
        theme = "ocean",
        decorations = emptyList()
    )
    LoveCardType.SCRATCH_REVEAL -> LoveCardDraftItem(
        type = type,
        prompt = "I love you endlessly",
        theme = "stardust",
        decorations = emptyList()
    )
}

private fun responseCompletes(item: LoveCardDeckItem, response: LoveCardDraftResponse?): Boolean {
    if (response == null) return false
    return when (enumValueOfSafe(item.card.type)) {
        LoveCardType.MULTIPLE_CHOICE -> !response.selectedChoice.isNullOrBlank()
        LoveCardType.CUTE_NOTE, LoveCardType.ANIMATED_GIFT, LoveCardType.PHOTO_MEMORY, LoveCardType.VOICE_MESSAGE, LoveCardType.MUSIC_DEDICATION -> {
            !response.answerText.isNullOrBlank() || !response.emojiReaction.isNullOrBlank()
        }
        LoveCardType.QUESTION -> !response.answerText.isNullOrBlank()
        LoveCardType.COUPON, LoveCardType.COUNTDOWN, LoveCardType.SCRATCH_REVEAL -> {
            !response.emojiReaction.isNullOrBlank()
        }
    }
}

private fun enumValueOfSafe(type: String): LoveCardType {
    return runCatching { LoveCardType.valueOf(type) }.getOrDefault(LoveCardType.CUTE_NOTE)
}

@Composable
private fun LoveCardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    cursorBrush: Brush,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        enabled = enabled,
        cursorBrush = cursorBrush,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = textStyle.color.copy(alpha = 0.4f))
                    )
                }
                innerTextField()
            }
        }
    )
}

private data class BurstParticle(
    val emoji: String,
    val angleDeg: Float,
    val distanceFraction: Float,
    val delayMs: Int,
    val durationMs: Int,
    val sizeSp: Float,
    val initialOffsetX: Float,
    val initialOffsetY: Float
)

@Composable
private fun BurstingEmojiOverlay(
    cardId: String,
    animationStyle: String?,
    hasBurst: BooleanArray = remember { booleanArrayOf(false) },
    modifier: Modifier = Modifier
) {
    if (animationStyle.isNullOrEmpty() || animationStyle.equals("none", ignoreCase = true)) return
    if (hasBurst[0]) return
    val style = animationStyle.lowercase()

    val emojis = androidx.compose.runtime.remember(style) {
        when (style) {
            "burst"   -> listOf("💖", "💋", "🫶", "🌸", "🎀", "💕", "❤️", "🥰", "😘", "💗")
            "sparkle" -> listOf("✨", "🌙", "⭐", "💫", "🌟", "💎", "🔮", "🪐", "🌈", "☀️")
            "ocean"   -> listOf("🫧", "🌊", "🐚", "🐬", "🐠", "💧", "🐙", "🌺", "🦀", "🐳")
            "nature"  -> listOf("🐝", "🍯", "🌻", "🌼", "🦋", "🌿", "🍀", "🌷", "🌱", "🦚")
            else      -> listOf("💖", "💋", "🫶", "✨", "🌸", "💕", "❤️", "🌈", "😍", "💫")
        }
    }

    val particleCount = 55
    val particles = androidx.compose.runtime.remember(cardId, style) {
        List(particleCount) { i ->
            // Evenly spread angles around 360° with a small per-particle jitter
            val angle = (i * 360f / particleCount) + ((i * 17) % 23 - 11).toFloat()
            // Distance: 50–90% of screen height
            val dist = 0.5f + ((i * 7 + 3) % 40) / 100f
            // Stagger: dense core burst first, then trailing sparkles
            val delay = when {
                i < (particleCount * 0.6).toInt() -> i * 2
                i < (particleCount * 0.9).toInt() -> 30 + ((i * 3) % 70)
                else                               -> 100 + ((i * 5) % 120)
            }
            val duration = 1400 + ((i * 11) % 600) // 1.4–2.0 s per particle
            val size = listOf(26f, 32f, 38f, 44f)[(i * 3) % 4]
            val offsetX = ((i * 13) % 60 - 30).toFloat() // ±30 px from centre
            val offsetY = ((i * 7) % 60 - 30).toFloat()
            BurstParticle(
                emoji = emojis[i % emojis.size],
                angleDeg = angle,
                distanceFraction = dist,
                delayMs = delay,
                durationMs = duration,
                sizeSp = size,
                initialOffsetX = offsetX,
                initialOffsetY = offsetY
            )
        }
    }

    var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(cardId, style) {
        hasBurst[0] = true
        isVisible = true
        kotlinx.coroutines.delay(2600L)
        isVisible = false
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    if (isVisible) {
        Popup(
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                excludeFromSystemGesture = true
            )
        ) {
            Box(
                modifier = Modifier
                    .width(config.screenWidthDp.dp)
                    .height(config.screenHeightDp.dp)
            ) {
                particles.forEach { particle ->
                    var progress by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
                    androidx.compose.runtime.LaunchedEffect(cardId, style, particle) {
                        kotlinx.coroutines.delay(particle.delayMs.toLong())
                        val startTime = System.currentTimeMillis()
                        while (true) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val t = (elapsed.toFloat() / particle.durationMs).coerceIn(0f, 1f)
                            progress = t
                            if (t >= 1f) break
                            kotlinx.coroutines.delay(16)
                        }
                    }

                    // Cubic ease-out: fast initial explosion that decelerates
                    val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
                    val angleRad = Math.toRadians(particle.angleDeg.toDouble())
                    val travelPx = particle.distanceFraction * screenHeightPx * eased
                    val dx = (kotlin.math.cos(angleRad) * travelPx).toFloat()
                    val dy = (kotlin.math.sin(angleRad) * travelPx).toFloat()

                    val alpha = when {
                        progress < 0.08f -> (progress / 0.08f).coerceIn(0f, 1f)          // quick pop-in
                        progress > 0.65f -> (1f - (progress - 0.65f) / 0.35f).coerceIn(0f, 1f) // fade out
                        else             -> 1f
                    }
                    val scale = if (progress < 0.12f) 0.3f + (progress / 0.12f) * 0.7f else 1f

                    Text(
                        text = particle.emoji,
                        modifier = Modifier
                            .align(Alignment.Center) // origin = screen centre
                            .graphicsLayer {
                                translationX = particle.initialOffsetX + dx
                                translationY = particle.initialOffsetY + dy
                                this.alpha = alpha
                                scaleX = scale
                                scaleY = scale
                            },
                        fontSize = particle.sizeSp.sp
                    )
                }
            }
        }
    }
}



