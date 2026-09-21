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
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localmodel.SocVariantResolver
import dev.chungjungsoo.gptmobile.data.localruntime.AcceleratorOption
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.localruntime.localSamplingDefaults
import dev.chungjungsoo.gptmobile.data.localruntime.resolvedEngineMaxTokens
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.GeminiSafetySettings
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.ToolBindingSelection
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import dev.chungjungsoo.gptmobile.di.DeviceSocModel
import dev.chungjungsoo.gptmobile.presentation.ui.setup.DownloadedLocalModelOption
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlatformLoadState {
    data object Loading : PlatformLoadState
    data object Loaded : PlatformLoadState
    data object NotFound : PlatformLoadState
    data class Error(val message: String) : PlatformLoadState
}

@HiltViewModel
class PlatformSettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    toolConnectionDao: ToolConnectionDao,
    secretVault: SecretVault,
    private val agentToolResolver: AgentToolResolver,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val localModelRepository: LocalModelRepository,
    @param:DeviceSocModel private val deviceSocModel: String,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val toolConnectionRepository = ToolConnectionRepository(toolConnectionDao, secretVault)

    private val platformUid: String = checkNotNull(savedStateHandle["platformUid"])

    private val _platformState = MutableStateFlow<PlatformV2?>(null)
    val platformState: StateFlow<PlatformV2?> = _platformState.asStateFlow()

    private val _platformLoadState = MutableStateFlow<PlatformLoadState>(PlatformLoadState.Loading)
    val platformLoadState: StateFlow<PlatformLoadState> = _platformLoadState.asStateFlow()

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

    val acceleratorOptions: StateFlow<List<AcceleratorOption>> = combine(_platformState, _catalogEntries) { platform, catalog ->
        val entry = catalog.firstOrNull { it.id == platform?.model }
        LocalAccelerators.choices(
            supported = entry?.supportedAccelerators.orEmpty(),
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

    init {
        loadPlatform()
        loadToolBindings()
        loadCatalog()
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            _catalogEntries.value = modelCatalogRepository.getVisibleEntries()
        }
    }

    private fun loadPlatform() {
        _platformLoadState.value = PlatformLoadState.Loading
        viewModelScope.launch {
            try {
                val platform = settingRepository.fetchPlatformV2s().firstOrNull { it.uid == platformUid }
                _platformState.value = platform
                _platformLoadState.value = if (platform == null) {
                    PlatformLoadState.NotFound
                } else {
                    PlatformLoadState.Loaded
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                _platformState.value = null
                _platformLoadState.value = PlatformLoadState.Error(
                    throwable.message ?: "Could not load platform"
                )
            }
        }
    }

    fun retryLoadPlatform() = loadPlatform()

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
                    selectedSearchConnectionUid = bindings.firstOrNull {
                        it.toolName == WEB_SEARCH_TOOL && it.connectionUid in searchConnectionUids
                    }?.connectionUid,
                    readUrlEnabled = bindings.any { it.toolName == BuiltInAgentTool.READ_URL && it.connectionUid == null },
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
        val platform = _platformState.value ?: return
        val enabling = !platform.enabled
        if (enabling && platform.compatibleType == ClientType.LITERT_LM) {
            viewModelScope.launch {
                val model = localModelRepository.getById(platform.model)
                if (model?.status != LocalModelStatus.READY) {
                    _userMessage.value = R.string.local_platform_enable_model_not_ready
                    return@launch
                }
                updatePlatform(platform.copy(enabled = true))
            }
            return
        }
        updatePlatform(platform.copy(enabled = !platform.enabled))
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    fun toggleReasoning() {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(reasoning = !platform.reasoning))
        }
    }

    fun updatePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.updatePlatformV2(platform)
            _platformState.update { platform }
        }
    }

    fun openPlatformNameDialog() = _dialogState.update { it.copy(isPlatformNameDialogOpen = true) }
    fun closePlatformNameDialog() = _dialogState.update { it.copy(isPlatformNameDialogOpen = false) }

    fun openApiUrlDialog() = _dialogState.update { it.copy(isApiUrlDialogOpen = true) }
    fun closeApiUrlDialog() = _dialogState.update { it.copy(isApiUrlDialogOpen = false) }

    fun openApiTokenDialog() = _dialogState.update { it.copy(isApiTokenDialogOpen = true) }
    fun closeApiTokenDialog() = _dialogState.update { it.copy(isApiTokenDialogOpen = false) }

    fun openApiModelDialog() = _dialogState.update { it.copy(isApiModelDialogOpen = true) }
    fun closeApiModelDialog() = _dialogState.update { it.copy(isApiModelDialogOpen = false) }

    fun openTemperatureDialog() = _dialogState.update { it.copy(isTemperatureDialogOpen = true) }
    fun closeTemperatureDialog() = _dialogState.update { it.copy(isTemperatureDialogOpen = false) }

    fun openTopPDialog() = _dialogState.update { it.copy(isTopPDialogOpen = true) }
    fun closeTopPDialog() = _dialogState.update { it.copy(isTopPDialogOpen = false) }

    fun openTopKDialog() = _dialogState.update { it.copy(isTopKDialogOpen = true) }
    fun closeTopKDialog() = _dialogState.update { it.copy(isTopKDialogOpen = false) }

    fun openMaxTokensDialog() = _dialogState.update { it.copy(isMaxTokensDialogOpen = true) }
    fun closeMaxTokensDialog() = _dialogState.update { it.copy(isMaxTokensDialogOpen = false) }

    fun openAcceleratorDialog() = _dialogState.update { it.copy(isAcceleratorDialogOpen = true) }
    fun closeAcceleratorDialog() = _dialogState.update { it.copy(isAcceleratorDialogOpen = false) }

    fun openSystemPromptDialog() = _dialogState.update { it.copy(isSystemPromptDialogOpen = true) }
    fun closeSystemPromptDialog() = _dialogState.update { it.copy(isSystemPromptDialogOpen = false) }

    fun openTimeoutDialog() = _dialogState.update { it.copy(isTimeoutDialogOpen = true) }
    fun closeTimeoutDialog() = _dialogState.update { it.copy(isTimeoutDialogOpen = false) }

    fun openGeminiSafetyDialog() = _dialogState.update { it.copy(isGeminiSafetyDialogOpen = true) }
    fun closeGeminiSafetyDialog() = _dialogState.update { it.copy(isGeminiSafetyDialogOpen = false) }

    fun updatePlatformName(name: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(name = name.trim()))
            closePlatformNameDialog()
        }
    }

    fun updateApiUrl(url: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(apiUrl = url.trim()))
            closeApiUrlDialog()
        }
    }

    fun updateApiToken(token: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(token = token.trim().takeIf { it.isNotEmpty() }))
            closeApiTokenDialog()
        }
    }

    fun updateApiModel(model: String) {
        _platformState.value?.let { platform ->
            val trimmed = model.trim()
            val updated = if (platform.compatibleType == ClientType.LITERT_LM) {
                reseedLocalModelDefaults(platform, trimmed)
            } else {
                platform.copy(model = trimmed)
            }
            updatePlatform(updated)
            closeApiModelDialog()
        }
    }

    private fun reseedLocalModelDefaults(platform: PlatformV2, catalogEntryId: String): PlatformV2 {
        val defaults = _catalogEntries.value
            .firstOrNull { it.id == catalogEntryId }
            ?.let { localSamplingDefaults(it, deviceSocModel) }
        return platform.copy(
            model = catalogEntryId,
            temperature = defaults?.temperature ?: platform.temperature,
            topP = defaults?.topP ?: platform.topP,
            topK = defaults?.topK ?: platform.topK,
            maxTokens = defaults?.maxTokens ?: platform.maxTokens,
            accelerator = defaults?.accelerator ?: platform.accelerator
        )
    }

    fun updateTemperature(temperature: Float?) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(temperature = temperature))
            closeTemperatureDialog()
        }
    }

    fun updateTopP(topP: Float?) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(topP = topP))
            closeTopPDialog()
        }
    }

    fun updateTopK(topK: Int?) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(topK = topK?.coerceIn(MIN_TOP_K, MAX_TOP_K)))
            closeTopKDialog()
        }
    }

    fun updateMaxTokens(maxTokens: Int?) {
        _platformState.value?.let { platform ->
            val capped = maxTokens?.let { requested ->
                resolvedEngineMaxTokens(
                    requestedMaxTokens = requested.coerceIn(MIN_MAX_TOKENS, DEFAULT_MAX_TOKENS_CAP),
                    accelerator = platform.accelerator.orEmpty(),
                    entry = catalogEntryFor(platform),
                    deviceSocModel = deviceSocModel
                )
            }
            updatePlatform(platform.copy(maxTokens = capped))
            closeMaxTokensDialog()
        }
    }

    fun maxTokensCap(): Int {
        val platform = _platformState.value ?: return DEFAULT_MAX_TOKENS_CAP
        val variantLimit = SocVariantResolver.resolve(
            catalogEntryFor(platform) ?: return DEFAULT_MAX_TOKENS_CAP,
            deviceSocModel
        ).contextSize
        if (LocalAccelerators.normalize(platform.accelerator) != LocalAccelerators.NPU || variantLimit <= 0) {
            return DEFAULT_MAX_TOKENS_CAP
        }
        return variantLimit
    }

    private fun catalogEntryFor(platform: PlatformV2): CatalogEntry? = _catalogEntries.value.firstOrNull { it.id == platform.model }

    fun updateAccelerator(accelerator: String) {
        val normalized = LocalAccelerators.normalize(accelerator)
        if (normalized != LocalAccelerators.CPU &&
            normalized != LocalAccelerators.GPU &&
            normalized != LocalAccelerators.NPU
        ) {
            return
        }
        val option = acceleratorOptions.value.firstOrNull { it.accelerator == normalized }
        if (option?.enabled != true) return
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(accelerator = normalized))
            closeAcceleratorDialog()
        }
    }

    fun updateSystemPrompt(prompt: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(systemPrompt = prompt.trim()))
            closeSystemPromptDialog()
        }
    }

    fun updateTimeout(timeoutSeconds: Int) {
        _platformState.value?.let { platform ->
            val normalizedTimeout = timeoutSeconds.coerceAtLeast(0)
            updatePlatform(platform.copy(timeout = normalizedTimeout))
            closeTimeoutDialog()
        }
    }

    fun updateGeminiSafetySettings(
        harassmentSafetyThreshold: String,
        hateSpeechSafetyThreshold: String,
        sexuallyExplicitSafetyThreshold: String,
        dangerousContentSafetyThreshold: String
    ) {
        _platformState.value?.let { platform ->
            updatePlatform(
                platform.copy(
                    harassmentSafetyThreshold = GeminiSafetySettings.normalizeThreshold(harassmentSafetyThreshold),
                    hateSpeechSafetyThreshold = GeminiSafetySettings.normalizeThreshold(hateSpeechSafetyThreshold),
                    sexuallyExplicitSafetyThreshold = GeminiSafetySettings.normalizeThreshold(sexuallyExplicitSafetyThreshold),
                    dangerousContentSafetyThreshold = GeminiSafetySettings.normalizeThreshold(dangerousContentSafetyThreshold)
                )
            )
            closeGeminiSafetyDialog()
        }
    }

    fun openDeleteDialog() = _dialogState.update { it.copy(isDeleteDialogOpen = true) }
    fun closeDeleteDialog() = _dialogState.update { it.copy(isDeleteDialogOpen = false) }

    fun deletePlatform() {
        _platformState.value?.let { platform ->
            viewModelScope.launch {
                settingRepository.deletePlatformV2(platform)
                closeDeleteDialog()
                _isDeleted.update { true }
            }
        }
    }

    fun openSearchBackendDialog() = _toolBindingState.update { it.copy(isSearchBackendDialogOpen = true) }
    fun closeSearchBackendDialog() = _toolBindingState.update { it.copy(isSearchBackendDialogOpen = false) }
    fun clearToolError() = _toolBindingState.update { it.copy(errorMessage = null) }

    fun selectSearchBackend(connectionUid: String?) {
        viewModelScope.launch {
            runCatching {
                if (connectionUid == null) {
                    toolConnectionRepository.removeWebSearchBinding(platformUid)
                } else {
                    toolConnectionRepository.replaceWebSearchBinding(platformUid, connectionUid)
                }
            }
                .onSuccess {
                    _toolBindingState.update {
                        it.copy(selectedSearchConnectionUid = connectionUid, isSearchBackendDialogOpen = false, errorMessage = null)
                    }
                }
                .onFailure(::showToolError)
        }
    }

    fun toggleReadUrl(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { toolConnectionRepository.setReadUrlBinding(platformUid, enabled) }
                .onSuccess {
                    _toolBindingState.update { it.copy(readUrlEnabled = enabled, errorMessage = null) }
                }
                .onFailure(::showToolError)
        }
    }

    fun openMcpToolsDialog() {
        mcpDiscoveryJob?.cancel()
        val connections = _toolBindingState.value.mcpConnections
        _toolBindingState.update {
            it.copy(
                isMcpToolsDialogOpen = true,
                isMcpToolsLoading = true,
                mcpToolOptions = emptyList(),
                pendingMcpTools = it.selectedMcpTools,
                errorMessage = null
            )
        }
        mcpDiscoveryJob = viewModelScope.launch {
            try {
                val results = coroutineScope {
                    connections.map { connection ->
                        async { connection to discoverMcpTools(connection) }
                    }.awaitAll()
                }
                val options = results.flatMap { (connection, result) ->
                    result.getOrDefault(emptyList()).map { tool ->
                        McpToolOption(
                            connectionUid = connection.connectionUid,
                            connectionName = connection.name,
                            toolName = tool.name,
                            modelToolName = namespaceMcpToolName(connection.alias, tool.name),
                            description = tool.description
                        )
                    }
                }.sortedWith(compareBy<McpToolOption> { it.connectionName }.thenBy { it.toolName })
                val failures = results.mapNotNull { (connection, result) ->
                    result.exceptionOrNull()?.let { "${connection.name}: ${it.message ?: "discovery failed"}" }
                }
                _toolBindingState.update {
                    it.copy(
                        isMcpToolsLoading = false,
                        mcpToolOptions = options,
                        errorMessage = failures.takeIf(List<String>::isNotEmpty)?.joinToString("\n")
                    )
                }
            } catch (error: CancellationException) {
                throw error
            }
        }
    }

    fun closeMcpToolsDialog() {
        mcpDiscoveryJob?.cancel()
        _toolBindingState.update { it.copy(isMcpToolsDialogOpen = false, isMcpToolsLoading = false, errorMessage = null) }
    }

    private suspend fun discoverMcpTools(connection: ToolConnection) = try {
        Result.success(agentToolResolver.discoverMcpTools(connection))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    fun toggleMcpTool(connectionUid: String, toolName: String) {
        val selection = ToolBindingSelection(connectionUid, toolName)
        _toolBindingState.update { state ->
            state.copy(
                pendingMcpTools = state.pendingMcpTools.toMutableSet().apply {
                    if (!add(selection)) remove(selection)
                }
            )
        }
    }

    fun saveMcpTools() {
        val selections = _toolBindingState.value.pendingMcpTools
            .sortedWith(compareBy<ToolBindingSelection> { it.connectionUid }.thenBy { it.toolName })
        viewModelScope.launch {
            runCatching { toolConnectionRepository.replaceMcpToolBindings(platformUid, selections) }
                .onSuccess {
                    _toolBindingState.update {
                        it.copy(
                            selectedMcpTools = selections.toSet(),
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
        val isDeleteDialogOpen: Boolean = false
    )

    data class ToolBindingState(
        val searchConnections: List<ToolConnection> = emptyList(),
        val selectedSearchConnectionUid: String? = null,
        val readUrlEnabled: Boolean = false,
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
