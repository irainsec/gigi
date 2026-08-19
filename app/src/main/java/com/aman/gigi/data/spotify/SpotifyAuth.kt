package com.aman.gigi.data.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.aman.gigi.utils.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authorization Code flow with PKCE against Spotify's accounts service.
 *
 * PKCE rather than the classic code flow because there is no safe place to keep a
 * client secret in a shipped APK — anyone can unzip it. With PKCE the app proves it
 * started the exchange by producing the verifier that hashes to the challenge it sent,
 * so no secret ever has to exist on the device.
 *
 * The client ID arrives from the server ([AppConfig.settings]) rather than being baked
 * in, so it can be set — or rotated — from the admin panel without an app release.
 */
@Singleton
class SpotifyAuth @Inject constructor(
    private val tokens: SpotifyTokenStore
) {
    private val http = OkHttpClient()

    val clientId: String get() = AppConfig.settings.spotifyClientId.trim()

    /** Blank client ID means no Spotify app is configured yet — hide the whole surface. */
    val isConfigured: Boolean get() = clientId.isNotBlank()

    val isConnected: Boolean get() = tokens.hasRefreshToken()

    /**
     * Only what the features actually use. Asking for more than you need makes the
     * consent screen scarier and is the fastest way to fail a quota-extension review.
     */
    private val scopes = listOf(
        "user-read-private",
        "user-read-email",
        "playlist-read-private",
        "playlist-read-collaborative",
        "user-library-read",
        "user-read-playback-state",
        "user-modify-playback-state",
        "user-read-currently-playing"
    ).joinToString(" ")

    @Volatile private var pendingVerifier: String? = null

    // ── step 1: send them to Spotify ─────────────────────────────────────────

    fun beginLogin(context: Context) {
        if (!isConfigured) {
            Log.w(TAG, "beginLogin with no client ID configured")
            return
        }
        val verifier = randomVerifier()
        pendingVerifier = verifier

        val url = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challengeFor(verifier))
            .appendQueryParameter("scope", scopes)
            .build()

        // A Custom Tab rather than a WebView: it shares the system browser's cookies, so
        // an already-signed-in user just taps Agree, and the user can see the real
        // accounts.spotify.com URL bar. A WebView asking for a password is a phishing
        // pattern and Spotify's terms forbid it.
        runCatching {
            CustomTabsIntent.Builder().setShowTitle(true).build()
                .launchUrl(context, url)
        }.onFailure {
            // No browser that supports Custom Tabs — fall back to whatever can open it.
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { e -> Log.e(TAG, "no browser available: ${e.message}") }
        }
    }

    // ── step 2: Spotify sends them back to gigi://spotify-callback ───────────

    /** @return true when this intent was our callback and a session now exists. */
    suspend fun handleRedirect(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "gigi" || uri.host != "spotify-callback") return false

        uri.getQueryParameter("error")?.let {
            Log.w(TAG, "authorization declined: $it")
            pendingVerifier = null
            return false
        }
        val code = uri.getQueryParameter("code") ?: return false
        val verifier = pendingVerifier ?: run {
            // Process died between launching the browser and coming back.
            Log.w(TAG, "callback arrived with no verifier in memory — asking again")
            return false
        }
        pendingVerifier = null
        return exchangeCode(code, verifier)
    }

    private suspend fun exchangeCode(code: String, verifier: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("client_id", clientId)
                .add("code_verifier", verifier)
                .build()
            postToken(body)
        }

    // ── keeping the session alive ────────────────────────────────────────────

    /**
     * A valid access token, refreshing if the cached one is expired or nearly so.
     *
     * @return null when not connected, or when the refresh token has been revoked —
     *         in which case the stored session is cleared so the UI can offer Connect
     *         again rather than failing silently forever.
     */
    suspend fun accessToken(): String? {
        tokens.validAccessToken()?.let { return it }
        val refresh = tokens.refreshToken() ?: return null

        return withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("client_id", clientId)
                .build()
            if (postToken(body)) tokens.validAccessToken() else null
        }
    }

    private fun postToken(body: FormBody): Boolean {
        return try {
            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(body)
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "token endpoint ${response.code}: ${text.take(200)}")
                    // 400 invalid_grant means the refresh token is dead — revoked by the
                    // user, or expired. Retrying forever would be pointless.
                    if (response.code == 400) tokens.clear()
                    return false
                }
                val json = JSONObject(text)
                tokens.save(
                    accessToken = json.getString("access_token"),
                    expiresInSeconds = json.optInt("expires_in", 3600),
                    // Refresh is only returned on the initial exchange, and sometimes on
                    // rotation. Keep the existing one when it isn't.
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
                )
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "token request failed: ${e.message}")
            false
        }
    }

    fun disconnect() {
        tokens.clear()
        pendingVerifier = null
    }

    // ── PKCE primitives ──────────────────────────────────────────────────────

    private fun randomVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, B64)
    }

    private fun challengeFor(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, B64)
    }

    companion object {
        const val REDIRECT_URI = "gigi://spotify-callback"
        private const val TAG = "SpotifyAuth"
        // RFC 7636 requires base64url with no padding and no line wrapping.
        private const val B64 = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    }
}
