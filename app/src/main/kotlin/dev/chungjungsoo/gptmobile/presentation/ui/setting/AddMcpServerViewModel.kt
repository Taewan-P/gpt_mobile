package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.McpServerConfig
import dev.chungjungsoo.gptmobile.data.database.entity.McpTransportType
import dev.chungjungsoo.gptmobile.data.mcp.McpManager
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@HiltViewModel
class AddMcpServerViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val mcpManager: McpManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun updateType(type: McpTransportType) {
        _uiState.update {
            it.copy(
                type = type,
                connectionMessage = null,
                isConnectionError = false
            )
        }
    }

    fun updateUrl(url: String) {
        _uiState.update { it.copy(url = url, connectionMessage = null, isConnectionError = false) }
    }

    fun updateCommand(command: String) {
        _uiState.update { it.copy(command = command, connectionMessage = null, isConnectionError = false) }
    }

    fun updateArgs(args: List<String>) {
        _uiState.update { it.copy(args = args, connectionMessage = null, isConnectionError = false) }
    }

    fun addArg(arg: String) {
        _uiState.update { it.copy(args = it.args + arg, connectionMessage = null, isConnectionError = false) }
    }

    fun removeArg(index: Int) {
        _uiState.update { state ->
            state.copy(
                args = state.args.filterIndexed { i, _ -> i != index },
                connectionMessage = null,
                isConnectionError = false
            )
        }
    }

    fun updateArg(index: Int, newValue: String) {
        _uiState.update { state ->
            state.copy(
                args = state.args.mapIndexed { i, arg -> if (i == index) newValue else arg },
                connectionMessage = null,
                isConnectionError = false
            )
        }
    }

    fun updateInstallJson(installJson: String) {
        _uiState.update { it.copy(installJson = installJson) }
    }

    fun addHeader(key: String, value: String) {
        if (key.isBlank()) return
        _uiState.update { it.copy(headers = it.headers + (key to value)) }
    }

    fun removeHeader(key: String) {
        _uiState.update { it.copy(headers = it.headers - key) }
    }

    fun updateHeader(oldKey: String, newKey: String, newValue: String) {
        _uiState.update {
            val updated = it.headers.toMutableMap()
            updated.remove(oldKey)
            if (newKey.isNotBlank()) {
                updated[newKey] = newValue
            }
            it.copy(headers = updated)
        }
    }

    fun importInstallJson() {
        val payload = _uiState.value.installJson.trim()
        if (payload.isBlank()) {
            _uiState.update {
                it.copy(
                    connectionMessage = "Paste install JSON first.",
                    isConnectionError = true
                )
            }
            return
        }

        runCatching {
            parseInstallJson(payload)
        }.onSuccess { imported ->
            _uiState.update {
                it.copy(
                    name = imported.name.ifBlank { it.name },
                    type = imported.type,
                    url = imported.url,
                    headers = imported.headers,
                    connectionMessage = "Imported ${imported.name} config (${imported.headers.size} headers).",
                    isConnectionError = false,
                    importSucceeded = true
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    connectionMessage = throwable.message ?: "Invalid install JSON",
                    isConnectionError = true,
                    importSucceeded = false
                )
            }
        }
    }

    fun clearImportSucceeded() {
        _uiState.update { it.copy(importSucceeded = false) }
    }

    fun testConnection() {
        val state = _uiState.value
        if (!state.canTest) {
            return
        }

        val testConfig = buildServerConfig(id = 0, enabled = true)
        _uiState.update { it.copy(isTesting = true, connectionMessage = null, isConnectionError = false) }

        viewModelScope.launch {
            val before = mcpManager.availableTools.value.size
            val result = runCatching {
                withTimeout(TEST_CONNECTION_TIMEOUT_MS) {
                    mcpManager.connect(testConfig).getOrThrow()
                }
            }
            val after = mcpManager.availableTools.value.size
            _uiState.update {
                if (result.isSuccess) {
                    val discovered = (after - before).coerceAtLeast(0)
                    it.copy(
                        isTesting = false,
                        connectionMessage = if (discovered > 0) {
                            "Connection successful. Found $discovered new tools."
                        } else {
                            "Connection successful."
                        },
                        isConnectionError = false
                    )
                } else {
                    val error = result.exceptionOrNull()
                    it.copy(
                        isTesting = false,
                        connectionMessage = when {
                            error is TimeoutCancellationException -> {
                                if (state.url.contains("context7.com", ignoreCase = true)) {
                                    "Connection timed out. For Context7, use Streamable HTTP and include auth header if required."
                                } else {
                                    "Connection timed out. Verify server URL/transport and try again."
                                }
                            }
                            else -> error?.message ?: "Connection failed"
                        },
                        isConnectionError = true
                    )
                }
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.isValid || state.isSaving) {
            return
        }

        _uiState.update { it.copy(isSaving = true, connectionMessage = null, isConnectionError = false) }

        viewModelScope.launch {
            runCatching {
                val insertedId = settingRepository.addMcpServer(buildServerConfig(id = 0, enabled = true))
                settingRepository.getMcpServerById(insertedId.toInt())
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false) }
                onSaved()

                if (it != null && it.enabled) {
                    viewModelScope.launch {
                        runCatching {
                            withTimeout(CONNECT_SAVED_SERVER_TIMEOUT_MS) {
                                mcpManager.connect(it).getOrThrow()
                            }
                        }
                    }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        connectionMessage = throwable.message ?: "Failed to save server",
                        isConnectionError = true
                    )
                }
            }
        }
    }

    private fun buildServerConfig(id: Int, enabled: Boolean): McpServerConfig = McpServerConfig(
        id = id,
        name = _uiState.value.name.trim(),
        type = _uiState.value.type,
        url = _uiState.value.url.trim().takeIf { it.isNotBlank() },
        command = _uiState.value.command.trim().takeIf { it.isNotBlank() },
        args = _uiState.value.args,
        headers = _uiState.value.headers,
        enabled = enabled
    )

    private fun parseInstallJson(rawJson: String): ImportedServer {
        val root = Json.parseToJsonElement(rawJson).jsonObject
        val mcpServers = root["mcpServers"]?.jsonObject
            ?: throw IllegalArgumentException("JSON must contain mcpServers")

        val firstServer = mcpServers.entries.firstOrNull()
            ?: throw IllegalArgumentException("mcpServers is empty")

        val serverName = firstServer.key
        val serverConfig = firstServer.value.jsonObject
        val url = serverConfig["url"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (url.isBlank()) {
            throw IllegalArgumentException("Install JSON server is missing url")
        }

        val headers = parseHeaders(serverConfig["headers"]?.jsonObject)
        val importedType = inferTransportType(serverConfig, url)

        return ImportedServer(
            name = serverName,
            type = importedType,
            url = url,
            headers = headers
        )
    }

    private fun parseHeaders(headersJson: JsonObject?): Map<String, String> {
        if (headersJson == null) {
            return emptyMap()
        }

        return headersJson.entries.associate { (key, value) ->
            key to value.jsonPrimitive.content
        }
    }

    private fun inferTransportType(serverConfig: JsonObject, url: String): McpTransportType {
        val transportHint = serverConfig["transport"]?.jsonPrimitive?.content
            ?.trim()
            ?.lowercase()
            ?: serverConfig["type"]?.jsonPrimitive?.content
                ?.trim()
                ?.lowercase()
                .orEmpty()

        if (transportHint.contains("ws")) {
            return McpTransportType.WEBSOCKET
        }
        if (transportHint.contains("streamable") || transportHint.contains("http")) {
            return McpTransportType.STREAMABLE_HTTP
        }
        if (transportHint.contains("sse")) {
            return McpTransportType.SSE
        }

        return when {
            url.startsWith("ws://") || url.startsWith("wss://") -> McpTransportType.WEBSOCKET
            else -> McpTransportType.STREAMABLE_HTTP
        }
    }

    data class UiState(
        val name: String = "",
        val type: McpTransportType = McpTransportType.STREAMABLE_HTTP,
        val url: String = "",
        val command: String = "",
        val args: List<String> = emptyList(),
        val installJson: String = "",
        val headers: Map<String, String> = emptyMap(),
        val isSaving: Boolean = false,
        val isTesting: Boolean = false,
        val connectionMessage: String? = null,
        val isConnectionError: Boolean = false,
        val importSucceeded: Boolean = false
    ) {
        val isValid: Boolean
            get() = name.isNotBlank() && when (type) {
                McpTransportType.STDIO -> command.isNotBlank()
                McpTransportType.WEBSOCKET -> url.startsWith("wss://") || url.startsWith("ws://")
                McpTransportType.STREAMABLE_HTTP,
                McpTransportType.SSE -> url.startsWith("https://") || url.startsWith("http://")
            }

        val canTest: Boolean
            get() = when (type) {
                McpTransportType.STDIO -> command.isNotBlank()
                McpTransportType.WEBSOCKET -> url.startsWith("wss://") || url.startsWith("ws://")
                McpTransportType.STREAMABLE_HTTP,
                McpTransportType.SSE -> url.startsWith("https://") || url.startsWith("http://")
            }
    }

    private data class ImportedServer(
        val name: String,
        val type: McpTransportType,
        val url: String,
        val headers: Map<String, String>
    )

    companion object {
        private const val TEST_CONNECTION_TIMEOUT_MS = 25_000L
        private const val CONNECT_SAVED_SERVER_TIMEOUT_MS = 8_000L
    }
}
