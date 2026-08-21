package com.aman.gigi.ui

import com.aman.gigi.ui.components.AvatarEmojiPickerDialog
import com.aman.gigi.ui.screensaver.components.SafeGlassBox
import android.app.NotificationManager
import android.util.Log
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.aman.gigi.R
import com.aman.gigi.model.Connection
import com.aman.gigi.model.LoveCardDeck
import com.aman.gigi.model.LoveCardDraftItem
import com.aman.gigi.model.LoveCardType
import com.aman.gigi.model.MemberIdentity
import com.aman.gigi.model.ServerMode
import com.aman.gigi.model.toTheme
import com.aman.gigi.model.toConnectionTheme
import com.aman.gigi.viewmodel.ReceivedQuoteOverlay
import com.aman.gigi.viewmodel.ScreensaverViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.Cloudy
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun Developer(
    viewModel: ScreensaverViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val darkTheme = false
    val memberIdentity by viewModel.memberIdentity.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val activeConnections by viewModel.activeConnections.collectAsState()
    val quoteOverlay by viewModel.quoteOverlay.collectAsState()
    val loveCardDecks by viewModel.loveCardDecks.collectAsState()
    val activeLoveCardDeck by viewModel.activeLoveCardDeck.collectAsState()
    val selectedSweetConnectionId by viewModel.selectedSweetConnectionId.collectAsState()
    val groupMemberEmojis by viewModel.groupMemberEmojis.collectAsState()
    val galaxyNowPlaying by viewModel.nowPlayingByConnection.collectAsState()
    val galaxyMyNowPlaying by viewModel.myNowPlaying.collectAsState()
    val galaxyQuotes by viewModel.quotesByConnection.collectAsState()
    val groupMembers by viewModel.selectedConnectionMembers.collectAsState()
    val isBusy by viewModel.isAuthBusy.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val canAddConnection by viewModel.canAddConnection.collectAsState()
    val showConnectionsSheet by viewModel.showConnectionsSheet.collectAsState()
    
    var showComposerOverlay by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showLpcStudio by remember { mutableStateOf(false) }
    var emojiPickerTarget by remember { mutableStateOf<String?>(null) } // "self" or "partner"
    var selectedLoveCardDeck by remember { mutableStateOf<LoveCardDeck?>(null) }
    var showLoveCardGallery by remember { mutableStateOf(false) }
    var showQuoteDialog by remember { mutableStateOf(false) }
    var showProfileHub by remember { mutableStateOf(false) }
    var showConnectionSwitcher by remember { mutableStateOf(false) }
    var showConnectionSettings by remember { mutableStateOf(false) }
    // null = show the constellation hub (you + orbiting connections); set = a connection is opened.
    var openedConnectionId by rememberSaveable { mutableStateOf<String?>(null) }
    // Drives the expanding-bubble fill transition between hub and detail.
    var bubbleFillSpec by remember { mutableStateOf<BubbleFillSpec?>(null) }
    var showAddMembers by remember { mutableStateOf(false) }
    var showBreakPicker by remember { mutableStateOf(false) }
    val activeBreak by viewModel.activeBreak.collectAsState()
    val breakResponses by viewModel.breakResponses.collectAsState()
    val myBreakAnswer by viewModel.myBreakAnswer.collectAsState()
    val nebulaMotes by viewModel.nebulaMotes.collectAsState()
    val nebulaSearchResults by viewModel.nebulaSearchResults.collectAsState()
    val nebulaSearchQuery by viewModel.nebulaSearchQuery.collectAsState()
    val pendingGhostInvites by viewModel.pendingGhostInvites.collectAsState()
    val incomingNebulaInvites by viewModel.incomingNebulaInvites.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.loadNebulaMotes()
            kotlinx.coroutines.delay(20_000L)
        }
    }
    val creatorConnections = activeConnections.filter { it.role.equals("CREATOR", ignoreCase = true) }
    val availableConnections = if (creatorConnections.isNotEmpty()) creatorConnections else activeConnections
    val currentPartner = when {
        openedConnectionId != null -> activeConnections.find { it.connectionId == openedConnectionId }
        creatorConnections.isNotEmpty() -> creatorConnections.find { it.connectionId == selectedSweetConnectionId }
            ?: creatorConnections.firstOrNull()
        else -> activeConnections.find { it.connectionId == selectedSweetConnectionId } ?: activeConnections.firstOrNull()
    }
    // If the opened connection disappears (removed/unpaired), fall back to the hub.
    LaunchedEffect(openedConnectionId, activeConnections) {
        if (openedConnectionId != null && activeConnections.none { it.connectionId == openedConnectionId }) {
            openedConnectionId = null
        }
    }
    // Back returns to the constellation hub when a connection is open.
    BackHandler(enabled = openedConnectionId != null) { openedConnectionId = null }
    
    val theme = remember(currentPartner?.relationshipType) {
        val typeStr = currentPartner?.relationshipType
        typeStr.toConnectionTheme()
    }

    val canSwitchConnections = creatorConnections.size > 1
    var partnerQuotePreview by remember(currentPartner?.connectionId) {
        mutableStateOf<ReceivedQuoteOverlay?>(null)
    }

    var draftName by rememberSaveable(memberIdentity?.displayName) {
        mutableStateOf(memberIdentity?.displayName.orEmpty())
    }
    var draftGender by rememberSaveable(memberIdentity?.gender) {
        mutableStateOf(normalizeGreetingStyle(memberIdentity?.gender))
    }
    var draftEmoji by rememberSaveable(memberIdentity?.emoji) {
        mutableStateOf(memberIdentity?.emoji ?: "🌻")
    }
    var pendingAvatarUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(
        memberIdentity?.displayName,
        memberIdentity?.gender,
        memberIdentity?.avatarUrl,
        memberIdentity?.emoji
    ) {
        draftName = memberIdentity?.displayName.orEmpty()
        draftGender = normalizeGreetingStyle(memberIdentity?.gender)
        draftEmoji = memberIdentity?.emoji ?: "🌻"
        if (!memberIdentity?.avatarUrl.isNullOrBlank()) {
            // Only clear pending if the server URL actually changed or we didn't have one before
            // To be safe, we clear it whenever we get a non-blank one from server after an update
            Log.d("Developer", "🔔 [GIGI] Server avatar updated: ${memberIdentity?.avatarUrl}. Clearing local pending.")
            pendingAvatarUri = null
        }
    }

    LaunchedEffect(availableConnections, selectedSweetConnectionId) {
        val availableIds = availableConnections.map { it.connectionId }.toSet()
        val resolvedSelection = when {
            availableConnections.isEmpty() -> null
            selectedSweetConnectionId in availableIds -> selectedSweetConnectionId
            else -> availableConnections.first().connectionId
        }
        if (resolvedSelection != null && resolvedSelection != selectedSweetConnectionId) {
            viewModel.selectSweetCornerConnection(resolvedSelection)
        }
    }

    LaunchedEffect(quoteOverlay, currentPartner?.connectionId) {
        if (
            quoteOverlay?.connectionId == currentPartner?.connectionId &&
            !quoteOverlay?.quote.isNullOrBlank()
        ) {
            partnerQuotePreview = quoteOverlay
        }
    }

    LaunchedEffect(activeLoveCardDeck?.stack?.stackId) {
        activeLoveCardDeck?.let { deck ->
            viewModel.markLoveCardDeckPresentedIfNeeded(deck)
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingAvatarUri = uri

        val resolvedName = draftName.ifBlank { memberIdentity?.displayName.orEmpty() }.trim()
        val resolvedGender = normalizeGreetingStyle(
            draftGender.ifBlank { memberIdentity?.gender }
        )

        if (resolvedName.isNotBlank()) {
            viewModel.completeProfile(
                displayName = resolvedName,
                gender = resolvedGender,
                avatarUri = uri
            )
        }
    }

    val backgroundBrush = if (darkTheme) {
        Brush.verticalGradient(colors = theme.darkBackgroundColors)
    } else {
        Brush.verticalGradient(colors = theme.backgroundColors)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        DeveloperAmbientDecorations(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 44.dp, bottom = 96.dp)
        )

        if (openedConnectionId == null) {
            val hasNoPartners = activeConnections.isEmpty()
            Box(modifier = Modifier.fillMaxSize()) {
                GalaxyView(
                    identity = memberIdentity,
                    connections = activeConnections,
                    groupMemberEmojis = groupMemberEmojis,
                    nowPlaying = galaxyNowPlaying,
                    myNowPlaying = galaxyMyNowPlaying,
                    quotes = galaxyQuotes,
                    nebulaMotes = if (nebulaSearchQuery.isNotBlank()) nebulaSearchResults else nebulaMotes,
                    incomingInvites = incomingNebulaInvites,
                    searchQuery = nebulaSearchQuery,
                    onSearchQueryChange = { viewModel.setNebulaSearchQuery(it) },
                    pendingGhostInvites = pendingGhostInvites,
                    onInviteMote = { mote ->
                        viewModel.sendNebulaInvite(mote)
                    },
                    onAcceptInvite = { inv ->
                        viewModel.respondToNebulaInvite(inv.inviteId, accept = true)
                    },
                    onDeclineInvite = { inv ->
                        viewModel.respondToNebulaInvite(inv.inviteId, accept = false)
                    },
                    onBlockMote = { id ->
                        viewModel.blockMember(id)
                    },
                    onReportMote = { id, reason, note ->
                        viewModel.reportMember(id, reason, note)
                    },
                    onInvite = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.CREATE) },
                    camera = viewModel.galaxyCamera,
                    onOpenConnection = { id ->
                        viewModel.selectSweetCornerConnection(id)
                        activeConnections.find { it.connectionId == id }?.let { viewModel.selectConnection(it) }
                        openedConnectionId = id
                    },
                    onSunClick = { showProfileHub = true },
                    onOpenMemories = { viewModel.openMemoriesSpace() },
                    onNowPlayingClick = { np ->
                        val searchTrack = if (np.title.isNotBlank()) "${np.title} ${np.artist}".trim() else np.label
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://open.spotify.com/search/${android.net.Uri.encode(searchTrack)}")).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (hasNoPartners) {
                    EmptyGalaxyOnboardingGuide(
                        onAddClick = { viewModel.openConnectionsSheet() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 175.dp)
                    )
                }

                GalaxyAddButton(
                    onClick = { viewModel.openConnectionsSheet() },
                    isPulsing = hasNoPartners,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 22.dp, bottom = 120.dp)
                )
            }
        } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .pointerInput(currentPartner?.connectionId, loveCardDecks.size) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            totalDrag += dragAmount
                            if (dragAmount > 0f) {
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (totalDrag > 120f && currentPartner != null) {
                                showLoveCardGallery = true
                            }
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 76.dp, bottom = 108.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                ProfileHero(
                    identity = memberIdentity,
                    currentPartner = currentPartner,
                    groupMembers = groupMembers,
                    connectionCount = creatorConnections.size,
                    pendingAvatarUri = pendingAvatarUri,
                    serverMode = serverStatus.mode,
                    quotePreview = partnerQuotePreview,
                    theme = theme,
                    onSelfAvatarClick = {
                        emojiPickerTarget = "self"
                        showEmojiPicker = true
                    },
                    onPartnerClick = {
                        if (currentPartner != null) {
                            showQuoteDialog = true
                        }
                    },
                    onEmojiClick = { target ->
                        emojiPickerTarget = target
                        showEmojiPicker = true
                    },
                    onAddMembers = { showAddMembers = true }
                )
                if (showAddMembers && currentPartner != null) {
                    AddMembersDialog(
                        candidates = activeConnections.filter { !isGroupConnection(it) && it.connectionId != currentPartner.connectionId },
                        onDismiss = { showAddMembers = false },
                        onConfirm = { codes ->
                            viewModel.inviteConnectionsToGroup(
                                currentPartner.connectionId,
                                currentPartner.partnerName.ifBlank { "our group" },
                                codes
                            )
                            showAddMembers = false
                        }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                if (currentPartner != null) {
                    ConnectionActionRow(
                          connection = currentPartner,
                          onChat = {
                              // Open the real in-app chat overlay (MainActivity renders the
                              // full ChatScreen wired to the server) — not the dead bubble Activity.
                              viewModel.openChat(currentPartner.connectionId)
                          },
                          onDoodle = { viewModel.setDrawingMode(true) },
                          onSparkle = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.SPARKLE, currentPartner.connectionId) },
                          onBreak = { showBreakPicker = true }
                      )
                    Spacer(modifier = Modifier.height(20.dp))
                }
                LoveCardsSection(
                    currentPartner = currentPartner,
                    decks = loveCardDecks,
                    onOpenGallery = { showLoveCardGallery = true },
                    onOpenComposer = {
                        if (currentPartner != null) {
                            showComposerOverlay = true
                            viewModel.setComposerMode(true)
                        }
                    },

                    onOpenDeck = { deck ->
                        currentPartner?.let { partner ->
                            viewModel.openLoveCardDeck(
                                connectionId = partner.connectionId,
                                stackId = deck.stack.stackId
                            )
                        }
                    },
                    onAnswerDeck = { deck, responses ->
                        currentPartner?.let { partner ->
                            viewModel.answerLoveCardDeck(
                                connectionId = partner.connectionId,
                                stackId = deck.stack.stackId,
                                responses = responses
                            )
                        }
                    },
                    onShowDeck = { deck ->
                        selectedLoveCardDeck = deck
                        viewModel.showLoveCardDeck(deck.stack.stackId)
                    }
                )
                Spacer(modifier = Modifier.height(108.dp))
            }
        }
        } // end detail-mode (openedConnectionId != null)

        // Back to the constellation hub (only while a connection is open)
        if (openedConnectionId != null) {
            BackToHubButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(top = 16.dp, start = 20.dp)
                    .zIndex(5f),
                onClick = { openedConnectionId = null }
            )
        }

        // Expanding-bubble fill transition (hub → detail)
        bubbleFillSpec?.let { spec ->
            BubbleFillOverlay(
                spec = spec,
                onCommit = {
                    viewModel.selectSweetCornerConnection(spec.connectionId)
                    activeConnections.find { it.connectionId == spec.connectionId }
                        ?.let { viewModel.selectConnection(it) }
                    openedConnectionId = spec.connectionId
                },
                onDone = { bubbleFillSpec = null },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(60f)
            )
        }

        // Connections Modal Sheet Overlay (triggered by + FAB or bottom nav tab re-tap)
        androidx.compose.animation.AnimatedVisibility(
            visible = showConnectionsSheet,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
            modifier = Modifier.zIndex(70f)
        ) {
            com.aman.gigi.ui.screensaver.connection.ConnectionListScreen(
                connections = activeConnections,
                canAddConnection = canAddConnection,
                serverStatus = serverStatus,
                onAddConnection = {
                    viewModel.closeConnectionsSheet()
                    viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.CREATE)
                },
                onAddGroup = {
                    viewModel.closeConnectionsSheet()
                    viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.CREATE_GROUP)
                },
                onJoinWithCode = {
                    viewModel.closeConnectionsSheet()
                    viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.JOIN)
                },
                onScribbleToPartner = { conn ->
                    viewModel.closeConnectionsSheet()
                    viewModel.selectSweetCornerConnection(conn.connectionId)
                    activeConnections.find { it.connectionId == conn.connectionId }?.let { viewModel.selectConnection(it) }
                    openedConnectionId = conn.connectionId
                },
                onDisconnect = { conn -> viewModel.disconnect(conn) },
                onDismiss = { viewModel.closeConnectionsSheet() }
            )
        }

        // Top-right corner button: connection settings (disconnect, etc.) when a connection is
        // open; your profile hub on the constellation.
        if (openedConnectionId != null) {
            ProfileCornerButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(top = 16.dp, end = 20.dp)
                    .zIndex(4f),
                icon = Icons.Default.Settings,
                desc = "Connection settings",
                onClick = {
                    showConnectionSettings = true
                }
            )
        }

        val settingsConn = currentPartner
        if (showConnectionSettings && settingsConn != null) {
            SessionSettingsDialog(
                onDismiss = { showConnectionSettings = false },
                onDisconnect = {
                    viewModel.disconnect(settingsConn)
                    showConnectionSettings = false
                    openedConnectionId = null
                },
                connection = settingsConn,
                viewModel = viewModel
            )
        }

        if (showEmojiPicker) {
            val galaxyPrefs = context.getSharedPreferences("galaxy_orbits", android.content.Context.MODE_PRIVATE)
            AvatarEmojiPickerDialog(
                onDismiss = { showEmojiPicker = false },
                onPickEmoji = { chosenEmojiUrl ->
                    showEmojiPicker = false
                    if (emojiPickerTarget == "self") {
                        galaxyPrefs.edit().putString("emoji_self", chosenEmojiUrl).commit()
                        viewModel.updateProfileEmoji(chosenEmojiUrl)
                    } else if (currentPartner != null) {
                        galaxyPrefs.edit().putString("emoji_${currentPartner.connectionId.lowercase()}", chosenEmojiUrl).commit()
                        viewModel.updatePartnerLocalEmoji(currentPartner.connectionId, chosenEmojiUrl)
                    }
                },
                onOpenTwigiStudio = {
                    showEmojiPicker = false
                    showLpcStudio = true
                },
                title = if (emojiPickerTarget == "self") "Pick your animated emoji ✨" else "Pick partner emoji ✨"
            )
        }

        // ── Break cards: pick one to call, and the live invite everyone sees ──
        if (showBreakPicker && currentPartner != null) {
            com.aman.gigi.ui.components.BreakPickerDialog(
                onPick = { kind ->
                    viewModel.callBreak(currentPartner.connectionId, kind.id)
                    showBreakPicker = false
                },
                onDismiss = { showBreakPicker = false }
            )
        }
        activeBreak?.let { invite ->
            com.aman.gigi.ui.components.BreakInviteOverlay(
                invite = invite,
                responses = breakResponses,
                isMine = invite.isMine,
                myResponse = myBreakAnswer,
                onAccept = { viewModel.answerBreak(true) },
                onReject = { viewModel.answerBreak(false) },
                onDismiss = { viewModel.dismissBreak() }
            )
        }

        if (showLpcStudio) {
            // Server-driven Twigi Studio: server renders + stores the avatar, then we
            // flip avatarMode so the saved Twigi immediately replaces the emoji
            // (locally AND for every partner via the profile_update broadcast).
            var twigiSaving by remember { mutableStateOf(false) }
            com.aman.gigi.ui.twigi.TwigiCreatorScreen(
                initialConfigJson = memberIdentity?.twigiConfigJson,
                saving = twigiSaving,
                isSubscribed = com.aman.gigi.utils.AppConfig.userPlan.isPaid,
                onDismiss = { showLpcStudio = false },
                onSave = { cfgJson ->
                    twigiSaving = true
                    viewModel.saveTwigi(cfgJson) { ok ->
                        twigiSaving = false
                        if (ok) {
                            viewModel.setAvatarMode("TWIGI")
                            showLpcStudio = false
                        }
                    }
                }
            )
        }

        // The constellation hub now replaces the old top-left connection switcher.

        if (showProfileHub) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showProfileHub = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                com.aman.gigi.ui.settings.ScreensaverSettingsScreen(
                    onBack = { showProfileHub = false },
                    onLogout = { 
                        viewModel.logout() 
                        showProfileHub = false
                    },
                    onDeleteAccount = { 
                        viewModel.deleteAccount() 
                        showProfileHub = false
                    }
                )
            }
        }

        if (showConnectionSwitcher) {
            ConnectionSwitcherDialog(
                connections = creatorConnections,
                selectedConnectionId = currentPartner?.connectionId,
                onDismiss = { showConnectionSwitcher = false },
                onSelect = { connectionId ->
                    viewModel.selectSweetCornerConnection(connectionId)
                    showConnectionSwitcher = false
                }
            )
        }

        if (showQuoteDialog && currentPartner != null) {
            QuoteComposerDialog(
                onDismiss = { showQuoteDialog = false },
                onSend = { quote ->
                    viewModel.sendQuote(
                        text = quote.text
                    )
                    showQuoteDialog = false
                }
            )
        }

        if (showLoveCardGallery) {
            LoveCardGalleryDialog(
                currentPartner = currentPartner,
                decks = loveCardDecks,
                onDismiss = { showLoveCardGallery = false },
                onOpenDeck = { deck ->
                    currentPartner?.let { partner ->
                        viewModel.openLoveCardDeck(
                            connectionId = partner.connectionId,
                            stackId = deck.stack.stackId
                        )
                    }
                },
                onShowDeck = { deck ->
                    selectedLoveCardDeck = deck
                    viewModel.showLoveCardDeck(deck.stack.stackId)
                    showLoveCardGallery = false
                    viewModel.setComposerMode(true)
                }
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = activeLoveCardDeck != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it },
            modifier = Modifier.zIndex(11f) // Even higher than composer (10f)
        ) {
            activeLoveCardDeck?.let { deck ->
                LoveCardDeckDialog(
                    deck = deck,
                    onDismiss = {
                        viewModel.dismissLoveCardDeck(deck.stack.stackId)
                        if (selectedLoveCardDeck?.stack?.stackId == deck.stack.stackId) {
                            selectedLoveCardDeck = null
                        }
                        viewModel.setComposerMode(false)
                    },
                    onAnswer = { answers ->
                        viewModel.answerLoveCardDeck(
                            connectionId = deck.stack.connectionId,
                            stackId = deck.stack.stackId,
                            responses = answers
                        )
                        viewModel.dismissLoveCardDeck(deck.stack.stackId)
                        if (selectedLoveCardDeck?.stack?.stackId == deck.stack.stackId) {
                            selectedLoveCardDeck = null
                        }
                        viewModel.setComposerMode(false)
                    }
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showComposerOverlay && currentPartner != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it },
            modifier = Modifier.zIndex(10f) // Ensure it's on top of corner buttons
        ) {
            currentPartner?.let { partner ->
                LoveCardComposerOverlay(
                    partnerName = partner.partnerName,
                    onDismiss = { 
                        showComposerOverlay = false
                        viewModel.setComposerMode(false)
                    },
                    onSend = { title, unlockDate, cards ->
                        viewModel.createLoveCardStack(
                            connectionId = partner.connectionId,
                            title = title,
                            cards = cards,
                            unlockDate = unlockDate
                        )
                        showComposerOverlay = false
                        viewModel.setComposerMode(false)
                    }
                )
            }
        }

        // ── Cosmic Memories Space Overlay ──
        val isMemoriesSpaceOpen by viewModel.isMemoriesSpaceOpen.collectAsState()
        val selectedMemoriesConnection by viewModel.selectedMemoriesConnection.collectAsState()
        val sharedSparkles by viewModel.sharedSparkles.collectAsState()
        val memoryCounts by viewModel.memoryCountsByConnection.collectAsState()

        androidx.compose.animation.AnimatedVisibility(
            visible = isMemoriesSpaceOpen,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(300), initialScale = 0.95f),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(200), targetScale = 0.95f),
            modifier = Modifier.fillMaxSize().zIndex(20f)
        ) {
            com.aman.gigi.ui.memories.MemoriesSpaceScreen(
                identity = memberIdentity,
                connections = activeConnections,
                selectedConnection = selectedMemoriesConnection,
                sparkles = sharedSparkles,
                memoryCounts = memoryCounts,
                onSelectConnection = { conn ->
                    if (conn.connectionId.isBlank()) {
                        viewModel.openMemoriesSpace(null)
                    } else {
                        viewModel.selectMemoriesConnection(conn)
                    }
                },
                onBack = {
                    viewModel.closeMemoriesSpace()
                },
                onReplayScribble = { id ->
                    viewModel.replayScribble(id)
                },
                onSendSparkle = { conn ->
                    viewModel.selectConnection(conn)
                    viewModel.closeMemoriesSpace()
                    viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.SPARKLE, conn.connectionId)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SWEET CORNER CONSTELLATION HUB
// You at the center, every connection orbiting around you. Partners and groups
// carry distinct colors. Tapping a bubble opens that connection's full view.
// ─────────────────────────────────────────────────────────────────────────────

private val PartnerBubbleColors = listOf(Color(0xFFEC4899), Color(0xFFF472B6))
private val GroupBubbleColors = listOf(Color(0xFF6366F1), Color(0xFF38BDF8))

private fun isGroupConnection(c: Connection): Boolean =
    c.isGroup || c.relationshipType.equals("GROUP", ignoreCase = true)

/** Describes an in-flight bubble→detail fill animation. */
private data class BubbleFillSpec(
    val connectionId: String,
    val dx: Float,
    val dy: Float,
    val colors: List<Color>
)

/**
 * Expands a colored circle from the tapped bubble until it fills the screen, commits the
 * navigation to the detail view at full coverage, then fades to reveal it.
 */
@Composable
private fun BubbleFillOverlay(
    spec: BubbleFillSpec,
    onCommit: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val bx = wPx / 2f + spec.dx
        val by = hPx / 2f + spec.dy
        val target = max(
            max(hypot(bx, by), hypot(wPx - bx, by)),
            max(hypot(bx, hPx - by), hypot(wPx - bx, hPx - by))
        )

        val progress = remember { Animatable(0f) }
        val alpha = remember { Animatable(1f) }
        LaunchedEffect(spec.connectionId) {
            progress.snapTo(0f); alpha.snapTo(1f)
            progress.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            onCommit()
            kotlinx.coroutines.delay(70)
            alpha.animateTo(0f, tween(220))
            onDone()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = max(target * progress.value, 0.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = spec.colors,
                    center = Offset(bx, by),
                    radius = radius
                ),
                radius = radius,
                center = Offset(bx, by),
                alpha = alpha.value
            )
        }
    }
}

