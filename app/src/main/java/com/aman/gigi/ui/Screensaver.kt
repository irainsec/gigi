package com.aman.gigi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Email
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.hilt.navigation.compose.hiltViewModel
import com.aman.gigi.model.*
import com.aman.gigi.ui.screensaver.components.GifPickerTray
import com.aman.gigi.ui.screensaver.components.SafeGlassBox
import com.aman.gigi.ui.screensaver.components.ScribblePlaybackComponent
import com.skydoves.cloudy.Cloudy
import com.aman.gigi.data.sync.ScribbleSerializer
import com.aman.gigi.ui.screensaver.connection.*
import com.aman.gigi.ui.screensaver.drawing.DrawingScreen
import com.aman.gigi.viewmodel.ScreensaverViewModel
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main Screensaver screen - shows different states based on connection
 */
@Composable
fun Screensaver(
    modifier: Modifier = Modifier,
    viewModel: ScreensaverViewModel = hiltViewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val pairingState by viewModel.pairingState.collectAsState()
    val activeConnections by viewModel.activeConnections.collectAsState()
    val partnerConnectionId by viewModel.partnerConnectionId.collectAsState()
    val memberIdentity by viewModel.memberIdentity.collectAsState()
    val selectedConnection by viewModel.selectedConnection.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDrawingMode by viewModel.isDrawingMode.collectAsState()
    val isViewingNotifications by viewModel.isViewingNotifications.collectAsState()
    var showEmojiPicker by remember { mutableStateOf(false) }
    val serverStatus by viewModel.serverStatus.collectAsState()
    val quoteOverlay by viewModel.quoteOverlay.collectAsState()
    val hasCreatorConnections = remember(activeConnections) {
        activeConnections.any { it.role.equals(com.aman.gigi.model.ConnectionRole.CREATOR.name, ignoreCase = true) }
    }
    val isPartnerOnlyUser = activeConnections.isNotEmpty() && !hasCreatorConnections

    LaunchedEffect(isPartnerOnlyUser, activeConnections, currentScreen) {
        if (isPartnerOnlyUser && currentScreen == ScreensaverViewModel.ScreensaverScreen.LIST) {
            activeConnections.firstOrNull()?.let { firstConnection ->
                viewModel.navigateTo(
                    ScreensaverViewModel.ScreensaverScreen.PARTNER_SESSIONS,
                    firstConnection.connectionId
                )
            }
        }
    }

    val isHistoryOpen by viewModel.isHistoryOpen.collectAsState()
    val isMemoriesSpaceOpen by viewModel.isMemoriesSpaceOpen.collectAsState()
    val isTrackingLocation by viewModel.isTrackingLocation.collectAsState()

    val isBackHandlerEnabled = isTrackingLocation ||
            isMemoriesSpaceOpen ||
            isHistoryOpen ||
            isDrawingMode ||
            currentScreen != ScreensaverViewModel.ScreensaverScreen.LIST

    // Handle Back Gesture
    BackHandler(enabled = isBackHandlerEnabled) {
        if (isTrackingLocation) {
            viewModel.setTrackingLocation(false)
        } else if (isMemoriesSpaceOpen) {
            if (viewModel.selectedMemoriesConnection.value != null && viewModel.selectedMemoriesConnection.value!!.connectionId.isNotBlank()) {
                viewModel.openMemoriesSpace(null)
            } else {
                viewModel.closeMemoriesSpace()
            }
        } else if (isHistoryOpen) {
            viewModel.setHistoryOpen(false)
        } else if (isDrawingMode) {
            viewModel.setDrawingMode(false)
        } else if (currentScreen != ScreensaverViewModel.ScreensaverScreen.LIST) {
            viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST)
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        when (currentScreen) {
            ScreensaverViewModel.ScreensaverScreen.LIST -> {
                val canAddConnection by viewModel.canAddConnection.collectAsState()
                val identity by viewModel.memberIdentity.collectAsState()
                val groupMemberEmojis by viewModel.groupMemberEmojis.collectAsState()
                val galaxyNowPlaying by viewModel.nowPlayingByConnection.collectAsState()
                val galaxyMyNowPlaying by viewModel.myNowPlaying.collectAsState()
                val galaxyQuotes by viewModel.quotesByConnection.collectAsState()
                val nebulaMotes by viewModel.nebulaMotes.collectAsState()
                val incomingInvites by viewModel.incomingNebulaInvites.collectAsState()
                val nebulaSearchQuery by viewModel.nebulaSearchQuery.collectAsState()
                val pendingGhostInvites by viewModel.pendingGhostInvites.collectAsState()

                GalaxyView(
                    identity = identity,
                    connections = activeConnections,
                    groupMemberEmojis = groupMemberEmojis,
                    nowPlaying = galaxyNowPlaying,
                    myNowPlaying = galaxyMyNowPlaying,
                    quotes = galaxyQuotes,
                    nebulaMotes = nebulaMotes,
                    incomingInvites = incomingInvites,
                    searchQuery = nebulaSearchQuery,
                    onSearchQueryChange = { viewModel.setNebulaSearchQuery(it) },
                    pendingGhostInvites = pendingGhostInvites,
                    onInviteMote = { m -> viewModel.sendNebulaInvite(m) },
                    onAcceptInvite = { inv -> viewModel.respondToNebulaInvite(inv.inviteId, accept = true) },
                    onDeclineInvite = { inv -> viewModel.respondToNebulaInvite(inv.inviteId, accept = false) },
                    onBlockMote = { id -> viewModel.blockMember(id) },
                    onReportMote = { id, r, n -> viewModel.reportMember(id, r, n) },
                    camera = viewModel.galaxyCamera,
                    onOpenConnection = { connectionId ->
                        if (serverStatus.mode != com.aman.gigi.model.ServerMode.MAINTENANCE) {
                            val conn = activeConnections.find { it.connectionId == connectionId }
                            if (conn != null) {
                                if (conn.isGroup) {
                                    if (conn.role.equals(com.aman.gigi.model.ConnectionRole.CREATOR.name, ignoreCase = true)) {
                                        viewModel.clearTargetGroupMember()
                                        viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.PARTNER_SESSIONS, connectionId)
                                    }
                                } else {
                                    viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.PARTNER_SESSIONS, connectionId)
                                }
                            }
                        }
                    },
                    onSunClick = {
                        viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.SETTINGS)
                    },
                    onOpenMemories = {
                        viewModel.openMemoriesSpace()
                    }
                )

                // If there are no connections, automatically show the create/join choice overlay
                if (activeConnections.isEmpty() && canAddConnection) {
                    NotConnectedOverlay(
                        serverStatus = serverStatus,
                        onCreateConnection = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.CREATE) },
                        onJoinConnection = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.JOIN) }
                    )
                }
                
                if (viewModel.showAddChoice.value && serverStatus.mode == com.aman.gigi.model.ServerMode.ONLINE) {
                    AddConnectionChoiceDialog(
                        onDismiss = { viewModel.hideAddChoice() },
                        onCreate = {
                            viewModel.hideAddChoice()
                            viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.CREATE)
                        },
                        onJoin = {
                            viewModel.hideAddChoice()
                            viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.JOIN)
                        }
                    )
                }
            }
            
            ScreensaverViewModel.ScreensaverScreen.CREATE -> {
                var partnerName by remember { mutableStateOf("") }
                var relationshipType by remember { mutableStateOf("ROMANTIC") }
                var partnerEmoji by remember { mutableStateOf<String?>(null) }
                var isNamingDone by remember { mutableStateOf(false) }
                val newCode = remember { com.aman.gigi.utils.ConnectionCodeGenerator.generateCode() }
                
                if (!isNamingDone) {
                    com.aman.gigi.ui.screensaver.connection.NamingScreen(
                        onNameEntered = { name, type, emojiUrl, _ ->
                            partnerName = name
                            relationshipType = type
                            partnerEmoji = emojiUrl
                            isNamingDone = true
                        },
                        onCancel = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST) }
                    )
                } else {
                    LaunchedEffect(newCode) {
                        viewModel.createConnection(
                            connectionId = newCode,
                            partnerName = partnerName,
                            partnerDeviceId = "waiting...",
                            connectionCode = newCode,
                            relationshipType = relationshipType,
                            partnerEmojiUrl = partnerEmoji
                        )
                    }
                    CreateConnectionScreen(
                        connectionCode = newCode,
                        partnerName = partnerName,
                        onCancel = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST) }
                    )
                }
            }
            ScreensaverViewModel.ScreensaverScreen.CREATE_GROUP -> {
                var groupName by remember { mutableStateOf("") }
                var groupEmoji by remember { mutableStateOf<String?>(null) }
                var isNamingDone by remember { mutableStateOf(false) }
                val newCode = remember { com.aman.gigi.utils.ConnectionCodeGenerator.generateCode() }
                
                // Keep track of the selected connection IDs to pass to the view model
                var selectedConnectionIds by remember { mutableStateOf(emptyList<String>()) }
                
                if (!isNamingDone) {
                    com.aman.gigi.ui.screensaver.connection.NamingScreen(
                        onNameEntered = { name, _, emojiUrl, selectedConns ->
                            groupName = name
                            groupEmoji = emojiUrl
                            selectedConnectionIds = selectedConns.map { it.connectionId }
                            isNamingDone = true
                        },
                        onCancel = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST) },
                        isGroup = true,
                        connections = activeConnections
                    )
                } else {
                    LaunchedEffect(newCode) {
                        viewModel.createGroupFromConnections(
                            groupName = groupName,
                            connectionIds = selectedConnectionIds,
                            groupEmoji = groupEmoji,
                            groupId = newCode
                        )
                    }
                    CreateConnectionScreen(
                        connectionCode = newCode,
                        partnerName = groupName,
                        onCancel = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST) }
                    )
                }
            }

            
            
            ScreensaverViewModel.ScreensaverScreen.MANAGE_GROUP -> {
                val groupConn = selectedConnection
                if (groupConn != null) {
                    com.aman.gigi.ui.screensaver.connection.GroupDetailsScreen(
                        connectionId = groupConn.connectionId,
                        onBack = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST) }
                    )
                } else {
                    // Never render an invisible overlay — it looked like the ⚙️ did nothing
                    // while the selected connection was still resolving.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF6F1FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF8B5CF6))
                    }
                }
            }

            ScreensaverViewModel.ScreensaverScreen.JOIN -> {
                DisposableEffect(Unit) {
                    android.util.Log.i("Screensaver", "🔍 [GIGI-LIFE] JOIN Screen Mounted")
                    onDispose {
                        android.util.Log.i("Screensaver", "🔍 [GIGI-LIFE] JOIN Screen Unmounted")
                    }
                }
                var scannedCode by remember { mutableStateOf("") }
                
                val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = com.journeyapps.barcodescanner.ScanContract()
                ) { result ->
                    if (result.contents != null) {
                        scannedCode = result.contents
                        android.util.Log.d("Screensaver", "QR Scanned: $scannedCode")
                    }
                }
                
                JoinConnectionScreen(
                    onJoinWithCode = { code, partnerName, relationshipType ->
                        viewModel.joinConnection(code, partnerName, relationshipType)
                        viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST)
                    },
                    onScanQR = { 
                        android.util.Log.i("Screensaver", "🚀 [GIGI-NAV] Scan QR requested")
                        val options = com.journeyapps.barcodescanner.ScanOptions()
                        options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                        options.setPrompt("Scan a partner's connection QR code")
                        options.setBeepEnabled(true)
                        options.setBarcodeImageEnabled(true)
                        options.setOrientationLocked(false)
                        scanLauncher.launch(options)
                    },
                    initialCode = scannedCode,
                    onCancel = { 
                        android.util.Log.i("Screensaver", "🚀 [GIGI-NAV] Cancel requested from JOIN")
                        viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST) 
                    }
                )
            }
            
            ScreensaverViewModel.ScreensaverScreen.PARTNER_SESSIONS -> {
                val connection = selectedConnection
                // Fix: Check if we are waiting for a connection to load
                if (connection != null) {
                    val isDrawingMode by viewModel.isDrawingMode.collectAsState()
                    
                    if (isDrawingMode) {
                        DrawingScreen(
                            connectionId = connection.connectionId,
                            onCancel = { viewModel.setDrawingMode(false) },
                            onSend = { viewModel.setDrawingMode(false) }
                        )
                    } else {
                        ConnectedIdleScreen(
                            connection = connection,
                            onTapToDraw = { viewModel.setDrawingMode(true) },
                            onDisconnect = { 
                                viewModel.disconnect(connection)
                                viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST)
                            },
                            onBack = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST) },
                            onEmojiClick = { showEmojiPicker = true }
                        )
                    }
                } else if (partnerConnectionId != null) {
                    // Loading State: Partner ID is set, but connection object not yet loaded from Repo
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF8B5CF6))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Syncing...",
                            modifier = Modifier.padding(top = 64.dp),
                            color = Color.Gray
                        )
                    }
                } else {
                    // No partner selected, go back to list
                    viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST)
                }
            }
            
            ScreensaverViewModel.ScreensaverScreen.SPARKLE -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                com.aman.gigi.ui.sparkle.SparkleCameraScreen(
                    onClose = {
                        viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST)
                    },
                    onCapture = {
                        // CAPTURE STUB (Restored per user request)
                        android.widget.Toast.makeText(context, "Captured! (Stub)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
            
            ScreensaverViewModel.ScreensaverScreen.SETTINGS -> {
                com.aman.gigi.ui.settings.ScreensaverSettingsScreen(
                    onBack = {
                        viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST)
                    }
                )
            }
        }
        
        // Partner Disconnected Dialog
        val partnerDisconnectedName by viewModel.partnerDisconnected.collectAsState()
        if (partnerDisconnectedName != null) {
            PartnerDisconnectedDialog(
                partnerName = partnerDisconnectedName!!,
                onDismiss = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST) }
            )
        }
        
        // Connection limit reached → route to the upgrade sheet
        val connectionLimitMessage by viewModel.connectionLimitMessage.collectAsState()
        if (connectionLimitMessage != null) {
            com.aman.gigi.ui.components.UpgradeSheet(
                featureName = "More Connections",
                featureDescription = connectionLimitMessage!!,
                onDismiss = { viewModel.dismissConnectionLimitSheet() }
            )
        }

        // ── Cosmic Memories Space Overlay ──
        val selectedMemoriesConnection by viewModel.selectedMemoriesConnection.collectAsState()
        val sharedSparkles by viewModel.sharedSparkles.collectAsState()
        val memoryCounts by viewModel.memoryCountsByConnection.collectAsState()

        androidx.compose.animation.AnimatedVisibility(
            visible = isMemoriesSpaceOpen && currentScreen == ScreensaverViewModel.ScreensaverScreen.LIST,
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
                    viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.SPARKLE, conn.connectionId)
                }
            )
        }


        // Send Failed / Partner Offline Dialog
        val sendFailedName by viewModel.sendFailedPartnerName.collectAsState()
        val lastErrorMessage by viewModel.lastErrorMessage.collectAsState()
        if (sendFailedName != null) {
            SendFailedDialog(
                partnerName = sendFailedName!!,
                message = lastErrorMessage ?: "Scribble or GIF not sent as connection was lost.",
                onDismiss = { viewModel.clearSendFailure() }
            )
        }

        // Reconnection Indicator Overlay (Non-blocking)
        val connectionState by viewModel.connectionState.collectAsState()
        val selectedConnectionState by viewModel.selectedConnectionState.collectAsState()
        val overlayConnectionState = if (
            currentScreen == ScreensaverViewModel.ScreensaverScreen.PARTNER_SESSIONS &&
            selectedConnection != null
        ) {
            selectedConnectionState
        } else {
            connectionState
        }
        val selectedConnectionReady = currentScreen == ScreensaverViewModel.ScreensaverScreen.PARTNER_SESSIONS &&
            selectedConnection?.transportState != "NO_INTERNET" &&
            serverStatus.mode == com.aman.gigi.model.ServerMode.ONLINE &&
            when {
                // Groups: show screen as soon as transport is connected to the server —
                // waiting for members to join is the normal group state, not an error.
                selectedConnection?.isGroup == true ->
                    selectedConnection?.transportState == "CONNECTED"
                // 1-1: require partner to be online
                else ->
                    selectedConnection?.partnerPresence == "ONLINE"
            }
        val showSyncOverlay = currentScreen != ScreensaverViewModel.ScreensaverScreen.CREATE &&
            currentScreen != ScreensaverViewModel.ScreensaverScreen.CREATE_GROUP &&
            currentScreen != ScreensaverViewModel.ScreensaverScreen.JOIN &&
            currentScreen != ScreensaverViewModel.ScreensaverScreen.LIST &&
            // The group management page is not a live-sync surface — don't cover it
            // with the reconnecting character.
            currentScreen != ScreensaverViewModel.ScreensaverScreen.MANAGE_GROUP &&
            !selectedConnectionReady &&
            (
                overlayConnectionState == com.aman.gigi.model.ConnectionState.DISCONNECTED ||
                    overlayConnectionState == com.aman.gigi.model.ConnectionState.CONNECTING ||
                    overlayConnectionState == com.aman.gigi.model.ConnectionState.NO_INTERNET ||
                    serverStatus.mode != com.aman.gigi.model.ServerMode.ONLINE
                )
        
        androidx.compose.animation.AnimatedVisibility(
            visible = showSyncOverlay,
            enter = androidx.compose.animation.scaleIn(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
        ) {
            val overlayTitle = when {
                overlayConnectionState == com.aman.gigi.model.ConnectionState.NO_INTERNET -> "No Internet Connection"
                serverStatus.mode == com.aman.gigi.model.ServerMode.MAINTENANCE -> "Server in maintenance"
                serverStatus.mode == com.aman.gigi.model.ServerMode.OFFLINE -> "Server offline"
                overlayConnectionState == com.aman.gigi.model.ConnectionState.CONNECTING -> "Reconnecting to server..."
                else -> "Connection lost"
            }
            val overlaySubtitle = when {
                overlayConnectionState == com.aman.gigi.model.ConnectionState.NO_INTERNET -> "Reconnect to the internet to resume partner sync"
                serverStatus.mode == com.aman.gigi.model.ServerMode.MAINTENANCE -> {
                    serverStatus.message ?: "Partner sync is temporarily paused."
                }
                serverStatus.mode == com.aman.gigi.model.ServerMode.OFFLINE -> "Trying to reach sync server"
                else -> "Trying to reach sync server"
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.7f),
                contentAlignment = Alignment.Center
            ) {
                SafeGlassBox(shape = RoundedCornerShape(24.dp)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(24.dp)
                    ) {
                        if (serverStatus.mode != com.aman.gigi.model.ServerMode.MAINTENANCE) {
                            AnimatedReconnectingCharacter(
                                isConnecting = overlayConnectionState == com.aman.gigi.model.ConnectionState.CONNECTING || overlayConnectionState == com.aman.gigi.model.ConnectionState.NO_INTERNET
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        Text(
                            text = overlayTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Text(
                            text = overlaySubtitle,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // Premium Notification Overlay (Popup)
        val partnerPresencePopup by viewModel.partnerPresencePopup.collectAsState()
        PartnerPresenceOverlay(
            popup = partnerPresencePopup,
            onDismiss = { viewModel.dismissPartnerPresencePopup() }
        )

        QuoteOverlay(
            overlay = quoteOverlay,
            onDismiss = { viewModel.dismissQuoteOverlay() }
        )



        // Notification History (Full List)
        if (isViewingNotifications) {
            com.aman.gigi.ui.screensaver.connection.NotificationListView(
                viewModel = viewModel,
                onClose = { viewModel.setViewingNotifications(false) }
            )
        }

        if (showEmojiPicker) {
            val connection = selectedConnection
            if (connection != null) {
                com.aman.gigi.ui.components.AvatarEmojiPickerDialog(
                    onDismiss = { showEmojiPicker = false },
                    onPickEmoji = { emoji ->
                        viewModel.updatePartnerLocalEmoji(connection.connectionId, emoji)
                        showEmojiPicker = false
                    },
                    title = "Pick an emoji for ${connection.partnerName}"
                )
            }
        }
    }
}

@Composable
fun SendFailedDialog(
    partnerName: String,
    message: String,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        SafeGlassBox(
                shape = RoundedCornerShape(32.dp),
                borderWidth = 2.dp
            ) {
                Surface(
                    color = Color(0xFFE6E0FF).copy(alpha = 0.7f),
                    shape = RoundedCornerShape(32.dp),
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(64.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Not Sent",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = message,
                            textAlign = TextAlign.Center,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("OK", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
    }
}

@Composable
fun PartnerDisconnectedDialog(
    partnerName: String,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        SafeGlassBox(
                shape = RoundedCornerShape(32.dp),
                borderWidth = 2.dp
            ) {
                Surface(
                    color = Color(0xFFE6E0FF).copy(alpha = 0.7f),
                    shape = RoundedCornerShape(32.dp),
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(64.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Partner Disconnected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "$partnerName has disconnected the session.",
                            textAlign = TextAlign.Center,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Return to Partners", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
    }
}

@Composable
fun AddConnectionChoiceDialog(
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        SafeGlassBox(
                shape = RoundedCornerShape(32.dp),
                borderWidth = 1.5.dp
            ) {
                Surface(
                    color = Color(0xFFE6E0FF).copy(alpha = 0.7f),
                    shape = RoundedCornerShape(32.dp),
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "Add Connection",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF8B5CF6),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp
                        )
                        
                        Text(
                            text = "Choose how you want to connect with a partner.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = onCreate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("New Partner Connection", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onJoin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8B5CF6)),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Join Existing Connection", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Cancel", color = Color.Black.copy(alpha = 0.4f), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
    }
}
@Composable
fun NotConnectedOverlay(
    serverStatus: com.aman.gigi.model.ServerStatus,
    onCreateConnection: () -> Unit,
    onJoinConnection: () -> Unit
) {
    val isServerOnline = serverStatus.mode == com.aman.gigi.model.ServerMode.ONLINE
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        SafeGlassBox(
                shape = RoundedCornerShape(32.dp),
                borderWidth = 1.5.dp
            ) {
                Surface(
                    color = Color(0xFFE6E0FF).copy(alpha = 0.7f),
                    shape = RoundedCornerShape(32.dp),
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Welcome to ScribbleSync",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1A237E),
                            textAlign = TextAlign.Center
                        )

                        if (serverStatus.mode != com.aman.gigi.model.ServerMode.ONLINE) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (serverStatus.mode == com.aman.gigi.model.ServerMode.MAINTENANCE) {
                                    "Server in maintenance"
                                } else {
                                    "Server offline"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5E35B1),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = serverStatus.message ?: if (serverStatus.mode == com.aman.gigi.model.ServerMode.MAINTENANCE) {
                                    "Partner sync is temporarily paused. Reminders still work."
                                } else {
                                    "Partner sync will resume when the server is reachable again."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF546E7A),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onCreateConnection,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = isServerOnline,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Create Partner Connection", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = onJoinConnection,
                            enabled = isServerOnline,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Join Existing Connection", color = Color(0xFF8B5CF6), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
    }
}

/**
 * Dialog that lets the creator pick which group member to target with partner controls.
 */
@Composable
private fun GroupMemberPickerDialog(
    connection: com.aman.gigi.model.Connection,
    viewModel: ScreensaverViewModel,
    onDismiss: () -> Unit,
    onMemberSelected: (com.aman.gigi.model.ConnectionMember) -> Unit
) {
    val members by produceState<List<com.aman.gigi.model.ConnectionMember>>(initialValue = emptyList()) {
        value = viewModel.getGroupMembers(connection.connectionId)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = connection.partnerName.ifEmpty { "Group" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A237E)
                )
                Text(
                    text = "Choose a member to control",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        text = {
            if (members.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFF8B5CF6), modifier = androidx.compose.ui.Modifier.size(32.dp))
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(members) { member ->
                        Surface(
                            onClick = { onMemberSelected(member) },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFEDE7F6),
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = androidx.compose.ui.Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = member.memberEmoji.ifEmpty { "👤" },
                                    fontSize = 26.sp
                                )
                                Column {
                                    Text(
                                        text = member.memberName.ifEmpty { "Member" },
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color(0xFF1A237E)
                                    )
                                    Text(
                                        text = member.role.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF8B5CF6)) }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

/**
 * Connected idle screen - shows clock and partner name
 */
@Composable
fun ConnectedIdleScreen(
    connection: com.aman.gigi.model.Connection,
    onTapToDraw: () -> Unit,
    onEmojiClick: () -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: com.aman.gigi.viewmodel.ScreensaverViewModel = hiltViewModel(),
    musicViewModel: com.aman.gigi.viewmodel.MusicViewModel = hiltViewModel()
) {
    // Duration state (Meeting / Connected Since)
    val context = androidx.compose.ui.platform.LocalContext.current
    val effectiveMeetingDate = connection.meetingDate ?: connection.createdAt
    var durationText by remember(effectiveMeetingDate) { mutableStateOf(formatDuration(effectiveMeetingDate)) }
    
    var anniversaryText by remember(connection.anniversaryDate) { 
        mutableStateOf(connection.anniversaryDate?.let { formatAnniversaryCountdown(it) }) 
    }

    // Theme Picker State
    var showThemePicker by remember { mutableStateOf(false) }

    // Role state
    val isCreator by viewModel.isCreator.collectAsState()
    val pendingActionCount by viewModel.pendingActionCount.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val canSendInteractiveActions = true // Allow taps, let ViewModel/SyncManager handle offline states

    // Group member targeting — when creator picked a specific member from the group
    val targetMemberName by viewModel.targetGroupMemberName.collectAsState()
    val targetMemberEmoji by viewModel.targetGroupMemberEmoji.collectAsState()
    // Display the picked member's name/emoji instead of the generic group name when applicable
    val displayName = if (connection.isGroup && !targetMemberName.isNullOrBlank()) targetMemberName!! else connection.partnerName
    val displayEmoji = if (connection.isGroup && !targetMemberEmoji.isNullOrBlank()) targetMemberEmoji!! else null

    // Group member presence — only used when this is a group connection
    val allGroupMembers by viewModel.getGroupMembersFlow(connection.connectionId)
        .collectAsState(initial = emptyList())
    val currentDeviceId = viewModel.currentDeviceId
    // A member is "online" if it's the current device OR it's the device the server last
    // reported as online for this connection.
    fun isMemberOnline(deviceId: String) = deviceId == currentDeviceId ||
        (deviceId == connection.partnerDeviceId && connection.partnerPresence == "ONLINE")
    
    val musicUiState by musicViewModel.uiState.collectAsState()

    // --- Resolve the theme from the connection's relationship type ---
    val theme = remember(connection.relationshipType) {
        com.aman.gigi.model.RelationshipType.fromString(connection.relationshipType).toTheme()
    }
    val statusLabel = when {
        connection.transportState == "NO_INTERNET" -> "No internet"
        serverStatus.mode == com.aman.gigi.model.ServerMode.MAINTENANCE -> "Server in maintenance"
        serverStatus.mode == com.aman.gigi.model.ServerMode.OFFLINE -> "Server offline"
        connection.partnerPresence == "ONLINE" -> "Partner online"
        connection.partnerPresence == "OFFLINE" -> "Partner offline"
        connection.transportState == "CONNECTING" -> "Reconnecting to server"
        else -> "Checking partner"
    }
    val statusColor = when {
        connection.transportState == "NO_INTERNET" -> Color(0xFFFF7043)
        serverStatus.mode == com.aman.gigi.model.ServerMode.MAINTENANCE -> Color(0xFFFFC107)
        serverStatus.mode == com.aman.gigi.model.ServerMode.OFFLINE -> Color(0xFFFFA726)
        connection.partnerPresence == "ONLINE" -> Color(0xFF4ADE80)
        connection.partnerPresence == "OFFLINE" -> Color(0xFFB0BEC5)
        connection.transportState == "CONNECTING" -> Color(0xFFFFB74D)
        else -> Color(0xFF90CAF9)
    }
    val lastSeenText = remember(
        connection.lastSeenAt,
        connection.partnerPresence,
        connection.transportState,
        serverStatus.mode,
        serverStatus.message
    ) {
        when {
            connection.transportState == "NO_INTERNET" -> "Reconnect to the internet to resume sync"
            serverStatus.mode == com.aman.gigi.model.ServerMode.MAINTENANCE -> {
                serverStatus.message ?: "Partner sync is temporarily paused."
            }
            serverStatus.mode == com.aman.gigi.model.ServerMode.OFFLINE -> "Trying to reach sync server"
            connection.partnerPresence == "ONLINE" -> "Connected and ready"
            connection.partnerPresence == "OFFLINE" && connection.lastSeenAt != null -> {
                "Last seen ${DateUtils.getRelativeTimeSpanString(connection.lastSeenAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}"
            }
            else -> "Awaiting presence snapshot"
        }
    }
    
    // Update timers every second
    LaunchedEffect(effectiveMeetingDate, connection.anniversaryDate) {
        while (true) {
            durationText = formatDuration(effectiveMeetingDate)
            anniversaryText = connection.anniversaryDate?.let { formatAnniversaryCountdown(it) }
            kotlinx.coroutines.delay(1000)
        }
    }
    
    // Animated floating orbs
    val infiniteTransition = rememberInfiniteTransition(label = "orbs")
    
    val orb1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1"
    )
    
    val orb2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -80f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2"
    )
    
    val orb3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb3"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Background brush - theme aware, respects system dark mode
    val isDark = false
    val backgroundBrush = Brush.verticalGradient(
        colors = if (isDark) theme.darkBackgroundColors else theme.backgroundColors
    )
    
    androidx.compose.runtime.CompositionLocalProvider(com.aman.gigi.model.LocalConnectionTheme provides theme) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        // Animated flowers, hearts, and sparkles
        com.aman.gigi.ui.components.RomanceAmbientDecor(
            modifier = Modifier.fillMaxSize(),
            darkTheme = false
        )

        // Notification History Toggle (Top Right)
        IconButton(
            onClick = { viewModel.setViewingNotifications(true) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = Color(0xFF8B5CF6)
            )
        }

        // Partner Typing / Drawing Badge
        val isTyping by viewModel.isPartnerTyping.collectAsState()
        val isDrawing by viewModel.isPartnerDrawing.collectAsState()

        if (isTyping || isDrawing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            ) {
                SafeGlassBox(
                    shape = RoundedCornerShape(20.dp),
                    borderWidth = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = theme.primaryColor,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = if (isDrawing) "$displayName is drawing..." else "$displayName is typing...",
                            color = Color(0xFF1A237E),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // (Buttons moved to end of Box)

        // Floating orbs in background
        FloatingOrb(
            size = 150.dp,
            color = theme.orbColor1,
            offsetX = orb1Offset.dp,
            offsetY = (-orb1Offset * 0.5f).dp,
            modifier = Modifier.align(Alignment.TopStart).offset(x = 50.dp, y = 100.dp)
        )
        
        FloatingOrb(
            size = 200.dp,
            color = theme.orbColor2,
            offsetX = orb2Offset.dp,
            offsetY = (orb2Offset * 0.7f).dp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-100).dp, y = 200.dp)
        )
        
        FloatingOrb(
            size = 120.dp,
            color = theme.orbColor3,
            offsetX = (-orb3Offset).dp,
            offsetY = orb3Offset.dp,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = 100.dp, y = (-200).dp)
        )
        
        // Center content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 40.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        if (dragAmount < -20f) {
                            viewModel.setHistoryOpen(true)
                        }
                    }
                }
        ) {
            // Partner / member name
            if (connection.isGroup && targetMemberName.isNullOrBlank()) {
                // ── GROUP HEADER ──────────────────────────────────────────────
                Text(
                    text = "Group Hub",
                    fontSize = 13.sp,
                    color = theme.primaryColor.copy(alpha = 0.55f),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = connection.partnerName.ifEmpty { "Group" },
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.primaryColor
                    )
                    if (allGroupMembers.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        val onlineCount = allGroupMembers.count { isMemberOnline(it.memberDeviceId) }
                        Surface(
                            color = theme.primaryColor.copy(alpha = 0.13f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(Color(0xFF4CAF50), CircleShape)
                                )
                                Text(
                                    text = "$onlineCount online · ${allGroupMembers.size} members",
                                    fontSize = 11.sp,
                                    color = theme.primaryColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // ── 1-1 / CONTROLLING HEADER ─────────────────────────────────
                Text(
                    text = if (connection.isGroup && !targetMemberName.isNullOrBlank())
                        "Controlling" else theme.connectedLabel,
                    fontSize = 16.sp,
                    color = Color.Black.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onEmojiClick() }
                ) {
                    Text(
                        text = displayName,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.primaryColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayEmoji ?: connection.partnerEmoji,
                        fontSize = 28.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (connection.isGroup && allGroupMembers.isNotEmpty() && targetMemberName.isNullOrBlank()) {
                // ── GROUP MEMBER PRESENCE STRIP ───────────────────────────────
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allGroupMembers, key = { it.memberDeviceId }) { member ->
                        GroupMemberBubble(
                            member = member,
                            isOnline = isMemberOnline(member.memberDeviceId),
                            theme = theme
                        )
                    }
                }
            } else {
                // ── 1-1 / CONTROLLING STATUS BOX ─────────────────────────────
                SafeGlassBox(
                    shape = RoundedCornerShape(22.dp),
                    borderWidth = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(statusColor, CircleShape)
                        )
                        Column {
                            Text(
                                text = statusLabel,
                                color = Color(0xFF1A237E),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = lastSeenText,
                                color = Color(0xFF455A64),
                                fontSize = 11.sp
                            )
                        }
                        if (pendingActionCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "$pendingActionCount pending",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = Color(0xFF8B5CF6),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Partner Music Now Playing
            val nowPlaying = musicUiState.sharedNowPlaying[connection.connectionId]
            if (nowPlaying != null) {
                Spacer(modifier = Modifier.height(16.dp))
                SafeGlassBox(
                    shape = RoundedCornerShape(22.dp),
                    borderWidth = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music",
                            tint = theme.primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Listening to",
                                color = Color(0xFF455A64),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${nowPlaying.first} • ${nowPlaying.second}",
                                color = Color(0xFF1A237E),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Connection Duration & Heart
            SafeGlassBox(
                shape = RoundedCornerShape(40.dp),
                borderWidth = 2.dp
            ) {
                Surface(
                    color = theme.softColor.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(40.dp),
                    modifier = Modifier.padding(1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (connection.isGroup && allGroupMembers.isNotEmpty()) {
                            // Group: animated ring of member emojis (max 4 shown, then +N)
                            val shown = allGroupMembers.take(4)
                            val extra = allGroupMembers.size - shown.size
                            Row(
                                horizontalArrangement = Arrangement.spacedBy((-10).dp),
                                modifier = Modifier.scale(pulseScale)
                            ) {
                                shown.forEach { member ->
                                    Surface(
                                        color = theme.primaryColor.copy(alpha = 0.15f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = member.memberEmoji.ifEmpty { "👤" },
                                                fontSize = 26.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                                if (extra > 0) {
                                    Surface(
                                        color = theme.accentColor.copy(alpha = 0.25f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "+$extra",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = theme.primaryColor,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = theme.centerIconEmoji,
                                fontSize = 48.sp,
                                modifier = Modifier.scale(pulseScale)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = theme.timerLabel,
                            fontSize = 14.sp,
                            color = Color.Black.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = durationText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.primaryColor,
                            textAlign = TextAlign.Center
                        )

                        // --- Anniversary Countdown ---
                        if (anniversaryText != null) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = theme.anniversaryLabel,
                                fontSize = 14.sp,
                                color = Color.Black.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = anniversaryText!!,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD4AF37), // Gold
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Text(
                            text = theme.centerCardLabel,
                            fontSize = 12.sp,
                            color = theme.primaryColor.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Action Cards
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassActionCard(
                    title = theme.doodleCardTitle,
                    subtitle = theme.doodleCardSubtitle,
                    icon = Icons.Default.Brush,
                    onClick = { if (canSendInteractiveActions) onTapToDraw() },
                    modifier = Modifier.weight(1f)
                )
                
                GlassActionCard(
                    title = "Chat",
                    subtitle = "Messages",
                    icon = Icons.Default.Email,
                    onClick = {
                        val intent = android.content.Intent(context, com.aman.gigi.ui.chat.ChatBubbleActivity::class.java).apply {
                            putExtra("CONNECTION_ID", connection.connectionId)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                )

                GlassActionCard(
                    title = theme.sparkleCardTitle,
                    subtitle = theme.sparkleCardSubtitle,
                    icon = Icons.Default.PhotoCamera,
                    onClick = { if (canSendInteractiveActions) viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.SPARKLE) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Poke has been removed from the app.

        // Back Button (Rendered on top)
        val showBackButton = isCreator || !connection.role.equals(com.aman.gigi.model.ConnectionRole.PARTNER.name, ignoreCase = true)
        if (showBackButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                TextButton(onClick = onBack) {
                    Text("< Back to Partners", color = theme.primaryColor)
                }
            }
        }
        
        // Settings Button (Rendered on top)
        var showSettings by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Session Settings",
                    tint = Color(0xFF8B5CF6).copy(alpha = 0.7f)
                )
            }
        }
        
        if (showSettings) {
            SessionSettingsDialog(
                onDismiss = { showSettings = false },
                onDisconnect = onDisconnect,
                connection = connection,
                viewModel = viewModel
            )
        }
        if (showThemePicker) {
            RelationshipTypePicker(
                currentType = com.aman.gigi.model.RelationshipType.fromString(connection.relationshipType),
                onTypeSelected = { type ->
                    viewModel.setRelationshipType(type)
                    showThemePicker = false
                },
                onDismiss = { showThemePicker = false }
            )
        }

        // History Drawer (Right Side)
        val isHistoryOpen by viewModel.isHistoryOpen.collectAsState()
        
        androidx.compose.animation.AnimatedVisibility(
            visible = isHistoryOpen,
            enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }), // From Right
            exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it })
        ) {
            com.aman.gigi.ui.screensaver.history.HistoryDrawer(
                viewModel = viewModel,
                onClose = { viewModel.setHistoryOpen(false) }
            )
        }

        // --- Replay Overlay (Historical Scribble) ---
        val replayingScribble by viewModel.replayingScribble.collectAsState()
        if (replayingScribble != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .pointerInput(Unit) { detectTapGestures { /* Block interactions */ } }
            ) {
                val serialized = remember(replayingScribble) { ScribbleSerializer.serialize(replayingScribble!!) }
                
                ScribblePlaybackComponent(
                    scribbleJson = serialized,
                    onAnimationFinished = {
                        // Keep it on screen or auto-close? 
                        // Let's keep it until user clicks close
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Header Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "History Replay",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { viewModel.stopReplay() },
                        modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

        // Notification List View
        val isViewingNotifications by viewModel.isViewingNotifications.collectAsState()
        if (isViewingNotifications && isCreator) {
            com.aman.gigi.ui.screensaver.connection.NotificationListView(
                viewModel = viewModel,
                onClose = { viewModel.setViewingNotifications(false) }
            )
        }

        // --- Live Location Map ---
        val isTrackingLocation by viewModel.isTrackingLocation.collectAsState()
        val isDirectingToNativeMaps by viewModel.isDirectingToNativeMaps.collectAsState()

        if (isDirectingToNativeMaps) {
            val lat = connection.partnerLatitude
            val lon = connection.partnerLongitude
            val context = androidx.compose.ui.platform.LocalContext.current
            
            if (lat != null && lon != null && lat != 0.0 && isCreator) {
                androidx.compose.runtime.LaunchedEffect(lat, lon) {
                    try {
                        // 📍 Launch Google Maps App with a marker at the coordinates
                        android.util.Log.i("ConnectedIdle", "📍 [MAPS-INTENT] Attempting to launch native maps for: $lat, $lon")
                        val uri = android.net.Uri.parse("geo:0,0?q=$lat,$lon")
                        val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        context.startActivity(mapIntent)
                        
                        // Close the tracking overlay after directing to maps
                        viewModel.setDirectingToNativeMaps(false)
                        android.util.Log.i("ConnectedIdle", "✅ [MAPS-INTENT] Successfully launched Google Maps app.")
                        android.widget.Toast.makeText(context, "Opening Google Maps...", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.util.Log.e("ConnectedIdle", "❌ [MAPS-INTENT] Failed to launch native app, falling back to browser", e)
                        // Fallback to browser if Maps app is not available
                        val browserUri = android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")
                        val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, browserUri)
                        context.startActivity(browserIntent)
                        viewModel.setDirectingToNativeMaps(false)
                        android.widget.Toast.makeText(context, "Opening Map in Browser...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (isCreator) {
                // Show "Waiting for location..." overlay or similar
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text("Waiting for partner's location...", color = Color.White)
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { viewModel.setDirectingToNativeMaps(false) }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        if (isTrackingLocation) {
            PartnerLocationMap(
                latitude = connection.partnerLatitude ?: 0.0,
                longitude = connection.partnerLongitude ?: 0.0,
                onClose = { viewModel.setTrackingLocation(false) }
            )
        }
    }
    }
}



/**
 * A single member bubble shown in the group member presence strip.
 * Displays the member's emoji avatar with an online/offline dot and their name.
 */
@Composable
private fun GroupMemberBubble(
    member: com.aman.gigi.model.ConnectionMember,
    isOnline: Boolean,
    theme: com.aman.gigi.model.ConnectionTheme
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            // Emoji avatar circle with frosted glass background
            Surface(
                color = theme.softColor.copy(alpha = 0.85f),
                shape = CircleShape,
                modifier = Modifier
                    .size(54.dp)
                    .border(1.5.dp, if (isOnline) Color(0xFF4CAF50).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f), CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = member.memberEmoji.ifEmpty { "👤" },
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            // Online / offline dot
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                    .offset(x = 2.dp, y = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = member.memberName.take(9),
            fontSize = 10.sp,
            color = theme.primaryColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (isOnline) "online" else "offline",
            fontSize = 9.sp,
            color = if (isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ToolCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    iconColor: Color = Color(0xFF8B5CF6),
    backgroundColor: Color = Color.White.copy(alpha = 0.5f)
) {
    Surface(
        onClick = onClick,
        color = backgroundColor,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF8B5CF6).copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black.copy(alpha = 0.5f)
                )
            }
        }
    }
}


@Composable
fun SessionSettingsDialog(
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit,
    connection: com.aman.gigi.model.Connection,
    viewModel: com.aman.gigi.viewmodel.ScreensaverViewModel = hiltViewModel()
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        SafeGlassBox(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(32.dp),
            borderWidth = 2.dp
        ) {
            Surface(
                color = Color(0xFFE6E0FF).copy(alpha = 0.7f),
                shape = RoundedCornerShape(32.dp),
                tonalElevation = 0.dp
            ) {
                val theme = remember(connection.relationshipType) {
                    com.aman.gigi.model.RelationshipType.fromString(connection.relationshipType).toTheme()
                }
                val isGroup = connection.isGroup || connection.relationshipType.equals("GROUP", ignoreCase = true)
                var showDisconnect by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header: centered title/subtitle with a small ⋯ overflow in the corner.
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = when {
                                    isGroup -> "Group Settings"
                                    theme.type == com.aman.gigi.model.RelationshipType.FRIENDSHIP -> "Friendship Settings"
                                    theme.type == com.aman.gigi.model.RelationshipType.FAMILY -> "Family Settings"
                                    else -> "Love Settings"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = theme.primaryColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isGroup) "Manage this group" else "Manage your ${theme.connectedLabel.lowercase().removeSuffix(":")} space",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(
                            onClick = { showDisconnect = !showDisconnect },
                            modifier = Modifier.align(Alignment.TopEnd).size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = theme.primaryColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Disconnect lives behind the ⋯ menu, in its own confirmation popup,
                    // so it's never an accidental tap.
                    if (showDisconnect) {
                        androidx.compose.ui.window.Dialog(onDismissRequest = { showDisconnect = false }) {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = Color(0xFFF6F2FF)
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (isGroup) "Leave this group?" else "Disconnect?",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.primaryColor
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Hold the button to confirm.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Black.copy(alpha = 0.45f)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    HoldToDisconnectButton(
                                        onDisconnect = {
                                            showDisconnect = false
                                            onDisconnect()
                                            onDismiss()
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TextButton(onClick = { showDisconnect = false }) {
                                        Text("Cancel", color = Color.Black.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Theme picker — relationship themes for 1-1, color themes for groups.
                    ConnectionThemePicker(
                        isGroup = isGroup,
                        current = com.aman.gigi.model.RelationshipType.fromString(connection.relationshipType),
                        onPick = { viewModel.setRelationshipTypeFor(connection.connectionId, it) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isGroup) {
                        // Groups: no relationship milestones — offer member management instead.
                        GroupSettingsLayer(
                            theme = theme,
                            onManageMembers = {
                                onDismiss()
                                viewModel.navigateTo(
                                    ScreensaverViewModel.ScreensaverScreen.MANAGE_GROUP,
                                    connection.connectionId
                                )
                            }
                        )
                    } else {
                        TimelineSettingsLayer(
                            connection = connection,
                            theme = theme,
                            onUpdate = { meeting, anniversary ->
                                viewModel.updateTimelineDates(connection.connectionId, meeting, anniversary)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = theme.primaryColor.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

/**
 * Theme picker for a connection. For 1-1 it selects the relationship type
 * (Romantic/Friendship/Family); for groups it selects a color theme.
 */
@Composable
fun ConnectionThemePicker(
    isGroup: Boolean,
    current: com.aman.gigi.model.RelationshipType,
    onPick: (com.aman.gigi.model.RelationshipType) -> Unit
) {
    val options: List<Triple<com.aman.gigi.model.RelationshipType, String, String>> = if (isGroup) {
        listOf(
            Triple(com.aman.gigi.model.RelationshipType.GROUP, "💜", "Purple"),
            Triple(com.aman.gigi.model.RelationshipType.ROMANTIC, "🌹", "Rose"),
            Triple(com.aman.gigi.model.RelationshipType.FRIENDSHIP, "🌊", "Ocean"),
            Triple(com.aman.gigi.model.RelationshipType.FAMILY, "🌿", "Mint")
        )
    } else {
        listOf(
            Triple(com.aman.gigi.model.RelationshipType.ROMANTIC, "💕", "Romantic"),
            Triple(com.aman.gigi.model.RelationshipType.FRIENDSHIP, "🤝", "Friends"),
            Triple(com.aman.gigi.model.RelationshipType.FAMILY, "🏠", "Family")
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (isGroup) "Group Theme" else "Relationship & Theme",
            fontWeight = FontWeight.Bold,
            color = current.toTheme().primaryColor
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (type, emoji, label) ->
                val selected = type == current
                val palette = type.toTheme()
                Surface(
                    onClick = { onPick(type) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) palette.primaryColor.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (selected) palette.primaryColor else Color.Transparent
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(emoji, fontSize = 20.sp)
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) palette.primaryColor else Color.Black.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/** Group-only settings — member management (no relationship milestones). */
@Composable
fun GroupSettingsLayer(
    theme: com.aman.gigi.model.ConnectionTheme,
    onManageMembers: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Group", fontWeight = FontWeight.Bold, color = theme.primaryColor)
        Button(
            onClick = onManageMembers,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage members & invites", fontWeight = FontWeight.Bold)
        }
        Text(
            "Rename the group, invite people, or remove members.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black.copy(alpha = 0.45f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TimelineSettingsLayer(
    connection: com.aman.gigi.model.Connection,
    theme: com.aman.gigi.model.ConnectionTheme,
    onUpdate: (Long?, Long?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Relationship-aware labels.
    val metLabel = when (theme.type) {
        com.aman.gigi.model.RelationshipType.FRIENDSHIP -> "Friends since"
        com.aman.gigi.model.RelationshipType.FAMILY -> "Together since"
        else -> "Met on"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("${theme.displayName} Milestones", fontWeight = FontWeight.Bold, color = theme.primaryColor)

        OutlinedButton(
            onClick = {
                showDatePicker(context, connection.meetingDate ?: System.currentTimeMillis()) { picked ->
                    onUpdate(picked, connection.anniversaryDate)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            val dateStr = connection.meetingDate?.let { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it)) } ?: "Set date"
            Text("$metLabel: $dateStr")
        }

        OutlinedButton(
            onClick = {
                showDatePicker(context, connection.anniversaryDate ?: System.currentTimeMillis()) { picked ->
                    onUpdate(connection.meetingDate, picked)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            val dateStr = connection.anniversaryDate?.let { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it)) } ?: "Set date"
            Text("${theme.anniversaryLabel.removeSuffix(" in")}: $dateStr")
        }
    }
}

private fun showDatePicker(context: android.content.Context, initialTime: Long, onPicked: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialTime }
    android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val result = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            onPicked(result)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

@Composable
fun HoldToDisconnectButton(
    onDisconnect: () -> Unit
) {
    var isPressing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (isPressing) tween(10000, easing = LinearEasing) else tween(300),
        label = "disconnectProgress"
    )
    
    LaunchedEffect(isPressing) {
        if (isPressing) {
            progress = 1f
            // Wait for 10 seconds
            kotlinx.coroutines.delay(10000)
            if (isPressing) {
                onDisconnect()
            }
        } else {
            progress = 0f
        }
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { _: androidx.compose.ui.geometry.Offset ->
                            isPressing = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressing = false
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Background ring
            androidx.compose.material3.CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(80.dp),
                color = Color.Red.copy(alpha = 0.8f),
                strokeWidth = 4.dp,
                trackColor = Color.Red.copy(alpha = 0.1f)
            )
            
            // Disconnect label/icon
            Surface(
                shape = CircleShape,
                color = if (isPressing) Color.Red.copy(alpha = 0.1f) else Color.Transparent,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hold to Disconnect",
                    tint = if (isPressing) Color.Red else Color.Red.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = if (isPressing) "Hold for ${((1f - animatedProgress) * 10).toInt() + 1}s..." else "Hold to Disconnect",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPressing) Color.Red else Color.Red.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun GlassActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SafeGlassBox(
        modifier = modifier
            .height(160.dp),
        shape = RoundedCornerShape(32.dp),
        borderWidth = 1.5.dp
    ) {
        Surface(
            onClick = onClick,
            color = Color.White.copy(alpha = 0.3f), // Reduced alpha to show glass effect
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF8B5CF6),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

@Composable
fun FloatingOrb(
    size: androidx.compose.ui.unit.Dp,
    color: Color,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color,
                        color.copy(alpha = 0.05f)
                    )
                )
            )
    )
}

fun formatDuration(startTime: Long): String {
    val diff = System.currentTimeMillis() - startTime
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "$days days, ${hours % 24} hours"
        hours > 0 -> "$hours hours, ${minutes % 60} mins"
        minutes > 0 -> "$minutes mins, ${seconds % 60} secs"
        else -> "$seconds secs"
    }
}

fun formatAnniversaryCountdown(targetTime: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = targetTime }
    
    // Adjust target to current or next year to make it recurring
    target.set(Calendar.YEAR, now.get(Calendar.YEAR))
    
    if (target.before(now)) {
        target.add(Calendar.YEAR, 1)
    }
    
    val diff = target.timeInMillis - now.timeInMillis
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "$days days, ${hours % 24} hrs"
        hours > 0 -> "$hours hrs, ${minutes % 60} mins"
        else -> "$minutes mins left!"
    }
}
@Composable
fun PartnerPresenceOverlay(
    popup: com.aman.gigi.viewmodel.PartnerPresencePopup?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = popup != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .zIndex(101f)
    ) {
        if (popup != null) {
            val accent = if (popup.isOnline) Color(0xFF4ADE80) else Color(0xFFFB7185)
            val surfaceTint = if (popup.isOnline) Color(0xFF166534) else Color(0xFF7F1D1D)

            SafeGlassBox(
                shape = RoundedCornerShape(24.dp),
                borderWidth = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
            ) {
                Surface(
                    color = surfaceTint.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(accent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${popup.partnerName} is ${if (popup.isOnline) "online" else "offline"}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (popup.isOnline) {
                                    "Connected and ready to sync"
                                } else {
                                    popup.lastSeenAt?.let {
                                        "Last seen ${DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}"
                                    } ?: "We'll keep reconnecting in the background"
                                },
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = if (popup.isOnline) "LIVE" else "OFFLINE",
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

    }
}

@Composable
fun QuoteOverlay(
    overlay: com.aman.gigi.viewmodel.ReceivedQuoteOverlay?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = overlay != null,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 96.dp)
            .zIndex(90f)
    ) {
        overlay?.let { quote ->
            SafeGlassBox(
                shape = RoundedCornerShape(30.dp),
                borderWidth = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFFF4D6D),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${quote.senderName} sent you a quote",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        if (quote.quote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "\"${quote.quote}\"",
                                color = Color.White.copy(alpha = 0.92f),
                                textAlign = TextAlign.Center,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tap anywhere to close",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

data class QuoteComposerResult(
    val text: String
)

@Composable
fun QuoteComposerDialog(
    onDismiss: () -> Unit,
    onSend: (QuoteComposerResult) -> Unit
) {
    val presets = listOf(
        "Thinking of you right now.",
        "You make my day softer.",
        "Missing you, but smiling.",
        "Proud to be your person."
    )
    var customQuote by rememberSaveable { mutableStateOf("") }
    val canSend = customQuote.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 28.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 360.dp),
                color = Color(0xFFE6E0FF).copy(alpha = 0.97f),
                shape = RoundedCornerShape(34.dp),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Send a sweet quote",
                        color = Color(0xFF8B5CF6),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Pick a little line for a beautiful now-playing love note.",
                        color = Color(0xFF6B7280),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        presets.forEach { preset ->
                            Surface(
                                onClick = { customQuote = preset },
                                color = Color.White.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    text = preset,
                                    color = Color(0xFF8B5CF6),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    androidx.compose.material3.OutlinedTextField(
                        value = customQuote,
                        onValueChange = { newValue -> customQuote = newValue.take(160) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom quote") },
                        minLines = 3,
                        shape = RoundedCornerShape(22.dp)
                    )


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Not now")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSend(
                                    QuoteComposerResult(
                                        text = customQuote.trim()
                                    )
                                )
                            },
                            enabled = canSend
                        ) {
                            Text("Send quote")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SweetSongBadge(
    title: String,
    modifier: Modifier = Modifier
) {
    val musicMotion = rememberInfiniteTransition(label = "quote-song-badge")
    val barOne by musicMotion.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "song-bar-1"
    )
    val barTwo by musicMotion.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(760, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "song-bar-2"
    )
    val barThree by musicMotion.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "song-bar-3"
    )

    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.18f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color(0xFFFFC6E6),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Now playing",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                modifier = Modifier.height(18.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height((12f * barOne).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFFF8BB3))
                )
                Spacer(modifier = Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height((12f * barTwo).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFA7A3FF))
                )
                Spacer(modifier = Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height((12f * barThree).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF8FE3FF))
                )
            }
        }
    }
}

@Composable
fun NotificationOverlay(
    notification: com.aman.gigi.model.RemoteNotification?,
    onDismiss: () -> Unit,
    onTap: () -> Unit
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp)
            .zIndex(100f) // Ensure it's on top of everything
    ) {
        if (notification != null) {
            SafeGlassBox(
                shape = RoundedCornerShape(24.dp),
                borderWidth = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTap() }
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon / Package Image
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                           val iconBitmap = remember(notification.iconBase64) {
                               notification.iconBase64?.let { base64 ->
                                   try {
                                       val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                       android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                   } catch (e: Exception) { null }
                               }
                           }
                           
                           if (iconBitmap != null) {
                               androidx.compose.foundation.Image(
                                   bitmap = iconBitmap,
                                   contentDescription = null,
                                   modifier = Modifier.fillMaxSize().padding(8.dp)
                               )
                           } else {
                               Icon(
                                   imageVector = Icons.Default.Notifications,
                                   contentDescription = null,
                                   tint = Color.White,
                                   modifier = Modifier.size(24.dp)
                               )
                           }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = notification.title ?: "Notification",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = notification.text ?: "",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Small "Dismiss" hint or close button
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RelationshipTypePicker(
    currentType: com.aman.gigi.model.RelationshipType,
    onTypeSelected: (com.aman.gigi.model.RelationshipType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        SafeGlassBox(
            shape = RoundedCornerShape(32.dp),
            borderWidth = 2.dp
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.9f),
                shape = RoundedCornerShape(32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Relationship Type",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B5CF6)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Choose a theme that matches your relationship. This will change the app's colors and words on both devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // GROUP is an internal type for group connections — don't show it in the picker
                    val types = com.aman.gigi.model.RelationshipType.entries
                        .filter { it != com.aman.gigi.model.RelationshipType.GROUP }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        types.forEach { type ->
                            val theme = type.toTheme()
                            val isSelected = type == currentType
                            
                            Surface(
                                onClick = { onTypeSelected(type) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) theme.softColor else Color.White,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, theme.primaryColor) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = theme.badgeEmoji, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = theme.displayName,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) theme.primaryColor else Color.Black
                                        )
                                        Text(
                                            text = when(type) {
                                                com.aman.gigi.model.RelationshipType.ROMANTIC -> "Love, anniversaries and couple goals"
                                                com.aman.gigi.model.RelationshipType.BESTIE -> "Best friends, crazy vibes and secret laughs"
                                                com.aman.gigi.model.RelationshipType.FRIENDSHIP -> "Fun, friends and shared memories"
                                                com.aman.gigi.model.RelationshipType.FAMILY -> "Family bonds and home moments"
                                                com.aman.gigi.model.RelationshipType.GROUP -> "Group sync and shared moments"
                                            },
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun PartnerLocationMap(
    latitude: Double,
    longitude: Double,
    onClose: () -> Unit
) {
    // 📍 Coordinate Validation: Only load if we have non-zero coordinates
    val isValidLocation = latitude != 0.0 && longitude != 0.0
    
    // 🌍 Robust Google Maps URL for direct WebView loading
    val mapUrl = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
    
    var isLoading by remember { mutableStateOf(true) }
    var key by remember { mutableIntStateOf(0) } 

    // 🔙 Support system back button to exit map
    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "In-App Tracker",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (isValidLocation) {
                        Text(
                            text = String.format("%.4f, %.4f", latitude, longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                
                IconButton(
                    onClick = { 
                        isLoading = true
                        key++ 
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White)
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (isValidLocation) {
                    key(key) {
                        AndroidView(
                            factory = { context ->
                                android.webkit.WebView(context).apply {
                                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.setSupportZoom(true)
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Mobile Safari/537.36"
                                    
                                    webViewClient = object : android.webkit.WebViewClient() {
                                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                        }
                                    }
                                    loadUrl(mapUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocationOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Waiting for partner's GPS...", color = Color.Gray)
                    }
                }

                if (isLoading && isValidLocation) {
                    CircularProgressIndicator(color = Color(0xFF8B5CF6))
                }
            }
        }
    }
}

@Composable
fun CuteHeartPulseLoader(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF4B8D),
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.Favorite,
            contentDescription = "Loading...",
            tint = color,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun AnimatedReconnectingCharacter(
    isConnecting: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "reconnecting")
    
    // Character bounce
    val bounceY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isConnecting) -15f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceY"
    )
    
    // Character rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )
    
    // Character scale
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .offset(y = bounceY.dp)
            .graphicsLayer {
                rotationZ = if (isConnecting) rotation else 0f
                scaleX = if (isConnecting) scale else 1f
                scaleY = if (isConnecting) scale else 1f
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "??",
            fontSize = 48.sp
        )
    }
}
