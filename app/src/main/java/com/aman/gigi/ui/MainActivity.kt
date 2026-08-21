package com.aman.gigi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LocationOn
import com.aman.gigi.ui.live.LiveMapScreen
import com.aman.gigi.ui.live.LiveScreen
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.aman.gigi.ui.components.GlassBottomNavigation
import com.aman.gigi.ui.components.NavigationItem
import com.aman.gigi.ui.components.RomanceAmbientDecor
import com.aman.gigi.service.ScreensaverManager
import com.aman.gigi.ui.theme.RemindMeTheme
import com.aman.gigi.viewmodel.ScreensaverViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var screensaverManager: ScreensaverManager

    @Inject
    lateinit var spotifyAuth: com.aman.gigi.data.spotify.SpotifyAuth

    private val currentIntentState = mutableStateOf<android.content.Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        currentIntentState.value = intent
        consumeSpotifyRedirect(intent)
        // Initialize screensaver manager
        screensaverManager.initialize()
        // Restore + re-verify any active Play subscription (handles reinstalls and
        // purchases that completed while the app was closed)
        com.aman.gigi.utils.BillingManager(applicationContext).startConnection()
        enableEdgeToEdge()
        
        // Configure immersive mode - hide system navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            RemindMeTheme {
                // The splash sits over the real UI rather than delaying it, so the app is
                // already warm by the time the animation clears. It can be tapped away.
                var splashDone by rememberSaveable { mutableStateOf(false) }

                // No up-front permission wall. Features ask for what they need, when they
                // need it, via PermissionFlowHost + an animated rationale popup.
                PermissionFlowHost {
                    Home(intent = currentIntentState.value)
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = !splashDone,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(420)
                    )
                ) {
                    com.aman.gigi.ui.components.GigiSplash(onFinished = { splashDone = true })
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-hide system bars when window regains focus
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
        consumeSpotifyRedirect(intent)
    }

    /**
     * Completes the Spotify PKCE handshake when the browser bounces the user back to
     * gigi://spotify-callback.
     *
     * The callback almost always arrives through onNewIntent — the activity is still
     * alive behind the Custom Tab — but onCreate is covered too in case the process was
     * killed while the consent screen was open. The intent's data is cleared afterwards
     * so a configuration change can't replay a one-time authorization code.
     */
    private fun consumeSpotifyRedirect(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "gigi" || uri.host != "spotify-callback") return
        intent.data = null
        lifecycleScope.launch {
            val ok = spotifyAuth.handleRedirect(uri)
            android.util.Log.i("MainActivity", "Spotify connect ${if (ok) "succeeded" else "failed"}")
        }
    }
}

/**
 * Bottom-nav tab order. Named because the pill's order is a product decision that
 * changes; the indices were previously bare literals scattered across the file.
 * Changing the order means editing `navItems` and these four values together.
 */
private const val REMINDERS_TAB_INDEX = 0
private const val LIVE_TAB_INDEX = 1
private const val SWEET_CORNER_TAB_INDEX = 2
private const val NEST_TAB_INDEX = 3
private const val MUSIC_TAB_INDEX = 4

