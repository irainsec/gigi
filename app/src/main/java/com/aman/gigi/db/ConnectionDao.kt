package com.aman.gigi.db

import androidx.room.*
import com.aman.gigi.model.Connection
import com.aman.gigi.model.ConnectionMember
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Connection operations
 */
@Dao
interface ConnectionDao {
    
    @Query("SELECT * FROM connections WHERE isActive = 1 ORDER BY lastSyncedAt DESC")
    fun getActiveConnections(): Flow<List<Connection>>
    
    @Query("SELECT * FROM connections WHERE isActive = 1 ORDER BY lastSyncedAt DESC")
    suspend fun getActiveConnectionsOnce(): List<Connection>

    @Query("SELECT * FROM connections ORDER BY lastSyncedAt DESC")
    suspend fun getAllConnectionsOnce(): List<Connection>
    
    @Query("SELECT * FROM connections WHERE isActive = 1 LIMIT 1")
    fun getActiveConnection(): Flow<Connection?>
    
    @Query("SELECT * FROM connections WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveConnectionOnceFirst(): Connection?
    
    @Query("SELECT * FROM connections WHERE connectionId = :connectionId")
    fun getConnectionByIdFlow(connectionId: String): Flow<Connection?>

    @Query("SELECT * FROM connections WHERE connectionId = :connectionId")
    suspend fun getConnectionById(connectionId: String): Connection?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: Connection)
    
    @Update
    suspend fun updateConnection(connection: Connection)
    
    @Query("UPDATE connections SET isActive = 0 WHERE connectionId = :connectionId")
    suspend fun deactivateConnection(connectionId: String)
    
    @Query("UPDATE connections SET lastSyncedAt = :timestamp WHERE connectionId = :connectionId")
    suspend fun updateLastSynced(connectionId: String, timestamp: Long)
    
    @Query("UPDATE connections SET connectionStatus = :status WHERE connectionId = :connectionId")
    suspend fun updateConnectionStatus(connectionId: String, status: String)

    @Query("UPDATE connections SET transportState = :transportState, connectionStatus = :connectionStatus WHERE connectionId = :connectionId")
    suspend fun updateTransportState(connectionId: String, transportState: String, connectionStatus: String)

    @Query("UPDATE connections SET partnerPresence = :partnerPresence, lastSeenAt = :lastSeenAt, connectionStatus = :connectionStatus WHERE connectionId = :connectionId")
    suspend fun updatePartnerPresence(
        connectionId: String,
        partnerPresence: String,
        lastSeenAt: Long?,
        connectionStatus: String
    )

    @Query("UPDATE connections SET isActive = 0, serverArchived = 1, lastSyncedAt = :timestamp WHERE connectionId NOT IN (:activeIds) AND isActive = 1")
    suspend fun archiveMissingActiveConnections(activeIds: List<String>, timestamp: Long)

    @Query("UPDATE connections SET isActive = 0, serverArchived = 1, lastSyncedAt = :timestamp WHERE isActive = 1")
    suspend fun archiveAllActiveConnections(timestamp: Long)
    
    @Query("DELETE FROM connections WHERE connectionId = :connectionId")
    suspend fun deleteConnection(connectionId: String)
    
    @Query("DELETE FROM connections")
    suspend fun deleteAllConnections()

    // --- Group Members ---
    
    @Query("SELECT * FROM connection_members WHERE connectionId = :connectionId ORDER BY joinedAt ASC")
    fun getMembersForConnectionFlow(connectionId: String): Flow<List<ConnectionMember>>
    
    @Query("SELECT * FROM connection_members WHERE connectionId = :connectionId ORDER BY joinedAt ASC")
    suspend fun getMembersForConnection(connectionId: String): List<ConnectionMember>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: ConnectionMember)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<ConnectionMember>)
    
    @Delete
    suspend fun deleteMember(member: ConnectionMember)
    
    @Query("DELETE FROM connection_members WHERE connectionId = :connectionId")
    suspend fun deleteAllMembersForConnection(connectionId: String)
}
