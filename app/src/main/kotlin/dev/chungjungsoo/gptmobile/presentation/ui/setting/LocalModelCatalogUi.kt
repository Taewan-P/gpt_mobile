package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.work.WorkInfo
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.localmodel.DownloadFailureKind
import dev.chungjungsoo.gptmobile.data.localmodel.DownloadProgress
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localmodel.SocVariantResolver
import dev.chungjungsoo.gptmobile.data.worker.LocalModelDownloadWorker

data class LocalModelsUiState(
    val items: List<LocalModelListItem> = emptyList(),
    val allItems: List<LocalModelListItem> = emptyList(),
    val totalItemCount: Int = 0,
    val searchQuery: String = "",
    val filter: LocalModelFilter = LocalModelFilter.ALL,
    val isLoading: Boolean = true,
    val totalStorageBytes: Long = 0L,
    val checkingAccessEntryId: String? = null,
    val dialog: LocalModelsDialog = LocalModelsDialog.Hidden,
    val hasHuggingFaceToken: Boolean = false,
    val source: LocalModelSource = LocalModelSource.CATALOG,
    val huggingFaceItems: List<LocalModelListItem> = emptyList(),
    val isSearchingHuggingFace: Boolean = false,
    val huggingFaceSearchError: String? = null
)

enum class LocalModelSource(val title: String) {
    CATALOG("Curated"),
    HUGGING_FACE("Hugging Face")
}

enum class LocalModelFilter(val title: String) {
    ALL("All"),
    READY("Downloaded"),
    AVAILABLE("Available"),
    DOWNLOADING("Downloading"),
    FAILED("Needs attention")
}

data class LocalModelDownloadUiState(
    val checkingAccessEntryId: String? = null,
    val dialog: LocalModelsDialog = LocalModelsDialog.Hidden
)

data class LocalModelListItem(
    val entry: CatalogEntry,
    val status: LocalModelItemStatus,
    val receivedBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val remainingMs: Long = 0L,
    val diskBytes: Long = 0L,
    val downloadSizeBytes: Long = 0L,
    val errorMessage: String? = null,
    val failureKind: DownloadFailureKind = DownloadFailureKind.GENERIC,
    val benchmarkScore: Int? = null
)

enum class LocalModelItemStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    FAILED
}

sealed class LocalModelsDialog {
    data object Hidden : LocalModelsDialog()
    data class RamWarning(val entry: CatalogEntry) : LocalModelsDialog()
    data class MeteredConfirm(val entry: CatalogEntry) : LocalModelsDialog()
    data class DeleteConfirm(val entry: CatalogEntry) : LocalModelsDialog()
    data class SignIn(val entry: CatalogEntry, val isSessionExpired: Boolean) : LocalModelsDialog()
    data class License(val entry: CatalogEntry, val modelPageUrl: String) : LocalModelsDialog()
    data class OAuthNotConfigured(val isSessionExpired: Boolean = false) : LocalModelsDialog()
    data class EnterAccessToken(val isSessionExpired: Boolean = false) : LocalModelsDialog()
    data object ProbeError : LocalModelsDialog()
    data object SignInFailed : LocalModelsDialog()
    data class ImportFailed(val message: String) : LocalModelsDialog()
}