@Composable
fun SweetCornerConstellation(
    identity: MemberIdentity?,
    connections: List<Connection>,
    pendingAvatarUri: Uri?,
    onOpenConnection: (id: String, dx: Float, dy: Float, isGroup: Boolean) -> Unit,
    onAddConnection: () -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val minDim = with(LocalDensity.current) { min(maxWidth.toPx(), maxHeight.toPx()) }
        val r1 = minDim * 0.34f
        val r2 = minDim * 0.50f

        // Lay out connections + a trailing "add" node evenly across up to two rings.
        val nodeCount = connections.size + 1
        val positions = remember(nodeCount, r1, r2) {
            val out = ArrayList<Pair<Float, Float>>(nodeCount)
            val firstN = min(nodeCount, 6)
            val secondN = nodeCount - firstN
            if (firstN > 0) {
                val step = 360f / firstN
                for (i in 0 until firstN) {
                    val a = Math.toRadians((-90f + i * step).toDouble())
                    out.add((r1 * cos(a)).toFloat() to (r1 * sin(a)).toFloat())
                }
            }
            if (secondN > 0) {
                val step = 360f / secondN
                for (i in 0 until secondN) {
                    val a = Math.toRadians((-90f + step / 2f + i * step).toDouble())
                    out.add((r2 * cos(a)).toFloat() to (r2 * sin(a)).toFloat())
                }
            }
            out
        }
        val addPos = positions.lastOrNull() ?: (0f to r1)

        // Decorative orbit rings + connecting threads behind everything.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            listOf(0.18f, 0.34f, 0.50f).forEach { f ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    radius = min(size.width, size.height) * f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.4f)
                )
            }
            connections.forEachIndexed { i, c ->
                val (dx, dy) = positions[i]
                val accent = if (isGroupConnection(c)) GroupBubbleColors.first() else PartnerBubbleColors.first()
                drawLine(
                    color = accent.copy(alpha = 0.22f),
                    start = Offset(cx, cy),
                    end = Offset(cx + dx, cy + dy),
                    strokeWidth = 1.3f
                )
            }
        }

        // ── Center: you ──
        ConstellationCenter(
            identity = identity,
            pendingAvatarUri = pendingAvatarUri,
            connectionCount = connections.size,
            onClick = onEditProfile
        )

        // ── Orbiting connection bubbles ──
        connections.forEachIndexed { index, c ->
            val (dx, dy) = positions[index]
            ConnectionBubble(
                connection = c,
                phase = index,
                onClick = { onOpenConnection(c.connectionId, dx, dy, isGroupConnection(c)) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
            )
        }

        // ── Add-connection bubble ──
        AddConnectionBubble(
            phase = connections.size,
            onClick = onAddConnection,
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(addPos.first.roundToInt(), addPos.second.roundToInt()) }
        )

        // ── Legend / empty hint ──
        if (connections.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No connections yet",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap ＋ to connect with someone",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(PartnerBubbleColors.first(), "Partner")
                LegendDot(GroupBubbleColors.first(), "Group")
            }
        }
    }
}

