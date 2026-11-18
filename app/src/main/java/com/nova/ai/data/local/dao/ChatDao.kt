package com.nova.ai.data.local.dao
import androidx.room.*
import com.nova.ai.data.local.entity.ChatMessage
import kotlinx.coroutines.flow.Flow
@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): Flow<List<ChatMessage>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)
}
