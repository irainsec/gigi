import re

file_path = 'app/src/main/java/com/aman/gigi/repository/ConnectionRepository.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'import com.aman.gigi.model.Connection',
    'import com.aman.gigi.model.Connection\nimport com.aman.gigi.model.ConnectionMember'
)

repo_code = """
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
"""

content = content.replace('suspend fun deleteAllConnections() {', repo_code + '\n    suspend fun deleteAllConnections() {')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated ConnectionRepository.kt")
