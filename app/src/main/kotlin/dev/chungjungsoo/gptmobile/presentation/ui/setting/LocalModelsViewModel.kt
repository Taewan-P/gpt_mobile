package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadCoordinator
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.di.DeviceSocModel
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.HuggingFaceAuthClient
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalDownloadGuards
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalModelDownloadActions
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    downloadGuards: LocalDownloadGuards,
    huggingFaceAuthClient: HuggingFaceAuthClient,
    @param:DeviceSocModel private val deviceSocModel: String
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

    private val _listState = MutableStateFlow(LocalModelsListState())
    private val listState = _listState.asStateFlow()
    private val _deleteDialog = MutableStateFlow<LocalModelsDialog>(LocalModelsDialog.Hidden)
    private val deleteDialog = _deleteDialog.asStateFlow()
    private val hasHuggingFaceToken = MutableStateFlow(false)

    val uiState: StateFlow<LocalModelsUiState> = combine(
        _listState,
        downloadActions.uiState,
        _deleteDialog,
        hasHuggingFaceToken
    ) { list, download, delete, hasToken ->
        LocalModelsUiState(
            items = list.items,
            isLoading = list.isLoading,
            loadError = list.loadError,
            totalStorageBytes = list.totalStorageBytes,
            checkingAccessEntryId = download.checkingAccessEntryId,
            dialog = if (delete !is LocalModelsDialog.Hidden) delete else download.dialog,
            hasHuggingFaceToken = hasToken
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalModelsUiState())

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            hasHuggingFaceToken.value = huggingFaceTokenStore.readAccessToken() != null
        }
        retryLoad()
    }

    fun retryLoad() {
        loadJob?.cancel()
        _listState.update { it.copy(isLoading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                localModelRepository.reconcile()
                val catalogEntries = modelCatalogRepository.getVisibleEntries()
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
                    _listState.update {
                        it.copy(
                            items = items,
                            isLoading = false,
                            loadError = null,
                            totalStorageBytes = storage
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                _listState.update {
                    it.copy(
                        isLoading = false,
                        loadError = throwable.message.orEmpty()
                    )
                }
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
        _deleteDialog.value = LocalModelsDialog.DeleteConfirm(entry)
    }

    fun confirmDelete() {
        val entry = (_deleteDialog.value as? LocalModelsDialog.DeleteConfirm)?.entry ?: return
        _deleteDialog.value = LocalModelsDialog.Hidden
        viewModelScope.launch { localModelRepository.deleteModel(entry.id) }
    }

    fun cancelDownload(entry: CatalogEntry) {
        viewModelScope.launch { localModelRepository.cancelDownload(entry.id) }
    }

    fun dismissDialog() {
        if (_deleteDialog.value !is LocalModelsDialog.Hidden) {
            _deleteDialog.value = LocalModelsDialog.Hidden
        } else {
            downloadActions.dismissDialog()
        }
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
        downloadActions.release()
        super.onCleared()
    }

    private fun currentStatus(catalogEntryId: String): LocalModelItemStatus? = _listState.value.items.firstOrNull { it.entry.id == catalogEntryId }?.status
}

private data class LocalModelsListState(
    val items: List<LocalModelListItem> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val totalStorageBytes: Long = 0L
)
