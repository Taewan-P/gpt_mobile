package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.dao.LocalModelDao
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelDownloadPaths
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelReconciler
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localmodel.ReconcileAction
import dev.chungjungsoo.gptmobile.data.localmodel.SocVariantResolver
import dev.chungjungsoo.gptmobile.data.worker.LocalModelDownloadWorker
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LocalModelRepositoryImpl(
    private val context: Context,
    private val localModelDao: LocalModelDao,
    private val deviceSocModel: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val diskFiles: (() -> Set<String>)? = null,
    private val workInfos: (() -> Flow<List<WorkInfo>>)? = null,
    private val externalFilesDir: (() -> File?)? = null
) : LocalModelRepository {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun observeAll(): Flow<List<LocalModel>> = localModelDao.observeAll()

    override fun observeWorkInfos(): Flow<List<WorkInfo>> = workInfosFlow()

    override suspend fun getById(catalogEntryId: String): LocalModel? = localModelDao.getById(catalogEntryId)

    override suspend fun resolveDownloadedPath(catalogEntryId: String): String? = withContext(ioDispatcher) {
        val model = localModelDao.getById(catalogEntryId) ?: return@withContext null
        if (model.status != LocalModelStatus.READY) return@withContext null
        val file = File(
            storageRoot(),
            LocalModelDownloadPaths.relativeFilePath(model.catalogEntryId, model.commitHash, model.fileName)
        )
        file.takeIf { it.exists() }?.absolutePath
    }

    override suspend fun startDownload(entry: CatalogEntry) {
        withContext(ioDispatcher) {
            val existing = localModelDao.getById(entry.id)
            if (existing?.status == LocalModelStatus.DOWNLOADING && entry.id in activeDownloadIds()) {
                return@withContext
            }
            val resolved = SocVariantResolver.resolve(entry, deviceSocModel)
            LocalModelDownloadPaths.requireValidPathSegments(entry.id, resolved.commitHash, resolved.fileName)
            val relativeDirectory = LocalModelDownloadPaths.relativeDirectory(entry.id, resolved.commitHash)
            val now = System.currentTimeMillis() / 1000
            localModelDao.upsert(
                LocalModel(
                    catalogEntryId = entry.id,
                    commitHash = resolved.commitHash,
                    fileName = resolved.fileName,
                    relativeDirectory = relativeDirectory,
                    totalBytes = resolved.sizeInBytes,
                    status = LocalModelStatus.DOWNLOADING,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )

            val inputData = Data.Builder()
                .putString(LocalModelDownloadWorker.KEY_CATALOG_ENTRY_ID, entry.id)
                .putString(LocalModelDownloadWorker.KEY_DISPLAY_NAME, entry.displayName)
                .putString(LocalModelDownloadWorker.KEY_DOWNLOAD_URL, resolved.downloadUrl)
                .putString(LocalModelDownloadWorker.KEY_COMMIT_HASH, resolved.commitHash)
                .putString(LocalModelDownloadWorker.KEY_FILE_NAME, resolved.fileName)
                .putLong(LocalModelDownloadWorker.KEY_TOTAL_BYTES, resolved.sizeInBytes)
                .putBoolean(LocalModelDownloadWorker.KEY_REQUIRES_HF_AUTH, entry.isGated)
                .build()

            val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    LocalModelDownloadWorker.INITIAL_BACKOFF_SECONDS,
                    TimeUnit.SECONDS
                )
                .setInputData(inputData)
                .addTag(LocalModelDownloadWorker.WORK_TAG)
                .addTag(LocalModelDownloadWorker.idTag(entry.id))
                .build()

            workManager.enqueueUniqueWork(
                LocalModelDownloadPaths.uniqueWorkName(entry.id),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun cancelDownload(catalogEntryId: String) {
        withContext(ioDispatcher) {
            workManager.cancelUniqueWork(LocalModelDownloadPaths.uniqueWorkName(catalogEntryId))
            val row = localModelDao.getById(catalogEntryId) ?: return@withContext
            val plan = LocalModelReconciler.planUserCancel()
            if (plan.deleteFiles) {
                File(storageRoot(), row.relativeDirectory).deleteRecursively()
            }
            if (plan.deleteRow) {
                localModelDao.deleteById(catalogEntryId)
            } else if (row.status == LocalModelStatus.DOWNLOADING) {
                localModelDao.updateStatus(
                    catalogEntryId = catalogEntryId,
                    status = plan.newStatus,
                    updatedAt = System.currentTimeMillis() / 1000
                )
            }
        }
    }

    override suspend fun deleteModel(catalogEntryId: String) {
        withContext(ioDispatcher) {
            LocalModelDownloadPaths.requireValidPathSegments(catalogEntryId)
            workManager.cancelUniqueWork(LocalModelDownloadPaths.uniqueWorkName(catalogEntryId))
            val row = localModelDao.getById(catalogEntryId)
            if (row != null) {
                File(storageRoot(), row.relativeDirectory).deleteRecursively()
                localModelDao.deleteById(catalogEntryId)
                File(storageRoot(), LocalModelDownloadPaths.MODELS_DIR)
                    .resolve(catalogEntryId)
                    .takeIf { it.isDirectory && it.list().isNullOrEmpty() }
                    ?.delete()
            }
        }
    }

    override suspend fun totalStorageUsed(): Long = withContext(ioDispatcher) {
        localModelDao.getAll()
            .filter { it.status == LocalModelStatus.READY }
            .sumOf { diskBytes(it) }
    }

    override fun diskPartialBytes(record: LocalModel): Long {
        val file = File(
            storageRoot(),
            LocalModelDownloadPaths.relativePartialFilePath(record.catalogEntryId, record.commitHash, record.fileName)
        )
        return file.takeIf { it.exists() }?.length() ?: 0L
    }

    override suspend fun reconcile() {
        withContext(ioDispatcher) {
            if (externalStorageRoot() == null) {
                runCatching { Log.w(TAG, "Skipping Local Model reconcile: external storage unavailable") }
                return@withContext
            }
            val actions = LocalModelReconciler.reconcile(
                rows = localModelDao.getAll().map { it.toRecord() },
                diskFiles = diskFilesOrDefault(),
                activeDownloadIds = activeDownloadIds()
            )
            val now = System.currentTimeMillis() / 1000
            actions.forEach { action ->
                when (action) {
                    is ReconcileAction.DeleteRow -> localModelDao.deleteById(action.catalogEntryId)

                    is ReconcileAction.MarkFailed -> localModelDao.updateStatus(
                        catalogEntryId = action.catalogEntryId,
                        status = LocalModelStatus.FAILED,
                        updatedAt = now
                    )

                    is ReconcileAction.DeleteFile -> File(storageRoot(), action.relativePath).delete()
                }
            }
        }
    }

    override suspend fun awaitActiveDownloadScheduling() = withContext(ioDispatcher) {
        val snapshot = runCatching { workInfosFlow().first() }.getOrDefault(emptyList())
        val unfinished = snapshot.filter { !it.state.isFinished }
        if (unfinished.isEmpty() || unfinished.any { it.state == WorkInfo.State.RUNNING }) {
            return@withContext
        }
        withTimeoutOrNull(JOB_DELIVERY_TIMEOUT_MS) {
            workInfosFlow().first { infos ->
                val active = infos.filter { !it.state.isFinished }
                active.isEmpty() || active.any { it.state == WorkInfo.State.RUNNING }
            }
        }
        Unit
    }

    private fun externalStorageRoot(): File? = if (externalFilesDir != null) {
        externalFilesDir.invoke()
    } else {
        context.getExternalFilesDir(null)
    }

    private fun storageRoot(): File = externalStorageRoot() ?: context.filesDir

    private fun diskFilesOrDefault(): Set<String> = diskFiles?.invoke() ?: listModelFiles()

    private fun workInfosFlow(): Flow<List<WorkInfo>> = workInfos?.invoke()
        ?: WorkManager.getInstance(context).getWorkInfosByTagFlow(LocalModelDownloadWorker.WORK_TAG)

    private fun listModelFiles(): Set<String> {
        val root = storageRoot()
        val modelsDir = File(root, LocalModelDownloadPaths.MODELS_DIR)
        if (!modelsDir.exists()) return emptySet()
        return modelsDir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()
    }

    private suspend fun activeDownloadIds(): Set<String> {
        val infos = runCatching { workInfosFlow().first() }.getOrDefault(emptyList())
        return infos
            .filter { !it.state.isFinished }
            .mapNotNull { info ->
                info.tags.firstNotNullOfOrNull(LocalModelDownloadWorker::catalogEntryIdFromTag)
            }
            .toSet()
    }

    private fun diskBytes(model: LocalModel): Long {
        val file = File(storageRoot(), LocalModelDownloadPaths.relativeFilePath(model.catalogEntryId, model.commitHash, model.fileName))
        return if (file.exists()) file.length() else model.totalBytes
    }

    private companion object {
        const val JOB_DELIVERY_TIMEOUT_MS = 2_000L
        private const val TAG = "LocalModelRepository"
    }
}
