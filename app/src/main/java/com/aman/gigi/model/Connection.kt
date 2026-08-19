package com.aman.gigi.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a connection between two users
 */
@Entity(tableName = "connections")
data class Connection(
    @PrimaryKey
    val connectionId: String = "",
    val isGroup: Boolean = false,
    val partnerName: String = "",
    val partnerEmoji: String = "🌻",
    // The partner's chosen animated emoji (asset/URL). Synced live via profile_update.
    val partnerEmojiUrl: String? = null,
    // Which identity the partner shows (EMOJI or TWIGI) + their Twigi render URL.
    val partnerAvatarMode: String = "EMOJI",
    val partnerTwigiUrl: String? = null,
    val partnerDeviceId: String = "",
    val connectionCode: String = "",
    val partnerAvatarUrl: String? = null,
    val role: String = ConnectionRole.PARTNER.name,
    val memberId: String? = null,
    val isActive: Boolean = true,
    val serverArchived: Boolean = false,
    val connectionStatus: String = "CONNECTED", // New: CONNECTED, DISCONNECTED, NO_INTERNET, PARTNER_OFFLINE
    val transportState: String = TransportState.CONNECTING.name,
    val partnerPresence: String = PartnerPresence.UNKNOWN.name,
    val lastSeenAt: Long? = null,
    val restoredAt: Long? = null,
    val partnerLatitude: Double? = null,
    val partnerLongitude: Double? = null,
    val partnerLocationName: String? = null,
    val anniversaryDate: Long? = null,
    val meetingDate: Long? = null,
    val creatorDeviceId: String? = null,
    val relationshipType: String = "ROMANTIC", // ROMANTIC, FRIENDSHIP, FAMILY
    val origin: String = "INVITE", // INVITE or NEBULA
    val trustRing: Int = 0, // 0 = My Heart, 1 = Close Ones, 2 = Dear Stars, 3 = Faraway
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * Connection state for UI
 */
enum class ConnectionState {
    NOT_CONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    NO_INTERNET,
    ERROR
}

enum class TransportState {
    NO_INTERNET,
    CONNECTING,
    CONNECTED
}

enum class PartnerPresence {
    UNKNOWN,
    ONLINE,
    OFFLINE
}

enum class ConnectionRole {
    CREATOR,
    PARTNER
}

enum class ServerMode {
    ONLINE,
    MAINTENANCE,
    OFFLINE
}

data class ServerStatus(
    val mode: ServerMode = ServerMode.OFFLINE,
    val message: String? = null,
    val lastCheckedAt: Long = System.currentTimeMillis()
)

data class MemberIdentity(
    val memberId: String = "",
    val authToken: String = "",
    val phoneNumber: String? = null,
    val googleEmail: String? = null,
    val displayName: String? = null,
    val gender: String? = null,
    val avatarUrl: String? = null,
    val emoji: String = "🌻",
    // The chosen animated profile emoji (asset/URL), persisted server-side.
    val profileEmojiUrl: String? = null,
    // ── Twigi (layered 2D avatar) ──
    // Which identity this member shows to others (EMOJI or TWIGI); both are kept.
    val avatarMode: String = "EMOJI",
    // Part/color selections as raw JSON (for re-editing).
    val twigiConfigJson: String? = null,
    // Server URL of the composited PNG — what other devices actually display.
    val twigiRenderUrl: String? = null,
    // Per-user client settings blob (raw JSON string) restored from the server.
    val prefsBlob: String? = null,
    val themeSongTitle: String? = null,
    val themeSongUrl: String? = null,
    val dateOfBirth: String? = null,
    val discoverable: Boolean = false,
    val handle: String? = null,
    val bio: String? = null,
    val nebulaSeed: Int = 42,
    val profileComplete: Boolean = false,
    val lastBootstrapAt: Long = System.currentTimeMillis()
)

/**
 * Represents a public discoverable user drifting in the Cosmic Nebula.
 */
data class NebulaMember(
    val memberId: String = "",
    val handle: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val twigiRenderUrl: String? = null,
    val profileEmojiUrl: String? = null,
    val bio: String? = null,
    val nebulaSeed: Int = 42,
    val isRecentlyActive: Boolean = false,
    val inviteStatus: String = "NONE" // NONE, SENT, RECEIVED
)
