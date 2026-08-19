package com.aman.gigi.repository

import com.aman.gigi.BuildConfig
import com.aman.gigi.data.auth.SessionTokenProvider
import com.aman.gigi.data.client.ConnectionBootstrapManager
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A nearby "I'm doing this, come along" post. */
data class LivePost(
    val postId: String,
    val authorMemberId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    val category: String,
    val mood: String?,
    val lat: Double?,
    val lng: Double?,
    /** True once we're the host or an accepted joiner — until then the pin is fuzzed. */
    val preciseLocation: Boolean,
    val placeLabel: String?,
    val radiusM: Int,
    val status: String,
    val acceptedCount: Int,
    val maxJoiners: Int?,
    val isMine: Boolean,
    val distanceM: Int?,
    val expiresAt: Long?,
    /** True once the host's headcount is reached — nobody else can join. */
    val isFull: Boolean = false,
    /** "done" / "expired" / "cancelled", or null while it's still live. */
    val endedReason: String? = null,
    val createdAt: Long? = null,
    val participants: List<LiveParticipant> = emptyList()
)

/** Someone in a meet-up: the host, or an accepted joiner. */
data class LiveParticipant(
    val memberId: String,
    val name: String,
    val avatarUrl: String?,
    val isHost: Boolean = false
)

/** Where a participant is right now, as pushed over the socket. */
data class LivePresence(
    val memberId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val heading: Float?,
    val at: Long,
    val avatarUrl: String? = null
)

/**
 * Talks to the /api/live endpoints. Mirrors [SharedAlarmRepository]'s HttpURLConnection style
 * rather than pulling in a client the rest of the app doesn't use.
 */
