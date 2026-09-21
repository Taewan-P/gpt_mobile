package dev.chungjungsoo.gptmobile.presentation.ui.setup

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadCoordinator
import dev.chungjungsoo.gptmobile.data.localruntime.localSamplingDefaults
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.di.DeviceSocModel
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.HuggingFaceAuthClient
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalDownloadGuards
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalModelDownloadActions
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelDownloadUiState
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelItemStatus
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelListItem
import dev.chungjungsoo.gptmobile.presentation.ui.setting.catalogLocalModelItems
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class SaveStatus {
    data object Idle : SaveStatus()
    data object Saving : SaveStatus()
    data object Success : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}

data class DownloadedLocalModelOption(
    val catalogEntryId: String,
    val displayName: String
)

@HiltViewModel
class SetupViewModelV2 @Inject constructor(
    private val settingRepository: SettingRepository,
    private val localModelRepository: LocalModelRepository,
    private val modelCatalogRepository: ModelCatalogRepository,
    gatedDownloadCoordinator: GatedDownloadCoordinator,
    huggingFaceTokenStore: HuggingFaceTokenStore,
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

    private val _platforms = MutableStateFlow<List<PlatformV2>>(emptyList())
    val platforms: StateFlow<List<PlatformV2>> = _platforms.asStateFlow()

    // Wizard state for adding a new platform
    private val _wizardStep = MutableStateFlow(0)
    val wizardStep: StateFlow<Int> = _wizardStep.asStateFlow()

    private val _selectedClientType = MutableStateFlow<ClientType?>(null)
    val selectedClientType: StateFlow<ClientType?> = _selectedClientType.asStateFlow()

    private val _platformName = MutableStateFlow("")
    val platformName: StateFlow<String> = _platformName.asStateFlow()

    private val _apiUrl = MutableStateFlow("")
    val apiUrl: StateFlow<String> = _apiUrl.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _model = MutableStateFlow("")
    val model: StateFlow<String> = _model.asStateFlow()

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    private val _catalogEntries = MutableStateFlow<List<CatalogEntry>>(emptyList())
    val catalogEntries = _catalogEntries.asStateFlow()

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

    val canProceed: StateFlow<Boolean> = combine(
        _wizardStep,
        _selectedClientType,
        _platformName,
        _apiUrl,
        _model
    ) { step, clientType, name, url, modelName ->
        canProceedFromStep(step, clientType, name, url, modelName)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isWaitingForDownload: StateFlow<Boolean> = combine(
        _selectedClientType,
        _model,
        catalogLocalModels
    ) { clientType, modelName, items ->
        isWaitingForModelDownload(clientType, modelName, items)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val localModelDownloadState: StateFlow<LocalModelDownloadUiState> = downloadActions.uiState

    init {
        loadPlatforms()
        viewModelScope.launch {
            _catalogEntries.value = modelCatalogRepository.getVisibleEntries()
        }
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            val existingPlatforms = settingRepository.fetchPlatformV2s()
            _platforms.value = existingPlatforms
        }
    }

    fun selectClientType(clientType: ClientType) {
        _selectedClientType.value = clientType
        _platformName.value = getDefaultPlatformName(clientType)
        _apiUrl.value = getDefaultApiUrl(clientType)
        _apiKey.value = ""
        _model.value = ModelConstants.defaultModel(clientType)
        _wizardStep.value = 0
    }

    fun updatePlatformName(name: String) {
        _platformName.value = name
    }

    fun updateApiUrl(url: String) {
        _apiUrl.value = url
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    fun updateModel(modelName: String) {
        _model.value = modelName
    }

    fun selectLocalModel(catalogEntryId: String) {
        _model.value = catalogEntryId
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

    fun isWaitingForModelDownload(): Boolean = isWaitingForDownload.value

    fun nextWizardStep() {
        if (isLiteRtLm() && _wizardStep.value == WIZARD_STEP_BASICS) {
            _wizardStep.value = WIZARD_STEP_MODEL
        } else {
            _wizardStep.update { it + 1 }
        }
    }

    fun previousWizardStep() {
        if (isLiteRtLm() && _wizardStep.value == WIZARD_STEP_MODEL) {
            _wizardStep.value = WIZARD_STEP_BASICS
        } else {
            _wizardStep.update { maxOf(0, it - 1) }
        }
    }

    fun resetWizard() {
        _wizardStep.value = 0
        _selectedClientType.value = null
        _platformName.value = ""
        _apiUrl.value = ""
        _apiKey.value = ""
        _model.value = ""
    }

    fun saveHuggingFaceAccessToken(token: String) {
        downloadActions.saveAccessTokenAndRetry(token)
    }

    fun openAccessTokenDialog() {
        downloadActions.openAccessTokenDialog()
    }

    fun savePlatform(onSuccess: (() -> Unit)? = null) {
        if (_saveStatus.value is SaveStatus.Saving) return
        val clientType = _selectedClientType.value ?: return
        _saveStatus.value = SaveStatus.Saving

        viewModelScope.launch {
            try {
                val defaults = if (clientType == ClientType.LITERT_LM) {
                    catalogDefaultsFor(_model.value.trim())
                } else {
                    null
                }
                val platform = PlatformV2(
                    name = _platformName.value.trim(),
                    compatibleType = clientType,
                    enabled = clientType != ClientType.LITERT_LM || selectedLocalModelIsDownloaded(),
                    apiUrl = if (clientType == ClientType.LITERT_LM) "" else _apiUrl.value.trim(),
                    token = _apiKey.value.trim().takeIf { it.isNotEmpty() && clientType != ClientType.LITERT_LM },
                    model = _model.value.trim(),
                    temperature = defaults?.temperature ?: 1.0f,
                    topP = defaults?.topP ?: 1.0f,
                    topK = defaults?.topK,
                    maxTokens = defaults?.maxTokens,
                    accelerator = defaults?.accelerator,
                    systemPrompt = ModelConstants.DEFAULT_PROMPT,
                    stream = true,
                    reasoning = false,
                    timeout = 30
                )
                settingRepository.addPlatformV2(platform)
                loadPlatforms()
                _saveStatus.value = SaveStatus.Success
                resetWizard()
                onSuccess?.invoke()
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is android.database.sqlite.SQLiteConstraintException -> "A platform with this name already exists."
                    is android.database.sqlite.SQLiteException -> "Database error: ${e.message}"
                    else -> e.message ?: "Unknown error occurred while saving platform."
                }
                _saveStatus.value = SaveStatus.Error(errorMessage)
            }
        }
    }

    fun clearSaveStatus() {
        _saveStatus.value = SaveStatus.Idle
    }

    fun deletePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.deletePlatformV2(platform)
            loadPlatforms()
        }
    }

    fun canProceedFromStep(step: Int): Boolean = canProceedFromStep(
        step = step,
        clientType = _selectedClientType.value,
        platformName = _platformName.value,
        apiUrl = _apiUrl.value,
        modelName = _model.value
    )

    fun isSetupComplete(): Boolean = _platforms.value.isNotEmpty()

    fun isLiteRtLm(): Boolean = _selectedClientType.value == ClientType.LITERT_LM

    fun wizardTotalSteps(): Int = if (isLiteRtLm()) WIZARD_LOCAL_STEPS else WIZARD_TOTAL_STEPS

    fun wizardDisplayStep(): Int = if (isLiteRtLm() && _wizardStep.value == WIZARD_STEP_MODEL) {
        1
    } else {
        _wizardStep.value
    }

    override fun onCleared() {
        downloadActions.release()
        super.onCleared()
    }

    private fun selectedLocalModelIsDownloaded(): Boolean = selectedLocalModelStatus(_model.value, catalogLocalModels.value) == LocalModelItemStatus.READY

    private fun canProceedFromStep(
        step: Int,
        clientType: ClientType?,
        platformName: String,
        apiUrl: String,
        modelName: String
    ): Boolean = when (step) {
        WIZARD_STEP_BASICS -> {
            val hasName = platformName.isNotBlank()
            if (clientType == ClientType.LITERT_LM) hasName else hasName && apiUrl.isNotBlank()
        }

        WIZARD_STEP_API_KEY -> true

        WIZARD_STEP_MODEL -> modelName.isNotBlank()

        else -> false
    }

    private fun isWaitingForModelDownload(
        clientType: ClientType?,
        modelName: String,
        items: List<LocalModelListItem>
    ): Boolean {
        if (clientType != ClientType.LITERT_LM) return false
        return selectedLocalModelStatus(modelName, items)?.let { it != LocalModelItemStatus.READY } == true
    }

    private fun selectedLocalModelStatus(
        catalogEntryId: String,
        items: List<LocalModelListItem>
    ): LocalModelItemStatus? {
        if (catalogEntryId.isBlank()) return null
        return items.firstOrNull { it.entry.id == catalogEntryId }?.status
    }

    private fun catalogDefaultsFor(modelId: String) = _catalogEntries.value
        .firstOrNull { it.id == modelId }
        ?.let { localSamplingDefaults(it, deviceSocModel) }

    private fun getDefaultPlatformName(clientType: ClientType): String = ModelConstants.defaultPlatformName(clientType)

    private fun getDefaultApiUrl(clientType: ClientType): String = ModelConstants.defaultApiUrl(clientType)

    companion object {
        const val WIZARD_STEP_BASICS = 0
        const val WIZARD_STEP_API_KEY = 1
        const val WIZARD_STEP_MODEL = 2
        const val WIZARD_TOTAL_STEPS = 3
        const val WIZARD_LOCAL_STEPS = 2
    }
}
