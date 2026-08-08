package com.aman.gigi.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.gigi.model.*
import com.aman.gigi.repository.ConnectionRepository
import com.aman.gigi.repository.ScribbleRepository
import com.aman.gigi.utils.StrokeSmoothing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for drawing canvas
 */
enum class SendStatus {
    IDLE,
    SENDING,
    SENT,
    QUEUED,
    ERROR
}

@HiltViewModel
class DrawingViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val scribbleRepository: com.aman.gigi.repository.ScribbleRepository,
    private val connectionRepository: com.aman.gigi.repository.ConnectionRepository,
    private val gifRepository: com.aman.gigi.repository.GifRepository,
    private val syncManager: com.aman.gigi.data.sync.ScribbleSyncManager
) : ViewModel() {

    private val TAG = "DrawingViewModel"
    
    // Recent GIFs
    val recentGifs: StateFlow<List<String>> = gifRepository.getRecentGifs()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())
    
    // Undo/Redo stacks
    private val _undoStack = MutableStateFlow<List<List<Stroke>>>(emptyList()) // each entry is a full strokes snapshot
    private val _redoStack = MutableStateFlow<List<List<Stroke>>>(emptyList())

    // Current strokes being drawn
    private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
    val strokes: StateFlow<List<Stroke>> = _strokes.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()
    
    // Current stroke being drawn
    private val _currentStroke = MutableStateFlow<Stroke?>(null)
    val currentStroke: StateFlow<Stroke?> = _currentStroke.asStateFlow()
    
    // Drawing tool state
    private val _selectedColor = MutableStateFlow(Color(0xFF8B5CF6).toArgb())
    val selectedColor: StateFlow<Int> = _selectedColor.asStateFlow()
    
    private val _strokeWidth = MutableStateFlow(8f)
    val strokeWidth: StateFlow<Float> = _strokeWidth.asStateFlow()
    
    private val _isEraser = MutableStateFlow(false)
    val isEraser: StateFlow<Boolean> = _isEraser.asStateFlow()
    
    // Sync Events
    val syncEvents = syncManager.events
    
    // Send Status for UI
    private val _sendStatus = MutableStateFlow(SendStatus.IDLE)
    val sendStatus: StateFlow<SendStatus> = _sendStatus.asStateFlow()

    private val _errorReason = MutableStateFlow<String?>(null)
    val errorReason: StateFlow<String?> = _errorReason.asStateFlow()

    // --- Advanced Drawing State ---
    private val _selectedBrush = MutableStateFlow(BrushType.MARKER)
    val selectedBrush: StateFlow<BrushType> = _selectedBrush.asStateFlow()
    
    private val _selectedEffects = MutableStateFlow<Set<EffectType>>(emptySet())
    val selectedEffects: StateFlow<Set<EffectType>> = _selectedEffects.asStateFlow()
    
    private val _selectedAnimation = MutableStateFlow(AnimationType.NONE)
    val selectedAnimation: StateFlow<AnimationType> = _selectedAnimation.asStateFlow()
    
    private val _selectedOpacity = MutableStateFlow(1.0f)
    val selectedOpacity: StateFlow<Float> = _selectedOpacity.asStateFlow()
    
    private val _showSettingsPanel = MutableStateFlow(false)
    val showSettingsPanel: StateFlow<Boolean> = _showSettingsPanel.asStateFlow()

    private val _isDrawing = MutableStateFlow(false)
    val isDrawing: StateFlow<Boolean> = _isDrawing.asStateFlow()

    private val _isToolPanelExpanded = MutableStateFlow(false)
    val isToolPanelExpanded: StateFlow<Boolean> = _isToolPanelExpanded.asStateFlow()

    init {
        // Observe sync events
        viewModelScope.launch {
            syncEvents.collect { event ->
                when (event) {
                    is com.aman.gigi.data.sync.SyncEvent.SendSuccess -> {
                        android.util.Log.i(TAG, "🏁 DrawingViewModel received SendSuccess for ${event.scribbleId}")
                        // Transition to SENT even if we missed the precise SENDING start
                        if (_sendStatus.value != SendStatus.SENT) {
                            _sendStatus.value = SendStatus.SENT
                        }
                    }
                    is com.aman.gigi.data.sync.SyncEvent.SendFailed -> {
                        if (_sendStatus.value == SendStatus.SENDING) {
                            android.util.Log.e(TAG, "❌ DrawingViewModel received SendFailed (queueing instead): ${event.reason}")
                            // Q: Offline Queue - transition to QUEUED silently instead of ERROR
                            _sendStatus.value = SendStatus.QUEUED
                        }
                    }
                    else -> {}
                }
            }
        }
    }
    
    // Stroke limit — value comes from server via AppConfig
    val maxStrokes: Int get() = com.aman.gigi.utils.AppConfig.userPlan.maxStrokes
    
    // Available colors
    val availableColors = listOf(
        Color(0xFF8B5CF6), // Purple
        Color(0xFFBB86FC), // Light Purple
        Color(0xFF03DAC6), // Teal
        Color(0xFFFF6B6B), // Red
        Color(0xFF4ECDC4), // Cyan
        Color(0xFFFFA07A), // Orange
        Color(0xFFFFD93D), // Yellow
        Color(0xFF000000)  // Black
    )
    
    /**
     * Start a new stroke
     */
    fun startStroke(x: Float, y: Float) {
        _isDrawing.value = true
        val point = Point(x, y, 1.0f, System.currentTimeMillis())
        val stroke = Stroke(
            points = listOf(point),
            color = if (_isEraser.value) Color.White.toArgb() else _selectedColor.value,
            width = if (_isEraser.value) _strokeWidth.value * 2 else _strokeWidth.value,
            brushType = if (_isEraser.value) BrushType.MARKER else _selectedBrush.value,
            opacity = if (_isEraser.value) 1.0f else _selectedOpacity.value,
            effects = if (_isEraser.value) emptyList() else _selectedEffects.value.toList(),
            animationType = if (_isEraser.value) AnimationType.NONE else _selectedAnimation.value
        )
        _currentStroke.value = stroke
    }
    
    /**
     * Add point to current stroke
     */
    fun addPointToStroke(x: Float, y: Float) {
        val current = _currentStroke.value ?: return
        
        // Calculate pressure based on velocity
        val newPoint = if (current.points.isNotEmpty()) {
            val lastPoint = current.points.last()
            val velocity = StrokeSmoothing.calculateVelocity(lastPoint, Point(x, y, 1f))
            val pressure = StrokeSmoothing.calculatePressure(velocity)
            Point(x, y, pressure, System.currentTimeMillis())
        } else {
            Point(x, y, 1.0f, System.currentTimeMillis())
        }
        
        _currentStroke.value = current.copy(
            points = current.points + newPoint
        )
    }
    
    /**
     * Finish current stroke
     */
    fun finishStroke() {
        _isDrawing.value = false
        val current = _currentStroke.value ?: return

        // Only add stroke if it has enough points
        if (current.points.size >= 2) {
            // Smooth the stroke
            val smoothedPoints = StrokeSmoothing.smoothPoints(current.points)
            val smoothedStroke = current.copy(points = smoothedPoints)

            // Push current state onto undo stack before modifying
            val undoStack = _undoStack.value.toMutableList()
            undoStack.add(_strokes.value)
            _undoStack.value = undoStack
            // Clear redo stack when new stroke is drawn
            _redoStack.value = emptyList()

            // Check stroke limit
            if (_strokes.value.size >= maxStrokes) {
                _strokes.value = _strokes.value.drop(1) + smoothedStroke
            } else {
                _strokes.value = _strokes.value + smoothedStroke
            }
            _canUndo.value = true
            _canRedo.value = false
        }

        _currentStroke.value = null
    }
    
    /**
     * Undo last stroke
     */
    fun undoLastStroke() {
        val undoStack = _undoStack.value
        if (undoStack.isEmpty()) return
        val redoStack = _redoStack.value.toMutableList()
        redoStack.add(_strokes.value)
        _redoStack.value = redoStack
        _strokes.value = undoStack.last()
        _undoStack.value = undoStack.dropLast(1)
        _canUndo.value = _undoStack.value.isNotEmpty()
        _canRedo.value = true
    }

    /**
     * Redo last undone stroke
     */
    fun redoStroke() {
        val redoStack = _redoStack.value
        if (redoStack.isEmpty()) return
        val undoStack = _undoStack.value.toMutableList()
        undoStack.add(_strokes.value)
        _undoStack.value = undoStack
        _strokes.value = redoStack.last()
        _redoStack.value = redoStack.dropLast(1)
        _canUndo.value = true
        _canRedo.value = _redoStack.value.isNotEmpty()
    }
    
    /**
     * Clear all strokes
     */
    fun clearAllStrokes() {
        _undoStack.value = emptyList()
        _redoStack.value = emptyList()
        _canUndo.value = false
        _canRedo.value = false
        _strokes.value = emptyList()
        _currentStroke.value = null
    }
    
    /**
     * Select color
     */
    fun selectColor(color: Color) {
        _selectedColor.value = color.toArgb()
        _isEraser.value = false
    }
    
    /**
     * Toggle eraser
     */
    fun toggleEraser() {
        _isEraser.value = !_isEraser.value
    }
    
    /**
     * Advanced Tool Selection
     */
    fun selectBrush(brush: BrushType) {
        _selectedBrush.value = brush
        _isEraser.value = false
    }
    
    fun toggleEffect(effect: EffectType) {
        val current = _selectedEffects.value.toMutableSet()
        if (current.contains(effect)) current.remove(effect)
        else current.add(effect)
        _selectedEffects.value = current
    }
    
    fun selectAnimation(animation: AnimationType) {
        _selectedAnimation.value = animation
    }
    
    fun setOpacity(opacity: Float) {
        _selectedOpacity.value = opacity
    }
    
    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
    }
    
    fun toggleSettingsPanel(show: Boolean? = null) {
        _showSettingsPanel.value = show ?: !_showSettingsPanel.value
    }

    fun toggleToolPanel(expanded: Boolean? = null) {
        _isToolPanelExpanded.value = expanded ?: !_isToolPanelExpanded.value
    }
    
    /**
     * Send scribble to partner
     */
    fun sendScribble(connectionId: String) {
        viewModelScope.launch {
            if (_strokes.value.isEmpty()) return@launch
            
            val scribble = Scribble(
                scribbleId = UUID.randomUUID().toString(),
                connectionId = connectionId,
                strokes = _strokes.value,
                isSent = true
            )
            
            // Save to repository (will be picked up by sync manager)
            _sendStatus.value = SendStatus.SENDING
            _errorReason.value = null
            scribbleRepository.createScribble(scribble)
            
            // Note: We NO LONGER clear strokes immediately. 
            // We wait for the SENT state in the UI.
        }
    }

    /**
     * Send a GIF to partner
     */
    fun sendGif(connectionId: String, gifUrl: String) {
        // 1. Set state immediately on Main thread
        _sendStatus.value = SendStatus.SENDING
        _errorReason.value = null
        
        viewModelScope.launch {
            try {
                // 2. Yield to Main thread so UI can recompose and show the spinner
                kotlinx.coroutines.yield()
                
                // 3. Move heavy work to Background
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    android.util.Log.i(TAG, "🎁 Processing Giphy GIF: $gifUrl")
                    gifRepository.addRecentGif(gifUrl)

                    val scribble = Scribble(
                        scribbleId = UUID.randomUUID().toString(),
                        connectionId = connectionId,
                        strokes = emptyList(),
                        isSent = true,
                        mediaUrl = gifUrl,
                        mediaType = "image/gif"
                    )
                    
                    scribbleRepository.createScribble(scribble)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error sending Giphy GIF", e)
                _errorReason.value = "Failed to send GIF"
                _sendStatus.value = SendStatus.ERROR
            }
        }
    }

    /**
     * Send a local GIF from Uri
     */
    fun sendLocalGif(connectionId: String, uri: android.net.Uri) {
        // 1. Trigger spinner instantly
        _sendStatus.value = SendStatus.SENDING
        _errorReason.value = null
        
        viewModelScope.launch {
            try {
                // 2. Give Main thread a tiny breathing room to render the overlay
                kotlinx.coroutines.yield()
                
                // 3. Perform heavy IO and Encoding
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    android.util.Log.d(TAG, "Reading local GIF: $uri")
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) return@withContext "FAILED_OPEN"
                    
                    val bytes = inputStream.readBytes()
                    inputStream.close()

                    // Lowered limit for better stability (Base64 + JSON + Socket Buffers)
                    // 1.5MB Raw -> 2MB Base64. Safe for most socket servers.
                    if (bytes.size > 1.5 * 1024 * 1024) {
                        android.util.Log.e(TAG, "Local GIF too large: ${bytes.size} bytes")
                        return@withContext "TOO_LARGE"
                    }

                    // Use Default dispatcher for the heavy CPU task of Base64 encoding
                    val base64 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    }
                    
                    val scribble = Scribble(
                        scribbleId = UUID.randomUUID().toString(),
                        connectionId = connectionId,
                        strokes = emptyList(),
                        isSent = true,
                        mediaType = "image/gif",
                        mediaBase64 = base64
                    )
                    
                    scribbleRepository.createScribble(scribble)
                    "SUCCESS"
                }

                if (result == "TOO_LARGE") {
                    _errorReason.value = "GIF too large (max 1.5MB for mobile)"
                    _sendStatus.value = SendStatus.ERROR
                } else if (result == "FAILED_OPEN") {
                    _errorReason.value = "Failed to access file"
                    _sendStatus.value = SendStatus.ERROR
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "OOM or IO Error during GIF processing", e)
                _errorReason.value = "Memory error - try a smaller GIF"
                _sendStatus.value = SendStatus.ERROR
            }
        }
    }
    
    /**
     * Get stroke count
     */
    fun getStrokeCount(): Int = _strokes.value.size
    
    /**
     * Check if at stroke limit
     */
    fun isAtStrokeLimit(): Boolean = _strokes.value.size >= maxStrokes

    /**
     * Reset send status to IDLE
     */
    fun resetSendStatus() {
        _sendStatus.value = SendStatus.IDLE
        _errorReason.value = null
    }

    /**
     * Notify partner that drawing started/stopped
     */
    fun notifyDrawingState(connectionId: String, drawing: Boolean) {
        viewModelScope.launch {
            val command = if (drawing) "PARTNER_DRAWING_START" else "PARTNER_DRAWING_STOP"
            syncManager.sendRemoteCommand(connectionId, command, null)
        }
    }
}
