package com.aman.gigi.ui.screensaver

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aman.gigi.data.sync.ScribbleSerializer
import com.aman.gigi.model.Scribble
import com.aman.gigi.ui.screensaver.components.ScribblePlaybackComponent
import com.aman.gigi.ui.theme.RemindMeTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.material.icons.Icons
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.PhotoCamera

@AndroidEntryPoint
class LockscreenScribbleActivity : ComponentActivity() {
    
    @Inject
    lateinit var scribbleRepository: com.aman.gigi.repository.ScribbleRepository

    @Inject
    lateinit var syncManager: com.aman.gigi.data.sync.ScribbleSyncManager

    private data class ScribbleData(
        val scribbleId: String,
        val json: String,
        val partnerName: String,
        val connectionId: String?
    )
    
    private var scribbleState by mutableStateOf<ScribbleData?>(null)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Essential flags for lockscreen display
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Hide system bars for immersive experience
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // Initial setup from intent
        updateScribbleState(intent)
        
        enableEdgeToEdge()
        
        setContent {
            RemindMeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White // Default fallback
                ) {
                    val currentOperations = scribbleState
                    if (currentOperations != null) {
                        // Use key to reset state when a new scribble arrives
                        key(currentOperations.json) {
                            LockscreenScribbleContent(
                                scribbleJson = currentOperations.json,
                                partnerName = currentOperations.partnerName,
                                connectionId = currentOperations.connectionId,
                                onDisplayed = { scribbleId, displayedConnectionId ->
                                    lifecycleScope.launch {
                                        runCatching {
                                            scribbleRepository.markAsDisplayed(scribbleId)
                                            if (!displayedConnectionId.isNullOrBlank()) {
                                                syncManager.sendActionReceipt(
                                                    connectionId = displayedConnectionId,
                                                    actionId = scribbleId,
                                                    receiptType = com.aman.gigi.data.sync.SyncProtocol.ACTION_ACTION_DISPLAYED
                                                )
                                            }
                                        }.onFailure { error ->
                                            android.util.Log.e(
                                                "LockscreenScribble",
                                                "Failed to mark scribble displayed: $scribbleId",
                                                error
                                            )
                                        }
                                    }
                                },
                                onFinished = { 
                                    syncManager.cancelNotification(currentOperations.scribbleId)
                                    finish() 
                                }
                            )
                        }
                    } else {
                        // Loading State
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF8B5CF6))
                        }
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        android.util.Log.d("LockscreenScribble", "onNewIntent received")
        updateScribbleState(intent)
    }
    
    private fun updateScribbleState(intent: Intent) {
        val id = intent.getStringExtra("scribble_id") ?: intent.getStringExtra("scribbleId") ?: intent.getStringExtra("messageId")
        val json = intent.getStringExtra("scribble_json")
        val name = intent.getStringExtra("partner_name") ?: "Partner"
        val connectionId = intent.getStringExtra("connection_id") ?: intent.getStringExtra("connectionId")
        
        if (!id.isNullOrBlank()) {
            syncManager.cancelNotification(id)
        }

        if (json != null && id != null) {
            android.util.Log.d("LockscreenScribble", "Updating state from Intent JSON, length: ${json.length}")
            scribbleState = ScribbleData(id, json, name, connectionId)
        } else if (id != null) {
            android.util.Log.d("LockscreenScribble", "Updating state from Database ID: $id")
            lifecycleScope.launch {
                var scribble: Scribble? = null
                for (attempt in 1..3) {
                    scribble = scribbleRepository.getScribbleById(id)
                    if (scribble != null) break
                    kotlinx.coroutines.delay(250L)
                }
                if (scribble != null) {
                    android.util.Log.d("LockscreenScribble", "Successfully loaded from DB: ${scribble.scribbleId}")
                    val serialized = ScribbleSerializer.serialize(scribble)
                    scribbleState = ScribbleData(scribble.scribbleId, serialized, name, connectionId)
                } else {
                    android.util.Log.e("LockscreenScribble", "Scribble not found in DB after retries: $id, trying fallback")
                    loadLatestScribbleFallback(connectionId, name)
                }
            }
        } else {
            android.util.Log.e("LockscreenScribble", "No ID in intent, trying fallback")
            loadLatestScribbleFallback(connectionId, name)
        }
    }

    private fun loadLatestScribbleFallback(connectionId: String?, name: String) {
        lifecycleScope.launch {
            val latestSummary = scribbleRepository.getLatestScribbleSummaries(limit = 1).firstOrNull()
            val latestScribble = latestSummary?.let { scribbleRepository.getScribbleById(it.scribbleId) }
            if (latestScribble != null) {
                android.util.Log.d("LockscreenScribble", "Loaded latest scribble fallback: ${latestScribble.scribbleId}")
                val serialized = ScribbleSerializer.serialize(latestScribble)
                scribbleState = ScribbleData(latestScribble.scribbleId, serialized, name, connectionId ?: latestScribble.connectionId)
            } else {
                android.util.Log.e("LockscreenScribble", "No scribbles available in DB fallback")
                finish()
            }
        }
    }
}