@Composable
private fun AddConnectionBubble(
    phase: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = rememberInfiniteTransition(label = "addBubble")
    val dy by t.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(2600 + (phase % 4) * 350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "addBob"
    )
    val ring by t.animateFloat(
        initialValue = 0.9f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "addRing"
    )
    Column(
        modifier = modifier
            .offset { IntOffset(0, dy.roundToInt()) }
            .width(92.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(ring)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("＋", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Add",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ConstellationCenter(
    identity: MemberIdentity?,
    pendingAvatarUri: Uri?,
    connectionCount: Int,
    onClick: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "centerPulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "centerScale"
    )
    val avatarModel: Any? = pendingAvatarUri ?: identity?.avatarUrl?.takeIf { it.isNotBlank() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Glow halo
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFEC4899).copy(alpha = 0.30f), Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFF8B5CF6))))
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                if (avatarModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(avatarModel).crossfade(true).build(),
                        contentDescription = "You",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(98.dp).clip(CircleShape)
                    )
                } else {
                    com.aman.gigi.utils.SmartEmojiAvatar(identity?.emoji, fontSize = 44.sp, modifier = Modifier.size(98.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = identity?.displayName?.takeIf { it.isNotBlank() } ?: "You",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (connectionCount == 0) "Tap to edit profile" else "$connectionCount connection${if (connectionCount == 1) "" else "s"}",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ConnectionBubble(
    connection: Connection,
    phase: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGroup = isGroupConnection(connection)
    val colors = if (isGroup) GroupBubbleColors else PartnerBubbleColors
    val isOnline = connection.partnerPresence.equals("ONLINE", ignoreCase = true)

    // Gentle, desynced bob so the constellation feels alive without moving tap targets much.
    val bob = rememberInfiniteTransition(label = "bob$phase")
    val dy by bob.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(
            tween(2600 + (phase % 4) * 350, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "bobY$phase"
    )
    val avatarModel: Any? = connection.partnerAvatarUrl?.takeIf { it.isNotBlank() }

    Column(
        modifier = modifier
            .offset { IntOffset(0, dy.roundToInt()) }
            .width(92.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(colors))
                    .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(avatarModel).crossfade(true).build(),
                        contentDescription = connection.partnerName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(68.dp).clip(CircleShape)
                    )
                } else if (isGroup) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                } else {
                    com.aman.gigi.utils.SmartEmojiAvatar(connection.partnerEmoji, fallbackEmoji = "💛", fontSize = 30.sp, modifier = Modifier.size(48.dp))
                }
            }
            // Online dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFF22C55E) else Color(0xFF94A3B8))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = connection.partnerName.ifBlank { if (isGroup) "Group" else "Partner" },
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConnectionActionRow(
    connection: Connection,
    onChat: () -> Unit,
    onDoodle: () -> Unit,
    onSparkle: () -> Unit,
    onBreak: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionTile(
                emoji = "\uD83D\uDCAC",
                title = "Chat",
                subtitle = "",
                accent = Color(0xFF3B82F6),
                modifier = Modifier.weight(1f),
                onClick = onChat
            )
            ActionTile(
                emoji = "\uD83C\uDFA8",
                title = "Doodle",
                subtitle = "",
                accent = Color(0xFF7C3AED),
                modifier = Modifier.weight(1f),
                onClick = onDoodle
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionTile(
                emoji = "\uD83D\uDCF8",
                title = "Sparkle",
                subtitle = "",
                accent = Color(0xFFEC4899),
                modifier = Modifier.weight(1f),
                onClick = onSparkle
            )
            ActionTile(
                emoji = "\uD83E\uDED6",
                title = "Break",
                subtitle = "",
                accent = Color(0xFFF0A75A),
                modifier = Modifier.weight(1f),
                onClick = onBreak
            )
        }
    }
}

@Composable
private fun ActionTile(
    emoji: String,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "tileScale"
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.5f),
        modifier = modifier
            .height(104.dp)
            .scale(scale)
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.height(7.dp))
            Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Color(0xFF546E7A), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EmptyGalaxyOnboardingGuide(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "guidedArrow")
    val bounceY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "bounceY"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
    ) {
        // Floating animated glass guidance card
        SafeGlassBox(
            shape = RoundedCornerShape(20.dp),
            borderWidth = 1.5.dp,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .border(1.5.dp, Color(0xFFA855F7).copy(alpha = pulseAlpha), RoundedCornerShape(20.dp))
                .clickable { onAddClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✨",
                    fontSize = 22.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column {
                    Text(
                        text = "Bring your person here!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Tap + to send an invite or join",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Animated bouncing arrow pointing to the + button below
        Text(
            text = "👇",
            fontSize = 26.sp,
            modifier = Modifier
                .padding(end = 12.dp)
                .offset(y = bounceY.dp)
        )
    }
}

@Composable
private fun GalaxyAddButton(
    onClick: () -> Unit,
    isPulsing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulseRing")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "ringScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "ringAlpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isPulsing) {
            // Glowing pulsing ring behind the + button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(ringScale)
                    .border(2.dp, Color(0xFFA855F7).copy(alpha = ringAlpha), CircleShape)
            )
        }

        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(48.dp)
                .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                .clip(CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFF472B6), Color(0xFFA855F7), Color(0xFF6366F1))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Add,
                    contentDescription = "Add Connection",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun BackToHubButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.35f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("←", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("All", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ConnectionSwitcherButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.48f),
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFF7B3FF2),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF5A2BC0)
            )
        }
    }
}

