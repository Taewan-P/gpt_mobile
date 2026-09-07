package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalModelDao {
    @Query("SELECT * FROM local_models ORDER BY catalog_entry_id")
    fun observeAll(): Flow<List<LocalModel>>

    @Query("SELECT * FROM local_models ORDER BY catalog_entry_id")
    suspend fun getAll(): List<LocalModel>

    @Query("SELECT * FROM local_models WHERE catalog_entry_id = :catalogEntryId")
    suspend fun getById(catalogEntryId: String): LocalModel?

    @Upsert
    suspend fun upsert(model: LocalModel)

    @Query("UPDATE local_models SET status = :status, updated_at = :updatedAt WHERE catalog_entry_id = :catalogEntryId")
    suspend fun updateStatus(catalogEntryId: String, status: String, updatedAt: Long)

    @Query("DELETE FROM local_models WHERE catalog_entry_id = :catalogEntryId")
    suspend fun deleteById(catalogEntryId: String)
}
