package com.aman.gigi.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.aman.gigi.data.nowplaying.NowPlaying
import com.aman.gigi.data.nowplaying.NowPlayingTracker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Reads the phone's active media session so we can share "what I'm listening to"
 * with the user's people — regardless of which app is playing (Spotify, YouTube
 * Music, Gigi's own library). Needs the user to grant notification access once.
 *
 * We only ever read title / artist / player name and never touch audio itself.
 */
@AndroidEntryPoint
class NowPlayingListenerService : NotificationListenerService() {

    @Inject lateinit var tracker: NowPlayingTracker

    private var sessionManager: MediaSessionManager? = null
    private val component by lazy { ComponentName(this, NowPlayingListenerService::class.java) }

    private val sessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener { refresh(it) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessionManager = getSystemService(MediaSessionManager::class.java)
        runCatching {
            sessionManager?.addOnActiveSessionsChangedListener(sessionsChanged, component)
            refresh(sessionManager?.getActiveSessions(component))
        }.onFailure { Log.w(TAG, "listener connect failed: ${it.message}") }
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        tracker.updateMine(null)
        super.onListenerDisconnected()
    }

    // Media notifications changing is the cheapest signal that playback moved on.
    override fun onNotificationPosted(sbn: StatusBarNotification?) = poke()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = poke()

    private fun poke() {
        runCatching { refresh(sessionManager?.getActiveSessions(component)) }
    }

    private fun refresh(controllers: List<MediaController>?) {
        val active = controllers?.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers?.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PAUSED
        }
        if (active == null) { tracker.updateMine(null); return }

        val md = active.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().trim()
        if (title.isBlank()) { tracker.updateMine(null); return }
        val artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty().trim()

        tracker.updateMine(
            NowPlaying(
                title = title.take(80),
                artist = artist.take(60),
                app = appLabel(active.packageName),
                isPlaying = active.playbackState?.state == PlaybackState.STATE_PLAYING
            )
        )
    }

    private fun appLabel(pkg: String?): String = when {
        pkg == null -> ""
        pkg.contains("spotify") -> "Spotify"
        pkg.contains("google.android.apps.youtube.music") -> "YT Music"
        pkg.contains("youtube") -> "YouTube"
        pkg.contains("apple") -> "Apple Music"
        pkg.contains("com.aman.gigi") -> "Gigi"
        else -> runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault("")
    }

    companion object { private const val TAG = "NowPlaying" }
}