@Composable
private fun ConnectionSwitcherDialog(
    connections: List<Connection>,
    selectedConnectionId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4EEFF).copy(alpha = 0.42f))
                .padding(horizontal = 22.dp, vertical = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 340.dp),
                color = Color(0xFFF8F4FF),
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Choose a partner",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF25104F)
                        )
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }

                    connections.forEach { connection ->
                        val isSelected = connection.connectionId == selectedConnectionId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(connection.connectionId) },
                            color = if (isSelected) {
                                Color(0xFFEFE5FF)
                            } else {
                                Color.White.copy(alpha = 0.9f)
                            },
                            shape = RoundedCornerShape(22.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF8C5CFF) else Color.White.copy(alpha = 0.8f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HeroAvatar(
                                    model = connection.partnerAvatarUrl,
                                    fallback = connection.partnerName.take(1).uppercase(),
                                    label = "",
                                    avatarSize = 48.dp,
                                    onClick = { onSelect(connection.connectionId) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = connection.partnerName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2D1462)
                                    )
                                    Text(
                                        text = if (isSelected) "Shown on your Developer page" else "Tap to show on your Developer page",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF746D89)
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

@Composable
private fun ProfileHero(
    identity: MemberIdentity?,
    currentPartner: com.aman.gigi.model.Connection?,
    groupMembers: List<com.aman.gigi.model.ConnectionMember> = emptyList(),
    connectionCount: Int,
    pendingAvatarUri: Uri?,
    serverMode: ServerMode,
    quotePreview: ReceivedQuoteOverlay?,
    theme: com.aman.gigi.model.ConnectionTheme,
    onSelfAvatarClick: () -> Unit,
    onPartnerClick: () -> Unit,
    onEmojiClick: (String) -> Unit,
    onAddMembers: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "profile-hearts")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart-scale"
    )
    val heartAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart-alpha"
    )
    val titleFloat by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "title-float"
    )
    val selfFloat by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "self-float"
    )
    val partnerFloat by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "partner-float"
    )

    val isDark = false
    val isGroupView = currentPartner != null &&
        (currentPartner.isGroup || currentPartner.relationshipType.equals("GROUP", ignoreCase = true)) &&
        groupMembers.isNotEmpty()

    GlassCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeroGreetingBanner(
                title = "Sweet little corner",
                theme = theme,
                modifier = Modifier.offset(y = titleFloat.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(356.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isDark) listOf(
                                    Color(0xFF2A1040).copy(alpha = 0.55f),
                                    theme.primaryColor.copy(alpha = 0.08f)
                                ) else listOf(
                                    Color.White.copy(alpha = 0.45f),
                                    theme.softColor.copy(alpha = 0.24f)
                                )
                            )
                        )
                        .border(1.dp, if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.48f), RoundedCornerShape(32.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .size(156.dp)
                            .align(Alignment.TopStart)
                            .offset(x = 22.dp, y = 26.dp)
                            .background(theme.softColor.copy(alpha = 0.5f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(168.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-28).dp, y = 74.dp)
                            .background(theme.accentColor.copy(alpha = 0.15f), CircleShape)
                    )

                    TinyAccent(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-34).dp, y = 18.dp),
                        tint = theme.accentColor.copy(alpha = 0.6f)
                    )
                    TinyAccent(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = 8.dp, y = 28.dp),
                        tint = theme.primaryColor.copy(alpha = 0.4f)
                    )
                    TinyAccent(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-64).dp, y = (-8).dp),
                        tint = theme.accentColor.copy(alpha = 0.7f)
                    )

                    // (The solid banner that used to sit here duplicated the quote —
                    // the soft QuoteSticker cloud below is the only one we show now.)

                    if (isGroupView) {
                        GroupMembersGrid(
                            members = groupMembers,
                            theme = theme,
                            modifier = Modifier.matchParentSize().padding(14.dp),
                            onAddClick = onAddMembers
                        )
                    } else {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val prefs = remember(context) { context.getSharedPreferences("galaxy_orbits", android.content.Context.MODE_PRIVATE) }
                    val selfEmojiUrl = if (identity?.avatarMode == "TWIGI" && !identity.twigiRenderUrl.isNullOrBlank()) {
                        identity.twigiRenderUrl
                    } else {
                        identity?.profileEmojiUrl?.takeIf { it.isNotBlank() }
                            ?: prefs.getString("emoji_self", null)?.takeIf { it.isNotBlank() }
                            ?: identity?.avatarUrl?.takeIf { it.isNotBlank() }
                            ?: com.aman.gigi.ui.components.TELEGRAM_EMOJIS.first()
                    }

                    val partnerEmojiUrl = if (currentPartner?.partnerAvatarMode == "TWIGI" && !currentPartner?.partnerTwigiUrl.isNullOrBlank()) {
                        currentPartner.partnerTwigiUrl
                    } else {
                        currentPartner?.partnerEmojiUrl?.takeIf { it.isNotBlank() }
                            ?: currentPartner?.partnerAvatarUrl?.takeIf { it.isNotBlank() }
                            ?: currentPartner?.partnerEmoji?.takeIf { it.isNotBlank() }
                    }

                    LoveCardAvatar(
                        model = selfEmojiUrl,
                        fallback = identity?.displayName?.take(1)?.uppercase() ?: "Y",
                        label = "You",
                        cardWidth = 138.dp,
                        cardHeight = 192.dp,
                        rotationDegrees = -7f,
                        onClick = onSelfAvatarClick,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 40.dp, y = (78 + selfFloat).dp)
                            .zIndex(2f)
                    )

                    LoveCardAvatar(
                        model = partnerEmojiUrl,
                        fallback = currentPartner?.partnerName?.take(1)?.uppercase() ?: "?",
                        label = currentPartner?.partnerName ?: "Partner",
                        cardWidth = 164.dp,
                        cardHeight = 224.dp,
                        rotationDegrees = 7f,
                        onClick = onPartnerClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-24).dp, y = (54 + partnerFloat).dp)
                            .zIndex(3f)
                    )
                    } // end else (single-partner cards vs group grid)
                }

                QuoteSticker(
                    quoteOverlay = quotePreview,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = 16.dp)
                        .zIndex(7f)
                )
            }
        }
    }
}

