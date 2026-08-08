package com.aman.gigi.data.auth

import android.app.Activity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Result of a successful Google + Firebase sign-in.
 * The [firebaseIdToken] should be sent to your server for verification.
 */
data class FirebaseTokenResult(
    val firebaseIdToken: String,
    val email: String?,
    val displayName: String?,
    val firebaseUid: String
)

@Singleton
class FirebaseAuthenticator @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        data class Error(val message: String) : AuthState()
        object Verified : AuthState()
    }

    /**
     * Build a [GoogleSignInClient] configured to request an ID token.
     * Call this from the Activity/composable that owns the sign-in launcher.
     *
     * @param activity The current activity (needed for GoogleSignIn context).
     * @param webClientId The OAuth web-client-id from Firebase Console.
     *                    Typically R.string.default_web_client_id generated from google-services.json.
     */
    fun buildGoogleSignInClient(activity: Activity, webClientId: String): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(activity, gso)
    }

    /**
     * Sign in to Firebase using the Google ID token obtained from [GoogleSignInAccount.idToken].
     * This exchanges the Google token for a Firebase session, then fetches a Firebase ID token
     * that your server can verify via Firebase Admin SDK.
     *
     * @param googleIdToken The token from [GoogleSignInAccount.idToken].
     * @return [FirebaseTokenResult] on success, throws on failure.
     */
    suspend fun signInWithGoogleCredential(googleIdToken: String): FirebaseTokenResult {
        _authState.value = AuthState.Loading
        return suspendCancellableCoroutine { cont ->
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user == null) {
                        _authState.value = AuthState.Error("No Firebase user after sign-in")
                        cont.cancel(IllegalStateException("No Firebase user after sign-in"))
                        return@addOnSuccessListener
                    }
                    // Fetch a fresh Firebase ID token to send to our server
                    user.getIdToken(true)
                        .addOnSuccessListener { tokenResult ->
                            val idToken = tokenResult.token
                            if (idToken.isNullOrBlank()) {
                                _authState.value = AuthState.Error("Could not get Firebase ID token")
                                cont.cancel(IllegalStateException("Firebase ID token was empty"))
                            } else {
                                _authState.value = AuthState.Verified
                                cont.resume(
                                    FirebaseTokenResult(
                                        firebaseIdToken = idToken,
                                        email = user.email,
                                        displayName = user.displayName,
                                        firebaseUid = user.uid
                                    )
                                )
                            }
                        }
                        .addOnFailureListener { e ->
                            _authState.value = AuthState.Error(e.localizedMessage ?: "Token fetch failed")
                            cont.cancel(e)
                        }
                }
                .addOnFailureListener { e ->
                    _authState.value = AuthState.Error(e.localizedMessage ?: "Google sign-in failed")
                    cont.cancel(e)
                }
        }
    }

    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    fun signOut() {
        auth.signOut()
        _authState.value = AuthState.Idle
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }
}