@Composable
fun LockscreenScribbleContent(
    scribbleJson: String?,
    partnerName: String,
    connectionId: String?,
    onDisplayed: (scribbleId: String, connectionId: String?) -> Unit,
    onFinished: () -> Unit,
    viewModel: com.aman.gigi.viewmodel.ScreensaverViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    var isReplying by remember { mutableStateOf(false) }
    var showReplyPicker by remember { mutableStateOf(false) }
    var scribble by remember { mutableStateOf<Scribble?>(null) }
    var displayReceiptSent by remember(scribbleJson) { mutableStateOf(false) }
    
    LaunchedEffect(scribbleJson) {
        if (scribbleJson != null) {
            val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ScribbleSerializer.deserialize(scribbleJson)
            }
            scribble = decoded
        }
    }

    fun markDisplayedIfNeeded() {
        val currentScribble = scribble ?: return
        if (displayReceiptSent) return
        displayReceiptSent = true
        onDisplayed(currentScribble.scribbleId, connectionId)
    }
    
    
    // Matching Main App Background Gradient
    val backgroundBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF0F4F8), // Soft Gray-Blue (Top)
            Color(0xFFE6E0FF), // Pale Lavender (Middle)
            Color(0xFFF3E5F5)  // Light Purple (Bottom)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
            if (isReplying && connectionId != null) {
            com.aman.gigi.ui.screensaver.drawing.DrawingScreen(
                connectionId = connectionId,
                onCancel = { isReplying = false },
                onSend = { 
                    markDisplayedIfNeeded()
                    onFinished()
                }
            )
        } else {
            // Fullscreen Scribble Player
            if (scribbleJson != null) {
                ScribblePlaybackComponent(
                    scribbleJson = scribbleJson,
                    onAnimationFinished = {
                        markDisplayedIfNeeded()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Connection Status Banner (Overlay at the top)
            ConnectionStatusBanner(
                connectionId = connectionId,
                syncManager = viewModel.syncManager,
                connectionRepository = viewModel.connectionRepository
            )

            // Top Header Overlay (Glass)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 80.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            ) {
                com.skydoves.cloudy.Cloudy(radius = 30) {
                    Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.15f)))
                }
                Text(
                    text = "New Scribble from $partnerName",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1A237E),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(16.dp).align(Alignment.Center)
                )
            }
            
            // Bottom Buttons Overlay (Glass)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 64.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
            ) {
                com.skydoves.cloudy.Cloudy(radius = 30) {
                    Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.15f)))
                }
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            markDisplayedIfNeeded()
                            onFinished()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Dismiss", color = Color.Black.copy(alpha = 0.7f))
                    }
                    
                    Button(
                        onClick = { showReplyPicker = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6).copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Reply", color = Color.White)
                    }
                }
            }

            // Reply Picker Dialog
            if (showReplyPicker) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showReplyPicker = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.95f))
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Reply to $partnerName",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color(0xFF1A237E)
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            
                            // Scribble Option
                            Button(
                                onClick = { 
                                    showReplyPicker = false
                                    isReplying = true 
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                            ) {
                                Icon(Icons.Default.Brush, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text("Scribble", fontSize = 16.sp)
                            }
                            
                            // Sparkle Option
                            val context = androidx.compose.ui.platform.LocalContext.current
                            Button(
                                onClick = {
                                    showReplyPicker = false
                                    // Launch Main App with Sparkle Action
                                    val intent = Intent(context, com.aman.gigi.ui.MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        action = "ACTION_REPLY_SPARKLE"
                                        putExtra("connection_id", connectionId)
                                    }
                                    markDisplayedIfNeeded()
                                    context.startActivity(intent)
                                    onFinished() // Close lockscreen activity
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081))
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text("Sparkle", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
fun ConnectionStatusBanner(
    connectionId: String?,
    syncManager: com.aman.gigi.data.sync.ScribbleSyncManager,
    connectionRepository: com.aman.gigi.repository.ConnectionRepository
) {
    var statusText by remember { mutableStateOf<String?>(null) }
    var statusColor by remember { mutableStateOf(Color(0xFF8B5CF6)) }
    var isVisible by remember { mutableStateOf(false) }
    var isStealth by remember { mutableStateOf(false) }

    // Stealth Check: If we are the joinee, never show this banner
    LaunchedEffect(connectionId) {
        if (connectionId != null) {
            connectionRepository.getConnectionById(connectionId).collect { conn ->
                if (conn != null) {
                    val myDeviceId = syncManager.deviceId
                    // If creatorDeviceId is set and it's NOT us, we are a Joinee (monitored)
                    isStealth = conn.creatorDeviceId != null && conn.creatorDeviceId != myDeviceId
                    android.util.Log.d("ConnectionStatusBanner", "Connection role: creatorDeviceId=${conn.creatorDeviceId}, myId=$myDeviceId, isStealth=$isStealth")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        syncManager.events.collect { event ->
            // Only care about events for THIS connection
            val eventId = when (event) {
                is com.aman.gigi.data.sync.SyncEvent.Connecting -> event.connectionId
                is com.aman.gigi.data.sync.SyncEvent.Connected -> event.connectionId
                is com.aman.gigi.data.sync.SyncEvent.Reconnecting -> event.connectionId
                is com.aman.gigi.data.sync.SyncEvent.IdleTimeout -> event.connectionId
                else -> null
            }
            
            if (eventId != connectionId) return@collect

            when (event) {
                is com.aman.gigi.data.sync.SyncEvent.Connecting -> {
                    statusText = "Connecting..."
                    statusColor = Color(0xFF8B5CF6)
                    isVisible = true
                }
                is com.aman.gigi.data.sync.SyncEvent.Connected -> {
                    statusText = "Connected ✓"
                    statusColor = Color(0xFF4CAF50)
                    isVisible = true
                    kotlinx.coroutines.delay(2000)
                    isVisible = false
                }
                is com.aman.gigi.data.sync.SyncEvent.Reconnecting -> {
                    statusText = "Reconnecting..."
                    statusColor = Color(0xFFFF9800)
                    isVisible = true
                }
                is com.aman.gigi.data.sync.SyncEvent.IdleTimeout -> {
                    statusText = "Disconnected (Idle)"
                    statusColor = Color(0xFF757575)
                    isVisible = true
                }
                else -> {}
            }
        }
    }

    if (!isStealth && isVisible && statusText != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .statusBarsPadding()
                .padding(horizontal = 48.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                color = statusColor.copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (statusText!!.contains("Connecting") || statusText!!.contains("Reconnecting")) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = statusText!!,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                }
            }
        }
    }
}
