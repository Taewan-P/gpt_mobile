package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.McpServerConfig
import dev.chungjungsoo.gptmobile.data.mcp.McpManager
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
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

    init {
        refresh()
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

    fun addHeader(key: String, value: String) {
        if (key.isBlank()) return
        val currentServer = _uiState.value.server ?: return
        viewModelScope.launch {
            val updated = currentServer.copy(headers = currentServer.headers + (key to value))
            settingRepository.updateMcpServer(updated)
            _uiState.update { it.copy(server = updated) }
        }
    }

    fun removeHeader(key: String) {
        val currentServer = _uiState.value.server ?: return
        viewModelScope.launch {
            val updated = currentServer.copy(headers = currentServer.headers - key)
            settingRepository.updateMcpServer(updated)
            _uiState.update { it.copy(server = updated) }
        }
    }

    fun updateHeader(oldKey: String, newKey: String, newValue: String) {
        val currentServer = _uiState.value.server ?: return
        viewModelScope.launch {
            val updatedHeaders = currentServer.headers.toMutableMap()
            updatedHeaders.remove(oldKey)
            if (newKey.isNotBlank()) {
                updatedHeaders[newKey] = newValue
            }
            val updated = currentServer.copy(headers = updatedHeaders)
            settingRepository.updateMcpServer(updated)
            _uiState.update { it.copy(server = updated) }
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
        val isDeleted: Boolean = false
    )

    companion object {
        private const val TEST_CONNECTION_TIMEOUT_MS = 10_000L
    }
}