@Composable
private fun GroupMembersGrid(
    members: List<com.aman.gigi.model.ConnectionMember>,
    theme: com.aman.gigi.model.ConnectionTheme,
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit = {}
) {
    // Animated decoder so member emojis/Twigis actually play (GIF/APNG/WebP)
    val gmCtx = androidx.compose.ui.platform.LocalContext.current
    val gmLoader = remember {
        coil.ImageLoader.Builder(gmCtx).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }.build()
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = theme.accentColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${members.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                Text(
                    text = " members",
                    color = theme.primaryColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(theme.primaryColor.copy(alpha = 0.14f))
                        .clickable { onAddClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = theme.primaryColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            items(members, key = { it.memberDeviceId }) { m ->
                MemberMiniCard(member = m, theme = theme)
            }
        }
    }
}

// Add-members picker: any member can pull their own 1-1 connections into the group.
@Composable
private fun AddMembersDialog(
    candidates: List<Connection>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val filtered = candidates.filter { it.partnerName.contains(query, ignoreCase = true) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(modifier = Modifier.padding(18.dp).heightIn(max = 520.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add members 👯", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF3B2A6B), modifier = Modifier.weight(1f))
                    Text("✕", color = Color(0xFF9A8FC0), fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDismiss() })
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search your connections…", fontSize = 13.sp) },
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (candidates.isEmpty()) {
                    Text("No other connections to add yet.", color = Color(0xFF9A8FC0),
                        fontSize = 13.sp, modifier = Modifier.padding(vertical = 20.dp))
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(filtered, key = { it.connectionId }) { c ->
                            val checked = selected.contains(c.connectionId)
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (checked) selected.remove(c.connectionId) else selected.add(c.connectionId)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(38.dp).clip(CircleShape)
                                        .background(Color(0xFFF3EEFF)),
                                    contentAlignment = Alignment.Center
                                ) { Text(c.partnerName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6)) }
                                Spacer(Modifier.width(12.dp))
                                Text(c.partnerName.ifBlank { "Connection" }, fontSize = 15.sp,
                                    color = Color(0xFF3B2A6B), modifier = Modifier.weight(1f))
                                androidx.compose.material3.Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (checked) selected.remove(c.connectionId) else selected.add(c.connectionId)
                                    },
                                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Color(0xFF8B5CF6))
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onConfirm(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (selected.isEmpty()) "Pick connections" else "Add ${selected.size} 💜",
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MemberMiniCard(
    member: com.aman.gigi.model.ConnectionMember,
    theme: com.aman.gigi.model.ConnectionTheme
) {
    // Animated decoder so member emojis/Twigis actually play in the group card
    val gmCtx = androidx.compose.ui.platform.LocalContext.current
    val gmLoader = remember {
        coil.ImageLoader.Builder(gmCtx).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }.build()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(76.dp)) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.BottomStart)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, theme.primaryColor, CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                val url = member.emojiUrl ?: member.memberAvatarUrl
                if (!url.isNullOrBlank()) {
                    val parsedUrl = com.aman.gigi.utils.ImageUtils.parseEmojiModel(url)
                    AsyncImage(
                        model = parsedUrl,
                        imageLoader = gmLoader,
                        contentDescription = member.memberName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = member.memberName.take(1).uppercase(),
                        color = theme.primaryColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    )
                }
            }
            
            if (member.role.equals(com.aman.gigi.model.ConnectionRole.CREATOR.name, ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = 2.dp)
                        .size(22.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color(0xFFFDE047), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👑", fontSize = 12.sp)
                }
            }
        }
        Text(
            text = member.memberName,
            color = theme.primaryColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(84.dp)
        )
    }
}

