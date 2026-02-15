package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2

@Dao
interface ChatPlatformModelV2Dao {

    @Query("SELECT * FROM chat_platform_model_v2 WHERE chat_id = :chatId")
    suspend fun getByChatId(chatId: Int): List<ChatPlatformModelV2>

    @Upsert
    suspend fun upsertAll(vararg models: ChatPlatformModelV2)

    @Query("DELETE FROM chat_platform_model_v2 WHERE chat_id = :chatId")
    suspend fun deleteByChatId(chatId: Int)

    @Query("DELETE FROM chat_platform_model_v2 WHERE platform_uid = :platformUid")
    suspend fun deleteByPlatformUid(platformUid: String)
}
