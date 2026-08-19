package com.aman.gigi.data.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Hands out a session token that is actually still valid.
 *
 * The server authenticates REST calls by passing the token to Firebase's
 * `verifyIdToken()`, and **Firebase ID tokens expire after one hour**. The app used to
 * send the token captured at sign-in and never refresh it, so an hour after signing in
 * every authenticated endpoint began returning "Session expired. Please sign in again."
 *
 * `getIdToken(false)` normally returns a cached token and mints a new one near expiry,
 * so it's cheap per request. But that isn't always enough — if the cached token is
 * rejected for any other reason (clock skew, a revoked refresh token, a member row whose
 * id no longer matches the Firebase uid) the caller needs to be able to demand a genuinely
 * fresh one, which is what [forceRefresh] is for.
 */
object SessionTokenProvider {
    private const val TAG = "SessionToken"

    /** @param force skip Firebase's cache and mint a new ID token. */
    suspend fun current(storedToken: String?, force: Boolean = false): String? {
        firebaseToken(force)?.let {
            if (force) Log.i(TAG, "using freshly minted Firebase ID token")
            return it
        }
        // Members who signed in by OTP have no Firebase user; the server accepts their
        // stored opaque token via its own AuthSession table.
        Log.w(TAG, "no Firebase user — falling back to the stored token")
        return storedToken?.takeIf { it.isNotBlank() }
    }

    private suspend fun firebaseToken(force: Boolean): String? {
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        if (user == null) return null
        return suspendCancellableCoroutine { cont ->
            runCatching {
                user.getIdToken(force)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result.token?.takeIf { it.isNotBlank() })
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "getIdToken(force=$force) failed: ${e.message}")
                        if (cont.isActive) cont.resume(null)
                    }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }
}
