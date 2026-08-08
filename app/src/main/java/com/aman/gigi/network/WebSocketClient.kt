package com.aman.gigi.network

import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * WebSocket client for real-time scribble communication
 */
@Singleton
class WebSocketClient @Inject constructor() {
    
    private val sessions = mutableMapOf<String, WebSocket>()
    private val sessionStates = mutableMapOf<String, Boolean>() // connectionId -> isOpened

    // Dedicated dispatcher for large binary sends — prevents blocking heartbeat on Dispatchers.IO
    private val sendDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    private val reconnectDelayMap = mutableMapOf<String, Long>()

    init {
        android.util.Log.i("WebSocketClient", "🚀 WebSocketClient initialized with Multi-Session support")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    private val _messages = MutableSharedFlow<SessionMessage>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<SessionMessage> = _messages.asSharedFlow()
    
    private val lastConnectAttempts = mutableMapOf<String, Long>()

    fun scheduleReconnect(connectionId: String, url: String, deviceId: String, listener: ConnectionListener) {
        val currentDelay = reconnectDelayMap[connectionId] ?: 2000L
        val nextDelay = (currentDelay * 2).coerceAtMost(30000L) // exponential backoff up to 30s
        reconnectDelayMap[connectionId] = nextDelay
        
        android.util.Log.i("WebSocketClient", "🔄 [$connectionId] Scheduling reconnect in ${currentDelay / 1000}s...")
        scope.launch {
            kotlinx.coroutines.delay(currentDelay)
            if (!isConnected(connectionId)) {
                connect(connectionId, url, deviceId, listener)
            }
        }
    }

    /**
     * Connect to WebSocket server for a specific connection
     */
    fun connect(connectionId: String, url: String, deviceId: String, listener: ConnectionListener) {
        // Debounce: Prevent rapid reconnects (flapping)
        val lastAttempt = lastConnectAttempts[connectionId] ?: 0L
        if (System.currentTimeMillis() - lastAttempt < 3000) { // 3 seconds debounce
            android.util.Log.w("WebSocketClient", "⚠️ [$connectionId] Connect throttled (debounce active)")
            return
        }
        lastConnectAttempts[connectionId] = System.currentTimeMillis()

        // Close existing session for this ID if any
        disconnect(connectionId)
        
        android.util.Log.i("WebSocketClient", "🔌 [$connectionId] Connecting to: $url")
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Device-Id", deviceId)
            .addHeader("Connection-Id", connectionId)
            .addHeader("User-Agent", "GigiAndroid/1.0")
            .addHeader("Origin", "https://gigi.iamanraj.com")
            .build()
        
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                android.util.Log.i("WebSocketClient", "✅ [$connectionId] WebSocket OPENED")
                sessionStates[connectionId] = true
                reconnectDelayMap[connectionId] = 2000L // Reset backoff delay on success
                listener.onConnected()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                _messages.tryEmit(SessionMessage.TextMessage(connectionId, text))
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                _messages.tryEmit(SessionMessage.BinaryMessage(connectionId, bytes.toByteArray()))
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.w("WebSocketClient", "🔌 [$connectionId] WebSocket CLOSING: $code - $reason")
                listener.onDisconnecting(code, reason)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.w("WebSocketClient", "🔌 [$connectionId] WebSocket CLOSED: $code - $reason")
                sessions.remove(connectionId)
                sessionStates.remove(connectionId)
                listener.onDisconnected(code, reason)
                scheduleReconnect(connectionId, url, deviceId, listener)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.e("WebSocketClient", "💥 [$connectionId] WebSocket FAILED. Message: ${t.message}", t)
                sessions.remove(connectionId)
                sessionStates.remove(connectionId)
                listener.onError(t)
                scheduleReconnect(connectionId, url, deviceId, listener)
            }
        })
        
        sessions[connectionId] = ws
    }
    
    fun sendText(connectionId: String, message: String): Boolean {
        return sessions[connectionId]?.send(message) ?: false
    }
    
    fun sendBinary(connectionId: String, data: ByteArray): Boolean {
        return sessions[connectionId]?.send(ByteString.of(*data)) ?: false
    }

    /**
     * Non-blocking binary send on a dedicated thread pool.
     * Use for large data (photos, files, video frames, audio) to avoid starving heartbeats.
     */
    suspend fun sendBinaryAsync(connectionId: String, data: ByteArray): Boolean {
        return withContext(sendDispatcher) {
            sessions[connectionId]?.send(ByteString.of(*data)) ?: false
        }
    }

    fun disconnect(connectionId: String) {
        sessions[connectionId]?.close(1000, "Client disconnect")
        sessions.remove(connectionId)
        sessionStates.remove(connectionId)
    }
    
    fun isConnected(connectionId: String): Boolean {
        return sessions.containsKey(connectionId) && (sessionStates[connectionId] == true)
    }
    
    interface ConnectionListener {
        fun onConnected()
        fun onDisconnecting(code: Int, reason: String)
        fun onDisconnected(code: Int, reason: String)
        fun onError(throwable: Throwable)
    }
}

/**
 * Session-aware message types
 */
sealed class SessionMessage {
    abstract val connectionId: String
    data class TextMessage(override val connectionId: String, val text: String) : SessionMessage()
    data class BinaryMessage(override val connectionId: String, val data: ByteArray) : SessionMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BinaryMessage
            return connectionId == other.connectionId && data.contentEquals(other.data)
        }
        override fun hashCode(): Int {
            var result = connectionId.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}

/**
 * WebSocket message types
 */
sealed class WebSocketMessage {
    data class TextMessage(val text: String) : WebSocketMessage()
    data class BinaryMessage(val data: ByteArray) : WebSocketMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BinaryMessage
            return data.contentEquals(other.data)
        }
        
        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }
}
