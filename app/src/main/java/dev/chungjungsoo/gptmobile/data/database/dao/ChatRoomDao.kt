package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom

@Dao
interface ChatRoomDao {

    @Query("SELECT * FROM chats")
    suspend fun getChatRooms(): List<ChatRoom>

    @Insert
    suspend fun addChatRoom(chatRoom: ChatRoom): Long

    @Update
    suspend fun editChatRoom(chatRoom: ChatRoom)

    @Delete
    suspend fun deleteChatRooms(vararg chatRooms: ChatRoom)
}
