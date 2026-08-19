package com.aman.gigi.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aman.gigi.data.music.CustomAlbumStore
import com.aman.gigi.data.music.StoredCustomAlbum
import com.aman.gigi.data.music.SharedAlbumStore
import com.aman.gigi.data.music.StoredSharedAlbum
import com.aman.gigi.data.music.YTSearchResult
import com.aman.gigi.data.music.YoutubeMusicClient
import com.aman.gigi.data.music.MusicDownloader
import com.aman.gigi.model.MusicAlbum
import com.aman.gigi.model.LocalSong
import com.aman.gigi.service.LyricLine
import com.aman.gigi.service.LyricsRepository
import com.aman.gigi.service.NotificationHelper
import com.aman.gigi.service.PlaybackManager
import com.aman.gigi.service.PlaybackRepeatMode
import com.aman.gigi.service.PlaybackSource
import com.aman.gigi.service.MusicPlaybackService
import com.aman.gigi.service.ThemeSongPlayer
import com.aman.gigi.utils.MusicLibraryScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

data class MusicUiState(
    val hasMusicPermission: Boolean = false,
    val isScanning: Boolean = false,
    val isPreparing: Boolean = false,
    val songs: List<LocalSong> = emptyList(),
    val albums: List<MusicAlbum> = emptyList(),
    val playbackQueue: List<LocalSong> = emptyList(),
    val activeAlbumId: Long? = null,
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
    val isSearching: Boolean = false,
    val searchResults: List<YTSearchResult> = emptyList(),
    val downloadingVideoIds: Set<String> = emptySet(),
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.NONE,
    val shuffleEnabled: Boolean = false,
    val sleepTimerRemainingMs: Long? = null,   // null = no timer
    val lyrics: List<LyricLine>? = null,        // null = loading / not found
    val isLyricsVisible: Boolean = false,
    val heartbeatActive: Boolean = false,
    val heartbeatPartnerName: String? = null,
    val sharedNowPlaying: Map<String, Pair<String, String>> = emptyMap() // connectionId to (title, artist)
) {
    val currentSong: LocalSong?
        get() = playbackQueue.firstOrNull { it.id == currentSongId }
            ?: songs.firstOrNull { it.id == currentSongId }
}

