package com.aman.gigi.ui.live

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aman.gigi.data.live.LiveEvent
import com.aman.gigi.data.live.LiveEventBus
import com.aman.gigi.repository.LivePost
import com.aman.gigi.repository.LivePresence
import com.aman.gigi.repository.LiveRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A pending "can I come?" waiting on the host. */
data class JoinRequest(
    val postId: String,
    val memberId: String,
    val name: String
)

data class LiveUiState(
    val loading: Boolean = false,
    /** True only for the very first load with nothing cached — drives the skeleton. */
    val firstLoad: Boolean = true,
    /** Socket-reported changes we haven't folded in yet; drives the "pull to refresh" pill. */
    val pendingEvents: Int = 0,
    val posts: List<LivePost> = emptyList(),
    val myLat: Double? = null,
    val myLng: Double? = null,
    val joinRequests: List<JoinRequest> = emptyList(),
    /** Live positions of everyone in the meet-up we're currently watching. */
    val presence: Map<String, LivePresence> = emptyMap(),
    val activePostId: String? = null,
    val error: String? = null,
    val refreshing: Boolean = false,
    val permissionDenied: Boolean = false,
    val history: List<LivePost> = emptyList(),
    val historyLoading: Boolean = false
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    application: Application,
    private val repository: LiveRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state

    private val fused by lazy {
        LocationServices.getFusedLocationProviderClient(getApplication<Application>())
    }

    private var hasLoaded = false

    init {
        // Paint whatever we saw last time immediately, so re-entering the tab never
        // shows an empty spinner.
        LiveFeedCache.read(application)?.let { cached ->
            _state.value = _state.value.copy(
                posts = cached.posts, myLat = cached.lat, myLng = cached.lng,
                firstLoad = false
            )
        }
        viewModelScope.launch {
            LiveEventBus.events.collect { event ->
                when (event) {
                    is LiveEvent.JoinRequested ->
                        onJoinRequested(event.postId, event.memberId, event.name)
                    is LiveEvent.PeerLocation -> onPeerLocation(
                        LivePresence(
                            memberId = event.memberId, name = event.name,
                            lat = event.lat, lng = event.lng,
                            heading = event.heading, at = System.currentTimeMillis(),
                            avatarUrl = event.avatarUrl
                        )
                    )
                    // Being accepted changes what we're allowed to see, so that one
                    // is worth fetching straight away.
                    is LiveEvent.JoinAnswered -> if (event.accepted) refresh(silent = true)
                    // Everything else just flags that there's something new. Yanking the
                    // list out from under someone mid-scroll is worse than a badge.
                    is LiveEvent.PostDone -> {
                        onPostDone(event.postId)
                        bumpPending()
                    }
                    LiveEvent.PostAdded -> bumpPending()
                }
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        val ctx = getApplication<Application>()
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    /** Called once when the tab is first shown; later visits reuse what's in memory. */
    fun loadOnce() {
        if (hasLoaded) return
        hasLoaded = true
        refresh(silent = _state.value.posts.isNotEmpty())
    }

    /**
     * @param silent keep showing the current list while revalidating, instead of
     *   flipping to a loading state. Used whenever we already have something on screen.
     */
    fun refresh(silent: Boolean = false) {
        if (!hasLocationPermission()) {
            _state.value = _state.value.copy(permissionDenied = true, loading = false)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = !silent, refreshing = silent,
                permissionDenied = false, error = null
            )
            val fix = currentLocation()
            if (fix == null) {
                _state.value = _state.value.copy(
                    loading = false, refreshing = false,
                    error = if (!locationServicesEnabled()) {
                        "Turn on Location in your phone's settings to use Live."
                    } else {
                        "Couldn't get a location fix — try again in a moment."
                    }
                )
                return@launch
            }
            val (lat, lng) = fix
            runCatching { repository.nearby(lat, lng) }
                .onSuccess { posts ->
                    _state.value = _state.value.copy(
                        loading = false, refreshing = false, firstLoad = false,
                        pendingEvents = 0, posts = posts,
                        myLat = lat, myLng = lng, error = null
                    )
                    LiveFeedCache.write(getApplication(), posts, lat, lng)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false, refreshing = false, myLat = lat, myLng = lng,
                        // Keep whatever is already on screen; a failed refresh should
                        // never blank a list the user was reading.
                        error = it.message ?: "Couldn't load Live."
                    )
                }
        }
    }

    fun createPost(
        text: String, category: String, mood: String?,
        radiusM: Int, durationMin: Int, visibility: String, maxJoiners: Int?
    ) {
        viewModelScope.launch {
            val fix = currentLocation() ?: run {
                _state.value = _state.value.copy(error = "Couldn't get your location.")
                return@launch
            }
            runCatching {
                repository.createPost(
                    text = text, category = category, mood = mood,
                    lat = fix.first, lng = fix.second,
                    radiusM = radiusM, durationMin = durationMin,
                    visibility = visibility, placeLabel = null, maxJoiners = maxJoiners
                )
            }.onSuccess { refresh() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun askToJoin(postId: String, note: String?) {
        viewModelScope.launch {
            runCatching { repository.requestJoin(postId, note) }
                .onSuccess { refresh() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun respond(request: JoinRequest, accept: Boolean) {
        viewModelScope.launch {
            runCatching { repository.respondToJoin(request.postId, request.memberId, accept) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        joinRequests = _state.value.joinRequests.filterNot {
                            it.postId == request.postId && it.memberId == request.memberId
                        }
                    )
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun markDone(postId: String) {
        viewModelScope.launch {
            runCatching { repository.markDone(postId) }.onSuccess { refresh() }
        }
    }

    fun leave(postId: String) {
        viewModelScope.launch {
            runCatching { repository.leave(postId) }.onSuccess { refresh() }
        }
    }

    fun watch(postId: String?) {
        _state.value = _state.value.copy(activePostId = postId, presence = emptyMap())
    }

    fun loadHistory() {
        viewModelScope.launch {
            _state.value = _state.value.copy(historyLoading = true)
            runCatching { repository.myPosts() }
                .onSuccess { _state.value = _state.value.copy(history = it, historyLoading = false) }
                .onFailure {
                    _state.value = _state.value.copy(
                        historyLoading = false, error = it.message ?: "Couldn't load your history."
                    )
                }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            runCatching { repository.deletePost(postId) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        history = _state.value.history.filterNot { it.postId == postId },
                        posts = _state.value.posts.filterNot { it.postId == postId }
                    )
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun bumpPending() {
        _state.value = _state.value.copy(pendingEvents = _state.value.pendingEvents + 1)
    }

    // ── socket events, forwarded by MainActivity ──────────────────────────────

    fun onJoinRequested(postId: String, memberId: String, name: String) {
        val existing = _state.value.joinRequests
        if (existing.any { it.postId == postId && it.memberId == memberId }) return
        _state.value = _state.value.copy(
            joinRequests = existing + JoinRequest(postId, memberId, name),
            pendingEvents = _state.value.pendingEvents + 1
        )
    }

    fun onPeerLocation(presence: LivePresence) {
        _state.value = _state.value.copy(
            presence = _state.value.presence + (presence.memberId to presence)
        )
    }

    fun onPostDone(postId: String) {
        _state.value = _state.value.copy(
            posts = _state.value.posts.filterNot { it.postId == postId },
            activePostId = _state.value.activePostId?.takeIf { it != postId }
        )
    }

    fun onNewPost() = refresh()

    private fun locationServicesEnabled(): Boolean = runCatching {
        LocationManagerCompat.isLocationEnabled(
            getApplication<Application>().getSystemService(LocationManager::class.java)
        )
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null

        // Last known position first. It is usually already in memory and returns in a
        // few milliseconds, whereas asking for a fresh fix can block for many seconds
        // — which is what made opening the tab feel so slow. For a "who's nearby" feed
        // a fix from a minute ago is indistinguishable from a fix from now.
        lastKnown()?.let { last ->
            // Refine in the background; re-query only if we've actually moved.
            viewModelScope.launch {
                val fresh = freshFix() ?: return@launch
                if (distanceM(last, fresh) > SIGNIFICANT_MOVE_M) {
                    runCatching { repository.nearby(fresh.first, fresh.second) }
                        .onSuccess { posts ->
                            _state.value = _state.value.copy(
                                posts = posts, myLat = fresh.first, myLng = fresh.second
                            )
                            LiveFeedCache.write(getApplication(), posts, fresh.first, fresh.second)
                        }
                }
            }
            return last
        }
        return freshFix()
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnown(): Pair<Double, Double>? = suspendCancellableCoroutine { cont ->
        runCatching {
            fused.lastLocation
                .addOnSuccessListener { loc ->
                    if (cont.isActive) cont.resume(loc?.let { it.latitude to it.longitude })
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(): Pair<Double, Double>? = suspendCancellableCoroutine { cont ->
        runCatching {
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (cont.isActive) cont.resume(loc?.let { it.latitude to it.longitude })
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    private fun distanceM(a: Pair<Double, Double>, b: Pair<Double, Double>): Float {
        val out = FloatArray(1)
        android.location.Location.distanceBetween(a.first, a.second, b.first, b.second, out)
        return out[0]
    }

    private companion object {
        /** Below this, the feed would come back identical — not worth a second call. */
        const val SIGNIFICANT_MOVE_M = 250f
    }
}