@Composable
private fun LoveCardAvatar(
    model: Any?,
    fallback: String,
    label: String,
    cardWidth: Dp,
    cardHeight: Dp,
    rotationDegrees: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val animatedImageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }

    val normalizedModel = when (model) {
        is String -> model.trim().takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        else -> model
    }
    val isDark = false
    var imageState by remember(normalizedModel) {
        mutableStateOf<AsyncImagePainter.State?>(
            if (normalizedModel != null) AsyncImagePainter.State.Empty else null
        )
    }
    val hasLoadedImage = imageState is AsyncImagePainter.State.Success

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .rotate(rotationDegrees)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(34.dp),
                    ambientColor = if (isDark) Color(0x22A060D0) else Color(0x44F4B7D8),
                    spotColor = if (isDark) Color(0x22906FD0) else Color(0x33CAA2FF)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .border(1.5.dp, Color.White, RoundedCornerShape(28.dp))
                .clickable(onClick = onClick)
                .size(width = cardWidth, height = cardHeight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )

            if (normalizedModel != null) {
                val modelStr = normalizedModel.toString()
                val is3D = modelStr.endsWith(".glb", ignoreCase = true) ||
                           modelStr.endsWith(".gltf", ignoreCase = true) ||
                           modelStr.endsWith(".vrm", ignoreCase = true) ||
                           modelStr.startsWith("models/")

                if (is3D) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF2E1065), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "✨ 3D",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Surface(
                                color = Color(0xFF7C3AED),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "3D Twigi",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val parsedModel = com.aman.gigi.utils.ImageUtils.parseEmojiModel(normalizedModel)
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context).data(parsedModel).crossfade(true).build(),
                            imageLoader = animatedImageLoader,
                            contentDescription = label,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                            onState = { state -> 
                                imageState = state
                                if (state is AsyncImagePainter.State.Error) {
                                    Log.e("LoveCardAvatar", "Failed to load image for $label: ${state.result.throwable.message}. URL: $normalizedModel")
                                }
                            }
                        )
                        
                        if (imageState is AsyncImagePainter.State.Loading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Center),
                                color = Color(0xFF7C3AED),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFD0BBFF) else Color(0xFF3D2E7C)
            )
        }
    }
}

@Composable
private fun HeroImageBadge(
    modifier: Modifier = Modifier,
    percentageLabel: String = "100%",
    title: String = "love"
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFFF8DBE),
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.22f),
                shape = CircleShape
            ) {
                Text(
                    text = percentageLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun HeroGreetingBanner(
    title: String,
    theme: com.aman.gigi.model.ConnectionTheme,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(top = 2.dp),
        color = theme.softColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = theme.primaryColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TinyAccent(
    modifier: Modifier = Modifier,
    tint: Color
) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.8f))
    )
}

@Composable
private fun DeveloperAmbientDecorations(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        FloatingHeartDecoration(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 28.dp, y = 176.dp),
            size = 12.dp,
            tint = Color(0xFFCAA2FF),
            travel = 8f,
            durationMs = 2600
        )
        FloatingHeartDecoration(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-40).dp, y = 142.dp),
            size = 10.dp,
            tint = Color(0xFFF2A5C8),
            travel = 10f,
            durationMs = 3000
        )
        FloatingFlowerDecoration(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-18).dp, y = 224.dp),
            size = 18.dp,
            petalColor = Color(0xFFF4B5D6),
            centerColor = Color(0xFFFFE39B),
            travel = 12f,
            durationMs = 3200
        )
        FloatingFlowerDecoration(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 14.dp, y = (-48).dp),
            size = 14.dp,
            petalColor = Color(0xFFD8C3FF),
            centerColor = Color(0xFFFFE7A8),
            travel = 7f,
            durationMs = 2800
        )
        FloatingHeartDecoration(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-54).dp, y = (-160).dp),
            size = 14.dp,
            tint = Color(0xFFFFA4B8),
            travel = 9f,
            durationMs = 2900
        )
        FloatingFlowerDecoration(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 40.dp, y = (-206).dp),
            size = 16.dp,
            petalColor = Color(0xFFF8BBD9),
            centerColor = Color(0xFFFFE08A),
            travel = 11f,
            durationMs = 3400
        )
    }
}

@Composable
private fun FloatingHeartDecoration(
    modifier: Modifier = Modifier,
    size: Dp,
    tint: Color,
    travel: Float,
    durationMs: Int
) {
    val transition = rememberInfiniteTransition(label = "floating-heart")
    val bob by transition.animateFloat(
        initialValue = -travel,
        targetValue = travel,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart-bob"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs + 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart-alpha"
    )
    val scale by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs + 250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart-scale"
    )

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = tint.copy(alpha = alpha),
        modifier = modifier
            .offset(y = bob.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(size)
    )
}

