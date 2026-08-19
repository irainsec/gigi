package com.aman.gigi.data.spotify

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the Spotify session across restarts.
 *
 * The refresh token is a long-lived credential — Spotify's dashboard shows a 180-day
 * lifetime — so it is encrypted at rest with an AES key held in the Android Keystore,
 * where the key material cannot be read out of the app's data even on a rooted device.
 *
 * Deliberately hand-rolled on Keystore rather than androidx.security-crypto: that
 * library pulls in Tink and costs the better part of a megabyte, and this is one string.
 * The access token is short-lived and kept only in memory.
 */
@Singleton
class SpotifyTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gigi_spotify", Context.MODE_PRIVATE)

    @Volatile private var accessToken: String? = null
    @Volatile private var expiresAtMs: Long = 0L

    fun hasRefreshToken(): Boolean = refreshToken() != null

    /** The cached access token, or null when absent or close enough to expiry to refresh. */
    fun validAccessToken(): String? {
        val token = accessToken ?: return null
        // 60s of headroom: a token that expires mid-flight fails the request it was
        // fetched for, which is worse than refreshing slightly early.
        return if (System.currentTimeMillis() < expiresAtMs - 60_000) token else null
    }

    fun save(accessToken: String, expiresInSeconds: Int, refreshToken: String?) {
        this.accessToken = accessToken
        this.expiresAtMs = System.currentTimeMillis() + expiresInSeconds * 1000L
        if (refreshToken != null) {
            runCatching { prefs.edit().putString(KEY_REFRESH, encrypt(refreshToken)).apply() }
                .onFailure { Log.e(TAG, "could not store refresh token: ${it.message}") }
        }
    }

    fun refreshToken(): String? {
        val stored = prefs.getString(KEY_REFRESH, null) ?: return null
        return runCatching { decrypt(stored) }.getOrElse {
            // Keystore keys are invalidated by things like a factory reset or, on some
            // devices, a lock-screen change. An undecryptable blob is dead weight.
            Log.w(TAG, "refresh token unreadable (${it.message}) — clearing")
            clear()
            null
        }
    }

    fun clear() {
        accessToken = null
        expiresAtMs = 0L
        prefs.edit().remove(KEY_REFRESH).apply()
    }

    // ── Keystore-backed AES/GCM ──────────────────────────────────────────────

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.doFinal(plain.toByteArray())
        // GCM generates its own IV, and it is needed to decrypt — store it alongside.
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val (ivPart, dataPart) = stored.split(":", limit = 2).let { it[0] to it[1] }
        val iv = Base64.decode(ivPart, Base64.NO_WRAP)
        val data = Base64.decode(dataPart, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        }
        return String(cipher.doFinal(data))
    }

    private companion object {
        const val TAG = "SpotifyTokenStore"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_ALIAS = "gigi_spotify_refresh"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
