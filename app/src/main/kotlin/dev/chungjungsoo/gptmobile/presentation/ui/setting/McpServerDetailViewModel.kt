package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.McpServerConfig
import dev.chungjungsoo.gptmobile.data.database.entity.McpTransportType
import dev.chungjungsoo.gptmobile.data.mcp.McpManager
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@HiltViewModel
class McpServerDetailViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val mcpManager: McpManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: Int = checkNotNull(savedStateHandle["serverId"])

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private var persistJob: Job? = null

    init {
        refresh()
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            val latest = _uiState.value.server ?: return@launch
            settingRepository.updateMcpServer(latest)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val server = settingRepository.getMcpServerById(serverId)
            _uiState.update { it.copy(server = server) }
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        val currentServer = _uiState.value.server ?: return
        viewModelScope.launch {
            val updated = currentServer.copy(enabled = enabled)
            settingRepository.updateMcpServer(updated)
            _uiState.update {
                it.copy(
                    server = updated,
                    statusMessage = null,
                    isStatusError = false
                )
            }
            if (enabled) {
                mcpManager.connect(updated)
            } else {
                mcpManager.disconnect(updated.id)
            }
        }
    }

    fun updateName(name: String) {
        if (name.isBlank()) return
        _uiState.update { state ->
            val current = state.server ?: return@update state
            state.copy(server = current.copy(name = name))
        }
        schedulePersist()
    }

    fun updateType(type: McpTransportType) {
        _uiState.update { state ->
            val current = state.server ?: return@update state
            state.copy(server = current.copy(type = type))
        }
        schedulePersist()
    }

    fun updateUrl(url: String) {
        _uiState.update { state ->
            val current = state.server ?: return@update state
            state.copy(server = current.copy(url = url.ifBlank { null }))
        }
        schedulePersist()
    }

    fun updateCommand(command: String) {
        _uiState.update { state ->
            val current = state.server ?: return@update state
            state.copy(server = current.copy(command = command.ifBlank { null }))
        }
        schedulePersist()
    }

    fun updateMaxToolCallIterations(maxIterations: Int) {
        _uiState.update { state ->
            val current = state.server ?: return@update state
            state.copy(server = current.copy(maxToolCallIterations = maxIterations))
        }
        schedulePersist()
    }

    fun addArg(arg: String) {
        if (arg.isBlank()) return
        _uiState.update { state ->
            val current = state.server ?: return@update state
            state.copy(server = current.copy(args = current.args + arg))
        }
        schedulePersist()
    }

    fun removeArg(index: Int) {
        val currentServer = _uiState.value.server ?: return
        if (index < 0 || index >= currentServer.args.size) return
        _uiState.update { state ->
            val current = state.server ?: return@update state
            val nextArgs = current.args.toMutableList().apply { removeAt(index) }
            state.copy(server = current.copy(args = nextArgs))
        }
        schedulePersist()
    }

    fun updateArg(index: Int, value: String) {
        val currentServer = _uiState.value.server ?: return
        if (index < 0 || index >= currentServer.args.size) return
        _uiState.update { state ->
            val current = state.server ?: return@update state
            val nextArgs = current.args.toMutableList().apply { set(index, value) }
            state.copy(server = current.copy(args = nextArgs))
        }
        schedulePersist()
    }

    fun addHeader(key: String, value: String) {
        if (key.isBlank()) return
        _uiState.update { state ->
            val current = state.server ?: return@update state
            state.copy(server = current.copy(headers = current.headers + (key to value)))
        }
        schedulePersist()
    }

    fun removeHeader(key: String) {
        _uiState.update { state ->
            val current = state.server ?: return@update state
            state.copy(server = current.copy(headers = current.headers - key))
        }
        schedulePersist()
    }

    fun updateHeader(oldKey: String, newKey: String, newValue: String) {
        _uiState.update { state ->
            val current = state.server ?: return@update state
            val updatedHeaders = current.headers.toMutableMap()
            updatedHeaders.remove(oldKey)
            if (newKey.isNotBlank()) {
                updatedHeaders[newKey] = newValue
            }
            state.copy(server = current.copy(headers = updatedHeaders))
        }
        schedulePersist()
    }

    fun updatePendingArg(value: String) {
        _uiState.update { it.copy(pendingArg = value) }
    }

    fun updatePendingHeaderKey(value: String) {
        _uiState.update { it.copy(pendingHeaderKey = value) }
    }

    fun updatePendingHeaderValue(value: String) {
        _uiState.update { it.copy(pendingHeaderValue = value) }
    }

    fun commitPending() {
        val state = _uiState.value
        val current = state.server ?: return

        var updated = current
        if (state.pendingArg.isNotBlank()) {
            updated = updated.copy(args = updated.args + state.pendingArg)
        }
        if (state.pendingHeaderKey.isNotBlank()) {
            updated = updated.copy(headers = updated.headers + (state.pendingHeaderKey to state.pendingHeaderValue))
        }

        if (updated != current) {
            _uiState.update {
                it.copy(
                    server = updated,
                    pendingArg = "",
                    pendingHeaderKey = "",
                    pendingHeaderValue = ""
                )
            }
            // Force immediate persistence - don't use debounce
            viewModelScope.launch {
                settingRepository.updateMcpServer(updated)
            }
        }
    }

    fun testConnection() {
        val currentServer = _uiState.value.server ?: return
        _uiState.update { it.copy(isTesting = true, statusMessage = null, isStatusError = false) }

        viewModelScope.launch {
            val result = runCatching {
                withTimeout(TEST_CONNECTION_TIMEOUT_MS) {
                    mcpManager.connect(currentServer.copy(enabled = true)).getOrThrow()
                }
            }
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isTesting = false, statusMessage = "Connection successful", isStatusError = false)
                } else {
                    it.copy(
                        isTesting = false,
                        statusMessage = result.exceptionOrNull()?.message ?: "Connection failed",
                        isStatusError = true
                    )
                }
            }
        }
    }

    fun delete() {
        val currentServer = _uiState.value.server ?: return
        viewModelScope.launch {
            runCatching {
                settingRepository.deleteMcpServer(currentServer)
            }.onSuccess {
                _uiState.update { it.copy(server = null, isDeleted = true) }

                // Cleanup should not block navigation; disconnect in background.
                viewModelScope.launch {
                    runCatching { mcpManager.disconnect(currentServer.id) }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        statusMessage = throwable.message ?: "Failed to delete server",
                        isStatusError = true
                    )
                }
            }
        }
    }

    data class UiState(
        val server: McpServerConfig? = null,
        val isTesting: Boolean = false,
        val statusMessage: String? = null,
        val isStatusError: Boolean = false,
        val isDeleted: Boolean = false,
        val pendingArg: String = "",
        val pendingHeaderKey: String = "",
        val pendingHeaderValue: String = ""
    )

    companion object {
        private const val TEST_CONNECTION_TIMEOUT_MS = 10_000L
        private const val PERSIST_DEBOUNCE_MS = 250L
    }
}
