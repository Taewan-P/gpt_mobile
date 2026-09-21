package dev.chungjungsoo.gptmobile.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.dao.LocalModelDao
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.DownloadAuthException
import dev.chungjungsoo.gptmobile.data.localmodel.DownloadErrorClassifier
import dev.chungjungsoo.gptmobile.data.localmodel.DownloadFailureKind
import dev.chungjungsoo.gptmobile.data.localmodel.DownloadProgress
import dev.chungjungsoo.gptmobile.data.localmodel.HuggingFaceDownloadAuth
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelDownloadPaths
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localmodel.PendingLocalPlatformActivator
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class LocalModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val localModelDao: LocalModelDao,
    private val huggingFaceTokenStore: HuggingFaceTokenStore,
    private val pendingLocalPlatformActivator: PendingLocalPlatformActivator
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notificationId: Int = params.id.hashCode()

    override suspend fun doWork(): Result {
        val catalogEntryId = inputData.getString(KEY_CATALOG_ENTRY_ID)
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: applicationContext.getString(R.string.local_models)
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL)
        val commitHash = inputData.getString(KEY_COMMIT_HASH)
        val fileName = inputData.getString(KEY_FILE_NAME)
        val totalBytes = inputData.getLong(KEY_TOTAL_BYTES, 0L)
        val accessToken = resolveAccessToken()

        if (catalogEntryId.isNullOrBlank() || downloadUrl.isNullOrBlank() || commitHash.isNullOrBlank() || fileName.isNullOrBlank()) {
            return Result.failure()
        }

        ensureNotificationChannel()
        val seededPercent = seedPercent(
            catalogEntryId = catalogEntryId,
            commitHash = commitHash,
            fileName = fileName,
            totalBytes = totalBytes
        )
        runCatching { setForeground(createForegroundInfo(progress = seededPercent, modelName = displayName)) }

        return withContext(Dispatchers.IO) {
            try {
                markStatus(catalogEntryId, LocalModelStatus.DOWNLOADING)
                downloadFile(
                    catalogEntryId = catalogEntryId,
                    displayName = displayName,
                    downloadUrl = downloadUrl,
                    commitHash = commitHash,
                    fileName = fileName,
                    totalBytes = totalBytes,
                    accessToken = accessToken
                )
                markStatus(catalogEntryId, LocalModelStatus.READY)
                runCatching { pendingLocalPlatformActivator.onModelsBecameReady(setOf(catalogEntryId)) }
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: DownloadAuthException) {
                Log.e(TAG, error.message, error)
                markStatus(catalogEntryId, LocalModelStatus.FAILED)
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, error.message)
                        .putString(KEY_FAILURE_KIND, error.kind.name)
                        .build()
                )
            } catch (error: IOException) {
                Log.e(TAG, error.message, error)
                val hadProgress = hadPartialProgress(catalogEntryId, commitHash, fileName)
                val classification = DownloadErrorClassifier.classify(error, hadProgress)
                if (DownloadErrorClassifier.shouldRetry(classification, runAttemptCount)) {
                    Result.retry()
                } else {
                    markStatus(catalogEntryId, LocalModelStatus.FAILED)
                    Result.failure(
                        Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, error.message)
                            .putString(KEY_FAILURE_KIND, DownloadFailureKind.GENERIC.name)
                            .build()
                    )
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val displayName = inputData.getString(KEY_DISPLAY_NAME)
        val percent = seedPercent(
            catalogEntryId = inputData.getString(KEY_CATALOG_ENTRY_ID),
            commitHash = inputData.getString(KEY_COMMIT_HASH),
            fileName = inputData.getString(KEY_FILE_NAME),
            totalBytes = inputData.getLong(KEY_TOTAL_BYTES, 0L)
        )
        return createForegroundInfo(percent, displayName)
    }

    private suspend fun downloadFile(
        catalogEntryId: String,
        displayName: String,
        downloadUrl: String,
        commitHash: String,
        fileName: String,
        totalBytes: Long,
        accessToken: String?
    ) {
        LocalModelDownloadPaths.requireValidPathSegments(catalogEntryId, commitHash, fileName)
        val storageRoot = applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
        val outputDir = File(storageRoot, LocalModelDownloadPaths.relativeDirectory(catalogEntryId, commitHash))
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IOException("Unable to create Local Model directory")
        }

        val outputTmpFile = File(outputDir, LocalModelDownloadPaths.partialFileName(fileName))
        val partialLength = outputTmpFile.length()
        publishSeededProgress(partialLength, totalBytes, displayName)
        val connection = HuggingFaceDownloadAuth.openConnection(
            url = downloadUrl,
            accessToken = accessToken,
            extraHeaders = LocalModelDownloadPaths.resumeHeaders(partialLength),
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS
        )
        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
                responseCode == HttpURLConnection.HTTP_FORBIDDEN
            ) {
                val isGated = inputData.getBoolean(KEY_REQUIRES_HF_AUTH, false)
                val kind = DownloadFailureKind.fromHttp(
                    statusCode = responseCode,
                    hasToken = accessToken != null,
                    isGated = isGated
                )
                throw DownloadAuthException(
                    kind = kind,
                    message = applicationContext.getString(messageResFor(kind))
                )
            }
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("HTTP error code: $responseCode")
            }

            val contentRange = connection.getHeaderField("Content-Range")
            val append = LocalModelDownloadPaths.shouldAppendToPartial(partialLength, contentRange)
            var downloadedBytes = LocalModelDownloadPaths.downloadedBytesAfterConnect(partialLength, contentRange)
            val bytesReadSizeBuffer = mutableListOf<Long>()
            val bytesReadLatencyBuffer = mutableListOf<Long>()

            connection.inputStream.use { inputStream ->
                FileOutputStream(outputTmpFile, append).use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int
                    var lastSetProgressTs = 0L
                    var deltaBytes = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            throw CancellationException("Local Model download cancelled")
                        }
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        deltaBytes += bytesRead

                        val curTs = System.currentTimeMillis()
                        if (curTs - lastSetProgressTs > PROGRESS_INTERVAL_MS) {
                            var bytesPerMs = 0f
                            if (lastSetProgressTs != 0L) {
                                if (bytesReadSizeBuffer.size == RATE_WINDOW) {
                                    bytesReadSizeBuffer.removeAt(0)
                                }
                                bytesReadSizeBuffer.add(deltaBytes)
                                if (bytesReadLatencyBuffer.size == RATE_WINDOW) {
                                    bytesReadLatencyBuffer.removeAt(0)
                                }
                                bytesReadLatencyBuffer.add(curTs - lastSetProgressTs)
                                deltaBytes = 0L
                                bytesPerMs = bytesReadSizeBuffer.sum().toFloat() / bytesReadLatencyBuffer.sum()
                            }

                            var remainingMs = 0f
                            if (bytesPerMs > 0f && totalBytes > 0L) {
                                remainingMs = (totalBytes - downloadedBytes) / bytesPerMs
                            }

                            setProgress(
                                Data.Builder()
                                    .putLong(KEY_RECEIVED_BYTES, downloadedBytes)
                                    .putLong(KEY_DOWNLOAD_RATE, (bytesPerMs * 1000).toLong())
                                    .putLong(KEY_REMAINING_MS, remainingMs.toLong())
                                    .build()
                            )
                            val percent = if (totalBytes > 0L) (downloadedBytes * 100 / totalBytes).toInt() else 0
                            runCatching { setForeground(createForegroundInfo(progress = percent, modelName = displayName)) }
                            lastSetProgressTs = curTs
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        if (!LocalModelDownloadPaths.isCompleteDownload(outputTmpFile.length(), totalBytes)) {
            throw IOException("Incomplete Local Model download")
        }

        val originalFile = File(outputDir, fileName)
        if (originalFile.exists() && !originalFile.delete()) {
            throw IOException("Unable to replace existing Local Model file")
        }
        if (!outputTmpFile.renameTo(originalFile)) {
            throw IOException("Unable to finalize Local Model file")
        }
    }

    private suspend fun resolveAccessToken(): String? {
        if (inputData.getBoolean(KEY_REQUIRES_HF_AUTH, false)) {
            return huggingFaceTokenStore.readAccessToken()
        }
        return inputData.getString(KEY_ACCESS_TOKEN)
    }

    private suspend fun markStatus(catalogEntryId: String, status: String) {
        localModelDao.updateStatus(catalogEntryId, status, System.currentTimeMillis() / 1000)
    }

    private fun ensureNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.local_model_download_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.local_model_download_notification_channel_description)
            }
        )
    }

    private fun createForegroundInfo(progress: Int, modelName: String? = null): ForegroundInfo {
        val title = applicationContext.getString(R.string.local_model_download_notification_title)
        val content = if (modelName != null) {
            applicationContext.getString(R.string.local_model_download_notification_content, modelName, progress)
        } else {
            applicationContext.getString(R.string.local_model_download_notification_progress, progress)
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_gpt_mobile_monochrome_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .build()
        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    companion object {
        const val WORK_TAG = "local_model_download"
        const val ID_TAG_PREFIX = "local_model_id:"
        const val KEY_CATALOG_ENTRY_ID = "catalog_entry_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_COMMIT_HASH = "commit_hash"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REQUIRES_HF_AUTH = "requires_hf_auth"
        const val KEY_RECEIVED_BYTES = "received_bytes"
        const val KEY_DOWNLOAD_RATE = "download_rate"
        const val KEY_REMAINING_MS = "remaining_ms"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_FAILURE_KIND = "failure_kind"
        const val INITIAL_BACKOFF_SECONDS = 10L
        private const val TAG = "LocalModelDownload"
        private const val CHANNEL_ID = "local_model_downloads"
        private const val PROGRESS_INTERVAL_MS = 200L
        private const val RATE_WINDOW = 5
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        fun idTag(catalogEntryId: String): String = "$ID_TAG_PREFIX$catalogEntryId"

        fun catalogEntryIdFromTag(tag: String): String? = tag.takeIf { it.startsWith(ID_TAG_PREFIX) }?.removePrefix(ID_TAG_PREFIX)
    }

    private fun seedPercent(
        catalogEntryId: String?,
        commitHash: String?,
        fileName: String?,
        totalBytes: Long
    ): Int {
        if (catalogEntryId.isNullOrBlank() || commitHash.isNullOrBlank() || fileName.isNullOrBlank()) return 0
        return DownloadProgress.percent(partialFileBytes(catalogEntryId, commitHash, fileName), totalBytes)
    }

    private fun hadPartialProgress(catalogEntryId: String, commitHash: String, fileName: String): Boolean = partialFileBytes(catalogEntryId, commitHash, fileName) > 0L

    private fun partialFileBytes(catalogEntryId: String, commitHash: String, fileName: String): Long {
        val storageRoot = applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
        val file = File(storageRoot, LocalModelDownloadPaths.relativePartialFilePath(catalogEntryId, commitHash, fileName))
        return file.takeIf { it.exists() }?.length() ?: 0L
    }

    private suspend fun publishSeededProgress(partialLength: Long, totalBytes: Long, displayName: String) {
        if (partialLength <= 0L) return
        val percent = DownloadProgress.percent(partialLength, totalBytes)
        setProgress(
            Data.Builder()
                .putLong(KEY_RECEIVED_BYTES, partialLength)
                .putLong(KEY_DOWNLOAD_RATE, 0L)
                .putLong(KEY_REMAINING_MS, 0L)
                .build()
        )
        runCatching { setForeground(createForegroundInfo(progress = percent, modelName = displayName)) }
    }

    private fun messageResFor(kind: DownloadFailureKind): Int = when (kind) {
        DownloadFailureKind.SESSION_EXPIRED -> R.string.local_model_session_expired
        DownloadFailureKind.AUTH_REQUIRED -> R.string.local_model_auth_required
        DownloadFailureKind.LICENSE_REQUIRED -> R.string.local_model_license_message
        DownloadFailureKind.GENERIC -> R.string.local_model_failed
    }
}
