package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadCoordinator
import dev.chungjungsoo.gptmobile.data.localruntime.LocalSamplingDefaults
import dev.chungjungsoo.gptmobile.data.localruntime.localSamplingDefaults
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.di.DeviceSocModel
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.HuggingFaceAuthClient
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalDownloadGuards
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalModelDownloadActions
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AddPlatformViewModel @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val modelCatalogRepository: ModelCatalogRepository,
    gatedDownloadCoordinator: GatedDownloadCoordinator,
    huggingFaceTokenStore: HuggingFaceTokenStore,
    downloadGuards: LocalDownloadGuards,
    huggingFaceAuthClient: HuggingFaceAuthClient,
    @param:DeviceSocModel private val deviceSocModel: String
) : ViewModel() {
    private val _catalogEntries = MutableStateFlow<List<CatalogEntry>>(emptyList())
    val catalogEntries = _catalogEntries.asStateFlow()
    private val _selectedCatalogEntryId = MutableStateFlow("")
    private val downloadActions = LocalModelDownloadActions(
        localModelRepository = localModelRepository,
        gatedDownloadCoordinator = gatedDownloadCoordinator,
        huggingFaceTokenStore = huggingFaceTokenStore,
        downloadGuards = downloadGuards,
        huggingFaceAuthClient = huggingFaceAuthClient,
        scope = viewModelScope,
        deviceSocModel = deviceSocModel
    )

    val selectedCatalogEntryId: StateFlow<String> = _selectedCatalogEntryId.asStateFlow()

    val catalogLocalModels: StateFlow<List<LocalModelListItem>> = combine(
        _catalogEntries,
        localModelRepository.observeAll(),
        localModelRepository.observeWorkInfos()
    ) { catalog, models, workInfos ->
        catalogLocalModelItems(
            catalog,
            models,
            workInfos,
            models.associate { it.catalogEntryId to localModelRepository.diskPartialBytes(it) },
            deviceSocModel = deviceSocModel
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val canSave: StateFlow<Boolean> = _selectedCatalogEntryId
        .combine(catalogLocalModels) { catalogEntryId, _ -> catalogEntryId.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isWaitingForDownload: StateFlow<Boolean> = combine(
        _selectedCatalogEntryId,
        catalogLocalModels
    ) { catalogEntryId, items ->
        isWaitingForModelDownload(catalogEntryId, items)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val localModelDownloadState: StateFlow<LocalModelDownloadUiState> = downloadActions.uiState

    init {
        viewModelScope.launch {
            _catalogEntries.value = modelCatalogRepository.getVisibleEntries()
        }
    }

    fun selectLocalModel(catalogEntryId: String) {
        _selectedCatalogEntryId.value = catalogEntryId
        val item = catalogLocalModels.value.firstOrNull { it.entry.id == catalogEntryId } ?: return
        downloadActions.requestDownload(item.entry, item.status)
    }

    fun confirmRamWarning() {
        downloadActions.confirmRamWarning()
    }

    fun confirmMeteredDownload() {
        downloadActions.confirmMeteredDownload()
    }

    fun dismissDownloadDialog() {
        downloadActions.dismissDialog()
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

    fun canSaveLocalModel(): Boolean = canSave.value

    fun shouldEnableLocalPlatform(): Boolean = selectedLocalModelStatus(_selectedCatalogEntryId.value, catalogLocalModels.value) == LocalModelItemStatus.READY

    fun saveHuggingFaceAccessToken(token: String) {
        downloadActions.saveAccessTokenAndRetry(token)
    }

    fun openAccessTokenDialog() {
        downloadActions.openAccessTokenDialog()
    }

    fun isWaitingForModelDownload(): Boolean = isWaitingForDownload.value

    fun defaultsFor(catalogEntryId: String): LocalSamplingDefaults? = _catalogEntries.value
        .firstOrNull { it.id == catalogEntryId }
        ?.let { localSamplingDefaults(it, deviceSocModel) }

    override fun onCleared() {
        downloadActions.release()
        super.onCleared()
    }

    private fun isWaitingForModelDownload(
        catalogEntryId: String,
        items: List<LocalModelListItem>
    ): Boolean {
        if (catalogEntryId.isBlank()) return false
        return selectedLocalModelStatus(catalogEntryId, items)?.let { it != LocalModelItemStatus.READY } == true
    }

    private fun selectedLocalModelStatus(
        catalogEntryId: String,
        items: List<LocalModelListItem>
    ): LocalModelItemStatus? {
        if (catalogEntryId.isBlank()) return null
        return items.firstOrNull { it.entry.id == catalogEntryId }?.status
    }
}
