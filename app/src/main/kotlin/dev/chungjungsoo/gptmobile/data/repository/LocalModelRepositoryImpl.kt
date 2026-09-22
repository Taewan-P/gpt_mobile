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
import androidx.work.await
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.dao.LocalModelDao
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelDownloadPaths
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelFileAccess
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelReconciler
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStorage
import dev.chungjungsoo.gptmobile.data.localmodel.ReconcileAction
import dev.chungjungsoo.gptmobile.data.localmodel.ResolvedModelDownload
import dev.chungjungsoo.gptmobile.data.localmodel.SocVariantResolver
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.matches
import dev.chungjungsoo.gptmobile.data.worker.LocalModelDownloadWorker
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LocalModelRepositoryImpl(
    private val context: Context,
    private val localModelDao: LocalModelDao,
    private val deviceSocModel: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val diskFiles: (() -> Set<String>)? = null,
    private val workInfos: (() -> Flow<List<WorkInfo>>)? = null,
    private val externalFilesDir: (() -> File?)? = null,
    private val localRuntime: LocalRuntime? = null,
    private val internalFilesDir: (() -> File)? = null
) : LocalModelRepository {

    private val schedulingMutex = Mutex()

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun observeAll(): Flow<List<LocalModel>> = localModelDao.observeAll()

    override fun observeWorkInfos(): Flow<List<WorkInfo>> = workInfosFlow()

    override suspend fun getById(catalogEntryId: String): LocalModel? = localModelDao.getById(catalogEntryId)

    override suspend fun resolveDownloadedPath(catalogEntryId: String): String? = withContext(ioDispatcher) {
        val model = localModelDao.getById(catalogEntryId) ?: return@withContext null
        if (model.status != LocalModelStatus.READY) return@withContext null
        val file = storage().find(LocalModelDownloadPaths.relativeFilePath(model.catalogEntryId, model.commitHash, model.fileName), model.totalBytes) ?: return@withContext null
        file.takeIf { it.isFile && (model.totalBytes <= 0L || it.length() == model.totalBytes) }?.absolutePath
    }

    override suspend fun startDownload(entry: CatalogEntry) = startDownload(entry, SocVariantResolver.resolve(entry, deviceSocModel))

    override suspend fun startDownload(entry: CatalogEntry, resolved: ResolvedModelDownload) = schedulingMutex.withLock {
        withContext(ioDispatcher) {
            val existing = localModelDao.getById(entry.id)
            check(entry.id !in activeDownloadIds()) { "A download for this model is already running. Cancel it or wait for it to finish before replacing the model." }
            LocalModelDownloadPaths.requireValidPathSegments(entry.id, resolved.commitHash, resolved.fileName)
            require(resolved.downloadUrl.startsWith("https://")) { "Local Model download requires HTTPS" }
            require(resolved.sizeInBytes > 0L) { "Local Model download size is missing" }
            if (existing?.status == LocalModelStatus.READY && resolved.matches(existing) && resolveDownloadedPath(entry.id) != null) return@withContext
            val partial = File(storageRoot(), LocalModelDownloadPaths.relativePartialFilePath(entry.id, resolved.commitHash, resolved.fileName))
            val requiredBytes = (resolved.sizeInBytes - partial.length()).coerceAtLeast(0L)
            require(storageRoot().usableSpace >= requiredBytes) { "Not enough space to download the replacement while keeping the current model" }
            val relativeDirectory = LocalModelDownloadPaths.relativeDirectory(entry.id, resolved.commitHash)
            val now = System.currentTimeMillis() / 1000
            if (existing?.status != LocalModelStatus.READY) {
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
            }

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
                ExistingWorkPolicy.KEEP,
                request
            ).await()
        }
    }

    override suspend fun cancelDownload(catalogEntryId: String) = schedulingMutex.withLock {
        withContext(ioDispatcher) {
            workManager.cancelUniqueWork(LocalModelDownloadPaths.uniqueWorkName(catalogEntryId)).await()
            val markCancelled: suspend () -> Unit = {
                val row = localModelDao.getById(catalogEntryId)
                if (row?.status == LocalModelStatus.DOWNLOADING) {
                    localModelDao.updateStatus(catalogEntryId, LocalModelStatus.FAILED, System.currentTimeMillis() / 1000)
                }
            }
            val runtime = localRuntime
            LocalModelFileAccess.withLock {
                if (runtime == null) markCancelled() else runtime.runExclusive { markCancelled() }
                val retained = localModelDao.getById(catalogEntryId)
                if (retained?.status == LocalModelStatus.READY) cleanupNonRetainedFiles(retained)
            }
        }
    }

    override suspend fun deleteModel(catalogEntryId: String) = schedulingMutex.withLock {
        withContext(ioDispatcher) {
            LocalModelDownloadPaths.requireValidPathSegments(catalogEntryId)
            workManager.cancelUniqueWork(LocalModelDownloadPaths.uniqueWorkName(catalogEntryId)).await()
            LocalModelFileAccess.withLock {
                withModelLock {
                    val row = localModelDao.getById(catalogEntryId)
                    if (row != null) {
                        storage().files().filter { LocalModelDownloadPaths.catalogEntryIdFromRelativePath(it) == catalogEntryId }
                            .forEach { storage().delete(it) }
                        localModelDao.deleteById(catalogEntryId)
                        File(storageRoot(), LocalModelDownloadPaths.MODELS_DIR)
                            .resolve(catalogEntryId)
                            .takeIf { it.isDirectory && it.list().isNullOrEmpty() }
                            ?.delete()
                    }
                }
            }
        }
    }

    private suspend fun withModelLock(block: suspend () -> Unit) {
        val runtime = localRuntime
        if (runtime == null) {
            block()
        } else {
            runtime.runExclusive {
                unloadEngine()
                block()
            }
        }
    }

    override suspend fun totalStorageUsed(): Long = withContext(ioDispatcher) {
        localModelDao.getAll()
            .filter { it.status == LocalModelStatus.READY }
            .sumOf { diskBytes(it) }
    }

    override fun diskPartialBytes(record: LocalModel): Long {
        if (record.status == LocalModelStatus.READY) return 0L
        val file = File(
            storageRoot(),
            LocalModelDownloadPaths.relativePartialFilePath(record.catalogEntryId, record.commitHash, record.fileName)
        )
        return file.takeIf { it.exists() }?.length() ?: 0L
    }

    override suspend fun reconcile() = schedulingMutex.withLock {
        withContext(ioDispatcher) {
            if (externalStorageRoot() == null) {
                runCatching { Log.w(TAG, "Skipping Local Model reconcile: external storage unavailable") }
                return@withContext
            }
            val reconcileFiles: suspend () -> Unit = {
                val active = activeDownloadIds()
                if (diskFiles == null) {
                    localModelDao.getAll().filter { it.status == LocalModelStatus.READY && it.catalogEntryId !in active }.forEach { model ->
                        val relative = LocalModelDownloadPaths.relativeFilePath(model.catalogEntryId, model.commitHash, model.fileName)
                        if (externalStorageRoot()?.let { File(it, relative).isFile } == true) {
                            runCatching {
                                localRuntime?.unloadEngine()
                                storage().migrate(relative, model.totalBytes)
                            }.onFailure { error ->
                                if (error is kotlinx.coroutines.CancellationException) throw error
                                Log.w(TAG, "Keeping existing Local Model after migration failure", error)
                            }
                        }
                    }
                }
                val actions = LocalModelReconciler.reconcile(
                    rows = localModelDao.getAll().map { it.toRecord() },
                    diskFiles = diskFilesOrDefault(),
                    activeDownloadIds = active
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

                        is ReconcileAction.DeleteFile -> storage().delete(action.relativePath)
                    }
                }
                // Remove artifacts left by a crash after publishing the replacement but before cleanup.
                localModelDao.getAll().filter { it.status == LocalModelStatus.READY && it.catalogEntryId !in active }.forEach { row ->
                    cleanupNonRetainedFiles(row)
                }
            }
            val runtime = localRuntime
            LocalModelFileAccess.whenIdle {
                if (runtime == null) reconcileFiles() else runtime.runExclusive { reconcileFiles() }
            }
        }
    }

    private fun cleanupNonRetainedFiles(row: LocalModel) {
        val retained = LocalModelDownloadPaths.relativeFilePath(row.catalogEntryId, row.commitHash, row.fileName)
        diskFilesOrDefault().filter { path ->
            LocalModelDownloadPaths.catalogEntryIdFromRelativePath(path) == row.catalogEntryId && path != retained
        }.forEach { storage().delete(it) }
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

    private fun storageRoot(): File = internalFilesDir?.invoke() ?: context.noBackupFilesDir

    private fun storage(): LocalModelStorage = LocalModelStorage(storageRoot(), externalStorageRoot())

    private fun diskFilesOrDefault(): Set<String> = diskFiles?.invoke() ?: listModelFiles()

    private fun workInfosFlow(): Flow<List<WorkInfo>> = workInfos?.invoke()
        ?: WorkManager.getInstance(context).getWorkInfosByTagFlow(LocalModelDownloadWorker.WORK_TAG)

    private fun listModelFiles(): Set<String> = storage().files()

    private suspend fun activeDownloadIds(): Set<String> {
        val infos = workInfosFlow().first()
        return infos
            .filter { !it.state.isFinished }
            .mapNotNull { info ->
                info.tags.firstNotNullOfOrNull(LocalModelDownloadWorker::catalogEntryIdFromTag)
            }
            .toSet()
    }

    private fun diskBytes(model: LocalModel): Long {
        val file = storage().find(LocalModelDownloadPaths.relativeFilePath(model.catalogEntryId, model.commitHash, model.fileName), model.totalBytes)
        return file?.length() ?: model.totalBytes
    }

    private companion object {
        const val JOB_DELIVERY_TIMEOUT_MS = 2_000L
        private const val TAG = "LocalModelRepository"
    }
}
