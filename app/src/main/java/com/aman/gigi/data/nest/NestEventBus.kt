package com.aman.gigi.data.nest

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject

sealed interface NestEvent {
    data class RoomUpdated(val connectionCode: String, val payload: JSONObject) : NestEvent
    data class PartnerMoved(val connectionCode: String, val x: Float, val y: Float, val anim: String, val facingLeft: Boolean) : NestEvent
    data class EmoteSent(val connectionCode: String, val actorName: String, val emote: String) : NestEvent
    data class PetInteracted(val connectionCode: String, val actorName: String, val action: String, val pet: PetState) : NestEvent
}

object NestEventBus {
    private val _events = MutableSharedFlow<NestEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<NestEvent> = _events

    fun emit(event: NestEvent) {
        _events.tryEmit(event)
    }
}