@Composable
private fun FloatingFlowerDecoration(
    modifier: Modifier = Modifier,
    size: Dp,
    petalColor: Color,
    centerColor: Color,
    travel: Float,
    durationMs: Int
) {
    val transition = rememberInfiniteTransition(label = "floating-flower")
    val bob by transition.animateFloat(
        initialValue = travel,
        targetValue = -travel,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flower-bob"
    )
    val rotate by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs + 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flower-rotate"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs + 250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flower-alpha"
    )

    Box(
        modifier = modifier
            .offset(y = bob.dp)
            .size(size)
            .graphicsLayer {
                rotationZ = rotate
                this.alpha = alpha
            }
    ) {
        val petalSize = size * 0.48f
        val centerSize = size * 0.24f

        Box(
            modifier = Modifier
                .size(petalSize)
                .align(Alignment.TopCenter)
                .background(petalColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(petalSize)
                .align(Alignment.BottomCenter)
                .background(petalColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(petalSize)
                .align(Alignment.CenterStart)
                .background(petalColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(petalSize)
                .align(Alignment.CenterEnd)
                .background(petalColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(centerSize)
                .align(Alignment.Center)
                .background(centerColor, CircleShape)
        )
    }
}

@Composable
private fun ProfileCornerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Person,
    desc: String = "Profile settings"
) {
    Surface(
        modifier = modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.34f),
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = 0.86f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = Color(0xFF6C39FF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HeroAvatar(
    model: Any?,
    fallback: String,
    label: String,
    avatarSize: Dp = 92.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.35f))
                .border(2.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = fallback,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6200EE)
                )
            }
        }
        if (label.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A237E),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PartnerHeroAvatar(
    model: Any?,
    fallback: String,
    label: String,
    avatarSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(avatarSize + 44.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
                    .border(2.dp, Color.White.copy(alpha = 0.78f), CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = fallback,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF6200EE)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A237E),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Tap to send a quote",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF7E57C2)
        )
    }
}

@Composable
private fun QuoteSticker(
    quoteOverlay: ReceivedQuoteOverlay?,
    modifier: Modifier = Modifier
) {
    val cleanQuote = quoteOverlay?.quote?.takeIf { it.isNotBlank() && it != "null" }
    val bubbleMotion = rememberInfiniteTransition(label = "quote-bubble")
    val orbitX by bubbleMotion.animateFloat(
        initialValue = -2f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubble-orbit-x"
    )
    val orbitY by bubbleMotion.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubble-orbit-y"
    )
    val colorPhase by bubbleMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubble-color-phase"
    )

    val accentA = Color(0xFFFF8BC3)
    val accentB = Color(0xFF82D7FF)
    val accentC = Color(0xFFB998FF)
    val bubbleFill = Color.White.copy(alpha = 0.97f)
    val bubbleEdge = Color.White.copy(alpha = 0.96f)
    val textColor = lerp(Color(0xFFB24E88), Color(0xFF6B5BFF), colorPhase)

    AnimatedVisibility(
        visible = !cleanQuote.isNullOrBlank(),
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(240)) + scaleIn(
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            initialScale = 0.88f
        ),
        exit = fadeOut(animationSpec = tween(180)) + scaleOut(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            targetScale = 0.94f
        )
    ) {
        Box(
            modifier = Modifier
                .offset(x = orbitX.dp, y = orbitY.dp)
                .widthIn(max = 214.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.padding(bottom = 26.dp)
            ) {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 18.dp,
                            shape = RoundedCornerShape(30.dp),
                            ambientColor = accentA.copy(alpha = 0.18f),
                            spotColor = accentB.copy(alpha = 0.14f)
                        )
                        .clip(RoundedCornerShape(34.dp))
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    bubbleFill,
                                    Color(0xFFFDEEFF).copy(alpha = 0.96f),
                                    Color(0xFFF4F7FF).copy(alpha = 0.94f)
                                ),
                                center = Offset(160f, 50f),
                                radius = 260f
                            )
                        )
                        .border(1.dp, bubbleEdge, RoundedCornerShape(34.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .widthIn(max = 188.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = 0.28f }
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            accentA.copy(alpha = 0.22f),
                                            accentC.copy(alpha = 0.14f),
                                            accentB.copy(alpha = 0.22f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                )
                        )
                        Text(
                            text = cleanQuote
                                ?: "A sweet little thought.",
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                shadow = Shadow(
                                    color = lerp(accentA, accentB, colorPhase).copy(alpha = 0.42f),
                                    offset = Offset.Zero,
                                    blurRadius = 12f
                                )
                            ),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .offset(x = 18.dp, y = (-9).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .shadow(
                                elevation = 7.dp,
                                shape = CircleShape,
                                ambientColor = accentA.copy(alpha = 0.16f),
                                spotColor = accentB.copy(alpha = 0.10f)
                            )
                            .background(Color.White.copy(alpha = 0.96f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .shadow(
                                elevation = 9.dp,
                                shape = CircleShape,
                                ambientColor = accentB.copy(alpha = 0.18f),
                                spotColor = accentC.copy(alpha = 0.12f)
                            )
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        bubbleFill,
                                        Color(0xFFF7F3FF).copy(alpha = 0.95f)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.24f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6200EE)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF546E7A)
            )
        }
    }
}

