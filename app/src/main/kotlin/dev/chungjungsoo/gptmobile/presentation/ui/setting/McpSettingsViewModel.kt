package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.McpServerConfig
import dev.chungjungsoo.gptmobile.data.dto.tool.Tool
import dev.chungjungsoo.gptmobile.data.mcp.McpManager
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.tool.BuiltInTool
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "McpSettingsViewModel"

@HiltViewModel
class McpSettingsViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val mcpManager: McpManager,
    builtInTools: Set<@JvmSuppressWildcards BuiltInTool>
) : ViewModel() {

    private val _servers = MutableStateFlow<List<McpServerConfig>>(emptyList())
    val servers: StateFlow<List<McpServerConfig>> = _servers.asStateFlow()

    private val _builtInTools = MutableStateFlow<List<Tool>>(builtInTools.map { it.definition }.sortedBy { it.name })
    val builtInTools: StateFlow<List<Tool>> = _builtInTools.asStateFlow()
    val connectionState = mcpManager.connectionState

    init {
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            _servers.update { settingRepository.fetchMcpServers() }
        }
    }

    fun connectAll() {
        Log.d(TAG, "connectAll called")
        viewModelScope.launch {
            try {
                Log.d(TAG, "connectAll launching")
                mcpManager.connectAll()
                Log.d(TAG, "connectAll completed")
            } catch (e: CancellationException) {
                Log.w(TAG, "connectAll cancelled", e)
            }
        }
    }

    fun reconnectServer(serverId: Int) {
        Log.d(TAG, "reconnectServer called serverId=$serverId")
        viewModelScope.launch {
            try {
                val server = _servers.value.find { it.id == serverId } ?: run {
                    Log.w(TAG, "reconnectServer server not found serverId=$serverId")
                    return@launch
                }
                Log.d(TAG, "reconnectServer launching serverId=$serverId name=${server.name}")
                mcpManager.connect(server)
                Log.d(TAG, "reconnectServer completed serverId=$serverId")
            } catch (e: CancellationException) {
                Log.w(TAG, "reconnectServer cancelled serverId=$serverId", e)
            }
        }
    }
}
