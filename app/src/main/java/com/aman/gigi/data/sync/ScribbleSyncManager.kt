package com.aman.gigi.data.sync
import java.io.File
import java.io.FileOutputStream
// GIGI_FIX_SENTINEL_1

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aman.gigi.data.client.ConnectionBootstrapManager
import com.aman.gigi.data.live.LiveEvent
import com.aman.gigi.data.live.LiveEventBus
import com.aman.gigi.data.nest.NestEvent
import com.aman.gigi.data.nest.NestEventBus
import com.aman.gigi.data.nest.PetState
import com.aman.gigi.model.PartnerPresence
import com.aman.gigi.model.Scribble
import com.aman.gigi.model.ScribbleStatus
import com.aman.gigi.model.TransportState
import com.aman.gigi.network.WebSocketClient
import com.aman.gigi.network.SessionMessage
import com.aman.gigi.repository.ConnectionRepository
import com.aman.gigi.repository.OutboundActionRepository
import com.aman.gigi.repository.ScribbleRepository
import com.aman.gigi.ui.screensaver.LockscreenScribbleActivity
import com.aman.gigi.ui.sparkle.SparkleRevealActivity
import com.aman.gigi.utils.CompressionUtil
import com.aman.gigi.utils.FileScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manager for syncing scribbles between devices
 */
