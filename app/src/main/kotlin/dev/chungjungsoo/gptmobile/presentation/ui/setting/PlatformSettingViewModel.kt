package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.agent.tool.AgentToolResolver
import dev.chungjungsoo.gptmobile.data.agent.tool.namespaceMcpToolName
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ProviderConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localruntime.AcceleratorOption
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.localruntime.localSamplingDefaults
import dev.chungjungsoo.gptmobile.data.localruntime.resolvedEngineMaxTokens
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import dev.chungjungsoo.gptmobile.data.model.ProfileLabel
import dev.chungjungsoo.gptmobile.data.model.SamplingCreativity
import dev.chungjungsoo.gptmobile.data.model.collectReusableProfileLabels
import dev.chungjungsoo.gptmobile.data.model.encodeProfileLabels
import dev.chungjungsoo.gptmobile.data.model.parseProfileLabels
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.OllamaServerRepository
import dev.chungjungsoo.gptmobile.data.repository.OpenRouterCreditsRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.ToolBindingSelection
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import dev.chungjungsoo.gptmobile.di.DeviceRamGb
import dev.chungjungsoo.gptmobile.di.DeviceSocModel
import dev.chungjungsoo.gptmobile.presentation.ui.setup.DownloadedLocalModelOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlatformSettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    toolConnectionDao: ToolConnectionDao,
    secretVault: SecretVault,
    private val agentToolResolver: AgentToolResolver,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val localModelRepository: LocalModelRepository,
    @param:DeviceSocModel private val deviceSocModel: String,
    @param:DeviceRamGb private val deviceRamGb: Long = 8L,
    private val openRouterCreditsRepository: OpenRouterCreditsRepository = OpenRouterCreditsRepository(),
    private val ollamaServerRepository: OllamaServerRepository = OllamaServerRepository(),
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val toolConnectionRepository = ToolConnectionRepository(toolConnectionDao, secretVault)

    val platformUid: String = checkNotNull(savedStateHandle["platformUid"])

    val debugMode = settingRepository.observeDebugMode().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val platformState: StateFlow<PlatformV2?> = settingRepository.observePlatformV2ByUid(platformUid)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val providerConnectionState: StateFlow<ProviderConnection?> = combine(
        platformState,
        settingRepository.observeProviderConnections()
    ) { platform, connections ->
        platform?.providerConnectionUid?.let { uid ->
            connections.firstOrNull { it.uid == uid }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val reusableLabels: StateFlow<List<ProfileLabel>> =
        settingRepository.observePlatformV2s()
            .map { profiles -> collectReusableProfileLabels(profiles.map { it.labels }) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _catalogEntries = MutableStateFlow<List<CatalogEntry>>(emptyList())
    val catalogEntries = _catalogEntries.asStateFlow()

    val downloadedLocalModels: StateFlow<List<DownloadedLocalModelOption>> = combine(
        localModelRepository.observeAll(),
        _catalogEntries
    ) { models, catalog ->
        val names = catalog.associate { it.id to it.displayName }
        models.filter { it.status == LocalModelStatus.READY }.map { model ->
            DownloadedLocalModelOption(
                catalogEntryId = model.catalogEntryId,
                displayName = names[model.catalogEntryId]?.takeIf { it.isNotBlank() } ?: model.catalogEntryId
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val acceleratorOptions: StateFlow<List<AcceleratorOption>> = combine(platformState, _catalogEntries, localModelRepository.observeAll()) { platform, catalog, records ->
        val entry = catalog.firstOrNull { it.id == platform?.model }?.let { entry ->
            records.firstOrNull { it.catalogEntryId == entry.id }?.let { dev.chungjungsoo.gptmobile.data.localmodel.LocalModelPackages.forInstalledFile(entry, it.fileName) } ?: entry
        }
        LocalAccelerators.choices(
            supported = entry?.supportedAccelerators ?: listOf(LocalAccelerators.CPU, LocalAccelerators.GPU),
            socToModelFiles = entry?.socToModelFiles.orEmpty(),
            deviceSocModel = deviceSocModel
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    private val _isDeleted = MutableStateFlow(false)
    val isDeleted: StateFlow<Boolean> = _isDeleted.asStateFlow()

    private val _userMessage = MutableStateFlow<Int?>(null)
    val userMessage: StateFlow<Int?> = _userMessage.asStateFlow()

    private val _toolBindingState = MutableStateFlow(ToolBindingState())
    val toolBindingState: StateFlow<ToolBindingState> = _toolBindingState.asStateFlow()
    private var mcpDiscoveryJob: Job? = null

    private val _openRouterCreditsState = MutableStateFlow<OpenRouterCreditsUiState>(OpenRouterCreditsUiState.Idle)
    val openRouterCreditsState: StateFlow<OpenRouterCreditsUiState> = _openRouterCreditsState.asStateFlow()

    private val _ollamaServerState = MutableStateFlow<OllamaServerUiState>(OllamaServerUiState.Idle)
    val ollamaServerState: StateFlow<OllamaServerUiState> = _ollamaServerState.asStateFlow()

    init {
        loadToolBindings()
        loadCatalog()
        observeOpenRouterCredits()
        observeOllamaServer()
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            _catalogEntries.value = modelCatalogRepository.getVisibleEntries()
        }
    }

    private fun observeOpenRouterCredits() {
        viewModelScope.launch {
            platformState.collect { platform ->
                if (platform?.compatibleType == ClientType.OPENROUTER && !platform.token.isNullOrBlank()) {
                    if (_openRouterCreditsState.value is OpenRouterCreditsUiState.Idle) {
                        refreshOpenRouterCredits(forceRefresh = false)
                    }
                } else if (platform?.compatibleType != ClientType.OPENROUTER) {
                    _openRouterCreditsState.value = OpenRouterCreditsUiState.Idle
                }
            }
        }
    }

    private fun observeOllamaServer() {
        viewModelScope.launch {
            platformState.collect { platform ->
                if (platform?.compatibleType == ClientType.OLLAMA && !platform.apiUrl.isNullOrBlank()) {
                    if (_ollamaServerState.value is OllamaServerUiState.Idle) {
                        checkOllamaServer()
                    }
                } else if (platform?.compatibleType != ClientType.OLLAMA) {
                    _ollamaServerState.value = OllamaServerUiState.Idle
                }
            }
        }
    }

    fun checkOllamaServer() {
        val platform = platformState.value ?: return
        if (platform.compatibleType != ClientType.OLLAMA) return
        val url = platform.apiUrl?.trim().orEmpty()
        if (url.isBlank()) {
            _ollamaServerState.value = OllamaServerUiState.Idle
            return
        }

        viewModelScope.launch {
            _ollamaServerState.value = OllamaServerUiState.Checking
            val healthResult = ollamaServerRepository.checkHealth(url)
            healthResult.onSuccess { health ->
                val modelsResult = ollamaServerRepository.fetchModels(url)
                val models = modelsResult.getOrDefault(emptyList())
                _ollamaServerState.value = OllamaServerUiState.Connected(
                    version = health.version,
                    latencyMs = health.latencyMs,
                    models = models
                )
            }.onFailure { error ->
                _ollamaServerState.value = OllamaServerUiState.Error(
                    message = error.message ?: "Failed to connect to Ollama server"
                )
            }
        }
    }

    fun refreshOpenRouterCredits(forceRefresh: Boolean = false) {
        val platform = platformState.value ?: return
        if (platform.compatibleType != ClientType.OPENROUTER) return
        val token = platform.token?.trim().orEmpty()
        if (token.isBlank()) {
            _openRouterCreditsState.value = OpenRouterCreditsUiState.Idle
            return
        }

        viewModelScope.launch {
            _openRouterCreditsState.value = OpenRouterCreditsUiState.Loading
            openRouterCreditsRepository.fetchCredits(token, forceRefresh = forceRefresh)
                .onSuccess { data ->
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    _openRouterCreditsState.value = OpenRouterCreditsUiState.Success(
                        credits = data,
                        lastUpdatedTime = timeFormat.format(Date())
                    )
                }
                .onFailure { error ->
                    _openRouterCreditsState.value = OpenRouterCreditsUiState.Error(
                        message = error.message ?: "Failed to fetch OpenRouter credits"
                    )
                }
        }
    }

    fun loadToolBindings() {
        viewModelScope.launch {
            runCatching {
                val connections = toolConnectionRepository.listConnections()
                val bindings = toolConnectionRepository.listBindingsByProfile(platformUid)
                val mcpConnections = connections.filter { it.type == ToolConnectionType.MCP }
                val mcpConnectionUids = mcpConnections.map { it.connectionUid }.toSet()
                val searchConnections = connections.filter { it.type in WEB_SEARCH_TYPES }
                val searchConnectionUids = searchConnections.map { it.connectionUid }.toSet()
                ToolBindingState(
                    searchConnections = searchConnections,
                    selectedSearchConnectionUids = bindings.filter {
                        it.toolName == WEB_SEARCH_TOOL && it.connectionUid in searchConnectionUids
                    }.mapNotNull { it.connectionUid }.toSet(),
                    readUrlEnabled = bindings.any { it.toolName == BuiltInAgentTool.READ_URL && it.connectionUid == null },
                    deviceLocationEnabled = bindings.any {
                        it.toolName == BuiltInAgentTool.DEVICE_LOCATION && it.connectionUid == null
                    },
                    mcpConnections = mcpConnections,
                    selectedMcpTools = bindings.mapNotNull { binding ->
                        binding.connectionUid?.takeIf { it in mcpConnectionUids }?.let { ToolBindingSelection(it, binding.toolName) }
                    }.toSet(),
                    errorMessage = null
                )
            }.onSuccess { state ->
                _toolBindingState.update { state }
            }.onFailure(::showToolError)
        }
    }

    fun toggleEnabled() {
        val platform = platformState.value ?: return
        val enabling = !platform.enabled
        if (enabling && platform.compatibleType == ClientType.LITERT_LM) {
            viewModelScope.launch {
                val model = localModelRepository.getById(platform.model)
                if (model?.status != LocalModelStatus.READY) {
                    _userMessage.value = R.string.local_platform_model_not_downloaded
                    return@launch
                }
                updatePlatform(platform.copy(enabled = true))
            }
            return
        }
        updatePlatform(platform.copy(enabled = enabling))
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    fun clearToolError() {
        _toolBindingState.update { it.copy(errorMessage = null) }
    }

    fun toggleStream() {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(stream = !platform.stream))
    }

    fun toggleReasoning() {
        val platform = platformState.value ?: return
        val updated = platform.copy(reasoning = !platform.reasoning)
        updatePlatform(updated)
    }

    fun toggleDisableAllTools() {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(disableAllTools = !platform.disableAllTools))
    }

    fun toggleDisableRemoteTools() {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(disableRemoteTools = !platform.disableRemoteTools))
    }

    fun toggleDisableLocalTools() {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(disableLocalTools = !platform.disableLocalTools))
    }

    fun recommendedMaxTokens(): Int = resolvedEngineMaxTokens(
        requestedMaxTokens = DEFAULT_MAX_TOKENS_CAP,
        accelerator = platformState.value?.accelerator.orEmpty(),
        entry = _catalogEntries.value.firstOrNull { it.id == platformState.value?.model },
        deviceSocModel = deviceSocModel,
        deviceRamGb = deviceRamGb
    )

    fun updatePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.updatePlatformV2(platform)
        }
    }

    fun selectFreeProvider(provider: FreeAiProvider) {
        val platform = platformState.value ?: return
        if (platform.compatibleType != ClientType.FREE) return
        if (!provider.isAvailable) {
            _userMessage.value = R.string.free_ai_approval_description
            return
        }
        viewModelScope.launch {
            try {
                val connection = settingRepository.fetchProviderConnections().firstOrNull {
                    it.compatibleType == ClientType.FREE && FreeAiProvider.fromApiUrl(it.apiUrl) == provider
                } ?: settingRepository.addProviderConnection(
                    ProviderConnection(name = provider.displayName, compatibleType = ClientType.FREE, apiUrl = provider.apiUrl),
                    credential = null
                )
                settingRepository.updatePlatformV2(provider.applyTo(platform).copy(providerConnectionUid = connection.uid))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _userMessage.value = R.string.free_ai_switch_failed
            }
        }
    }

    fun updatePlatformName(name: String) {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(name = name))
        closePlatformNameDialog()
    }

    fun updateApiUrl(url: String) {
        val platform = platformState.value ?: return
        val connection = providerConnectionState.value
        if (connection != null) {
            viewModelScope.launch {
                settingRepository.updateProviderConnection(
                    connection.copy(apiUrl = url.trim()),
                    credential = null
                )
                closeApiUrlDialog()
                if (platform.compatibleType == ClientType.OLLAMA) {
                    checkOllamaServer()
                }
            }
        } else {
            updatePlatform(platform.copy(apiUrl = url))
            closeApiUrlDialog()
            if (platform.compatibleType == ClientType.OLLAMA) {
                checkOllamaServer()
            }
        }
    }

    fun updateApiToken(token: String) {
        val platform = platformState.value ?: return
        val connection = providerConnectionState.value
        if (connection != null) {
            viewModelScope.launch {
                settingRepository.updateProviderConnection(connection, credential = token)
                closeApiTokenDialog()
            }
        } else {
            updatePlatform(platform.copy(token = token))
            closeApiTokenDialog()
        }
    }

    fun updateApiModel(model: String) {
        val platform = platformState.value ?: return
        if (platform.compatibleType == ClientType.LITERT_LM) {
            val entry = _catalogEntries.value.firstOrNull { it.id == model }
            if (entry != null) {
                val defaults = localSamplingDefaults(
                    entry = entry,
                    deviceSocModel = deviceSocModel,
                    deviceRamGb = deviceRamGb
                )
                updatePlatform(
                    platform.copy(
                        model = model,
                        accelerator = defaults.accelerator,
                        temperature = defaults.temperature,
                        topP = defaults.topP,
                        topK = defaults.topK,
                        maxTokens = platform.maxTokens
                    )
                )
            } else {
                // Imported packages have no verified NPU variant/capability metadata.
                updatePlatform(platform.copy(model = model, accelerator = LocalAccelerators.GPU))
            }
        } else {
            updatePlatform(platform.copy(model = model))
        }
        closeApiModelDialog()
    }

    fun updateTemperature(temperature: Float?) {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(temperature = temperature))
        closeTemperatureDialog()
    }

    fun updateTopP(topP: Float?) {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(topP = topP))
        closeTopPDialog()
    }

    fun updateCreativity(value: Float) {
        val platform = platformState.value ?: return
        val sampling = SamplingCreativity.toSampling(value)
        updatePlatform(
            platform.copy(
                temperature = sampling.temperature,
                topP = sampling.topP
            )
        )
    }

    fun saveProfileLabels(labels: List<ProfileLabel>) {
        val current = platformState.value ?: return
        viewModelScope.launch {
            val desiredColors = labels.associateBy(ProfileLabel::key)
            val profiles = settingRepository.fetchPlatformV2s()
            profiles.forEach { profile ->
                val linked = parseProfileLabels(profile.labels)
                val recolored = linked.map { existing ->
                    desiredColors[existing.key]
                        ?.colorHex
                        ?.let { existing.copy(colorHex = it) }
                        ?: existing
                }
                val targetLabels = if (profile.uid == current.uid) labels else recolored
                val encoded = encodeProfileLabels(targetLabels)
                if (encoded != profile.labels) {
                    settingRepository.updatePlatformV2(profile.copy(labels = encoded))
                }
            }
            closeLabelsDialog()
        }
    }

    fun updateTopK(topK: Int?) {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(topK = topK))
        closeTopKDialog()
    }

    fun updateMaxTokens(maxTokens: Int?) {
        val platform = platformState.value ?: return
        if (maxTokens != null && maxTokens < MIN_MAX_TOKENS) return
        updatePlatform(platform.copy(maxTokens = maxTokens))
        closeMaxTokensDialog()
    }

    fun updateAccelerator(accelerator: String?) {
        val platform = platformState.value ?: return
        val allowed = acceleratorOptions.value.filter { it.enabled }.map { it.accelerator }.toSet()
        val normalized = accelerator?.let { LocalAccelerators.normalize(it) }?.takeIf { it in allowed } ?: return
        updatePlatform(platform.copy(accelerator = normalized))
        closeAcceleratorDialog()
    }

    fun updateSystemPrompt(prompt: String?) {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(systemPrompt = prompt?.takeIf { it.isNotBlank() }))
        closeSystemPromptDialog()
    }

    fun updateTimeout(timeout: Int) {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(timeout = timeout))
        closeTimeoutDialog()
    }

    fun updateGeminiSafetySettings(
        harassment: String,
        hateSpeech: String,
        sexuallyExplicit: String,
        dangerousContent: String
    ) {
        val platform = platformState.value ?: return
        updatePlatform(
            platform.copy(
                harassmentSafetyThreshold = harassment,
                hateSpeechSafetyThreshold = hateSpeech,
                sexuallyExplicitSafetyThreshold = sexuallyExplicit,
                dangerousContentSafetyThreshold = dangerousContent
            )
        )
        closeGeminiSafetyDialog()
    }

    fun updateOpenRouterRouting(routingJson: String?) {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(openRouterRouting = routingJson))
        closeOpenRouterSettingsDialog()
    }

    fun updateOllamaOptions(optionsJson: String?) {
        val platform = platformState.value ?: return
        updatePlatform(platform.copy(ollamaOptions = optionsJson))
        closeOllamaAdvancedDialog()
    }

    fun deletePlatform() {
        val platform = platformState.value ?: return
        viewModelScope.launch {
            settingRepository.deletePlatformV2(platform)
            _isDeleted.value = true
            closeDeleteDialog()
        }
    }

    // Dialog state management
    fun openPlatformNameDialog() {
        _dialogState.update { it.copy(isPlatformNameDialogOpen = true) }
    }

    fun closePlatformNameDialog() {
        _dialogState.update { it.copy(isPlatformNameDialogOpen = false) }
    }

    fun openLabelsDialog() {
        _dialogState.update { it.copy(isLabelsDialogOpen = true) }
    }

    fun closeLabelsDialog() {
        _dialogState.update { it.copy(isLabelsDialogOpen = false) }
    }

    fun openApiUrlDialog() {
        _dialogState.update { it.copy(isApiUrlDialogOpen = true) }
    }

    fun closeApiUrlDialog() {
        _dialogState.update { it.copy(isApiUrlDialogOpen = false) }
    }

    fun openApiTokenDialog() {
        _dialogState.update { it.copy(isApiTokenDialogOpen = true) }
    }

    fun closeApiTokenDialog() {
        _dialogState.update { it.copy(isApiTokenDialogOpen = false) }
    }

    fun openApiModelDialog() {
        _dialogState.update { it.copy(isApiModelDialogOpen = true) }
    }

    fun closeApiModelDialog() {
        _dialogState.update { it.copy(isApiModelDialogOpen = false) }
    }

    fun openTemperatureDialog() {
        _dialogState.update { it.copy(isTemperatureDialogOpen = true) }
    }

    fun closeTemperatureDialog() {
        _dialogState.update { it.copy(isTemperatureDialogOpen = false) }
    }

    fun openTopPDialog() {
        _dialogState.update { it.copy(isTopPDialogOpen = true) }
    }

    fun closeTopPDialog() {
        _dialogState.update { it.copy(isTopPDialogOpen = false) }
    }

    fun openTopKDialog() {
        _dialogState.update { it.copy(isTopKDialogOpen = true) }
    }

    fun closeTopKDialog() {
        _dialogState.update { it.copy(isTopKDialogOpen = false) }
    }

    fun openMaxTokensDialog() {
        _dialogState.update { it.copy(isMaxTokensDialogOpen = true) }
    }

    fun closeMaxTokensDialog() {
        _dialogState.update { it.copy(isMaxTokensDialogOpen = false) }
    }

    fun openAcceleratorDialog() {
        _dialogState.update { it.copy(isAcceleratorDialogOpen = true) }
    }

    fun closeAcceleratorDialog() {
        _dialogState.update { it.copy(isAcceleratorDialogOpen = false) }
    }

    fun openSystemPromptDialog() {
        _dialogState.update { it.copy(isSystemPromptDialogOpen = true) }
    }

    fun closeSystemPromptDialog() {
        _dialogState.update { it.copy(isSystemPromptDialogOpen = false) }
    }

    fun openTimeoutDialog() {
        _dialogState.update { it.copy(isTimeoutDialogOpen = true) }
    }

    fun closeTimeoutDialog() {
        _dialogState.update { it.copy(isTimeoutDialogOpen = false) }
    }

    fun openGeminiSafetyDialog() {
        _dialogState.update { it.copy(isGeminiSafetyDialogOpen = true) }
    }

    fun closeGeminiSafetyDialog() {
        _dialogState.update { it.copy(isGeminiSafetyDialogOpen = false) }
    }

    fun openOpenRouterSettingsDialog() {
        _dialogState.update { it.copy(isOpenRouterSettingsDialogOpen = true) }
    }

    fun closeOpenRouterSettingsDialog() {
        _dialogState.update { it.copy(isOpenRouterSettingsDialogOpen = false) }
    }

    fun openOllamaAdvancedDialog() {
        _dialogState.update { it.copy(isOllamaAdvancedDialogOpen = true) }
    }

    fun closeOllamaAdvancedDialog() {
        _dialogState.update { it.copy(isOllamaAdvancedDialogOpen = false) }
    }

    fun openLlamaAdvancedDialog() {
        _dialogState.update { it.copy(isLlamaAdvancedDialogOpen = true) }
    }

    fun closeLlamaAdvancedDialog() {
        _dialogState.update { it.copy(isLlamaAdvancedDialogOpen = false) }
    }

    fun openDeleteDialog() {
        _dialogState.update { it.copy(isDeleteDialogOpen = true) }
    }

    fun closeDeleteDialog() {
        _dialogState.update { it.copy(isDeleteDialogOpen = false) }
    }

    fun openSearchBackendDialog() {
        _toolBindingState.update { it.copy(isSearchBackendDialogOpen = true, errorMessage = null) }
    }

    fun closeSearchBackendDialog() {
        _toolBindingState.update { it.copy(isSearchBackendDialogOpen = false) }
    }

    fun selectSearchBackends(connectionUids: Set<String>) {
        viewModelScope.launch {
            runCatching {
                toolConnectionRepository.replaceWebSearchBindings(platformUid, connectionUids)
                _toolBindingState.update {
                    it.copy(selectedSearchConnectionUids = connectionUids, isSearchBackendDialogOpen = false, errorMessage = null)
                }
            }.onFailure(::showToolError)
        }
    }

    fun toggleReadUrl(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                toolConnectionRepository.setReadUrlBinding(platformUid, enabled)
                _toolBindingState.update { it.copy(readUrlEnabled = enabled, errorMessage = null) }
            }.onFailure(::showToolError)
        }
    }

    fun toggleDeviceLocation(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                toolConnectionRepository.setBuiltInToolBinding(
                    platformUid,
                    BuiltInAgentTool.DEVICE_LOCATION,
                    enabled
                )
                _toolBindingState.update {
                    it.copy(deviceLocationEnabled = enabled, errorMessage = null)
                }
            }.onFailure(::showToolError)
        }
    }

    fun openMcpToolsDialog() {
        val currentState = _toolBindingState.value
        _toolBindingState.update {
            it.copy(
                isMcpToolsDialogOpen = true,
                isMcpToolsLoading = true,
                pendingMcpTools = currentState.selectedMcpTools,
                errorMessage = null
            )
        }
        mcpDiscoveryJob?.cancel()
        mcpDiscoveryJob = viewModelScope.launch {
            try {
                val connections = currentState.mcpConnections
                val options = coroutineScope {
                    connections.map { connection ->
                        async {
                            runCatching {
                                val tools = agentToolResolver.discoverMcpTools(connection)
                                tools.map { tool ->
                                    McpToolOption(
                                        connectionUid = connection.connectionUid,
                                        connectionName = connection.name,
                                        toolName = tool.name,
                                        modelToolName = namespaceMcpToolName(connection.name, tool.name),
                                        description = tool.description
                                    )
                                }
                            }.getOrDefault(emptyList())
                        }
                    }.awaitAll().flatten().sortedWith(compareBy({ it.connectionName }, { it.toolName }))
                }
                _toolBindingState.update {
                    it.copy(
                        mcpToolOptions = options,
                        isMcpToolsLoading = false
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _toolBindingState.update {
                    it.copy(
                        isMcpToolsLoading = false,
                        errorMessage = e.message ?: "Failed to discover MCP tools."
                    )
                }
            }
        }
    }

    fun closeMcpToolsDialog() {
        mcpDiscoveryJob?.cancel()
        _toolBindingState.update {
            it.copy(
                isMcpToolsDialogOpen = false,
                isMcpToolsLoading = false,
                pendingMcpTools = emptySet()
            )
        }
    }

    fun toggleMcpTool(connectionUid: String, toolName: String) {
        _toolBindingState.update { state ->
            val updated = state.pendingMcpTools.toMutableSet()
            val item = ToolBindingSelection(connectionUid, toolName)
            if (item in updated) {
                updated.remove(item)
            } else {
                updated.add(item)
            }
            state.copy(pendingMcpTools = updated)
        }
    }

    fun togglePendingMcpTool(connectionUid: String, toolName: String, enabled: Boolean) {
        _toolBindingState.update { state ->
            val updated = state.pendingMcpTools.toMutableSet()
            val item = ToolBindingSelection(connectionUid, toolName)
            if (enabled) {
                updated.add(item)
            } else {
                updated.remove(item)
            }
            state.copy(pendingMcpTools = updated)
        }
    }

    fun saveMcpTools() {
        saveMcpToolSelections()
    }

    fun saveMcpToolSelections() {
        val selections = _toolBindingState.value.pendingMcpTools
        viewModelScope.launch {
            runCatching {
                toolConnectionRepository.replaceMcpToolBindings(platformUid, selections.toList())
            }
                .onSuccess {
                    _toolBindingState.update {
                        it.copy(
                            selectedMcpTools = selections,
                            pendingMcpTools = emptySet(),
                            isMcpToolsDialogOpen = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure(::showToolError)
        }
    }

    private fun showToolError(error: Throwable) {
        _toolBindingState.update { it.copy(errorMessage = error.message ?: "Tool binding update failed.") }
    }

    data class DialogState(
        val isPlatformNameDialogOpen: Boolean = false,
        val isLabelsDialogOpen: Boolean = false,
        val isApiUrlDialogOpen: Boolean = false,
        val isApiTokenDialogOpen: Boolean = false,
        val isApiModelDialogOpen: Boolean = false,
        val isTemperatureDialogOpen: Boolean = false,
        val isTopPDialogOpen: Boolean = false,
        val isTopKDialogOpen: Boolean = false,
        val isMaxTokensDialogOpen: Boolean = false,
        val isAcceleratorDialogOpen: Boolean = false,
        val isSystemPromptDialogOpen: Boolean = false,
        val isTimeoutDialogOpen: Boolean = false,
        val isGeminiSafetyDialogOpen: Boolean = false,
        val isOpenRouterSettingsDialogOpen: Boolean = false,
        val isOllamaAdvancedDialogOpen: Boolean = false,
        val isLlamaAdvancedDialogOpen: Boolean = false,
        val isDeleteDialogOpen: Boolean = false
    )

    data class ToolBindingState(
        val searchConnections: List<ToolConnection> = emptyList(),
        val selectedSearchConnectionUids: Set<String> = emptySet(),
        val readUrlEnabled: Boolean = false,
        val deviceLocationEnabled: Boolean = false,
        val mcpConnections: List<ToolConnection> = emptyList(),
        val selectedMcpTools: Set<ToolBindingSelection> = emptySet(),
        val pendingMcpTools: Set<ToolBindingSelection> = emptySet(),
        val mcpToolOptions: List<McpToolOption> = emptyList(),
        val isSearchBackendDialogOpen: Boolean = false,
        val isMcpToolsDialogOpen: Boolean = false,
        val isMcpToolsLoading: Boolean = false,
        val errorMessage: String? = null
    )

    data class McpToolOption(
        val connectionUid: String,
        val connectionName: String,
        val toolName: String,
        val modelToolName: String,
        val description: String?
    )

    companion object {
        private const val WEB_SEARCH_TOOL = "web_search"
        private val WEB_SEARCH_TYPES = setOf(ToolConnectionType.FIRECRAWL, ToolConnectionType.PERPLEXITY, ToolConnectionType.EXA)
        const val MIN_TOP_K = 1
        const val MAX_TOP_K = 128
        const val MIN_MAX_TOKENS = 1
        const val DEFAULT_MAX_TOKENS_CAP = 32768
    }
}
