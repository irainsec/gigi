package com.aman.gigi.data.nowplaying

import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The remote control for whatever is playing on the phone.
 *
 * [NowPlayingListenerService] already resolves a [MediaController] for the active
 * session in order to read the title and artist — it just threw the handle away
 * afterwards. A MediaController is two-way: the same object that reports what
 * Spotify is playing can also tell it to pause. That is the mechanism Android Auto,
 * Wear OS and Bluetooth headsets use, and players publish a session precisely so
 * remote controllers can drive them. No SDK and no account is involved.
 *
 * The controller is kept here rather than in the service so the UI can survive the
 * service reconnecting, and so nothing outside this class ever touches the handle.
 */
@Singleton
class MediaControlHub @Inject constructor() {

    /** What the active player will actually allow right now. */
    data class Capabilities(
        val canPlayPause: Boolean = false,
        val canSkipNext: Boolean = false,
        val canSkipPrevious: Boolean = false,
        val canSeek: Boolean = false,
        /**
         * Whether the player will start a track we name, rather than only stepping
         * through the queue it already has. This is the capability that decides
         * whether Gigi ever needs the Spotify SDK: with it, "play this song" works
         * over the plain system session; without it, that feature needs App Remote.
         */
        val canPlayFromSearch: Boolean = false,
        val canPlayFromUri: Boolean = false
    ) {
        val any: Boolean get() = canPlayPause || canSkipNext || canSkipPrevious || canSeek
        val canStartSpecificTrack: Boolean get() = canPlayFromSearch || canPlayFromUri
    }

    private val _capabilities = MutableStateFlow(Capabilities())
    val capabilities: StateFlow<Capabilities> = _capabilities

    /** Package of the app we're controlling, so the UI can say "in Spotify". */
    private val _sourcePackage = MutableStateFlow<String?>(null)
    val sourcePackage: StateFlow<String?> = _sourcePackage

    @Volatile private var controller: MediaController? = null

    /**
     * Hands the hub the currently active session, or null when nothing is playing.
     *
     * Free Spotify refuses skips once the hourly limit is hit, and podcast players
     * often disallow "previous" — so capabilities are read from the session's own
     * declared actions rather than assumed, and the UI greys out what won't work.
     */
    fun attach(newController: MediaController?) {
        controller = newController
        if (newController == null) {
            _capabilities.value = Capabilities()
            _sourcePackage.value = null
            return
        }
        _sourcePackage.value = newController.packageName
        _capabilities.value = capabilitiesOf(newController.playbackState)
        logCapabilities()
    }

    /** Called when the active session reports new state without changing identity. */
    fun onStateChanged(state: PlaybackState?) {
        if (controller != null) _capabilities.value = capabilitiesOf(state)
    }

    private fun capabilitiesOf(state: PlaybackState?): Capabilities {
        val actions = state?.actions ?: 0L
        fun has(flag: Long) = actions and flag != 0L
        return Capabilities(
            // PLAY_PAUSE alone is common, but plenty of players advertise only the
            // individual PLAY and PAUSE actions, so accept any of the three.
            canPlayPause = has(PlaybackState.ACTION_PLAY_PAUSE) ||
                has(PlaybackState.ACTION_PLAY) || has(PlaybackState.ACTION_PAUSE),
            canSkipNext = has(PlaybackState.ACTION_SKIP_TO_NEXT),
            canSkipPrevious = has(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
            canSeek = has(PlaybackState.ACTION_SEEK_TO),
            canPlayFromSearch = has(PlaybackState.ACTION_PLAY_FROM_SEARCH),
            canPlayFromUri = has(PlaybackState.ACTION_PLAY_FROM_URI)
        )
    }

    // ── commands ─────────────────────────────────────────────────────────────
    // Every one is a no-op when there is no session, so the UI never has to guard.

    fun playPause() = withControls { c, playing ->
        if (playing) c.pause() else c.play()
    }

    fun next() = withControls { c, _ -> c.skipToNext() }

    fun previous() = withControls { c, _ -> c.skipToPrevious() }

    fun seekTo(positionMs: Long) = withControls { c, _ -> c.seekTo(positionMs.coerceAtLeast(0)) }

    /**
     * Asks the active player to start a track by name — the same route Google
     * Assistant uses for "play Blinding Lights on Spotify".
     *
     * Fuzzy by nature: the player picks what it thinks you meant, and it may pick
     * wrong or pick nothing. Check [Capabilities.canPlayFromSearch] first.
     */
    fun playFromSearch(query: String) = withControls { c, _ ->
        c.playFromSearch(query, null)
    }

    /** Exact, when the player accepts URIs (e.g. spotify:track:...). */
    fun playFromUri(uri: android.net.Uri) = withControls { c, _ -> c.playFromUri(uri, null) }

    /**
     * One line per session describing what the active player actually permits.
     *
     * This exists to answer a specific question with evidence instead of assumption:
     * does Spotify's Android app expose PLAY_FROM_SEARCH to arbitrary controllers?
     * Filter logcat for "MediaControlHub" while Spotify plays.
     */
    fun logCapabilities() {
        val c = _capabilities.value
        Log.i(
            TAG,
            "session=${_sourcePackage.value} playPause=${c.canPlayPause} next=${c.canSkipNext} " +
                "prev=${c.canSkipPrevious} seek=${c.canSeek} " +
                "playFromSearch=${c.canPlayFromSearch} playFromUri=${c.canPlayFromUri} " +
                "=> canStartSpecificTrack=${c.canStartSpecificTrack}"
        )
    }

    private inline fun withControls(block: (MediaController.TransportControls, Boolean) -> Unit) {
        val c = controller ?: return
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        // The owning app can die between our reading the session and sending a
        // command; that surfaces as a DeadObjectException we have no way to prevent.
        runCatching { block(c.transportControls, playing) }
            .onFailure { Log.w(TAG, "transport command failed: ${it.message}") }
    }

    /** Live position, since PlaybackState only gives a timestamped anchor. */
    fun currentPositionMs(): Long {
        val state = controller?.playbackState ?: return 0L
        if (state.state != PlaybackState.STATE_PLAYING) return state.position
        val drift = System.currentTimeMillis() - state.lastPositionUpdateTime
        return state.position + (drift * state.playbackSpeed).toLong()
    }

    private companion object { const val TAG = "MediaControlHub" }
}
