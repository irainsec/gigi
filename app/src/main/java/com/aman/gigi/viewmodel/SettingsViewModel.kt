package com.aman.gigi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.gigi.model.Connection
import com.aman.gigi.repository.ConnectionRepository
import com.aman.gigi.repository.ScribbleRepository
import com.aman.gigi.data.client.ConnectionBootstrapManager
import com.aman.gigi.model.MemberIdentity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for screensaver settings
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val scribbleRepository: ScribbleRepository,
    private val bootstrapManager: ConnectionBootstrapManager
) : ViewModel() {
    
    val memberIdentity = bootstrapManager.memberIdentity
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Switches which identity partners see (EMOJI or TWIGI); both are always kept. */
    fun setAvatarMode(mode: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            bootstrapManager.saveTwigiOnServer(mode = mode)
            // Note: ScreensaverViewModel usually broadcasts this live to partners.
            // But saving it to server updates it globally anyway.
        }
    }

    /** Saves the Twigi config to the server and switches mode to TWIGI. */
    fun saveTwigi(configJson: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            bootstrapManager.saveTwigiOnServer(mode = "TWIGI", configJson = configJson)
        }
    }

    /**
     * Saves a VRM avatar exported from CharacterStudio.
     *
     * @param vrmBlobUrl    The blob: or https: URL of the exported .vrm file.
     *                      Stored as `twigiConfigJson` so the server knows which file to fetch.
     * @param thumbnailB64  Optional Base64-encoded PNG screenshot used as the 2D preview
     *                      card image on the partner's screensaver (stored as renderBase64).
     */
    fun saveTwigiVrm(vrmBlobUrl: String, thumbnailB64: String?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Wrap the VRM URL in a minimal JSON config so the server field stays valid JSON
            val configJson = org.json.JSONObject().apply {
                put("vrmUrl", vrmBlobUrl)
                put("source", "CharacterStudio")
            }.toString()
            bootstrapManager.saveTwigiOnServer(
                mode         = "TWIGI",
                configJson   = configJson,
                renderBase64 = thumbnailB64
            )
        }
    }

    /** Update user profile on server. */
    fun updateProfile(displayName: String, gender: String, emoji: String?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            bootstrapManager.updateProfile(
                displayName = displayName,
                gender = gender,
                emoji = emoji
            )
            
            // If they picked an emoji, we also sync it to preferences
            if (emoji != null) {
                bootstrapManager.updateProfileEmoji(emoji)
            }
        }
    }

    /** Set the user's own profile animated emoji. */
    fun setProfileEmoji(context: android.content.Context, emojiUrl: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Persist locally for immediate feedback in Galaxy View
            context.getSharedPreferences("galaxy_orbits", android.content.Context.MODE_PRIVATE)
                .edit().putString("emoji_self", emojiUrl).apply()
            
            // Also update the server and internal state
            bootstrapManager.updateProfileEmoji(emojiUrl)
        }
    }


    // Active connection
    private val _activeConnection = MutableStateFlow<Connection?>(null)
    val activeConnection: StateFlow<Connection?> = _activeConnection.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadActiveConnection()
    }
    
    /**
     * Load active connection
     */
    private fun loadActiveConnection() {
        viewModelScope.launch {
            connectionRepository.getActiveConnection().collect { connection ->
                _activeConnection.value = connection
            }
        }
    }
    
    /**
     * Disconnect and cleanup
     */
    fun disconnect() {
        viewModelScope.launch {
            _isLoading.value = true
            
            val connection = _activeConnection.value
            if (connection != null) {
                // Delete all scribbles for this connection
                scribbleRepository.deleteScribblesByConnection(connection.connectionId)
                
                // Delete connection
                connectionRepository.deleteConnection(connection.connectionId)
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Clear scribble history only
     */
    fun clearScribbleHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            
            val connection = _activeConnection.value
            if (connection != null) {
                scribbleRepository.deleteScribblesByConnection(connection.connectionId)
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Get connection duration in readable format
     */
    fun getConnectionDuration(connectedAt: Long): String {
        val durationMs = System.currentTimeMillis() - connectedAt
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs / (1000 * 60)) % 60
        
        return when {
            hours > 24 -> "${hours / 24} days"
            hours > 0 -> "$hours hours"
            minutes > 0 -> "$minutes minutes"
            else -> "Just now"
        }
    }
}