@Singleton
class LiveRepository @Inject constructor(
    private val bootstrapManager: ConnectionBootstrapManager
) {
    private val httpBaseUrl = run {
        val wsUri = URI(BuildConfig.SERVER_URL)
        val scheme = if (wsUri.scheme.equals("wss", ignoreCase = true)) "https" else "http"
        URI(scheme, wsUri.userInfo, wsUri.host, if (wsUri.port == -1) -1 else wsUri.port, null, null, null)
            .toString().trimEnd('/')
    }

    /**
     * Always resolved fresh — a cached Firebase ID token is only valid for an hour,
     * which is what made Live start 401ing after a while.
     */
    private suspend fun token(force: Boolean = false): String? =
        SessionTokenProvider.current(bootstrapManager.memberIdentity.value?.authToken, force)

    suspend fun nearby(lat: Double, lng: Double): List<LivePost> {
        val t = token() ?: return emptyList()
        val q = "lat=${enc(lat.toString())}&lng=${enc(lng.toString())}"
        val json = request("/api/live/posts?$q", "GET", null, t)
        val arr = json.optJSONArray("posts") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parsePost(arr.optJSONObject(it)) }
    }

    suspend fun createPost(
        text: String,
        category: String,
        mood: String?,
        lat: Double,
        lng: Double,
        radiusM: Int,
        durationMin: Int,
        visibility: String,
        placeLabel: String?,
        maxJoiners: Int?
    ): LivePost? {
        val t = token() ?: return null
        val body = JSONObject().apply {
            put("text", text); put("category", category)
            mood?.let { put("mood", it) }
            put("lat", lat); put("lng", lng)
            put("radiusM", radiusM); put("durationMin", durationMin)
            put("visibility", visibility)
            placeLabel?.let { put("placeLabel", it) }
            maxJoiners?.let { put("maxJoiners", it) }
        }
        return parsePost(request("/api/live/posts", "POST", body, t).optJSONObject("post"))
    }

    suspend fun requestJoin(postId: String, note: String?): Boolean {
        val t = token() ?: return false
        val body = JSONObject().apply { note?.let { put("note", it) } }
        return request("/api/live/posts/$postId/join", "POST", body, t).optBoolean("ok")
    }

    /** Host side: accepting is what unlocks precise location for that member. */
    suspend fun respondToJoin(postId: String, memberId: String, accept: Boolean): Boolean {
        val t = token() ?: return false
        val body = JSONObject().apply { put("memberId", memberId); put("accept", accept) }
        return request("/api/live/posts/$postId/respond", "POST", body, t).optBoolean("ok")
    }

    /** Returns false when the server says the meet-up is over, so the service can stop. */
    suspend fun pushLocation(
        postId: String, lat: Double, lng: Double, heading: Float?, speed: Float?, battery: Int?
    ): Boolean {
        val t = token() ?: return false
        val body = JSONObject().apply {
            put("postId", postId); put("lat", lat); put("lng", lng)
            heading?.let { put("heading", it) }
            speed?.let { put("speed", it) }
            battery?.let { put("battery", it) }
        }
        return runCatching { request("/api/live/track", "POST", body, t).optBoolean("ok") }
            .getOrDefault(false)
    }

    /** Everything I've posted, newest first — powers the Live history sheet. */
    suspend fun myPosts(): List<LivePost> {
        val t = token() ?: return emptyList()
        val json = request("/api/live/mine", "GET", null, t)
        val arr = json.optJSONArray("posts") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parsePost(arr.optJSONObject(it)) }
    }

    suspend fun deletePost(postId: String): Boolean {
        val t = token() ?: return false
        return request("/api/live/posts/$postId", "DELETE", null, t).optBoolean("ok")
    }

    suspend fun markDone(postId: String): Boolean {
        val t = token() ?: return false
        return request("/api/live/posts/$postId/done", "POST", JSONObject(), t).optBoolean("ok")
    }

    suspend fun leave(postId: String): Boolean {
        val t = token() ?: return false
        return request("/api/live/posts/$postId/leave", "POST", JSONObject(), t).optBoolean("ok")
    }

    private fun parsePost(o: JSONObject?): LivePost? {
        if (o == null) return null
        return LivePost(
            postId = o.optString("postId"),
            authorMemberId = o.optString("authorMemberId"),
            authorName = o.optString("authorName", "Someone"),
            authorAvatarUrl = o.optString("authorAvatarUrl").takeIf { it.isNotBlank() && it != "null" },
            text = o.optString("text"),
            category = o.optString("category", "other"),
            mood = o.optString("mood").takeIf { it.isNotBlank() && it != "null" },
            lat = if (o.isNull("lat")) null else o.optDouble("lat"),
            lng = if (o.isNull("lng")) null else o.optDouble("lng"),
            preciseLocation = o.optBoolean("preciseLocation"),
            placeLabel = o.optString("placeLabel").takeIf { it.isNotBlank() && it != "null" },
            radiusM = o.optInt("radiusM", 500),
            status = o.optString("status", "OPEN"),
            acceptedCount = o.optInt("acceptedCount"),
            maxJoiners = o.optInt("maxJoiners").takeIf { it > 0 },
            isMine = o.optBoolean("isMine"),
            distanceM = if (o.isNull("distanceM")) null else o.optInt("distanceM"),
            expiresAt = parseIso(o.optString("expiresAt")),
            isFull = o.optBoolean("isFull"),
            endedReason = o.optString("endedReason").takeIf { it.isNotBlank() && it != "null" },
            createdAt = parseIso(o.optString("createdAt")),
            participants = o.optJSONArray("participants")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { pj ->
                        LiveParticipant(
                            memberId = pj.optString("memberId"),
                            name = pj.optString("name", "Someone"),
                            avatarUrl = pj.optString("avatarUrl")
                                .takeIf { it.isNotBlank() && it != "null" },
                            isHost = pj.optBoolean("isHost")
                        )
                    }
                }
            } ?: emptyList()
        )
    }

    private fun parseIso(value: String?): Long? = runCatching {
        if (value.isNullOrBlank()) null
        else java.time.Instant.parse(value).toEpochMilli()
    }.getOrNull()

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    /**
     * A 401 usually means the ID token went stale between minting and arrival, so it's
     * worth one forced refresh before surfacing "session expired" to someone who is, in
     * fact, perfectly signed in.
     */
    private suspend fun request(
        path: String, method: String, body: JSONObject?, sessionToken: String
    ): JSONObject {
        return try {
            attempt(path, method, body, sessionToken)
        } catch (e: UnauthorizedException) {
            val fresh = token(force = true)
                ?: throw IllegalStateException("Session expired. Please sign in again.")
            attempt(path, method, body, fresh)
        }
    }

    private class UnauthorizedException : Exception("unauthorized")

    private suspend fun attempt(
        path: String, method: String, body: JSONObject?, sessionToken: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("$httpBaseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-session-token", sessionToken)
        }
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val ok = connection.responseCode in 200..299
        val payload = runCatching {
            (if (ok) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { JSONObject(it.readText()) }
        }.getOrNull() ?: JSONObject()
        if (connection.responseCode == 401) throw UnauthorizedException()
        if (!ok) {
            throw IllegalStateException(
                payload.optString("error").ifBlank { "Request failed with ${connection.responseCode}" }
            )
        }
        payload
    }
}
