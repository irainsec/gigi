package com.aman.gigi.data.client

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.aman.gigi.model.Connection
import com.aman.gigi.model.ConnectionRole
import com.aman.gigi.model.MemberIdentity
import com.aman.gigi.model.PartnerPresence
import com.aman.gigi.model.ServerMode
import com.aman.gigi.model.ServerStatus
import com.aman.gigi.model.BreakCardConfig
import com.aman.gigi.model.TransportState
import com.aman.gigi.repository.ConnectionRepository
import com.aman.gigi.repository.LoveCardRepository
import com.aman.gigi.repository.SharedAlarmRepository
import com.aman.gigi.repository.ScribbleRepository
import com.aman.gigi.repository.OutboundActionRepository
import com.aman.gigi.service.ScreensaverSyncService
import com.aman.gigi.utils.NetworkMonitor
import com.aman.gigi.data.auth.FirebaseAuthenticator
import com.aman.gigi.data.sync.FirestoreSyncManager
import com.aman.gigi.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class ConnectionBootstrapManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionRepository: ConnectionRepository,
    private val sharedAlarmRepository: SharedAlarmRepository,
    private val loveCardRepository: LoveCardRepository,
    private val identityStore: ClientIdentityStore,
    private val networkMonitor: NetworkMonitor,
    private val firebaseAuthenticator: FirebaseAuthenticator,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val scribbleRepository: ScribbleRepository,
    private val outboundActionRepository: OutboundActionRepository,
    private val chatRepository: com.aman.gigi.repository.ChatRepository
) {
    private fun sanitizeOptionalText(value: String?): String? =
        value
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    private fun resolveServerAssetUrl(value: String?): String? {
        val sanitized = sanitizeOptionalText(value) ?: return null
        return when {
            sanitized.startsWith("http://", ignoreCase = true) ||
                sanitized.startsWith("https://", ignoreCase = true) -> sanitized
            sanitized.startsWith("/") -> "$httpBaseUrl$sanitized"
            else -> sanitized
        }
    }

    private fun isAuthenticated(identity: MemberIdentity?): Boolean =
        !sanitizeOptionalText(identity?.phoneNumber).isNullOrBlank() ||
        !sanitizeOptionalText(identity?.googleEmail).isNullOrBlank()

    @Deprecated("Use isAuthenticated", ReplaceWith("isAuthenticated(identity)"))
    private fun isPhoneAuthenticated(identity: MemberIdentity?): Boolean =
        isAuthenticated(identity)

    fun isProfileComplete(identity: MemberIdentity?): Boolean =
        identity?.profileComplete == true

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val bootstrapMutex = Mutex()
    private var currentBootstrapJob: Job? = null
    private val tag = "BootstrapManager"
    private val httpBaseUrl = Constants.SERVER_URL
        .replaceFirst("wss://", "https://")
        .replaceFirst("ws://", "http://")
        .trimEnd('/')

    private var started = false

    private val _serverStatus = MutableStateFlow(
        ServerStatus(
            mode = ServerMode.OFFLINE,
            message = "Checking sync server",
            lastCheckedAt = 0L
        )
    )
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _memberIdentity = MutableStateFlow<MemberIdentity?>(null)
    val memberIdentity: StateFlow<MemberIdentity?> = _memberIdentity.asStateFlow()

    private val _isAuthBusy = MutableStateFlow(false)
    val isAuthBusy: StateFlow<Boolean> = _isAuthBusy.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _pendingPhoneNumber = MutableStateFlow<String?>(null)
    val pendingPhoneNumber: StateFlow<String?> = _pendingPhoneNumber.asStateFlow()

    private val _devOtpHint = MutableStateFlow<String?>(null)
    val devOtpHint: StateFlow<String?> = _devOtpHint.asStateFlow()
    private val _breakCards = MutableStateFlow<List<BreakCardConfig>>(emptyList())
    val breakCards: StateFlow<List<BreakCardConfig>> = _breakCards.asStateFlow()

    val selectedSweetConnectionId: Flow<String?> = identityStore.selectedSweetConnectionId

    private val deviceId: String by lazy {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
    }

    private val deviceName: String by lazy {
        "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    fun start() {
        if (started) return
        started = true

        startPrefsSync()

        currentBootstrapJob = scope.launch {
            identityStore.memberIdentity.collectLatest { identity ->
                _memberIdentity.value = identity
            }
        }

        scope.launch {
            networkMonitor.isOnline.collectLatest { isOnline ->
                if (!isOnline) {
                    Log.w(tag, "No internet connection. Marking sync server offline.")
                    updateServerStatus(
                        ServerStatus(
                            mode = ServerMode.OFFLINE,
                            message = "No internet connection",
                            lastCheckedAt = System.currentTimeMillis()
                        )
                    )
                    stopSyncService()
                } else {
                    bootstrapNow(reason = "network_online")
                }
            }
        }

        scope.launch {
            bootstrapNow(reason = "app_start")
        }
    }

    fun onAppForegrounded() {
        scope.launch {
            bootstrapNow(reason = "foreground")
        }
    }

    fun refreshFromServer(reason: String = "manual_refresh") {
        scope.launch {
            bootstrapNow(reason = reason)
        }
    }

    fun requestOtp(phoneNumber: String, activity: android.app.Activity) {
        scope.launch {
            val normalized = normalizePhoneNumber(phoneNumber)
            if (normalized.isBlank()) {
                _authError.value = "Enter a valid phone number."
                return@launch
            }

            if (!(networkMonitor.isOnline.first())) {
                _authError.value = "No internet connection."
                return@launch
            }

            _isAuthBusy.value = true
            _authError.value = null
            _devOtpHint.value = null

            try {
                val payload = JSONObject().apply {
                    put("phoneNumber", normalized)
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                }

                val result = requestJson("/api/auth/request-otp", "POST", payload)
                val json = result.json
                if (json != null && json.optBoolean("ok")) {
                    _pendingPhoneNumber.value = normalized
                    _devOtpHint.value = sanitizeOptionalText(json.optString("devOtp"))
                    _authError.value = null
                } else {
                    _authError.value = json?.optString("error") ?: "Request failed (${result.code})"
                }
            } catch (e: Exception) {
                _authError.value = "Server error: ${e.message}"
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    fun verifyOtp(otp: String) {
        val phone = _pendingPhoneNumber.value
        Log.i(tag, "🔑 Verify OTP requested. Phone: $phone, OTP length: ${otp.length}")
        
        scope.launch {
            val normalizedOtp = otp.trim().filter { it.isDigit() }
            if (normalizedOtp.length != 6) {
                _authError.value = "Enter the 6-digit OTP."
                return@launch
            }

            val phone = _pendingPhoneNumber.value
            if (phone == null) {
                _authError.value = "Phone number missing. Request OTP again."
                return@launch
            }

            _isAuthBusy.value = true
            _authError.value = null

            try {
                val payload = JSONObject().apply {
                    put("phoneNumber", phone)
                    put("otp", normalizedOtp)
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                }

                val result = requestJson("/api/auth/verify-otp", "POST", payload)
                val json = result.json
                
                // Be more lenient: as long as we have a member identity OR an error message, we process it.
                // The server's buildBootstrapResponse usually nests the authToken inside the memberIdentity.
                if (json != null && (json.has("memberIdentity") || json.has("authToken"))) {
                    Log.i(tag, "✅ OTP Verified successfully. Applying bootstrap state.")
                    _devOtpHint.value = null
                    applyBootstrapResponse(json, authoritativeEmptyAllowed = true, isAuthoritativeLogin = true)
                } else {
                    val errorMsg = json?.optString("error") ?: "Invalid OTP (${result.code})"
                    Log.w(tag, "❌ OTP Verification failed: $errorMsg")
                    _authError.value = errorMsg
                }
            } catch (e: Exception) {
                _authError.value = "Verification failed: ${e.message}"
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    fun completeProfile(
        displayName: String,
        gender: String,
        avatarUri: Uri? = null,
        themeSongTitle: String? = null,
        themeSongUrl: String? = null,
        emoji: String = "🌻",
        dateOfBirth: String? = null
    ) {
        scope.launch {
            val identity = _memberIdentity.value
            if (identity?.authToken.isNullOrBlank()) {
                _authError.value = "Sign in first."
                return@launch
            }

            val cleanName = sanitizeOptionalText(displayName)
            val cleanGender = sanitizeOptionalText(gender)?.lowercase()
            val cleanSongTitle = sanitizeOptionalText(themeSongTitle)
            val cleanSongUrl = sanitizeOptionalText(themeSongUrl)
            val cleanDob = sanitizeOptionalText(dateOfBirth)
            if (cleanName.isNullOrBlank()) {
                _authError.value = "Enter your name."
                return@launch
            }
            if (cleanGender.isNullOrBlank()) {
                _authError.value = "Choose how you want Gigi to greet you."
                return@launch
            }

            bootstrapMutex.withLock {
                _isAuthBusy.value = true
                try {
                    val avatarPayload = avatarUri?.let { encodeAvatar(it) }
                    val response = requestJson(
                        path = "/api/auth/profile",
                        method = "POST",
                        body = JSONObject().apply {
                            put("sessionToken", identity!!.authToken)
                            put("displayName", cleanName)
                            put("gender", cleanGender)
                            put("themeSongTitle", cleanSongTitle)
                            put("themeSongUrl", cleanSongUrl)
                            put("emoji", emoji)
                            cleanDob?.let { put("dateOfBirth", it) }
                            avatarPayload?.first?.let { put("avatarBase64", it) }
                            avatarPayload?.second?.let { put("avatarMimeType", it) }
                        }
                    )

                    if (response.code in 200..299 && response.json != null) {
                        response.json.optJSONObject("memberIdentity")?.let { identityJson ->
                            parseMemberIdentity(identityJson)?.let { parsed ->
                                identityStore.saveIdentity(parsed)
                                _memberIdentity.value = parsed
                                // Upload to Firestore for real-time sync
                                scope.launch {
                                    firestoreSyncManager.uploadProfile(parsed)
                                }
                            }
                        }
                        _authError.value = null
                        bootstrapNow(reason = "profile_completed", authoritative = true)
                    } else {
                        _authError.value = response.json?.optString("error")
                            ?.takeIf { it.isNotBlank() }
                            ?: "Could not save your profile."
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to complete profile", e)
                    _authError.value = "Could not save your profile."
                } finally {
                    _isAuthBusy.value = false
                }
            }
        }
    }

    fun updateProfile(
        displayName: String? = null,
        gender: String? = null,
        emoji: String? = null
    ) {
        scope.launch {
            val identity = _memberIdentity.value ?: return@launch
            try {
                val response = requestJson(
                    path = "/api/auth/profile",
                    method = "POST",
                    body = JSONObject().apply {
                        put("sessionToken", identity.authToken)
                        put("displayName", displayName ?: identity.displayName)
                        put("gender", gender ?: identity.gender)
                        put("emoji", emoji ?: identity.emoji)
                    }
                )
                if (response.code in 200..299 && response.json != null) {
                    response.json.optJSONObject("memberIdentity")?.let { identityJson ->
                        parseMemberIdentity(identityJson)?.let { parsed ->
                            identityStore.saveIdentity(parsed)
                            _memberIdentity.value = parsed
                            // Upload to Firestore for real-time sync
                            scope.launch {
                                firestoreSyncManager.uploadProfile(parsed)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to update profile", e)
            }
        }
    }

    /**
     * Sign in using a Google ID token obtained from the Google Sign-In flow.
     * The token is exchanged with Firebase (via [FirebaseAuthenticator]) to get
     * a Firebase ID token, which is then sent to the Gigi server.
     *
     * @param googleIdToken  The ID token from [GoogleSignInAccount.getIdToken].
     */
    fun signInWithGoogle(googleIdToken: String) {
        scope.launch {
            if (!(networkMonitor.isOnline.first())) {
                _authError.value = "No internet connection."
                return@launch
            }
            _isAuthBusy.value = true
            _authError.value = null

            try {
                // Step 1: Verify with Firebase → get Firebase ID token + user info
                val fbResult = firebaseAuthenticator.signInWithGoogleCredential(googleIdToken)

                // Step 2: Send Firebase ID token to our server
                val payload = JSONObject().apply {
                    put("firebaseIdToken", fbResult.firebaseIdToken)
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                    fbResult.email?.let { put("googleEmail", it) }
                    fbResult.displayName?.let { put("googleDisplayName", it) }
                }

                val result = requestJson("/api/auth/google-signin", "POST", payload)
                val json = result.json

                if (json != null && (json.has("memberIdentity") || json.has("authToken"))) {
                    Log.i(tag, "✅ Google Sign-In verified. Applying bootstrap state.")
                    applyBootstrapResponse(json, authoritativeEmptyAllowed = true, isAuthoritativeLogin = true)
                } else {
                    val errorMsg = json?.optString("error") ?: "Google sign-in failed (${result.code})"
                    Log.w(tag, "❌ Google Sign-In failed: $errorMsg")
                    _authError.value = errorMsg
                }
            } catch (e: Exception) {
                Log.e(tag, "Google sign-in error", e)
                _authError.value = "Google sign-in failed: ${e.message}"
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun persistMemberIdentity(
        memberId: String?,
        authToken: String?,
        phoneNumber: String?,
        displayName: String?,
        gender: String? = null,
        avatarUrl: String? = null,
        themeSongTitle: String? = null,
        themeSongUrl: String? = null,
        emoji: String = "🌻",
        profileEmojiUrl: String? = null,
        profileComplete: Boolean = false
    ) {
        val sanitizedMemberId = sanitizeOptionalText(memberId)
        val sanitizedAuthToken = sanitizeOptionalText(authToken)
        if (sanitizedMemberId.isNullOrBlank() || sanitizedAuthToken.isNullOrBlank()) return

        scope.launch {
            val current = _memberIdentity.value
            val sanitizedPhoneNumber =
                sanitizeOptionalText(phoneNumber) ?: sanitizeOptionalText(current?.phoneNumber)
            if (sanitizedPhoneNumber.isNullOrBlank()) return@launch
            val identity = MemberIdentity(
                memberId = sanitizedMemberId,
                authToken = sanitizedAuthToken,
                phoneNumber = sanitizedPhoneNumber,
                displayName = sanitizeOptionalText(displayName) ?: sanitizeOptionalText(current?.displayName),
                gender = sanitizeOptionalText(gender) ?: sanitizeOptionalText(current?.gender),
                avatarUrl = resolveServerAssetUrl(avatarUrl) ?: resolveServerAssetUrl(current?.avatarUrl),
                profileEmojiUrl = sanitizeOptionalText(profileEmojiUrl) ?: current?.profileEmojiUrl,
                themeSongTitle = sanitizeOptionalText(themeSongTitle) ?: sanitizeOptionalText(current?.themeSongTitle),
                themeSongUrl = sanitizeOptionalText(themeSongUrl) ?: sanitizeOptionalText(current?.themeSongUrl),
                emoji = sanitizeOptionalText(emoji) ?: current?.emoji ?: "🌻",
                profileComplete = profileComplete || current?.profileComplete == true,
                lastBootstrapAt = System.currentTimeMillis()
            )
            identityStore.saveIdentity(identity)
            _memberIdentity.value = identity
        }
    }

    suspend fun saveIdentity(identity: MemberIdentity) {
        identityStore.saveIdentity(identity)
        _memberIdentity.value = identity
    }

    fun handleSocketServerStatus(mode: String?, message: String?) {
        val nextStatus = ServerStatus(
            mode = parseServerMode(mode),
            message = message?.takeIf { it.isNotBlank() },
            lastCheckedAt = System.currentTimeMillis()
        )
        updateServerStatus(nextStatus)

        when (nextStatus.mode) {
            ServerMode.MAINTENANCE -> stopSyncService()
            ServerMode.ONLINE -> onAppForegrounded()
            ServerMode.OFFLINE -> Unit
        }
    }

    private suspend fun bootstrapNow(
        reason: String,
        authoritative: Boolean = false
    ) {
        bootstrapMutex.withLock {
            try {
                if (!(networkMonitor.isOnline.first())) {
                    updateServerStatus(
                        ServerStatus(
                            mode = ServerMode.OFFLINE,
                            message = "No internet connection",
                            lastCheckedAt = System.currentTimeMillis()
                        )
                    )
                    stopSyncService()
                    return
                }

                val statusResponse = requestJson("/status")
                if (statusResponse.code in 200..299 && statusResponse.json != null) {
                    updateServerStatus(parseStatusResponse(statusResponse.json))
                } else {
                    updateServerStatus(
                        ServerStatus(
                            mode = ServerMode.OFFLINE,
                            message = "Sync server is offline",
                            lastCheckedAt = System.currentTimeMillis()
                        )
                    )
                    stopSyncService()
                    return
                }

                val currentIdentity = _memberIdentity.value
                val bootstrapResponse = requestJson(
                    path = "/api/client/bootstrap",
                    method = "POST",
                    body = JSONObject().apply {
                        put("deviceId", deviceId)
                        put("deviceName", deviceName)
                        currentIdentity?.authToken?.let { put("sessionToken", it) }
                    }
                )

                if (bootstrapResponse.code in 200..299 && bootstrapResponse.json != null) {
                    applyBootstrapResponse(bootstrapResponse.json, authoritativeEmptyAllowed = false, isAuthoritativeLogin = authoritative)
                    _authError.value = null
                } else {
                    Log.w(tag, "Bootstrap failed during $reason with HTTP ${bootstrapResponse.code}")
                    if (_serverStatus.value.mode != ServerMode.MAINTENANCE) {
                        updateServerStatus(
                            ServerStatus(
                                mode = ServerMode.OFFLINE,
                                message = "Sync server is offline",
                                lastCheckedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    stopSyncService()
                }
            } catch (e: Exception) {
                Log.e(tag, "Bootstrap failed during $reason", e)
                if (_serverStatus.value.mode != ServerMode.MAINTENANCE) {
                    updateServerStatus(
                        ServerStatus(
                            mode = ServerMode.OFFLINE,
                            message = "Sync server is offline",
                            lastCheckedAt = System.currentTimeMillis()
                        )
                    )
                }
                stopSyncService()
            }
        }
    }

    private suspend fun applyBootstrapResponse(
        json: JSONObject,
        authoritativeEmptyAllowed: Boolean,
        isAuthoritativeLogin: Boolean = false
    ): Boolean {
        Log.d(tag, "📥 Applying bootstrap response. Authoritative: ${json.optBoolean("authoritative")}, Identity: ${json.has("memberIdentity")}")
        
        val responseStatus = parseStatusResponse(json)
        updateServerStatus(responseStatus)
        val authoritative = json.optBoolean("authoritative", false)
        val requiresLogin = json.optBoolean("requiresLogin", false)

        com.aman.gigi.utils.AppConfig.applyServerConfigJson(json.optJSONObject("appConfig"))

        val parsedIdentity = json.optJSONObject("memberIdentity")?.let { identityJson ->
            // Ensure authToken is present in the identity object even if the server sent it at the root
            if (!identityJson.has("authToken") && json.has("authToken")) {
                identityJson.put("authToken", json.optString("authToken"))
            }
            val identity = parseMemberIdentity(identityJson)
            if (identity != null) {
                identityStore.saveIdentity(identity)
                _memberIdentity.value = identity
                _pendingPhoneNumber.value = sanitizeOptionalText(identity.phoneNumber) ?: _pendingPhoneNumber.value
                _devOtpHint.value = null

                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful && !task.result.isNullOrBlank()) {
                            registerFcmToken(task.result)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to get FCM token", e)
                }
            }
            if (identity != null) {
                val pendingPrefs = context.getSharedPreferences("gigi_twigi_offline_sync", Context.MODE_PRIVATE)
                val pendingConfig = pendingPrefs.getString("pending_twigi_config", null)
                if (!pendingConfig.isNullOrBlank()) {
                    scope.launch(Dispatchers.IO) {
                        val syncRes = saveTwigiOnServer(mode = "TWIGI", configJson = pendingConfig)
                        if (syncRes != null) {
                            pendingPrefs.edit().remove("pending_twigi_config").apply()
                        }
                    }
                }
            }
            identity
        }
        
        if (isAuthoritativeLogin) {
            Log.w(tag, "🚨 Authoritative Login detected. Wiping local scribbles and actions first.")
            scribbleRepository.deleteAllScribbles()
            outboundActionRepository.deleteAllActions()
        }

        if ((parsedIdentity == null && authoritative) || (requiresLogin && authoritative)) {
            // Server no longer knows this account (e.g. after a server reset) — fully log out
            // and wipe local chat history so re-login starts fresh.
            identityStore.clear()
            _memberIdentity.value = null
            runCatching { chatRepository.clearAll() }
            // Reset settings-sync markers so the next account restores ITS own blob
            // (rather than inheriting this account's galaxy prefs on the same device).
            settingsInitialPushDone = false
            runCatching { galaxyPrefs.edit().remove("prefs_restored_v1").apply() }
        }

        val serverConnections = parseBootstrapConnections(
            json.optJSONArray("connections"),
            fallbackMemberId = _memberIdentity.value?.memberId
        )
        val sharedAlarms = sharedAlarmRepository.parseSharedAlarms(json.optJSONArray("sharedAlarms"))
        val loveCardStacks = loveCardRepository.parseStacks(json.optJSONArray("loveCardStacks"))
        val loveCardItems = loveCardRepository.parseItems(json.optJSONArray("loveCardItems"))
        val loveCardResponses = loveCardRepository.parseResponses(json.optJSONArray("loveCardResponses"))

        val hasLocalConnections = connectionRepository.getAllConnectionsOnce().any { it.isActive && !it.serverArchived }
        val hasAuthoritativeRestore = authoritative || isAuthenticated(_memberIdentity.value) || serverConnections.isNotEmpty()

        if (hasAuthoritativeRestore || (!hasLocalConnections && authoritativeEmptyAllowed)) {
            connectionRepository.reconcileWithServer(serverConnections, authoritative = isAuthoritativeLogin)
            saveConnectionMembers(json.optJSONArray("connections"))
        }

        // Restore per-user settings the server holds (galaxy layout, emoji, renames,
        // relationship-theme) and re-apply the sticky per-connection overrides so the
        // server reconcile above doesn't wipe how this device sees each connection.
        restoreClientPrefsFromServer(parsedIdentity ?: _memberIdentity.value)

        // One-time upload of this device's current prefs/emoji so existing accounts
        // get their settings onto the server even if nothing changes this session.
        if (!settingsInitialPushDone && isAuthenticated(_memberIdentity.value)) {
            settingsInitialPushDone = true
            pushClientSettings()
        }

        val activeConnections = connectionRepository.getAllActiveConnectionsOnce()
        val selectedAlarmConnectionId = identityStore.selectedAlarmConnectionId.first()
        val selectedSweetConnectionId = identityStore.selectedSweetConnectionId.first()
        sharedAlarmRepository.reconcileWithServer(
            serverAlarms = sharedAlarms,
            activeConnections = activeConnections,
            selectedCreatorConnectionId = selectedAlarmConnectionId,
            authoritative = isAuthoritativeLogin
        )
        loveCardRepository.reconcileWithServer(
            serverStacks = loveCardStacks,
            serverItems = loveCardItems,
            serverResponses = loveCardResponses,
            authoritative = isAuthoritativeLogin
        )

        val creatorConnections = activeConnections.filter { it.role.equals(ConnectionRole.CREATOR.name, ignoreCase = true) }
        if (creatorConnections.isNotEmpty()) {
            val selectedConnectionStillValid = creatorConnections.any { it.connectionId == selectedAlarmConnectionId }
            if (!selectedConnectionStillValid) {
                identityStore.saveSelectedAlarmConnectionId(creatorConnections.first().connectionId)
                sharedAlarmRepository.refreshSchedules(activeConnections, creatorConnections.first().connectionId)
            }
        }

        val sweetCornerConnections = creatorConnections.ifEmpty { activeConnections }
        if (sweetCornerConnections.isNotEmpty()) {
            val selectedSweetStillValid = sweetCornerConnections.any { it.connectionId == selectedSweetConnectionId }
            if (!selectedSweetStillValid) {
                identityStore.saveSelectedSweetConnectionId(sweetCornerConnections.first().connectionId)
            }
        }

        if (responseStatus.mode == ServerMode.MAINTENANCE) {
            stopSyncService()
        } else {
            if (activeConnections.isNotEmpty()) {
                startSyncService()
            } else {
                stopSyncService()
            }
        }

        return hasAuthoritativeRestore || (!hasLocalConnections && authoritativeEmptyAllowed)
    }

    private fun parseMemberIdentity(identityJson: JSONObject): MemberIdentity? {
        val memberId = sanitizeOptionalText(identityJson.optString("memberId"))
        val authToken = sanitizeOptionalText(identityJson.optString("authToken"))
        val phoneNumber = sanitizeOptionalText(identityJson.optString("phoneNumber"))
        val googleEmail = sanitizeOptionalText(identityJson.optString("googleEmail"))
        if (memberId.isNullOrBlank() || authToken.isNullOrBlank()) return null
        // Require either phone (OTP auth) or Google email (Google auth)
        if (phoneNumber.isNullOrBlank() && googleEmail.isNullOrBlank()) return null

        return MemberIdentity(
            memberId = memberId,
            authToken = authToken,
            phoneNumber = phoneNumber,
            googleEmail = googleEmail,
            displayName = sanitizeOptionalText(identityJson.optString("displayName")),
            gender = sanitizeOptionalText(identityJson.optString("gender")),
            avatarUrl = resolveServerAssetUrl(identityJson.optString("avatarUrl")),
            profileEmojiUrl = resolveServerAssetUrl(identityJson.optString("profileEmojiUrl")),
            avatarMode = identityJson.optString("avatarMode").takeIf { it == "TWIGI" } ?: "EMOJI",
            twigiConfigJson = identityJson.optJSONObject("twigiConfig")?.toString(),
            twigiRenderUrl = resolveServerAssetUrl(identityJson.optString("twigiRenderUrl")),
            prefsBlob = identityJson.optJSONObject("prefsBlob")?.toString(),
            themeSongTitle = sanitizeOptionalText(identityJson.optString("themeSongTitle")),
            themeSongUrl = resolveServerAssetUrl(identityJson.optString("themeSongUrl")),
            dateOfBirth = sanitizeOptionalText(identityJson.optString("dateOfBirth")),
            emoji = sanitizeOptionalText(identityJson.optString("emoji")) ?: "🌻",
            discoverable = identityJson.optBoolean("discoverable", false),
            handle = sanitizeOptionalText(identityJson.optString("handle")),
            bio = sanitizeOptionalText(identityJson.optString("bio")),
            nebulaSeed = identityJson.optInt("nebulaSeed", 42),
            profileComplete = identityJson.optBoolean("profileComplete", false),
            lastBootstrapAt = System.currentTimeMillis()
        )
    }

    /** Persist the per-connection member roster (for group Sweet Corner cards). */
    private suspend fun saveConnectionMembers(connectionsJson: JSONArray?) {
        if (connectionsJson == null) return
        for (i in 0 until connectionsJson.length()) {
            val item = connectionsJson.optJSONObject(i) ?: continue
            val code = item.optString("connectionCode").ifBlank { item.optString("connectionId") }.lowercase()
            if (code.isBlank()) continue
            val membersArr = item.optJSONArray("members") ?: continue
            val members = mutableListOf<com.aman.gigi.model.ConnectionMember>()
            for (j in 0 until membersArr.length()) {
                val m = membersArr.optJSONObject(j) ?: continue
                val deviceId = m.optString("deviceId").ifBlank { m.optString("memberId") }
                if (deviceId.isBlank()) continue
                members += com.aman.gigi.model.ConnectionMember(
                    connectionId = code,
                    memberDeviceId = deviceId,
                    memberName = m.optString("name").ifBlank { "Member" },
                    memberEmoji = m.optString("emoji").ifBlank { "🌻" },
                    memberAvatarUrl = resolveServerAssetUrl(m.optString("avatarUrl")),
                    emojiUrl = resolveServerAssetUrl(m.optString("emojiUrl")),
                    role = m.optString("role").ifBlank { "PARTNER" }
                )
            }
            connectionRepository.deleteAllMembersForConnection(code)
            if (members.isNotEmpty()) connectionRepository.saveMembers(members)
        }
    }

    private fun parseBootstrapConnections(
        connectionsJson: JSONArray?,
        fallbackMemberId: String?
    ): List<Connection> {
        if (connectionsJson == null) return emptyList()

        val now = System.currentTimeMillis()
        val restored = mutableListOf<Connection>()
        for (index in 0 until connectionsJson.length()) {
            val item = connectionsJson.optJSONObject(index) ?: continue
            val connectionCode = item.optString("connectionCode")
                .ifBlank { item.optString("connectionId") }
                .lowercase()
            if (connectionCode.isBlank()) continue

            val role = item.optString("role")
                .takeIf { it.isNotBlank() }
                ?: ConnectionRole.PARTNER.name
            val transportState = when (item.optString("transportHint").uppercase()) {
                TransportState.CONNECTED.name -> TransportState.CONNECTED
                TransportState.NO_INTERNET.name -> TransportState.NO_INTERNET
                else -> TransportState.CONNECTING
            }
            val partnerPresence = when (item.optString("partnerPresence").uppercase()) {
                PartnerPresence.ONLINE.name -> PartnerPresence.ONLINE
                PartnerPresence.OFFLINE.name -> PartnerPresence.OFFLINE
                else -> PartnerPresence.UNKNOWN
            }
            val serverArchived = item.optBoolean("isArchived", false)
            val placeholderPartnerId = when (role.uppercase()) {
                ConnectionRole.CREATOR.name -> "waiting..."
                else -> "joining..."
            }

            val partnerName = item.optString("partnerDisplayName").ifBlank { "Partner" }
            val partnerEmoji = item.optString("partnerEmoji").ifBlank { "🌻" }
            val resolvedPartnerEmojiUrl = resolveServerAssetUrl(item.optString("partnerEmojiUrl"))
            val resolvedAvatar = resolveServerAssetUrl(item.optString("partnerAvatarUrl"))
            val origin = item.optString("origin").ifBlank { "INVITE" }
            val trustRing = item.optInt("trustRing", 0)
            Log.d(tag, "Bootstrapping connection $connectionCode: partner=$partnerName, emoji=$partnerEmoji, avatar=$resolvedAvatar, origin=$origin")

            val relationshipType = item.optString("relationshipType").ifBlank { "ROMANTIC" }
            restored += Connection(
                connectionId = connectionCode,
                isGroup = item.optBoolean("isGroup", relationshipType.equals("GROUP", ignoreCase = true)),
                relationshipType = relationshipType,
                origin = origin,
                trustRing = trustRing,
                partnerName = partnerName,
                partnerEmoji = partnerEmoji,
                partnerEmojiUrl = resolvedPartnerEmojiUrl,
                partnerAvatarMode = item.optString("partnerAvatarMode").takeIf { it == "TWIGI" } ?: "EMOJI",
                partnerTwigiUrl = resolveServerAssetUrl(item.optString("partnerTwigiUrl")),
                partnerDeviceId = item.optString("partnerDeviceId").ifBlank { placeholderPartnerId },
                connectionCode = connectionCode,
                partnerAvatarUrl = resolvedAvatar,
                role = role,
                memberId = fallbackMemberId,
                isActive = !serverArchived,
                serverArchived = serverArchived,
                connectionStatus = deriveLegacyStatus(transportState, partnerPresence),
                transportState = transportState.name,
                partnerPresence = partnerPresence.name,
                lastSeenAt = item.optLong("lastSeenAt").takeIf { it > 0L },
                restoredAt = now,
                creatorDeviceId = item.optString("creatorDeviceId").takeIf { it.isNotBlank() },
                lastSyncedAt = now
            )
        }

        return restored
    }

    private fun parseStatusResponse(json: JSONObject): ServerStatus {
        val mode = parseServerMode(
            json.optString("serverMode")
                .ifBlank { json.optString("mode") }
        )
        val message = json.optString("maintenanceMessage")
            .ifBlank { json.optString("message") }
            .takeIf { it.isNotBlank() }
        val serverTime = json.optLong("serverTime").takeIf { it > 0L } ?: System.currentTimeMillis()

        return ServerStatus(
            mode = mode,
            message = message,
            lastCheckedAt = serverTime
        )
    }

    private fun parseServerMode(rawMode: String?): ServerMode {
        return when (rawMode?.uppercase()) {
            ServerMode.ONLINE.name -> ServerMode.ONLINE
            ServerMode.MAINTENANCE.name -> ServerMode.MAINTENANCE
            else -> ServerMode.OFFLINE
        }
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        val digits = phoneNumber.filter { it.isDigit() }
        return if (digits.length in 10..15) {
            "+$digits"
        } else {
            ""
        }
    }

    private fun updateServerStatus(status: ServerStatus) {
        _serverStatus.value = status.copy(
            lastCheckedAt = if (status.lastCheckedAt > 0L) status.lastCheckedAt else System.currentTimeMillis()
        )
    }

    suspend fun saveSelectedAlarmConnectionId(connectionId: String?) {
        identityStore.saveSelectedAlarmConnectionId(connectionId)
    }

    suspend fun selectedAlarmConnectionId(): String? {
        return identityStore.selectedAlarmConnectionId.first()
    }

    suspend fun saveSelectedSweetConnectionId(connectionId: String?) {
        identityStore.saveSelectedSweetConnectionId(connectionId)
    }

    suspend fun selectedSweetConnectionId(): String? {
        return identityStore.selectedSweetConnectionId.first()
    }

    fun signOut() {
        Log.i(tag, "👤 Sign out requested. Clearing local state immediately.")
        
        // Immediate UI feedback: clear identity and errors to trigger navigation back to onboarding
        _memberIdentity.value = null
        _authError.value = null

        scope.launch {
            try {
                // 0. Cancel any active bootstrap jobs immediately
                currentBootstrapJob?.cancel()
                currentBootstrapJob = null

                // 1. Clear authentication IMMEDIATELY and WITHOUT WAITING for the lock.
                // This ensures that even if a sync task is stuck, restarting the app
                // will never restore the old account.
                Log.d(tag, "Clearing persistent identity and firebase auth...")
                firebaseAuthenticator.signOut()
                identityStore.clear()
                
                // 2. Clear transient auth state
                _pendingPhoneNumber.value = null
                _devOtpHint.value = null
                
                // 3. For the remaining deep repository cleanup, we use the lock 
                // to avoid interfering with ongoing database operations.
                bootstrapMutex.withLock {
                    Log.d(tag, "Acquired lock. Performing deep repository wipe...")
                    
                    connectionRepository.deleteAllConnections()
                    sharedAlarmRepository.deleteAllAlarms()
                    loveCardRepository.deleteAllData()
                    scribbleRepository.deleteAllScribbles()
                    outboundActionRepository.deleteAllActions()
                    chatRepository.clearAll()
                    // Reset settings-sync markers so the next account restores its own blob.
                    settingsInitialPushDone = false
                    galaxyPrefs.edit().remove("prefs_restored_v1").apply()

                    stopSyncService()
                    Log.i(tag, "✅ Sign out cleanup complete.")
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Error during sign out cleanup", e)
            } finally {
                // Double check identity is cleared everywhere
                _memberIdentity.value = null
            }
        }
    }

    fun deleteAccount() {
        Log.i(tag, "👤 Account deletion requested.")
        scope.launch {
            try {
                val identity = _memberIdentity.value
                if (identity?.authToken != null) {
                    val response = requestJson(
                        path = "/api/auth/account",
                        method = "DELETE",
                        body = JSONObject().apply {
                            put("sessionToken", identity.authToken)
                        }
                    )
                    Log.i(tag, "Account deletion response: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to call delete account endpoint", e)
            } finally {
                // Regardless of server success, sign out locally
                signOut()
            }
        }
    }

    private fun encodeAvatar(uri: Uri): Pair<String, String>? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return@use null
                val scaled = Bitmap.createScaledBitmap(bitmap, 420, 420, true)
                val output = java.io.ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
                output.toByteArray().let { bytes ->
                    java.util.Base64.getEncoder().encodeToString(bytes) to "image/jpeg"
                }
            }
        }.getOrNull()
    }

    // ── Per-user client settings sync ──────────────────────────────────────────
    private val galaxyPrefs by lazy {
        context.getSharedPreferences("galaxy_orbits", Context.MODE_PRIVATE)
    }
    @Volatile private var suppressPrefsPush = false
    @Volatile private var settingsInitialPushDone = false
    private var prefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var prefsPushJob: Job? = null

    /** Watch the galaxy prefs and debounce-push them to the server as one blob. */
    private fun startPrefsSync() {
        if (prefsListener != null) return
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (suppressPrefsPush) return@OnSharedPreferenceChangeListener
            prefsPushJob?.cancel()
            prefsPushJob = scope.launch {
                kotlinx.coroutines.delay(2500)
                pushClientSettings()
            }
        }
        prefsListener = listener
        galaxyPrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Serialize the galaxy prefs (type-preserving) and POST them to the server. */
    fun pushClientSettings() {
        scope.launch {
            val identity = _memberIdentity.value ?: return@launch
            val token = identity.authToken
            if (token.isBlank()) return@launch
            val galaxy = JSONObject()
            runCatching {
                galaxyPrefs.all.forEach { (k, v) ->
                    // Skip the cached remote emoji catalog (large, re-fetched from the
                    // server anyway) and our own local sync marker — they'd bloat the blob.
                    if (k == "emoji_catalog" || k == "prefs_restored_v1") return@forEach
                    when (v) {
                        is Boolean -> galaxy.put(k, v)
                        is Int -> galaxy.put(k, v)
                        is Long -> galaxy.put(k, v)
                        is Float -> galaxy.put(k, v.toDouble())
                        is String -> galaxy.put(k, v)
                        else -> {}
                    }
                }
            }
            val settings = JSONObject().apply { put("galaxyPrefs", galaxy.toString()) }
            val myEmoji = galaxyPrefs.getString("emoji_self", null)
            runCatching {
                requestJson(
                    path = "/api/client/settings",
                    method = "POST",
                    body = JSONObject().apply {
                        put("sessionToken", token)
                        put("settings", settings)
                        // The shared profile emoji, so partners + new members see it on bootstrap.
                        if (!myEmoji.isNullOrBlank()) put("profileEmojiUrl", myEmoji)
                    }
                )
            }.onFailure { Log.w(tag, "Client settings push failed: ${it.message}") }
        }
    }

    /** Reliable server-side delete/leave (HTTP). Creators hard-delete; members archive. */
    suspend fun archiveConnectionOnServer(connectionCode: String): Boolean {
        val token = _memberIdentity.value?.authToken?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            requestJson(
                path = "/api/client/connections/archive",
                method = "POST",
                body = JSONObject().apply {
                    put("sessionToken", token)
                    put("connectionCode", connectionCode.lowercase())
                }
            ).code in 200..299
        }.getOrDefault(false).also {
            if (!it) Log.w(tag, "archiveConnectionOnServer failed for $connectionCode")
        }
    }

    /**
     * Saves the member's Twigi to the server: mode toggle, part config, and/or the
     * composited PNG render (base64). Returns the stored render URL when uploading.
     */
    fun updateProfileEmoji(emojiUrl: String) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val current = _memberIdentity.value
            if (current != null) {
                val updated = current.copy(
                    profileEmojiUrl = emojiUrl,
                    avatarMode = "EMOJI"
                )
                _memberIdentity.value = updated
                identityStore.saveIdentity(updated)
            }
            try {
                pushClientSettings()
            } catch (e: Exception) {
                android.util.Log.e(tag, "Exception pushing local settings for emoji", e)
            }
        }
    }

    suspend fun saveTwigiOnServer(
        mode: String? = null,
        configJson: String? = null,
        renderBase64: String? = null
    ): String? {
        val token = _memberIdentity.value?.authToken?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val response = requestJson(
                path = "/api/client/twigi",
                method = "POST",
                body = JSONObject().apply {
                    put("sessionToken", token)
                    put("deviceId", deviceId)
                    if (mode != null) put("mode", mode)
                    if (configJson != null) {
                        val parsedObj = runCatching { JSONObject(configJson) }.getOrNull()
                        if (parsedObj != null) {
                            put("config", parsedObj)
                        } else {
                            put("config", JSONObject().put("vrmUrl", configJson))
                        }
                    }
                    if (renderBase64 != null) put("renderBase64", renderBase64)
                }
            )
            if (response.code in 200..299) {
                val url = resolveServerAssetUrl(response.json?.optString("twigiRenderUrl"))
                val finalUrl = url ?: configJson
                val current = _memberIdentity.value
                if (current != null) {
                    val updated = current.copy(
                        avatarMode = mode ?: "TWIGI",
                        twigiConfigJson = configJson ?: current.twigiConfigJson,
                        twigiRenderUrl = finalUrl ?: current.twigiRenderUrl
                    )
                    _memberIdentity.value = updated
                    identityStore.saveIdentity(updated)
                }
                finalUrl ?: ""
            } else {
                val current = _memberIdentity.value
                if (current != null && configJson != null) {
                    val updated = current.copy(
                        avatarMode = mode ?: "TWIGI",
                        twigiRenderUrl = configJson
                    )
                    _memberIdentity.value = updated
                    identityStore.saveIdentity(updated)
                }
                null
            }
        }.getOrNull().also {
            if (it == null) Log.w(tag, "saveTwigiOnServer failed")
        }
    }

    /** Atomically creates a group server-side: session + emoji + members in one call. */
    suspend fun createGroupOnServer(
        groupCode: String,
        name: String,
        emojiUrl: String?,
        viaCodes: List<String>
    ): Boolean {
        val token = _memberIdentity.value?.authToken?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            requestJson(
                path = "/api/client/groups/create",
                method = "POST",
                body = JSONObject().apply {
                    put("sessionToken", token)
                    put("groupCode", groupCode.lowercase())
                    put("name", name)
                    if (!emojiUrl.isNullOrBlank()) put("emojiUrl", emojiUrl)
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                    put("viaCodes", JSONArray(viaCodes.map { it.lowercase() }))
                }
            ).code in 200..299
        }.getOrDefault(false).also {
            if (!it) Log.w(tag, "createGroupOnServer failed for $groupCode")
        }
    }

    /** Adds partners (via their 1-1 connection codes) to a group, server-side. */
    suspend fun addGroupMembersOnServer(groupCode: String, viaCodes: List<String>): Boolean {
        val token = _memberIdentity.value?.authToken?.takeIf { it.isNotBlank() } ?: return false
        if (viaCodes.isEmpty()) return true
        return runCatching {
            requestJson(
                path = "/api/client/groups/add-members",
                method = "POST",
                body = JSONObject().apply {
                    put("sessionToken", token)
                    put("groupCode", groupCode.lowercase())
                    put("viaCodes", JSONArray(viaCodes.map { it.lowercase() }))
                }
            ).code in 200..299
        }.getOrDefault(false).also {
            if (!it) Log.w(tag, "addGroupMembersOnServer failed for $groupCode")
        }
    }

    /** Sets the group's shared animated emoji, server-side (synced to every member). */
    suspend fun setGroupEmojiOnServer(groupCode: String, emojiUrl: String): Boolean {
        val token = _memberIdentity.value?.authToken?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            requestJson(
                path = "/api/client/groups/emoji",
                method = "POST",
                body = JSONObject().apply {
                    put("sessionToken", token)
                    put("groupCode", groupCode.lowercase())
                    put("emojiUrl", emojiUrl)
                }
            ).code in 200..299
        }.getOrDefault(false).also {
            if (!it) Log.w(tag, "setGroupEmojiOnServer failed for $groupCode")
        }
    }

    /** Restore server-held settings into local prefs + re-apply per-connection overrides. */
    private suspend fun restoreClientPrefsFromServer(identity: MemberIdentity?) {
        identity ?: return
        // 1) Seed my own profile emoji if this device doesn't have one yet.
        identity.profileEmojiUrl?.takeIf { it.isNotBlank() }?.let { url ->
            if (galaxyPrefs.getString("emoji_self", null).isNullOrBlank()) {
                galaxyPrefs.edit().putString("emoji_self", url).apply()
            }
        }
        // 2) Restore the full galaxy prefs blob ONCE per install/login (don't clobber
        //    live edits on later bootstraps). The marker is set regardless of whether the
        //    server had a blob, so an initially-empty server (existing users) can't cause
        //    a later re-restore that overwrites this device's fresh changes.
        if (!galaxyPrefs.getBoolean("prefs_restored_v1", false)) {
            runCatching {
                val galaxyJson = identity.prefsBlob
                    ?.takeIf { it.isNotBlank() }
                    ?.let { JSONObject(it).optString("galaxyPrefs") }
                    .orEmpty()
                suppressPrefsPush = true
                val editor = galaxyPrefs.edit()
                if (galaxyJson.isNotBlank()) {
                    val obj = JSONObject(galaxyJson)
                    obj.keys().forEach { key ->
                        when (val v = obj.get(key)) {
                            is Boolean -> editor.putBoolean(key, v)
                            is Int -> editor.putInt(key, v)
                            is Long -> editor.putLong(key, v)
                            is Double -> editor.putFloat(key, v.toFloat())
                            is String -> editor.putString(key, v)
                            else -> {}
                        }
                    }
                }
                editor.putBoolean("prefs_restored_v1", true)
                editor.apply()
                suppressPrefsPush = false
            }.onFailure { suppressPrefsPush = false; Log.w(tag, "prefs restore failed: ${it.message}") }
        }
        // 3) Re-apply per-connection rename / relationship-theme overrides (sticky vs reconcile).
        runCatching {
            connectionRepository.getAllConnectionsOnce().forEach { conn ->
                val rename = galaxyPrefs.getString("rename_${conn.connectionId}", null)?.takeIf { it.isNotBlank() }
                val reltype = galaxyPrefs.getString("reltype_${conn.connectionId}", null)?.takeIf { it.isNotBlank() }
                if (rename != null || reltype != null) {
                    connectionRepository.updateConnection(
                        conn.copy(
                            partnerName = rename ?: conn.partnerName,
                            relationshipType = reltype ?: conn.relationshipType
                        )
                    )
                }
            }
        }
    }

    private suspend fun requestJson(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        headers: Map<String, String>? = null
    ): HttpResponse {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val url = URL("$httpBaseUrl$path")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 5000
                readTimeout = 8000
                doInput = true
                setRequestProperty("Accept", "application/json")
                headers?.forEach { (k, v) ->
                    setRequestProperty(k, v)
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }

            try {
                if (body != null) {
                    connection.outputStream.use { output ->
                        output.write(body.toString().toByteArray(Charsets.UTF_8))
                    }
                }

                val responseCode = connection.responseCode
                val responseText = (if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                })?.bufferedReader()?.use { it.readText() }.orEmpty()

                HttpResponse(
                    code = responseCode,
                    json = responseText.takeIf { it.isNotBlank() }?.let {
                        runCatching { JSONObject(it) }.getOrNull()
                    }
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun startSyncService() {
        val intent = Intent(context, ScreensaverSyncService::class.java).apply {
            action = ScreensaverSyncService.ACTION_START_SYNC
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopSyncService() {
        runCatching {
            context.startService(
                Intent(context, ScreensaverSyncService::class.java).apply {
                    action = ScreensaverSyncService.ACTION_STOP_SYNC
                }
            )
        }.onFailure {
            Log.w(tag, "Unable to send graceful stop to sync service", it)
            context.stopService(Intent(context, ScreensaverSyncService::class.java))
        }
    }

    private fun deriveLegacyStatus(
        transportState: TransportState,
        partnerPresence: PartnerPresence
    ): String {
        return when {
            transportState == TransportState.NO_INTERNET -> "NO_INTERNET"
            transportState == TransportState.CONNECTING -> "DISCONNECTED"
            partnerPresence == PartnerPresence.OFFLINE -> "PARTNER_OFFLINE"
            else -> "CONNECTED"
        }
    }

    private data class HttpResponse(
        val code: Int,
        val json: JSONObject?
    )
    /** Fetches the available Break Cards from the server. */
    // suspend fun fetchBreakCardConfigs() {
    //     val token = _memberIdentity.value?.authToken?.takeIf { it.isNotBlank() } ?: return
    //     runCatching {
    //         val response = requestJson(
    //             path = "/api/client/break-cards",
    //             method = "POST",
    //             body = JSONObject(),
    //             token = token
    //         )
    //         if (response.code in 200..299) {
    //             val jsonBody = JSONObject(response.body)
    //             val cardsArr = jsonBody.optJSONArray("cards")
    //             if (cardsArr != null) {
    //                 val cards = mutableListOf<BreakCardConfig>()
    //                 for (i in 0 until cardsArr.length()) {
    //                     val c = cardsArr.optJSONObject(i) ?: continue
    //                     cards.add(BreakCardConfig(
    //                         cardId = c.optString("id"),
    //                         name = c.optString("name"),
    //                         animatedSvgUrl = resolveServerAssetUrl(c.optString("animatedSvgUrl"))
    //                     ))
    //                 }
    //                 _breakCards.value = cards
    //                 return
    //             }
    //         }
    //     }
    //     // Fallback if API fails or isn't ready
    //     _breakCards.value = listOf(
    //         BreakCardConfig("tea_break", "Tea Break", null),
    //         BreakCardConfig("coffee_break", "Coffee Break", null),
    //         BreakCardConfig("sutta_break", "Sutta Break", null),
    //         BreakCardConfig("stretch_break", "Stretch Break", null)
    //     )
    // }

    fun registerFcmToken(token: String) {
        if (token.isBlank()) return
        scope.launch {
            try {
                val identity = _memberIdentity.value ?: return@launch
                if (identity.authToken.isBlank()) return@launch
                Log.i(tag, "📲 Registering FCM token to server via HTTP...")
                val response = requestJson(
                    path = "/api/auth/fcm-token",
                    method = "POST",
                    body = JSONObject().apply {
                        put("sessionToken", identity.authToken)
                        put("fcmToken", token)
                    }
                )
                if (response.code in 200..299) {
                    Log.i(tag, "✅ FCM Token successfully registered on server!")
                } else {
                    Log.w(tag, "⚠️ FCM Token registration response: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Failed to register FCM token to server", e)
            }
        }
    }

    // ── Nebula Discovery Methods ──────────────────────────────────────────

    suspend fun updateDiscoverability(
        discoverable: Boolean,
        handle: String?,
        bio: String?
    ): Result<MemberIdentity> = withContext(Dispatchers.IO) {
        try {
            val identity = _memberIdentity.value ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            val token = com.aman.gigi.data.auth.SessionTokenProvider.current(identity.authToken) ?: identity.authToken
            val response = requestJson(
                path = "/api/profile/discoverability",
                method = "POST",
                headers = mapOf("x-session-token" to token),
                body = JSONObject().apply {
                    put("sessionToken", token)
                    put("discoverable", discoverable)
                    if (handle != null) put("handle", handle)
                    if (bio != null) put("bio", bio)
                }
            )
            if (response.code in 200..299 && response.json != null) {
                val json = response.json
                val updated = identity.copy(
                    discoverable = json.optBoolean("discoverable", discoverable),
                    handle = json.optString("handle").takeIf { it.isNotBlank() },
                    bio = json.optString("bio").takeIf { it.isNotBlank() }
                )
                _memberIdentity.value = updated
                identityStore.saveIdentity(updated)
                Result.success(updated)
            } else {
                val err = response.json?.optString("error")
                Result.failure(Exception(err ?: "Server returned code ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchNebulaMotes(): List<com.aman.gigi.model.NebulaMember> = withContext(Dispatchers.IO) {
        try {
            val identity = _memberIdentity.value ?: return@withContext emptyList()
            val token = com.aman.gigi.data.auth.SessionTokenProvider.current(identity.authToken) ?: identity.authToken
            val response = requestJson(
                path = "/api/nebula/browse",
                method = "GET",
                headers = mapOf("x-session-token" to token)
            )
            if (response.code in 200..299 && response.json != null) {
                val json = response.json
                val arr = json.optJSONArray("motes") ?: return@withContext emptyList()
                val list = mutableListOf<com.aman.gigi.model.NebulaMember>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list += com.aman.gigi.model.NebulaMember(
                        memberId = obj.optString("memberId"),
                        handle = obj.optString("handle"),
                        displayName = obj.optString("displayName"),
                        avatarUrl = resolveServerAssetUrl(obj.optString("avatarUrl")),
                        twigiRenderUrl = resolveServerAssetUrl(obj.optString("twigiRenderUrl")),
                        profileEmojiUrl = resolveServerAssetUrl(obj.optString("profileEmojiUrl")),
                        avatarMode = obj.optString("avatarMode", "EMOJI"),
                        bio = sanitizeOptionalText(obj.optString("bio")),
                        nebulaSeed = obj.optInt("nebulaSeed", 42),
                        isRecentlyActive = obj.optBoolean("isRecentlyActive", false),
                        inviteStatus = obj.optString("inviteStatus", "NONE")
                    )
                }
                list
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to browse nebula", e)
            emptyList()
        }
    }

    suspend fun searchNebula(query: String): List<com.aman.gigi.model.NebulaMember> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val identity = _memberIdentity.value ?: return@withContext emptyList()
            val token = com.aman.gigi.data.auth.SessionTokenProvider.current(identity.authToken) ?: identity.authToken
            val encodedQ = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val response = requestJson(
                path = "/api/nebula/search?q=$encodedQ",
                method = "GET",
                headers = mapOf("x-session-token" to token)
            )
            if (response.code in 200..299 && response.json != null) {
                val json = response.json
                val arr = json.optJSONArray("results") ?: return@withContext emptyList()
                val list = mutableListOf<com.aman.gigi.model.NebulaMember>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list += com.aman.gigi.model.NebulaMember(
                        memberId = obj.optString("memberId"),
                        handle = obj.optString("handle"),
                        displayName = obj.optString("displayName"),
                        avatarUrl = resolveServerAssetUrl(obj.optString("avatarUrl")),
                        twigiRenderUrl = resolveServerAssetUrl(obj.optString("twigiRenderUrl")),
                        profileEmojiUrl = resolveServerAssetUrl(obj.optString("profileEmojiUrl")),
                        avatarMode = obj.optString("avatarMode", "EMOJI"),
                        bio = sanitizeOptionalText(obj.optString("bio")),
                        nebulaSeed = obj.optInt("nebulaSeed", 42),
                        isRecentlyActive = obj.optBoolean("isRecentlyActive", false),
                        inviteStatus = obj.optString("inviteStatus", "NONE")
                    )
                }
                list
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to search nebula", e)
            emptyList()
        }
    }

    suspend fun sendNebulaInvite(
        targetMemberId: String?,
        targetHandle: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val identity = _memberIdentity.value ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            val response = requestJson(
                path = "/api/nebula/invite",
                method = "POST",
                body = JSONObject().apply {
                    put("sessionToken", identity.authToken)
                    if (targetMemberId != null) put("targetMemberId", targetMemberId)
                    if (targetHandle != null) put("targetHandle", targetHandle)
                }
            )
            if (response.code in 200..299 && response.json != null) {
                val json = response.json
                Result.success(json.optString("inviteId"))
            } else {
                val err = response.json?.optString("error")
                Result.failure(Exception(err ?: "Server error ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun respondNebulaInvite(
        inviteId: String,
        accept: Boolean
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val identity = _memberIdentity.value ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            val response = requestJson(
                path = "/api/nebula/invite/respond",
                method = "POST",
                body = JSONObject().apply {
                    put("sessionToken", identity.authToken)
                    put("inviteId", inviteId)
                    put("accept", accept)
                }
            )
            if (response.code in 200..299 && response.json != null) {
                val json = response.json
                val connectionCode = json.optString("connectionCode").takeIf { it.isNotBlank() }
                if (connectionCode != null) {
                    refreshFromServer("nebula_invite_accepted")
                }
                Result.success(connectionCode)
            } else {
                val err = response.json?.optString("error")
                Result.failure(Exception(err ?: "Server error ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPendingNebulaInvites(): List<com.aman.gigi.model.IncomingNebulaInvite> = withContext(Dispatchers.IO) {
        try {
            val identity = _memberIdentity.value ?: return@withContext emptyList()
            val token = com.aman.gigi.data.auth.SessionTokenProvider.current(identity.authToken) ?: identity.authToken
            val response = requestJson(
                path = "/api/nebula/invites/pending",
                method = "GET",
                headers = mapOf("x-session-token" to token)
            )
            if (response.code in 200..299 && response.json != null) {
                val arr = response.json.optJSONArray("invites") ?: return@withContext emptyList()
                val list = mutableListOf<com.aman.gigi.model.IncomingNebulaInvite>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list += com.aman.gigi.model.IncomingNebulaInvite(
                        inviteId = obj.optString("inviteId"),
                        fromMemberId = obj.optString("fromMemberId"),
                        handle = obj.optString("handle"),
                        displayName = obj.optString("displayName"),
                        avatarUrl = resolveServerAssetUrl(obj.optString("avatarUrl")),
                        twigiRenderUrl = resolveServerAssetUrl(obj.optString("twigiRenderUrl")),
                        profileEmojiUrl = resolveServerAssetUrl(obj.optString("profileEmojiUrl")),
                        avatarMode = obj.optString("avatarMode", "EMOJI"),
                        bio = sanitizeOptionalText(obj.optString("bio")),
                        createdAt = obj.optString("createdAt")
                    )
                }
                list
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch pending nebula invites", e)
            emptyList()
        }
    }

    suspend fun blockMember(targetMemberId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val identity = _memberIdentity.value ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            val response = requestJson(
                path = "/api/nebula/block",
                method = "POST",
                body = JSONObject().apply {
                    put("sessionToken", identity.authToken)
                    put("targetMemberId", targetMemberId)
                }
            )
            if (response.code in 200..299) Result.success(Unit)
            else Result.failure(Exception("Failed to block"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportMember(targetMemberId: String, reason: String, note: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val identity = _memberIdentity.value ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            val response = requestJson(
                path = "/api/nebula/report",
                method = "POST",
                body = JSONObject().apply {
                    put("sessionToken", identity.authToken)
                    put("targetMemberId", targetMemberId)
                    put("reason", reason)
                    if (note != null) put("note", note)
                }
            )
            if (response.code in 200..299) Result.success(Unit)
            else Result.failure(Exception("Failed to report"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
