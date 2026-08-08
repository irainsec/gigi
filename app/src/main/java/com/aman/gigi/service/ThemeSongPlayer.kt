package com.aman.gigi.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.aman.gigi.model.MemberIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeSongPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentUrl: String? = null
    private var autoPlaybackSuppressed: Boolean = false

    fun playIfNeeded(identity: MemberIdentity?) {
        if (autoPlaybackSuppressed) return
        val songUrl = identity?.themeSongUrl?.trim().orEmpty()
        if (songUrl.isBlank()) return
        if (songUrl == currentUrl && mediaPlayer?.isPlaying == true) return
        play(songUrl, shouldLoop = true)
    }

    fun play(songUrl: String, shouldLoop: Boolean = true) {
        stop()
        currentUrl = songUrl
        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(songUrl)
                setOnPreparedListener { player ->
                    player.isLooping = shouldLoop
                    player.start()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("ThemeSongPlayer", "Theme song playback failed: what=$what extra=$extra")
                    stop()
                    true
                }
                prepareAsync()
            }
        }.onFailure { error ->
            Log.e("ThemeSongPlayer", "Unable to start theme song", error)
            stop()
        }
    }

    fun playTransient(songUrl: String) {
        play(songUrl, shouldLoop = false)
    }

    fun setAutoPlaybackSuppressed(suppressed: Boolean) {
        autoPlaybackSuppressed = suppressed
        if (suppressed) {
            stop()
        }
    }

    fun stop() {
        mediaPlayer?.runCatching {
            stop()
        }
        mediaPlayer?.release()
        mediaPlayer = null
        currentUrl = null
    }
}
