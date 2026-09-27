package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.agent.tool.AgentToolResolver
import dev.chungjungsoo.gptmobile.data.agent.tool.McpClientManager
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthCoordinator
import dev.chungjungsoo.gptmobile.data.agent.tool.mcpOAuthConnectionUid
import dev.chungjungsoo.gptmobile.data.agent.tool.mcpOAuthRedirectUri
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ToolConnectionHealthStatus {
    CHECKING,
    ONLINE,
    LIMITED,
    OFFLINE
}

data class ToolConnectionHealth(
    val status: ToolConnectionHealthStatus,
    val toolCount: Int? = null,
    val message: String,
    val checkedAt: Long? = null
)

@HiltViewModel
class ToolConnectionsViewModel @Inject constructor(
    toolConnectionDao: ToolConnectionDao,
    secretVault: SecretVault,
    private val oauthCoordinator: McpOAuthCoordinator,
    private val mcpClientManager: McpClientManager,
    private val agentToolResolver: AgentToolResolver? = null,
    private val toolTrust: dev.chungjungsoo.gptmobile.data.permissions.ToolTrustStore? = null
) : ViewModel() {
    fun revokeToolGrants(connectionUid: String) {
        toolTrust?.revoke(connectionUid)
    }

    private val toolConnectionRepository = ToolConnectionRepository(toolConnectionDao, secretVault)

    private val _uiState = MutableStateFlow(ToolConnectionsUiState())
    val uiState: StateFlow<ToolConnectionsUiState> = _uiState.asStateFlow()
    private val _oauthLaunches = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val oauthLaunches: SharedFlow<String> = _oauthLaunches.asSharedFlow()
    private var oauthStartJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { toolConnectionRepository.listConnections() }
                .onSuccess { connections ->
                    _uiState.update { state ->
                        state.copy(
                            connections = connections,
                            connectionHealth = state.connectionHealth.filterKeys { uid ->
                                connections.any { it.connectionUid == uid }
                            },
                            errorMessage = null
                        )
                    }
                    probeConnections(connections)
                }
                .onFailure(::showError)
        }
    }

    fun savePolicy(connection: ToolConnection, policy: String, reads: String) {
        viewModelScope.launch {
            try {
                toolConnectionRepository.upsertConnection(connection.copy(toolPolicy = policy, approvedReadTools = reads.take(16000)))
                refresh()
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    fun saveConnection(
        existing: ToolConnection?,
        provider: ToolConnectionProvider,
        name: String,
        alias: String,
        endpointUrl: String,
        authType: String,
        credential: String,
        oauthClientId: String,
        allowCleartext: Boolean,
        clearCredential: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        val normalizedAlias = normalizeAlias(alias)
        if (!isValidAlias(normalizedAlias)) {
            _uiState.update { it.copy(errorMessage = "Alias must match [a-z][a-z0-9_]{0,31}.") }
            return
        }
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name is required.") }
            return
        }
        val actualEndpoint = if (provider.type == ToolConnectionType.MCP) endpointUrl.trim() else provider.endpointUrl
        val actualAuthType = if (provider.type == ToolConnectionType.MCP) authType else provider.authType
        if (provider.type == ToolConnectionType.MCP && !isValidMcpEndpoint(actualEndpoint, allowCleartext)) {
            _uiState.update { it.copy(errorMessage = "MCP endpoint must be HTTP(S); cleartext HTTP requires explicit approval.") }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis() / 1000
            val clientId = oauthClientId.trim().takeIf { actualAuthType == ToolConnectionAuthType.OAUTH && it.isNotEmpty() }
            val connection = ToolConnection(
                connectionUid = existing?.connectionUid ?: UUID.randomUUID().toString(),
                name = name.trim(),
                alias = normalizedAlias,
                type = provider.type,
                endpointUrl = actualEndpoint,
                authType = actualAuthType,
                secretRef = existing?.secretRef,
                oauthClientId = clientId,
                allowCleartext = provider.type == ToolConnectionType.MCP && actualEndpoint.startsWith("http://", ignoreCase = true) && allowCleartext,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            val metadataChanged = existing?.let {
                it.type != connection.type ||
                    it.endpointUrl != connection.endpointUrl ||
                    it.authType != connection.authType ||
                    it.oauthClientId != connection.oauthClientId
            } == true
            val shouldClear = clearCredential || actualAuthType == ToolConnectionAuthType.NONE || metadataChanged
            val credentialBytes = if (actualAuthType == ToolConnectionAuthType.BEARER || actualAuthType == ToolConnectionAuthType.API_KEY) {
                credentialInput(credential, clearCredential)
            } else {
                null
            }
            runCatching {
                val shouldClearCredential = shouldClearCredential(
                    existingType = existing?.type,
                    providerType = provider.type,
                    credential = credential,
                    clearCredential = clearCredential
                )
                toolConnectionRepository.upsertConnection(
                    connection = connection,
                    credential = credentialBytes,
                    clearCredential = (shouldClear || shouldClearCredential) && credentialBytes == null
                )
            }.onSuccess {
                runCatching { mcpClientManager.close(connection.connectionUid) }
                refresh()
                probeConnection(connection)
                onSuccess()
            }.onFailure(::showError)
        }
    }

    fun deleteConnection(connectionUid: String) {
        viewModelScope.launch {
            runCatching {
                mcpClientManager.close(connectionUid)
                toolConnectionRepository.deleteConnection(connectionUid)
            }
                .onSuccess { refresh() }
                .onFailure(::showError)
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun probeConnections(connections: List<ToolConnection> = _uiState.value.connections, force: Boolean = false) {
        val resolver = agentToolResolver ?: return
        connections
            .filter { it.type == ToolConnectionType.MCP }
            .forEach { connection ->
                val previous = _uiState.value.connectionHealth[connection.connectionUid]
                val recentlyChecked = previous?.checkedAt?.let { System.currentTimeMillis() - it < 30_000 } == true
                if (previous?.status == ToolConnectionHealthStatus.CHECKING || (!force && recentlyChecked)) {
                    return@forEach
                }
                _uiState.update { state ->
                    state.copy(
                        connectionHealth = state.connectionHealth + mapOf(
                            connection.connectionUid to ToolConnectionHealth(
                                status = ToolConnectionHealthStatus.CHECKING,
                                message = "Checking Streamable HTTP server…"
                            )
                        )
                    )
                }
                viewModelScope.launch {
                    val checkedAt = System.currentTimeMillis()
                    val health = runCatching { resolver.discoverMcpTools(connection) }
                        .fold(
                            onSuccess = { tools ->
                                if (tools.isEmpty()) {
                                    ToolConnectionHealth(
                                        status = ToolConnectionHealthStatus.LIMITED,
                                        toolCount = 0,
                                        message = "Connected, but the server advertised no tools.",
                                        checkedAt = checkedAt
                                    )
                                } else {
                                    ToolConnectionHealth(
                                        status = ToolConnectionHealthStatus.ONLINE,
                                        toolCount = tools.size,
                                        message = "Online",
                                        checkedAt = checkedAt
                                    )
                                }
                            },
                            onFailure = { error ->
                                if (error is kotlinx.coroutines.CancellationException) throw error
                                val message = error.message ?: "Unable to reach the MCP server."
                                val limited = message.contains("401") ||
                                    message.contains("403") ||
                                    message.contains("auth", ignoreCase = true) ||
                                    message.contains("permission", ignoreCase = true)
                                ToolConnectionHealth(
                                    status = if (limited) {
                                        ToolConnectionHealthStatus.LIMITED
                                    } else {
                                        ToolConnectionHealthStatus.OFFLINE
                                    },
                                    message = message,
                                    checkedAt = checkedAt
                                )
                            }
                        )
                    _uiState.update { state ->
                        state.copy(
                            connectionHealth = state.connectionHealth + mapOf(
                                connection.connectionUid to health
                            )
                        )
                    }
                }
            }
    }

    fun probeConnection(connection: ToolConnection) = probeConnections(listOf(connection), force = true)

    fun startOAuth(connectionUid: String) {
        if (oauthStartJob?.isActive == true) return
        oauthStartJob = viewModelScope.launch {
            _uiState.update { it.copy(isOAuthBusy = true, errorMessage = null) }
            runCatching { oauthCoordinator.begin(connectionUid, mcpOAuthRedirectUri(connectionUid)) }
                .onSuccess { authorizationUri -> _oauthLaunches.emit(authorizationUri) }
                .onFailure(::showError)
            _uiState.update { it.copy(isOAuthBusy = false) }
        }
    }

    fun completeOAuthCallback(callbackUri: String?) {
        if (callbackUri == null) {
            _uiState.update { it.copy(errorMessage = "OAuth authorization was canceled.") }
            return
        }
        val connectionUid = mcpOAuthConnectionUid(callbackUri)
        if (connectionUid == null) {
            _uiState.update { it.copy(errorMessage = "OAuth callback URI is invalid.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isOAuthBusy = true, errorMessage = null) }
            runCatching { oauthCoordinator.complete(connectionUid, callbackUri) }
                .onSuccess { refresh() }
                .onFailure(::showError)
            _uiState.update { it.copy(isOAuthBusy = false) }
        }
    }

    fun failOAuthLaunch(message: String = "No browser is available for OAuth authorization.") {
        _uiState.update { it.copy(isOAuthBusy = false, errorMessage = message) }
    }

    private fun showError(error: Throwable) {
        _uiState.update { it.copy(errorMessage = error.message ?: "Tool connection update failed.") }
    }

    data class ToolConnectionsUiState(
        val connections: List<ToolConnection> = emptyList(),
        val connectionHealth: Map<String, ToolConnectionHealth> = emptyMap(),
        val isOAuthBusy: Boolean = false,
        val errorMessage: String? = null
    )

    companion object {
        val providers = listOf(
            ToolConnectionProvider("Firecrawl", ToolConnectionType.FIRECRAWL, "https://api.firecrawl.dev/v2/search", ToolConnectionAuthType.BEARER),
            ToolConnectionProvider("Perplexity", ToolConnectionType.PERPLEXITY, "https://api.perplexity.ai/search", ToolConnectionAuthType.BEARER),
            ToolConnectionProvider("Exa", ToolConnectionType.EXA, "https://api.exa.ai/search", ToolConnectionAuthType.API_KEY),
            ToolConnectionProvider("Brave Search", ToolConnectionType.BRAVE, "https://api.search.brave.com/res/v1/web/search", ToolConnectionAuthType.API_KEY),
            ToolConnectionProvider("MCP server", ToolConnectionType.MCP, "", ToolConnectionAuthType.NONE)
        )

        private val aliasRegex = Regex("[a-z][a-z0-9_]{0,31}")

        fun normalizeAlias(alias: String): String = alias.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_")

        fun isValidAlias(alias: String): Boolean = aliasRegex.matches(alias)

        fun credentialInput(apiKey: String, clearCredential: Boolean): ByteArray? = apiKey.trim().takeIf { it.isNotEmpty() && !clearCredential }?.encodeToByteArray()

        fun shouldClearCredential(
            existingType: String?,
            providerType: String,
            credential: String,
            clearCredential: Boolean
        ): Boolean = clearCredential || (existingType != null && existingType != providerType && credential.isBlank())

        fun isValidMcpEndpoint(endpointUrl: String, allowCleartext: Boolean): Boolean = runCatching {
            val uri = java.net.URI(endpointUrl)
            uri.host != null &&
                uri.userInfo == null &&
                uri.fragment == null &&
                (uri.scheme.equals("https", ignoreCase = true) || (uri.scheme.equals("http", ignoreCase = true) && allowCleartext))
        }.getOrDefault(false)
    }
}

data class ToolConnectionProvider(
    val label: String,
    val type: String,
    val endpointUrl: String,
    val authType: String
)
