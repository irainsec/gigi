package com.aman.gigi.data.client

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aman.gigi.model.MemberIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.identityDataStore by preferencesDataStore(name = "gigi_identity")

private fun sanitizeStoredText(value: String?): String? =
    value
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

@Singleton
class ClientIdentityStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val memberId = stringPreferencesKey("member_id")
        val authToken = stringPreferencesKey("auth_token")
        val phoneNumber = stringPreferencesKey("phone_number")
        val googleEmail = stringPreferencesKey("google_email")
        val displayName = stringPreferencesKey("display_name")
        val gender = stringPreferencesKey("gender")
        val profileEmojiUrl = stringPreferencesKey("profile_emoji_url")
        val avatarUrl = stringPreferencesKey("avatar_url")
        val avatarMode = stringPreferencesKey("avatar_mode")
        val twigiConfig = stringPreferencesKey("twigi_config")
        val twigiRenderUrl = stringPreferencesKey("twigi_render_url")
        val themeSongTitle = stringPreferencesKey("theme_song_title")
        val themeSongUrl = stringPreferencesKey("theme_song_url")
        val selectedAlarmConnectionId = stringPreferencesKey("selected_alarm_connection_id")
        val selectedSweetConnectionId = stringPreferencesKey("selected_sweet_connection_id")
        val lastBootstrapAt = longPreferencesKey("last_bootstrap_at")
    }

    val memberIdentity: Flow<MemberIdentity?> = context.identityDataStore.data.map { preferences ->
        preferences.toMemberIdentity()
    }

    val selectedAlarmConnectionId: Flow<String?> = context.identityDataStore.data.map { preferences ->
        sanitizeStoredText(preferences[Keys.selectedAlarmConnectionId])
    }

    val selectedSweetConnectionId: Flow<String?> = context.identityDataStore.data.map { preferences ->
        sanitizeStoredText(preferences[Keys.selectedSweetConnectionId])
    }

    suspend fun saveIdentity(identity: MemberIdentity) {
        context.identityDataStore.edit { preferences ->
            preferences[Keys.memberId] = identity.memberId
            preferences[Keys.authToken] = identity.authToken
            sanitizeStoredText(identity.phoneNumber)?.let { preferences[Keys.phoneNumber] = it }
                ?: preferences.remove(Keys.phoneNumber)
            sanitizeStoredText(identity.googleEmail)?.let { preferences[Keys.googleEmail] = it }
                ?: preferences.remove(Keys.googleEmail)
            sanitizeStoredText(identity.displayName)?.let { preferences[Keys.displayName] = it }
                ?: preferences.remove(Keys.displayName)
            sanitizeStoredText(identity.gender)?.let { preferences[Keys.gender] = it }
                ?: preferences.remove(Keys.gender)
            sanitizeStoredText(identity.profileEmojiUrl)?.let { preferences[Keys.profileEmojiUrl] = it }
                ?: preferences.remove(Keys.profileEmojiUrl)
            sanitizeStoredText(identity.avatarUrl)?.let { preferences[Keys.avatarUrl] = it }
                ?: preferences.remove(Keys.avatarUrl)
            preferences[Keys.avatarMode] = if (identity.avatarMode == "TWIGI") "TWIGI" else "EMOJI"
            sanitizeStoredText(identity.twigiConfigJson)?.let { preferences[Keys.twigiConfig] = it }
                ?: preferences.remove(Keys.twigiConfig)
            sanitizeStoredText(identity.twigiRenderUrl)?.let { preferences[Keys.twigiRenderUrl] = it }
                ?: preferences.remove(Keys.twigiRenderUrl)
            sanitizeStoredText(identity.themeSongTitle)?.let { preferences[Keys.themeSongTitle] = it }
                ?: preferences.remove(Keys.themeSongTitle)
            sanitizeStoredText(identity.themeSongUrl)?.let { preferences[Keys.themeSongUrl] = it }
                ?: preferences.remove(Keys.themeSongUrl)
            preferences[Keys.lastBootstrapAt] = identity.lastBootstrapAt
        }
    }

    suspend fun saveSelectedAlarmConnectionId(connectionId: String?) {
        context.identityDataStore.edit { preferences ->
            sanitizeStoredText(connectionId)?.let { preferences[Keys.selectedAlarmConnectionId] = it }
                ?: preferences.remove(Keys.selectedAlarmConnectionId)
        }
    }

    suspend fun saveSelectedSweetConnectionId(connectionId: String?) {
        context.identityDataStore.edit { preferences ->
            sanitizeStoredText(connectionId)?.let { preferences[Keys.selectedSweetConnectionId] = it }
                ?: preferences.remove(Keys.selectedSweetConnectionId)
        }
    }

    suspend fun clear() {
        context.identityDataStore.edit { preferences ->
            preferences.remove(Keys.memberId)
            preferences.remove(Keys.authToken)
            preferences.remove(Keys.phoneNumber)
            preferences.remove(Keys.googleEmail)
            preferences.remove(Keys.displayName)
            preferences.remove(Keys.gender)
            preferences.remove(Keys.profileEmojiUrl)
            preferences.remove(Keys.avatarUrl)
            preferences.remove(Keys.avatarMode)
            preferences.remove(Keys.twigiConfig)
            preferences.remove(Keys.twigiRenderUrl)
            preferences.remove(Keys.themeSongTitle)
            preferences.remove(Keys.themeSongUrl)
            preferences.remove(Keys.selectedAlarmConnectionId)
            preferences.remove(Keys.selectedSweetConnectionId)
            preferences.remove(Keys.lastBootstrapAt)
        }
    }

    private fun Preferences.toMemberIdentity(): MemberIdentity? {
        val memberId = sanitizeStoredText(this[Keys.memberId])
        val authToken = sanitizeStoredText(this[Keys.authToken])
        val phoneNumber = sanitizeStoredText(this[Keys.phoneNumber])
        val googleEmail = sanitizeStoredText(this[Keys.googleEmail])
        if (memberId.isNullOrBlank() || authToken.isNullOrBlank()) {
            return null
        }
        // Require either phone number (OTP auth) or Google email (Google auth)
        if (phoneNumber.isNullOrBlank() && googleEmail.isNullOrBlank()) {
            return null
        }

        return MemberIdentity(
            memberId = memberId,
            authToken = authToken,
            phoneNumber = phoneNumber,
            googleEmail = googleEmail,
            displayName = sanitizeStoredText(this[Keys.displayName]),
            gender = sanitizeStoredText(this[Keys.gender]),
            profileEmojiUrl = sanitizeStoredText(this[Keys.profileEmojiUrl]),
            avatarUrl = sanitizeStoredText(this[Keys.avatarUrl]),
            avatarMode = sanitizeStoredText(this[Keys.avatarMode])?.takeIf { it == "TWIGI" } ?: "EMOJI",
            twigiConfigJson = sanitizeStoredText(this[Keys.twigiConfig]),
            twigiRenderUrl = sanitizeStoredText(this[Keys.twigiRenderUrl]),
            themeSongTitle = sanitizeStoredText(this[Keys.themeSongTitle]),
            themeSongUrl = sanitizeStoredText(this[Keys.themeSongUrl]),
            profileComplete = !sanitizeStoredText(this[Keys.displayName]).isNullOrBlank() &&
                !sanitizeStoredText(this[Keys.gender]).isNullOrBlank(),
            lastBootstrapAt = this[Keys.lastBootstrapAt] ?: 0L
        )
    }
}
