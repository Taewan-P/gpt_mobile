package dev.chungjungsoo.gptmobile.data.repository

import androidx.work.WorkInfo
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import kotlinx.coroutines.flow.Flow

interface LocalModelRepository {
    fun observeAll(): Flow<List<LocalModel>>
    fun observeWorkInfos(): Flow<List<WorkInfo>>
    suspend fun getById(catalogEntryId: String): LocalModel?
    suspend fun resolveDownloadedPath(catalogEntryId: String): String?
    suspend fun startDownload(entry: CatalogEntry)
    suspend fun cancelDownload(catalogEntryId: String)
    suspend fun deleteModel(catalogEntryId: String)
    suspend fun totalStorageUsed(): Long
    suspend fun reconcile()
    suspend fun awaitActiveDownloadScheduling() = Unit
    fun diskPartialBytes(record: LocalModel): Long = 0L
}
