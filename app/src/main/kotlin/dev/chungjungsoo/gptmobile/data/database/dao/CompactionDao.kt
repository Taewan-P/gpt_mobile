package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.chungjungsoo.gptmobile.data.database.entity.ContextCheckpointEntity
import dev.chungjungsoo.gptmobile.data.database.entity.ModelCapacityEntity

@Dao
interface CompactionDao {
    @Query("SELECT * FROM context_checkpoints WHERE chat_id = :chatId AND platform_uid = :platformUid")
    suspend fun getCheckpoint(chatId: Int, platformUid: String): ContextCheckpointEntity?

    @Upsert
    suspend fun upsertCheckpoint(entity: ContextCheckpointEntity)

    @Query("DELETE FROM context_checkpoints WHERE chat_id = :chatId AND platform_uid = :platformUid")
    suspend fun deleteCheckpoint(chatId: Int, platformUid: String)

    @Query("SELECT * FROM model_capacities WHERE platform_uid = :platformUid AND endpoint = :endpoint AND model = :model")
    suspend fun getCapacity(platformUid: String, endpoint: String, model: String): ModelCapacityEntity?

    @Upsert
    suspend fun upsertCapacity(entity: ModelCapacityEntity)
}