@Singleton
class ScribbleSyncManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val scribbleRepository: ScribbleRepository,
    private val outboundActionRepository: OutboundActionRepository,
    val connectionRepository: ConnectionRepository,
    private val bootstrapManager: ConnectionBootstrapManager,
    private val identityStore: com.aman.gigi.data.client.ClientIdentityStore,
    private val sharedAlarmRepository: com.aman.gigi.repository.SharedAlarmRepository,
    private val loveCardRepository: com.aman.gigi.repository.LoveCardRepository,
    private val webSocketClient: WebSocketClient,
    private val chatRepository: com.aman.gigi.repository.ChatRepository,
    private val httpUploader: com.aman.gigi.network.HttpUploader,
    val networkMonitor: com.aman.gigi.utils.NetworkMonitor,
    private val fileScanner: FileScanner,
    private val sharedAlbumStore: com.aman.gigi.data.music.SharedAlbumStore,
    private val breakCardDao: com.aman.gigi.data.dao.BreakCardDao,
    val nowPlayingTracker: com.aman.gigi.data.nowplaying.NowPlayingTracker
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSyncJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val TAG = "ScribbleSyncManager"
    private var isStarted = false
    private var customLifecycleOwner: LifecycleOwner? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    // Event flow for UI observation
    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<SyncEvent>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow().filter { value ->
        val cid = when (value) {
            is SyncEvent.FileDownloadProgress -> value.connectionId
            is SyncEvent.FileDownloadReceived -> value.connectionId
            is SyncEvent.PhotoDownloadReceived -> value.connectionId
            else -> null
        }
        if (cid != null) {
            val activeConn = kotlinx.coroutines.runBlocking {
                connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == cid }
            }
            val isCreator = activeConn?.creatorDeviceId?.equals(deviceId, ignoreCase = true) == true
            if (!isCreator) {
                Log.i(TAG, "⏭️ [ROLE-CHECK] Intercepted and skipped emit of $value for partner device.")
                false
            } else true
        } else true
    }
    
    // Server configuration
    private val SERVER_URL = com.aman.gigi.utils.Constants.SERVER_URL
    
    // Retry configuration
    private val maxRetries = 5
    private val baseDelayMs = 1000L // 1 second
    
    // Heartbeat configuration
    private val HEARTBEAT_INTERVAL = 15000L
    private val HEARTBEAT_TIMEOUT = 45000L
    private val ACTION_PROCESS_INTERVAL = 2000L
    private val MEDIA_TYPE_HEARTBEAT = "application/vnd.gigi.heartbeat"
    
    val deviceId: String by lazy {
        android.provider.Settings.Secure.getString(
            context.contentResolver, 
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: java.util.UUID.randomUUID().toString()
    }
    
    // Track last activity time for each connection
    private val lastPartnerActivity = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val lastPresenceQueries = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val shownScribbleNotificationIds = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private var nsdManager: android.net.nsd.NsdManager? = null
    private var registrationListener: android.net.nsd.NsdManager.RegistrationListener? = null
    private var discoveryListener: android.net.nsd.NsdManager.DiscoveryListener? = null

    init {
        Log.i(TAG, "🚀 [INIT] Initializing ScribbleSyncManager and starting auto-pruning")
        startDailyAutoPruning()
        startLocalNsd()
    }

    private fun startLocalNsd() {
        try {
            nsdManager = context.getSystemService(android.content.Context.NSD_SERVICE) as android.net.nsd.NsdManager

            val serviceInfo = android.net.nsd.NsdServiceInfo().apply {
                serviceName = "Gigi_${deviceId.take(6)}"
                serviceType = "_gigi._tcp"
                port = 6969
            }
            
            registrationListener = object : android.net.nsd.NsdManager.RegistrationListener {
                override fun onServiceRegistered(nsdServiceInfo: android.net.nsd.NsdServiceInfo) {
                    Log.i(TAG, "✅ [NSD] Service registered: ${nsdServiceInfo.serviceName}")
                }
                override fun onRegistrationFailed(serviceInfo: android.net.nsd.NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "❌ [NSD] Registration failed: $errorCode")
                }
                override fun onServiceUnregistered(arg0: android.net.nsd.NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: android.net.nsd.NsdServiceInfo, errorCode: Int) {}
            }
            
            nsdManager?.registerService(serviceInfo, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, registrationListener)

            discoveryListener = object : android.net.nsd.NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "❌ [NSD] Discovery start failed: $errorCode")
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "❌ [NSD] Discovery stop failed: $errorCode")
                }
                override fun onDiscoveryStarted(regType: String) {
                    Log.i(TAG, "✅ [NSD] Discovery started for $regType")
                }
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onServiceFound(serviceInfo: android.net.nsd.NsdServiceInfo) {
                    Log.i(TAG, "🔍 [NSD] Service found: ${serviceInfo.serviceName}")
                    if (serviceInfo.serviceType == "_gigi._tcp." && serviceInfo.serviceName != "Gigi_${deviceId.take(6)}") {
                        nsdManager?.resolveService(serviceInfo, object : android.net.nsd.NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: android.net.nsd.NsdServiceInfo, errorCode: Int) {
                                Log.e(TAG, "❌ [NSD] Resolve failed: $errorCode")
                            }
                            override fun onServiceResolved(serviceInfo: android.net.nsd.NsdServiceInfo) {
                                Log.i(TAG, "✅ [NSD] Service resolved: ${serviceInfo.serviceName} at ${serviceInfo.host.hostAddress}:${serviceInfo.port}")
                            }
                        })
                    }
                }
                override fun onServiceLost(serviceInfo: android.net.nsd.NsdServiceInfo) {}
            }
            nsdManager?.discoverServices("_gigi._tcp", android.net.nsd.NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "❌ [NSD] Error initializing NSD discovery", e)
        }
    }

    private fun startDailyAutoPruning() {
        scope.launch {
            while (true) {
                try {
                    // 1. Prune database records older than 7 days
                    outboundActionRepository.pruneCompletedActions(7 * 24 * 60 * 60 * 1000)
                    
                    // 2. Prune old temporary files in cache directory
                    val cacheFiles = context.cacheDir.listFiles() ?: emptyArray()
                    for (file in cacheFiles) {
                        if (file.name.startsWith("minio_") || file.name.startsWith("ws_")) {
                            if (System.currentTimeMillis() - file.lastModified() > (24 * 60 * 60 * 1000)) {
                                file.delete()
                                Log.i(TAG, "🗑️ Auto-pruning deleted old cached file: ${file.name}")
                            }
                        }
                    }
                    // 3. Purge corrupted blank chat messages
                    chatRepository.deleteBlankMessages()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to run auto-pruning", e)
                }
                kotlinx.coroutines.delay(24 * 60 * 60 * 1000)
            }
        }
    }
    
    
    // Acknowledgement tracking
    private val pendingAcks = java.util.concurrent.ConcurrentHashMap<String, Job>()

    private val _isPartnerTyping = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isPartnerTyping: StateFlow<Map<String, Boolean>> = _isPartnerTyping

    private val _isPartnerDrawing = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isPartnerDrawing: StateFlow<Map<String, Boolean>> = _isPartnerDrawing

    private val ACK_TIMEOUT = 180000L
    
    data class IncomingFileState(
        var currentSize: Long = 0L,
        var fileSize: Long = 0L,
        var tempFile: java.io.File? = null,
        var fileName: String? = null,
        var lastEmittedProgress: Int = -1
    )

    companion object {
        const val PRESENCE_CONNECTION_ID = "SYS_PRESENCE"
        val completedDownloads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val incomingFiles = java.util.concurrent.ConcurrentHashMap<String, IncomingFileState>()
        val activeDownloads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    }

    private var lastChunkReceivedTime: Long = 0
    private val pendingHttpDownloads = java.util.concurrent.ConcurrentHashMap<String, PendingDownload>()
    private data class PendingDownload(
        val fileName: String,
        val assetPath: String,
        val fileSize: Long,
        val connectionId: String
    )

    private fun downloadAndProcessFile(connectionId: String, fileName: String, assetPath: String, fileSize: Long) {
        val pendingKey = "$connectionId:$fileName"
        pendingHttpDownloads[pendingKey] = PendingDownload(fileName, assetPath, fileSize, connectionId)

        scope.launch {
            _events.emit(SyncEvent.FileDownloadProgress(connectionId, fileName, 0))
            val destFile = File(context.cacheDir, "downloaded_file_$fileName")
            val success = httpUploader.downloadFile(assetPath, destFile, object : com.aman.gigi.network.HttpUploader.OnProgressListener {
                override fun onProgress(bytesRead: Long, totalBytes: Long, done: Boolean) {
                    val actualTotal = if (totalBytes > 0) totalBytes else fileSize
                    if (actualTotal > 0) {
                        val progress = ((bytesRead.toFloat() / actualTotal * 100).toInt()).coerceIn(0, 100)
                        scope.launch { _events.emit(SyncEvent.FileDownloadProgress(connectionId, fileName, progress)) }
                    }
                }
            })
            if (success) {
                pendingHttpDownloads.remove(pendingKey)
                val fileData = destFile.readBytes()
                _events.emit(SyncEvent.FileDownloadReceived(connectionId, fileName, fileData))
                destFile.delete()
            } else {
                Log.e(TAG, "❌ Failed to download remote file from $assetPath")
            }
        }
    }

    private fun resumeInterruptedDownloads(connectionId: String) {
        pendingHttpDownloads.values.filter { it.connectionId == connectionId }.forEach { pending ->
            Log.i(TAG, "🔄 [RESUME] Resuming interrupted download for ${pending.fileName}...")
            downloadAndProcessFile(pending.connectionId, pending.fileName, pending.assetPath, pending.fileSize)
        }
    }

    fun start() {
        if (isStarted) {
            Log.d(TAG, "Sync manager already started, ignoring redundant call")
            return
        }
        isStarted = true
        
        // Install Global Crash Handler to catch elusive crashes
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "💥 [FATAL-CRASH] Uncaught exception in thread ${thread.name}", throwable)
            oldHandler?.uncaughtException(thread, throwable)
        }

        Log.i(TAG, "🚀 Starting sync manager for all active connections...")
        
        // 0. Recovery: Clear any "Zombie" sending states from previous sessions
        scope.launch {
            resetStuckScribbles()
            outboundActionRepository.deleteRemoteCommands()
            outboundActionRepository.requeueInFlightActions(
                connectionRepository.getAllActiveConnectionsOnce().map { it.connectionId }
            )
            outboundActionRepository.pruneCompletedActions(olderThanMillis = 7L * 24L * 60L * 60L * 1000L)
            startPresenceSync()
        }
        
        // Observe internet status
        scope.launch {
            networkMonitor.isOnline.collectLatest { isOnline ->
                Log.i(TAG, "≡ƒîÉ Internet status changed: isOnline=$isOnline")
                if (!isOnline) {
                    val activeConnections = connectionRepository.getAllActiveConnectionsOnce()
                    activeConnections.forEach { conn ->
                        setTransportState(conn.connectionId, TransportState.NO_INTERNET)
                    }
                    // Stop jobs so they can be fresh-started when online returns
                    stopJobs()
                } else {
                    connectionRepository.getAllActiveConnectionsOnce().forEach { conn ->
                        setTransportState(conn.connectionId, TransportState.CONNECTING)
                    }
                    // Re-trigger sync jobs when back online
                    val activeConnections = connectionRepository.getAllActiveConnectionsOnce()
                    updateSyncJobs(activeConnections)
                }
            }
        }

        // Observe the list of active connections and manage jobs accordingly
        scope.launch {
            connectionRepository.getActiveConnections().collect { activeConnections ->
                Log.i(TAG, "🔄 [SYNC-UPDATE] Active connections changed: ${activeConnections.size} connections found")
                activeConnections.forEach { 
                    Log.d(TAG, "   └─ Connection: ${it.connectionId}, isActive: ${it.isActive}, Role: ${it.role}")
                }
                updateSyncJobs(activeConnections)
            }
        }
    }
    
    private var lastForegroundTime = 0L

    // Mirrors the last transport state we wrote per connection so we can de-dupe DB writes
    // (the sync loop can fire many messages/sec) and avoid stale "Reconnecting…" states.
    private val transportStateCache = java.util.concurrent.ConcurrentHashMap<String, TransportState>()

    /** Single funnel for transport-state writes: only hits the DB when the value actually changes. */
    private suspend fun setTransportState(connectionId: String, state: TransportState) {
        if (transportStateCache[connectionId] == state) return
        transportStateCache[connectionId] = state
        connectionRepository.updateTransportState(connectionId, state)
    }

    /**
     * Called when app comes to foreground - triggers auto-reconnect for all active connections
     */
    fun onAppForegrounded() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundTime < 5000) {
            Log.d(TAG, "⏳ [LIFECYCLE] onAppForegrounded throttled (debounce active)")
            return
        }
        lastForegroundTime = now
        
        Log.i(TAG, "🔄 [LIFECYCLE] App foregrounded - checking connections for auto-reconnect")
        scope.launch {
            val activeConnections = connectionRepository.getActiveConnections().firstOrNull() ?: emptyList()
            activeConnections.forEach { connection ->
                val lowerId = connection.connectionId.lowercase()
                val isConnected = webSocketClient.isConnected(lowerId)
                if (!isConnected) {
                    Log.i(TAG, "🔄 [AUTO-RECONNECT] Reconnecting $lowerId...")
                    _events.emit(SyncEvent.Reconnecting(lowerId, "App foregrounded"))
                    
                    // Cancel existing job if any
                    activeSyncJobs[lowerId]?.cancel()
                    activeSyncJobs.remove(lowerId)
                    
                    // Start fresh sync job
                    activeSyncJobs[lowerId] = startSyncJob(connection)
                } else {
                    Log.d(TAG, "✅ [AUTO-RECONNECT] $lowerId already connected - triggering heartbeat burst")
                    // Proactive Heartbeat Burst on foreground even if "connected" (in case of stale server state)
                    launch {
                        repeat(3) { i ->
                            delay(300L * i)
                            sendHeartbeat(lowerId)
                        }
                    }
                    sendResumeSession(connection)
                    queryPartnerPresence(lowerId, showPopup = false)
                }
            }
        }
    }

    private fun startPresenceSync() {
        if (activeSyncJobs.containsKey(PRESENCE_CONNECTION_ID)) return
        
        Log.i(TAG, "≡ƒîÉ Starting global presence sync...")
        val presenceJob = scope.launch {
            // Observe messages for presence channel
            launch {
                webSocketClient.messages.collect { message ->
                    if (message.connectionId == PRESENCE_CONNECTION_ID) {
                        // Presence updates mostly handled server-side, but we can log here
                        Log.d(TAG, "≡ƒôí Presence message received: $message")
                    }
                }
            }
            
            // Initial connection
            connectPresenceWebSocket()
            
            // Keep-alive loop for presence
            while (isActive) {
                delay(60000L)
                if (webSocketClient.isConnected(PRESENCE_CONNECTION_ID)) {
                    val deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver, 
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "unknown"
                    
                    val ping = """{"type": "presence_ping", "deviceId": "$deviceId", "deviceName": "${android.os.Build.MODEL}"}"""
                    webSocketClient.sendText(PRESENCE_CONNECTION_ID, ping)
                } else if (isStarted) {
                    connectPresenceWebSocket()
                }
            }
        }
        activeSyncJobs[PRESENCE_CONNECTION_ID] = presenceJob
    }

    private fun connectPresenceWebSocket() {
        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver, 
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        
        webSocketClient.connect(
            connectionId = PRESENCE_CONNECTION_ID,
            url = SERVER_URL,
            deviceId = deviceId,
            listener = object : WebSocketClient.ConnectionListener {
                override fun onConnected() {
                    Log.i(TAG, "Γ£à Presence WebSocket Connected")
                    scope.launch {
                        val register = """
                            {
                                "type": "register",
                                "deviceId": "$deviceId",
                                "deviceName": "${android.os.Build.MODEL}"
                            }
                        """.trimIndent()
                        webSocketClient.sendText(PRESENCE_CONNECTION_ID, register)
                    }
                }
                override fun onDisconnecting(code: Int, reason: String) {}
                override fun onDisconnected(code: Int, reason: String) {
                    Log.w(TAG, "≡ƒöî Presence WebSocket Disconnected: $reason")
                }
                override fun onError(throwable: Throwable) {
                    Log.e(TAG, "≡ƒÆÑ Presence WebSocket Error", throwable)
                }
            }
        )
    }

    @Synchronized
    private fun updateSyncJobs(activeConnections: List<com.aman.gigi.model.Connection>) {
        val currentConnectionIds = activeConnections.map { it.connectionId.lowercase() }.toSet()
        
        // 1. Stop jobs for connections that are no longer active
        val jobsToStop = activeSyncJobs.keys.filter { it.lowercase() !in currentConnectionIds && it != PRESENCE_CONNECTION_ID }
        Log.i(TAG, "🔄 [SYNC-UPDATE] Updating jobs. Current: ${activeSyncJobs.keys}, New Active: $currentConnectionIds")
        
        jobsToStop.forEach { id ->
            Log.i(TAG, "🛑 [SYNC-UPDATE] Stopping sync job for connection: $id")
            activeSyncJobs[id]?.cancel()
            activeSyncJobs.remove(id)
        }
        
        // 2. Start jobs for new active connections (also restart if previous job completed/cancelled)
        activeConnections.forEach { connection ->
            val lowerId = connection.connectionId.lowercase()
            val existingKey = activeSyncJobs.keys.find { it.lowercase() == lowerId }
            val existingJob = if (existingKey != null) activeSyncJobs[existingKey] else null
            if (existingJob == null || !existingJob.isActive) {
                if (existingJob != null) {
                    Log.i(TAG, "♻️ Replacing completed/cancelled job for: $lowerId")
                }
                Log.i(TAG, "Starting sync job for connection: $lowerId")
                activeSyncJobs[lowerId] = startSyncJob(connection)
            }
        }
    }

    private fun startSyncJob(connection: com.aman.gigi.model.Connection): Job {
        return scope.launch {
            // Each connection gets its own WebSocket client instance for parallel sessions
            // Note: Since WebSocketClient is currently a @Singleton, we might need to 
            // refactor it to be injectable per session, or manage multiple sockets internally.
            // For now, let's assume one socket can handle multiple handshakes 
            // OR (better) we change WebSocketClient to NOT be a singleton.
            
            // 1. Listen for ALL incoming messages FIRST
            launch {
                webSocketClient.messages.collect { message ->
                    handleInternalMessage(message, connection.connectionId)
                }
            }

            // Ensure the collector coroutine is subscribed before we connect
            yield()

            // 2. Connect to WebSocket server AFTER collector is ready
            connectWebSocket(connection)
            
            // 3. Heartbeat & Reconnect Loop: Essential for Background Persistence
            launch {
                while (isActive) {
                    delay(HEARTBEAT_INTERVAL)
                    
                    val isConnected = webSocketClient.isConnected(connection.connectionId)
                    if (isConnected) {
                        Log.d(TAG, "≡ƒÆô Sending periodic heartbeat for ${connection.connectionId}")
                        sendHeartbeat(connection.connectionId)
                        
                        // ROBUST ROLE CHECK: If we are missing creatorDeviceId, ask the server/partner for it
                        scope.launch {
                            val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connection.connectionId }
                            if (conn != null && conn.creatorDeviceId == null) {
                                Log.w(TAG, "≡ƒöì [ROLE-RECOVERY] creatorDeviceId is missing for ${connection.connectionCode}, requesting role info...")
                                val requestRole = """{"type": "request_role_info", "connectionId": "${connection.connectionId}"}"""
                                webSocketClient.sendText(connection.connectionId, requestRole)
                            }
                        }
                        queryPartnerPresence(connection.connectionId, showPopup = false)
                    } else if (isStarted) {
                        Log.i(TAG, "≡ƒöä [AUTO-RECONNECT] Session ${connection.connectionId} lost in background, retrying...")
                        connectWebSocket(connection)
                    }
                }
            }
            
            // 4. Presence Watchdog: Ask the server for a fresh snapshot instead of locally guessing.
            launch {
                while (isActive) {
                    delay(5000L)
                    val lastActivity = lastPartnerActivity[connection.connectionId] ?: 0L
                    val timeSinceActivity = System.currentTimeMillis() - lastActivity
                    if (timeSinceActivity > HEARTBEAT_TIMEOUT) {
                        val lastPresenceQuery = lastPresenceQueries[connection.connectionId] ?: 0L
                        if (System.currentTimeMillis() - lastPresenceQuery >= HEARTBEAT_TIMEOUT / 2) {
                            Log.w(TAG, "ΓÅ░ Presence watchdog requesting authoritative snapshot for ${connection.connectionId}")
                            queryPartnerPresence(connection.connectionId, showPopup = false)
                        }
                    }
                }
            }
            
            // 5. Observe pending scribbles for THIS connection and queue them into the durable outbox.
            launch {
                Log.d(TAG, "≡ƒöì Observing scribble summaries for connection: ${connection.connectionId}")
                scribbleRepository.getScribbleSummariesByConnection(connection.connectionId).collect { summaries ->
                    summaries
                        .filter { it.status == ScribbleStatus.PENDING || it.status == ScribbleStatus.FAILED }
                        .forEach { summary ->
                            outboundActionRepository.enqueueScribbleIfMissing(
                                connectionId = connection.connectionId,
                                scribbleId = summary.scribbleId,
                                requiresDisplayReceipt = true
                            )
                        }
                }
            }

            // 6. Process queued outbound actions with retry/backoff.
            launch {
                while (isActive) {
                    val isConnected = webSocketClient.isConnected(connection.connectionId)
                    if (isConnected) {
                        // RECOVERY: If any actions are stuck in SENDING for more than 30s, revert them to QUEUED.
                        outboundActionRepository.requeueTimedOutSendingActions(connection.connectionId, timeoutMillis = 30000L)

                        val dueActions = outboundActionRepository.getDueActions(
                            connection.connectionId,
                            System.currentTimeMillis(),
                            limit = 15
                        )
                        if (dueActions.isNotEmpty()) {
                            Log.d(TAG, "📤 [OUTBOUND] Found ${dueActions.size} actions for ${connection.connectionId}")
                            dueActions.forEach { action ->
                                processOutboundAction(connection.connectionId, action.id)
                                delay(100L) // Pace processing to avoid flooding socket buffer
                            }
                        }
                    } else {
                        Log.v(TAG, "📤 [OUTBOUND] WebSocket not connected for ${connection.connectionId}, skipping poll")
                    }
                    delay(ACTION_PROCESS_INTERVAL)
                }
            }
        }
    }

    private suspend fun resetStuckScribbles() {
        try {
            Log.i(TAG, "≡ƒöä [RECOVERY] Checking for stuck 'SENDING' scribbles...")
            // We use status directly from repository if possible, or fetch all and filter
            val sending = scribbleRepository.getScribbleSummariesByStatus(com.aman.gigi.model.ScribbleStatus.SENDING, limit = 100)
            if (sending.isNotEmpty()) {
                Log.w(TAG, "ΓÜá∩╕Å [RECOVERY] Found ${sending.size} stuck scribbles. Resetting to PENDING.")
                sending.forEach { 
                    scribbleRepository.updateScribbleStatus(it.scribbleId, com.aman.gigi.model.ScribbleStatus.PENDING)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Γ¥î [RECOVERY] Failed to reset stuck scribbles", e)
        }
    }

    private suspend fun processOutboundAction(connectionId: String, actionId: String) {
        val action = outboundActionRepository.getAction(actionId) ?: return
        if (!webSocketClient.isConnected(connectionId)) {
            Log.w(TAG, "ΓÜá∩╕Å [SYNC-LOOP] Cannot process action $actionId: Socket not connected for $connectionId")
            return
        }

        try {
            Log.i(TAG, "≡ƒôñ [SYNC-LOOP] Processing action $actionId (Type: ${action.actionType}, Attempt: ${action.attemptCount})")
            outboundActionRepository.markSending(action.id)

            when (action.actionType) {
                "scribble" -> {
                    val scribbleId = action.relatedScribbleId ?: action.id
                    val scribble = scribbleRepository.getScribbleById(scribbleId)
                    if (scribble == null) {
                        Log.e(TAG, "Γ¥î [SYNC-LOOP] Scribble payload missing for action $actionId")
                        outboundActionRepository.markFailedPermanent(action.id, "Scribble payload missing")
                        return
                    }

                    // TARGET FALLBACK: If we don't know the partner's ID, try to find it
                    val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
                    if (conn?.partnerDeviceId == null) {
                        Log.w(TAG, "ΓÜá∩╕Å [SYNC-LOOP] partnerDeviceId is NULL for $connectionId. Requesting presence snapshot to recover ID...")
                        queryPartnerPresence(connectionId, showPopup = false)
                    }

                    val sent = sendScribble(scribble, action.id)
                    if (!sent) {
                        Log.e(TAG, "Γ¥î [SYNC-LOOP] Scribble send failed for action $actionId")
                        scheduleActionRetry(action.id, action.attemptCount, "Scribble send failed")
                    } else {
                        Log.i(TAG, "Γ£à [SYNC-LOOP] Scribble sent successfully for action $actionId, waiting for ACK")
                    }
                }

                "remote_command" -> {
                    val envelope = SyncProtocol.buildEnvelope(
                        connectionId = connectionId,
                        senderDeviceId = deviceId,
                        // Not specifying recipientDeviceId to allow broadcast to all connected partners.
                        // This fixes issues where the sender has a stale partnerDeviceId.
                        recipientDeviceId = null, 
                        actionType = SyncProtocol.ACTION_REMOTE_COMMAND,
                        payload = JSONObject(action.payloadJson),
                        messageId = action.id,
                        requiresDisplayReceipt = action.requiresDisplayReceipt
                    )
                    val sent = webSocketClient.sendText(connectionId, envelope.toString())
                    if (!sent) {
                        Log.e(TAG, "❌ [SYNC-LOOP] Command send failed for action $actionId")
                        scheduleActionRetry(action.id, action.attemptCount, "Command send failed")
                    } else {
                        Log.i(TAG, "✅ [SYNC-LOOP] Command sent successfully for action $actionId. Marking as ACCEPTED immediately to keep queue moving.")
                        outboundActionRepository.markAccepted(action.id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Γ¥î [SYNC-LOOP] Failed to process outbound action ${action.id}", e)
            scheduleActionRetry(action.id, action.attemptCount, e.message ?: "Action processing failed")
        }
    }

    private suspend fun scheduleActionRetry(actionId: String, attemptCount: Int, error: String?) {
        if (attemptCount >= maxRetries) {
            outboundActionRepository.markFailedPermanent(actionId, error ?: "Retry budget exhausted")
            val scribble = scribbleRepository.getScribbleById(actionId)
            if (scribble != null) {
                scribbleRepository.updateScribbleStatus(actionId, ScribbleStatus.FAILED)
                _events.emit(SyncEvent.SendFailed(scribble.connectionId, error ?: "Retry budget exhausted"))
            }
            return
        }

        outboundActionRepository.markFailedRetryable(
            actionId = actionId,
            nextAttemptAt = System.currentTimeMillis() + calculateBackoffDelay(attemptCount),
            error = error
        )
    }

    private fun sendResumeSession(connection: com.aman.gigi.model.Connection) {
        scope.launch {
            try {
                val envelope = SyncProtocol.buildEnvelope(
                    connectionId = connection.connectionId,
                    senderDeviceId = deviceId,
                    actionType = SyncProtocol.ACTION_RESUME_SESSION,
                    payload = JSONObject().apply {
                        put("connectionCode", connection.connectionCode)
                        put("lastKnownPresenceAt", connection.lastSeenAt ?: 0L)
                        put("acknowledgedActionIds", JSONArray(outboundActionRepository.getTerminalActionIds(connection.connectionId, 100)))
                    },
                    requiresDisplayReceipt = false
                )
                webSocketClient.sendText(connection.connectionId, envelope.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Γ¥î Failed to send resume_session for ${connection.connectionId}", e)
            }
        }
    }

    suspend fun sendActionReceipt(connectionId: String, actionId: String, receiptType: String, error: String? = null) {
        val payload = JSONObject().apply {
            put("actionId", actionId)
            if (!error.isNullOrBlank()) put("error", error)
        }
        val envelope = SyncProtocol.buildEnvelope(
            connectionId = connectionId,
            senderDeviceId = deviceId,
            actionType = receiptType,
            payload = payload,
            requiresDisplayReceipt = false
        )
        webSocketClient.sendText(connectionId, envelope.toString())
    }

    private fun deriveLegacyStatus(transportState: TransportState, partnerPresence: PartnerPresence): String {
        return when {
            transportState == TransportState.NO_INTERNET -> "NO_INTERNET"
            transportState == TransportState.CONNECTING -> "DISCONNECTED"
            partnerPresence == PartnerPresence.OFFLINE -> "PARTNER_OFFLINE"
            else -> "CONNECTED"
        }
    }
    
    /**
     * Broadcasts current location/status as a heartbeat to a specific partner.
     * Useful for forcing a location update when opening camera.
     */
    suspend fun announcePresence(connectionId: String) {
        // Force a fresh heartbeat with current location
        sendHeartbeat(connectionId)
    }

    fun queryPartnerPresence(connectionId: String, showPopup: Boolean = true) {
        scope.launch {
            try {
                val lowerId = connectionId.lowercase()
                val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == lowerId }
                val request = SyncProtocol.buildEnvelope(
                    connectionId = lowerId,
                    senderDeviceId = deviceId,
                    actionType = SyncProtocol.ACTION_PRESENCE_SNAPSHOT,
                    payload = JSONObject().apply {
                        put("request", true)
                        put("connectionCode", conn?.connectionCode ?: lowerId)
                        put("deviceId", deviceId) // Inform server of our current device ID
                        put("deviceName", android.os.Build.MODEL)
                        put("showPopup", showPopup)
                    },
                    requiresDisplayReceipt = false
                )
                val sent = webSocketClient.sendText(lowerId, request.toString())
                if (sent) {
                    lastPresenceQueries[lowerId] = System.currentTimeMillis()
                    Log.i(TAG, "≡ƒôí Requested partner presence for $lowerId (popup=$showPopup)")
                } else {
                    Log.w(TAG, "ΓÜá∩╕Å Failed to request partner presence for $lowerId because the socket is not ready")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Γ¥î Failed to request partner presence for $connectionId", e)
            }
        }
    }

    private fun isPlaceholderPartnerName(name: String): Boolean {
        return name.equals("waiting...", ignoreCase = true) ||
            name.equals("joining...", ignoreCase = true) ||
            name.equals("Partner", ignoreCase = true) ||
            name.isBlank()
    }

    private fun persistServerIdentity(json: JSONObject) {
        bootstrapManager.persistMemberIdentity(
            memberId = json.optString("memberId").takeIf { it.isNotBlank() },
            authToken = json.optString("authToken").takeIf { it.isNotBlank() },
            phoneNumber = json.optString("phoneNumber").takeIf { it.isNotBlank() },
            displayName = json.optString("displayName").takeIf { it.isNotBlank() }
        )
    }

    private suspend fun updateConnectionMetadata(
        connectionId: String,
        creatorDeviceId: String? = null,
        partnerDeviceId: String? = null,
        partnerName: String? = null,
        role: String? = null,
        memberId: String? = null,
        isActive: Boolean? = null,
        relationshipType: String? = null
    ) {
        val existing = connectionRepository.getAllConnectionsOnce().find { it.connectionId == connectionId } ?: return

        val mergedRole = role?.takeIf { it.isNotBlank() } ?: existing.role
        val mergedPartnerName = partnerName?.takeIf { it.isNotBlank() }?.let { incomingName ->
            if (isPlaceholderPartnerName(existing.partnerName)) incomingName else existing.partnerName
        } ?: existing.partnerName

        connectionRepository.updateConnection(
            existing.copy(
                creatorDeviceId = creatorDeviceId?.takeIf { it.isNotBlank() } ?: existing.creatorDeviceId,
                partnerDeviceId = partnerDeviceId?.takeIf { it.isNotBlank() } ?: existing.partnerDeviceId,
                partnerName = mergedPartnerName,
                role = mergedRole,
                memberId = memberId?.takeIf { it.isNotBlank() } ?: existing.memberId,
                isActive = isActive ?: existing.isActive,
                relationshipType = relationshipType ?: existing.relationshipType,
                restoredAt = existing.restoredAt ?: System.currentTimeMillis()
            )
        )
    }

    private suspend fun applyPartnerPresence(
        connectionId: String,
        isOnline: Boolean,
        partnerNameHint: String? = null,
        partnerDeviceId: String? = null, // Added
        latitude: Double? = null,
        longitude: Double? = null,
        lastSeenAt: Long? = null,
        showPopup: Boolean = true,
        respondWithHeartbeat: Boolean = false
    ) {
        val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
        val cleanedPartnerName = partnerNameHint?.takeIf { it.isNotBlank() }
        val resolvedPartnerName = cleanedPartnerName ?: conn?.partnerName ?: "Partner"
        val effectivePresence = if (isOnline) PartnerPresence.ONLINE else PartnerPresence.OFFLINE
        val effectiveLastSeenAt = when {
            isOnline -> System.currentTimeMillis()
            lastSeenAt != null -> lastSeenAt
            else -> conn?.lastSeenAt
        }

        conn?.let { existing ->
            val updatedPartnerName = if (cleanedPartnerName != null && isPlaceholderPartnerName(existing.partnerName)) {
                cleanedPartnerName
            } else {
                existing.partnerName
            }
            val transportState = TransportState.entries.firstOrNull { it.name == existing.transportState } ?: TransportState.CONNECTING

            val updated = existing.copy(
                partnerName = updatedPartnerName,
                partnerDeviceId = partnerDeviceId?.takeIf { it.isNotBlank() } ?: existing.partnerDeviceId,
                partnerPresence = effectivePresence.name,
                partnerLatitude = latitude ?: existing.partnerLatitude,
                partnerLongitude = longitude ?: existing.partnerLongitude,
                lastSeenAt = effectiveLastSeenAt,
                connectionStatus = deriveLegacyStatus(transportState, effectivePresence)
            )

            if (existing != updated) {
                connectionRepository.updateConnection(
                    updated
                )
            }
        } ?: connectionRepository.updatePartnerPresence(connectionId, effectivePresence, effectiveLastSeenAt)

        if (isOnline) {
            lastPartnerActivity[connectionId] = System.currentTimeMillis()
            resumeInterruptedDownloads(connectionId)
        }

        _events.emit(
            SyncEvent.PartnerPresenceChanged(
                connectionId = connectionId,
                partnerName = resolvedPartnerName,
                isOnline = isOnline,
                latitude = latitude,
                longitude = longitude,
                lastSeenAt = effectiveLastSeenAt,
                showPopup = showPopup
            )
        )

        if (isOnline && respondWithHeartbeat) {
            sendHeartbeat(connectionId)
        }
    }

    private suspend fun sendHeartbeat(connectionId: String) {
        try {
            Log.d(TAG, "💓 [HEARTBEAT] Starting heartbeat for $connectionId...")
            val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }

            // Send legacy binary heartbeat
            val heartbeat = Scribble(
                scribbleId = java.util.UUID.randomUUID().toString(),
                connectionId = connectionId,
                strokes = emptyList(),
                isSent = true,
                mediaType = MEDIA_TYPE_HEARTBEAT,
                meetingDate = conn?.meetingDate,
                anniversaryDate = conn?.anniversaryDate
            )
            val json = ScribbleSerializer.serialize(heartbeat)
            val compressed = CompressionUtil.compress(json)
            val success = webSocketClient.sendBinary(connectionId, compressed)
            
            if (success) {
                Log.d(TAG, "💓 Sent binary heartbeat for $connectionId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Γ¥î Error sending heartbeat for $connectionId", e)
        }
    }

    /**
     * Sends a remote command to the partner on this connection
     */
    fun sendRemoteCommand(connectionId: String, command: String, targetDeviceId: String? = null) {
        sendRemoteCommandWithData(connectionId, command, null, targetDeviceId)
    }

    /**
     * Sends a remote command to the partner with optional data
     */
    fun sendRemoteCommandWithData(
        connectionId: String, 
        command: String, 
        data: org.json.JSONObject? = null,
        targetDeviceId: String? = null
    ) {
        Log.d(TAG, "📡 [COMMAND-DISPATCH] Dispatching $command for $connectionId (Target: $targetDeviceId)")
        scope.launch {
            try {
                val lowerId = connectionId.lowercase()
                val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == lowerId }
                
                // Resolve target devices (could be multiple for groups)
                val resolvedTargetIds = mutableListOf<String>()
                if (targetDeviceId != null) {
                    resolvedTargetIds.add(targetDeviceId)
                } else {
                    if (conn?.isGroup == true) {
                        val members = connectionRepository.getMembersForConnection(lowerId)
                        resolvedTargetIds.addAll(members.map { it.memberDeviceId }.filter { it != deviceId })
                    } else {
                        conn?.partnerDeviceId?.let { resolvedTargetIds.add(it) }
                    }
                }
                
                Log.i(TAG, "📤 Enqueuing remote command: $command for $lowerId (Targets: $resolvedTargetIds)")

                val isTransientDirect = listOf(
                    "COMMAND_FILE_UPLOADED",
                    com.aman.gigi.utils.Constants.COMMAND_POKE
                )

                val isPersistentDirect = listOf(
                    com.aman.gigi.utils.Constants.COMMAND_SET_RELATIONSHIP_TYPE,
                    com.aman.gigi.utils.Constants.COMMAND_SEND_QUOTE,
                    com.aman.gigi.utils.Constants.COMMAND_LOVE_CARD_STACK,
                    com.aman.gigi.utils.Constants.COMMAND_LOVE_CARD_OPENED,
                    com.aman.gigi.utils.Constants.COMMAND_LOVE_CARD_ANSWERED
                )

                if (resolvedTargetIds.isEmpty()) {
                    Log.w(TAG, "⚠️ [WebRTC-DIRECT] Cannot send $command: No target device IDs known for connection $lowerId")
                }

                for (resolvedTargetId in resolvedTargetIds) {
                    // HIGH-PRIORITY BYPASS: Send directly via WebSocket if possible for immediate start
                    if (isTransientDirect.contains(command) || isPersistentDirect.contains(command)) {
                        val directJson = org.json.JSONObject().apply {
                            put("type", "remote_command")
                            put("connectionId", lowerId)
                            put("command", command)
                            put("data", data ?: org.json.JSONObject())
                            put("senderDeviceId", deviceId)
                            put("recipientDeviceId", resolvedTargetId)
                        }
                        val sent = webSocketClient.sendText(lowerId, directJson.toString())
                        val isSocketConnected = webSocketClient.isConnected(lowerId)
                        Log.i(TAG, "📤 [WebRTC-DIRECT] Sent direct $command to $lowerId (Success=$sent, SocketStatus=$isSocketConnected, Target=$resolvedTargetId)")
                        
                        // Transient interactive control commands MUST NOT be persisted to the database.
                        if (isTransientDirect.contains(command)) {
                            continue
                        }
                    }

                    outboundActionRepository.enqueueRemoteCommand(
                        connectionId = lowerId,
                        command = command,
                        data = data,
                        targetDeviceId = resolvedTargetId
                    )
                }
                
                // The background processOutboundAction job will pick this up and send it
                // using the proper SyncProtocol envelope.
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue remote command: $command", e)
            }
        }
    }
    /**
     * Sends a relationship type update to the partner immediately via WebSocket
     */
    fun sendRelationshipType(
        connectionId: String,
        type: com.aman.gigi.model.RelationshipType,
        targetDeviceId: String? = null
    ) {
        Log.i(TAG, "💜 [THEME] Sending relationship type '${type.name}' for $connectionId")
        val data = org.json.JSONObject().apply {
            put("type", type.name)
        }
        sendRemoteCommandWithData(
            connectionId = connectionId,
            command = com.aman.gigi.utils.Constants.COMMAND_SET_RELATIONSHIP_TYPE,
            data = data,
            targetDeviceId = targetDeviceId
        )
    }

    fun updateFcmToken(connectionId: String, token: String) {
        scope.launch {
            try {
                val json = org.json.JSONObject().apply {
                    put("type", "update_fcm_token")
                    put("token", token)
                }
                webSocketClient.sendText(connectionId.lowercase(), json.toString())
                Log.i(TAG, "📲 Sent update_fcm_token for $connectionId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token", e)
            }
        }
    }

    fun updateFcmTokenGlobal(token: String) {
        scope.launch {
            try {
                bootstrapManager.registerFcmToken(token)
                val activeConnections = connectionRepository.getAllActiveConnectionsOnce()
                activeConnections.forEach { connection ->
                    if (connection.isActive) {
                        updateFcmToken(connection.connectionId, token)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token globally", e)
            }
        }
    }

    fun sendQuote(connectionId: String, text: String) {
        scope.launch {
            try {
                val data = org.json.JSONObject().apply {
                    put("text", text)
                    put("senderName", bootstrapManager.memberIdentity.value?.displayName ?: "Your partner")
                }
                sendRemoteCommandWithData(connectionId, com.aman.gigi.utils.Constants.COMMAND_SEND_QUOTE, data)
                Log.i(TAG, "💬 Sent quote to $connectionId: $text")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send quote", e)
            }
        }
    }

    fun sendLoveCardStack(connectionId: String, stackId: String, title: String, unlockDate: Long? = null, payloadCards: org.json.JSONArray) {
        scope.launch {
            try {
                val data = org.json.JSONObject().apply {
                    put("stackId", stackId)
                    put("title", title)
                    put("cards", payloadCards)
                    unlockDate?.let { put("unlockDate", it) }
                }
                sendRemoteCommandWithData(connectionId, com.aman.gigi.utils.Constants.COMMAND_LOVE_CARD_STACK, data)
                Log.i(TAG, "💌 Sent LoveCardStack to $connectionId: $title")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send love card stack", e)
            }
        }
    }

    fun sendLoveCardOpened(connectionId: String, stackId: String) {
        scope.launch {
            try {
                val data = org.json.JSONObject().apply {
                    put("stackId", stackId)
                }
                sendRemoteCommandWithData(connectionId, com.aman.gigi.utils.Constants.COMMAND_LOVE_CARD_OPENED, data)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send love card opened", e)
            }
        }
    }

    fun sendLoveCardAnswered(connectionId: String, stackId: String, responses: org.json.JSONArray) {
        scope.launch {
            try {
                val data = org.json.JSONObject().apply {
                    put("stackId", stackId)
                    put("responses", responses)
                }
                sendRemoteCommandWithData(connectionId, com.aman.gigi.utils.Constants.COMMAND_LOVE_CARD_ANSWERED, data)
                Log.i(TAG, "💌 Sent LoveCardAnswered for $stackId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send love card answered", e)
            }
        }
    }

    /**
     * Sends a notification search request to the server
     */
    fun sendSearchCommand(connectionId: String, query: String, packageName: String? = null) {
        scope.launch {
            try {
                val lowerId = connectionId.lowercase()
                val json = org.json.JSONObject().apply {
                    put("type", "search_notifications")
                    if (query.isNotBlank()) put("query", query)
                    if (!packageName.isNullOrBlank()) put("packageName", packageName)
                }
                webSocketClient.sendText(lowerId, json.toString())
                Log.i(TAG, "≡ƒöì Sent search_notifications to $lowerId: query='$query' pkg='$packageName'")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send search_notifications", e)
            }
        }
    }

    /**
     * Fetches the list of apps that have sent notifications (for categorization)
     */
    fun sendGetNotificationApps(connectionId: String) {
        scope.launch {
            try {
                val lowerId = connectionId.lowercase()
                val json = org.json.JSONObject().apply { put("type", "get_notification_apps") }
                webSocketClient.sendText(lowerId, json.toString())
                Log.i(TAG, "≡ƒô▒ Sent get_notification_apps to $lowerId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send get_notification_apps", e)
            }
        }
    }

    /**
     * Requests the full history (scribbles + notifications) from the server (MongoDB-backed).
     */
    fun sendGetHistory(connectionId: String) {
        scope.launch {
            try {
                val lowerId = connectionId.lowercase()
                val json = org.json.JSONObject().apply { put("type", "get_history") }
                webSocketClient.sendText(lowerId, json.toString())
                Log.i(TAG, "≡ƒô£ Sent get_history to $lowerId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send get_history", e)
            }
        }
    }

    private fun shouldMarkPartnerActive(message: SessionMessage): Boolean {
        if (message !is SessionMessage.TextMessage) return true

        return try {
            val envelope = SyncProtocol.parse(message.text)
            if (envelope != null) {
                return envelope.actionType !in setOf(
                SyncProtocol.ACTION_PRESENCE_SNAPSHOT,
                SyncProtocol.ACTION_PARTNER_STATUS_CHANGED,
                SyncProtocol.ACTION_RESUME_RESULT,
                SyncProtocol.ACTION_SERVER_STATUS,
                SyncProtocol.ACTION_ACTION_ACCEPTED,
                SyncProtocol.ACTION_ACTION_DELIVERED,
                SyncProtocol.ACTION_ACTION_DISPLAYED,
                    SyncProtocol.ACTION_ACTION_FAILED
                )
            }

            when (JSONObject(message.text).optString("type")) {
                "partner_presence",
                "partner_offline",
                "partner_disconnected",
                "session_info",
                "role_info",
                "connection_created",
                "connection_joined",
                "server_status",
                "error",
                "connection_idle_timeout" -> false
                else -> true
            }
        } catch (e: Exception) {
            true
        }
    }

    private fun handleInternalMessage(message: SessionMessage, connectionId: String) {
        if (message.connectionId == connectionId && shouldMarkPartnerActive(message)) {
            // Update last activity timestamp
            lastPartnerActivity[connectionId] = System.currentTimeMillis()
            scope.launch { _events.emit(SyncEvent.PresenceUpdate(connectionId)) }
        }
 
        when (message) {
            is SessionMessage.BinaryMessage -> {
                if (message.connectionId == connectionId) {
                    val data = message.data
                    
                    if (data.size > 20) {
                        try {
                            val potentialHeader = String(data.copyOfRange(0, kotlin.math.min(128, data.size)), Charsets.UTF_8)
                            if (potentialHeader.startsWith("WS_CHUNK_START:")) {
                                val parts = potentialHeader.trim().split(":")
                                if (parts.size >= 4) {
                                    val fileId = parts[1]
                                    val fileName = parts[2]
                                    val totalSize = parts[3].toLongOrNull() ?: 0L
                                    
                                    if (completedDownloads.contains("$connectionId:$fileName")) {
                                        Log.i(TAG, "⏭️ File $fileName already downloaded, skipping duplicate WS chunks.")
                                        return
                                    }
                                    
                                    val state = incomingFiles.getOrPut(fileId) { IncomingFileState() }
                                    val tempFile = java.io.File(context.cacheDir, "ws_incoming_$fileId.tmp")
                                    state.currentSize = if (tempFile.exists()) tempFile.length() else 0L
                                    state.fileName = fileName
                                    state.fileSize = totalSize
                                    state.tempFile = tempFile
                                    
                                    Log.i(TAG, "📁 [WS-CHUNK] Starting file receipt: $fileName ($totalSize bytes). Resuming from ${state.currentSize} bytes.")
                                    val initialProgress = if (totalSize > 0) (state.currentSize.toFloat() / totalSize * 100).toInt() else 0
                                    scope.launch { _events.emit(SyncEvent.FileDownloadProgress(connectionId, fileName, initialProgress)) }
                                    return
                                }
                            } else if (potentialHeader.startsWith("WS_CHUNK_DATA:")) {
                                val header64 = String(data.copyOfRange(0, kotlin.math.min(64, data.size)), Charsets.UTF_8)
                                val parts = header64.trim().split(":")
                                if (parts.size >= 2) {
                                    val fileId = parts[1]
                                    val chunkData = data.copyOfRange(64, data.size)
                                    
                                    val state = incomingFiles.getOrPut(fileId) { IncomingFileState() }
                                    val tempFile = state.tempFile ?: java.io.File(context.cacheDir, "ws_incoming_$fileId.tmp")
                                    java.io.FileOutputStream(tempFile, true).use { out ->
                                        out.write(chunkData)
                                    }
                                    state.currentSize += chunkData.size
                                    lastChunkReceivedTime = System.currentTimeMillis()
                                    val progress = if (state.fileSize > 0) (state.currentSize.toFloat() / state.fileSize * 100).toInt() else 0
                                    if (state.fileName != null && progress != state.lastEmittedProgress && progress in 0..100) {
                                        state.lastEmittedProgress = progress
                                        scope.launch { _events.emit(SyncEvent.FileDownloadProgress(connectionId, state.fileName!!, progress)) }
                                    }
                                    return
                                }
                            } else if (potentialHeader.startsWith("WS_CHUNK_END:")) {
                                val header64 = String(data.copyOfRange(0, kotlin.math.min(64, data.size)), Charsets.UTF_8)
                                val parts = header64.trim().split(":")
                                if (parts.size >= 2) {
                                    val fileId = parts[1]
                                    val state = incomingFiles.remove(fileId)
                                    val fileName = state?.fileName ?: "file"
                                    val tempFile = state?.tempFile ?: java.io.File(context.cacheDir, "ws_incoming_$fileId.tmp")
                                    if (tempFile.exists()) {
                                        Log.i(TAG, "📁 [WS-CHUNK] File receipt complete: $fileName (${tempFile.length()} bytes)")
                                        scope.launch {
                                            if (fileName.contains("photo") || fileName.contains(".jpg") || fileName.contains(".png")) {
                                                _events.emit(SyncEvent.PhotoDownloadReceived(connectionId, fileName, file = tempFile))
                                            } else {
                                                _events.emit(SyncEvent.FileDownloadReceived(connectionId, fileName, file = tempFile))
                                            }
                                        }
                                    }
                                    return
                                }
                            }

                            if (potentialHeader.startsWith("LIVE_VIDEO:")) {
                                val headerSize = "LIVE_VIDEO:".toByteArray(Charsets.UTF_8).size
                                val payload = data.copyOfRange(headerSize, data.size)
                                scope.launch { _events.emit(SyncEvent.LiveVideoFrameReceived(connectionId, payload)) }
                                return
                            } else if (potentialHeader.startsWith("PHOTO_DATA:")) {
                                val parts = potentialHeader.split(":")
                                if (parts.size >= 3) {
                                    val photoId = parts[1]
                                    val headerSize = "PHOTO_DATA:$photoId:".toByteArray(Charsets.UTF_8).size
                                    val imageBytes = data.copyOfRange(headerSize, data.size)
                                    Log.i(TAG, "≡ƒû╝∩╕Å Received full photo binary for $photoId (${imageBytes.size} bytes)")
                                    scope.launch { _events.emit(SyncEvent.PhotoDownloadReceived(connectionId, photoId, imageBytes)) }
                                    return
                                }
                            } else if (potentialHeader.startsWith("FILE_DATA:")) {
                                val secondColonIndex = potentialHeader.indexOf(":", "FILE_DATA:".length)
                                if (secondColonIndex != -1) {
                                    val fileId = potentialHeader.substring("FILE_DATA:".length, secondColonIndex)
                                    val thirdColonIndex = potentialHeader.indexOf(":", secondColonIndex + 1)
                                    if (thirdColonIndex != -1) {
                                        val fileName = potentialHeader.substring(secondColonIndex + 1, thirdColonIndex)
                                        val headerSize = "FILE_DATA:$fileId:$fileName:".toByteArray(Charsets.UTF_8).size
                                        val fileData = data.copyOfRange(headerSize, data.size)
                                        Log.i(TAG, "📁 Received file binary for $fileName (${fileData.size} bytes)")
                                        scope.launch {
                                            _events.emit(SyncEvent.FileDownloadProgress(connectionId, fileName, 0))
                                            _events.emit(SyncEvent.FileDownloadProgress(connectionId, fileName, 100))
                                            _events.emit(SyncEvent.FileDownloadReceived(connectionId, fileName, fileData))
                                        }
                                        return
                                    }
                                }
                            } else if (potentialHeader.startsWith("AUDIO_DATA:")) {
                                val parts = potentialHeader.split(":")
                                if (parts.size >= 3) {
                                    val audioId = parts[1]
                                    val headerSize = "AUDIO_DATA:$audioId:".toByteArray(Charsets.UTF_8).size
                                    val audioBytes = data.copyOfRange(headerSize, data.size)
                                    Log.i(TAG, "≡ƒÄñ Received remote audio binary for $audioId (${audioBytes.size} bytes)")
                                    scope.launch { _events.emit(SyncEvent.AudioDownloadReceived(connectionId, audioId, audioBytes)) }
                                    return
                                }
                            }
                        } catch (e: Exception) { /* Not a custom binary header */ }
                    }

                    // Try to detect binary ACK or Remote Command (small uncompressed JSON)
                    if (data.size < 500) {
                        try {
                            val potentialJson = String(data, Charsets.UTF_8)
                            val json = org.json.JSONObject(potentialJson)
                            val type = json.optString("type")
                            
                            if (type == "ack") {
                                Log.i(TAG, "≡ƒÄ» Received Binary ACK for connection $connectionId")
                                handlePairingMessage(potentialJson, connectionId)
                                return
                            } else if (type == "remote_command") {
                                Log.i(TAG, "≡ƒò╡∩╕Å Received Remote Command for connection $connectionId: ${json.optString("command")}")
                                scope.launch { handleRemoteCommand(json, connectionId) }
                                return
                            }
                        } catch (e: Exception) { /* Not a text command/ack */ }
                    }


                    Log.d(TAG, "∩┐╜≡ƒôª Received heartbeat (${data.size} bytes) for connection: $connectionId")
                
                    // --- Partner Location Update ---
                    scope.launch {
                        val scribble = handleIncomingScribble(data, connectionId)
                        if (scribble?.mediaType == MEDIA_TYPE_HEARTBEAT && (scribble.latitude != null || scribble.longitude != null)) {
                            Log.i(TAG, "≡ƒôì Updating partner location & timeline: ${scribble.latitude}, ${scribble.longitude}")
                            val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
                            conn?.let {
                                connectionRepository.updateConnection(it.copy(
                                    partnerLatitude = scribble.latitude,
                                    partnerLongitude = scribble.longitude,
                                    meetingDate = scribble.meetingDate ?: it.meetingDate,
                                    anniversaryDate = scribble.anniversaryDate ?: it.anniversaryDate,
                                    lastSyncedAt = System.currentTimeMillis()
                                ))
                            }
                        }
                    }
                }
            }
            is SessionMessage.TextMessage -> {
                if (message.connectionId == connectionId) {
                    Log.d(TAG, "≡ƒô¿ Received raw text message for $connectionId: ${message.text}")
                    try {
                        val protocolEnvelope = SyncProtocol.parse(message.text)
                        if (protocolEnvelope != null) {
                            scope.launch { handleProtocolEnvelope(protocolEnvelope, connectionId) }
                            return
                        }
                        val json = org.json.JSONObject(message.text)
                        val type = json.optString("type")
                        
                        when (type) {
                            "remote_command" -> {
                                scope.launch { handleRemoteCommand(json, connectionId) }
                            }
                            "connection_idle_timeout" -> {
                                val idleMinutes = json.optInt("idleMinutes", 30)
                                Log.w(TAG, "ΓÅ░ [IDLE-TIMEOUT] Connection $connectionId idle for $idleMinutes minutes. Server closing...")
                                scope.launch { 
                                    _events.emit(SyncEvent.IdleTimeout(connectionId, idleMinutes))
                                    connectionRepository.updateConnectionStatus(connectionId, "IDLE_TIMEOUT")
                                }
                            }
                            "search_results" -> {
                                val category = json.optString("category", "notifications")
                                val resultsArray = json.optJSONArray("results")
                                val notifications = mutableListOf<com.aman.gigi.model.RemoteNotification>()
                                if (resultsArray != null) {
                                    for (i in 0 until resultsArray.length()) {
                                        val item = resultsArray.getJSONObject(i)
                                        val data = item.optJSONObject("data")
                                        notifications.add(com.aman.gigi.model.RemoteNotification(
                                            id = item.optString("_id", java.util.UUID.randomUUID().toString()),
                                            packageName = data?.optString("package_name") ?: "",
                                            title = data?.optString("title") ?: "",
                                            text = data?.optString("text") ?: "",
                                            timestamp = item.optLong("createdAt", System.currentTimeMillis())
                                        ))
                                    }
                                }
                                scope.launch { _events.emit(SyncEvent.SearchResults(category, notifications)) }
                            }
                            "notification_apps_result" -> {
                                val appsArray = json.optJSONArray("apps")
                                val apps = mutableListOf<String>()
                                if (appsArray != null) {
                                    for (i in 0 until appsArray.length()) apps.add(appsArray.getString(i))
                                }
                                scope.launch { _events.emit(SyncEvent.NotificationAppsResult(apps)) }
                            }
                            "chat_message" -> {
                                scope.launch { handleIncomingChat(json, connectionId) }
                            }
                            // Live tab — relayed straight to the bus; the ViewModel
                            // decides what's relevant to whatever is on screen.
                            "live_post_new" -> LiveEventBus.emit(LiveEvent.PostAdded)
                            "live_join_request" -> LiveEventBus.emit(
                                LiveEvent.JoinRequested(
                                    json.optString("postId"),
                                    json.optString("memberId"),
                                    json.optString("name", "Someone")
                                )
                            )
                            "live_join_accepted", "live_join_declined" -> LiveEventBus.emit(
                                LiveEvent.JoinAnswered(
                                    json.optString("postId"),
                                    json.optString("memberId"),
                                    json.optString("type") == "live_join_accepted"
                                )
                            )
                            "live_location" -> LiveEventBus.emit(
                                LiveEvent.PeerLocation(
                                    postId = json.optString("postId"),
                                    memberId = json.optString("memberId"),
                                    name = json.optString("name", "Someone"),
                                    avatarUrl = json.optString("avatarUrl")
                                        .takeIf { it.isNotBlank() && it != "null" },
                                    lat = json.optDouble("lat"),
                                    lng = json.optDouble("lng"),
                                    heading = json.optDouble("heading").toFloat()
                                        .takeIf { !json.isNull("heading") }
                                )
                            )
                            "live_post_done" -> LiveEventBus.emit(
                                LiveEvent.PostDone(json.optString("postId"))
                            )
                            // Our Nest (Cozy Shared Room) events
                            "nest_room_update" -> NestEventBus.emit(
                                NestEvent.RoomUpdated(connectionId, json)
                            )
                            "nest_move" -> NestEventBus.emit(
                                NestEvent.PartnerMoved(
                                    connectionCode = connectionId,
                                    x = json.optDouble("x", 0.5).toFloat(),
                                    y = json.optDouble("y", 0.5).toFloat(),
                                    anim = json.optString("anim", "walk"),
                                    facingLeft = json.optBoolean("facingLeft", false)
                                )
                            )
                            "nest_emote" -> {
                                if (json.optString("action") == "pet_interact") {
                                    NestEventBus.emit(
                                        NestEvent.PetInteracted(
                                            connectionCode = connectionId,
                                            actorName = json.optString("actorName", "Partner"),
                                            action = json.optString("petAction", "PET"),
                                            pet = PetState.fromJson(json.optJSONObject("pet"))
                                        )
                                    )
                                } else {
                                    NestEventBus.emit(
                                        NestEvent.EmoteSent(
                                            connectionCode = connectionId,
                                            actorName = json.optString("actorName", "Partner"),
                                            emote = json.optString("emote", "💖")
                                        )
                                    )
                                }
                            }
                            // Admin pushed new remote settings (kill switches, keys,
                            // release gating) — apply without waiting for a relaunch.
                            "app_settings_update" -> com.aman.gigi.utils.AppConfig
                                .applySettingsJson(json.optJSONObject("settings"))
                            "now_playing" -> handleIncomingNowPlaying(json, connectionId)
                            "break_invite" -> handleIncomingBreakInvite(json, connectionId)
                            "break_response" -> handleIncomingBreakResponse(json)
                            "profile_update" -> {
                                scope.launch { handleIncomingProfileUpdate(json, connectionId) }
                            }
                            "group_emoji" -> {
                                // Someone changed the group's shared emoji — apply it live.
                                val emojiUrl = json.optString("emojiUrl")
                                if (emojiUrl.isNotBlank()) {
                                    scope.launch {
                                        connectionRepository.getAllActiveConnectionsOnce()
                                            .find { it.connectionId.equals(connectionId, ignoreCase = true) }
                                            ?.let { connectionRepository.updateConnection(it.copy(partnerEmojiUrl = emojiUrl)) }
                                        // Keep the local galaxy pick in step too.
                                        context.getSharedPreferences("galaxy_orbits", android.content.Context.MODE_PRIVATE)
                                            .edit().putString("emoji_${connectionId.lowercase()}", emojiUrl).apply()
                                    }
                                }
                            }
                            "plan_update", "plan_config_update" -> {
                                // Admin changed global plan config or member's tier — apply live.
                                val appCfg = json.optJSONObject("appConfig")
                                if (appCfg != null) {
                                    com.aman.gigi.utils.AppConfig.applyServerConfigJson(appCfg)
                                } else {
                                    json.optJSONObject("planConfig")?.optString("upgradeUrl")?.let { u ->
                                        if (u.isNotBlank()) com.aman.gigi.utils.AppConfig.upgradeUrl = u
                                    }
                                }
                            }
                            "group_invite" -> {
                                val groupCode = json.optString("groupCode")
                                val groupName = json.optString("groupName", "Group")
                                val inviterName = json.optString("inviterName", "A friend")
                                if (groupCode.isNotBlank()) {
                                    scope.launch {
                                        _events.emit(SyncEvent.GroupInviteReceived(groupCode, groupName, inviterName))
                                    }
                                }
                            }
                            "force_logout" -> {
                                val reason = json.optString("reason", "Server reset")
                                Log.w(TAG, "🔴 [FORCE-LOGOUT] Received force_logout: $reason")
                                scope.launch { _events.emit(SyncEvent.ForceLogout(reason)) }
                            }
                            "connection_removed", "connection_archived", "force_disconnect" -> {
                                val code = json.optString("connectionCode", json.optString("connectionId", connectionId))
                                val reason = json.optString("reason", "Connection removed")
                                Log.w(TAG, "🗑️ [CONNECTION-REMOVED] connectionCode=$code reason=$reason")
                                scope.launch {
                                    // Mark the connection as inactive/deleted locally so galaxy removes it
                                    try { connectionRepository.deleteConnection(code) } catch (_: Exception) {}
                                    _events.emit(SyncEvent.ConnectionRemoved(code, reason))
                                }
                            }
                            else -> {
                                handlePairingMessage(message.text, connectionId)
                            }
                        }
                    } catch (e: Exception) {
                        handlePairingMessage(message.text, connectionId)
                    }
                }
            }
        }
    }

    private suspend fun handleProtocolEnvelope(envelope: SyncProtocol.Envelope, connectionId: String) {
        // PROACTIVE ID UPDATE: Always update the partner's device ID if we receive a message from them
        if (!envelope.senderDeviceId.isNullOrBlank() && envelope.senderDeviceId != "server") {
            val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
            if (conn != null && conn.partnerDeviceId != envelope.senderDeviceId) {
                Log.i(TAG, "🆔 [ID-SYNC] Updating partnerDeviceId for $connectionId from ${conn.partnerDeviceId} to ${envelope.senderDeviceId}")
                connectionRepository.updateConnection(conn.copy(partnerDeviceId = envelope.senderDeviceId))
            }
        }

        Log.i(TAG, "📥 [INCOMING] actionType=${envelope.actionType} from=${envelope.senderDeviceId}")

        // If the server is delivering messages for this connection, the socket is alive —
        // assert CONNECTED so a stale "Reconnecting to server…" (e.g. left over from a
        // network blip or app resume) clears immediately. De-duped, so this is cheap.
        setTransportState(connectionId, TransportState.CONNECTED)

        when (envelope.actionType) {
            SyncProtocol.ACTION_PRESENCE_SNAPSHOT,
            SyncProtocol.ACTION_PARTNER_STATUS_CHANGED -> {
                applyPartnerPresence(
                    connectionId = connectionId,
                    isOnline = envelope.payload.optBoolean("isOnline", false),
                    partnerNameHint = envelope.payload.optString("partnerDeviceName").takeIf { it.isNotBlank() },
                    partnerDeviceId = envelope.payload.optString("partnerDeviceId").takeIf { it.isNotBlank() },
                    latitude = envelope.payload.optDouble("latitude").takeIf { !it.isNaN() },
                    longitude = envelope.payload.optDouble("longitude").takeIf { !it.isNaN() },
                    lastSeenAt = envelope.payload.optLong("lastSeenAt").takeIf { it > 0L },
                    showPopup = envelope.payload.optBoolean("showPopup", true),
                    respondWithHeartbeat = false // DISABLED: Prevents ping-pong loop
                )
            }

            SyncProtocol.ACTION_RESUME_RESULT -> {
                val partnerPayload = envelope.payload.optJSONObject("partner") ?: JSONObject()
                applyPartnerPresence(
                    connectionId = connectionId,
                    isOnline = partnerPayload.optBoolean("isOnline", false),
                    partnerNameHint = partnerPayload.optString("partnerDeviceName").takeIf { it.isNotBlank() },
                    latitude = partnerPayload.optDouble("latitude").takeIf { !it.isNaN() },
                    longitude = partnerPayload.optDouble("longitude").takeIf { !it.isNaN() },
                    lastSeenAt = partnerPayload.optLong("lastSeenAt").takeIf { it > 0L },
                    showPopup = false
                )

                val receipts = envelope.payload.optJSONArray("receipts")
                if (receipts != null) {
                    for (i in 0 until receipts.length()) {
                        val receipt = receipts.optJSONObject(i) ?: continue
                        applyActionStatusUpdate(
                            actionId = receipt.optString("actionId"),
                            status = receipt.optString("status"),
                            error = receipt.optString("lastError").ifBlank { null }
                        )
                    }
                }
            }

            SyncProtocol.ACTION_SERVER_STATUS -> {
                bootstrapManager.handleSocketServerStatus(
                    mode = envelope.payload.optString("mode").ifBlank { null },
                    message = envelope.payload.optString("message").ifBlank { null }
                )
            }

            SyncProtocol.ACTION_ACTION_ACCEPTED -> {
                applyActionStatusUpdate(
                    actionId = envelope.payload.optString("actionId").ifBlank { envelope.messageId },
                    status = "ACCEPTED",
                    error = null
                )
            }

            SyncProtocol.ACTION_ACTION_DELIVERED -> {
                applyActionStatusUpdate(
                    actionId = envelope.payload.optString("actionId").ifBlank { envelope.messageId },
                    status = "DELIVERED",
                    error = null
                )
            }

            SyncProtocol.ACTION_ACTION_DISPLAYED -> {
                applyActionStatusUpdate(
                    actionId = envelope.payload.optString("actionId").ifBlank { envelope.messageId },
                    status = "DISPLAYED",
                    error = null
                )
            }

            SyncProtocol.ACTION_ACTION_FAILED -> {
                applyActionStatusUpdate(
                    actionId = envelope.payload.optString("actionId").ifBlank { envelope.messageId },
                    status = "FAILED",
                    error = envelope.payload.optString("error").ifBlank { "Delivery failed" }
                )
            }

            SyncProtocol.ACTION_REMOTE_COMMAND -> {
                val commandJson = JSONObject().apply {
                    put("type", "remote_command")
                    put("command", envelope.payload.optString("command"))
                    val data = envelope.payload.optJSONObject("data")
                    if (data != null) put("data", data)
                }
                handleRemoteCommand(
                    json = commandJson,
                    connectionId = connectionId,
                    actionId = envelope.messageId
                )
            }
        }
    }

    private suspend fun applyActionStatusUpdate(actionId: String, status: String, error: String?) {
        if (actionId.isBlank()) return

        when (status.uppercase()) {
            "ACCEPTED" -> {
                outboundActionRepository.markAccepted(actionId)
                val scribble = scribbleRepository.getScribbleById(actionId)
                if (scribble != null) {
                    scribbleRepository.markAsSent(actionId)
                    _events.emit(SyncEvent.SendSuccess(scribble.connectionId, actionId))
                }
            }
            "DELIVERED" -> outboundActionRepository.markDelivered(actionId)
            "DISPLAYED" -> outboundActionRepository.markDisplayed(actionId)
            else -> {
                outboundActionRepository.markFailedRetryable(
                    actionId = actionId,
                    nextAttemptAt = System.currentTimeMillis() + calculateBackoffDelay(0),
                    error = error
                )
                val scribble = scribbleRepository.getScribbleById(actionId)
                if (scribble != null) {
                    scribbleRepository.updateScribbleStatus(actionId, ScribbleStatus.FAILED)
                    _events.emit(SyncEvent.SendFailed(scribble.connectionId, error ?: "Delivery failed"))
                }
            }
        }
    }

    private suspend fun handleRemoteCommand(
        json: org.json.JSONObject,
        connectionId: String,
        actionId: String? = null,
        senderDeviceId: String? = null
    ) {
        val command = json.optString("command")
        Log.i(TAG, "📡 [REMOTE-COMMAND] Received: $command for $connectionId. Full JSON: $json")
        
        val activeConn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
        val senderName = if (activeConn != null && senderDeviceId != null && activeConn.isGroup) {
            connectionRepository.getMembersForConnection(connectionId).find { it.memberDeviceId == senderDeviceId }?.memberName ?: activeConn.partnerName
        } else {
            activeConn?.partnerName ?: "Partner"
        }
        val creatorId = activeConn?.creatorDeviceId
        val isCreator = creatorId?.equals(deviceId, ignoreCase = true) == true
        
        Log.d(TAG, "🕵️ [ROLE-CHECK] Command: $command, Conn: $connectionId, CreatorID: $creatorId, MyID: $deviceId, isCreator: $isCreator")
        
        when (command) {
            com.aman.gigi.utils.Constants.COMMAND_GROUP_NAME_CHANGED -> {
                val newName = json.optJSONObject("data")?.optString("newName").orEmpty()
                if (newName.isNotBlank()) {
                    val conn = connectionRepository.getAllActiveConnectionsOnce()
                        .find { it.connectionId == connectionId }
                    conn?.let { connectionRepository.updateConnection(it.copy(partnerName = newName)) }
                    Log.i(TAG, "📛 [GROUP] Received group name change for $connectionId → \"$newName\"")
                }
            }
            com.aman.gigi.utils.Constants.COMMAND_MEMBER_REMOVED -> {
                val removedId = json.optJSONObject("data")?.optString("memberDeviceId").orEmpty()
                if (removedId == deviceId) {
                    Log.i(TAG, "👋 [GROUP] This device was removed from group $connectionId — cleaning up locally")
                    connectionRepository.deleteAllMembersForConnection(connectionId)
                    connectionRepository.deleteConnection(connectionId)
                    _events.emit(SyncEvent.PartnerDisconnected(connectionId, "You were removed from the group"))
                }
            }
            "PARTNER_TYPING_START" -> {
                _isPartnerTyping.value = _isPartnerTyping.value + (connectionId to true)
            }
            "PARTNER_TYPING_STOP" -> {
                _isPartnerTyping.value = _isPartnerTyping.value + (connectionId to false)
            }
            "PARTNER_DRAWING_START" -> {
                _isPartnerDrawing.value = _isPartnerDrawing.value + (connectionId to true)
            }
            "PARTNER_DRAWING_STOP" -> {
                _isPartnerDrawing.value = _isPartnerDrawing.value + (connectionId to false)
            }
            "COMMAND_SHARE_ALBUM" -> {
                val albumName = json.optJSONObject("data")?.optString("name").orEmpty()
                val songsArray = json.optJSONObject("data")?.optJSONArray("songs")
                if (albumName.isNotBlank() && songsArray != null) {
                    val sharedSongs = mutableListOf<com.aman.gigi.data.music.SharedSong>()
                    for (i in 0 until songsArray.length()) {
                        val obj = songsArray.getJSONObject(i)
                        sharedSongs.add(com.aman.gigi.data.music.SharedSong(
                            title = obj.getString("title"),
                            artist = obj.getString("artist"),
                            album = obj.getString("album"),
                            durationMs = obj.optLong("durationMs", 0L)
                        ))
                    }
                    scope.launch {
                        sharedAlbumStore.saveSharedAlbum(
                            name = albumName,
                            senderName = senderName,
                            senderConnectionId = connectionId,
                            songs = sharedSongs
                        )
                        _events.emit(SyncEvent.SharedAlbumReceived(connectionId, albumName))
                    }
                }
            }
            com.aman.gigi.utils.Constants.COMMAND_SET_RELATIONSHIP_TYPE -> {
                val type = json.optJSONObject("data")?.optString("type") ?: "ROMANTIC"
                Log.i(TAG, "💜 [THEME] Received relationship type update: $type for $connectionId")
                scope.launch {
                    val conn = connectionRepository.getAllActiveConnectionsOnce()
                        .find { it.connectionId == connectionId }
                    if (conn != null) {
                        connectionRepository.updateConnection(conn.copy(relationshipType = type))
                        Log.i(TAG, "✅ [THEME] Relationship type updated to $type for $connectionId")
                    } else {
                        Log.w(TAG, "⚠️ [THEME] Could not find connection $connectionId to update theme")
                    }
                }
            }
            "COMMAND_FILE_UPLOADED" -> {
                val data = json.optJSONObject("data")
                val fileName = data?.optString("fileName")
                val path = data?.optString("path")
                val fileSize = data?.optLong("fileSize", -1L) ?: -1L
                if (fileName != null) {
                    val activeConn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
                    val isCreator = activeConn?.creatorDeviceId?.equals(deviceId, ignoreCase = true) == true
                    if (!isCreator) {
                        Log.i(TAG, "⏭️ [ROLE-CHECK] Skipping file download for partner device since only creator handles partner downloads.")
                        return
                    }
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val targetFileDisk = java.io.File(java.io.File(downloadsDir, "Gigi"), fileName)
                    if (completedDownloads.contains("$connectionId:$fileName") || targetFileDisk.exists()) {
                        Log.i(TAG, "⏭️ File $fileName already downloaded (memory or disk), skipping duplicate.")
                        return
                    }
                    val dKey = "$connectionId:$fileName"
                    if (activeDownloads.contains(dKey)) {
                        Log.i(TAG, "⏭️ File $fileName is already actively downloading. Skipping duplicate trigger.")
                        return
                    }
                    activeDownloads.add(dKey)
                    scope.launch {
                        try {
                            Log.i(TAG, "📥 [FILE] Fetching pre-signed URL for $fileName")
                            val token = bootstrapManager.memberIdentity.value?.authToken
                            val downloadUrl = httpUploader.getPresignedUrl(isUpload = false, fileName = fileName, sessionToken = token)
                            if (downloadUrl != null) {
                                com.aman.gigi.utils.AppLogs.addLog("SYNC", "📥 Starting direct download for $fileName")
                                val tempFile = java.io.File(context.cacheDir, "minio_incoming_$fileName")
                                val isComp = data.optBoolean("isCompressed", false)
                                val targetFile = if (isComp) java.io.File(context.cacheDir, "minio_compressed_$fileName") else tempFile
                                
                                var success = false
                                var retries = 0
                                while (!success && retries < 4) {
                                    if (targetFile.exists()) {
                                        targetFile.delete()
                                    }
                                    Log.i(TAG, "📥 [MinIO] Attempt ${retries + 1} for $fileName")
                                    
                                    val currentUrl = if (retries == 0) downloadUrl else httpUploader.getPresignedUrl(isUpload = false, fileName = fileName, sessionToken = token) ?: downloadUrl

                                    var lastProg = -1
                                    success = httpUploader.parallelDownloadFromPresignedUrl(
                                        currentUrl, targetFile, offset = 0L, expectedSize = fileSize,
                                        progressListener = object : com.aman.gigi.network.HttpUploader.OnProgressListener {
                                            override fun onProgress(bytesRead: Long, totalBytes: Long, isDone: Boolean) {
                                                val p = (if (totalBytes > 0) (bytesRead.toFloat() / totalBytes * 100).toInt() else 0).coerceIn(0, 100)
                                                if (p != lastProg) {
                                                    lastProg = p
                                                    com.aman.gigi.utils.AppLogs.addLog("SYNC", "📥 Download progress for $fileName: $p%")
                                                    scope.launch { _events.emit(SyncEvent.FileDownloadProgress(connectionId, fileName, p)) }
                                                }
                                            }
                                        }
                                    )
                                    if (!success) {
                                        retries++
                                        kotlinx.coroutines.delay(2000L)
                                    }
                                }
                                
                                if (success) {
                                    com.aman.gigi.utils.AppLogs.addLog("SYNC", "✅ Successfully downloaded $fileName")
                                    if (isComp) {
                                        httpUploader.decompressFileGzip(targetFile, tempFile)
                                        targetFile.delete()
                                    }
                                    
                                    val expectedChecksum = data.optString("checksum")
                                    if (expectedChecksum.isNotEmpty()) {
                                        val actualChecksum = httpUploader.computeSHA256(tempFile)
                                        if (actualChecksum != expectedChecksum) {
                                            Log.e(TAG, "❌ [Checksum-Error] Checksum mismatch for $fileName! Discarding.")
                                            tempFile.delete()
                                            return@launch
                                        }
                                        Log.i(TAG, "✅ [Checksum-Match] File verified successfully for $fileName")
                                    }
                                    
                                    completedDownloads.add("$connectionId:$fileName")
                                    if (fileName.contains("photo") || fileName.contains(".jpg") || fileName.contains(".png")) {
                                        _events.emit(SyncEvent.PhotoDownloadReceived(connectionId, fileName, file = tempFile))
                                    } else {
                                        _events.emit(SyncEvent.FileDownloadReceived(connectionId, fileName, file = tempFile))
                                    }
                                    return@launch
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ [MinIO] Error downloading file: $fileName", e)
                        } finally {
                            activeDownloads.remove(dKey)
                        }
                    }
                }
            }
            com.aman.gigi.utils.Constants.COMMAND_SEND_QUOTE -> {
                val data = json.optJSONObject("data")
                data?.let {
                    val text = it.getString("text")
                    val payloadSenderName = it.optString("senderName", "")
                    val finalSenderName = payloadSenderName.ifBlank { senderName }
                    scope.launch { 
                        _events.emit(SyncEvent.QuoteReceived(connectionId, text, finalSenderName)) 
                    }
                }
            }
            com.aman.gigi.utils.Constants.COMMAND_LOVE_CARD_STACK -> {
                val data = json.optJSONObject("data")
                data?.let {
                    val stackId = it.getString("stackId")
                    val title = it.getString("title")
                    val cards = it.getJSONArray("cards")
                    val payloadSenderName = it.optString("senderName", "")
                    val finalSenderName = payloadSenderName.ifBlank { senderName }
                    val unlockDate = it.optLong("unlockDate").takeIf { it > 0L }
                    
                    scope.launch {
                        android.util.Log.i("ScribbleSyncManager", "📥 [LOVECARD] Received stack: $stackId, title: $title, cards: ${cards.length()}")
                        // Store the incoming deck locally first
                        loveCardRepository.receiveIncomingDeck(
                            connectionId = connectionId,
                            stackId = stackId,
                            title = title,
                            senderName = finalSenderName,
                            unlockDate = unlockDate,
                            cardsJson = cards.toString()
                        )
                        android.util.Log.i("ScribbleSyncManager", "✅ [LOVECARD] Stack saved to DB: $stackId")
                        _events.emit(SyncEvent.LoveCardStackReceived(
                            connectionId = connectionId,
                            stackId = stackId,
                            title = title,
                            cardCount = cards.length(),
                            senderName = finalSenderName
                        ))
                        
                        // Show system push notification if in background
                        val isForeground = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                        if (!isForeground) {
                            com.aman.gigi.utils.NotificationHelper.showLoveCardNotification(context, finalSenderName, stackId)
                        }
                        
                        // Schedule unlock alarm if this is a time capsule for the future
                        if (unlockDate != null && unlockDate > System.currentTimeMillis()) {
                            com.aman.gigi.utils.NotificationHelper.scheduleTimeCapsuleUnlock(context, finalSenderName, stackId, unlockDate)
                        }
                    }
                }
            }
            com.aman.gigi.utils.Constants.COMMAND_LOVE_CARD_OPENED -> {
                val data = json.optJSONObject("data")
                data?.let {
                    val stackId = it.getString("stackId")
                    scope.launch {
                        loveCardRepository.markDeckAsPartnerOpened(stackId)
                    }
                }
            }
            com.aman.gigi.utils.Constants.COMMAND_LOVE_CARD_ANSWERED -> {
                val data = json.optJSONObject("data")
                data?.let {
                    val stackId = it.getString("stackId")
                    val responses = it.getJSONArray("responses")
                    scope.launch {
                        val title = loveCardRepository.receiveAnswers(stackId, responses.toString())
                        _events.emit(SyncEvent.LoveCardStackAnswered(
                            connectionId = connectionId,
                            stackId = stackId,
                            title = title ?: "Love Cards",
                            answerCount = responses.length()
                        ))
                    }
                }
            }
            com.aman.gigi.utils.Constants.COMMAND_PARTNER_PROFILE_UPDATED -> {
                val data = json.optJSONObject("data")
                data?.let {
                    val payloadPartnerName = it.optString("partnerName", "Partner")
                    val finalPartnerName = if (senderName != "Partner") senderName else payloadPartnerName
                    val partnerAvatar = it.optString("partnerAvatar", null)
                    scope.launch {
                        _events.emit(SyncEvent.PartnerProfileUpdated(connectionId, finalPartnerName, partnerAvatar))
                    }
                }
            }
            com.aman.gigi.utils.Constants.COMMAND_EXCHANGE_MUSIC_HISTORY -> {
                val data = json.optJSONObject("data")
                data?.let {
                    val partnerHistoryArray = it.optJSONArray("history")
                    val partnerHistory = mutableSetOf<String>()
                    if (partnerHistoryArray != null) {
                        for (i in 0 until partnerHistoryArray.length()) {
                            partnerHistory.add(partnerHistoryArray.getString(i))
                        }
                    }
                    val prefs = context.getSharedPreferences("gigi_music_prefs", android.content.Context.MODE_PRIVATE)
                    val localHistory = prefs.getStringSet("listened_artists", emptySet()) ?: emptySet()
                    val intersection = localHistory.intersect(partnerHistory).size
                    val union = localHistory.union(partnerHistory).size
                    val score = if (union == 0) 0 else ((intersection.toFloat() / union) * 100).toInt().coerceIn(0, 100)
                    
                    scope.launch {
                        _events.emit(SyncEvent.MusicCompatibilityReceived(connectionId, score))
                    }
                    
                    val isReply = it.optBoolean("isReply", false)
                    if (!isReply) {
                        val replyPayload = org.json.JSONObject().apply {
                            put("history", org.json.JSONArray(localHistory.toList()))
                            put("isReply", true)
                        }
                        scope.launch {
                            sendRemoteCommandWithData(connectionId, com.aman.gigi.utils.Constants.COMMAND_EXCHANGE_MUSIC_HISTORY, replyPayload)
                        }
                    }
                }
            }
            "COMMAND_ALARM_DONE_TOGETHER" -> {
                val data = json.optJSONObject("data")
                data?.let {
                    val alarmTitle = it.optString("alarmTitle", "Alarm")
                    val text = it.optString("text", "Done Together!")
                    val emoji = it.optString("emoji", "??")
                    scope.launch {
                        _events.emit(SyncEvent.AlarmDoneTogether(connectionId, alarmTitle, text, emoji))
                    }
                }
            }
            else -> {
                Log.w(TAG, "Unknown native remote command: $command. Emitting as generic RemoteCommand event.")
                scope.launch {
                    _events.emit(SyncEvent.RemoteCommand(
                        connectionId = connectionId,
                        command = command ?: "UNKNOWN",
                        data = json.optJSONObject("data")
                    ))
                }
            }
        }

        if (!actionId.isNullOrBlank()) {
            sendActionReceipt(connectionId, actionId, SyncProtocol.ACTION_ACTION_DELIVERED)
        }
    }

    fun sendNotificationEvent(eventType: String, notification: com.aman.gigi.model.RemoteNotification) {
        scope.launch {
            val activeConnections = connectionRepository.getAllActiveConnectionsOnce()
            activeConnections.forEach { connection ->
                // Joinee Stealth Check: Only a JOINEE should send notifications to a CREATOR.
                // A device is a Joinee if creatorDeviceId is NOT their own deviceId.
                val isCreator = connection.creatorDeviceId?.equals(deviceId, ignoreCase = true) == true
                val isJoinee = connection.creatorDeviceId != null && !isCreator
                
                Log.d(TAG, "≡ƒöì [ROLE-CHECK] Conn: ${connection.connectionCode}, CreatorID: ${connection.creatorDeviceId}, MyID: $deviceId, isCreator: $isCreator, isJoinee: $isJoinee")

                if (connection.isActive && isJoinee) {
                    val json = org.json.JSONObject().apply {
                        put("id", notification.id)
                        put("package_name", notification.packageName)
                        put("title", notification.title)
                        put("text", notification.text)
                        put("timestamp", notification.timestamp)
                        put("icon_base64", notification.iconBase64)
                        put("is_clearable", notification.isClearable)
                    }
                    sendRemoteCommandWithData(connection.connectionId, eventType, json)
                    Log.d(TAG, "≡ƒôñ [NOTIF-SYNC] Sent notification event to creator for connection: ${connection.connectionId}")
                }
            }
        }
    }


    private fun fetchHistory(connectionId: String) {
        scope.launch {
            Log.i(TAG, "≡ƒô£ Requesting history for connection: $connectionId")
            val request = """{"type": "get_history"}"""
            webSocketClient.sendText(connectionId, request)
        }
    }

    private fun handlePairingMessage(text: String, connectionId: String) {
        try {
            val json = org.json.JSONObject(text)
            val messageType = json.optString("type")
            Log.d(TAG, "Received pairing message type: $messageType")

            if (messageType == "history_result") {
                val scribblesJson = json.optJSONArray("scribbles")
                val notificationsJson = json.optJSONArray("notifications")
                
                val scribbles = mutableListOf<com.aman.gigi.model.Scribble>()
                val notifications = mutableListOf<com.aman.gigi.model.RemoteNotification>()
                
                scribblesJson?.let {
                    for (i in 0 until it.length()) {
                        val obj = it.getJSONObject(i)
                        try {
                            val payload = obj.optJSONObject("payload")
                            if (payload != null) {
                                val scribble = ScribbleSerializer.deserialize(payload.toString())
                                scribble?.let { s -> scribbles.add(s) }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing historical scribble", e)
                        }
                    }
                }
                
                notificationsJson?.let {
                    for (i in 0 until it.length()) {
                        val obj = it.getJSONObject(i)
                        val data = obj.optJSONObject("data")
                        data?.let { d ->
                            notifications.add(com.aman.gigi.model.RemoteNotification(
                                id = d.getString("id"),
                                packageName = d.optString("packageName"),
                                title = d.getString("title"),
                                text = d.getString("text"),
                                timestamp = d.getLong("timestamp"),
                                iconBase64 = null, 
                                isClearable = d.optBoolean("is_clearable", true)
                            ))
                        }
                    }
                }
                
                Log.i(TAG, "≡ƒô£ Successfully parsed history: ${scribbles.size} scribbles, ${notifications.size} notifications")
                scope.launch {
                    _events.emit(SyncEvent.HistoryReceived(connectionId, scribbles, notifications))
                }
                return
            }
            
            if (messageType == "ack") {
                val scribbleId = json.optString("scribbleId").trim()
                val ackKey = "$connectionId:$scribbleId"
                Log.i(TAG, "≡ƒÄ» [ACK] Received for $scribbleId. Key: $ackKey. Total pending: ${pendingAcks.keys.size}")
                
                // Try specific key first
                var job = pendingAcks.remove(ackKey)
                
                // Try case-insensitive fallback if ID is weird
                if (job == null) {
                    val fallbackKey = pendingAcks.keys().asSequence().find { it.equals(ackKey, ignoreCase = true) }
                    if (fallbackKey != null) {
                        Log.w(TAG, "ΓÜá∩╕Å [ACK] Match found via case-insensitive lookup: $fallbackKey")
                        job = pendingAcks.remove(fallbackKey)
                    }
                }

                if (job != null) {
                    Log.d(TAG, "Γ£à [ACK] Found matching job. Cancelling timeout.")
                    job.cancel()
                    scope.launch {
                        outboundActionRepository.markAccepted(scribbleId)
                        val acknowledgedScribble = scribbleRepository.getScribbleById(scribbleId)
                        if (acknowledgedScribble != null) {
                            scribbleRepository.markAsSent(scribbleId)
                        }
                    }
                    
                    // Notify UI of success
                    scope.launch {
                        _events.emit(SyncEvent.SendSuccess(connectionId, scribbleId))
                    }
                } else {
                    Log.w(TAG, "ΓÜá∩╕Å [ACK] No job found for $ackKey. Current keys: ${pendingAcks.keys().asSequence().toList()}")
                }
                return
            }
            
            if (messageType == "request_role_info") {
                scope.launch {
                    val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
                    conn?.creatorDeviceId?.let { creatorId ->
                        Log.i(TAG, "≡ƒöæ [ROLE-INFO] Responding to partner's role request with: $creatorId")
                        val response = """{"type": "role_info", "connectionId": "$connectionId", "creatorDeviceId": "$creatorId"}"""
                        webSocketClient.sendText(connectionId, response)
                    }
                }
                return
            }

            if (messageType == "server_status") {
                bootstrapManager.handleSocketServerStatus(
                    mode = json.optString("mode").ifBlank { null },
                    message = json.optString("message").ifBlank { null }
                )
                return
            }
            
            if (messageType == "connection_established") {
                val partnerId = json.getString("partnerDeviceId")
                val partnerName = json.getString("partnerDeviceName")
                persistServerIdentity(json)
                
                Log.i(TAG, "≡ƒÄë Connection established for $connectionId with partner: $partnerName ($partnerId)")
                
                // Fetch dedicated history for this connection immediately
                fetchHistory(connectionId)
                
                scope.launch {
                    updateConnectionMetadata(
                        connectionId = connectionId,
                        creatorDeviceId = json.optString("creatorDeviceId").takeIf { it.isNotBlank() },
                        partnerDeviceId = partnerId,
                        partnerName = partnerName,
                        role = json.optString("role").takeIf { it.isNotBlank() },
                        memberId = json.optString("memberId").takeIf { it.isNotBlank() },
                        isActive = true
                    )
                    Log.i(TAG, "Γ£à Updated repository for partner session metadata on $connectionId")

                    Log.i(TAG, "≡ƒæï Announcing presence to partner for $connectionId")
                    sendHeartbeat(connectionId)
                    queryPartnerPresence(connectionId, showPopup = true)
                }
            } else if (messageType == "partner_disconnected") {
                Log.w(TAG, "≡ƒÆö Partner disconnected for $connectionId")
                scope.launch {
                    val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
                    conn?.let {
                        applyPartnerPresence(
                            connectionId = connectionId,
                            isOnline = false,
                            partnerNameHint = it.partnerName,
                            lastSeenAt = System.currentTimeMillis(),
                            showPopup = true
                        )
                        // Don't show "Partner Disconnected" dialog for group connections —
                        // members come and go; having no one online is normal.
                        if (!it.isGroup) {
                            _events.emit(SyncEvent.PartnerDisconnected(it.connectionId, it.partnerName))
                        }
                    }
                }
            } else if (messageType == "partner_online") {
                Log.i(TAG, "≡ƒÆÜ Partner came back online for $connectionId")
                scope.launch {
                    val partnerNameHint = json.optString("partnerDeviceName").takeIf { it.isNotBlank() }
                    applyPartnerPresence(
                        connectionId = connectionId,
                        isOnline = true,
                        partnerNameHint = partnerNameHint,
                        lastSeenAt = json.optLong("lastSeenAt").takeIf { it > 0L },
                        showPopup = true,
                        respondWithHeartbeat = false
                    )
                }
            } else if (messageType == "partner_offline") {
                Log.i(TAG, "≡ƒûñ Partner is offline for $connectionId")
                val creatorId = json.optString("creatorDeviceId")
                if (creatorId.isNotEmpty()) {
                    scope.launch {
                        applyPartnerPresence(
                            connectionId = connectionId,
                            isOnline = false,
                            partnerNameHint = json.optString("partnerDeviceName").takeIf { it.isNotBlank() },
                            lastSeenAt = json.optLong("lastSeenAt").takeIf { it > 0L },
                            showPopup = true
                        )
                        val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
                        conn?.let {
                            if (!it.creatorDeviceId.equals(creatorId, ignoreCase = true)) {
                                Log.i(TAG, "≡ƒöæ Updating creatorDeviceId for $connectionId: $creatorId")
                                connectionRepository.updateConnection(it.copy(creatorDeviceId = creatorId))
                            }
                        }
                    }
                } else {
                    scope.launch {
                        applyPartnerPresence(
                            connectionId = connectionId,
                            isOnline = false,
                            partnerNameHint = json.optString("partnerDeviceName").takeIf { it.isNotBlank() },
                            partnerDeviceId = json.optString("partnerDeviceId").takeIf { it.isNotBlank() },
                            lastSeenAt = json.optLong("lastSeenAt").takeIf { it > 0L },
                            showPopup = true
                        )
                    }
                }
            } else if (messageType == "partner_presence") {
                val isOnline = json.optBoolean("isOnline", false)
                val showPopup = json.optBoolean("showPopup", true)
                val partnerNameHint = json.optString("partnerDeviceName").takeIf { it.isNotBlank() }
                Log.i(TAG, "≡ƒôí Partner presence for $connectionId -> ${if (isOnline) "ONLINE" else "OFFLINE"}")
                scope.launch {
                    applyPartnerPresence(
                        connectionId = connectionId,
                        isOnline = isOnline,
                        partnerNameHint = partnerNameHint,
                        partnerDeviceId = json.optString("partnerDeviceId").takeIf { it.isNotBlank() },
                        lastSeenAt = json.optLong("lastSeenAt").takeIf { it > 0L },
                        showPopup = showPopup,
                        respondWithHeartbeat = isOnline
                    )
                }
            } else if (messageType == "role_info" || messageType == "session_info") {
                val creatorId = json.optString("creatorDeviceId")
                persistServerIdentity(json)
                if (creatorId.isNotEmpty()) {
                    Log.i(TAG, "≡ƒöæ [ROLE-INFO] Received creatorDeviceId for $connectionId: $creatorId")
                }
                scope.launch {
                    updateConnectionMetadata(
                        connectionId = connectionId,
                        creatorDeviceId = creatorId.takeIf { it.isNotBlank() },
                        role = json.optString("role").takeIf { it.isNotBlank() },
                        memberId = json.optString("memberId").takeIf { it.isNotBlank() },
                        relationshipType = json.optString("relationshipType").takeIf { it.isNotBlank() }
                    )
                }
            } else if (messageType == "connection_created") {
                val creatorId = json.optString("creatorDeviceId")
                val freshSession = json.optBoolean("freshSession", false)
                Log.i(TAG, "≡ƒåò [CONN-CREATED] Connection established as creator: $creatorId freshSession=$freshSession")
                persistServerIdentity(json)
                scope.launch {
                    updateConnectionMetadata(
                        connectionId = connectionId,
                        creatorDeviceId = creatorId.takeIf { it.isNotBlank() },
                        role = json.optString("role").takeIf { it.isNotBlank() } ?: com.aman.gigi.model.ConnectionRole.CREATOR.name,
                        memberId = json.optString("memberId").takeIf { it.isNotBlank() },
                        isActive = true,
                        relationshipType = json.optString("relationshipType").takeIf { it.isNotBlank() }
                    )
                    // Server had no record for this code — it was wiped (e.g. after admin reset).
                    // Clear any stale locally-cached partner so the UI doesn't show a ghost partner.
                    if (freshSession) {
                        Log.w(TAG, "🔴 [FRESH-SESSION] Server had no record for $connectionId — clearing stale partner cache")
                        val existing = connectionRepository.getAllActiveConnectionsOnce()
                            .find { it.connectionId == connectionId }
                        // Only clear stale partner for 1-1 connections. For groups,
                        // "waiting..." is the normal initial state — don't fire PartnerDisconnected.
                        val isPlaceholder = existing?.partnerDeviceId == "waiting..." || existing?.partnerDeviceId == "joining..."
                        if (existing != null && existing.partnerDeviceId.isNotBlank()
                            && !existing.isGroup && !isPlaceholder) {
                            connectionRepository.updateConnection(
                                existing.copy(
                                    partnerDeviceId = "",
                                    partnerName = existing.partnerName, // keep user-set name
                                    isActive = true
                                )
                            )
                            _events.emit(SyncEvent.PartnerDisconnected(connectionId, existing.partnerName))
                        }
                    }
                }
            } else if (messageType == "connection_joined") {
                persistServerIdentity(json)
                scope.launch {
                    val isGroupJoin = json.optBoolean("isGroup", false)
                    val groupName = json.optString("groupName").takeIf { it.isNotBlank() }
                    // Use GROUP relationshipType for group joins, otherwise keep whatever was set
                    val resolvedRelationshipType = if (isGroupJoin) "GROUP"
                        else json.optString("relationshipType").takeIf { it.isNotBlank() }
                    updateConnectionMetadata(
                        connectionId = connectionId,
                        creatorDeviceId = json.optString("creatorDeviceId").takeIf { it.isNotBlank() },
                        role = json.optString("role").takeIf { it.isNotBlank() } ?: com.aman.gigi.model.ConnectionRole.PARTNER.name,
                        memberId = json.optString("memberId").takeIf { it.isNotBlank() },
                        isActive = true,
                        relationshipType = resolvedRelationshipType
                    )
                    // For group joins, overwrite the local placeholder name with the real group name
                    if (isGroupJoin && groupName != null) {
                        val conn = connectionRepository.getAllActiveConnectionsOnce()
                            .find { it.connectionId == connectionId }
                        if (conn != null) {
                            connectionRepository.updateConnection(
                                conn.copy(
                                    partnerName = groupName,
                                    isGroup = true,
                                    relationshipType = "GROUP"
                                )
                            )
                            Log.i(TAG, "🏷️ [GROUP-JOIN] Updated group name to '$groupName' for $connectionId")
                        }
                    }
                }
            } else if (messageType == "error") {
                val errorMsg = json.optString("message")
                val errorCode = json.optString("code")
                Log.e(TAG, "Γ¥î Server reported error for $connectionId: $errorMsg (code: $errorCode)")
                
                if (errorCode == "SERVER_MAINTENANCE") {
                    bootstrapManager.handleSocketServerStatus("MAINTENANCE", errorMsg.ifBlank { null })
                    return
                }

                if (errorCode == "CONNECTION_LIMIT_REACHED") {
                    // The server refused to create this connection — stop retrying and
                    // let the UI route the user to the upgrade sheet.
                    scope.launch {
                        _events.emit(
                            SyncEvent.ConnectionLimitReached(
                                connectionId,
                                errorMsg.ifBlank { "You've reached your plan's connection limit. Upgrade to create more." }
                            )
                        )
                        webSocketClient.disconnect(connectionId)
                    }
                    return
                }

                if (errorCode == "SESSION_EXPIRED" ||
                    errorMsg.contains("not found", ignoreCase = true) ||
                    errorMsg.contains("expired", ignoreCase = true)) {
                    Log.w(TAG, "ΓÜá∩╕Å Session $connectionId is invalid/expired. Attempting re-handshake instead of deleting.")
                    scope.launch {
                        setTransportState(connectionId, TransportState.CONNECTING)
                        _events.emit(SyncEvent.SessionExpired(connectionId))
                        // Re-attempt connection after a brief delay ΓÇö server restores from MongoDB
                        delay(2000L)
                        val conn = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
                        if (conn != null) {
                            Log.i(TAG, "≡ƒöä [SESSION-RECOVERY] Re-attempting handshake for $connectionId")
                            connectWebSocket(conn)
                        }
                    }
                } else {
                    // If the server tells us it couldn't deliver, assume partner is offline
                    scope.launch {
                        _events.emit(SyncEvent.SendFailed(connectionId, errorMsg.ifEmpty { "Partner unreachable" }))
                        queryPartnerPresence(connectionId, showPopup = false)
                    }
                }
            } else if (messageType == "mirror_frame") {
                // This is a metadata frame usually followed by binary, 
                // but if the server relays binary directly, it's handled in handleBinaryMessage
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message for $connectionId: $text", e)
        }
    }

    /**
     * Connect to WebSocket server for a specific connection
     */
    private fun connectWebSocket(connection: com.aman.gigi.model.Connection) {
        Log.d(TAG, "[${connection.connectionId}] Connecting for Device ID: $deviceId")
        
        webSocketClient.connect(
            connectionId = connection.connectionId,
            url = SERVER_URL,
            deviceId = deviceId,
            listener = object : WebSocketClient.ConnectionListener {
                override fun onConnected() {
                    Log.i(TAG, "Γ£à WebSocket Handshake Established for ${connection.connectionId}")
                    // Initialize partner activity timestamp to prevent instant false PARTNER_OFFLINE
                    lastPartnerActivity[connection.connectionId] = System.currentTimeMillis()
                    scope.launch {
                        setTransportState(connection.connectionId, TransportState.CONNECTED)
                        flushPendingOutgoingScribbles(connection.connectionId)
                    }

                    // Perform handshake once connected
                    scope.launch {
                        val role = runCatching {
                            com.aman.gigi.model.ConnectionRole.valueOf(connection.role)
                        }.getOrDefault(com.aman.gigi.model.ConnectionRole.PARTNER)
                        val isSelfCreator = role == com.aman.gigi.model.ConnectionRole.CREATOR ||
                            connection.creatorDeviceId?.equals(deviceId, ignoreCase = true) == true
                        val messageType = if (isSelfCreator) "create_connection" else "join_connection"
                        val authToken = bootstrapManager.memberIdentity.value?.authToken
                        Log.i(TAG, "≡ƒñ¥ Handshake: role=${role.name}, isSelfCreator=$isSelfCreator, sending=$messageType for ${connection.connectionCode}")
                        
                        val handshake = """
                            {
                                "type": "$messageType",
                                "connectionCode": "${connection.connectionCode}",
                                "deviceId": "$deviceId",
                                "deviceName": "${android.os.Build.MODEL}",
                                "sessionToken": ${authToken?.let { "\"$it\"" } ?: "null"},
                                "partnerLabel": ${connection.partnerName.takeIf { it.isNotBlank() }?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"},
                                "relationshipType": "${connection.relationshipType}"
                            }
                        """.trimIndent()
                        webSocketClient.sendText(connection.connectionId, handshake)
                        
                        // --- PROACTIVE SESSION SYNC ---
                        // Immediately ask the server for current session details (creator status, etc)
                        delay(500L) // Give server a moment to process the handshake
                        val syncCmd = """{"type": "get_session_info", "connectionCode": "${connection.connectionCode}"}"""
                        webSocketClient.sendText(connection.connectionId, syncCmd)
                        Log.i(TAG, "≡ƒöì [SESSION-SYNC] Sent get_session_info for ${connection.connectionCode}")
                        delay(200L)
                        sendResumeSession(connection)
                        
                        // PROACTIVE PRESENCE: Inform the partner we are back online AND ask for their state
                        val announce = SyncProtocol.buildEnvelope(
                            connectionId = connection.connectionId,
                            senderDeviceId = deviceId,
                            actionType = SyncProtocol.ACTION_PRESENCE_SNAPSHOT,
                            payload = JSONObject().apply {
                                put("isOnline", true)
                                put("deviceId", deviceId)
                                val myName = bootstrapManager.memberIdentity.value?.displayName.orEmpty().ifBlank { android.os.Build.MODEL }
                                put("partnerDeviceName", myName)
                                put("connectionCode", connection.connectionCode)
                            },
                            requiresDisplayReceipt = false
                        )
                        webSocketClient.sendText(connection.connectionId, announce.toString())
                        Log.i(TAG, "≡ƒôí [HANDSHAKE] Announced our presence for ${connection.connectionId}")
                        
                        queryPartnerPresence(connection.connectionId, showPopup = true)
                        
                        // --- PROACTIVE RECONNECTION FIX ---
                        // Inform partner immediately (and multiple times) that we are back online
                        launch {
                            repeat(3) { i ->
                                delay(200L * (i + 1)) 
                                Log.i(TAG, "≡ƒæï Heartbeat burst $i for ${connection.connectionId}")
                                sendHeartbeat(connection.connectionId)
                            }
                        }
                    }
                }

                override fun onDisconnecting(code: Int, reason: String) {
                    Log.w(TAG, "WebSocket disconnecting for ${connection.connectionId}: $code - $reason")
                }

                override fun onDisconnected(code: Int, reason: String) {
                    Log.w(TAG, "WebSocket disconnected for ${connection.connectionId}: $code - $reason")
                    
                    // PROACTIVE FIX: If code is 4000, it means the server intentionally replaced this session.
                    // DO NOT trigger auto-reconnect as it will cause a cascade.
                    if (code == 4000) {
                        Log.i(TAG, "Γ£à [CASCADE-PREVENT] Not reconnecting as session was replaced by server.")
                        return
                    }

                    scope.launch {
                        // Mark as NO_INTERNET if local monitor says so, otherwise DISCONNECTED
                        val isOnline = networkMonitor.isOnline.firstOrNull() ?: true
                        val nextTransportState = if (!isOnline) TransportState.NO_INTERNET else TransportState.CONNECTING
                        setTransportState(connection.connectionId, nextTransportState)
                        
                        // AUTO-RECONNECT: Wait and try again if we shouldn't be stopped
                        if (isStarted && activeSyncJobs.containsKey(connection.connectionId)) {
                            delay(baseDelayMs * 2)
                            Log.i(TAG, "≡ƒöä Attempting auto-reconnect for ${connection.connectionId}...")
                            connectWebSocket(connection)
                        }
                    }
                }

                override fun onError(throwable: Throwable) {
                    val message = throwable.message ?: "Unknown WebSocket Error"
                    Log.e(TAG, "≡ƒÆÑ WebSocket error for ${connection.connectionId}: $message", throwable)
                    
                    if (message.contains("530")) {
                        Log.e(TAG, "≡ƒÜ¿ [SERVER-ERROR] Sync server (gigi.iamanraj.com) is returning 530. This usually indicates a gateway or upstream issue.")
                    }

                    scope.launch {
                        val isOnline = networkMonitor.isOnline.firstOrNull() ?: true
                        setTransportState(
                            connection.connectionId,
                            if (isOnline) TransportState.CONNECTING else TransportState.NO_INTERNET
                        )
                        
                        // AUTO-RECONNECT for errors too
                        if (isStarted && activeSyncJobs.containsKey(connection.connectionId)) {
                            // Exponential-ish backoff for errors
                            val delayMs = baseDelayMs * 5
                            delay(delayMs)
                            Log.i(TAG, "≡ƒöä Attempting auto-reconnect after error for ${connection.connectionId} (Wait: ${delayMs}ms)...")
                            connectWebSocket(connection)
                        }
                    }
                }
            }
        )
    }

    private fun flushPendingOutgoingScribbles(connectionId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val pendingSummaries = scribbleRepository.getPendingScribbleSummaries().firstOrNull() ?: emptyList()
                Log.i(TAG, "📦 [OFFLINE-QUEUE] Checking pending outgoing scribbles for $connectionId: ${pendingSummaries.size} pending")
                for (summary in pendingSummaries) {
                    val scribble = scribbleRepository.getScribbleById(summary.scribbleId)
                    if (scribble != null && (scribble.status == com.aman.gigi.model.ScribbleStatus.PENDING || scribble.status == com.aman.gigi.model.ScribbleStatus.FAILED)) {
                        Log.i(TAG, "📤 [OFFLINE-QUEUE] Flushing pending scribble: ${scribble.scribbleId}")
                        sendScribble(scribble)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing pending scribbles for $connectionId", e)
            }
        }
    }
    
    /**
     * Stop sync manager
     */
    fun stop() {
        Log.i(TAG, "Stopping all sync manager jobs...")
        isStarted = false
        customLifecycleOwner = null
        stopJobs()
        // Disconnect all sessions
        activeSyncJobs.keys.toList().forEach { id ->
            webSocketClient.disconnect(id)
        }
    }

    fun setLifecycleOwner(owner: LifecycleOwner?) {
        this.customLifecycleOwner = owner
        Log.d(TAG, "≡ƒô╣ LifecycleOwner updated: ${owner?.javaClass?.simpleName ?: "NULL"}")
    }

    private fun getActiveLifecycleOwner(): LifecycleOwner {
        val custom = customLifecycleOwner
        if (custom != null) {
            val state = custom.lifecycle.currentState
            if (state != Lifecycle.State.DESTROYED) {
                return custom
            }
        }
        return ProcessLifecycleOwner.get()
    }
    
    /**
     * Explicitly disconnect from a session
     */
    fun disconnect(connectionId: String) {
        scope.launch {
            Log.i(TAG, "Sending explicit disconnect for $connectionId")
            webSocketClient.sendText(connectionId, """{"type": "disconnect", "archiveMembership": true}""")
            // We give it a moment to send before closing the socket
            delay(500)
            webSocketClient.disconnect(connectionId)
        }
    }

    private fun stopJobs() {
        activeSyncJobs.values.forEach { it.cancel() }
        activeSyncJobs.clear()
    }
    
    /**
     * Send scribble to partner
     */
    private suspend fun sendScribble(scribble: Scribble, actionId: String? = null): Boolean {
        try {
            Log.i(TAG, "[sendScribble] Step 1: Updating status to SENDING for ${scribble.scribbleId}")
            scribbleRepository.updateScribbleStatus(scribble.scribbleId, ScribbleStatus.SENDING)
            
            var scribbleToSend = scribble.copy(senderDeviceId = deviceId)

            // HYBRID ARCHITECTURE: Offload Media to HTTP
            // Check if we have a large Base64 payload (Image/Audio)
            if (!scribble.mediaBase64.isNullOrEmpty() && scribble.mediaBase64.length > 5000) { // > ~3.7KB
                try {
                     Log.i(TAG, "≡ƒôñ [UPLOAD] Offloading media for ${scribble.scribbleId} via HTTP...")
                     // 1. Write to temp file
                     val tempFile = java.io.File(context.cacheDir, "upload_${scribble.scribbleId}.bin")
                     val bytes = android.util.Base64.decode(scribble.mediaBase64, android.util.Base64.DEFAULT)
                     tempFile.writeBytes(bytes)
                     
                     // 2. Upload
                     val remotePath = httpUploader.uploadFile(tempFile, scribble.connectionId, scribble.scribbleId)
                     
                     if (remotePath != null) {
                         // 3. Update payload (Strip base64, add URL)
                         scribbleToSend = scribble.copy(
                             mediaUrl = remotePath,
                             mediaBase64 = null 
                         )
                         if (!actionId.isNullOrBlank()) {
                             outboundActionRepository.updateRemoteAssetUrl(actionId, remotePath)
                         }
                         Log.i(TAG, "Γ£à [UPLOAD] Success! Remote path: $remotePath. Swapped out Base64.")
                         tempFile.delete()
                     } else {
                         Log.e(TAG, "Γ¥î [UPLOAD] Failed. Sending via WebSocket as fallback (High risk of disconnection).")
                     }
                } catch (e: Exception) {
                    Log.e(TAG, "Γ¥î [UPLOAD] Error during media offloading", e)
                }
            }

            Log.i(TAG, "[sendScribble] Step 2: Serializing scribble (mediaBase64: ${scribbleToSend.mediaBase64?.length ?: 0} chars, mediaUrl: ${scribbleToSend.mediaUrl})")
            val json = try {
                ScribbleSerializer.serialize(scribbleToSend)
            } catch (e: Exception) {
                Log.e(TAG, "Γ¥î Serialization failed for ${scribble.scribbleId}", e)
                _events.emit(SyncEvent.SendFailed(scribble.connectionId, "Failed to package drawing"))
                return false
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Γ¥î OutOfMemoryError during serialization for ${scribble.scribbleId}")
                _events.emit(SyncEvent.SendFailed(scribble.connectionId, "Drawing is too large for memory"))
                return false
            }
            
            Log.i(TAG, "[sendScribble] Step 3: Compressing JSON (json length: ${json.length})")
            val compressed = try {
                CompressionUtil.compress(json)
            } catch (e: Exception) {
                Log.e(TAG, "Γ¥î Compression failed for ${scribble.scribbleId}", e)
                return false
            }
            
            Log.i(TAG, "Outgoing payload size for ${scribble.scribbleId}: ${compressed.size} bytes (compressed)")

            // 4. Send via WebSocket FIRST, then register ACK timeout only on success
            val ackKey = "${scribble.connectionId}:${scribble.scribbleId}"
            val success = webSocketClient.sendBinary(scribble.connectionId, compressed)

            if (success) {
                // Register ACK timeout AFTER confirmed send
                val ackJob = scope.launch {
                    try {
                        delay(ACK_TIMEOUT)
                        Log.w(TAG, "ΓÅ░ [ACK-TIMEOUT] Timer expired for $ackKey. Attempting recovery...")
                        pendingAcks.remove(ackKey)
                        
                        // Check if it's still SENDING in our repository
                        val currentAction = outboundActionRepository.getAction(actionId ?: scribble.scribbleId)
                        if (currentAction?.state == com.aman.gigi.model.OutboundActionState.SENDING.name) {
                            Log.e(TAG, "Γ¥î [ACK-TIMEOUT] Partner delivery ack timeout for $ackKey. Marking as FAILED.")
                            scribbleRepository.updateScribbleStatus(scribble.scribbleId, ScribbleStatus.FAILED)
                            if (!actionId.isNullOrBlank()) {
                                scheduleActionRetry(
                                    actionId = actionId,
                                    attemptCount = outboundActionRepository.getAction(actionId)?.attemptCount ?: 0,
                                    error = "Partner delivery ack timeout"
                                )
                            }
                            _events.emit(SyncEvent.SendFailed(scribble.connectionId, "Partner delivery ack timeout."))
                        } else {
                            Log.i(TAG, "Γ£à [ACK-TIMEOUT] Action $ackKey state is already ${currentAction?.state}, ignoring timeout.")
                        }
                    } catch (e: CancellationException) {
                        Log.d(TAG, "Γ£à ACK timer cancelled for $ackKey (Success)")
                    }
                }
                pendingAcks[ackKey] = ackJob
                Log.i(TAG, "Γ£à [PUSH] Scribble ${scribble.scribbleId} sent. Payload: ${compressed.size} bytes. Waiting for ACK ($ackKey)")
                return true
            } else {
                val isConnected = webSocketClient.isConnected(scribble.connectionId)
                Log.e(TAG, "Γ¥î [PUSH-FAIL] Failed to push ${scribble.scribbleId}. Connected: $isConnected. Payload: ${compressed.size} bytes")
                
                if (!isConnected) {
                    scribbleRepository.updateScribbleStatus(scribble.scribbleId, ScribbleStatus.PENDING)
                    setTransportState(scribble.connectionId, TransportState.CONNECTING)
                    _events.emit(SyncEvent.SendFailed(scribble.connectionId, "Connection lost. Will retry automatically."))
                } else {
                    scribbleRepository.updateScribbleStatus(scribble.scribbleId, ScribbleStatus.FAILED)
                    _events.emit(SyncEvent.SendFailed(scribble.connectionId, "Failed to push scribble payload"))
                }
                return false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in sendScribble for ${scribble.scribbleId}", e)
            scribbleRepository.updateScribbleStatus(scribble.scribbleId, ScribbleStatus.FAILED)
            _events.emit(SyncEvent.SendFailed(scribble.connectionId, "Message failed before delivery"))
            return false
        }
    }
    
    /**
     * Handle incoming scribble
     */
    private suspend fun handleIncomingScribble(compressedData: ByteArray, connectionId: String): Scribble? {
        try {
            // Streaming Pipeline: Byte[] -> GZIPInputStream -> JsonReader -> Scribble
            // This avoids creating a massive intermediate JSON String in RAM.
            val scribble = CompressionUtil.decompressToStream(compressedData).use { stream ->
                ScribbleSerializer.deserializeFromStream(stream)
            }
            
            if (scribble != null) {
                // Check for Heartbeat
                if (scribble.mediaType == MEDIA_TYPE_HEARTBEAT) {
                    return scribble
                }

                var processedScribble = scribble
                
                // HYBRID ARCHITECTURE: Download Offloaded Media
                if (processedScribble.mediaUrl != null && processedScribble.mediaBase64.isNullOrEmpty()) {
                    try {
                        Log.i(TAG, "≡ƒôÑ [DOWNLOAD] Found offloaded media at ${processedScribble.mediaUrl}. Downloading...")
                        val tempFile = java.io.File(context.cacheDir, "download_${processedScribble.scribbleId}.bin")
                        val success = httpUploader.downloadFile(processedScribble.mediaUrl!!, tempFile)
                        
                        if (success) {
                            val bytes = tempFile.readBytes()
                            // Use NO_WRAP to avoid newlines in base64 which might break things
                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            processedScribble = processedScribble.copy(mediaBase64 = base64) // Re-hydrate
                            Log.i(TAG, "Γ£à [DOWNLOAD] Success! Re-hydrated Base64 (${base64.length} chars).")
                            tempFile.delete()
                        } else {
                            Log.e(TAG, "Γ¥î [DOWNLOAD] Failed to download media from ${processedScribble.mediaUrl}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Γ¥î [DOWNLOAD] Error during media re-hydration", e)
                    }
                }

                Log.d(TAG, "Received scribble ${processedScribble.scribbleId} from connection $connectionId")
                
                // --- Early ACK for Media ---
                // If it's a GIF/Media, ACK immediately so the sender isn't stuck while we process/save
                val isMedia = processedScribble.mediaUrl != null || processedScribble.mediaBase64 != null
                
                val ackAction = {
                    scope.launch {
                        val ackJson = """
                            {
                                "type": "ack",
                                "scribbleId": "${processedScribble.scribbleId}"
                            }
                        """.trimIndent()
                        
                        var ackSuccess = false
                        var ackRetries = 0
                        while (!ackSuccess && ackRetries < 3) {
                            Log.d(TAG, "≡ƒôñ [ACK-OUT] Sending ACK for ${processedScribble.scribbleId} (Attempt ${ackRetries + 1})")
                            ackSuccess = webSocketClient.sendText(connectionId, ackJson)
                            if (!ackSuccess) {
                                delay(1000L * (ackRetries + 1))
                                ackRetries++
                            }
                        }
                        if (ackSuccess) {
                            Log.i(TAG, "Γ£à [ACK-OUT] Successfully sent ACK for ${processedScribble.scribbleId}")
                            sendActionReceipt(connectionId, processedScribble.scribbleId, SyncProtocol.ACTION_ACTION_DELIVERED)
                        }
                    }
                }

                if (isMedia) {
                    Log.i(TAG, "ΓÜí Media detected. Sending early ACK for ${processedScribble.scribbleId}")
                    ackAction()
                }

                // Save as received scribble, ensuring it's tied to the correct connection
                val receivedScribble = processedScribble.copy(connectionId = connectionId)
                scribbleRepository.saveReceivedScribble(receivedScribble)
                
                // Trigger Lockscreen Notification/Activity
                triggerLockscreenScribble(receivedScribble)
                
                if (isMedia) {
                    // ... already handled
                } else {
                    ackAction()
                }
                return processedScribble
            } else {
                Log.e(TAG, "Failed to deserialize incoming scribble from $connectionId")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming scribble from $connectionId", e)
            return null
        }
    }

    // Persistent dedup: store scribble IDs shown in prefs so reconnects don't re-notify
    private fun isScribbleAlreadyNotified(scribbleId: String): Boolean {
        if (scribbleId.isBlank()) return false
        val prefs = context.getSharedPreferences("gigi_notif_dedup", android.content.Context.MODE_PRIVATE)
        val lastShown = prefs.getLong(scribbleId, 0L)
        val now = System.currentTimeMillis()
        if (lastShown > 0L && (now - lastShown) < 600_000L) { // 10-minute window
            Log.i(TAG, "⏭️ [DEDUPE] Notification for scribble $scribbleId already shown within 10 mins (persistent cache). Skipping.")
            return true
        }
        // Also check in-memory cache
        val inMemLast = shownScribbleNotificationIds[scribbleId]
        if (inMemLast != null && (now - inMemLast) < 600_000L) {
            Log.i(TAG, "⏭️ [DEDUPE] Notification for scribble $scribbleId in memory cache. Skipping.")
            return true
        }
        prefs.edit().putLong(scribbleId, now).apply()
        shownScribbleNotificationIds[scribbleId] = now
        return false
    }

    private fun triggerLockscreenScribble(scribble: Scribble) {
        // 1. Guard against non-doodle / empty / heartbeat payloads (location updates, presence pings)
        val hasContent = scribble.strokes.isNotEmpty() ||
                !scribble.mediaUrl.isNullOrBlank() ||
                !scribble.mediaBase64.isNullOrBlank() ||
                scribble.revealType != null
        if (!hasContent) {
            Log.d(TAG, "⏭️ [NON-DOODLE] Skipping notification for empty/ping payload: ${scribble.scribbleId}")
            return
        }

        // 2. Guard against self-sent scribbles
        if (!scribble.senderDeviceId.isNullOrBlank() && scribble.senderDeviceId.equals(deviceId, ignoreCase = true)) {
            Log.d(TAG, "⏭️ [SELF-DOODLE] Skipping notification for own scribble: ${scribble.scribbleId}")
            return
        }

        // 3. Guard against old historic scribbles synced from DB/history (older than 3 minutes)
        val now = System.currentTimeMillis()
        val creationTime = if (scribble.createdAt > 0L) scribble.createdAt else (scribble.receivedAt ?: 0L)
        if (creationTime > 0L && (now - creationTime) > 180_000L) {
            Log.d(TAG, "⏭️ [HISTORIC-DOODLE] Skipping notification for old scribble: ${scribble.scribbleId} (age: ${(now - creationTime) / 1000}s)")
            return
        }

        // 4. Guard against duplicate notifications for the same scribbleId
        if (scribble.scribbleId.isNotBlank()) {
            if (isScribbleAlreadyNotified(scribble.scribbleId)) return
        }
        Log.i(TAG, "🔔 Triggering lockscreen display for genuine new scribble: ${scribble.scribbleId}")
        
        val km = context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val isLocked = km?.isKeyguardLocked == true

        // Determine target activity
        val activityClass = if (scribble.revealType != null) {
            SparkleRevealActivity::class.java
        } else {
            LockscreenScribbleActivity::class.java
        }

        // Try to find partner name
        val partnerName = runBlocking {
            val conn = connectionRepository.getAllActiveConnectionsOnce()
                .find { it.connectionId == scribble.connectionId }
            
            if (conn?.isGroup == true && scribble.senderDeviceId != null) {
                val members = connectionRepository.getMembersForConnection(scribble.connectionId)
                members.find { it.memberDeviceId == scribble.senderDeviceId }?.memberName ?: conn.partnerName
            } else {
                conn?.partnerName ?: "Partner"
            }
        }

        val intent = android.content.Intent(context, activityClass).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("scribble_id", scribble.scribbleId) // Use ID to avoid TransactionTooLargeException
            putExtra("connection_id", scribble.connectionId)
            putExtra("partner_name", partnerName)
            
            // Always attach serialized scribble if available (for instant display without DB delay)
            if (scribble.mediaBase64 == null) {
                try {
                    putExtra("scribble_json", ScribbleSerializer.serialize(scribble))
                } catch (_: Exception) {}
            }
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            scribble.scribbleId.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "scribble_alerts_v3"
        createNotificationChannel(channelId)

        val title = if (scribble.revealType == "SPARKLE") "$partnerName sent a Sparkle moment! ✨" else "$partnerName sent a doodle! 🎨"
        val body = if (scribble.revealType == "SPARKLE") "Tap to reveal $partnerName's photo moment" else "Tap to watch $partnerName's drawing animation"

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) 
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(if (isLocked) androidx.core.app.NotificationCompat.PRIORITY_MAX else androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isLocked) androidx.core.app.NotificationCompat.CATEGORY_CALL else androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            .setVibrate(longArrayOf(0, 500L))
            .setAutoCancel(true)

        if (isLocked) {
            builder.setFullScreenIntent(pendingIntent, true)
        }

        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notifId = scribble.scribbleId.hashCode()
        notificationManager.notify(notifId, builder.build())
        
        // Direct launch attempt ONLY when keyguard is locked
        if (isLocked) {
            try {
                context.startActivity(intent)
                Log.i(TAG, "🚀 Direct activity launch initiated for locked screen: ${scribble.scribbleId}")
            } catch (e: Exception) {
                Log.w(TAG, "Direct activity launch blocked, relying on fullscreen intent", e)
            }
        }
    }

    fun cancelNotification(scribbleId: String) {
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(scribbleId.hashCode())
        Log.i(TAG, "🚫 Cancelled system notification for scribble: $scribbleId")
    }

    fun clearAllSystemNotifications() {
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancelAll()
        Log.i(TAG, "🧹 Cleared all Gigi system tray notifications")
    }

    private fun createNotificationChannel(channelId: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Scribble Alerts"
            val descriptionText = "Notifications for incoming scribbles"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000L)
            }
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Calculate exponential backoff delay
     */
    private fun calculateBackoffDelay(retryCount: Int): Long {
        val exponentialDelay = baseDelayMs * (2.0.pow(retryCount.toDouble())).toLong()
        val jitter = (0..1000).random()
        return min(exponentialDelay + jitter, 30000L) // Max 30 seconds
    }
    
    /**
     * Retry failed scribbles
     */
    suspend fun retryFailedScribbles() {
        val failedScribbles = scribbleRepository.getScribbleSummariesByStatus(com.aman.gigi.model.ScribbleStatus.FAILED, limit = 100)
        failedScribbles.forEach { summary ->
            // Reset to pending
            scribbleRepository.updateScribbleStatus(summary.scribbleId, com.aman.gigi.model.ScribbleStatus.PENDING)
        }
    }
    
    /**
     * Get sync statistics
     */
    suspend fun getSyncStats(): SyncStats {
        val pending = scribbleRepository.getScribbleSummariesByStatus(com.aman.gigi.model.ScribbleStatus.PENDING, limit = 100).size
        val sending = scribbleRepository.getScribbleSummariesByStatus(com.aman.gigi.model.ScribbleStatus.SENDING, limit = 100).size
        val failed = scribbleRepository.getScribbleSummariesByStatus(com.aman.gigi.model.ScribbleStatus.FAILED, limit = 100).size
        
        return SyncStats(
            pendingCount = pending,
            sendingCount = sending,
            failedCount = failed
        )
    }

    // ─────────────────────────── Chat ───────────────────────────
    /** Persists an incoming chat message and notifies observers. */
    private suspend fun handleIncomingChat(json: org.json.JSONObject, connectionId: String) {
        try {
            val text = json.optString("text", "").trim()
            val gifUrl = json.optString("gifUrl", "").trim()
            if (text.isBlank() && gifUrl.isBlank()) {
                Log.w(TAG, "Ignoring blank incoming chat message")
                return
            }

            val id = json.optString("clientMsgId").ifBlank { java.util.UUID.randomUUID().toString() }
            val senderName = json.optString("senderName", "Partner")
            val type = json.optString("msgType", "text")
            chatRepository.save(
                com.aman.gigi.model.ChatMessage(
                    id = id,
                    connectionId = connectionId,
                    senderDeviceId = json.optString("senderDeviceId", ""),
                    senderName = senderName,
                    isMine = false,
                    type = type,
                    text = text,
                    gifUrl = gifUrl,
                    sentAt = json.optLong("sentAt", System.currentTimeMillis()),
                    status = "DELIVERED"
                )
            )
            val preview = if (type == "gif") "sent a GIF 🎞️" else text
            _events.emit(SyncEvent.ChatMessageReceived(connectionId, senderName, preview))
            // Don't pop a bubble for a chat the user is already looking at.
            if (com.aman.gigi.ui.chat.ChatPresence.openConnectionId != connectionId) {
                triggerChatBubble(connectionId, senderName, preview, type, gifUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle incoming chat", e)
        }
    }

    /** Wakes the screen and shows a lock-screen chat bubble for an incoming message. */
    private suspend fun triggerChatBubble(
        connectionId: String, senderName: String, preview: String,
        msgType: String, gifUrl: String
    ) {
        try {
            // When the screen is unlocked and we have overlay permission, show the
            // persistent floating chat-head. When locked, fall back to the lock-screen
            // activity (which can draw over the keyguard).
            val km = context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            val locked = km?.isKeyguardLocked == true
            if (!locked && android.provider.Settings.canDrawOverlays(context)) {
                // Float a chat head with the sender's emoji / Twigi — never steal the screen.
                val senderAvatar = runCatching {
                    connectionRepository.getAllActiveConnectionsOnce()
                        .find { it.connectionId.equals(connectionId, ignoreCase = true) }
                        ?.let { c ->
                            if (c.partnerAvatarMode == "TWIGI" && !c.partnerTwigiUrl.isNullOrBlank())
                                c.partnerTwigiUrl else c.partnerEmojiUrl
                        }.orEmpty()
                }.getOrDefault("")
                // If the bubble can't be shown (permission / background start blocked) we
                // fall through to the notification path rather than showing nothing.
                if (com.aman.gigi.service.ChatHeadService.show(context, connectionId, senderName, senderAvatar)) {
                    return
                }
                Log.w(TAG, "chat head unavailable — using notification instead")
            }
            val notifId = ("chat_$connectionId").hashCode()
            // Look up the sender's animated emoji / Twigi for the lock-screen bubble.
            val lockAvatar = runCatching {
                connectionRepository.getAllActiveConnectionsOnce()
                    .find { it.connectionId.equals(connectionId, ignoreCase = true) }
                    ?.let { c ->
                        if (c.partnerAvatarMode == "TWIGI" && !c.partnerTwigiUrl.isNullOrBlank())
                            c.partnerTwigiUrl else c.partnerEmojiUrl
                    }.orEmpty()
            }.getOrDefault("")
            val intent = android.content.Intent(context, com.aman.gigi.ui.chat.ChatBubbleActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("connection_id", connectionId)
                putExtra("sender_name", senderName)
                putExtra("preview", preview)
                putExtra("msg_type", msgType)
                putExtra("gif_url", gifUrl)
                putExtra("emoji_url", lockAvatar)
                // Over the keyguard show the compact bubble, not the whole conversation.
                putExtra("compact", true)
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, notifId, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            // Tapping the notification from the shade opens the full chat in the app;
            // the full-screen intent (below) shows the lock-screen bubble when locked.
            val openChatIntent = android.content.Intent(context, com.aman.gigi.ui.MainActivity::class.java).apply {
                action = "ACTION_OPEN_CHAT"
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("connection_id", connectionId)
            }
            val contentPending = android.app.PendingIntent.getActivity(
                context, notifId + 1, openChatIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val channelId = "chat_bubbles_v1"
            createChatChannel(channelId)
            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(senderName)
                .setContentText(preview.ifBlank { "New message" })
                .setPriority(
                    if (locked) androidx.core.app.NotificationCompat.PRIORITY_MAX
                    else androidx.core.app.NotificationCompat.PRIORITY_DEFAULT
                )
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
                // A full-screen intent LAUNCHES the activity on its own — that is what was
                // yanking the chat open on every message. Only attach it on the lock
                // screen, where a full-screen bubble is actually the point.
                .apply { if (locked) setFullScreenIntent(pendingIntent, true) }
                .setContentIntent(contentPending)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVibrate(longArrayOf(0, 400L))
                .setAutoCancel(true)
                .build()
            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(notifId, notification)
            // Only the lock screen gets the full-screen bubble activity. When unlocked we
            // deliberately do NOT startActivity — that was yanking the chat open on every
            // message; the notification (and chat head above) are enough.
            if (locked) {
                try { context.startActivity(intent) } catch (_: Exception) { /* rely on full-screen intent */ }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger chat bubble", e)
        }
    }

    private fun createChatChannel(channelId: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Chat Messages", android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming chat messages"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400L)
            }
            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun cancelChatNotification(connectionId: String) {
        val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(("chat_$connectionId").hashCode())
    }

    /** Applies a partner's incoming profile change (avatar / emoji / name). */
    private suspend fun handleIncomingProfileUpdate(json: org.json.JSONObject, connectionId: String) {
        try {
            val conn = connectionRepository.getAllActiveConnectionsOnce()
                .find { it.connectionId.equals(connectionId, ignoreCase = true) } ?: return
            val avatarUrl = json.optString("avatarUrl", "")
            val emojiUrl = json.optString("emojiUrl", "")
            val name = json.optString("name", "")
            val senderDeviceId = json.optString("senderDeviceId", "")

            // In a group, a member's profile change updates THAT member's roster row (which
            // drives their orbiting moon), not the group's single "partner" fields.
            if (conn.isGroup || conn.relationshipType.equals("GROUP", ignoreCase = true)) {
                if (senderDeviceId.isNotBlank()) {
                    val member = connectionRepository.getMembersForConnection(conn.connectionId)
                        .find { it.memberDeviceId.equals(senderDeviceId, ignoreCase = true) }
                    if (member != null) {
                        connectionRepository.saveMembers(listOf(
                            member.copy(
                                emojiUrl = emojiUrl.ifBlank { member.emojiUrl },
                                memberAvatarUrl = avatarUrl.ifBlank { member.memberAvatarUrl },
                                memberName = name.ifBlank { member.memberName }
                            )
                        ))
                    }
                }
                return
            }

            val twigiUrl = json.optString("twigiUrl", "")
            val avatarMode = json.optString("avatarMode", "")
            connectionRepository.updateConnection(
                conn.copy(
                    partnerAvatarUrl = avatarUrl.ifBlank { conn.partnerAvatarUrl },
                    partnerEmojiUrl = emojiUrl.ifBlank { conn.partnerEmojiUrl },
                    partnerName = name.ifBlank { conn.partnerName },
                    partnerTwigiUrl = resolveAssetUrl(twigiUrl) ?: conn.partnerTwigiUrl,
                    partnerAvatarMode = avatarMode.takeIf { it == "TWIGI" || it == "EMOJI" }
                        ?: conn.partnerAvatarMode
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply profile update", e)
        }
    }

    /** Resolves a server-relative asset path ("/avatars/x.png") to an absolute URL. */
    private fun resolveAssetUrl(value: String?): String? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (v.startsWith("http")) return v
        val base = com.aman.gigi.utils.Constants.SERVER_URL
            .replaceFirst("wss://", "https://")
            .replaceFirst("ws://", "http://")
            .trimEnd('/')
        return if (v.startsWith("/")) "$base$v" else v
    }

    /** Persists a chat message delivered via FCM (app was killed / offline) and shows the bubble. */
    fun deliverChatFromPush(
        connectionId: String, senderName: String, msgType: String,
        text: String, gifUrl: String, clientMsgId: String
    ) {
        val lower = connectionId.lowercase()
        if (lower.isBlank()) return
        val cleanText = text.trim()
        val cleanGif = gifUrl.trim()
        if (cleanText.isBlank() && cleanGif.isBlank()) {
            Log.w(TAG, "Ignoring blank chat message from push")
            return
        }
        scope.launch {
            val id = clientMsgId.ifBlank { java.util.UUID.randomUUID().toString() }
            chatRepository.save(
                com.aman.gigi.model.ChatMessage(
                    id = id, connectionId = lower, senderDeviceId = "",
                    senderName = senderName, isMine = false, type = msgType,
                    text = cleanText, gifUrl = cleanGif,
                    sentAt = System.currentTimeMillis(), status = "DELIVERED"
                )
            )
            val preview = if (msgType == "gif") "sent a GIF 🎞️" else cleanText
            _events.emit(SyncEvent.ChatMessageReceived(lower, senderName, preview))
            if (com.aman.gigi.ui.chat.ChatPresence.openConnectionId != lower) {
                triggerChatBubble(lower, senderName, preview, msgType, cleanGif)
            }
        }
    }

    /** Handles a doodle/scribble or sparkle delivered via FCM push when the app is closed. */
    fun deliverScribbleFromPush(
        connectionId: String,
        assetRef: String,
        scribbleId: String? = null,
        actionType: String? = "scribble"
    ) {
        val lower = connectionId.lowercase()
        if (lower.isBlank()) return
        scope.launch {
            try {
                val sId = scribbleId?.takeIf { it.isNotBlank() }
                    ?: ("fcm_" + lower + "_" + (if (assetRef.isNotBlank()) assetRef.hashCode() else (System.currentTimeMillis() / 60000L)))
                Log.i(TAG, "🎨 [FCM-Push] Processing incoming doodle push for $lower: asset=$assetRef, id=$sId")
                
                val baseUrl = com.aman.gigi.utils.Constants.SERVER_URL.trimEnd('/')
                val cleanAsset = assetRef.replace("\\", "/")
                    .trimStart('/')
                    .removePrefix("app/")
                    .removePrefix("captures/")
                    .trimStart('/')
                val fullUrl = if (assetRef.startsWith("http://", ignoreCase = true) || assetRef.startsWith("https://", ignoreCase = true)) {
                    assetRef.replace("/app/captures/", "/captures/").replace("/captures/captures/", "/captures/")
                } else {
                    "$baseUrl/captures/$cleanAsset"
                }

                var parsedScribble: Scribble? = null

                if (cleanAsset.isNotBlank()) {
                    val tempFile = java.io.File(context.cacheDir, "fcm_download_$sId.bin")
                    val downloaded = httpUploader.downloadFile(fullUrl, tempFile)
                    if (downloaded && tempFile.exists() && tempFile.length() > 0) {
                        val rawBytes = tempFile.readBytes()
                        tempFile.delete()

                        // Try decompressing GZIP payload first (standard binary scribble packet format)
                        val decompressedBytes = runCatching {
                            java.util.zip.GZIPInputStream(rawBytes.inputStream()).use { it.readBytes() }
                        }.getOrNull() ?: rawBytes

                        val jsonString = runCatching {
                            String(decompressedBytes, java.nio.charset.StandardCharsets.UTF_8)
                        }.getOrNull()

                        if (jsonString != null && jsonString.startsWith("{") && jsonString.endsWith("}")) {
                            parsedScribble = ScribbleSerializer.deserialize(jsonString)
                        } else if (cleanAsset.endsWith(".png", ignoreCase = true) || 
                                   cleanAsset.endsWith(".jpg", ignoreCase = true) || 
                                   cleanAsset.endsWith(".jpeg", ignoreCase = true) || 
                                   cleanAsset.endsWith(".gif", ignoreCase = true) || 
                                   cleanAsset.endsWith(".webp", ignoreCase = true)) {
                            val base64 = android.util.Base64.encodeToString(rawBytes, android.util.Base64.NO_WRAP)
                            parsedScribble = Scribble(
                                scribbleId = sId,
                                connectionId = lower,
                                strokes = emptyList(),
                                isSent = false,
                                status = com.aman.gigi.model.ScribbleStatus.RECEIVED,
                                revealType = if (actionType == "sparkle") "SPARKLE" else null,
                                mediaUrl = fullUrl,
                                mediaBase64 = base64
                            )
                        }
                    }
                }

                val finalScribble = parsedScribble?.copy(
                    scribbleId = sId,
                    connectionId = lower,
                    isSent = false,
                    status = com.aman.gigi.model.ScribbleStatus.RECEIVED,
                    revealType = if (actionType == "sparkle") "SPARKLE" else parsedScribble.revealType
                ) ?: Scribble(
                    scribbleId = sId,
                    connectionId = lower,
                    strokes = emptyList(),
                    isSent = false,
                    status = com.aman.gigi.model.ScribbleStatus.RECEIVED,
                    revealType = if (actionType == "sparkle") "SPARKLE" else null,
                    mediaUrl = if (cleanAsset.endsWith(".png", ignoreCase = true) || 
                                   cleanAsset.endsWith(".jpg", ignoreCase = true) || 
                                   cleanAsset.endsWith(".jpeg", ignoreCase = true) || 
                                   cleanAsset.endsWith(".gif", ignoreCase = true) || 
                                   cleanAsset.endsWith(".webp", ignoreCase = true)) fullUrl else null,
                    mediaBase64 = null
                )
                
                scribbleRepository.saveReceivedScribble(finalScribble)
                triggerLockscreenScribble(finalScribble)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error handling doodle from FCM push", e)
            }
        }
    }

    /** Broadcasts my avatar/emoji/name to a connected partner. */
    fun sendProfileUpdate(connectionId: String, avatarUrl: String, emojiUrl: String, name: String) {
        val envelope = org.json.JSONObject().apply {
            put("type", "profile_update")
            put("connectionId", connectionId.lowercase())
            put("avatarUrl", avatarUrl)
            put("emojiUrl", emojiUrl)
            put("name", name)
        }
        webSocketClient.sendText(connectionId.lowercase(), envelope.toString())
    }

    /** Sends a group invite to a partner over an existing connection. */
    fun sendGroupInvite(viaConnectionId: String, groupCode: String, groupName: String) {
        val myName = bootstrapManager.memberIdentity.value?.displayName ?: "A friend"
        val envelope = org.json.JSONObject().apply {
            put("type", "group_invite")
            put("viaConnectionId", viaConnectionId.lowercase())
            put("groupCode", groupCode.lowercase())
            put("groupName", groupName)
            put("inviterName", myName)
        }
        webSocketClient.sendText(viaConnectionId.lowercase(), envelope.toString())
    }

    /** Sends a chat message over the WebSocket and stores it locally as mine. */
    // ── Now Playing ───────────────────────────────────────────────────────────
    // Share "what I'm listening to" with every connection. Works with any player
    // (Spotify / YT Music / local) because it comes from the phone's media session.
    // Live-only: relayed over the socket, never persisted.
    private var nowPlayingStarted = false

    fun startNowPlayingSharing() {
        val tracker = nowPlayingTracker
        if (nowPlayingStarted) return
        nowPlayingStarted = true
        scope.launch {
            tracker.mine.collect { np ->
                val codes = runCatching {
                    connectionRepository.getAllActiveConnectionsOnce().map { it.connectionId.lowercase() }
                }.getOrDefault(emptyList())
                val myName = bootstrapManager.memberIdentity.value?.displayName ?: "Someone"
                codes.forEach { code ->
                    val envelope = org.json.JSONObject().apply {
                        put("type", "now_playing")
                        put("connectionId", code)
                        put("senderName", myName)
                        put("title", np?.title ?: "")
                        put("artist", np?.artist ?: "")
                        put("app", np?.app ?: "")
                        put("playing", np?.isPlaying ?: false)
                    }
                    runCatching { webSocketClient.sendText(code, envelope.toString()) }
                }
            }
        }
    }

    private fun handleIncomingNowPlaying(json: org.json.JSONObject, connectionId: String) {
        val tracker = nowPlayingTracker
        val title = json.optString("title").trim()
        if (title.isBlank()) { tracker.clearOther(connectionId); return }
        tracker.updateOther(
            connectionId,
            com.aman.gigi.data.nowplaying.NowPlaying(
                title = title,
                artist = json.optString("artist").trim(),
                app = json.optString("app").trim(),
                isPlaying = json.optBoolean("playing", true)
            )
        )
    }

    // ── Break cards ───────────────────────────────────────────────────────────
    // A break invite is live-only (like a ringing call): it is relayed over the socket
    // and held in memory, never persisted. activeBreak drives the full-screen overlay;
    // breakResponses is the live accept/reject tally everyone sees.
    private val _activeBreak = MutableStateFlow<com.aman.gigi.model.BreakInvite?>(null)
    val activeBreak: StateFlow<com.aman.gigi.model.BreakInvite?> = _activeBreak

    private val _breakResponses = MutableStateFlow<List<com.aman.gigi.model.BreakResponse>>(emptyList())
    val breakResponses: StateFlow<List<com.aman.gigi.model.BreakResponse>> = _breakResponses

    private val _myBreakAnswer = MutableStateFlow<Boolean?>(null)
    val myBreakAnswer: StateFlow<Boolean?> = _myBreakAnswer

    fun sendBreakInvite(connectionId: String, cardId: String) {
        val lower = connectionId.lowercase()
        val myName = bootstrapManager.memberIdentity.value?.displayName ?: "Someone"
        val breakId = java.util.UUID.randomUUID().toString()
        _activeBreak.value = com.aman.gigi.model.BreakInvite(
            breakId = breakId, connectionId = lower, cardId = cardId,
            fromName = myName, fromDeviceId = "", isMine = true
        )
        _breakResponses.value = emptyList()
        _myBreakAnswer.value = true          // the caller is implicitly in
        val envelope = org.json.JSONObject().apply {
            put("type", "break_invite")
            put("connectionId", lower)
            put("breakId", breakId)
            put("cardId", cardId)
            put("senderName", myName)
        }
        webSocketClient.sendText(lower, envelope.toString())
    }

    fun sendBreakResponse(accepted: Boolean) {
        val invite = _activeBreak.value ?: return
        val myName = bootstrapManager.memberIdentity.value?.displayName ?: "Someone"
        _myBreakAnswer.value = accepted
        val envelope = org.json.JSONObject().apply {
            put("type", "break_response")
            put("connectionId", invite.connectionId)
            put("breakId", invite.breakId)
            put("accepted", accepted)
            put("senderName", myName)
        }
        webSocketClient.sendText(invite.connectionId, envelope.toString())
    }

    fun dismissBreak() {
        _activeBreak.value = null
        _breakResponses.value = emptyList()
        _myBreakAnswer.value = null
    }

    private fun handleIncomingBreakInvite(json: org.json.JSONObject, connectionId: String) {
        val breakId = json.optString("breakId").takeIf { it.isNotBlank() } ?: return
        _activeBreak.value = com.aman.gigi.model.BreakInvite(
            breakId = breakId,
            connectionId = connectionId.lowercase(),
            cardId = json.optString("cardId", "tea"),
            fromName = json.optString("senderName", "Someone"),
            fromDeviceId = json.optString("senderDeviceId", ""),
            isMine = false
        )
        _breakResponses.value = emptyList()
        _myBreakAnswer.value = null
        launchBreakFullScreen(breakId, json.optString("cardId", "tea"),
            json.optString("senderName", "Someone"), connectionId.lowercase())
    }

    /**
     * Pops the break card over the lock screen / other apps, the same way an incoming
     * scribble does: a CATEGORY_CALL notification with a full-screen intent, plus a
     * direct launch attempt for when background starts are permitted.
     */
    private fun launchBreakFullScreen(
        breakId: String, cardId: String, fromName: String, connectionId: String
    ) {
        try {
            val intent = android.content.Intent(
                context, com.aman.gigi.ui.breaks.BreakInviteActivity::class.java
            ).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("break_id", breakId)
                putExtra("card_id", cardId)
                putExtra("from_name", fromName)
                putExtra("connection_id", connectionId)
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, breakId.hashCode(), intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val channelId = "break_alerts_v1"
            createNotificationChannel(channelId)
            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("$fromName called a break!")
                .setContentText("Tap to join in")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVibrate(longArrayOf(0, 600L))
                .setAutoCancel(true)
                .build()
            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            nm.notify(breakId.hashCode(), notification)
            try { context.startActivity(intent) } catch (e: Exception) {
                Log.w(TAG, "break direct launch blocked, relying on full-screen intent: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show break invite", e)
        }
    }

    private fun handleIncomingBreakResponse(json: org.json.JSONObject) {
        val breakId = json.optString("breakId")
        if (breakId.isBlank() || _activeBreak.value?.breakId != breakId) return
        val r = com.aman.gigi.model.BreakResponse(
            breakId = breakId,
            deviceId = json.optString("senderDeviceId", ""),
            name = json.optString("senderName", "Someone"),
            accepted = json.optBoolean("accepted", false)
        )
        _breakResponses.value = _breakResponses.value
            .filterNot { it.deviceId == r.deviceId && it.name == r.name } + r
    }

    fun sendChatMessage(connectionId: String, text: String, gifUrl: String = "") {
        val lower = connectionId.lowercase()
        val id = java.util.UUID.randomUUID().toString()
        val myName = bootstrapManager.memberIdentity.value?.displayName ?: "Me"
        val type = if (gifUrl.isNotBlank()) "gif" else "text"
        scope.launch {
            chatRepository.save(
                com.aman.gigi.model.ChatMessage(
                    id = id,
                    connectionId = lower,
                    senderDeviceId = "",
                    senderName = myName,
                    isMine = true,
                    type = type,
                    text = text,
                    gifUrl = gifUrl,
                    sentAt = System.currentTimeMillis(),
                    status = "SENT"
                )
            )
            val envelope = org.json.JSONObject().apply {
                put("type", "chat_message")
                put("connectionId", lower)
                put("senderName", myName)
                put("msgType", type)
                put("text", text)
                put("gifUrl", gifUrl)
                put("clientMsgId", id)
            }
            webSocketClient.sendText(lower, envelope.toString())
        }
    }

    fun sendCustomJson(connectionId: String, type: String, payload: Map<String, Any>) {
        val lower = connectionId.lowercase()
        val envelope = org.json.JSONObject().apply {
            put("type", type)
            put("connectionId", lower)
            payload.forEach { (k, v) -> put(k, v) }
        }
        webSocketClient.sendText(lower, envelope.toString())
    }

}

/**
 * Sync events for UI observation
 */
sealed class SyncEvent {
    data class ChatMessageReceived(
        val connectionId: String,
        val senderName: String,
        val preview: String
    ) : SyncEvent()
    data class GroupInviteReceived(
        val groupCode: String,
        val groupName: String,
        val inviterName: String
    ) : SyncEvent()
    data class AlarmDoneTogether(val connectionId: String, val alarmTitle: String, val text: String, val emoji: String) : SyncEvent()
    data class PartnerDisconnected(val connectionId: String, val partnerName: String) : SyncEvent()
    data class PartnerPresenceChanged(
        val connectionId: String,
        val partnerName: String,
        val isOnline: Boolean,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val lastSeenAt: Long?,
        val showPopup: Boolean
    ) : SyncEvent()
    data class SendFailed(val connectionId: String, val reason: String) : SyncEvent()
    data class SendSuccess(val connectionId: String, val scribbleId: String) : SyncEvent()
    data class ConnectionLimitReached(val connectionId: String, val message: String) : SyncEvent()
    data class PresenceUpdate(val connectionId: String) : SyncEvent()
    data class PhotoListReceived(val connectionId: String, val photos: List<com.aman.gigi.model.RemotePhoto>) : SyncEvent()
    data class PhotoDownloadReceived(val connectionId: String, val photoId: String, val data: ByteArray? = null, val file: java.io.File? = null) : SyncEvent()
    data class FileListReceived(val connectionId: String, val path: String, val files: List<com.aman.gigi.model.RemoteFile>) : SyncEvent()
    data class FileDownloadReceived(val connectionId: String, val fileName: String, val data: ByteArray? = null, val file: java.io.File? = null) : SyncEvent()
    data class FileDownloadProgress(val connectionId: String, val fileName: String, val progress: Int) : SyncEvent()
    data class NotificationReceived(val connectionId: String, val notification: com.aman.gigi.model.RemoteNotification) : SyncEvent()
    data class LiveVideoFrameReceived(val connectionId: String, val data: ByteArray) : SyncEvent()

    data class HistoryReceived(
        val connectionId: String,
        val scribbles: List<com.aman.gigi.model.Scribble>,
        val notifications: List<com.aman.gigi.model.RemoteNotification>
    ) : SyncEvent()
    data class SearchResults(val category: String, val results: List<com.aman.gigi.model.RemoteNotification>) : SyncEvent()
    data class NotificationAppsResult(val apps: List<String>) : SyncEvent()
    
    // Remote Feature Events
    data class QuoteReceived(
        val connectionId: String, 
        val text: String, 
        val senderName: String
    ) : SyncEvent()
    data class PartnerProfileUpdated(val connectionId: String, val partnerName: String, val partnerId: String) : SyncEvent()
    data class LoveCardStackReceived(
        val connectionId: String,
        val stackId: String,
        val title: String,
        val cardCount: Int,
        val senderName: String
    ) : SyncEvent()
    data class LoveCardStackAnswered(
        val connectionId: String,
        val stackId: String,
        val title: String,
        val answerCount: Int
    ) : SyncEvent()
    
    // Connection Lifecycle Events
    data class Connecting(val connectionId: String) : SyncEvent()
    data class Connected(val connectionId: String, val partnerName: String?) : SyncEvent()
    data class Reconnecting(val connectionId: String, val reason: String) : SyncEvent()
    data class IdleTimeout(val connectionId: String, val idleMinutes: Int) : SyncEvent()
    data class AudioDownloadReceived(val connectionId: String, val audioId: String, val data: ByteArray) : SyncEvent()
    data class SessionExpired(val connectionId: String) : SyncEvent()
    data class ForceLogout(val reason: String) : SyncEvent()
    data class ConnectionRemoved(val connectionCode: String, val reason: String) : SyncEvent()
    data class SharedAlbumReceived(val connectionId: String, val albumName: String) : SyncEvent()
    data class RemoteCommand(
        val connectionId: String,
        val command: String,
        val data: org.json.JSONObject? = null
    ) : SyncEvent()
    
    data class MusicCompatibilityReceived(val connectionId: String, val score: Int) : SyncEvent()
}

/**
 * Sync statistics
 */
data class SyncStats(
    val pendingCount: Int,
    val sendingCount: Int,
    val failedCount: Int
)
