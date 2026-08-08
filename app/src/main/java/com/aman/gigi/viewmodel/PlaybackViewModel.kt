package com.aman.gigi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.gigi.model.Scribble
import com.aman.gigi.model.ScribbleStatus
import com.aman.gigi.repository.ScribbleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for scribble playback
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val scribbleRepository: ScribbleRepository,
    private val syncManager: com.aman.gigi.data.sync.ScribbleSyncManager
) : ViewModel() {
    
    // Current scribble being played
    private val _currentScribble = MutableStateFlow<Scribble?>(null)
    val currentScribble: StateFlow<Scribble?> = _currentScribble.asStateFlow()
    
    // Playback queue (summaries to prevent CursorWindowAllocationException)
    private val _playbackQueue = MutableStateFlow<List<com.aman.gigi.model.ScribbleSummary>>(emptyList())
    val playbackQueue: StateFlow<List<com.aman.gigi.model.ScribbleSummary>> = _playbackQueue.asStateFlow()
    
    // Is playing
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    /**
     * Load received scribbles
     */
    fun loadReceivedScribbles() {
        viewModelScope.launch {
            val received = scribbleRepository.getReceivedScribbleSummaries(limit = 10)
            _playbackQueue.value = received
            
            // Auto-play first scribble if available
            if (received.isNotEmpty() && _currentScribble.value == null) {
                playNextScribble()
            }
        }
    }
    
    /**
     * Play next scribble from queue
     */
    fun playNextScribble() {
        viewModelScope.launch {
            val queue = _playbackQueue.value
            
            if (queue.isNotEmpty()) {
                val summary = queue.first()
                // Fetch full scribble by ID immediately before playing
                val fullScribble = scribbleRepository.getScribbleById(summary.scribbleId)
                
                if (fullScribble != null) {
                    _currentScribble.value = fullScribble
                    _isPlaying.value = true
                    
                    // Mark as displaying
                    scribbleRepository.updateScribbleStatus(
                        fullScribble.scribbleId,
                        ScribbleStatus.DISPLAYING
                    )
                } else {
                    android.util.Log.e("PlaybackViewModel", "Failed to fetch full scribble for ID: ${summary.scribbleId}")
                    // Remove erroneous summary and try next
                    _playbackQueue.value = queue.drop(1)
                    playNextScribble()
                }
            } else {
                _currentScribble.value = null
                _isPlaying.value = false
            }
        }
    }
    
    /**
     * Complete current scribble playback
     */
    fun completeCurrentScribble() {
        viewModelScope.launch {
            val current = _currentScribble.value ?: return@launch
            
            // Mark as displayed
            scribbleRepository.markAsDisplayed(current.scribbleId)
            syncManager.sendActionReceipt(
                connectionId = current.connectionId,
                actionId = current.scribbleId,
                receiptType = com.aman.gigi.data.sync.SyncProtocol.ACTION_ACTION_DISPLAYED
            )
            
            // Cancel notification
            syncManager.cancelNotification(current.scribbleId)
            
            // Remove from queue
            _playbackQueue.value = _playbackQueue.value.filter { 
                it.scribbleId != current.scribbleId 
            }
            
            _currentScribble.value = null
            _isPlaying.value = false
            
            // Auto-play next if available
            if (_playbackQueue.value.isNotEmpty()) {
                playNextScribble()
            }
        }
    }
    
    /**
     * Skip current scribble
     */
    fun skipCurrentScribble() {
        completeCurrentScribble()
    }
    
    /**
     * Clear playback queue
     */
    fun clearQueue() {
        viewModelScope.launch {
            _playbackQueue.value.forEach { scribble ->
                scribbleRepository.markAsDisplayed(scribble.scribbleId)
                syncManager.sendActionReceipt(
                    connectionId = scribble.connectionId,
                    actionId = scribble.scribbleId,
                    receiptType = com.aman.gigi.data.sync.SyncProtocol.ACTION_ACTION_DISPLAYED
                )
                syncManager.cancelNotification(scribble.scribbleId)
            }
            
            _playbackQueue.value = emptyList()
            _currentScribble.value = null
            _isPlaying.value = false
        }
    }
    
    /**
     * Get queue size
     */
    fun getQueueSize(): Int = _playbackQueue.value.size
}
