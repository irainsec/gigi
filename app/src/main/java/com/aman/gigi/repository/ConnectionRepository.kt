package com.aman.gigi.repository

import com.aman.gigi.db.ConnectionDao
import com.aman.gigi.model.Connection
import com.aman.gigi.model.ConnectionMember
import com.aman.gigi.model.ConnectionRole
import com.aman.gigi.model.ConnectionState
import com.aman.gigi.model.PartnerPresence
import com.aman.gigi.model.TransportState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing connections
 */
@Singleton
class ConnectionRepository @Inject constructor(
    private val connectionDao: ConnectionDao
) {
    
    /**
     * Get all active connections as Flow
     */
    fun getActiveConnections(): Flow<List<Connection>> {
        return connectionDao.getActiveConnections()
    }
    
    /**
     * Get active connection as Flow (first one)
     */
    fun getActiveConnection(): Flow<Connection?> {
        return connectionDao.getActiveConnection()
    }

    /**
     * Get connection by ID as Flow
     */
    fun getConnectionById(connectionId: String): Flow<Connection?> {
        return connectionDao.getConnectionByIdFlow(connectionId)
    }
    
    /**
     * Get connection state as Flow for the first active connection
     */
    fun getConnectionState(): Flow<ConnectionState> {
        return connectionDao.getActiveConnection().map { connection ->
            when {
                connection == null -> ConnectionState.NOT_CONNECTED
                connection.isActive -> {
                    val isPairing = connection.partnerDeviceId == "waiting..." || connection.partnerDeviceId == "joining..."
                    when (TransportState.entries.firstOrNull { it.name == connection.transportState } ?: TransportState.CONNECTING) {
                        TransportState.NO_INTERNET -> if (isPairing) ConnectionState.CONNECTING else ConnectionState.NO_INTERNET
                        TransportState.CONNECTING -> ConnectionState.CONNECTING
                        TransportState.CONNECTED -> if (isPairing) ConnectionState.CONNECTING else ConnectionState.CONNECTED
                    }
                }
                else -> ConnectionState.DISCONNECTED
            }
        }
    }
    
    /**
     * Get active connection once (suspend)
     */
    suspend fun getActiveConnectionOnce(): Connection? {
        return connectionDao.getActiveConnectionOnceFirst()
    }

    /**
     * Get all active connections once (suspend)
     */
    suspend fun getAllActiveConnectionsOnce(): List<Connection> {
        return connectionDao.getActiveConnectionsOnce()
    }

    suspend fun getAllConnectionsOnce(): List<Connection> {
        return connectionDao.getAllConnectionsOnce()
    }
    
    /**
     * Create a new connection (supporting multiple)
     */
    suspend fun createConnection(
        connectionId: String,
        partnerName: String,
        partnerDeviceId: String,
        connectionCode: String,
        creatorDeviceId: String? = null,
        role: ConnectionRole = ConnectionRole.PARTNER,
        memberId: String? = null,
        relationshipType: String = "ROMANTIC",
        isGroup: Boolean = false,
        partnerEmojiUrl: String? = null
    ): Connection {
        val lowerCode = connectionId.lowercase()
        val connection = Connection(
            connectionId = lowerCode,
            partnerName = partnerName,
            partnerDeviceId = partnerDeviceId,
            connectionCode = lowerCode,
            role = role.name,
            memberId = memberId,
            creatorDeviceId = creatorDeviceId,
            relationshipType = relationshipType,
            isGroup = isGroup,
            partnerEmojiUrl = partnerEmojiUrl,
            isActive = true,
            serverArchived = false,
            connectionStatus = deriveLegacyStatus(
                transportState = TransportState.CONNECTING,
                partnerPresence = PartnerPresence.UNKNOWN
            ),
            transportState = TransportState.CONNECTING.name,
            partnerPresence = PartnerPresence.UNKNOWN.name
        )
        
        connectionDao.insertConnection(connection)
        return connection
    }
    
    /**
     * Update connection
     */
    suspend fun updateConnection(connection: Connection) {
        connectionDao.updateConnection(connection)
    }

    suspend fun upsertConnection(connection: Connection) {
        connectionDao.insertConnection(connection)
    }

    suspend fun reconcileWithServer(
        serverConnections: List<Connection>,
        authoritative: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        
        if (authoritative) {
            android.util.Log.i("ConnectionRepo", "⚠️ Authoritative reconciliation requested. Performing full local wipe first.")
            connectionDao.deleteAllConnections()
        }

        val existingActiveConnections = connectionDao.getActiveConnectionsOnce()
        val mergedConnections = serverConnections.map { serverConnection ->
            val existing = connectionDao.getConnectionById(serverConnection.connectionId)
            serverConnection.copy(
                partnerName = serverConnection.partnerName.ifBlank { existing?.partnerName ?: "Partner" },
                partnerDeviceId = serverConnection.partnerDeviceId.ifBlank { existing?.partnerDeviceId ?: "" },
                partnerAvatarUrl = serverConnection.partnerAvatarUrl ?: existing?.partnerAvatarUrl,
                // Keep the live-synced emoji when the server doesn't send one — otherwise
                // every bootstrap wiped partner/group emojis back to the default.
                partnerEmojiUrl = serverConnection.partnerEmojiUrl ?: existing?.partnerEmojiUrl,
                partnerEmoji = if (serverConnection.partnerEmoji != "🌻") serverConnection.partnerEmoji else (existing?.partnerEmoji ?: "🌻"),
                partnerTwigiUrl = serverConnection.partnerTwigiUrl ?: existing?.partnerTwigiUrl,
                role = serverConnection.role.ifBlank { existing?.role ?: ConnectionRole.PARTNER.name },
                memberId = serverConnection.memberId ?: existing?.memberId,
                creatorDeviceId = serverConnection.creatorDeviceId ?: existing?.creatorDeviceId,
                anniversaryDate = existing?.anniversaryDate ?: serverConnection.anniversaryDate,
                meetingDate = existing?.meetingDate ?: serverConnection.meetingDate,
                createdAt = existing?.createdAt ?: serverConnection.createdAt,
                relationshipType = existing?.relationshipType ?: serverConnection.relationshipType,
                lastSyncedAt = now,
                isActive = !serverConnection.serverArchived,
                serverArchived = serverConnection.serverArchived
            )
        }

        mergedConnections.forEach { connectionDao.insertConnection(it) }

        val activeIds = mergedConnections
            .filter { !it.serverArchived }
            .map { it.connectionId }

        val missingActiveConnections = existingActiveConnections.filter { it.connectionId !in activeIds }
        val archiveCandidates = missingActiveConnections.filterNot(::shouldPreserveMissingLocalConnection)

        archiveCandidates.forEach { connection ->
            connectionDao.insertConnection(
                connection.copy(
                    isActive = false,
                    serverArchived = true,
                    lastSyncedAt = now
                )
            )
        }
    }

    private fun shouldPreserveMissingLocalConnection(connection: Connection): Boolean {
        // Always trust server authority. If the server no longer returns a connection
        // (e.g. deleted via admin panel), archive it locally regardless of creator role.
        return false
    }
    
    /**
     * Update last synced timestamp
     */
    suspend fun updateLastSynced(connectionId: String) {
        connectionDao.updateLastSynced(connectionId, System.currentTimeMillis())
    }

    /**
     * Update connection status
     */
    suspend fun updateConnectionStatus(connectionId: String, status: String) {
        connectionDao.updateConnectionStatus(connectionId, status)
    }

    suspend fun updateTransportState(connectionId: String, transportState: TransportState) {
        val existing = connectionDao.getConnectionById(connectionId) ?: return
        val partnerPresence = PartnerPresence.entries.firstOrNull { it.name == existing.partnerPresence } ?: PartnerPresence.UNKNOWN
        connectionDao.updateTransportState(
            connectionId = connectionId,
            transportState = transportState.name,
            connectionStatus = deriveLegacyStatus(transportState, partnerPresence)
        )
    }

    suspend fun updatePartnerPresence(connectionId: String, partnerPresence: PartnerPresence, lastSeenAt: Long?) {
        val existing = connectionDao.getConnectionById(connectionId) ?: return
        val transportState = TransportState.entries.firstOrNull { it.name == existing.transportState } ?: TransportState.CONNECTING
        connectionDao.updatePartnerPresence(
            connectionId = connectionId,
            partnerPresence = partnerPresence.name,
            lastSeenAt = lastSeenAt,
            connectionStatus = deriveLegacyStatus(transportState, partnerPresence)
        )
    }
    
    /**
     * Disconnect (deactivate connection)
     */
    suspend fun disconnect() {
        val connection = connectionDao.getActiveConnectionOnceFirst()
        connection?.let {
            connectionDao.deactivateConnection(it.connectionId)
        }
    }
    
    /**
     * Delete connection permanently
     */
    suspend fun deleteConnection(connectionId: String) {
        connectionDao.deleteConnection(connectionId)
    }
    
    /**
     * Delete all connections
     */
    
    // --- Group Members ---
    
    fun getMembersForConnectionFlow(connectionId: String): Flow<List<ConnectionMember>> {
        return connectionDao.getMembersForConnectionFlow(connectionId)
    }
    
    suspend fun getMembersForConnection(connectionId: String): List<ConnectionMember> {
        return connectionDao.getMembersForConnection(connectionId)
    }
    
    suspend fun saveMembers(members: List<ConnectionMember>) {
        connectionDao.insertMembers(members)
    }
    
    suspend fun saveMember(member: ConnectionMember) {
        connectionDao.insertMember(member)
    }

    suspend fun deleteMember(member: ConnectionMember) {
        connectionDao.deleteMember(member)
    }

    suspend fun deleteAllMembersForConnection(connectionId: String) {
        connectionDao.deleteAllMembersForConnection(connectionId)
    }

    suspend fun deleteAllConnections() {
        connectionDao.deleteAllConnections()
    }

    private fun deriveLegacyStatus(transportState: TransportState, partnerPresence: PartnerPresence): String {
        return when {
            transportState == TransportState.NO_INTERNET -> "NO_INTERNET"
            transportState == TransportState.CONNECTING -> "DISCONNECTED"
            partnerPresence == PartnerPresence.OFFLINE -> "PARTNER_OFFLINE"
            else -> "CONNECTED"
        }
    }
}
