package com.aman.gigi.service

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.aman.gigi.model.LocalSong
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

enum class PlaybackSource {
    APP,
    LOCKSCREEN,
    NOTIFICATION,
    MEDIA_SESSION,
    SYSTEM
}

enum class PlaybackRepeatMode { NONE, ONE, ALL }

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "PlaybackManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("gigi_music_prefs", Context.MODE_PRIVATE)

    private var mediaPlayer: MediaPlayer? = null
    private var fadeOutPlayer: MediaPlayer? = null   // used for crossfade
    private var fadeOutJob: Job? = null
    private var preparedSongId: Long? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    // ──────────────────────────────── Audio Focus ────────────────────────────────

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    }

    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                if (_isPlaying.value) togglePlayback(PlaybackSource.SYSTEM)
                abandonAudioFocus()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_isPlaying.value) togglePlayback(PlaybackSource.SYSTEM)
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (!_isPlaying.value && _lastPauseSource.value == PlaybackSource.SYSTEM) {
                    togglePlayback(PlaybackSource.SYSTEM)
                }
            }
        }
    }

    private var audioFocusRequest: android.media.AudioFocusRequest? = null

    private fun requestAudioFocus(): Boolean {
        val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    // ──────────────────────────────── State Flows ─────────────────────────────────

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _lastPauseSource = MutableStateFlow(PlaybackSource.SYSTEM)
    val lastPauseSource: StateFlow<PlaybackSource> = _lastPauseSource.asStateFlow()

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    private val _currentSong = MutableStateFlow<LocalSong?>(null)
    val currentSong: StateFlow<LocalSong?> = _currentSong.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<LocalSong>>(emptyList())
    val playbackQueue: StateFlow<List<LocalSong>> = _playbackQueue.asStateFlow()

    private val _activeAlbumId = MutableStateFlow<Long?>(null)
    val activeAlbumId: StateFlow<Long?> = _activeAlbumId.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    // Repeat & Shuffle
    private val _repeatMode = MutableStateFlow(
        PlaybackRepeatMode.entries.getOrElse(prefs.getInt("repeat_mode", 0)) { PlaybackRepeatMode.NONE }
    )
    val repeatMode: StateFlow<PlaybackRepeatMode> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(prefs.getBoolean("shuffle_enabled", false))
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    var onPlaybackStateChangedListener: (() -> Unit)? = null

    // ──────────────────────────────── Queue Control ─────────────────────────────────

    fun setQueue(queue: List<LocalSong>, activeAlbumId: Long?) {
        _playbackQueue.value = queue
        _activeAlbumId.value = activeAlbumId
        notifyStateChanged()
    }

    fun addToQueue(song: LocalSong) {
        val current = _playbackQueue.value.toMutableList()
        if (current.none { it.id == song.id }) {
            current.add(song)
            _playbackQueue.value = current
            notifyStateChanged()
        }
    }

    fun toggleRepeat() {
        val next = when (_repeatMode.value) {
            PlaybackRepeatMode.NONE -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL  -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE  -> PlaybackRepeatMode.NONE
        }
        _repeatMode.value = next
        prefs.edit().putInt("repeat_mode", next.ordinal).apply()
        notifyStateChanged()
    }

    fun toggleShuffle() {
        val next = !_shuffleEnabled.value
        _shuffleEnabled.value = next
        prefs.edit().putBoolean("shuffle_enabled", next).apply()
        notifyStateChanged()
    }

    // ──────────────────────────────── Playback ─────────────────────────────────

    fun playSong(song: LocalSong, albumContextId: Long? = null) {
        val resolvedAlbumId = albumContextId ?: _activeAlbumId.value

        val activePlayer = mediaPlayer
        if (preparedSongId == song.id && activePlayer != null) {
            if (!requestAudioFocus()) return
            runCatching {
                activePlayer.seekTo(0)
                if (!activePlayer.isPlaying) {
                    activePlayer.start()
                }
            }
            _currentSong.value = song
            _activeAlbumId.value = resolvedAlbumId
            _isPlaying.value = true
            _isPreparing.value = false
            _durationMs.value = max(_durationMs.value, song.durationMs)
            ensureProgressTicker()
            saveState(song.id, resolvedAlbumId)
            notifyStateChanged()
            return
        }

        // Start crossfade if a song is currently playing
        startCrossfade()

        if (!requestAudioFocus()) return

        _currentSong.value = song
        _activeAlbumId.value = resolvedAlbumId
        _isPreparing.value = true
        _isPlaying.value = false
        _progressMs.value = 0L
        _durationMs.value = song.durationMs
        notifyStateChanged()

        val player = MediaPlayer()
        mediaPlayer = player

        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(context, song.contentUri)
            player.setOnPreparedListener { prepared ->
                preparedSongId = song.id
                prepared.start()
                _isPreparing.value = false
                _isPlaying.value = true
                _durationMs.value = max(song.durationMs, prepared.duration.toLong())
                ensureProgressTicker()
                saveState(song.id, resolvedAlbumId)
                notifyStateChanged()
            }
            player.setOnCompletionListener {
                handleSongCompletion()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(tag, "Music playback failed: what=$what extra=$extra")
                releasePlayer()
                _isPreparing.value = false
                _isPlaying.value = false
                _progressMs.value = 0L
                notifyStateChanged()
                true
            }
            player.prepareAsync()
        }.onFailure { error ->
            Log.e(tag, "Unable to start track ${song.title}", error)
            releasePlayer()
            _isPreparing.value = false
            _isPlaying.value = false
            _progressMs.value = 0L
            notifyStateChanged()
        }
    }

    // Crossfade: move current player to fadeOutPlayer and fade its volume to 0 over 3 seconds
    private fun startCrossfade() {
        val current = mediaPlayer ?: return
        if (!current.isPlaying) {
            releasePlayer()
            return
        }
        // Cancel any previous fade
        fadeOutJob?.cancel()
        fadeOutPlayer?.runCatching { stop(); release() }
        fadeOutPlayer = current
        mediaPlayer = null
        preparedSongId = null
        progressJob?.cancel()
        progressJob = null
        fadeOutJob = scope.launch {
            val steps = 30
            val stepMs = 3000L / steps
            for (i in steps downTo 0) {
                val vol = i.toFloat() / steps
                runCatching { fadeOutPlayer?.setVolume(vol, vol) }
                delay(stepMs)
            }
            fadeOutPlayer?.runCatching { stop(); release() }
            fadeOutPlayer = null
        }
    }

    fun togglePlayback(source: PlaybackSource = PlaybackSource.APP) {
        val song = _currentSong.value ?: _playbackQueue.value.firstOrNull()
        val player = mediaPlayer

        when {
            song == null -> return
            player == null || preparedSongId == null -> playSong(song)
            player.isPlaying -> {
                player.pause()
                _isPlaying.value = false
                _lastPauseSource.value = source
                notifyStateChanged()
            }
            else -> {
                if (requestAudioFocus()) {
                    player.start()
                    _isPlaying.value = true
                    ensureProgressTicker()
                    notifyStateChanged()
                }
            }
        }
    }

    fun playNext() {
        val songs = _playbackQueue.value
        if (songs.isEmpty()) return
        val currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
        if (_shuffleEnabled.value) {
            val nextIndex = (0 until songs.size).filter { it != currentIndex }.randomOrNull() ?: 0
            playSong(songs[nextIndex], _activeAlbumId.value)
        } else {
            when {
                currentIndex == -1 -> playSong(songs.first(), _activeAlbumId.value)
                currentIndex < songs.lastIndex -> playSong(songs[currentIndex + 1], _activeAlbumId.value)
                else -> playSong(songs.first(), _activeAlbumId.value)
            }
        }
    }

    fun playPrevious() {
        val songs = _playbackQueue.value
        if (songs.isEmpty()) return

        val player = mediaPlayer
        if (player != null && preparedSongId != null && player.currentPosition > 3_000) {
            player.seekTo(0)
            _progressMs.value = 0L
            return
        }

        val currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
        when {
            currentIndex > 0 -> playSong(songs[currentIndex - 1], _activeAlbumId.value)
            currentIndex == 0 -> playSong(songs.last(), _activeAlbumId.value)
            else -> playSong(songs.first(), _activeAlbumId.value)
        }
    }

    fun seekTo(positionMs: Long) {
        if (preparedSongId == null) return
        val safePosition = positionMs.coerceAtLeast(0L).coerceAtMost(_durationMs.value)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mediaPlayer?.seekTo(safePosition, android.media.MediaPlayer.SEEK_CLOSEST)
        } else {
            mediaPlayer?.seekTo(safePosition.toInt())
        }
        _progressMs.value = safePosition
        notifyStateChanged()
    }

    fun stop() {
        releasePlayer()
        _isPlaying.value = false
        _isPreparing.value = false
        notifyStateChanged()
    }

    private fun handleSongCompletion() {
        when (_repeatMode.value) {
            PlaybackRepeatMode.ONE -> {
                // Replay the same song from the start
                _currentSong.value?.let { playSong(it, _activeAlbumId.value) }
            }
            PlaybackRepeatMode.ALL -> {
                // Go to next; playNext already wraps to start
                playNext()
            }
            PlaybackRepeatMode.NONE -> {
                val songs = _playbackQueue.value
                val currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
                if (currentIndex < songs.lastIndex || _shuffleEnabled.value) {
                    playNext()
                } else {
                    // End of queue — stop
                    _isPlaying.value = false
                    _progressMs.value = 0L
                    notifyStateChanged()
                }
            }
        }
    }

    // ──────────────────────────────── Progress Ticker ─────────────────────────────────

    private fun ensureProgressTicker() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                syncProgress()
                delay(250)
            }
        }
    }

    private fun syncProgress() {
        val player = mediaPlayer ?: return
        if (preparedSongId == null) return
        val currentPosition = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
        val duration = runCatching { player.duration.toLong() }.getOrDefault(_durationMs.value)
        val playing = runCatching { player.isPlaying }.getOrDefault(false)

        _progressMs.value = currentPosition.coerceAtLeast(0L).coerceAtMost(duration.coerceAtLeast(1L))
        _durationMs.value = max(duration, _durationMs.value)
        if (!_isPreparing.value) {
            _isPlaying.value = playing
        }
    }

    private fun releasePlayer() {
        abandonAudioFocus()
        mediaPlayer?.apply {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            runCatching { reset() }
            release()
        }
        mediaPlayer = null
        preparedSongId = null
        progressJob?.cancel()
        progressJob = null
    }

    private fun saveState(songId: Long, albumId: Long?) {
        prefs.edit().apply {
            putLong("last_song_id", songId)
            putLong("last_progress_ms", _progressMs.value)
            if (albumId != null) {
                putLong("last_album_id", albumId)
            } else {
                remove("last_album_id")
            }
            apply()
        }
    }

    private fun notifyStateChanged() {
        onPlaybackStateChangedListener?.invoke()
    }

    fun restoreState(songs: List<LocalSong>, albumId: Long?) {
        val savedSongId = prefs.getLong("last_song_id", -1L)
        if (savedSongId != -1L) {
            val song = songs.firstOrNull { it.id == savedSongId }
            if (song != null) {
                _currentSong.value = song
                _durationMs.value = song.durationMs
                _progressMs.value = prefs.getLong("last_progress_ms", 0L)
                _activeAlbumId.value = albumId ?: prefs.getLong("last_album_id", -1L).takeIf { it != -1L }
            }
        }
    }
}