fun catalogLocalModelItems(
    catalog: List<CatalogEntry>,
    records: List<LocalModel>,
    workInfos: List<WorkInfo>,
    partialBytesById: Map<String, Long> = emptyMap(),
    deviceSocModel: String = ""
): List<LocalModelListItem> {
    val workById = workInfos.mapNotNull { info ->
        val id = info.tags.firstNotNullOfOrNull(LocalModelDownloadWorker::catalogEntryIdFromTag)
        id?.let { it to info }
    }.toMap()
    val modelsById = records.associateBy { it.catalogEntryId }
    val catalogItems = catalog.map { entry ->
        toLocalModelListItem(
            modelsById[entry.id]?.let { dev.chungjungsoo.gptmobile.data.localmodel.LocalModelPackages.forInstalledFile(entry, it.fileName) } ?: entry,
            modelsById[entry.id],
            workById[entry.id],
            diskPartialBytes = partialBytesById[entry.id] ?: 0L,
            downloadSizeBytes = SocVariantResolver.resolveForRuntime(entry, deviceSocModel).sizeInBytes
        )
    }
    val catalogIds = catalog.map { it.id }.toSet()
    val customItems = records.filter { it.catalogEntryId !in catalogIds }.map { record ->
        val syntheticEntry = CatalogEntry(
            id = record.catalogEntryId,
            displayName = record.fileName,
            sizeInBytes = record.totalBytes,
            isGated = false
        )
        toLocalModelListItem(
            entry = dev.chungjungsoo.gptmobile.data.localmodel.LocalModelPackages.forInstalledFile(syntheticEntry, record.fileName),
            record = record,
            workInfo = workById[record.catalogEntryId],
            diskPartialBytes = partialBytesById[record.catalogEntryId] ?: 0L,
            downloadSizeBytes = record.totalBytes
        )
    }
    return catalogItems + customItems
}

fun toLocalModelListItem(
    entry: CatalogEntry,
    record: LocalModel?,
    workInfo: WorkInfo?,
    diskPartialBytes: Long = 0L,
    downloadSizeBytes: Long = entry.sizeInBytes
): LocalModelListItem {
    val workReceived = workInfo?.progress?.getLong(LocalModelDownloadWorker.KEY_RECEIVED_BYTES, 0L) ?: 0L
    val receivedBytes = DownloadProgress.receivedBytes(workReceived, diskPartialBytes)
    val bytesPerSecond = workInfo?.progress?.getLong(LocalModelDownloadWorker.KEY_DOWNLOAD_RATE, 0L) ?: 0L
    val remainingMs = workInfo?.progress?.getLong(LocalModelDownloadWorker.KEY_REMAINING_MS, 0L) ?: 0L
    val errorMessage = workInfo?.outputData?.getString(LocalModelDownloadWorker.KEY_ERROR_MESSAGE)
    val failureKind = DownloadFailureKind.fromWorkOutput(
        workInfo?.outputData?.getString(LocalModelDownloadWorker.KEY_FAILURE_KIND)
    )
    val workState = workInfo?.state
    val isWorkActive = workState == WorkInfo.State.RUNNING ||
        workState == WorkInfo.State.ENQUEUED ||
        workState == WorkInfo.State.BLOCKED
    val resolvedDownloadSize = if (downloadSizeBytes > 0L) downloadSizeBytes else entry.sizeInBytes
    return when {
        record?.status == LocalModelStatus.READY -> LocalModelListItem(
            entry = entry,
            status = LocalModelItemStatus.READY,
            diskBytes = record.totalBytes,
            downloadSizeBytes = resolvedDownloadSize
        )

        record?.status == LocalModelStatus.DOWNLOADING || isWorkActive -> LocalModelListItem(
            entry = entry,
            status = LocalModelItemStatus.DOWNLOADING,
            receivedBytes = receivedBytes,
            bytesPerSecond = bytesPerSecond,
            remainingMs = remainingMs,
            diskBytes = resolvedDownloadSize,
            downloadSizeBytes = resolvedDownloadSize
        )

        record?.status == LocalModelStatus.FAILED || workState == WorkInfo.State.FAILED -> LocalModelListItem(
            entry = entry,
            status = LocalModelItemStatus.FAILED,
            downloadSizeBytes = resolvedDownloadSize,
            errorMessage = errorMessage,
            failureKind = failureKind
        )

        else -> LocalModelListItem(
            entry = entry,
            status = LocalModelItemStatus.NOT_DOWNLOADED,
            downloadSizeBytes = resolvedDownloadSize
        )
    }
}

fun downloadFailureMessageRes(kind: DownloadFailureKind): Int = when (kind) {
    DownloadFailureKind.SESSION_EXPIRED -> R.string.local_model_session_expired
    DownloadFailureKind.AUTH_REQUIRED -> R.string.local_model_auth_required
    DownloadFailureKind.LICENSE_REQUIRED -> R.string.local_model_license_message
    DownloadFailureKind.GENERIC -> R.string.local_model_failed
}
