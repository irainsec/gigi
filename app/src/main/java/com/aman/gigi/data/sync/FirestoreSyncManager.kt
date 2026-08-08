package com.aman.gigi.data.sync

import android.util.Log
import com.aman.gigi.model.Connection
import com.aman.gigi.model.MemberIdentity
import com.aman.gigi.repository.ConnectionRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSyncManager @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tag = "FirestoreSyncManager"

    init {
        // Automatically start listening when the user signs in
        scope.launch {
            auth.addAuthStateListener { firebaseAuth ->
                val uid = firebaseAuth.currentUser?.uid
                if (uid != null) {
                    startSyncing(uid)
                } else {
                    stopSyncing()
                }
            }
        }
    }

    private fun startSyncing(uid: String) {
        Log.i(tag, "Starting Firestore sync for user: $uid")
        
        // Listen for user profile changes
        scope.launch {
            observeUserProfile(uid).collectLatest { profile ->
                // Update local profile store if needed
                // Note: IdentityStore usually handles this, but Firestore could be authoritative
            }
        }

        // Listen for connections
        scope.launch {
            observeConnections(uid).collectLatest { connections ->
                Log.d(tag, "Received ${connections.size} connections from Firestore")
                connectionRepository.reconcileWithServer(connections)
            }
        }
    }

    private fun stopSyncing() {
        Log.i(tag, "Stopping Firestore sync")
        // Listeners are cleared when the scope is cancelled or we can hold listener registrations
    }

    private fun observeUserProfile(uid: String): Flow<MemberIdentity?> = callbackFlow {
        val docRef = db.collection("users_v2").document(uid)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(tag, "User profile listen failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val identity = snapshot.toObject(MemberIdentity::class.java)
                trySend(identity)
            }
        }
        awaitClose { registration.remove() }
    }

    private fun observeConnections(uid: String): Flow<List<Connection>> = callbackFlow {
        // Query connections where this user is either creator or partner
        // For simplicity, we'll assume a "connections" collection where members array contains UID
        val query = db.collection("connections_v2")
            .whereArrayContains("memberIds", uid)
            
        val registration = query.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.e(tag, "Connections listen failed", error)
                return@addSnapshotListener
            }
            if (snapshots != null) {
                val list = snapshots.mapNotNull { doc ->
                    doc.toObject(Connection::class.java).copy(connectionId = doc.id)
                }
                trySend(list)
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun uploadProfile(identity: MemberIdentity) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("users_v2").document(uid)
                .set(identity, SetOptions.merge())
                .await()
            Log.i(tag, "Profile uploaded to Firestore")
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload profile", e)
        }
    }

    suspend fun updateConnectionStatus(connectionId: String, statusUpdates: Map<String, Any>) {
        try {
            db.collection("connections_v2").document(connectionId)
                .update(statusUpdates)
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to update connection status", e)
        }
    }
}