@Composable
private fun ProfileHubOverlay(
    identity: MemberIdentity?,
    currentPartner: Connection?,
    connectionCount: Int,
    serverMode: ServerMode,
    displayName: String,
    selectedGender: String,
    selectedEmoji: String,
    avatarModel: Any?,
    authError: String?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onGenderSelected: (String) -> Unit,
    onEmojiSelected: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onSaveProfile: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onRestorePurchases: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0B1A)) // Dark background to match screenshots
            .safeDrawingPadding()
            .clickable(enabled = false) { }, // Consume clicks
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFF9F1FF), // Very soft lavender
                                    Color(0xFFF4EEFF)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Top bar inside header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEditing) "Edit your little corner" else "Sweet little settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6C39FF)
                            )
                            IconButton(onClick = { if (isEditing) isEditing = false else onDismiss() }) {
                                Text(
                                    text = if (isEditing) "Back" else "Done",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6C39FF)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(onClick = onPickAvatar),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarModel != null) {
                                AsyncImage(
                                    model = avatarModel,
                                    contentDescription = "Profile image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                com.aman.gigi.utils.SmartEmojiAvatar(
                                    emojiOrUrl = identity?.emoji,
                                    fallbackEmoji = (identity?.displayName?.take(1)?.uppercase() ?: "Y"),
                                    fontSize = 36.sp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = identity?.displayName ?: "Your profile",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF24104F)
                        )
                        Text(
                            text = identity?.googleEmail ?: identity?.phoneNumber ?: "Not signed in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF5E5C73)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (isEditing) {
                            Button(
                                onClick = {
                                    onSaveProfile()
                                    isEditing = false
                                },
                                enabled = displayName.isNotBlank() && !isBusy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(999.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE2E9FE),
                                    contentColor = Color(0xFF4263EB)
                                )
                            ) {
                                Text(if (isBusy) "Saving..." else "Save changes", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { isEditing = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(999.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF3EDFF),
                                    contentColor = Color(0xFF6C39FF)
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Edit profile", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (isEditing) {
                    InlineProfileEditor(
                        displayName = displayName,
                        selectedGender = selectedGender,
                        selectedEmoji = selectedEmoji,
                        avatarModel = avatarModel,
                        authError = authError,
                        onNameChange = onNameChange,
                        onGenderSelected = onGenderSelected,
                        onEmojiSelected = onEmojiSelected,
                        onPickAvatar = onPickAvatar
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Your identity section
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text(
                                    text = "Your identity",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF24104F)
                                )
                                Text(
                                    text = "Pick what your loved ones see.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF7E7C90)
                                )
                            }
                            val isTwigiActive = identity?.avatarMode == "TWIGI" || selectedEmoji.startsWith("data:")
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Emoji card
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (!isTwigiActive) Color(0xFFF3EDFF) else Color(0xFFF7F6FB))
                                        .border(
                                            width = if (!isTwigiActive) 2.dp else 1.dp,
                                            color = if (!isTwigiActive) Color(0xFF6C39FF) else Color(0xFFE2E2EC),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clickable {
                                            onEmojiSelected(if (selectedEmoji.startsWith("data:")) "🌻" else selectedEmoji)
                                        }
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.TopEnd
                                    ) {
                                        com.aman.gigi.utils.SmartEmojiAvatar(
                                            emojiOrUrl = if (selectedEmoji.startsWith("data:")) "🌻" else selectedEmoji,
                                            fallbackEmoji = "🌻",
                                            fontSize = 32.sp,
                                            modifier = Modifier.size(52.dp).align(Alignment.Center)
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.padding(4.dp).size(14.dp),
                                                tint = Color(0xFF6C39FF)
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Text("Emoji Avatar", fontWeight = FontWeight.Bold, color = Color(0xFF24104F), fontSize = 14.sp)
                                        Text("visible to partners 💜", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7E7C90))
                                    }
                                }
                                
                                // Twigi card
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isTwigiActive) Color(0xFFF3EDFF) else Color(0xFFF7F6FB))
                                        .border(
                                            width = if (isTwigiActive) 2.dp else 1.dp,
                                            color = if (isTwigiActive) Color(0xFF6C39FF) else Color(0xFFE2E2EC),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clickable {
                                            onEmojiSelected(selectedEmoji)
                                        }
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.TopEnd
                                    ) {
                                        com.aman.gigi.utils.SmartEmojiAvatar(
                                            emojiOrUrl = if (selectedEmoji.startsWith("data:")) selectedEmoji else identity?.twigiRenderUrl,
                                            fallbackEmoji = "🐉",
                                            fontSize = 32.sp,
                                            modifier = Modifier.size(52.dp).align(Alignment.Center)
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.padding(4.dp).size(14.dp),
                                                tint = Color(0xFF6C39FF)
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Text("Twigi Avatar", fontWeight = FontWeight.Bold, color = Color(0xFF24104F), fontSize = 14.sp)
                                        Text(if (isTwigiActive) "Active avatar ✨" else "Tap to edit 🎨", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7E7C90))
                                    }
                                }
                            }
                        }

                        // Account & sync section
                        var syncExpanded by remember { mutableStateOf(false) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF7F6FB))
                                .clickable { syncExpanded = !syncExpanded }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF6C39FF), modifier = Modifier.size(20.dp))
                                    Text("Account & sync", fontWeight = FontWeight.Bold, color = Color(0xFF24104F))
                                }
                                Icon(
                                    imageVector = if (syncExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF7E7C90)
                                )
                            }
                            if (syncExpanded) {
                                Text(
                                    text = if (serverMode == ServerMode.ONLINE) "Online •  links" else "Offline •  links",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6C39FF),
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    HeroStat(label = "Links", value = connectionCount.toString(), modifier = Modifier.weight(1f))
                                    HeroStat(label = "Server", value = serverMode.name.lowercase().replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(1f))
                                    HeroStat(label = "Profile", value = if (identity?.profileComplete == true) "Ready" else "Needs love", modifier = Modifier.weight(1f))
                                }
                                Text(
                                    text = identity?.googleEmail ?: identity?.phoneNumber ?: "Not signed in",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF24104F)
                                )
                                Text(
                                    text = when (serverMode) {
                                        ServerMode.ONLINE -> "Server sync is online and ready."
                                        ServerMode.MAINTENANCE -> "Server maintenance is active right now."
                                        ServerMode.OFFLINE -> "Server is offline right now. Personal alarms still work."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF7E7C90)
                                )
                            }
                        }

                        // Action Buttons
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = onRestorePurchases,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Restore Purchases", color = Color(0xFF5E5C73))
                            }
                            TextButton(
                                onClick = onPrivacyPolicy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Privacy Policy", color = Color(0xFF5E5C73))
                            }
                            TextButton(
                                onClick = onLogout,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Logout from Gigi", color = Color(0xFF5E5C73))
                            }
                            TextButton(
                                onClick = onDeleteAccount,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = Color(0xFFD32F2F)
                                )
                            ) {
                                Text("Delete Account", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineProfileEditor(
    displayName: String,
    selectedGender: String,
    selectedEmoji: String,
    avatarModel: Any?,
    authError: String?,
    onNameChange: (String) -> Unit,
    onGenderSelected: (String) -> Unit,
    onEmojiSelected: (String) -> Unit,
    onPickAvatar: () -> Unit
) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Your emoji section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Your emoji",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF24104F)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF7F6FB)),
                    contentAlignment = Alignment.Center
                ) {
                    com.aman.gigi.utils.SmartEmojiAvatar(emojiOrUrl = selectedEmoji, fallbackEmoji = "🌻", fontSize = 28.sp, modifier = Modifier.size(48.dp))
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E2EC)),
                    modifier = Modifier.clickable { /* Handle emoji selection - might need integration with emoji picker */ }
                ) {
                    Text(
                        text = "Change emoji ✨",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF24104F),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Display name
        OutlinedTextField(
            value = displayName,
            onValueChange = onNameChange,
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFE2E2EC),
                focusedBorderColor = Color(0xFF6C39FF)
            )
        )

        // Greeting style
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Greeting style",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF7E7C90)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("him", "her").forEach { option ->
                    val isSelected = selectedGender == option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFFF3EDFF) else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF6C39FF) else Color(0xFFE2E2EC)),
                        modifier = Modifier.weight(1f).clickable { onGenderSelected(option) }
                    ) {
                        Text(
                            text = option.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF6C39FF) else Color(0xFF24104F)
                        )
                    }
                }
            }
        }

        authError?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD32F2F)
            )
        }
    }
}
@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    val isDark = false
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.28f),
                RoundedCornerShape(28.dp)
            )
    ) {
        Cloudy(radius = 22) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (isDark) Color(0xFF1A0E28).copy(alpha = 0.72f)
                        else Color.White.copy(alpha = 0.12f)
                    )
            )
        }
        Surface(
            color = if (isDark) Color(0xFF1E1330).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.18f),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

private fun normalizeGreetingStyle(value: String?): String {
    return if (value.equals("her", ignoreCase = true)) "her" else "him"
}

@Composable
private fun StabilityDashboard() {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val isIgnoringBatteryOptimizations = remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        )
    }
    val canUseFullScreenIntents = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.canUseFullScreenIntent()
            } else {
                true
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.25f))
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
        ) {
            Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF6200EE))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Connection stability tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A237E)
                )
                Text(
                    text = "Collapsed when life is good, ready when sync gets picky.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF546E7A)
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = Color(0xFF1A237E).copy(alpha = 0.6f)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                StatusItem(
                    label = "Display over other apps",
                    status = if (canDrawOverlays.value) "Granted" else "Missing",
                    isWarning = !canDrawOverlays.value,
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
                StatusItem(
                    label = "Full-screen intent",
                    status = if (canUseFullScreenIntents.value) "Allowed" else "Blocked",
                    isWarning = !canUseFullScreenIntents.value,
                    onAction = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        }
                        context.startActivity(intent)
                    }
                )
                StatusItem(
                    label = "Battery optimization",
                    status = if (isIgnoringBatteryOptimizations.value) "Unrestricted" else "Optimized",
                    isWarning = !isIgnoringBatteryOptimizations.value,
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF7E57C2),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "On some phones you may still need to enable Auto-start manually in App Info.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF546E7A)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, status: String, isWarning: Boolean, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.7f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isWarning) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isWarning) Color(0xFFD32F2F) else Color(0xFF388E3C),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isWarning) Color(0xFFD32F2F) else Color(0xFF388E3C),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        IconButton(onClick = onAction) {
            Icon(
                Icons.Default.Launch,
                contentDescription = "Open settings",
                tint = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

val CURATED_EMOJIS = listOf(
    "🌻", "🌸", "🌷", "🌹", "🌺", "🌼", "💐", "🍂", "🍃", "🌿",
    "🎀", "🧸", "✨", "💫", "🌟", "🌙", "☁️", "🌈", "🍭", "🍩",
    "🍓", "🍑", "🍒", "🦋", "🦄", "🐇", "🐈", "🐶", "🐼", "🐨",
    "💌", "💖", "💝", "🐝", "🐥", "🐣", "🍓", "🍉", "🧁", "🍦"
)

@Composable
fun EmojiPickerDialog(
    currentEmoji: String,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .heightIn(max = 450.dp)
                .statusBarsPadding()
                .displayCutoutPadding()
                .clip(RoundedCornerShape(32.dp))
                .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(32.dp)),
            color = Color.White.copy(alpha = 0.92f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Pick a cute emoji",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D1462)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "This will be shown next to the name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(CURATED_EMOJIS) { emoji ->
                        val isSelected = emoji == currentEmoji
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFFEEE5FF) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color(0xFF8C5CFF) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { 
                                    onEmojiSelected(emoji)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color(0xFF8C5CFF))
                }
            }
        }
    }
}
