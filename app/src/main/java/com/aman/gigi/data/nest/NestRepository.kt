package com.aman.gigi.data.nest

import com.aman.gigi.BuildConfig
import com.aman.gigi.data.auth.SessionTokenProvider
import com.aman.gigi.data.client.ConnectionBootstrapManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NestRepository @Inject constructor(
    private val bootstrapManager: ConnectionBootstrapManager
) {
    private val httpBaseUrl = BuildConfig.SERVER_URL
        .replaceFirst("wss://", "https://")
        .replaceFirst("ws://", "http://")
        .trimEnd('/')

    private suspend fun token(force: Boolean = false): String? =
        SessionTokenProvider.current(bootstrapManager.memberIdentity.value?.authToken, force)

    private val _roomState = MutableStateFlow<NestRoomData?>(null)
    val roomState: StateFlow<NestRoomData?> = _roomState.asStateFlow()

    // Shared flows for incoming WebSocket emotes / moves
    private val _partnerMoveEvent = MutableSharedFlow<TwigiRoomPosition>(extraBufferCapacity = 64)
    val partnerMoveEvent: SharedFlow<TwigiRoomPosition> = _partnerMoveEvent.asSharedFlow()

    private val _partnerEmoteEvent = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 32)
    val partnerEmoteEvent: SharedFlow<Pair<String, String>> = _partnerEmoteEvent.asSharedFlow()

    suspend fun fetchRoom(connectionCode: String): Result<NestRoomData> = withContext(Dispatchers.IO) {
        runCatching {
            val t = token() ?: throw IllegalStateException("Not signed in")
            val resp = request("/api/nest/$connectionCode", "GET", null, t)
            val roomObj = resp.optJSONObject("room") ?: JSONObject()
            val room = NestRoomData.fromJson(roomObj)
            _roomState.value = room
            room
        }
    }

    suspend fun updateDecor(
        connectionCode: String,
        wallpaper: String? = null,
        flooring: String? = null,
        roomMood: String? = null,
        furniture: List<FurnitureItem>? = null
    ): Result<NestRoomData> = withContext(Dispatchers.IO) {
        runCatching {
            val t = token() ?: throw IllegalStateException("Not signed in")
            val body = JSONObject().apply {
                put("connectionCode", connectionCode)
                wallpaper?.let { put("wallpaper", it) }
                flooring?.let { put("flooring", it) }
                roomMood?.let { put("roomMood", it) }
                furniture?.let { list ->
                    val arr = JSONArray()
                    list.forEach { arr.put(it.toJson()) }
                    put("furniture", arr)
                }
            }
            val resp = request("/api/nest/decor", "POST", body, t)
            val roomObj = resp.optJSONObject("room") ?: JSONObject()
            val room = NestRoomData.fromJson(roomObj)
            _roomState.value = room
            room
        }
    }

    suspend fun addFridgeNote(
        connectionCode: String,
        text: String,
        drawingUrl: String? = null,
        color: String = "#FEF08A"
    ): Result<List<FridgeNote>> = withContext(Dispatchers.IO) {
        runCatching {
            val t = token() ?: throw IllegalStateException("Not signed in")
            val body = JSONObject().apply {
                put("connectionCode", connectionCode)
                put("action", "ADD")
                put("text", text)
                drawingUrl?.let { put("drawingUrl", it) }
                put("color", color)
            }
            val resp = request("/api/nest/notes", "POST", body, t)
            val nArr = resp.optJSONArray("fridgeNotes") ?: JSONArray()
            val nList = mutableListOf<FridgeNote>()
            for (i in 0 until nArr.length()) {
                nArr.optJSONObject(i)?.let { nList.add(FridgeNote.fromJson(it)) }
            }
            _roomState.value = _roomState.value?.copy(fridgeNotes = nList)
            nList
        }
    }

    suspend fun deleteFridgeNote(
        connectionCode: String,
        noteId: String
    ): Result<List<FridgeNote>> = withContext(Dispatchers.IO) {
        runCatching {
            val t = token() ?: throw IllegalStateException("Not signed in")
            val body = JSONObject().apply {
                put("connectionCode", connectionCode)
                put("action", "DELETE")
                put("noteId", noteId)
            }
            val resp = request("/api/nest/notes", "POST", body, t)
            val nArr = resp.optJSONArray("fridgeNotes") ?: JSONArray()
            val nList = mutableListOf<FridgeNote>()
            for (i in 0 until nArr.length()) {
                nArr.optJSONObject(i)?.let { nList.add(FridgeNote.fromJson(it)) }
            }
            _roomState.value = _roomState.value?.copy(fridgeNotes = nList)
            nList
        }
    }

    suspend fun interactPet(
        connectionCode: String,
        action: String = "PET"
    ): Result<PetState> = withContext(Dispatchers.IO) {
        runCatching {
            val t = token() ?: throw IllegalStateException("Not signed in")
            val body = JSONObject().apply {
                put("connectionCode", connectionCode)
                put("action", action)
            }
            val resp = request("/api/nest/pet/interact", "POST", body, t)
            val pet = PetState.fromJson(resp.optJSONObject("pet"))
            _roomState.value = _roomState.value?.copy(pet = pet)
            pet
        }
    }

    fun handleRemoteRoomUpdate(json: JSONObject) {
        val roomObj = json.optJSONObject("room")
        if (roomObj != null) {
            _roomState.value = NestRoomData.fromJson(roomObj)
        } else {
            val nArr = json.optJSONArray("fridgeNotes")
            if (nArr != null) {
                val nList = mutableListOf<FridgeNote>()
                for (i in 0 until nArr.length()) {
                    nArr.optJSONObject(i)?.let { nList.add(FridgeNote.fromJson(it)) }
                }
                _roomState.value = _roomState.value?.copy(fridgeNotes = nList)
            }
        }
    }

    fun handleRemotePartnerMove(pos: TwigiRoomPosition) {
        _partnerMoveEvent.tryEmit(pos)
    }

    fun handleRemotePartnerEmote(actor: String, emote: String) {
        _partnerEmoteEvent.tryEmit(actor to emote)
    }

    private suspend fun request(
        path: String,
        method: String,
        body: JSONObject?,
        sessionToken: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL("$httpBaseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-session-token", sessionToken)
        }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val ok = conn.responseCode in 200..299
        val payload = runCatching {
            (if (ok) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { JSONObject(it.readText()) }
        }.getOrNull() ?: JSONObject()
        if (!ok) {
            throw IllegalStateException(
                payload.optString("error").ifBlank { "Request failed with ${conn.responseCode}" }
            )
        }
        payload
    }
}
