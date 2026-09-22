package dev.chungjungsoo.gptmobile.presentation.ui.localmodel

import androidx.work.WorkInfo
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.ResolvedModelDownload
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.presentation.ui.setup.FakeHuggingFaceAuthClient
import dev.chungjungsoo.gptmobile.presentation.ui.setup.FakeLocalDownloadGuards
import dev.chungjungsoo.gptmobile.presentation.ui.setup.MapSecretVault
import dev.chungjungsoo.gptmobile.presentation.ui.setup.wizardGatedCoordinator
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalModelDownloadActionsTest {
    @Test
    fun acceptedRequest_isNotReplacedWhileItsRepositoryLookupIsSuspended() = runTest {
        val repository = SuspendedLookupRepository()
        val actions = LocalModelDownloadActions(
            localModelRepository = repository,
            gatedDownloadCoordinator = wizardGatedCoordinator(),
            huggingFaceTokenStore = HuggingFaceTokenStore(MapSecretVault()),
            downloadGuards = FakeLocalDownloadGuards(),
            huggingFaceAuthClient = FakeHuggingFaceAuthClient(),
            scope = this
        )
        val first = entry("first")
        val second = entry("second")

        actions.requestDownload(first)
        runCurrent()
        assertTrue(repository.firstLookupStarted.isCompleted)

        actions.requestDownload(second)
        repository.releaseFirstLookup.complete(Unit)
        runCurrent()

        assertEquals(listOf("first"), repository.startedDownloads)
    }

    private fun entry(id: String) = CatalogEntry(
        id = id,
        downloadUrl = "https://example.com/$id/resolve/hash/$id.litertlm",
        sizeInBytes = 10L,
        supportedAccelerators = listOf("cpu")
    )
}

private class SuspendedLookupRepository : LocalModelRepository {
    val firstLookupStarted = CompletableDeferred<Unit>()
    val releaseFirstLookup = CompletableDeferred<Unit>()
    val startedDownloads = CopyOnWriteArrayList<String>()

    override fun observeAll(): Flow<List<LocalModel>> = flowOf(emptyList())
    override fun observeWorkInfos(): Flow<List<WorkInfo>> = flowOf(emptyList())

    override suspend fun getById(catalogEntryId: String): LocalModel? {
        if (catalogEntryId == "first") {
            firstLookupStarted.complete(Unit)
            releaseFirstLookup.await()
        }
        return null
    }

    override suspend fun resolveDownloadedPath(catalogEntryId: String): String? = null
    override suspend fun startDownload(entry: CatalogEntry) {
        startedDownloads += entry.id
    }
    override suspend fun startDownload(entry: CatalogEntry, resolved: ResolvedModelDownload) = startDownload(entry)
    override suspend fun cancelDownload(catalogEntryId: String) = Unit
    override suspend fun deleteModel(catalogEntryId: String) = Unit
    override suspend fun totalStorageUsed(): Long = 0L
    override suspend fun reconcile() = Unit
}