@Composable
fun Home(
    intent: android.content.Intent?,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    screensaverViewModel: ScreensaverViewModel = hiltViewModel(),
    musicViewModel: com.aman.gigi.viewmodel.MusicViewModel = hiltViewModel()
) {
    var selectedNavIndex by rememberSaveable { mutableIntStateOf(SWEET_CORNER_TAB_INDEX) }
    var isMusicBottomNavVisible by rememberSaveable { mutableStateOf(true) }
    val isAlbumBrowserOpen by musicViewModel.isAlbumBrowserOpen.collectAsStateWithLifecycle()
    val isMusicSettingsOpen by musicViewModel.isMusicSettingsOpen.collectAsStateWithLifecycle()
    val darkTheme = false
    val isDrawingMode by screensaverViewModel.isDrawingMode.collectAsState()
    val memberIdentity by screensaverViewModel.memberIdentity.collectAsState()
    val serverStatus by screensaverViewModel.serverStatus.collectAsState()
    val activeLoveCardDeck by screensaverViewModel.activeLoveCardDeck.collectAsState()
    val isAuthBusy by screensaverViewModel.isAuthBusy.collectAsState()
    val authError by screensaverViewModel.authError.collectAsState()
    // User needs onboarding when: not signed in at all, OR signed in but profile incomplete
    val requiresOnboarding = memberIdentity?.let {
        (it.phoneNumber.isNullOrBlank() && it.googleEmail.isNullOrBlank()) || !it.profileComplete
    } ?: true

    // Onboarding really ends when your person is here, not when the account exists.
    // A brand-new user with an empty galaxy gets the invite screen instead of a void;
    // it disappears by itself the moment someone joins.
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current

    BackHandler(enabled = !requiresOnboarding && selectedNavIndex != SWEET_CORNER_TAB_INDEX && !isDrawingMode) {
        selectedNavIndex = SWEET_CORNER_TAB_INDEX // Return to the default Sweet Corner tab
    }

    val remoteSettings by com.aman.gigi.utils.AppConfig.settingsFlow.collectAsState()

    // Moonlight is no longer a tab — its flows (doodle/sparkle/create) launch as a
    // full-screen overlay from the Sweet Corner constellation.
    val navItems = remember(remoteSettings) {
        buildList {
            if (!remoteSettings.killReminders) add(NavigationItem("Reminders", Icons.Default.Notifications, REMINDERS_TAB_INDEX))
            if (!remoteSettings.killLive) add(NavigationItem("Live", Icons.Default.LocationOn, LIVE_TAB_INDEX))
            if (!remoteSettings.killSweetCorner) add(NavigationItem("Sweet Corner", Icons.Default.Favorite, SWEET_CORNER_TAB_INDEX))
            if (!remoteSettings.killNest) add(NavigationItem("Nest", Icons.Default.Home, NEST_TAB_INDEX))
            if (!remoteSettings.killMusic) add(NavigationItem("Music", Icons.Default.LibraryMusic, MUSIC_TAB_INDEX))
        }
    }

    LaunchedEffect(navItems) {
        if (navItems.isNotEmpty() && navItems.none { it.tabIndex == selectedNavIndex }) {
            selectedNavIndex = (navItems.find { it.tabIndex == SWEET_CORNER_TAB_INDEX } ?: navItems.first()).tabIndex
        }
    }

    // Inside the Music tab the pill morphs into music controls (Player centered).
    val musicNavItems = listOf(
        NavigationItem("Library", Icons.Default.Album, 0),
        NavigationItem("Player", Icons.Default.PlayArrow, 1),
        NavigationItem("Settings", Icons.Default.Settings, 2)
    )
    val musicTabIndex = MUSIC_TAB_INDEX

    // Handle Intent-based navigation (e.g., Reply with Sparkle)
    LaunchedEffect(intent) {
        val action = intent?.action
        if (action == "ACTION_OPEN_SWEET_CORNER" || action == "ACTION_VIEW_LOVE_CARDS") {
            selectedNavIndex = SWEET_CORNER_TAB_INDEX
            intent.action = null
        } else if (action == "ACTION_OPEN_MUSIC_PLAYER") {
            selectedNavIndex = musicTabIndex
            musicViewModel.setAlbumBrowserOpen(false)
            musicViewModel.setMusicSettingsOpen(false)
            intent.action = null
        } else if (action == android.content.Intent.ACTION_VIEW) {
            // Invite deep link: https://gigi.iamanraj.com/join?code=XXXXXXXX (or gigi://join…)
            // Join straight away so the invitee never has to type a code by hand.
            val code = intent.data?.getQueryParameter("code")
                ?.let { com.aman.gigi.utils.ConnectionCodeGenerator.normalizeCode(it) }
            if (!code.isNullOrBlank() &&
                com.aman.gigi.utils.ConnectionCodeGenerator.isValidCode(code)
            ) {
                android.util.Log.i("MainActivity", "🔗 Invite link → joining $code")
                selectedNavIndex = SWEET_CORNER_TAB_INDEX
                screensaverViewModel.joinConnection(code)
            }
            intent.action = null
        } else if (action == "ACTION_OPEN_CHAT") {
            val connectionId = intent.getStringExtra("connection_id")
            if (connectionId != null) {
                selectedNavIndex = SWEET_CORNER_TAB_INDEX // Sweet Corner
                screensaverViewModel.openChat(connectionId)
            }
            intent.action = null
        } else if (action == "ACTION_OPEN_MEMORIES") {
            // "Memories" from a sparkle reveal → open that partner's memories directly.
            val connectionId = intent.getStringExtra("connection_id")
            selectedNavIndex = SWEET_CORNER_TAB_INDEX
            val conn = connectionId?.let { id ->
                screensaverViewModel.activeConnections.value.find { it.connectionId == id }
            }
            screensaverViewModel.openMemoriesSpace(conn)
            intent.action = null
        } else if (action == "ACTION_REPLY_SPARKLE" || action == "ACTION_REPLY_SCRIBBLE") {
            val connectionId = intent.getStringExtra("connection_id")
            if (connectionId != null) {
                android.util.Log.i("MainActivity", "🎯 Handling Reply ($action) for $connectionId")
                selectedNavIndex = SWEET_CORNER_TAB_INDEX // Sweet Corner — the screensaver flow shows as an overlay
                screensaverViewModel.navigateTo(
                    com.aman.gigi.viewmodel.ScreensaverViewModel.ScreensaverScreen.PARTNER_SESSIONS,
                    connectionId
                )
                if (action == "ACTION_REPLY_SPARKLE") {
                    screensaverViewModel.navigateTo(
                        com.aman.gigi.viewmodel.ScreensaverViewModel.ScreensaverScreen.SPARKLE,
                        connectionId
                    )
                } else {
                    // ACTION_REPLY_SCRIBBLE
                    screensaverViewModel.setDrawingMode(true)
                }
                // Clear action to prevent re-triggering on config change
                intent.action = null
            }
        }
        
        // Handle direct doodle / FCM notification tap
        val pushType = intent?.getStringExtra("type") ?: intent?.getStringExtra("actionType")
        val doodleId = intent?.getStringExtra("scribble_id") ?: intent?.getStringExtra("scribbleId") ?: intent?.getStringExtra("messageId")
        if (pushType == "scribble" || pushType == "doodle" || pushType == "sparkle" || (doodleId != null && intent?.action != "ACTION_OPEN_CHAT")) {
            val connId = intent?.getStringExtra("connection_id") ?: intent?.getStringExtra("connectionId")
            val partnerName = intent?.getStringExtra("partner_name") ?: "Partner"
            android.util.Log.i("MainActivity", "🎨 Launching LockscreenScribbleActivity from notification intent")
            val targetIntent = android.content.Intent(context, com.aman.gigi.ui.screensaver.LockscreenScribbleActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("scribble_id", doodleId)
                putExtra("connection_id", connId)
                putExtra("partner_name", partnerName)
            }
            context.startActivity(targetIntent)
            intent?.action = null
        }
    }

    LaunchedEffect(activeLoveCardDeck?.stack?.stackId) {
        if (activeLoveCardDeck != null && !requiresOnboarding) {
            selectedNavIndex = SWEET_CORNER_TAB_INDEX
        }
    }

    LaunchedEffect(selectedNavIndex) {
        if (selectedNavIndex != musicTabIndex) {
            isMusicBottomNavVisible = true
        }
    }

    // In-App Server Auto-Update Check & Notification Cleanup
    var updateInfoState by remember { mutableStateOf<com.aman.gigi.data.update.UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        screensaverViewModel.clearAllNotifications()
        val info = com.aman.gigi.data.update.AppUpdateManager.checkForUpdates(context)
        if (info != null && info.hasUpdate) {
            updateInfoState = info
        }
    }

    // Keep the UpdateInfo around after the sheet is hidden so the floating chip can
    // bring it back — dismissing used to throw it away, stranding a download that was
    // still running with no way into the installer from inside the app.
    var updateSheetOpen by remember { mutableStateOf(false) }
    LaunchedEffect(updateInfoState) {
        val info = updateInfoState ?: return@LaunchedEffect
        // "Later" means later, not "next time you open the app". The chip and Settings
        // still get you back to it; this just stops the dialog ambushing every launch.
        if (com.aman.gigi.data.update.UpdatePrefs.isDeferred(context, info.versionCode)) return@LaunchedEffect
        updateSheetOpen = true
    }

    key(updateSheetOpen) {
        com.aman.gigi.ui.components.CuteUpdateDialog(
            updateInfo = if (updateSheetOpen) updateInfoState else null,
            onDismiss = { updateSheetOpen = false }
        )
    }

    // Sits above everything, just under the status bar, whenever a download is
    // running with the sheet hidden. Tapping it brings the installer back.
    Box(Modifier.fillMaxSize().zIndex(20f)) {
        com.aman.gigi.ui.components.UpdateFloatingChip(
            visible = !updateSheetOpen,
            onClick = { updateSheetOpen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp)
        )
    }

    // ─── Contextual permissions ───────────────────────────────────────────
    // Ask for what each feature needs, the moment the user reaches it, with an
    // animated rationale popup — instead of a wall of requests on first launch.
    val permissionFlow = LocalPermissionFlow.current

    // Ask once per session for the mandatory background permissions.
    LaunchedEffect(requiresOnboarding) {
        if (!requiresOnboarding) {
            permissionFlow.requestOnce(FeaturePermission.NOTIFICATIONS) {
                permissionFlow.requestOnce(FeaturePermission.OVERLAY) {
                    permissionFlow.requestOnce(FeaturePermission.BATTERY)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (darkTheme) {
                        listOf(
                            Color(0xFF090B16),
                            Color(0xFF15112A),
                            Color(0xFF1C1431)
                        )
                    } else {
                        listOf(
                            Color(0xFFF0F4F8),
                            Color(0xFFE6E0FF),
                            Color(0xFFF3E5F5)
                        )
                    }
                )
            )
    ) {
        RomanceAmbientDecor(
            darkTheme = darkTheme,
            modifier = Modifier
                .fillMaxSize()
        )

        // Honest connectivity: say when we're offline instead of letting messages
        // silently pile up. Sits above everything, including the galaxy.
        val deviceOnline by screensaverViewModel.isDeviceOnline.collectAsState()
        androidx.compose.animation.AnimatedVisibility(
            visible = !deviceOnline,
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).zIndex(200f)
        ) {
            androidx.compose.material3.Surface(
                color = androidx.compose.ui.graphics.Color(0xFF4A3585),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Text(
                        "📡  Offline — we'll send these when you're back",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }

        if (requiresOnboarding) {
            OnboardingGateScreen(
                serverStatus = serverStatus,
                memberIdentity = memberIdentity,
                isBusy = isAuthBusy,
                authError = authError,
                onGoogleSignIn = screensaverViewModel::signInWithGoogle,
                onCompleteProfile = screensaverViewModel::completeProfile,
                onClearError = screensaverViewModel::clearAuthError
            )
        } else {
            // When the chat sheet is open above, frost the content behind it (glass blur).
            val chatOpenForBlur by screensaverViewModel.openChatConnectionId.collectAsState()
            val contentBlur by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (chatOpenForBlur != null) 22.dp else 0.dp,
                label = "chatBackdropBlur"
            )
            AnimatedContent(
                targetState = selectedNavIndex,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn())
                            .togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn())
                            .togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                    }
                },
                label = "TabTransition",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(contentBlur)
            ) { targetIndex ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (targetIndex) {
                        REMINDERS_TAB_INDEX -> Reminders(viewModel = viewModel)
                        LIVE_TAB_INDEX -> {
                            // Live keeps its own tiny back-stack: feed ⇄ meet-up map.
                            var openMapPostId by rememberSaveable { mutableStateOf<String?>(null) }
                            val current = openMapPostId
                            if (current == null) {
                                LiveScreen(onOpenMap = { openMapPostId = it })
                            } else {
                                LiveMapScreen(postId = current, onBack = { openMapPostId = null })
                            }
                        }
                        SWEET_CORNER_TAB_INDEX -> Developer(viewModel = screensaverViewModel)
                        NEST_TAB_INDEX -> com.aman.gigi.ui.nest.NestScreen(
                            screensaverViewModel = screensaverViewModel,
                            musicViewModel = musicViewModel
                        )
                        MUSIC_TAB_INDEX -> MusicScreen(
                            onBottomNavVisibilityChanged = { isMusicBottomNavVisible = it }
                        )
                    }
                }
            }

            val currentScreen by screensaverViewModel.currentScreen.collectAsState()
            val isSparkleMode = currentScreen == ScreensaverViewModel.ScreensaverScreen.SPARKLE
            val isComposerMode by screensaverViewModel.isComposerMode.collectAsState()
            val isHistoryOpen by screensaverViewModel.isHistoryOpen.collectAsState()
            val isViewingNotifications by screensaverViewModel.isViewingNotifications.collectAsState()
            val isTrackingLocation by screensaverViewModel.isTrackingLocation.collectAsStateWithLifecycle()

            // Immersive screensaver flows (doodle/sparkle/create/join) now run as a
            // full-screen overlay launched from Sweet Corner, not as a tab.
            val screensaverFlowActive =
                (isDrawingMode && currentScreen == ScreensaverViewModel.ScreensaverScreen.PARTNER_SESSIONS) ||
                currentScreen == ScreensaverViewModel.ScreensaverScreen.SPARKLE ||
                currentScreen == ScreensaverViewModel.ScreensaverScreen.CREATE ||
                currentScreen == ScreensaverViewModel.ScreensaverScreen.CREATE_GROUP ||
                currentScreen == ScreensaverViewModel.ScreensaverScreen.JOIN ||
                currentScreen == ScreensaverViewModel.ScreensaverScreen.MANAGE_GROUP

            if (screensaverFlowActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(50f)
                ) {
                    Screensaver(viewModel = screensaverViewModel)
                }
            }

            // Chat sheet — full-screen overlay above everything when a chat is open.
            val openChatId by screensaverViewModel.openChatConnectionId.collectAsState()
            val activeConnectionsForChat by screensaverViewModel.activeConnections.collectAsState()
            androidx.compose.animation.AnimatedVisibility(
                visible = openChatId != null,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
                modifier = Modifier.zIndex(60f)
            ) {
                val cid = openChatId
                if (cid != null) {
                    val conn = activeConnectionsForChat.find { it.connectionId == cid }
                    val title = conn?.partnerName?.takeIf { it.isNotBlank() } ?: "Chat"
                    androidx.activity.compose.BackHandler(enabled = true) { screensaverViewModel.closeChat() }
                    com.aman.gigi.ui.chat.ChatScreen(
                        connectionId = cid,
                        title = title,
                        relationshipType = if (conn?.isGroup == true) "GROUP" else (conn?.relationshipType ?: "ROMANTIC"),
                        onClose = { screensaverViewModel.closeChat() },
                        viewModel = screensaverViewModel
                    )
                }
            }

            val isMusicLandscape = selectedNavIndex == musicTabIndex &&
                configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val isNavVisible = !screensaverFlowActive &&
                !isDrawingMode &&
                !isSparkleMode &&
                !isComposerMode &&
                !isHistoryOpen &&
                !isViewingNotifications &&
                !isTrackingLocation &&
                !isMusicLandscape &&
                !(selectedNavIndex == musicTabIndex && isMusicSettingsOpen) &&
                (selectedNavIndex != musicTabIndex || isMusicBottomNavVisible)




            val musicUiState by musicViewModel.uiState.collectAsStateWithLifecycle()
            val myNowPlayingState by screensaverViewModel.myNowPlaying.collectAsStateWithLifecycle()
            val partnerNowPlayingMap by screensaverViewModel.nowPlayingByConnection.collectAsStateWithLifecycle()
            val activeConnsList by screensaverViewModel.activeConnections.collectAsStateWithLifecycle()

            val (activeTrackLabel, activePartnerName, isNowPlayingPlaying) = remember(musicUiState, myNowPlayingState, partnerNowPlayingMap, activeConnsList) {
                val localSong = musicUiState.currentSong
                val localTrackLabel = if (localSong != null) "${localSong.title} • ${localSong.artist}" else null
                val localIsPlaying = musicUiState.isPlaying

                val myNp = myNowPlayingState
                val partnerNp = partnerNowPlayingMap.values.firstOrNull { it.isPlaying } ?: partnerNowPlayingMap.values.firstOrNull()
                val partnerObj = partnerNowPlayingMap.entries.firstOrNull { it.value == partnerNp }?.key?.let { pId ->
                    activeConnsList.find { it.connectionId == pId }
                }
                when {
                    localIsPlaying && !localTrackLabel.isNullOrBlank() -> Triple(localTrackLabel, null, true)
                    myNp != null && myNp.isPlaying -> Triple(myNp.label, null, true)
                    partnerNp != null -> Triple(partnerNp.label, partnerObj?.partnerName, partnerNp.isPlaying)
                    !localTrackLabel.isNullOrBlank() -> Triple(localTrackLabel, null, false)
                    myNp != null -> Triple(myNp.label, null, false)
                    else -> Triple(null, null, false)
                }
            }

            var lockedTabFeatureName by remember { mutableStateOf<String?>(null) }

            if (lockedTabFeatureName != null) {
                com.aman.gigi.ui.components.UpgradeSheet(
                    featureName = lockedTabFeatureName!!,
                    featureDescription = "Access to the ${lockedTabFeatureName!!} is locked on your current plan (${com.aman.gigi.utils.AppConfig.userPlan.tier.uppercase()}). Upgrade your subscription to unlock it!",
                    onDismiss = { lockedTabFeatureName = null }
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isNavVisible,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val inMusicTab = selectedNavIndex == musicTabIndex
                GlassBottomNavigation(
                    items = if (inMusicTab) musicNavItems else navItems,
                    selectedItem = when {
                        !inMusicTab -> navItems.indexOfFirst { it.tabIndex == selectedNavIndex }.coerceAtLeast(0)
                        isMusicSettingsOpen -> 2
                        isAlbumBrowserOpen -> 0
                        else -> 1
                    },
                    onItemSelected = { index ->
                        if (inMusicTab) {
                            when (index) {
                                0 -> { // Library
                                    musicViewModel.setMusicSettingsOpen(false)
                                    musicViewModel.setAlbumBrowserOpen(true)
                                }
                                1 -> { // Player
                                    musicViewModel.setMusicSettingsOpen(false)
                                    musicViewModel.setAlbumBrowserOpen(false)
                                }
                                2 -> { // Settings (opens directly without collapsing library first)
                                    if (!isAlbumBrowserOpen) {
                                        musicViewModel.setAlbumBrowserOpen(false)
                                    }
                                    musicViewModel.setMusicSettingsOpen(true)
                                }
                            }
                        } else {
                            val targetItem = navItems.getOrNull(index) ?: return@GlassBottomNavigation
                            val targetTabIndex = targetItem.tabIndex
                            val planFeatures = com.aman.gigi.utils.AppConfig.userPlan.features
                            val isAllowed = when (targetTabIndex) {
                                REMINDERS_TAB_INDEX -> planFeatures.tabReminders
                                LIVE_TAB_INDEX -> planFeatures.tabLive
                                SWEET_CORNER_TAB_INDEX -> planFeatures.tabSweetCorner
                                NEST_TAB_INDEX -> planFeatures.tabNest
                                MUSIC_TAB_INDEX -> planFeatures.tabMusic
                                else -> true
                            }
                            if (!isAllowed) {
                                lockedTabFeatureName = when (targetTabIndex) {
                                    REMINDERS_TAB_INDEX -> "Reminders Tab"
                                    LIVE_TAB_INDEX -> "Live Location Tab"
                                    SWEET_CORNER_TAB_INDEX -> "Sweet Corner Tab"
                                    NEST_TAB_INDEX -> "Our Nest Tab"
                                    MUSIC_TAB_INDEX -> "Music Player Tab"
                                    else -> "Navigation Tab"
                                }
                            } else {
                                // Re-tapping Sweet Corner opens the connections sheet.
                                if (targetTabIndex == SWEET_CORNER_TAB_INDEX && selectedNavIndex == SWEET_CORNER_TAB_INDEX) {
                                    screensaverViewModel.openConnectionsSheet()
                                } else {
                                    selectedNavIndex = targetTabIndex
                                }
                            }
                        }
                    },

                    onSwipe = { delta ->
                        if (inMusicTab) {
                            val currentSub = when {
                                isMusicSettingsOpen -> 2
                                isAlbumBrowserOpen -> 0
                                else -> 1
                            }
                            if (delta < 0) { // Swiping RIGHT (towards previous screen)
                                if (currentSub == 2) {
                                    // From Settings -> Player
                                    musicViewModel.setMusicSettingsOpen(false)
                                    musicViewModel.setAlbumBrowserOpen(false)
                                } else {
                                    // From Player or Library -> Direct 1-swipe back to Sweet Corner
                                    musicViewModel.setMusicSettingsOpen(false)
                                    musicViewModel.setAlbumBrowserOpen(false)
                                    selectedNavIndex = SWEET_CORNER_TAB_INDEX
                                }
                            } else if (delta > 0) { // Swiping LEFT (towards next screen)
                                if (currentSub == 0) {
                                    // From Library -> Player
                                    musicViewModel.setMusicSettingsOpen(false)
                                    musicViewModel.setAlbumBrowserOpen(false)
                                } else if (currentSub == 1) {
                                    // From Player -> Settings
                                    musicViewModel.setMusicSettingsOpen(true)
                                } else {
                                    // From Settings -> Sweet Corner
                                    musicViewModel.setMusicSettingsOpen(false)
                                    musicViewModel.setAlbumBrowserOpen(false)
                                    selectedNavIndex = SWEET_CORNER_TAB_INDEX
                                }
                            }
                        } else {
                            selectedNavIndex = (selectedNavIndex + delta).coerceIn(0, navItems.lastIndex)
                        }
                    },
                    isMusicTab = inMusicTab,
                    activeNowPlayingTrackLabel = activeTrackLabel,
                    activeNowPlayingPartnerName = activePartnerName,
                    isNowPlayingPlaying = isNowPlayingPlaying,
                    onNowPlayingClick = {
                        selectedNavIndex = musicTabIndex
                        musicViewModel.setAlbumBrowserOpen(false)
                        musicViewModel.setMusicSettingsOpen(false)
                    },
                    onNowPlayingTogglePlay = {
                        musicViewModel.togglePlayback()
                    }
                )
            }
        }
    }
}
