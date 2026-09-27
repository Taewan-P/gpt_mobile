package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceModelSearchClient
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadCoordinator
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelImportResult
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.di.DeviceSocModel
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.HuggingFaceAuthClient
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalDownloadGuards
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalModelDownloadActions
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LocalModelsViewModel @Inject constructor(
    private val modelCatalogRepository: ModelCatalogRepository,
    private val localModelRepository: LocalModelRepository,
    gatedDownloadCoordinator: GatedDownloadCoordinator,
    private val huggingFaceTokenStore: HuggingFaceTokenStore,
    private val downloadGuards: LocalDownloadGuards,
    huggingFaceAuthClient: HuggingFaceAuthClient,
    @param:DeviceSocModel private val deviceSocModel: String,
    private val huggingFaceModelSearchClient: HuggingFaceModelSearchClient? = null
) : ViewModel() {

    private val downloadActions = LocalModelDownloadActions(
        localModelRepository = localModelRepository,
        gatedDownloadCoordinator = gatedDownloadCoordinator,
        huggingFaceTokenStore = huggingFaceTokenStore,
        downloadGuards = downloadGuards,
        huggingFaceAuthClient = huggingFaceAuthClient,
        scope = viewModelScope,
        deviceSocModel = deviceSocModel
    )

    private val localListState = MutableStateFlow(LocalModelsListState())
    private val customDialogState = MutableStateFlow<LocalModelsDialog>(LocalModelsDialog.Hidden)
    private val hasHuggingFaceToken = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")
    private val modelFilter = MutableStateFlow(LocalModelFilter.ALL)
    private val modelSource = MutableStateFlow(LocalModelSource.CATALOG)
    private val huggingFaceEntries = MutableStateFlow<List<CatalogEntry>>(emptyList())
    private val huggingFaceSearchState = MutableStateFlow(HuggingFaceSearchState())
    private var huggingFaceSearchJob: Job? = null
    private var npuOnly = false

    fun setNpuOnly(value: Boolean) {
        if (npuOnly == value) return
        npuOnly = value
        refreshHuggingFaceSearch()
    }

    private val discoveryState = combine(
        searchQuery,
        modelFilter,
        modelSource,
        huggingFaceEntries,
        huggingFaceSearchState
    ) { query, filter, source, hfEntries, hfSearch ->
        LocalModelDiscoveryState(
            query = query,
            filter = filter,
            source = source,
            huggingFaceEntries = hfEntries,
            huggingFaceSearch = hfSearch
        )
    }

    val uiState: StateFlow<LocalModelsUiState> = combine(
        localListState,
        downloadActions.uiState,
        customDialogState,
        hasHuggingFaceToken,
        discoveryState
    ) { list, download, customDialog, hasToken, discovery ->
        val normalized = discovery.query.trim().lowercase()
        fun matchesFilter(item: LocalModelListItem): Boolean = when (discovery.filter) {
            LocalModelFilter.ALL -> true
            LocalModelFilter.READY -> item.status == LocalModelItemStatus.READY
            LocalModelFilter.AVAILABLE -> item.status == LocalModelItemStatus.NOT_DOWNLOADED
            LocalModelFilter.DOWNLOADING -> item.status == LocalModelItemStatus.DOWNLOADING
            LocalModelFilter.FAILED -> item.status == LocalModelItemStatus.FAILED
        }

        val localItemsById = list.items.associateBy { it.entry.id }
        val hfItems = discovery.huggingFaceEntries.map { entry ->
            localItemsById[entry.id]?.copy(entry = entry)
                ?: LocalModelListItem(
                    entry = entry,
                    status = LocalModelItemStatus.NOT_DOWNLOADED,
                    downloadSizeBytes = entry.sizeInBytes
                )
        }.filter(::matchesFilter)

        val catalogItems = list.items.filter { item ->
            val matchesQuery = normalized.isBlank() ||
                item.entry.displayName.lowercase().contains(normalized) ||
                item.entry.id.lowercase().contains(normalized) ||
                item.entry.downloadUrl.lowercase().contains(normalized) ||
                item.entry.supportedAccelerators.any { it.lowercase().contains(normalized) }
            matchesQuery && matchesFilter(item)
        }

        LocalModelsUiState(
            allItems = list.items,
            items = if (discovery.source == LocalModelSource.CATALOG) catalogItems else hfItems,
            totalItemCount = if (discovery.source == LocalModelSource.CATALOG) list.items.size else discovery.huggingFaceEntries.size,
            searchQuery = discovery.query,
            filter = discovery.filter,
            isLoading = list.isLoading,
            totalStorageBytes = list.totalStorageBytes,
            checkingAccessEntryId = download.checkingAccessEntryId,
            dialog = if (customDialog !is LocalModelsDialog.Hidden) customDialog else download.dialog,
            hasHuggingFaceToken = hasToken,
            source = discovery.source,
            huggingFaceItems = hfItems,
            isSearchingHuggingFace = discovery.huggingFaceSearch.isLoading,
            huggingFaceSearchError = discovery.huggingFaceSearch.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalModelsUiState())

    init {
        viewModelScope.launch {
            hasHuggingFaceToken.value = huggingFaceTokenStore.readAccessToken() != null
        }
        viewModelScope.launch {
            runCatching { localModelRepository.reconcile() }
            val catalogEntries = runCatching { modelCatalogRepository.getVisibleEntries() }
                .getOrDefault(emptyList())
            combine(
                localModelRepository.observeAll(),
                localModelRepository.observeWorkInfos()
            ) { localModels, workInfos ->
                val items = catalogLocalModelItems(
                    catalogEntries,
                    localModels,
                    workInfos,
                    localModels.associate { it.catalogEntryId to localModelRepository.diskPartialBytes(it) },
                    deviceSocModel = deviceSocModel
                )
                val storage = localModels
                    .filter { it.status == LocalModelStatus.READY }
                    .sumOf { it.totalBytes }
                items to storage
            }.collect { (items, storage) ->
                localListState.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        totalStorageBytes = storage
                    )
                }
            }
        }
    }

    fun updateSearchQuery(value: String) {
        searchQuery.value = value
        if (modelSource.value == LocalModelSource.HUGGING_FACE) {
            scheduleHuggingFaceSearch(value)
        }
    }

    fun updateFilter(filter: LocalModelFilter) {
        modelFilter.value = filter
    }

    fun updateModelSource(source: LocalModelSource) {
        modelSource.value = source
        if (source == LocalModelSource.HUGGING_FACE && huggingFaceEntries.value.isEmpty()) {
            scheduleHuggingFaceSearch(searchQuery.value, immediate = true)
        }
    }

    fun refreshHuggingFaceSearch() {
        if (modelSource.value == LocalModelSource.HUGGING_FACE) {
            scheduleHuggingFaceSearch(searchQuery.value, immediate = true)
        }
    }

    private fun scheduleHuggingFaceSearch(query: String, immediate: Boolean = false) {
        huggingFaceSearchJob?.cancel()
        val client = huggingFaceModelSearchClient
        if (client == null) {
            huggingFaceSearchState.value = HuggingFaceSearchState(
                isLoading = false,
                error = "Hugging Face search is unavailable in this build."
            )
            return
        }

        huggingFaceSearchJob = viewModelScope.launch {
            if (!immediate) delay(HUGGING_FACE_SEARCH_DEBOUNCE_MS)
            huggingFaceEntries.value = emptyList()
            huggingFaceSearchState.value = HuggingFaceSearchState(isLoading = true)
            runCatching { if (npuOnly) client.searchNpu(query, deviceSocModel) else client.search(query) }
                .onSuccess { results ->
                    huggingFaceEntries.value = results.map { it.toCatalogEntry() }.filter { entry ->
                        !downloadGuards.belowRamRequirement(entry) &&
                            if (entry.supportedAccelerators.any { it.equals("npu", true) }) {
                                dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators.isNpuEligible(entry.supportedAccelerators, entry.socToModelFiles, deviceSocModel)
                            } else {
                                !npuOnly && entry.supportedAccelerators.any { it.equals("gpu", true) }
                            }
                    }
                    huggingFaceSearchState.value = HuggingFaceSearchState()
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    huggingFaceSearchState.value = HuggingFaceSearchState(
                        error = error.localizedMessage ?: "Could not search Hugging Face."
                    )
                }
        }
    }

    fun onDownloadClick(entry: CatalogEntry) {
        downloadActions.requestDownload(entry, currentStatus(entry.id))
    }

    fun confirmRamWarning() {
        downloadActions.confirmRamWarning()
    }

    fun confirmMeteredDownload() {
        downloadActions.confirmMeteredDownload()
    }

    fun onDeleteClick(entry: CatalogEntry) {
        customDialogState.value = LocalModelsDialog.DeleteConfirm(entry)
    }

    fun confirmDelete() {
        val entry = (customDialogState.value as? LocalModelsDialog.DeleteConfirm)?.entry ?: return
        customDialogState.value = LocalModelsDialog.Hidden
        viewModelScope.launch { localModelRepository.deleteModel(entry.id) }
    }

    fun cancelDownload(entry: CatalogEntry) {
        viewModelScope.launch { localModelRepository.cancelDownload(entry.id) }
    }

    fun dismissDialog() {
        if (customDialogState.value !is LocalModelsDialog.Hidden) {
            customDialogState.value = LocalModelsDialog.Hidden
        } else {
            downloadActions.dismissDialog()
        }
    }

    fun importCustomModel(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val fileName = queryDisplayName(contentResolver, uri) ?: uri.lastPathSegment ?: "custom.litertlm"
            val inputStream = runCatching { contentResolver.openInputStream(uri) }.getOrNull()
            if (inputStream == null) {
                customDialogState.value = LocalModelsDialog.ImportFailed("Could not open file stream.")
                return@launch
            }
            val result = localModelRepository.importCustomModel(inputStream, fileName)
            if (result is LocalModelImportResult.Failure) {
                customDialogState.value = LocalModelsDialog.ImportFailed(result.message)
            }
        }
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }

    fun startHuggingFaceSignIn(): Intent? = downloadActions.startHuggingFaceSignIn()

    fun onAuthActivityResult(data: Intent?) {
        downloadActions.onAuthActivityResult(data)
    }

    fun onLicenseTabClosed() {
        downloadActions.onLicenseTabClosed()
    }

    fun retryAfterLicense() {
        downloadActions.retryAfterLicense()
    }

    fun openAccessTokenDialog() {
        downloadActions.openAccessTokenDialog()
    }

    fun saveHuggingFaceAccessToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            huggingFaceTokenStore.saveAccessToken(trimmed)
            hasHuggingFaceToken.value = true
            downloadActions.retryAfterAccessToken()
        }
    }

    fun removeHuggingFaceAccessToken() {
        viewModelScope.launch {
            huggingFaceTokenStore.clear()
            hasHuggingFaceToken.value = false
        }
    }

    override fun onCleared() {
        huggingFaceSearchJob?.cancel()
        downloadActions.release()
        super.onCleared()
    }

    private companion object {
        const val HUGGING_FACE_SEARCH_DEBOUNCE_MS = 350L
    }

    private fun currentStatus(catalogEntryId: String): LocalModelItemStatus? = localListState.value.items.firstOrNull { it.entry.id == catalogEntryId }?.status
}

private data class HuggingFaceSearchState(
    val isLoading: Boolean = false,
    val error: String? = null
)

private data class LocalModelDiscoveryState(
    val query: String,
    val filter: LocalModelFilter,
    val source: LocalModelSource,
    val huggingFaceEntries: List<CatalogEntry>,
    val huggingFaceSearch: HuggingFaceSearchState
)

private data class LocalModelsListState(
    val items: List<LocalModelListItem> = emptyList(),
    val isLoading: Boolean = true,
    val totalStorageBytes: Long = 0L
)
