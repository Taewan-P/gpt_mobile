package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2

@Dao
interface ChatRoomV2Dao {

    @Query("SELECT * FROM chats_v2 ORDER BY updated_at DESC")
    suspend fun getChatRooms(): List<ChatRoomV2>

    @Insert
    suspend fun addChatRoom(chatRoom: ChatRoomV2): Long

    @Update
    suspend fun editChatRoom(chatRoom: ChatRoomV2)

    @Delete
    suspend fun deleteChatRooms(vararg chatRooms: ChatRoomV2)
}
