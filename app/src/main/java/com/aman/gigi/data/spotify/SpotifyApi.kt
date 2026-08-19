package com.aman.gigi.data.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val trackCount: Int,
    val owner: String,
    val uri: String
)

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String?,
    val durationMs: Long,
    val uri: String
)

/** Why a call failed, in terms the UI can act on rather than a bare exception. */
sealed class SpotifyError {
    object NotConnected : SpotifyError()
    /** Spotify blocks the whole Web API for apps owned by non-Premium accounts. */
    object PremiumRequired : SpotifyError()
    /** No Spotify device is awake, so playback can't be targeted at anything. */
    object NoActiveDevice : SpotifyError()
    data class Http(val code: Int, val message: String) : SpotifyError()
}

@Singleton
class SpotifyApi @Inject constructor(
    private val auth: SpotifyAuth
) {
    private val http = OkHttpClient()

    // ── reads ────────────────────────────────────────────────────────────────

    suspend fun playlists(limit: Int = 50): Result<List<SpotifyPlaylist>> =
        get("me/playlists?limit=$limit").map { json ->
            json.optJSONArray("items").orEmptyList().mapNotNull { item ->
                runCatching {
                    SpotifyPlaylist(
                        id = item.getString("id"),
                        name = item.optString("name", "Untitled"),
                        imageUrl = item.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                        trackCount = item.optJSONObject("tracks")?.optInt("total") ?: 0,
                        owner = item.optJSONObject("owner")?.optString("display_name").orEmpty(),
                        uri = item.optString("uri")
                    )
                }.getOrNull()
            }
        }

    suspend fun playlistTracks(playlistId: String, limit: Int = 100): Result<List<SpotifyTrack>> =
        get("playlists/$playlistId/tracks?limit=$limit").map { json ->
            json.optJSONArray("items").orEmptyList().mapNotNull { item ->
                item.optJSONObject("track")?.let(::parseTrack)
            }
        }

    suspend fun savedTracks(limit: Int = 50): Result<List<SpotifyTrack>> =
        get("me/tracks?limit=$limit").map { json ->
            json.optJSONArray("items").orEmptyList().mapNotNull { item ->
                item.optJSONObject("track")?.let(::parseTrack)
            }
        }

    suspend fun search(query: String, limit: Int = 20): Result<List<SpotifyTrack>> {
        val encoded = Uri.encode(query)
        return get("search?q=$encoded&type=track&limit=$limit").map { json ->
            json.optJSONObject("tracks")?.optJSONArray("items").orEmptyList()
                .mapNotNull(::parseTrack)
        }
    }

    // ── playback ─────────────────────────────────────────────────────────────

    /**
     * Starts a track on the user's active Spotify device.
     *
     * Premium only, and it needs a device that Spotify considers awake — if the app
     * hasn't been opened recently there is nothing to target and this returns
     * [SpotifyError.NoActiveDevice]. Callers should fall back to [openInSpotify],
     * which always works because it just hands the URI to the Spotify app.
     */
    suspend fun play(trackUri: String): Result<Unit> {
        val token = auth.accessToken() ?: return Result.failure(SpotifyException(SpotifyError.NotConnected))
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("uris", listOf(trackUri).toJsonArray())
                    .toString().toRequestBody(JSON)
                val request = Request.Builder()
                    .url("$BASE/me/player/play")
                    .put(body)
                    .header("Authorization", "Bearer $token")
                    .build()
                http.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> Unit
                        response.code == 404 -> throw SpotifyException(SpotifyError.NoActiveDevice)
                        response.code == 403 -> throw SpotifyException(SpotifyError.PremiumRequired)
                        else -> throw SpotifyException(
                            SpotifyError.Http(response.code, response.message)
                        )
                    }
                }
            }
        }
    }

    /**
     * Hands the URI to the Spotify app via a plain intent.
     *
     * The dependable path: no API, no token, no Premium, works when [play] can't find a
     * device. The cost is that Spotify comes to the foreground for a moment.
     */
    fun openInSpotify(context: Context, uri: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage("com.spotify.music")
        )
        true
    }.recoverCatching {
        // Spotify not installed — fall back to the web player.
        val web = uri.removePrefix("spotify:").replace(":", "/")
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/$web"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    // ── plumbing ─────────────────────────────────────────────────────────────

    private suspend fun get(path: String): Result<JSONObject> {
        val token = auth.accessToken()
            ?: return Result.failure(SpotifyException(SpotifyError.NotConnected))

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$BASE/$path")
                    .header("Authorization", "Bearer $token")
                    .build()
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.w(TAG, "GET $path -> ${response.code}: ${text.take(180)}")
                        throw SpotifyException(
                            when (response.code) {
                                // Spotify returns 403 for the whole Web API when the
                                // developer account isn't Premium, not just for playback.
                                403 -> SpotifyError.PremiumRequired
                                401 -> SpotifyError.NotConnected
                                else -> SpotifyError.Http(response.code, response.message)
                            }
                        )
                    }
                    JSONObject(text)
                }
            }
        }
    }

    private fun parseTrack(t: JSONObject): SpotifyTrack? = runCatching {
        SpotifyTrack(
            id = t.getString("id"),
            title = t.optString("name", "Untitled"),
            artist = t.optJSONArray("artists").orEmptyList()
                .joinToString(", ") { it.optString("name") },
            album = t.optJSONObject("album")?.optString("name").orEmpty(),
            imageUrl = t.optJSONObject("album")?.optJSONArray("images")
                ?.optJSONObject(0)?.optString("url"),
            durationMs = t.optLong("duration_ms"),
            uri = t.optString("uri")
        )
    }.getOrNull()

    private companion object {
        const val TAG = "SpotifyApi"
        const val BASE = "https://api.spotify.com/v1"
        val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}

class SpotifyException(val error: SpotifyError) : Exception(error.toString())

// ── small JSON helpers, kept local so nothing else has to care ───────────────

private fun org.json.JSONArray?.orEmptyList(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

private fun List<String>.toJsonArray(): org.json.JSONArray =
    org.json.JSONArray().also { arr -> forEach { arr.put(it) } }