@HiltViewModel
class MusicViewModel @Inject constructor(
    application: Application,
    private val musicLibraryScanner: MusicLibraryScanner,
    private val customAlbumStore: CustomAlbumStore,
    private val sharedAlbumStore: SharedAlbumStore,
    private val themeSongPlayer: ThemeSongPlayer,
    private val playbackManager: PlaybackManager,
    private val youtubeMusicClient: YoutubeMusicClient,
    private val musicDownloader: MusicDownloader,
    private val syncManager: com.aman.gigi.data.sync.ScribbleSyncManager,
    private val connectionRepository: com.aman.gigi.repository.ConnectionRepository,
    private val lyricsRepository: LyricsRepository,
    private val notificationHelper: NotificationHelper,
    private val recentPlayDao: com.aman.gigi.db.RecentPlayDao
) : AndroidViewModel(application) {

    private val tag = "MusicViewModel"

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    val activeConnections = connectionRepository.getActiveConnections()

    /** Play history, newest first — backs the Library's "Recent" deck. */
    val recentPlays = recentPlayDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isAlbumBrowserOpen = MutableStateFlow(false)
    val isAlbumBrowserOpen = _isAlbumBrowserOpen.asStateFlow()

    // Whether the music settings sheet (opened from the nav pill) is showing.
    private val _isMusicSettingsOpen = MutableStateFlow(false)
    val isMusicSettingsOpen = _isMusicSettingsOpen.asStateFlow()

    fun setMusicSettingsOpen(open: Boolean) {
        _isMusicSettingsOpen.value = open
    }

    private var searchJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lyricsJob: Job? = null
    private var heartbeatJob: Job? = null

    // ────────────────── Listen History for Compatibility Score ──────────────────
    private val prefs = application.getSharedPreferences("gigi_music_prefs", Context.MODE_PRIVATE)

    private fun recordListenedSong(song: LocalSong) {
        val key = "listened_artists"
        val current = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        current.add(song.artist.trim().lowercase())
        current.add("${song.title.trim().lowercase()}::${song.artist.trim().lowercase()}")
        // Keep max 500 entries
        val trimmed = if (current.size > 500) current.take(500).toMutableSet() else current
        prefs.edit().putStringSet(key, trimmed).apply()
    }

    fun getLocalListenHistory(): Set<String> =
        prefs.getStringSet("listened_artists", emptySet()) ?: emptySet()

    fun computeCompatibilityScore(partnerHistory: Set<String>): Int {
        val local = getLocalListenHistory()
        if (local.isEmpty() || partnerHistory.isEmpty()) return 0
        val intersection = local.intersect(partnerHistory).size
        val union = local.union(partnerHistory).size
        return if (union == 0) 0 else ((intersection.toFloat() / union) * 100).toInt().coerceIn(0, 100)
    }

    init {
        // Collect states from PlaybackManager to sync with _uiState
        viewModelScope.launch {
            playbackManager.currentSong.collect { song ->
                _uiState.update { it.copy(currentSongId = song?.id) }
                if (song != null) {
                    recordListenedSong(song)
                    fetchLyrics(song)
                }
            }
        }

        viewModelScope.launch {
            playbackManager.isPlaying.collect { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
            }
        }
        viewModelScope.launch {
            playbackManager.isPreparing.collect { preparing ->
                _uiState.update { it.copy(isPreparing = preparing) }
            }
        }
        viewModelScope.launch {
            playbackManager.playbackQueue.collect { queue ->
                _uiState.update { it.copy(playbackQueue = queue) }
            }
        }
        viewModelScope.launch {
            playbackManager.activeAlbumId.collect { albumId ->
                _uiState.update { it.copy(activeAlbumId = albumId) }
            }
        }
        viewModelScope.launch {
            playbackManager.progressMs.collect { progress ->
                _uiState.update { it.copy(progressMs = progress) }
            }
        }
        viewModelScope.launch {
            playbackManager.durationMs.collect { duration ->
                _uiState.update { it.copy(durationMs = duration) }
            }
        }
        viewModelScope.launch {
            playbackManager.repeatMode.collect { mode ->
                _uiState.update { it.copy(repeatMode = mode) }
            }
        }
        viewModelScope.launch {
            playbackManager.shuffleEnabled.collect { enabled ->
                _uiState.update { it.copy(shuffleEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            syncManager.events.collect { event ->
                if (event is com.aman.gigi.data.sync.SyncEvent.SharedAlbumReceived) {
                    scanDeviceMusic()
                }
                handleSyncEvent(event)
            }
        }
    }

    private fun handleSyncEvent(event: com.aman.gigi.data.sync.SyncEvent) {
        when {
            event is com.aman.gigi.data.sync.SyncEvent.RemoteCommand -> {
                when (event.command) {
                    "COMMAND_NOW_PLAYING" -> {
                        val title = event.data?.optString("title", "") ?: ""
                        val artist = event.data?.optString("artist", "") ?: ""
                        val partnerName = event.data?.optString("senderName", "Partner") ?: "Partner"
                        if (title.isNotBlank()) {
                            _uiState.update { 
                                val newMap = it.sharedNowPlaying.toMutableMap()
                                newMap[event.connectionId] = Pair(title, artist)
                                it.copy(sharedNowPlaying = newMap) 
                            }
                            notificationHelper.showNowPlayingReceivedNotification(
                                getApplication(), partnerName, title, artist
                            )
                        }
                    }
                    "COMMAND_QUEUE_SONG" -> {
                        val title = event.data?.optString("title") ?: return
                        val artist = event.data?.optString("artist") ?: return
                        val song = _uiState.value.songs.firstOrNull {
                            it.title.trim().equals(title.trim(), ignoreCase = true) &&
                            it.artist.trim().equals(artist.trim(), ignoreCase = true)
                        }
                        if (song != null) {
                            playbackManager.addToQueue(song)
                        }
                    }
                    "COMMAND_HEARTBEAT_START" -> {
                        val title = event.data?.optString("title") ?: return
                        val artist = event.data?.optString("artist") ?: return
                        val progressMs = event.data?.optLong("progressMs") ?: 0L
                        val senderName = event.data?.optString("senderName", "Partner") ?: "Partner"
                        val song = _uiState.value.songs.firstOrNull {
                            it.title.trim().equals(title.trim(), ignoreCase = true)
                        }
                        if (song != null) {
                            playSong(song, null)
                            viewModelScope.launch {
                                delay(1800) // wait for prepare
                                seekTo(progressMs)
                            }
                        }
                        _uiState.update { it.copy(heartbeatActive = true, heartbeatPartnerName = senderName) }
                    }
                    "COMMAND_HEARTBEAT_SYNC" -> {
                        val progressMs = event.data?.optLong("progressMs") ?: return
                        seekTo(progressMs)
                    }
                    "COMMAND_HEARTBEAT_STOP" -> {
                        _uiState.update { it.copy(heartbeatActive = false, heartbeatPartnerName = null) }
                        heartbeatJob?.cancel()
                    }
                }
            }
        }
    }

    fun setAlbumBrowserOpen(open: Boolean) {
        _isAlbumBrowserOpen.value = open
    }

    fun onPermissionStateChanged(granted: Boolean) {
        _uiState.update { state ->
            if (state.hasMusicPermission == granted) state else state.copy(hasMusicPermission = granted)
        }
    }

    fun scanDeviceMusic() {
        if (!_uiState.value.hasMusicPermission) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, errorMessage = null) }

            runCatching {
                musicLibraryScanner.scanSongs()
            }.onSuccess { songs ->
                val storedCustomAlbums = customAlbumStore.loadAlbums()
                val sharedStoredAlbums = sharedAlbumStore.loadSharedAlbums()
                val albums = buildAlbumCollection(songs, storedCustomAlbums, sharedStoredAlbums)

                if (playbackManager.currentSong.value == null) {
                    playbackManager.restoreState(songs, null)
                }

                val currentSongId = playbackManager.currentSong.value?.id
                val activeAlbumId = playbackManager.activeAlbumId.value ?: resolveActiveAlbumId(
                    currentSongId = currentSongId,
                    existingActiveAlbumId = _uiState.value.activeAlbumId,
                    albums = albums,
                    songs = songs
                )
                val playbackQueue = buildPlaybackQueue(
                    songs = songs,
                    albums = albums,
                    activeAlbumId = activeAlbumId,
                    preferredSongId = currentSongId,
                    previousQueue = _uiState.value.playbackQueue
                )

                if (playbackManager.playbackQueue.value.isEmpty()) {
                    playbackManager.setQueue(playbackQueue, activeAlbumId)
                }

                _uiState.update {
                    it.copy(
                        isScanning = false,
                        songs = songs,
                        albums = albums,
                        playbackQueue = playbackManager.playbackQueue.value,
                        activeAlbumId = playbackManager.activeAlbumId.value,
                        currentSongId = playbackManager.currentSong.value?.id,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                Log.e(tag, "Device music scan failed", error)
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        songs = emptyList(),
                        albums = emptyList(),
                        playbackQueue = emptyList(),
                        activeAlbumId = null,
                        currentSongId = null,
                        errorMessage = "Gigi could not scan music on this phone."
                    )
                }
            }
        }
    }

    private fun startPlaybackService() {
        val intent = Intent(getApplication(), MusicPlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun playSong(song: LocalSong, albumContextId: Long? = null) {
        val state = _uiState.value
        val resolvedAlbumId = albumContextId?.takeIf { id -> state.albums.any { it.albumId == id } }
            ?: state.activeAlbumId?.takeIf { id -> state.albums.any { it.albumId == id } }

        themeSongPlayer.setAutoPlaybackSuppressed(true)
        themeSongPlayer.stop()

        startPlaybackService()
        playbackManager.playSong(song, resolvedAlbumId)
        rememberPlay(song)
    }

    /**
     * Records the play for the Recent deck. Keyed on songId so a track on repeat moves
     * up the list instead of filling history with copies of itself.
     */
    private fun rememberPlay(song: LocalSong) {
        viewModelScope.launch {
            runCatching {
                recentPlayDao.record(
                    com.aman.gigi.db.RecentPlay(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        albumArtUri = song.albumArtUri?.toString(),
                        playedAt = System.currentTimeMillis()
                    )
                )
                recentPlayDao.trim()
            }.onFailure { Log.w(tag, "Could not record play: ${it.message}") }
        }
    }

    fun clearRecentPlays() {
        viewModelScope.launch { runCatching { recentPlayDao.clear() } }
    }

    fun playAlbum(album: MusicAlbum) {
        album.tracks.firstOrNull()?.let { firstTrack ->
            playbackManager.setQueue(album.tracks, album.albumId)
            playSong(firstTrack, album.albumId)
        } ?: run {
            _uiState.update { it.copy(errorMessage = "This album does not have playable songs.") }
        }
    }

    fun playShuffledLibrary() {
        val state = _uiState.value
        val queue = buildShuffledQueue(
            songs = state.songs,
            preferredSongId = state.currentSongId,
            previousQueue = state.playbackQueue
        )

        playbackManager.setQueue(queue, null)
        queue.firstOrNull()?.let { firstSong ->
            playSong(firstSong, null)
        }
    }

    // ────────────────── Repeat & Shuffle ──────────────────

    fun toggleRepeat() = playbackManager.toggleRepeat()
    fun toggleShuffle() = playbackManager.toggleShuffle()

    // ────────────────── Sleep Timer ──────────────────

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val totalMs = minutes * 60_000L
        _uiState.update { it.copy(sleepTimerRemainingMs = totalMs) }
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1000L
                _uiState.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0L)) }
            }
            // Timer expired: pause
            if (_uiState.value.isPlaying) {
                togglePlayback()
            }
            _uiState.update { it.copy(sleepTimerRemainingMs = null) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.update { it.copy(sleepTimerRemainingMs = null) }
    }

    // ────────────────── Share Now Playing ──────────────────

    fun shareNowPlaying(connectionId: String) {
        val song = _uiState.value.currentSong ?: return
        viewModelScope.launch {
            try {
                val payload = org.json.JSONObject().apply {
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album)
                    put("senderName", "Me")
                }
                syncManager.sendRemoteCommandWithData(
                    connectionId = connectionId,
                    command = "COMMAND_NOW_PLAYING",
                    data = payload
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to share now playing", e)
            }
        }
    }

    fun dismissSharedNowPlaying(connectionId: String) {
        _uiState.update { 
            val newMap = it.sharedNowPlaying.toMutableMap()
            newMap.remove(connectionId)
            it.copy(sharedNowPlaying = newMap) 
        }
    }

    // ────────────────── Shared Queue ──────────────────

    fun addToSharedQueue(song: LocalSong, connectionId: String) {
        viewModelScope.launch {
            try {
                val payload = org.json.JSONObject().apply {
                    put("title", song.title)
                    put("artist", song.artist)
                }
                syncManager.sendRemoteCommandWithData(
                    connectionId = connectionId,
                    command = "COMMAND_QUEUE_SONG",
                    data = payload
                )
                // Also add locally
                playbackManager.addToQueue(song)
            } catch (e: Exception) {
                Log.e(tag, "Failed to add to shared queue", e)
            }
        }
    }

    // ────────────────── Lyrics ──────────────────

    private fun fetchLyrics(song: LocalSong) {
        lyricsJob?.cancel()
        _uiState.update { it.copy(lyrics = null) }
        lyricsJob = viewModelScope.launch {
            val lyrics = lyricsRepository.fetchLyrics(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album
            )
            _uiState.update { it.copy(lyrics = lyrics ?: emptyList()) }
        }
    }

    fun toggleLyricsVisible() {
        _uiState.update { it.copy(isLyricsVisible = !it.isLyricsVisible) }
    }

    // ────────────────── Heartbeat Mode ──────────────────

    fun startHeartbeatMode(connectionId: String) {
        val song = _uiState.value.currentSong ?: return
        val progress = _uiState.value.progressMs
        heartbeatJob?.cancel()
        _uiState.update { it.copy(heartbeatActive = true) }
        viewModelScope.launch {
            try {
                val payload = org.json.JSONObject().apply {
                    put("title", song.title)
                    put("artist", song.artist)
                    put("progressMs", progress)
                    put("senderName", "Me")
                }
                syncManager.sendRemoteCommandWithData(connectionId, "COMMAND_HEARTBEAT_START", payload)
            } catch (e: Exception) {
                Log.e(tag, "Failed to start heartbeat mode", e)
            }
        }
        // Sync position every 10 seconds
        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(10_000L)
                if (!_uiState.value.heartbeatActive) break
                try {
                    val syncPayload = org.json.JSONObject().apply {
                        put("progressMs", _uiState.value.progressMs)
                    }
                    syncManager.sendRemoteCommandWithData(connectionId, "COMMAND_HEARTBEAT_SYNC", syncPayload)
                } catch (_: Exception) { }
            }
        }
    }

    fun stopHeartbeatMode(connectionId: String) {
        heartbeatJob?.cancel()
        _uiState.update { it.copy(heartbeatActive = false, heartbeatPartnerName = null) }
        viewModelScope.launch {
            try {
                syncManager.sendRemoteCommandWithData(
                    connectionId, "COMMAND_HEARTBEAT_STOP",
                    org.json.JSONObject()
                )
            } catch (_: Exception) { }
        }
    }

    // ────────────────── Standard Controls ──────────────────

    fun createCustomAlbum(name: String, songIds: List<Long>) {
        val currentSongs = _uiState.value.songs
        if (name.isBlank() || songIds.isEmpty() || currentSongs.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Add a name and at least one song to create an album.") }
            return
        }

        viewModelScope.launch {
            val storedAlbums = customAlbumStore.createAlbum(name, songIds)
            val sharedStoredAlbums = sharedAlbumStore.loadSharedAlbums()
            val albums = buildAlbumCollection(currentSongs, storedAlbums, sharedStoredAlbums)
            val playbackQueue = buildPlaybackQueue(
                songs = currentSongs,
                albums = albums,
                activeAlbumId = _uiState.value.activeAlbumId,
                preferredSongId = _uiState.value.currentSongId,
                previousQueue = _uiState.value.playbackQueue
            )
            playbackManager.setQueue(playbackQueue, _uiState.value.activeAlbumId)
            _uiState.update { state ->
                state.copy(
                    albums = albums,
                    playbackQueue = playbackQueue,
                    activeAlbumId = resolveActiveAlbumId(
                        currentSongId = state.currentSongId,
                        existingActiveAlbumId = state.activeAlbumId,
                        albums = albums,
                        songs = currentSongs
                    ),
                    errorMessage = null
                )
            }
        }
    }

    fun deleteAlbum(albumId: Long) {
        val currentSongs = _uiState.value.songs
        viewModelScope.launch {
            val sharedAlbums = sharedAlbumStore.loadSharedAlbums()
            val isShared = sharedAlbums.any { it.id == albumId }

            val storedCustomAlbums = if (isShared) {
                sharedAlbumStore.deleteSharedAlbum(albumId)
                customAlbumStore.loadAlbums()
            } else {
                customAlbumStore.deleteAlbum(albumId)
            }
            val sharedStoredAlbums = sharedAlbumStore.loadSharedAlbums()

            val albums = buildAlbumCollection(currentSongs, storedCustomAlbums, sharedStoredAlbums)
            val nextActiveAlbumId = if (_uiState.value.activeAlbumId == albumId) {
                null
            } else {
                _uiState.value.activeAlbumId
            }
            val playbackQueue = buildPlaybackQueue(
                songs = currentSongs,
                albums = albums,
                activeAlbumId = nextActiveAlbumId,
                preferredSongId = _uiState.value.currentSongId,
                previousQueue = _uiState.value.playbackQueue
            )
            playbackManager.setQueue(playbackQueue, nextActiveAlbumId)
            _uiState.update { state ->
                state.copy(
                    albums = albums,
                    playbackQueue = playbackQueue,
                    activeAlbumId = nextActiveAlbumId,
                    errorMessage = null
                )
            }
        }
    }

    fun togglePlayback(fromLockscreen: Boolean = false) {
        val state = _uiState.value
        val playbackQueue = resolvePlaybackQueue(state)
        val selectedSong = state.currentSong ?: playbackQueue.firstOrNull() ?: state.songs.firstOrNull()

        if (selectedSong == null) {
            _uiState.update { it.copy(errorMessage = "No songs found on this phone yet.") }
            return
        }

        themeSongPlayer.setAutoPlaybackSuppressed(true)
        themeSongPlayer.stop()
        startPlaybackService()
        playbackManager.togglePlayback(if (fromLockscreen) PlaybackSource.LOCKSCREEN else PlaybackSource.APP)
    }

    fun playNext() { playbackManager.playNext() }
    fun playPrevious() { playbackManager.playPrevious() }
    fun seekTo(positionMs: Long) { playbackManager.seekTo(positionMs) }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun resolvePlaybackQueue(state: MusicUiState): List<LocalSong> {
        return state.playbackQueue.takeIf { it.isNotEmpty() }
            ?: state.albums.firstOrNull { it.albumId == state.activeAlbumId }?.tracks?.takeIf { it.isNotEmpty() }
            ?: state.songs
    }

    private fun resolveActiveAlbumId(
        currentSongId: Long?,
        existingActiveAlbumId: Long?,
        albums: List<MusicAlbum>,
        songs: List<LocalSong>
    ): Long? {
        if (existingActiveAlbumId != null && albums.any { it.albumId == existingActiveAlbumId }) {
            return existingActiveAlbumId
        }
        return albums.firstOrNull { album -> album.tracks.any { it.id == currentSongId } }?.albumId
    }

    private fun buildAlbumCollection(
        songs: List<LocalSong>,
        storedCustomAlbums: List<StoredCustomAlbum>,
        sharedStoredAlbums: List<StoredSharedAlbum>
    ): List<MusicAlbum> {
        val localCustoms = buildCustomAlbums(songs, storedCustomAlbums)
        val partnerShareds = buildSharedAlbums(songs, sharedStoredAlbums)
        return localCustoms + partnerShareds
    }

    private fun buildCustomAlbums(
        songs: List<LocalSong>,
        storedCustomAlbums: List<StoredCustomAlbum>?
    ): List<MusicAlbum> {
        val albumsList = storedCustomAlbums ?: return emptyList()
        if (songs.isEmpty() || albumsList.isEmpty()) return emptyList()

        val songsById = songs.associateBy(LocalSong::id)
        return albumsList.mapNotNull { stored ->
            val songIds = stored.songIds ?: return@mapNotNull null
            val orderedTracks = songIds.mapNotNull(songsById::get)
            if (orderedTracks.isEmpty()) {
                null
            } else {
                MusicAlbum(
                    albumId = stored.id,
                    title = stored.name ?: "Custom album",
                    artist = "Custom album",
                    albumArtUri = orderedTracks.firstNotNullOfOrNull { it.albumArtUri },
                    tracks = orderedTracks,
                    isCustom = true
                )
            }
        }
    }

    private fun buildSharedAlbums(
        songs: List<LocalSong>,
        sharedStoredAlbums: List<StoredSharedAlbum>?
    ): List<MusicAlbum> {
        val albumsList = sharedStoredAlbums ?: return emptyList()
        if (albumsList.isEmpty()) return emptyList()

        val songsByTitleArtist = songs.associateBy { (it.title.trim().lowercase() + "::" + it.artist.trim().lowercase()) }

        return albumsList.mapNotNull { stored ->
            val sharedSongs = stored.songs ?: return@mapNotNull null
            val albumId = stored.id
            val albumName = stored.name ?: "Shared Album"
            val senderName = stored.senderName ?: "Partner"

            val mappedTracks = sharedSongs.mapIndexed { index, sharedSong ->
                val songTitle = sharedSong.title ?: "Unknown Song"
                val songArtist = sharedSong.artist ?: "Unknown Artist"
                val songAlbum = sharedSong.album ?: ""
                val songDuration = sharedSong.durationMs

                val key = songTitle.trim().lowercase() + "::" + songArtist.trim().lowercase()
                songsByTitleArtist[key] ?: run {
                    val placeholderId = (songTitle + songArtist).hashCode().toLong()
                    LocalSong(
                        id = placeholderId,
                        title = songTitle,
                        artist = songArtist,
                        album = songAlbum.ifBlank { albumName },
                        albumId = albumId,
                        trackNumber = index + 1,
                        durationMs = songDuration,
                        contentUri = android.net.Uri.parse("shared://downloadable/$placeholderId")
                    )
                }
            }

            MusicAlbum(
                albumId = albumId,
                title = albumName,
                artist = "Shared Album",
                albumArtUri = mappedTracks.firstNotNullOfOrNull { it.albumArtUri },
                tracks = mappedTracks,
                isCustom = true,
                sharedByPartnerName = senderName
            )
        }
    }

    fun shareAlbum(album: MusicAlbum, connectionId: String) {
        viewModelScope.launch {
            try {
                val payload = org.json.JSONObject().apply {
                    put("name", album.title)
                    val songsArray = org.json.JSONArray()
                    album.tracks.forEach { song ->
                        songsArray.put(org.json.JSONObject().apply {
                            put("title", song.title)
                            put("artist", song.artist)
                            put("album", song.album)
                            put("durationMs", song.durationMs)
                        })
                    }
                    put("songs", songsArray)
                }
                syncManager.sendRemoteCommandWithData(
                    connectionId = connectionId,
                    command = "COMMAND_SHARE_ALBUM",
                    data = payload
                )
                Log.i(tag, "✅ Shared custom album ${album.title} with partner connection $connectionId")
            } catch (e: Exception) {
                Log.e(tag, "Failed to share custom album", e)
            }
        }
    }

    fun downloadSharedAlbum(album: MusicAlbum) {
        viewModelScope.launch {
            val pendingDownloads = album.tracks.filter { it.contentUri.toString().startsWith("shared://") }
            if (pendingDownloads.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "All songs in this album are already downloaded!") }
                return@launch
            }

            _uiState.update { state ->
                val pendingVideoIds = state.downloadingVideoIds.toMutableSet()
                pendingDownloads.forEach { pendingVideoIds.add(it.id.toString()) }
                state.copy(downloadingVideoIds = pendingVideoIds)
            }

            var successCount = 0
            pendingDownloads.forEach { pending ->
                val query = "${pending.title} ${pending.artist}"
                val results = youtubeMusicClient.searchSongs(query)
                val topResult = results.firstOrNull()
                if (topResult != null) {
                    val streamUrl = youtubeMusicClient.getAudioStreamUrl(topResult.videoId)
                    if (streamUrl != null) {
                        val success = musicDownloader.downloadSong(topResult, streamUrl)
                        if (success) successCount++
                    }
                }
                _uiState.update { state ->
                    state.copy(downloadingVideoIds = state.downloadingVideoIds - pending.id.toString())
                }
            }

            if (successCount > 0) {
                scanDeviceMusic()
            }
        }
    }

    private fun buildPlaybackQueue(
        songs: List<LocalSong>,
        albums: List<MusicAlbum>,
        activeAlbumId: Long?,
        preferredSongId: Long?,
        previousQueue: List<LocalSong>
    ): List<LocalSong> {
        return albums.firstOrNull { it.albumId == activeAlbumId }
            ?.tracks
            ?.takeIf { it.isNotEmpty() }
            ?: buildShuffledQueue(
                songs = songs,
                preferredSongId = preferredSongId,
                previousQueue = previousQueue
            )
    }

    private fun buildShuffledQueue(
        songs: List<LocalSong>,
        preferredSongId: Long?,
        previousQueue: List<LocalSong>
    ): List<LocalSong> {
        if (songs.isEmpty()) return emptyList()

        val songsById = songs.associateBy(LocalSong::id)
        val previousOrder = previousQueue.mapNotNull { songsById[it.id] }
        val remainingSongs = songs.filterNot { song -> previousOrder.any { it.id == song.id } }.shuffled()
        val queue = (previousOrder + remainingSongs).distinctBy(LocalSong::id).ifEmpty { songs.shuffled() }

        if (preferredSongId == null) return queue

        val preferredIndex = queue.indexOfFirst { it.id == preferredSongId }
        if (preferredIndex <= 0) return queue

        return listOf(queue[preferredIndex]) + queue.filterIndexed { index, _ -> index != preferredIndex }
    }

    fun searchYoutube(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        _uiState.update { it.copy(isSearching = true) }
        searchJob = viewModelScope.launch {
            val results = youtubeMusicClient.searchSongs(query)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
            
            // Check for lyrics in parallel
            val updatedResults = results.map { result ->
                async {
                    val has = lyricsRepository.hasLyrics(result.title, result.artist)
                    result.copy(hasLyrics = has)
                }
            }.awaitAll()
            
            _uiState.update { it.copy(searchResults = updatedResults) }
        }
    }

    fun downloadAndPlayYoutubeSong(result: YTSearchResult) {
        _uiState.update { it.copy(downloadingVideoIds = it.downloadingVideoIds + result.videoId) }
        viewModelScope.launch {
            val streamUrl = youtubeMusicClient.getAudioStreamUrl(result.videoId)
            if (streamUrl != null) {
                // We'll also fetch lyrics and save to a .lrc file via MusicDownloader
                val lyricsContent = if (result.hasLyrics) {
                    val rawLyrics = lyricsRepository.fetchLyrics(
                        songId = -1L,
                        title = result.title,
                        artist = result.artist,
                        album = ""
                    )
                    rawLyrics?.joinToString("\n") { "[%02d:%02d.%03d]${it.text}".format(
                        (it.timestampMs / 60000),
                        (it.timestampMs % 60000) / 1000,
                        (it.timestampMs % 1000)
                    ) }
                } else null

                val success = musicDownloader.downloadSong(result, streamUrl, lyricsContent)
                if (success) {
                    scanDeviceMusic()
                } else {
                    _uiState.update { it.copy(errorMessage = "Failed to download ${result.title}") }
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Could not fetch stream URL for ${result.title}") }
            }
            _uiState.update { it.copy(downloadingVideoIds = it.downloadingVideoIds - result.videoId) }
        }
    }

    fun streamYoutubeSong(result: YTSearchResult) {
        _uiState.update { it.copy(downloadingVideoIds = it.downloadingVideoIds + result.videoId) }
        viewModelScope.launch {
            val streamUrl = youtubeMusicClient.getAudioStreamUrl(result.videoId)
            if (streamUrl != null) {
                // Create a temporary LocalSong that points to the stream URL
                val tempSong = LocalSong(
                    id = result.videoId.hashCode().toLong(),
                    title = result.title,
                    artist = result.artist,
                    album = "YouTube Stream",
                    albumId = 0L,
                    trackNumber = 1,
                    durationMs = 0L,
                    contentUri = android.net.Uri.parse(streamUrl),
                    albumArtUri = android.net.Uri.parse(result.thumbnailUrl)
                )
                
                themeSongPlayer.setAutoPlaybackSuppressed(true)
                themeSongPlayer.stop()
                startPlaybackService()
                
                // Clear queue and play this temporary song
                playbackManager.setQueue(listOf(tempSong), null)
                playbackManager.playSong(tempSong, null)
            } else {
                _uiState.update { it.copy(errorMessage = "Could not fetch stream URL for ${result.title}") }
            }
            _uiState.update { it.copy(downloadingVideoIds = it.downloadingVideoIds - result.videoId) }
        }
    }

    override fun onCleared() {
        themeSongPlayer.setAutoPlaybackSuppressed(false)
        sleepTimerJob?.cancel()
        heartbeatJob?.cancel()
        lyricsJob?.cancel()
        super.onCleared()
    }
}
