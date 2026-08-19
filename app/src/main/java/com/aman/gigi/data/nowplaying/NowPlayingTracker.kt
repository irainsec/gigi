package com.aman.gigi.data.nowplaying

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What someone is listening to right now. Works with ANY player on the phone
 * (Spotify, YouTube Music, Gigi's own library…) because it is read from the
 * system media session, not from a music service's API.
 */
data class NowPlaying(
    val title: String,
    val artist: String,
    val app: String = "",          // human label of the source player
    val isPlaying: Boolean = true,
    val at: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    /**
     * Album art as a content:// URI when the player publishes one. Deliberately a URI
     * and not a Bitmap: this object is also sent to the user's people over the socket,
     * and artwork is a local handle that means nothing on someone else's phone.
     */
    val artworkUri: String? = null
) {
    val label: String get() = if (artist.isBlank()) title else "$title · $artist"
}

/**
 * Holds my current track plus the latest track for each connection. Purely
 * in-memory / live — nothing is persisted (it's a "right now" signal).
 */
@Singleton
class NowPlayingTracker @Inject constructor() {

    private val _mine = MutableStateFlow<NowPlaying?>(null)
    val mine: StateFlow<NowPlaying?> = _mine

    // connectionId (lowercase) -> what that person is playing
    private val _others = MutableStateFlow<Map<String, NowPlaying>>(emptyMap())
    val others: StateFlow<Map<String, NowPlaying>> = _others

    /** Called by the notification listener when the phone's media session changes. */
    fun updateMine(np: NowPlaying?) {
        val cur = _mine.value
        // ignore no-op churn so we don't spam the socket
        if (cur?.title == np?.title && cur?.artist == np?.artist && cur?.isPlaying == np?.isPlaying) return
        _mine.value = np
    }

    fun updateOther(connectionId: String, np: NowPlaying?) {
        val key = connectionId.lowercase()
        _others.value = if (np == null) _others.value - key else _others.value + (key to np)
    }

    fun clearOther(connectionId: String) = updateOther(connectionId, null)
}
