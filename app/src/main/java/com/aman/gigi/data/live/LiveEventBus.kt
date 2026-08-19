package com.aman.gigi.data.live

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Socket pushes for the Live tab. */
sealed interface LiveEvent {
    data class JoinRequested(val postId: String, val memberId: String, val name: String) : LiveEvent
    data class JoinAnswered(val postId: String, val memberId: String, val accepted: Boolean) : LiveEvent
    data class PeerLocation(
        val postId: String, val memberId: String, val name: String,
        val lat: Double, val lng: Double, val heading: Float?,
        val avatarUrl: String? = null
    ) : LiveEvent
    data class PostDone(val postId: String) : LiveEvent
    data object PostAdded : LiveEvent
}

/**
 * Bridges the WebSocket (owned by ScribbleSyncManager) to LiveViewModel without giving
 * the sync manager a dependency on the UI layer. Replay 0 — Live events are only
 * meaningful while the tab is on screen.
 */
object LiveEventBus {
    private val _events = MutableSharedFlow<LiveEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<LiveEvent> = _events

    fun emit(event: LiveEvent) { _events.tryEmit(event) }
}
