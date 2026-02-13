package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.McpServerConfig
import dev.chungjungsoo.gptmobile.data.dto.tool.Tool
import dev.chungjungsoo.gptmobile.data.mcp.McpManager
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.tool.BuiltInTool
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _servers.update { settingRepository.fetchMcpServers() }
            mcpManager.connectAll()
        }
    }
}
