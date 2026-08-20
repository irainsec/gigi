package com.aman.gigi.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.gigi.data.client.ConnectionBootstrapManager
import com.aman.gigi.model.Connection
import com.aman.gigi.model.ConnectionRole
import com.aman.gigi.model.ConnectionState
import com.aman.gigi.model.LoveCardDeck
import com.aman.gigi.model.LoveCardDraftItem
import com.aman.gigi.model.LoveCardDraftResponse
import com.aman.gigi.model.LoveCardLocalState
import com.aman.gigi.model.LoveCardStackStatus
import com.aman.gigi.model.MemberIdentity
import com.aman.gigi.model.ServerStatus
import com.aman.gigi.repository.ConnectionRepository
import com.aman.gigi.repository.LoveCardRepository
import com.aman.gigi.repository.OutboundActionRepository
import com.aman.gigi.repository.ScribbleRepository
import com.aman.gigi.service.ScreensaverSyncService
import com.aman.gigi.service.ThemeSongPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.aman.gigi.data.sync.SyncEvent
import com.aman.gigi.R
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * ViewModel for Screensaver feature
 */
@HiltViewModel
class ScreensaverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val connectionRepository: ConnectionRepository,
    private val scribbleRepository: ScribbleRepository,
    private val outboundActionRepository: OutboundActionRepository,
    private val loveCardRepository: LoveCardRepository,
    private val chatRepository: com.aman.gigi.repository.ChatRepository,
    private val gifRepository: com.aman.gigi.repository.GifRepository,
    val syncManager: com.aman.gigi.data.sync.ScribbleSyncManager,
    private val bootstrapManager: ConnectionBootstrapManager,
    private val themeSongPlayer: ThemeSongPlayer,
    private val httpUploader: com.aman.gigi.network.HttpUploader,
    private val locationProvider: com.aman.gigi.data.location.LocationProvider,
    val breakCardDao: com.aman.gigi.data.dao.BreakCardDao
) : ViewModel() {
    
    // Sparkle Send Status
    private val _sparkleSendStatus = MutableStateFlow(SendStatus.IDLE)
    val sparkleSendStatus: StateFlow<SendStatus> = _sparkleSendStatus.asStateFlow()
    
    private var pendingSparkleId: String? = null
    
    // Partner state flows (Move to top to avoid NPE in init)
    // Navigation state
    private val _currentScreen = MutableStateFlow(ScreensaverScreen.LIST)
    val currentScreen: StateFlow<ScreensaverScreen> = _currentScreen.asStateFlow()
    
    private val _partnerConnectionId = MutableStateFlow<String?>(null)
    val partnerConnectionId: StateFlow<String?> = _partnerConnectionId.asStateFlow()

    private val _partnerDisconnected = MutableStateFlow<String?>(null)
    val partnerDisconnected: StateFlow<String?> = _partnerDisconnected.asStateFlow()
    
    private val _sendFailedPartnerName = MutableStateFlow<String?>(null)
    val sendFailedPartnerName: StateFlow<String?> = _sendFailedPartnerName.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _partnerPresencePopup = MutableStateFlow<PartnerPresencePopup?>(null)
    val partnerPresencePopup: StateFlow<PartnerPresencePopup?> = _partnerPresencePopup.asStateFlow()
    private var partnerPresenceDismissJob: kotlinx.coroutines.Job? = null
    private val lastPartnerPresenceStates = mutableMapOf<String, Boolean>()
    private val lastShownPartnerPresenceStates = mutableMapOf<String, Boolean>()


    private val _quoteOverlay = MutableStateFlow<ReceivedQuoteOverlay?>(null)
    val quoteOverlay: StateFlow<ReceivedQuoteOverlay?> = _quoteOverlay.asStateFlow()
    private var quoteDismissJob: kotlinx.coroutines.Job? = null
    // Card Composer State
    private val _isComposerMode = MutableStateFlow(false)
    val isComposerMode: StateFlow<Boolean> = _isComposerMode.asStateFlow()
    
    // Music Compatibility
    private val _musicCompatibilityScore = MutableStateFlow<Map<String, Int>>(emptyMap())
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val musicCompatibilityScore: StateFlow<Int?> = _partnerConnectionId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else _musicCompatibilityScore.map { it[id] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setComposerMode(enabled: Boolean) {
        _isComposerMode.value = enabled
    }

    private val _activeLoveCardDeckId = MutableStateFlow<String?>(null)

    // Set when the server rejects a connection with CONNECTION_LIMIT_REACHED;
    // the UI shows the upgrade sheet while this is non-null.
    private val _connectionLimitMessage = MutableStateFlow<String?>(null)
    val connectionLimitMessage: StateFlow<String?> = _connectionLimitMessage.asStateFlow()

    fun dismissConnectionLimitSheet() {
        _connectionLimitMessage.value = null
    }

    /**
     * Group connections are a paid feature. Returns true when the member's plan
     * allows them; otherwise opens the upgrade sheet and returns false.
     */
    fun requireGroupFeatureOrUpgrade(): Boolean {
        if (com.aman.gigi.utils.AppConfig.userPlan.features.groupConnections) return true
        _connectionLimitMessage.value = "Group connections are a Gigi Plus feature. Upgrade to create groups."
        return false
    }

    // ─────────────────────────── Chat ───────────────────────────
    // The connection whose chat sheet is open (null = closed).
    private val _openChatConnectionId = MutableStateFlow<String?>(null)
    val openChatConnectionId: StateFlow<String?> = _openChatConnectionId.asStateFlow()

    fun openChat(connectionId: String) { _openChatConnectionId.value = connectionId }
    fun closeChat() { _openChatConnectionId.value = null }

    // Connections sheet (list of connections + create group), opened from Sweet Corner nav.
    private val _showConnectionsSheet = MutableStateFlow(false)
    val showConnectionsSheet: StateFlow<Boolean> = _showConnectionsSheet.asStateFlow()
    fun openConnectionsSheet() { _showConnectionsSheet.value = true }
    fun closeConnectionsSheet() { _showConnectionsSheet.value = false }

    // Toast-style message shown after being auto-added to a group.
    private val _groupInviteMessage = MutableStateFlow<String?>(null)
    val groupInviteMessage: StateFlow<String?> = _groupInviteMessage.asStateFlow()
    fun clearGroupInviteMessage() { _groupInviteMessage.value = null }

    fun inviteConnectionsToGroup(groupConnectionId: String, groupName: String, connectionIds: List<String>) {
        if (connectionIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val serverOk = bootstrapManager.addGroupMembersOnServer(groupConnectionId, connectionIds)
            connectionIds.forEach { via ->
                syncManager.sendGroupInvite(via, groupConnectionId, groupName)
            }
            _groupInviteMessage.value = if (serverOk) {
                "Invites sent! 👯"
            } else {
                "Invites sent locally, they will sync when you're online 📡"
            }
        }
    }

    /**
     * WhatsApp-style group creation: makes a new group connection and invites each
     * selected existing connection's partner to auto-join it.
     */
    fun createGroupFromConnections(groupName: String, connectionIds: List<String>, groupEmoji: String? = null, groupId: String) {
        if (!requireGroupFeatureOrUpgrade()) return
        val code = groupId
        val name = groupName.ifBlank { "Our Group" }
        // Local pick so the galaxy planet shows the emoji instantly.
        if (!groupEmoji.isNullOrBlank()) {
            context.getSharedPreferences("galaxy_orbits", Context.MODE_PRIVATE)
                .edit().putString("emoji_$code", groupEmoji).apply()
        }
        viewModelScope.launch(Dispatchers.IO) {
            // SERVER-FIRST: create session + emoji + members atomically over HTTP, so
            // nothing depends on WS registration timing (which used to 404 the follow-ups).
            val serverOk = bootstrapManager.createGroupOnServer(code, name, groupEmoji, connectionIds)
            // Local row after (or regardless — the WS create resumes the existing session).
            createConnection(
                connectionId = code,
                partnerName = name,
                partnerDeviceId = "waiting...",
                connectionCode = code,
                relationshipType = "GROUP",
                isGroup = true
            )
            if (!groupEmoji.isNullOrBlank()) {
                // Mirror onto the local row so the detail/settings pages show it at once.
                // createConnection inserts asynchronously, so retry briefly until the row lands.
                for (attempt in 0 until 5) {
                    val row = connectionRepository.getAllConnectionsOnce().find { it.connectionId == code }
                    if (row != null) {
                        connectionRepository.updateConnection(row.copy(partnerEmojiUrl = groupEmoji))
                        break
                    }
                    kotlinx.coroutines.delay(200)
                }
            }
            // Live notification to online partners (membership is already on the server).
            connectionIds.forEach { via ->
                syncManager.sendGroupInvite(via, code, name)
            }
            _groupInviteMessage.value =
                if (serverOk) "Group \"$name\" created — members added 💌"
                else "Group \"$name\" created — members will sync when you're online 💫"
            // Pull the authoritative roster (members[]) onto this device.
            kotlinx.coroutines.delay(1000)
            bootstrapManager.onAppForegrounded()
        }
    }

    // ── Twigi (layered avatar) ────────────────────────────────────────────────
    private val _twigiSaving = MutableStateFlow(false)
    val twigiSaving: StateFlow<Boolean> = _twigiSaving

    /** Saves the Twigi config locally (works offline) and syncs to server when online. */
    fun saveTwigi(configJson: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _twigiSaving.value = true
            try {
                // Try online save
                val serverResult = bootstrapManager.saveTwigiOnServer(mode = "TWIGI", configJson = configJson)
                if (serverResult == null) {
                    // Offline fallback: save locally to memberIdentity & SharedPreferences
                    val prefs = context.getSharedPreferences("gigi_twigi_offline_sync", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putString("pending_twigi_config", configJson).apply()
                    
                    val identity = memberIdentity.value
                    if (identity != null) {
                        val updated = identity.copy(
                            avatarMode = "TWIGI",
                            twigiConfigJson = configJson
                        )
                        bootstrapManager.saveIdentity(updated)
                    }
                } else {
                    val prefs = context.getSharedPreferences("gigi_twigi_offline_sync", android.content.Context.MODE_PRIVATE)
                    prefs.edit().remove("pending_twigi_config").apply()
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverVM", "Twigi offline save error", e)
            } finally {
                _twigiSaving.value = false
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(true) }
        }
    }

    /** Switches which identity partners see (EMOJI or TWIGI); both are always kept. */
    fun setAvatarMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            bootstrapManager.saveTwigiOnServer(mode = mode)
            // Re-broadcast so live partners refresh immediately (emoji case).
            broadcastMyProfile()
        }
    }

    /** Set the user's own profile animated emoji. */
    fun setProfileEmoji(emojiUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Persist locally for immediate feedback in Galaxy View
            context.getSharedPreferences("galaxy_orbits", Context.MODE_PRIVATE)
                .edit().putString("emoji_self", emojiUrl).apply()
            
            // Also save to server (can use saveTwigiOnServer with EMOJI mode just to update identity, 
            // though assuming bootstrapManager handles it or just broadcasting is enough for live partners)
            // For now, let's just broadcast it to live partners.
            broadcastMyProfile()
        }
    }

    /** Set a group's shared animated emoji (from the galaxy picker) — local + server. */
    fun setGroupEmoji(connectionId: String, emojiUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            connectionRepository.getAllConnectionsOnce()
                .find { it.connectionId == connectionId }
                ?.let { connectionRepository.updateConnection(it.copy(partnerEmojiUrl = emojiUrl)) }
            bootstrapManager.setGroupEmojiOnServer(connectionId, emojiUrl)
        }
    }

    fun chatMessages(connectionId: String): Flow<List<com.aman.gigi.model.ChatMessage>> =
        chatRepository.messagesFor(connectionId)

    /** Device connectivity — drives the "you're offline" banner. */
    val isDeviceOnline: StateFlow<Boolean> = syncManager.networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ── Now Playing (live, ephemeral) ─────────────────────────────────────────
    /** connectionId → the track that person is playing right now. */
    val nowPlayingByConnection = syncManager.nowPlayingTracker.others
    val myNowPlaying = syncManager.nowPlayingTracker.mine

    init { syncManager.startNowPlayingSharing() }

    // ── Break cards (live, ephemeral) ─────────────────────────────────────────
    val activeBreak = syncManager.activeBreak
    val breakResponses = syncManager.breakResponses
    val myBreakAnswer = syncManager.myBreakAnswer

    fun callBreak(connectionId: String, cardId: String) = syncManager.sendBreakInvite(connectionId, cardId)
    fun answerBreak(accepted: Boolean) = syncManager.sendBreakResponse(accepted)
    fun dismissBreak() = syncManager.dismissBreak()

    fun sendChat(connectionId: String, text: String, gifUrl: String = "") {
        val t = text.trim()
        if (t.isEmpty() && gifUrl.isBlank()) return
        syncManager.sendChatMessage(connectionId, t, gifUrl)
        if (gifUrl.isNotBlank()) viewModelScope.launch { gifRepository.addRecentGif(gifUrl) }
    }

    // Connection State
    val connectionState: StateFlow<ConnectionState> = connectionRepository.getConnectionState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    val serverStatus: StateFlow<ServerStatus> = bootstrapManager.serverStatus
    val memberIdentity: StateFlow<MemberIdentity?> = bootstrapManager.memberIdentity
    val isAuthBusy: StateFlow<Boolean> = bootstrapManager.isAuthBusy
    val authError: StateFlow<String?> = bootstrapManager.authError
    val pendingPhoneNumber: StateFlow<String?> = bootstrapManager.pendingPhoneNumber
    val devOtpHint: StateFlow<String?> = bootstrapManager.devOtpHint

    @OptIn(ExperimentalCoroutinesApi::class)
    val isPartnerTyping: StateFlow<Boolean> = _partnerConnectionId.flatMapLatest { id ->
        if (id == null) flowOf(false)
        else syncManager.isPartnerTyping.map { it[id] == true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val isPartnerDrawing: StateFlow<Boolean> = _partnerConnectionId.flatMapLatest { id ->
        if (id == null) flowOf(false)
        else syncManager.isPartnerDrawing.map { it[id] == true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** My current profile emoji (animated asset/URL) from galaxy prefs.
     *  Seeds a random cute default on first run so new users aren't all the sun. */
    private fun myProfileEmoji(): String {
        val prefs = context.getSharedPreferences("galaxy_orbits", Context.MODE_PRIVATE)
        prefs.getString("emoji_self", null)?.let { return it }
        val pool = listOf(
            "file:///android_asset/galaxy/emoji/sun_with_face.png",
            "file:///android_asset/galaxy/emoji/heart_eyes.png",
            "file:///android_asset/galaxy/emoji/partying_face.png",
            "file:///android_asset/galaxy/emoji/ringed_planet.png"
        )
        val pick = pool[(System.nanoTime() % pool.size).toInt()]
        prefs.edit().putString("emoji_self", pick).apply()
        return pick
    }

    /** Broadcasts my avatar/emoji/name to every connected partner so their view updates live. */
    fun broadcastMyProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            val id = memberIdentity.value
            val avatar = id?.avatarUrl ?: ""
            val name = id?.displayName ?: ""
            val emoji = myProfileEmoji()
            connectionRepository.getAllActiveConnectionsOnce().forEach { conn ->
                syncManager.sendProfileUpdate(conn.connectionId, avatar, emoji, name)
            }
        }
    }

    init {
        android.util.Log.i("ScreensaverVM", "🚀 [GIGI-FIX-V3] ScreensaverViewModel Initialized")
        myProfileEmoji() // seed a random default profile emoji on first run
        cleanupExpiredInvites()
        // Re-broadcast my profile to partners whenever my avatar or name changes.
        viewModelScope.launch {
            memberIdentity
                .map { (it?.avatarUrl ?: "") to (it?.displayName ?: "") }
                .distinctUntilChanged()
                .collect { broadcastMyProfile() }
        }
        // Observe sync events
        viewModelScope.launch {
            memberIdentity.collect { identity ->
                if (identity != null) {
                    loadNebulaMotes()
                }
            }
        }
        viewModelScope.launch {
            syncManager.events.collect { event: com.aman.gigi.data.sync.SyncEvent ->
                when (event) {
                    is com.aman.gigi.data.sync.SyncEvent.SharedAlbumReceived -> {
                        // Handled dynamically in MusicViewModel
                    }
                    is com.aman.gigi.data.sync.SyncEvent.PartnerDisconnected -> {
                        lastPartnerPresenceStates[event.connectionId] = false
                        // Safety guard: never show "Partner Disconnected" dialog for group
                        // connections — members joining/leaving is normal for groups.
                        // (Exception: COMMAND_MEMBER_REMOVED deletes the connection first,
                        // so conn will be null here and the dialog correctly fires.)
                        viewModelScope.launch {
                            val conn = connectionRepository.getAllActiveConnectionsOnce()
                                .find { it.connectionId == event.connectionId }
                            if (conn?.isGroup != true) {
                                onPartnerDisconnected(event.partnerName)
                            }
                        }
                    }
                    is SyncEvent.PartnerPresenceChanged -> {
                        handlePartnerPresenceChanged(event)
                    }
                    is SyncEvent.SessionExpired -> {
                        android.util.Log.w("ScreensaverVM", "Session expired for ${event.connectionId}. Removing connection from local DB.")
                        viewModelScope.launch {
                            // Delete the connection so the ghost partner disappears
                            connectionRepository.deleteConnection(event.connectionId)
                        }
                        if (_currentScreen.value == ScreensaverScreen.PARTNER_SESSIONS &&
                            _partnerConnectionId.value == event.connectionId) {
                            _currentScreen.value = ScreensaverScreen.LIST
                        }
                    }
                    is SyncEvent.ForceLogout -> {
                        android.util.Log.w("ScreensaverVM", "🔴 Force logout received: ${event.reason}")
                        bootstrapManager.signOut()
                    }
                    is com.aman.gigi.data.sync.SyncEvent.GroupInviteReceived -> {
                        android.util.Log.i("ScreensaverVM", "👥 Group invite: ${event.groupName} (${event.groupCode})")
                        viewModelScope.launch {
                            // Auto-join the group if we're not already in it.
                            val already = connectionRepository.getAllActiveConnectionsOnce()
                                .any { it.connectionId.equals(event.groupCode, ignoreCase = true) }
                            if (!already) {
                                joinConnection(event.groupCode)
                                _groupInviteMessage.value = "${event.inviterName} added you to \"${event.groupName}\" 💜"
                            }
                        }
                    }
                    is com.aman.gigi.data.sync.SyncEvent.ConnectionLimitReached -> {
                        android.util.Log.w("ScreensaverVM", "🚫 Connection limit reached for ${event.connectionId}")
                        viewModelScope.launch {
                            // The server refused the connection — remove the local pending
                            // record so the UI doesn't sit in "Connecting…" forever.
                            connectionRepository.deleteConnection(event.connectionId)
                            _connectionLimitMessage.value = event.message
                            if (_currentScreen.value == ScreensaverScreen.PARTNER_SESSIONS &&
                                _partnerConnectionId.value == event.connectionId) {
                                _currentScreen.value = ScreensaverScreen.LIST
                            }
                        }
                    }
                    is com.aman.gigi.data.sync.SyncEvent.ConnectionRemoved -> {
                        android.util.Log.w("ScreensaverVM", "🗑️ Connection removed by admin: ${event.connectionCode}")
                        viewModelScope.launch {
                            // Archive just this one connection — do NOT sign out the whole account
                            val conn = connectionRepository.getAllActiveConnectionsOnce()
                                .find { it.connectionId == event.connectionCode }
                            if (conn != null) {
                                connectionRepository.deleteConnection(conn.connectionId)
                                android.util.Log.i("ScreensaverVM", "✅ Archived removed connection: ${conn.connectionId}")
                            }
                        }
                    }
                    is com.aman.gigi.data.sync.SyncEvent.SendFailed -> {
                        viewModelScope.launch {
                            val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == event.connectionId }
                            _sendFailedPartnerName.value = conn?.partnerName ?: "Partner"
                            _lastErrorMessage.value = event.reason
                            
                            // Check if it was our sparkle
                            if (_sparkleSendStatus.value == SendStatus.SENDING) {
                                _sparkleSendStatus.value = SendStatus.ERROR
                            }
                        }
                    }
                    is com.aman.gigi.data.sync.SyncEvent.SendSuccess -> {
                        android.util.Log.i("ScreensaverViewModel", "✅ Received SendSuccess event for ${event.scribbleId}")
                        // Check if this matches our pending Sparkle
                        if (pendingSparkleId != null) {
                            if (event.scribbleId == pendingSparkleId) {
                                android.util.Log.i("ScreensaverViewModel", "🎉 MATCH! Updating status to SENT")
                                _sparkleSendStatus.value = SendStatus.SENT
                                pendingSparkleId = null
                            } else {
                                android.util.Log.w("ScreensaverViewModel", "⚠️ Mismatched ID. Pending: $pendingSparkleId, Received: ${event.scribbleId}")
                            }
                        }
                    }
                    is SyncEvent.QuoteReceived -> {
                        showQuoteOverlay(
                            connectionId = event.connectionId,
                            quote = event.text,
                            senderName = event.senderName
                        )
                    }
                    is SyncEvent.AlarmDoneTogether -> {
                        showQuoteOverlay(
                            connectionId = event.connectionId,
                            quote = "${event.alarmTitle}: ${event.text}",
                            senderName = "Partner" // Will be replaced by UI using connectionId
                        )
                        // Play jingle
                        com.aman.gigi.ui.CardSoundEngine.init(context)
                        com.aman.gigi.ui.CardSoundEngine.playReveal()
                    }
                    is SyncEvent.LoveCardStackReceived -> {
                        _activeLoveCardDeckId.value = event.stackId
                    }
                    is SyncEvent.LoveCardStackAnswered -> {
                        _activeLoveCardDeckId.value = event.stackId
                    }
                    is com.aman.gigi.data.sync.SyncEvent.PresenceUpdate -> {
                        // Clear offline errors if we receive ANY activity from partner
                        if (_sendFailedPartnerName.value != null || _partnerDisconnected.value != null) {
                            android.util.Log.i("ScreensaverViewModel", "📡 Partner activity received, clearing offline error states.")
                            _sendFailedPartnerName.value = null
                            _partnerDisconnected.value = null
                        }
                    }
                    is SyncEvent.Connected -> {
                        android.util.Log.i("ScreensaverVM", "Γ£ì Connected to ${event.connectionId}")
                    }
                    is SyncEvent.Connecting -> {
                        android.util.Log.i("ScreensaverVM", "≡ƒôí Connecting to ${event.connectionId}...")
                    }
                    is SyncEvent.Reconnecting -> {
                        android.util.Log.i("ScreensaverVM", "≡ƒöä Reconnecting to ${event.connectionId}: ${event.reason}")
                    }
                    is SyncEvent.IdleTimeout -> {
                        android.util.Log.i("ScreensaverVM", "ΓÅ▒ Idle timeout for ${event.connectionId} after ${event.idleMinutes} mins")
                    }
                    is SyncEvent.PartnerProfileUpdated -> {
                        android.util.Log.i("ScreensaverVM", "👤 Partner profile updated: ${event.partnerName}")
                    }
                    is SyncEvent.MusicCompatibilityReceived -> {
                        val newScore = event.score ?: 0
                        _musicCompatibilityScore.value = _musicCompatibilityScore.value + (event.connectionId to newScore)
                    }
                    else -> {
                        // Unhandled sync events
                    }
                }
            }
        }

        // Auto-start sync service if we already have an active connection
        viewModelScope.launch {
            val connections = connectionRepository.getAllActiveConnectionsOnce()
            if (connections.isNotEmpty()) {
                android.util.Log.i("ScreensaverVM", "🚀 [STARTUP] Found ${connections.size} existing connections, starting sync service...")
                startSyncService()
            }
        }
    }

    fun resetSparkleStatus() {
        _sparkleSendStatus.value = SendStatus.IDLE
        pendingSparkleId = null
    }
    
    // UI Screens
    enum class ScreensaverScreen {
        LIST,
        CREATE,
        CREATE_GROUP,
        JOIN,
        MANAGE_GROUP,
        PARTNER_SESSIONS,
        SPARKLE, // New Camera Feature
        SETTINGS // Global Settings
    }
    
    // Navigation state
    // (Moved to top of class to fix initialization order NPE)    
    // All active connections (filtering out unjoined placeholders and expired invites >30 mins)
    val activeConnections: StateFlow<List<Connection>> = connectionRepository.getActiveConnections()
        .combine(memberIdentity) { list, _ ->
            val now = System.currentTimeMillis()
            val thirtyMinsMs = 30 * 60 * 1000L

            list.filter { conn ->
                val pId = conn.partnerDeviceId
                val isPlaceholder = pId.isBlank() || pId == "waiting..." || pId == "joining..."
                val isExpired = isPlaceholder && (now - conn.createdAt > thirtyMinsMs)

                !isPlaceholder && !isExpired
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Galaxy camera (zoom/rotation/tilt) — held here so it survives tab switches.
    val galaxyCamera = com.aman.gigi.ui.GalaxyCamera()

    // Members of the currently-opened connection (for group "Sweet Corner" member cards).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedConnectionMembers: StateFlow<List<com.aman.gigi.model.ConnectionMember>> = _partnerConnectionId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList())
            else connectionRepository.getMembersForConnectionFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live member count per group connection — used to draw a group's "moons" in the galaxy.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val groupMemberCounts: StateFlow<Map<String, Int>> = activeConnections
        .flatMapLatest { conns ->
            val groups = conns.filter { it.isGroup || it.relationshipType.equals("GROUP", ignoreCase = true) }
            if (groups.isEmpty()) flowOf(emptyMap())
            else combine(
                groups.map { g ->
                    connectionRepository.getMembersForConnectionFlow(g.connectionId)
                        .map { g.connectionId to it.size }
                }
            ) { pairs -> pairs.toMap() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Per-group member profile-emoji URLs (index-aligned to the roster) — each becomes a
    // moon orbiting the group in the galaxy; updates when a member changes their emoji.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val groupMemberEmojis: StateFlow<Map<String, List<String?>>> = activeConnections
        .flatMapLatest { conns ->
            val groups = conns.filter { it.isGroup || it.relationshipType.equals("GROUP", ignoreCase = true) }
            if (groups.isEmpty()) flowOf(emptyMap())
            else combine(
                groups.map { g ->
                    connectionRepository.getMembersForConnectionFlow(g.connectionId)
                        .map { members -> g.connectionId to members.map { it.emojiUrl } }
                }
            ) { pairs -> pairs.toMap() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val groupMembersMap: StateFlow<Map<String, List<com.aman.gigi.model.ConnectionMember>>> = activeConnections
        .flatMapLatest { conns ->
            val groups = conns.filter { it.isGroup || it.relationshipType.equals("GROUP", ignoreCase = true) }
            if (groups.isEmpty()) flowOf(emptyMap())
            else combine(
                groups.map { g ->
                    connectionRepository.getMembersForConnectionFlow(g.connectionId)
                        .map { members -> g.connectionId to members }
                }
            ) { pairs -> pairs.toMap() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val selectedSweetConnectionId: StateFlow<String?> = bootstrapManager.selectedSweetConnectionId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val loveCardDecks: StateFlow<List<LoveCardDeck>> = loveCardRepository.observeDecks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectConnection(connection: Connection) {
        _partnerConnectionId.value = connection.connectionId
        _currentScreen.value = ScreensaverScreen.PARTNER_SESSIONS
        
        // Ensure sync manager is active for this connection
        startSyncService()
    }

    val activeLoveCardDeck: StateFlow<LoveCardDeck?> = combine(
        loveCardDecks,
        _activeLoveCardDeckId
    ) { decks, activeId ->
        activeId?.let { id -> decks.firstOrNull { it.stack.stackId == id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    // Connection state for the "currently being added" connection
    private val _pairingState = MutableStateFlow<ConnectionState>(ConnectionState.NOT_CONNECTED)
    val pairingState: StateFlow<ConnectionState> = _pairingState.asStateFlow()

    // Currently selected connection for PARTNER_SESSIONS screen
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedConnection: StateFlow<Connection?> = _partnerConnectionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else connectionRepository.getConnectionById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedConnectionState: StateFlow<ConnectionState> = selectedConnection
        .map(::toConnectionState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.NOT_CONNECTED)

    // Role Check: Am I the creator of the currently active connection?
    val isCreator: StateFlow<Boolean> = selectedConnection.map { conn ->
        when {
            conn == null -> false
            conn.role.equals(ConnectionRole.CREATOR.name, ignoreCase = true) -> true
            else -> conn.creatorDeviceId?.equals(syncManager.deviceId, ignoreCase = true) == true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingActionCount: StateFlow<Int> = _partnerConnectionId
        .flatMapLatest { connectionId ->
            if (connectionId == null) flowOf(0)
            else outboundActionRepository.observeActiveActionCount(connectionId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    // Targeted group member for partner controls (creator picks one member from the group)
    private val _targetGroupMemberDeviceId = MutableStateFlow<String?>(null)
    val targetGroupMemberDeviceId: StateFlow<String?> = _targetGroupMemberDeviceId.asStateFlow()

    private val _targetGroupMemberName = MutableStateFlow<String?>(null)
    val targetGroupMemberName: StateFlow<String?> = _targetGroupMemberName.asStateFlow()

    private val _targetGroupMemberEmoji = MutableStateFlow<String?>(null)
    val targetGroupMemberEmoji: StateFlow<String?> = _targetGroupMemberEmoji.asStateFlow()

    fun setTargetGroupMember(deviceId: String, name: String, emoji: String) {
        _targetGroupMemberDeviceId.value = deviceId
        _targetGroupMemberName.value = name
        _targetGroupMemberEmoji.value = emoji
    }

    /** Clears the group target (called when entering a group session fresh). */
    fun clearTargetGroupMember() {
        _targetGroupMemberDeviceId.value = null
        _targetGroupMemberName.value = null
        _targetGroupMemberEmoji.value = null
    }

    /** Returns the effective target device ID for remote commands.
     *  For group connections, uses the creator-selected member.
     *  For 1-to-1 connections, uses the single partnerDeviceId. */
    private fun effectiveTargetId(conn: com.aman.gigi.model.Connection): String? =
        if (conn.isGroup) _targetGroupMemberDeviceId.value else conn.partnerDeviceId

    /** Returns all group members except the current device (for the member picker). */
    suspend fun getGroupMembers(connectionId: String): List<com.aman.gigi.model.ConnectionMember> =
        connectionRepository.getMembersForConnection(connectionId)
            .filter { it.memberDeviceId != syncManager.deviceId }

    /** Returns ALL group members (including self) as a live Flow — for the group connected screen. */
    fun getGroupMembersFlow(connectionId: String): kotlinx.coroutines.flow.Flow<List<com.aman.gigi.model.ConnectionMember>> =
        connectionRepository.getMembersForConnectionFlow(connectionId)

    /** The current device's ID — used by UI to mark self as online in the group member strip. */
    val currentDeviceId: String get() = syncManager.deviceId

    // Remote Notifications
    private val _remoteNotifications = MutableStateFlow<List<com.aman.gigi.model.RemoteNotification>>(emptyList())
    val remoteNotifications: StateFlow<List<com.aman.gigi.model.RemoteNotification>> = _remoteNotifications.asStateFlow()

    fun getNotificationsForPartner(connectionId: String?): Flow<List<com.aman.gigi.model.RemoteNotification>> {
        return _remoteNotifications.map { list -> list.filter { it.connectionId == connectionId } }
    }

    fun deleteNotification(id: String) {
        _remoteNotifications.value = _remoteNotifications.value.filter { it.id != id }
    }
    
    fun clearAllNotifications() {
        _remoteNotifications.value = emptyList()
        try { syncManager.clearAllSystemNotifications() } catch (_: Exception) {}
    }

    private fun handlePartnerPresenceChanged(event: SyncEvent.PartnerPresenceChanged) {
        lastPartnerPresenceStates[event.connectionId] = event.isOnline

        if (event.isOnline) {
            if (_sendFailedPartnerName.value != null || _partnerDisconnected.value != null) {
                android.util.Log.i("ScreensaverViewModel", "💚 Partner is back online, clearing offline warnings.")
                _sendFailedPartnerName.value = null
                _partnerDisconnected.value = null
            }
        }

        val lastShownState = lastShownPartnerPresenceStates[event.connectionId]
        val shouldShowPopup = event.showPopup && (lastShownState == null || lastShownState != event.isOnline)
        if (shouldShowPopup) {
            lastShownPartnerPresenceStates[event.connectionId] = event.isOnline
            showPartnerPresencePopup(event.connectionId, event.partnerName, event.isOnline, event.lastSeenAt)
        }
    }

    private fun toConnectionState(connection: Connection?): ConnectionState {
        if (connection == null) {
            return ConnectionState.NOT_CONNECTED
        }

        if (!connection.isActive) {
            return ConnectionState.DISCONNECTED
        }

        val isPairing = connection.partnerDeviceId == "waiting..." || connection.partnerDeviceId == "joining..."
        val transportState = runCatching {
            com.aman.gigi.model.TransportState.valueOf(connection.transportState)
        }.getOrElse {
            com.aman.gigi.model.TransportState.CONNECTING
        }
        val partnerPresence = runCatching {
            com.aman.gigi.model.PartnerPresence.valueOf(connection.partnerPresence)
        }.getOrElse {
            com.aman.gigi.model.PartnerPresence.UNKNOWN
        }

        return when (transportState) {
            com.aman.gigi.model.TransportState.NO_INTERNET ->
                if (isPairing) ConnectionState.CONNECTING else ConnectionState.NO_INTERNET
            com.aman.gigi.model.TransportState.CONNECTING ->
                if (!isPairing && partnerPresence == com.aman.gigi.model.PartnerPresence.ONLINE) {
                    ConnectionState.CONNECTED
                } else {
                    ConnectionState.CONNECTING
                }
            // Once the WebSocket handshake is established we ARE connected to the server.
            // Waiting on an offline partner (isPairing / partnerDeviceId == "waiting...") is a
            // partner-presence state, not a transport state — don't mislabel it as
            // "Reconnecting to server...".
            com.aman.gigi.model.TransportState.CONNECTED ->
                ConnectionState.CONNECTED
        }
    }

    private fun showPartnerPresencePopup(connectionId: String, partnerName: String, isOnline: Boolean, lastSeenAt: Long?) {
        partnerPresenceDismissJob?.cancel()
        _partnerPresencePopup.value = PartnerPresencePopup(
            connectionId = connectionId,
            partnerName = partnerName.ifBlank { "Partner" },
            isOnline = isOnline,
            lastSeenAt = lastSeenAt
        )

        partnerPresenceDismissJob = viewModelScope.launch {
            delay(3000)
            _partnerPresencePopup.value = null
        }
    }

    fun dismissPartnerPresencePopup() {
        partnerPresenceDismissJob?.cancel()
        _partnerPresencePopup.value = null
    }



    private fun showQuoteOverlay(
        connectionId: String,
        quote: String,
        senderName: String,
        emoji: String = "??"
    ) {
        quoteDismissJob?.cancel()
        _quoteOverlay.value = ReceivedQuoteOverlay(
            connectionId = connectionId,
            senderName = senderName.ifBlank { selectedConnection.value?.partnerName ?: "Your partner" },
            quote = quote,
            emoji = emoji
        )
        quoteDismissJob = viewModelScope.launch {
            delay(5500)
            _quoteOverlay.value = null
        }
        // Also float it on that connection's planet in the galaxy (both directions:
        // one you sent and one you received both belong to the same planet).
        val key = connectionId.lowercase()
        _quotesByConnection.value = _quotesByConnection.value + (key to quote)
        galaxyQuoteJobs[key]?.cancel()
        galaxyQuoteJobs[key] = viewModelScope.launch {
            delay(30_000)
            _quotesByConnection.value = _quotesByConnection.value - key
        }
    }

    /** connectionId → the sweet quote currently floating on that planet. */
    private val _quotesByConnection = MutableStateFlow<Map<String, String>>(emptyMap())
    val quotesByConnection: StateFlow<Map<String, String>> = _quotesByConnection.asStateFlow()
    private val galaxyQuoteJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    fun dismissQuoteOverlay() {
        quoteDismissJob?.cancel()
        _quoteOverlay.value = null
    }

    private val _isViewingNotifications = MutableStateFlow(false)
    val isViewingNotifications: StateFlow<Boolean> = _isViewingNotifications.asStateFlow()

    /**
     * Sets the screensaver background image for the selected partner.
     */
    fun updateScreensaver(imageUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val connId = _partnerConnectionId.value ?: return@launch
            val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connId }
            if (conn != null) {
                syncManager.sendRemoteCommandWithData(
                    conn.connectionId,
                    "set_background",
                    org.json.JSONObject().apply { put("imageUrl", imageUrl) },
                    effectiveTargetId(conn)
                )
                android.util.Log.i("ScreensaverViewModel", "🖼️ Sent background image to partner ${conn.connectionId}")
            }
        }
    }

    fun setViewingNotifications(viewing: Boolean) {
        _isViewingNotifications.value = viewing
        if (viewing) {
            // Fetch full history from MongoDB so the list is up to date
            fetchNotificationHistory()
        }
    }

    /**
     * Requests the full notification history from the server (MongoDB-backed).
     */
    fun fetchNotificationHistory() {
        val conn = selectedConnection.value ?: return
        val token = memberIdentity.value?.authToken
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val history = httpUploader.getNotifications(conn.connectionId, conn.partnerDeviceId, skip = 0, limit = 50, sessionToken = token)
                withContext(Dispatchers.Main) {
                    if (history.isNotEmpty()) {
                        // Merge or replace current notifications with history
                        val currentList = _remoteNotifications.value.toMutableList()
                        history.forEach { historical ->
                            if (currentList.none { it.id == historical.id }) {
                                currentList.add(historical)
                            }
                        }
                        currentList.sortByDescending { it.timestamp ?: 0L }
                        _remoteNotifications.value = currentList
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverVM", "❌ Failed history fetch", e)
            }
        }
    }

    private val _isTrackingLocation = MutableStateFlow(false)
    val isTrackingLocation: StateFlow<Boolean> = _isTrackingLocation.asStateFlow()

    private val _isDirectingToNativeMaps = MutableStateFlow(false)
    val isDirectingToNativeMaps: StateFlow<Boolean> = _isDirectingToNativeMaps.asStateFlow()

    fun setTrackingLocation(tracking: Boolean) {
        android.util.Log.i("ScreensaverViewModel", "📍 [LOCATION-STATE] setTrackingLocation: $tracking")
        _isTrackingLocation.value = tracking
    }

    fun setDirectingToNativeMaps(directing: Boolean) {
        android.util.Log.i("ScreensaverViewModel", "📍 [LOCATION-STATE] setDirectingToNativeMaps: $directing")
        _isDirectingToNativeMaps.value = directing
    }

    /**
     * Updates the relationship type for the currently selected connection.
     * Persists locally and syncs to the partner device immediately.
     */
    fun setRelationshipType(type: com.aman.gigi.model.RelationshipType) {
        viewModelScope.launch(Dispatchers.IO) {
            val connId = _partnerConnectionId.value ?: return@launch
            val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connId }
            if (conn != null) {
                android.util.Log.i("ScreensaverViewModel", "💜 [THEME] Setting relationship type: ${type.name} for ${conn.connectionId}")
                try {
                    // 1. Persist locally
                    connectionRepository.updateConnection(conn.copy(relationshipType = type.name))
                    // 2. Sync to partner immediately
                    syncManager.sendRelationshipType(
                        connectionId = conn.connectionId,
                        type = type,
                        targetDeviceId = effectiveTargetId(conn)
                    )
                    android.util.Log.i("ScreensaverViewModel", "✅ [THEME] Relationship type synced: ${type.name}")
                } catch (e: Exception) {
                    android.util.Log.e("ScreensaverViewModel", "❌ [THEME] Failed to set relationship type", e)
                }
            }
        }
    }

    /** Sets the theme/relationship type for a specific connection (used from settings). */
    fun setRelationshipTypeFor(connectionId: String, type: com.aman.gigi.model.RelationshipType) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connectionRepository.getAllActiveConnectionsOnce()
                .find { it.connectionId == connectionId } ?: return@launch
            try {
                // Keep the group flag intact — for groups this only recolors the theme.
                connectionRepository.updateConnection(conn.copy(relationshipType = type.name))
                // Remember my choice locally so it survives reinstall + bootstrap reconcile
                // (also picked up by the settings-sync push to the server).
                context.getSharedPreferences("galaxy_orbits", Context.MODE_PRIVATE)
                    .edit().putString("reltype_$connectionId", type.name).apply()
                syncManager.sendRelationshipType(
                    connectionId = conn.connectionId,
                    type = type,
                    targetDeviceId = effectiveTargetId(conn)
                )
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverViewModel", "Failed to set theme for $connectionId", e)
            }
        }
    }

    // --- History Feature ---
    private val _isHistoryOpen = MutableStateFlow(false)
    val isHistoryOpen: StateFlow<Boolean> = _isHistoryOpen.asStateFlow()

    private val _replayingScribble = MutableStateFlow<com.aman.gigi.model.Scribble?>(null)
    val replayingScribble: StateFlow<com.aman.gigi.model.Scribble?> = _replayingScribble.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyFlow: Flow<List<com.aman.gigi.model.ScribbleSummary>> = selectedConnection.flatMapLatest { activeConn ->
        if (activeConn != null) {
            // Enforce retention on load
            viewModelScope.launch {
                scribbleRepository.enforceHistoryRetention(activeConn.connectionId, com.aman.gigi.utils.AppConfig.userPlan.historyDays)
            }
            scribbleRepository.getScribbleSummariesByConnection(activeConn.connectionId)
        } else {
            flowOf(emptyList())
        }
    }

    fun setHistoryOpen(open: Boolean) {
        _isHistoryOpen.value = open
    }

    fun clearHistory() {
        val activeConn = activeConnections.value.firstOrNull()
        activeConn?.let {
            viewModelScope.launch {
                scribbleRepository.clearHistory(it.connectionId)
            }
        }
    }

    fun replayScribble(scribbleId: String) {
        viewModelScope.launch {
            val scribble = scribbleRepository.getScribbleById(scribbleId)
            _replayingScribble.value = scribble
        }
    }

    fun stopReplay() {
        _replayingScribble.value = null
    }


    

    // Location States
    private val _myLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val myLocation: StateFlow<Pair<Double, Double>?> = _myLocation.asStateFlow()

    val partnerLocation: StateFlow<Pair<Double, Double>?> = selectedConnection
        .map { conn ->
            if (conn?.partnerLatitude != null && conn.partnerLongitude != null) {
                Pair(conn.partnerLatitude, conn.partnerLongitude)
            } else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun updateMyLocation() {
        _myLocation.value = locationProvider.getCurrentLocation()
    }
    
    // Fullscreen Drawing state
    private val _isDrawingMode = MutableStateFlow(false)
    val isDrawingMode: StateFlow<Boolean> = _isDrawingMode.asStateFlow()
    
    // Dialog state for "Create vs Join"
    private val _showAddChoice = androidx.compose.runtime.mutableStateOf(false)
    val showAddChoice: androidx.compose.runtime.State<Boolean> = _showAddChoice

    // Recent GIFs
    val recentGifs: StateFlow<List<String>> = gifRepository.getRecentGifs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Check if user can add more connections.
     * Rule: Guests (Joiners) cannot add more connections. Hosts (Creators) can.
     */
    val canAddConnection: StateFlow<Boolean> = combine(
        activeConnections,
        com.aman.gigi.utils.AppConfig.planFlow
    ) { connections, plan ->
        if (plan.maxConnections <= 0) true
        else connections.size < plan.maxConnections
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    /**
     * Refreshes local location and broadcasts presence to partners.
     * Call this when entering camera mode to ensure accurate distance.
     */
    fun refreshLocations() {
        android.util.Log.i("ScreensaverViewModel", "📍 Refreshing locations for Sparkle...")
        viewModelScope.launch {
            // 1. Update Local (AWAIT result)
            updateMyLocation()
            
            // 2. Broadcast to Partners (so they see us updated)
            activeConnections.value.forEach { conn ->
                android.util.Log.d("ScreensaverViewModel", "📡 Broadcasting presence to ${conn.partnerName}")
                syncManager.announcePresence(conn.connectionId)
            }
        }
    }
    
    fun showAddChoice() {
        _showAddChoice.value = true
    }
    
    fun hideAddChoice() {
        _showAddChoice.value = false
    }
    
    /**
     * Navigate to a different screensaver UI
     */
    fun navigateTo(screen: ScreensaverScreen, connectionId: String? = null) {
        android.util.Log.i("ScreensaverVM", "🚀 [GIGI-NAV] Navigating to: $screen (ID: $connectionId) from ${_currentScreen.value}")
        _currentScreen.value = screen
        if (connectionId != null) {
            _partnerConnectionId.value = connectionId
            syncManager.queryPartnerPresence(connectionId, showPopup = true)
        }
        
        // Reset state when navigating to Sparkle Camera or returning to List
        if (screen == ScreensaverScreen.SPARKLE) {
            resetSparkleStatus()
        }
        
        if (screen == ScreensaverScreen.LIST) {
            _partnerDisconnected.value = null
            _isDrawingMode.value = false
            _partnerConnectionId.value = null
            _partnerPresencePopup.value = null
            _targetGroupMemberDeviceId.value = null
            _targetGroupMemberName.value = null
            _targetGroupMemberEmoji.value = null
        }
    }
    
    /**
     * Set drawing mode (fullscreen)
     */
    fun setDrawingMode(isDrawing: Boolean) {
        _isDrawingMode.value = isDrawing
    }
    
    /**
     * Handle incoming partner disconnection
     */
    fun onPartnerDisconnected(partnerName: String) {
        _partnerDisconnected.value = partnerName
    }

    /**
     * Clear send failure state
     */
    fun clearSendFailure() {
        _sendFailedPartnerName.value = null
        _lastErrorMessage.value = null
    }

    /**
     * Sign in with a Google ID token obtained from the Google Sign-In flow.
     * Forwards to [ConnectionBootstrapManager.signInWithGoogle].
     */
    fun signInWithGoogle(googleIdToken: String) {
        bootstrapManager.signInWithGoogle(googleIdToken)
    }

    fun completeProfile(
        displayName: String,
        gender: String,
        avatarUri: android.net.Uri? = null,
        dateOfBirth: String? = null
    ) {
        bootstrapManager.completeProfile(displayName, gender, avatarUri, null, null, "🌻", dateOfBirth)
    }

    fun updateProfile(
        displayName: String? = null,
        gender: String? = null,
        emoji: String? = null,
        avatarUri: android.net.Uri? = null
    ) {
        viewModelScope.launch {
            bootstrapManager.updateProfile(displayName, gender, emoji)
        }
    }

    fun clearAuthError() {
        bootstrapManager.clearAuthError()
    }

    fun refreshBootstrapState() {
        bootstrapManager.onAppForegrounded()
    }

    fun selectSweetCornerConnection(connectionId: String) {
        viewModelScope.launch {
            bootstrapManager.saveSelectedSweetConnectionId(connectionId.lowercase())
        }
    }
    
    /**
     * Create a new connection
     */
    fun createConnection(
        connectionId: String,
        partnerName: String,
        partnerDeviceId: String,
        connectionCode: String,
        relationshipType: String = "ROMANTIC",
        isGroup: Boolean = false,
        partnerEmojiUrl: String? = null
    ) {
        android.util.Log.i("ScreensaverVM", "🚀 [GIGI-TRACE] Creating connection: $connectionId for $partnerName")
        viewModelScope.launch {
            try {
                _pairingState.value = ConnectionState.CONNECTING
                connectionRepository.createConnection(
                    connectionId = connectionId,
                    partnerName = partnerName,
                    partnerDeviceId = partnerDeviceId,
                    connectionCode = connectionCode,
                    creatorDeviceId = syncManager.deviceId,
                    role = ConnectionRole.CREATOR,
                    memberId = memberIdentity.value?.memberId,
                    relationshipType = relationshipType,
                    isGroup = isGroup,
                    partnerEmojiUrl = partnerEmojiUrl
                )
                if (bootstrapManager.selectedSweetConnectionId() == null) {
                    bootstrapManager.saveSelectedSweetConnectionId(connectionId.lowercase())
                }
                // Start the sync service
                startSyncService()
                // Wait for pairing or manual cancel
            } catch (e: Exception) {
                _pairingState.value = ConnectionState.ERROR
            }
        }
    }
    
    /**
     * Auto-revoke unjoined pending invite codes older than 30 minutes
     */
    fun cleanupExpiredInvites() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val thirtyMinsMs = 30 * 60 * 1000L
                val allConns = connectionRepository.getAllConnectionsOnce()
                allConns.forEach { conn ->
                    val pId = conn.partnerDeviceId
                    val isPlaceholder = pId.isBlank() || pId == "waiting..." || pId == "joining..."
                    if (isPlaceholder && (now - conn.createdAt > thirtyMinsMs)) {
                        android.util.Log.i("Cleanup", "🧹 [AUTO-REVOKE] Deleting expired invite code ${conn.connectionCode}")
                        connectionRepository.deleteConnection(conn.connectionId)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Cleanup", "Error cleaning up expired invites: ${e.message}")
            }
        }
    }
    
    /**
     * Disconnect from a specific partner
     */
    fun disconnect(connection: Connection) {
        viewModelScope.launch {
            try {
                // 1. Tell the server we are disconnecting — HTTP first (reliable), then WS.
                bootstrapManager.archiveConnectionOnServer(connection.connectionId)
                syncManager.disconnect(connection.connectionId)
                
                // 2. Delete all scribbles for this connection
                scribbleRepository.deleteScribblesByConnection(connection.connectionId)
                
                // 3. Deactivate in repository
                connectionRepository.deleteConnection(connection.connectionId)
                
                // If no more connections, service will handle cleanup via sync manager observation
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverViewModel", "Error disconnecting ${connection.connectionId}", e)
            }
        }
    }
    
    /**
     * Join an existing connection.
     * No partner name needed — the server will return the real name (or group name)
     * in the connection_joined message and the SyncManager will apply it.
     */
    fun joinConnection(code: String, partnerName: String = "My person", relationshipType: String = "ROMANTIC") {
        viewModelScope.launch {
            try {
                _pairingState.value = ConnectionState.CONNECTING
                connectionRepository.createConnection(
                    connectionId = code,
                    partnerName = partnerName.ifBlank { "My person" },
                    partnerDeviceId = "joining...",
                    connectionCode = code,
                    relationshipType = relationshipType,
                    creatorDeviceId = null, // Will be updated by server message
                    role = ConnectionRole.PARTNER,
                    memberId = memberIdentity.value?.memberId
                )
                if (bootstrapManager.selectedSweetConnectionId() == null) {
                    bootstrapManager.saveSelectedSweetConnectionId(code.lowercase())
                }
                // Start the sync service
                startSyncService()
                // Wait for pairing or manual cancel
            } catch (e: Exception) {
                _pairingState.value = ConnectionState.ERROR
            }
        }
    }

    /**
     * Update last synced timestamp
     */
    fun updateLastSynced(connectionId: String) {
        viewModelScope.launch {
            connectionRepository.updateLastSynced(connectionId)
        }
    }

    /**
     * Update current user's profile animated emoji URL and broadcast to all connections.
     */
    fun updateProfileEmoji(emojiUrl: String, mode: String = "TWIGI") {
        viewModelScope.launch {
            try {
                val current = memberIdentity.value
                if (current != null) {
                    val updated = current.copy(
                        profileEmojiUrl = emojiUrl,
                        avatarMode = mode,
                        twigiRenderUrl = if (mode == "TWIGI") emojiUrl else current.twigiRenderUrl
                    )
                    bootstrapManager.saveIdentity(updated)
                }
                // Send profile_update to all active connections
                val active = connectionRepository.getAllActiveConnectionsOnce()
                active.forEach { conn ->
                    val myName = memberIdentity.value?.displayName ?: "Partner"
                    val myAvatar = memberIdentity.value?.avatarUrl ?: ""
                    syncManager.sendProfileUpdate(
                        connectionId = conn.connectionId,
                        avatarUrl = myAvatar,
                        emojiUrl = emojiUrl,
                        name = myName
                    )
                }
                android.util.Log.i("ScreensaverVM", "✨ Live animated profile emoji updated to: $emojiUrl (mode=$mode)")
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverVM", "Failed to update profile emoji: ${e.message}")
            }
        }
    }

    fun updateAvatarMode(mode: String) {
        viewModelScope.launch {
            try {
                val current = memberIdentity.value
                if (current != null) {
                    val updated = current.copy(avatarMode = mode)
                    bootstrapManager.saveIdentity(updated)
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverVM", "Failed to update avatar mode: ${e.message}")
            }
        }
    }

    /**
     * Saves 3D Twigi VRM model and broadcasts mode update to all connections.
     */
    fun saveTwigiVrm(vrmUrl: String, thumbnailB64: String? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            bootstrapManager.saveTwigiOnServer(
                mode = "TWIGI",
                configJson = vrmUrl,
                renderBase64 = thumbnailB64
            )
        }
    }

    /**
     * Update anniversary and meeting dates for a connection
     */
    fun updateTimelineDates(connectionId: String, meetingDate: Long?, anniversaryDate: Long?) {
        viewModelScope.launch {
            val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
            if (conn != null) {
                val updatedConn = conn.copy(
                    meetingDate = meetingDate,
                    anniversaryDate = anniversaryDate
                )
                connectionRepository.updateConnection(updatedConn)
                
                // Broadcast change immediately to partner
                syncManager.announcePresence(connectionId)
                android.util.Log.i("ScreensaverViewModel", "📅 Updated timeline dates for $connectionId. Meeting: $meetingDate, Anniversary: $anniversaryDate")
            }
        }
    }

    /**
     * Send a Giphy GIF
     */
    fun sendGif(connectionId: String, gifUrl: String) {
        viewModelScope.launch {
            // Save to recents
            gifRepository.addRecentGif(gifUrl)

            val scribble = com.aman.gigi.model.Scribble(
                scribbleId = java.util.UUID.randomUUID().toString(),
                connectionId = connectionId,
                strokes = emptyList(),
                isSent = true,
                mediaUrl = gifUrl,
                mediaType = "image/gif"
            )
            scribbleRepository.createScribble(scribble)
        }
    }

    /**
     * Send a local GIF from Uri
     */
    fun sendLocalGif(connectionId: String, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                android.util.Log.d("ScreensaverViewModel", "Processing local GIF for $connectionId from Uri: $uri")
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    android.util.Log.e("ScreensaverViewModel", "InputStream is null")
                    android.widget.Toast.makeText(context, "Failed to open GIF stream", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val bytes = inputStream.readBytes()
                inputStream.close()

                android.util.Log.d("ScreensaverViewModel", "Local GIF size: ${bytes.size} bytes")
                
                // Limit to 5MB
                if (bytes.size > 5 * 1024 * 1024) {
                    android.util.Log.e("ScreensaverViewModel", "GIF too large: ${bytes.size} bytes")
                    android.widget.Toast.makeText(context, "GIF is too large (max 5MB)", android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }

                android.widget.Toast.makeText(context, "Encoding GIF...", android.widget.Toast.LENGTH_SHORT).show()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                android.util.Log.d("ScreensaverViewModel", "Base64 encoding complete, length: ${base64.length}")
                
                val scribble = com.aman.gigi.model.Scribble(
                    scribbleId = java.util.UUID.randomUUID().toString(),
                    connectionId = connectionId,
                    strokes = emptyList(),
                    isSent = true,
                    mediaType = "image/gif",
                    mediaBase64 = base64
                )
                
                scribbleRepository.createScribble(scribble)
                android.util.Log.d("ScreensaverViewModel", "Local GIF scribble created and saved")
                android.widget.Toast.makeText(context, "GIF Ready to Sync!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverViewModel", "Error processing local GIF", e)
                android.widget.Toast.makeText(context, "Processing Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun sendQuote(text: String) {
        val connectionId = _partnerConnectionId.value ?: return
        sendQuote(connectionId, text)
    }

    fun sendQuote(connectionId: String, text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        syncManager.sendQuote(connectionId, cleanText)
        
        // Also show it locally in Sweet Corner
        showQuoteOverlay(
            connectionId = connectionId,
            quote = cleanText,
            senderName = "You"
        )
    }

    fun createLoveCardStack(
        connectionId: String,
        title: String,
        cards: List<LoveCardDraftItem>,
        unlockDate: Long? = null
    ) {
        if (cards.isEmpty()) return
        viewModelScope.launch {
            val member = memberIdentity.value
            val deck = loveCardRepository.createOutgoingDeck(
                connectionId = connectionId.lowercase(),
                senderMemberId = member?.memberId,
                senderDisplayName = member?.displayName,
                title = title,
                cards = cards,
                unlockDate = unlockDate
            )
            syncManager.sendLoveCardStack(
                connectionId = connectionId,
                stackId = deck.stack.stackId,
                title = deck.stack.title,
                unlockDate = unlockDate,
                payloadCards = org.json.JSONArray().apply {
                    deck.items.forEach { item ->
                        put(
                            org.json.JSONObject().apply {
                                put("cardId", item.card.cardId)
                                put("stackId", deck.stack.stackId)
                                put("type", item.card.type)
                                put("prompt", item.card.prompt)
                                item.card.choicesJson?.takeIf { it.isNotBlank() }?.let { put("choices", org.json.JSONArray(it)) }
                                item.card.theme?.let { put("theme", it) }
                                item.card.animationStyle?.let { put("animationStyle", it) }
                                item.card.decorationsJson?.takeIf { it.isNotBlank() }?.let { put("decorations", org.json.JSONArray(it)) }
                                put("sortOrder", item.card.sortOrder)
                            }
                        )
                    }
                }
            )
        }
    }

    fun openLoveCardDeck(connectionId: String, stackId: String) {
        viewModelScope.launch {
            loveCardRepository.markDeckOpened(stackId)
            syncManager.sendLoveCardOpened(connectionId, stackId)
        }
    }

    fun exchangeMusicCompatibility(connectionId: String) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("gigi_music_prefs", Context.MODE_PRIVATE)
            val localHistory = prefs.getStringSet("listened_artists", emptySet()) ?: emptySet()
            val payload = org.json.JSONObject().apply {
                put("history", org.json.JSONArray(localHistory.toList()))
            }
            syncManager.sendRemoteCommandWithData(connectionId, com.aman.gigi.utils.Constants.COMMAND_EXCHANGE_MUSIC_HISTORY, payload)
        }
    }

    fun answerLoveCardDeck(
        connectionId: String,
        stackId: String,
        responses: List<LoveCardDraftResponse>
    ) {
        viewModelScope.launch {
            val deck = loveCardRepository.answerDeck(
                stackId = stackId,
                connectionId = connectionId.lowercase(),
                memberId = memberIdentity.value?.memberId,
                responses = responses
            ) ?: return@launch

            syncManager.sendLoveCardAnswered(
                connectionId = connectionId,
                stackId = stackId,
                responses = org.json.JSONArray().apply {
                    deck.items.forEach { item ->
                        val response = item.response ?: return@forEach
                        put(
                            org.json.JSONObject().apply {
                                put("responseId", response.responseId)
                                put("stackId", stackId)
                                put("cardId", response.cardId)
                                response.answerText?.let { put("answerText", it) }
                                response.selectedChoice?.let { put("selectedChoice", it) }
                                response.emojiReaction?.let { put("emojiReaction", it) }
                                put("answeredAt", response.answeredAt)
                                response.answeredByMemberId?.let { put("answeredByMemberId", it) }
                            }
                        )
                    }
                }
            )
            if (_activeLoveCardDeckId.value == stackId) {
                _activeLoveCardDeckId.value = null
            }
        }
    }

    fun showLoveCardDeck(stackId: String) {
        _activeLoveCardDeckId.value = stackId
    }

    fun dismissLoveCardDeck(stackId: String? = null) {
        if (stackId == null || _activeLoveCardDeckId.value == stackId) {
            _activeLoveCardDeckId.value = null
        }
    }

    fun markLoveCardDeckPresentedIfNeeded(deck: LoveCardDeck) {
        if (!deck.stack.isIncoming || deck.stack.status != LoveCardStackStatus.SENT.name) return
        openLoveCardDeck(deck.stack.connectionId, deck.stack.stackId)
    }

    fun uploadLoveCardMedia(context: android.content.Context, uri: android.net.Uri, onUploaded: (String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = connectionRepository.getAllActiveConnectionsOnce().firstOrNull()
                if (conn == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onUploaded(null) }
                    return@launch
                }
                val tempFile = java.io.File(context.cacheDir, "lovecard_media_${java.util.UUID.randomUUID()}")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                val token = memberIdentity.value?.authToken
                val uploadedUrl = httpUploader.uploadFile(tempFile, conn.connectionId, "card_${java.util.UUID.randomUUID()}", sessionToken = token)
                tempFile.delete()
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onUploaded(uploadedUrl)
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverViewModel", "Failed to upload love card media", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onUploaded(null)
                }
            }
        }
    }

    fun uploadLoveCardMediaFile(file: java.io.File, onUploaded: (String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = connectionRepository.getAllActiveConnectionsOnce().firstOrNull()
                if (conn == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onUploaded(null) }
                    return@launch
                }
                val token = memberIdentity.value?.authToken
                val uploadedUrl = httpUploader.uploadFile(file, conn.connectionId, "card_${java.util.UUID.randomUUID()}", sessionToken = token)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onUploaded(uploadedUrl)
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverViewModel", "Failed to upload love card media file", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onUploaded(null)
                }
            }
        }
    }

    /**
     * Start the screensaver sync service
     */
    /**
     * Process captured photo with effects
     */
    fun sendSparkle(
        bitmap: android.graphics.Bitmap, 
        connectionId: String = "", 
        revealType: String? = null,
        secretMessage: String? = null
    ) {
        viewModelScope.launch {
            try {
                // Determine connectionId if not provided
                val targetConnId = if (connectionId.isEmpty()) {
                    activeConnections.value.firstOrNull()?.connectionId ?: ""
                } else {
                    connectionId
                }
                
                if (targetConnId.isEmpty()) return@launch

                // 1. Resize if too large (Max 720p - conservative for stability)
                val maxDimension = 720
                var finalBitmap = bitmap
                if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                     val scale = maxDimension.toFloat() / kotlin.math.max(bitmap.width, bitmap.height)
                     val newWidth = (bitmap.width * scale).toInt()
                     val newHeight = (bitmap.height * scale).toInt()
                     finalBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                }

                // 2. Save to internal storage & Compress (Lower quality for consistency)
                val filename = "sparkle_${System.currentTimeMillis()}.jpg"
                val file = java.io.File(context.filesDir, filename)
                val stream = java.io.FileOutputStream(file)
                
                finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, stream)
                stream.close()
                
                // 2. Encode to Base64
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                
                // 3. Create Scribble (Sparkle Type)
                val newScribbleId = java.util.UUID.randomUUID().toString()
                
                // Track this specific sparkle
                pendingSparkleId = newScribbleId
                _sparkleSendStatus.value = SendStatus.SENDING
                
                val scribble = com.aman.gigi.model.Scribble(
                    scribbleId = newScribbleId,
                    connectionId = targetConnId,
                    strokes = emptyList(),
                    isSent = true,
                    mediaType = "image/sparkle",
                    mediaBase64 = base64,
                    revealType = revealType,
                    secretMessage = secretMessage
                )
                
                scribbleRepository.createScribble(scribble)
                // Navigation will happen in UI when status becomes SENT
                
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Failed to send Sparkle", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSyncService() {
        if (serverStatus.value.mode == com.aman.gigi.model.ServerMode.MAINTENANCE) {
            android.util.Log.i("ScreensaverViewModel", "Skipping sync service start because server is in maintenance")
            return
        }
        val intent = Intent(context, ScreensaverSyncService::class.java).apply {
            action = ScreensaverSyncService.ACTION_START_SYNC
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Notification Access Helper
     */
    fun isNotificationAccessGranted(): Boolean {
        return try {
            val enabledListeners = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
            enabledListeners.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun openNotificationSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
             android.widget.Toast.makeText(context, "Could not open settings", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun updateMyEmoji(emoji: String) {
        viewModelScope.launch {
            bootstrapManager.updateProfile(emoji = emoji)
        }
    }

    fun updatePartnerLocalEmoji(connectionId: String, emoji: String) {
        viewModelScope.launch {
            val existing = connectionRepository.getAllConnectionsOnce().find { it.connectionId == connectionId }
            if (existing != null) {
                connectionRepository.updateConnection(existing.copy(partnerEmoji = emoji))
            }
        }
    }

    /** Rename the display name the current device shows for a connection (local only). */
    fun renamePartnerLocalName(connectionId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val existing = connectionRepository.getAllConnectionsOnce().find { it.connectionId == connectionId }
            if (existing != null) {
                connectionRepository.updateConnection(existing.copy(partnerName = clean))
            }
            // Persist the override locally so it survives reinstall + bootstrap reconcile
            // (also synced to the server via the settings-sync push).
            context.getSharedPreferences("galaxy_orbits", Context.MODE_PRIVATE)
                .edit().putString("rename_$connectionId", clean).apply()
        }
    }

    fun logout() {
        bootstrapManager.signOut()
    }

    fun deleteAccount() {
        bootstrapManager.deleteAccount()
    }

    // ── Nebula Discovery State & Operations ───────────────────────────────

    private val _nebulaMotes = MutableStateFlow<List<com.aman.gigi.model.NebulaMember>>(emptyList())
    val nebulaMotes: StateFlow<List<com.aman.gigi.model.NebulaMember>> = _nebulaMotes.asStateFlow()

    private val _nebulaSearchResults = MutableStateFlow<List<com.aman.gigi.model.NebulaMember>>(emptyList())
    val nebulaSearchResults: StateFlow<List<com.aman.gigi.model.NebulaMember>> = _nebulaSearchResults.asStateFlow()

    private val _isNebulaLoading = MutableStateFlow(false)
    val isNebulaLoading: StateFlow<Boolean> = _isNebulaLoading.asStateFlow()

    private val _nebulaSearchQuery = MutableStateFlow("")
    val nebulaSearchQuery: StateFlow<String> = _nebulaSearchQuery.asStateFlow()

    private val _pendingGhostInvites = MutableStateFlow<Set<String>>(emptySet())
    val pendingGhostInvites: StateFlow<Set<String>> = _pendingGhostInvites.asStateFlow()

    fun loadNebulaMotes() {
        viewModelScope.launch {
            _isNebulaLoading.value = true
            val motes = bootstrapManager.fetchNebulaMotes()
            _nebulaMotes.value = motes
            _isNebulaLoading.value = false
        }
    }

    fun setNebulaSearchQuery(query: String) {
        _nebulaSearchQuery.value = query
        if (query.isBlank()) {
            _nebulaSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            val results = bootstrapManager.searchNebula(query)
            _nebulaSearchResults.value = results
        }
    }

    fun toggleDiscoverability(discoverable: Boolean, handle: String?, bio: String?, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = bootstrapManager.updateDiscoverability(discoverable, handle, bio)
            if (res.isSuccess) {
                loadNebulaMotes()
                onResult(Result.success(Unit))
            } else {
                onResult(Result.failure(res.exceptionOrNull() ?: Exception("Unknown error")))
            }
        }
    }

    fun sendNebulaInvite(target: com.aman.gigi.model.NebulaMember, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            _pendingGhostInvites.value = _pendingGhostInvites.value + target.memberId
            val res = bootstrapManager.sendNebulaInvite(target.memberId, target.handle)
            if (res.isSuccess) {
                _nebulaMotes.value = _nebulaMotes.value.map {
                    if (it.memberId == target.memberId) it.copy(inviteStatus = "SENT") else it
                }
                _nebulaSearchResults.value = _nebulaSearchResults.value.map {
                    if (it.memberId == target.memberId) it.copy(inviteStatus = "SENT") else it
                }
            }
            onResult(res)
        }
    }

    fun blockMember(memberId: String) {
        viewModelScope.launch {
            bootstrapManager.blockMember(memberId)
            _nebulaMotes.value = _nebulaMotes.value.filter { it.memberId != memberId }
            _nebulaSearchResults.value = _nebulaSearchResults.value.filter { it.memberId != memberId }
        }
    }

    fun reportMember(memberId: String, reason: String, note: String?) {
        viewModelScope.launch {
            bootstrapManager.reportMember(memberId, reason, note)
        }
    }
}

data class PartnerPresencePopup(
    val connectionId: String,
    val partnerName: String,
    val isOnline: Boolean,
    val lastSeenAt: Long?
)

data class ReceivedQuoteOverlay(
    val connectionId: String,
    val senderName: String,
    val quote: String,
    val emoji: String = "??"
)