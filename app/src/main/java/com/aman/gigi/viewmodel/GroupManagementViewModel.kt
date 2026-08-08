package com.aman.gigi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.gigi.data.sync.ScribbleSyncManager
import com.aman.gigi.model.Connection
import com.aman.gigi.model.ConnectionMember
import com.aman.gigi.repository.ConnectionRepository
import com.aman.gigi.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class GroupManagementViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val syncManager: ScribbleSyncManager,
    private val bootstrapManager: com.aman.gigi.data.client.ConnectionBootstrapManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _groupConnection = MutableStateFlow<Connection?>(null)
    val groupConnection: StateFlow<Connection?> = _groupConnection

    private val _members = MutableStateFlow<List<ConnectionMember>>(emptyList())
    val members: StateFlow<List<ConnectionMember>> = _members

    private val _leaveComplete = MutableStateFlow(false)
    val leaveComplete: StateFlow<Boolean> = _leaveComplete

    val currentDeviceId: String get() = syncManager.deviceId

    private var membersJob: kotlinx.coroutines.Job? = null

    fun loadGroup(connectionId: String) {
        viewModelScope.launch {
            val connection = connectionRepository.getAllActiveConnectionsOnce().find { it.connectionId == connectionId }
            _groupConnection.value = connection
        }
        membersJob?.cancel()
        membersJob = viewModelScope.launch {
            connectionRepository.getMembersForConnectionFlow(connectionId).collect {
                _members.value = it
            }
        }
    }

    fun updateGroupName(newName: String) {
        val currentGroup = _groupConnection.value ?: return
        viewModelScope.launch {
            val updated = currentGroup.copy(partnerName = newName)
            connectionRepository.updateConnection(updated)
            _groupConnection.value = updated
            syncManager.sendRemoteCommandWithData(
                connectionId = currentGroup.connectionId,
                command = Constants.COMMAND_GROUP_NAME_CHANGED,
                data = JSONObject().apply { put("newName", newName) }
            )
        }
    }

    fun removeMember(memberDeviceId: String) {
        val group = _groupConnection.value ?: return
        val member = _members.value.find { it.memberDeviceId == memberDeviceId } ?: return
        viewModelScope.launch {
            connectionRepository.deleteMember(member)
            syncManager.sendRemoteCommandWithData(
                connectionId = group.connectionId,
                command = Constants.COMMAND_MEMBER_REMOVED,
                data = JSONObject().apply { put("memberDeviceId", memberDeviceId) },
                targetDeviceId = memberDeviceId
            )
            _members.value = connectionRepository.getMembersForConnection(group.connectionId)
        }
    }

    fun leaveGroup() {
        val group = _groupConnection.value ?: return
        viewModelScope.launch {
            // Delete/leave on the server over HTTP — authoritative and tombstoned, so a
            // racing socket reconnect can't resurrect the group. (No WS disconnect frame:
            // that path used to hard-delete the record and let create_connection re-create it.)
            bootstrapManager.archiveConnectionOnServer(group.connectionId)
            connectionRepository.deleteAllMembersForConnection(group.connectionId)
            connectionRepository.deleteConnection(group.connectionId)
            _leaveComplete.value = true
        }
    }

    /** Set the group's shared animated emoji — local + server (synced to every member). */
    fun setGroupEmoji(emojiUrl: String) {
        val group = _groupConnection.value ?: return
        viewModelScope.launch {
            val updated = group.copy(partnerEmojiUrl = emojiUrl)
            connectionRepository.updateConnection(updated)
            _groupConnection.value = updated
            // Keep the galaxy's local pick in step so the planet updates instantly.
            context.getSharedPreferences("galaxy_orbits", android.content.Context.MODE_PRIVATE)
                .edit().putString("emoji_${group.connectionId}", emojiUrl).apply()
            bootstrapManager.setGroupEmojiOnServer(group.connectionId, emojiUrl)
        }
    }
}
