package com.aman.gigi.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aman.gigi.data.client.ConnectionBootstrapManager
import com.aman.gigi.data.nest.*
import com.aman.gigi.data.sync.ScribbleSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class NestViewModel @Inject constructor(
    application: Application,
    private val nestRepository: NestRepository,
    private val syncManager: ScribbleSyncManager,
    private val bootstrapManager: ConnectionBootstrapManager
) : AndroidViewModel(application) {

    val roomState: StateFlow<NestRoomData?> = nestRepository.roomState

    private val _activeConnectionCode = MutableStateFlow<String?>(null)
    val activeConnectionCode: StateFlow<String?> = _activeConnectionCode.asStateFlow()

    private val _partnerName = MutableStateFlow("Partner")
    val partnerName: StateFlow<String> = _partnerName.asStateFlow()

    private val _myTwigiState = MutableStateFlow(TwigiRoomPosition(x = 0.35f, y = 0.55f))
    val myTwigiState: StateFlow<TwigiRoomPosition> = _myTwigiState.asStateFlow()

    private val _partnerTwigiState = MutableStateFlow(TwigiRoomPosition(x = 0.65f, y = 0.55f, facingLeft = true))
    val partnerTwigiState: StateFlow<TwigiRoomPosition> = _partnerTwigiState.asStateFlow()

    private val _timeOfDay = MutableStateFlow(calculateTimeOfDay())
    val timeOfDay: StateFlow<TimeOfDay> = _timeOfDay.asStateFlow()

    private val _isDecorMode = MutableStateFlow(false)
    val isDecorMode: StateFlow<Boolean> = _isDecorMode.asStateFlow()

    private val _isFridgeOpen = MutableStateFlow(false)
    val isFridgeOpen: StateFlow<Boolean> = _isFridgeOpen.asStateFlow()

    private val _isShopOpen = MutableStateFlow(false)
    val isShopOpen: StateFlow<Boolean> = _isShopOpen.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _activeEmote = MutableStateFlow<Pair<String, String>?>(null)
    val activeEmote: StateFlow<Pair<String, String>?> = _activeEmote.asStateFlow()

    init {
        // Listen to NestEventBus events
        viewModelScope.launch {
            NestEventBus.events.collect { event ->
                when (event) {
                    is NestEvent.RoomUpdated -> {
                        nestRepository.handleRemoteRoomUpdate(event.payload)
                    }
                    is NestEvent.PartnerMoved -> {
                        val current = _partnerTwigiState.value
                        val newAction = when (event.anim) {
                            "walk" -> TwigiAction.WALK
                            "sit" -> TwigiAction.SIT_COUCH
                            "sleep" -> TwigiAction.SLEEP_BED
                            "jam" -> TwigiAction.JAM_MUSIC
                            else -> TwigiAction.IDLE
                        }
                        _partnerTwigiState.value = current.copy(
                            x = event.x,
                            y = event.y,
                            facingLeft = event.facingLeft,
                            action = newAction
                        )
                    }
                    is NestEvent.EmoteSent -> {
                        _activeEmote.value = event.actorName to event.emote
                        _toastEvent.tryEmit("${event.actorName}: ${event.emote}")
                    }
                    is NestEvent.PetInteracted -> {
                        _toastEvent.tryEmit("${event.actorName} ${if (event.action == "FEED") "fed" else "petted"} Mochi! 🐱✨")
                    }
                }
            }
        }
    }

    fun setConnection(connectionCode: String, name: String) {
        if (_activeConnectionCode.value == connectionCode) return
        _activeConnectionCode.value = connectionCode
        _partnerName.value = name
        viewModelScope.launch {
            nestRepository.fetchRoom(connectionCode)
        }
    }

    fun setDecorMode(active: Boolean) {
        _isDecorMode.value = active
    }

    fun setFridgeOpen(open: Boolean) {
        _isFridgeOpen.value = open
    }

    fun setShopOpen(open: Boolean) {
        _isShopOpen.value = open
    }

    /** Move own Twigi to a position on the floor and broadcast to partner */
    fun moveMyTwigiTo(targetX: Float, targetY: Float, action: TwigiAction = TwigiAction.WALK) {
        val current = _myTwigiState.value
        val facingLeft = targetX < current.x
        _myTwigiState.value = current.copy(
            x = targetX,
            y = targetY,
            facingLeft = facingLeft,
            action = action
        )

        val code = _activeConnectionCode.value ?: return
        val animStr = when (action) {
            TwigiAction.WALK -> "walk"
            TwigiAction.SIT_COUCH -> "sit"
            TwigiAction.SLEEP_BED -> "sleep"
            TwigiAction.JAM_MUSIC -> "jam"
            TwigiAction.IDLE -> "idle"
        }
        // Send over WebSocket
        syncManager.sendCustomJson(
            connectionId = code,
            type = "nest_move",
            payload = mapOf(
                "x" to targetX,
                "y" to targetY,
                "anim" to animStr,
                "facingLeft" to facingLeft
            )
        )
    }

    fun sendEmote(emote: String) {
        val code = _activeConnectionCode.value ?: return
        val myName = bootstrapManager.memberIdentity.value?.displayName ?: "Me"
        _activeEmote.value = myName to emote
        syncManager.sendCustomJson(
            connectionId = code,
            type = "nest_emote",
            payload = mapOf(
                "actorName" to myName,
                "emote" to emote
            )
        )
    }

    fun updateWallpaper(wallpaperId: String) {
        val code = _activeConnectionCode.value ?: return
        viewModelScope.launch {
            nestRepository.updateDecor(connectionCode = code, wallpaper = wallpaperId)
        }
    }

    fun updateFlooring(flooringId: String) {
        val code = _activeConnectionCode.value ?: return
        viewModelScope.launch {
            nestRepository.updateDecor(connectionCode = code, flooring = flooringId)
        }
    }

    fun updateFurnitureLayout(items: List<FurnitureItem>) {
        val code = _activeConnectionCode.value ?: return
        viewModelScope.launch {
            nestRepository.updateDecor(connectionCode = code, furniture = items)
        }
    }

    fun addFridgeNote(text: String, drawingUrl: String? = null, color: String = "#FEF08A") {
        val code = _activeConnectionCode.value ?: return
        viewModelScope.launch {
            nestRepository.addFridgeNote(code, text, drawingUrl, color)
        }
    }

    fun deleteFridgeNote(noteId: String) {
        val code = _activeConnectionCode.value ?: return
        viewModelScope.launch {
            nestRepository.deleteFridgeNote(code, noteId)
        }
    }

    fun interactPet(action: String = "PET") {
        val code = _activeConnectionCode.value ?: return
        viewModelScope.launch {
            nestRepository.interactPet(code, action)
        }
    }

    fun updateNowPlayingBehavior(isPlayingMusic: Boolean) {
        val current = _myTwigiState.value
        if (isPlayingMusic && current.action != TwigiAction.JAM_MUSIC) {
            // Place by turntable
            moveMyTwigiTo(0.78f, 0.35f, TwigiAction.JAM_MUSIC)
        } else if (!isPlayingMusic && current.action == TwigiAction.JAM_MUSIC) {
            moveMyTwigiTo(current.x, current.y, TwigiAction.IDLE)
        }
    }

    private fun calculateTimeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..7 -> TimeOfDay.SUNRISE
            in 8..16 -> TimeOfDay.DAY
            in 17..19 -> TimeOfDay.SUNSET
            else -> TimeOfDay.NIGHT
        }
    }
}
