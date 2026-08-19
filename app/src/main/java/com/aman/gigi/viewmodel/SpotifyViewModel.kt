package com.aman.gigi.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.gigi.data.spotify.SpotifyApi
import com.aman.gigi.data.spotify.SpotifyAuth
import com.aman.gigi.data.spotify.SpotifyError
import com.aman.gigi.data.spotify.SpotifyException
import com.aman.gigi.data.spotify.SpotifyPlaylist
import com.aman.gigi.data.spotify.SpotifyTrack
import com.aman.gigi.utils.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpotifyUiState(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val loading: Boolean = false,
    val playlists: List<SpotifyPlaylist> = emptyList(),
    /** Non-null while a playlist is open; null means we're showing the playlist list. */
    val openPlaylist: SpotifyPlaylist? = null,
    val tracks: List<SpotifyTrack> = emptyList(),
    val message: String? = null,
    /** True when Spotify refused because the developer account isn't Premium. */
    val premiumRequired: Boolean = false
)

@HiltViewModel
class SpotifyViewModel @Inject constructor(
    private val auth: SpotifyAuth,
    private val api: SpotifyApi
) : ViewModel() {

    private val _state = MutableStateFlow(SpotifyUiState())
    val state: StateFlow<SpotifyUiState> = _state.asStateFlow()

    init { refreshConnection() }

    /**
     * Re-reads whether we're configured and connected.
     *
     * Called on open and after returning from the browser — the OAuth callback lands in
     * MainActivity, not here, so this is how the screen learns the handshake finished.
     */
    fun refreshConnection() {
        _state.update {
            it.copy(configured = auth.isConfigured, connected = auth.isConnected)
        }
        if (auth.isConfigured && auth.isConnected && _state.value.playlists.isEmpty()) {
            loadPlaylists()
        }
    }

    fun connect(context: Context) = auth.beginLogin(context)

    fun disconnect() {
        auth.disconnect()
        _state.value = SpotifyUiState(configured = auth.isConfigured, connected = false)
    }

    fun loadPlaylists() {
        if (!auth.isConnected) return
        _state.update { it.copy(loading = true, message = null, premiumRequired = false) }
        viewModelScope.launch {
            api.playlists()
                .onSuccess { list ->
                    _state.update { it.copy(loading = false, playlists = list, connected = true) }
                }
                .onFailure { e -> fail(e) }
        }
    }

    fun openPlaylist(playlist: SpotifyPlaylist) {
        _state.update { it.copy(openPlaylist = playlist, tracks = emptyList(), loading = true) }
        viewModelScope.launch {
            api.playlistTracks(playlist.id)
                .onSuccess { list -> _state.update { it.copy(loading = false, tracks = list) } }
                .onFailure { e -> fail(e) }
        }
    }

    fun closePlaylist() {
        _state.update { it.copy(openPlaylist = null, tracks = emptyList(), message = null) }
    }

    /**
     * Plays a track, preferring the API so the user stays inside Gigi.
     *
     * Falls back to a deep link when there is no awake Spotify device — which is the
     * common case if Spotify hasn't been opened since boot. The fallback foregrounds
     * Spotify briefly, but it always works, including without Premium.
     */
    fun play(context: Context, track: SpotifyTrack) {
        viewModelScope.launch {
            api.play(track.uri)
                .onSuccess { _state.update { it.copy(message = null) } }
                .onFailure { e ->
                    val error = (e as? SpotifyException)?.error
                    if (error is SpotifyError.NoActiveDevice || error is SpotifyError.PremiumRequired) {
                        val opened = api.openInSpotify(context, track.uri)
                        _state.update {
                            it.copy(
                                message = if (opened) null else "Couldn't reach Spotify",
                                premiumRequired = false
                            )
                        }
                    } else {
                        fail(e)
                    }
                }
        }
    }

    private fun fail(e: Throwable) {
        val error = (e as? SpotifyException)?.error
        _state.update {
            when (error) {
                is SpotifyError.PremiumRequired -> it.copy(
                    loading = false, premiumRequired = true,
                    message = "Spotify only allows this for Premium accounts."
                )
                is SpotifyError.NotConnected -> it.copy(
                    loading = false, connected = false,
                    message = "Your Spotify session ended — connect again."
                )
                is SpotifyError.Http -> it.copy(
                    loading = false, message = "Spotify error ${error.code}"
                )
                else -> it.copy(loading = false, message = "Couldn't reach Spotify")
            }
        }
    }

    /** Blank client ID means no Spotify app is registered — hide the surface entirely. */
    val hasClientId: Boolean get() = AppConfig.settings.spotifyClientId.isNotBlank()
}
