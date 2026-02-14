package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.chungjungsoo.gptmobile.data.database.entity.McpToolEventEntity

@Dao
interface McpToolEventDao {
    @Query("SELECT * FROM mcp_tool_events WHERE chat_id = :chatId ORDER BY id ASC")
    suspend fun getEventsByChatId(chatId: Int): List<McpToolEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: McpToolEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<McpToolEventEntity>)

    @Query("DELETE FROM mcp_tool_events WHERE chat_id = :chatId AND message_index = :messageIndex AND platform_index = :platformIndex")
    suspend fun deleteEvents(chatId: Int, messageIndex: Int, platformIndex: Int)

    @Query("DELETE FROM mcp_tool_events WHERE chat_id = :chatId")
    suspend fun deleteAllByChatId(chatId: Int)

    @Query(
        """
        UPDATE mcp_tool_events 
        SET output = :output, status = :status, is_error = :isError 
        WHERE chat_id = :chatId AND call_id = :callId
        """
    )
    suspend fun updateEvent(chatId: Int, callId: String, output: String, status: String, isError: Boolean)
}
