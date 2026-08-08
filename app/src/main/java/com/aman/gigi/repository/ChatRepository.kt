package com.aman.gigi.repository

import com.aman.gigi.db.ChatDao
import com.aman.gigi.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    suspend fun save(message: ChatMessage) {
        chatDao.insert(message)
    }

    fun messagesFor(connectionId: String): Flow<List<ChatMessage>> {
        return chatDao.messagesFor(connectionId)
    }

    suspend fun clearAll() {
        chatDao.clearAll()
    }
}
