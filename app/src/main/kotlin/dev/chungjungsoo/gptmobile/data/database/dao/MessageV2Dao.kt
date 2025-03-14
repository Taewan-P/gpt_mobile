package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2

@Dao
interface MessageV2Dao {

    @Query("SELECT * FROM messages_v2 WHERE chat_id=:chatInt")
    suspend fun loadMessages(chatInt: Int): List<MessageV2>

    @Query(
        "SELECT DISTINCT chat_id FROM messages_v2 " +
            "WHERE content LIKE '%' || :query || '%' OR " +
            "revisions LIKE '%' || :query || '%'"
    )
    suspend fun searchMessagesByContent(query: String): List<Int>

    @Insert
    suspend fun addMessages(vararg messages: MessageV2)

    @Update
    suspend fun editMessages(vararg message: MessageV2)

    @Delete
    suspend fun deleteMessages(vararg message: MessageV2)
}
