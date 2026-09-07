package dev.chungjungsoo.gptmobile.data.repository

import androidx.work.WorkInfo
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeLocalModelRepository(
    initialModels: List<LocalModel> = emptyList(),
    private val downloadedPaths: Map<String, String> = emptyMap()
) : LocalModelRepository {
    private val models = MutableStateFlow(initialModels)
    val startDownloadCalls = mutableListOf<String>()
    val cancelDownloadCalls = mutableListOf<String>()

    override fun observeAll(): Flow<List<LocalModel>> = models.asStateFlow()

    override fun observeWorkInfos(): Flow<List<WorkInfo>> = MutableStateFlow(emptyList())

    override suspend fun getById(catalogEntryId: String): LocalModel? = models.value.firstOrNull { it.catalogEntryId == catalogEntryId }

    override suspend fun resolveDownloadedPath(catalogEntryId: String): String? = downloadedPaths[catalogEntryId]

    override suspend fun startDownload(entry: CatalogEntry) {
        startDownloadCalls += entry.id
        val now = System.currentTimeMillis() / 1000
        val existing = models.value.firstOrNull { it.catalogEntryId == entry.id }
        val downloading = LocalModel(
            catalogEntryId = entry.id,
            commitHash = existing?.commitHash ?: "hash",
            fileName = existing?.fileName ?: "${entry.id}.litertlm",
            relativeDirectory = existing?.relativeDirectory ?: "models/${entry.id}/hash",
            totalBytes = if (entry.sizeInBytes > 0L) entry.sizeInBytes else existing?.totalBytes ?: 0L,
            status = LocalModelStatus.DOWNLOADING,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        models.value = models.value.filterNot { it.catalogEntryId == entry.id } + downloading
    }

    override suspend fun cancelDownload(catalogEntryId: String) {
        cancelDownloadCalls += catalogEntryId
    }

    override suspend fun deleteModel(catalogEntryId: String) = Unit

    fun setModels(next: List<LocalModel>) {
        models.value = next
    }

    override suspend fun totalStorageUsed(): Long = 0L

    override suspend fun reconcile() = Unit
}
