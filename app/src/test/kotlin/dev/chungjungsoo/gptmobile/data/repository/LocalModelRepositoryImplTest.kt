package dev.chungjungsoo.gptmobile.data.repository

import android.content.ContextWrapper
import androidx.work.WorkInfo
import dev.chungjungsoo.gptmobile.data.database.dao.LocalModelDao
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelRepositoryImplTest {

    @Test
    fun `reconcile runs on the injected dispatcher`() = runTest {
        val onInjectedDispatcher = ThreadLocal<Boolean>()
        val recordingDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                onInjectedDispatcher.set(true)
                try {
                    block.run()
                } finally {
                    onInjectedDispatcher.remove()
                }
            }
        }
        var listedOnInjectedDispatcher = false

        repository(
            ioDispatcher = recordingDispatcher,
            diskFiles = {
                listedOnInjectedDispatcher = onInjectedDispatcher.get() == true
                emptySet()
            }
        ).reconcile()

        assertTrue(listedOnInjectedDispatcher)
    }

    @Test
    fun `awaitActiveDownloadScheduling returns immediately when no work is pending`() = runTest {
        repository(workInfos = { flowOf(emptyList()) }).awaitActiveDownloadScheduling()
    }

    @Test
    fun `reconcile is skipped when external storage is unavailable`() = runTest {
        val dao = RecordingLocalModelDao(
            listOf(
                LocalModel(
                    catalogEntryId = "ready-model",
                    commitHash = "abc123",
                    fileName = "model.litertlm",
                    relativeDirectory = "models/ready-model/abc123",
                    totalBytes = 10L,
                    status = LocalModelStatus.READY
                )
            )
        )

        repository(
            localModelDao = dao,
            externalFilesDir = { null },
            diskFiles = { emptySet() }
        ).reconcile()

        assertTrue(dao.deletedIds.isEmpty())
    }

    private fun repository(
        ioDispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        },
        localModelDao: LocalModelDao = EmptyLocalModelDao(),
        diskFiles: () -> Set<String> = { emptySet() },
        workInfos: () -> Flow<List<WorkInfo>> = { flowOf(emptyList()) },
        externalFilesDir: (() -> File?)? = { File("/tmp") }
    ) = LocalModelRepositoryImpl(
        context = ContextWrapper(null),
        localModelDao = localModelDao,
        deviceSocModel = "",
        ioDispatcher = ioDispatcher,
        diskFiles = diskFiles,
        workInfos = workInfos,
        externalFilesDir = externalFilesDir
    )
}

private class EmptyLocalModelDao : LocalModelDao {
    override fun observeAll(): Flow<List<LocalModel>> = MutableStateFlow(emptyList())
    override suspend fun getAll(): List<LocalModel> = emptyList()
    override suspend fun getById(catalogEntryId: String): LocalModel? = null
    override suspend fun upsert(model: LocalModel) = Unit
    override suspend fun updateStatus(catalogEntryId: String, status: String, updatedAt: Long) = Unit
    override suspend fun deleteById(catalogEntryId: String) = Unit
}

private class RecordingLocalModelDao(
    private val rows: List<LocalModel>
) : LocalModelDao {
    val deletedIds = mutableListOf<String>()

    override fun observeAll(): Flow<List<LocalModel>> = MutableStateFlow(rows)
    override suspend fun getAll(): List<LocalModel> = rows
    override suspend fun getById(catalogEntryId: String): LocalModel? = rows.firstOrNull { it.catalogEntryId == catalogEntryId }
    override suspend fun upsert(model: LocalModel) = Unit
    override suspend fun updateStatus(catalogEntryId: String, status: String, updatedAt: Long) = Unit
    override suspend fun deleteById(catalogEntryId: String) {
        deletedIds += catalogEntryId
    }
}
