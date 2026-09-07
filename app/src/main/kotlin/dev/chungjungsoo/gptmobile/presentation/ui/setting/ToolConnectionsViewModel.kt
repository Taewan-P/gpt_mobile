package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ToolConnectionsViewModel @Inject constructor(
    toolConnectionDao: ToolConnectionDao,
    secretVault: SecretVault,
    private val oauthCoordinator: McpOAuthCoordinator,
    private val mcpClientManager: McpClientManager
) : ViewModel() {
    private val toolConnectionRepository = ToolConnectionRepository(toolConnectionDao, secretVault)

    private val _uiState = MutableStateFlow(ToolConnectionsUiState())
    val uiState: StateFlow<ToolConnectionsUiState> = _uiState.asStateFlow()
    private val _oauthLaunches = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val oauthLaunches: SharedFlow<String> = _oauthLaunches.asSharedFlow()
    private var oauthStartJob: Job? = null
    private var pendingOAuthConnectionUid: String? = null
    private var refreshGeneration = 0

    init {
        refresh()
    }

    fun refresh() {
        val generation = ++refreshGeneration
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val connections = toolConnectionRepository.listConnections()
                if (generation == refreshGeneration) {
                    _uiState.update {
                        it.copy(
                            connections = connections,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                if (generation == refreshGeneration) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Could not load tool connections."
                        )
                    }
                }
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
        if (_uiState.value.isSaving) return
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
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
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
            try {
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
                mcpClientManager.close(connection.connectionUid)
                refresh()
                onSuccess()
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                showError(throwable)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteConnection(connectionUid: String) {
        if (_uiState.value.busyConnectionUid != null) return
        setRowBusy(connectionUid)
        viewModelScope.launch {
            var didDelete = false
            try {
                mcpClientManager.close(connectionUid)
                toolConnectionRepository.deleteConnection(connectionUid)
                didDelete = true
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                showRowError(connectionUid, throwable)
            } finally {
                clearRowBusy(connectionUid)
            }
            if (didDelete) refresh()
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun clearRowError(connectionUid: String) {
        _uiState.update { state ->
            if (state.rowErrorConnectionUid == connectionUid) {
                state.copy(rowErrorConnectionUid = null, rowErrorMessage = null)
            } else {
                state
            }
        }
    }

    fun startOAuth(connectionUid: String) {
        if (oauthStartJob?.isActive == true ||
            pendingOAuthConnectionUid != null ||
            _uiState.value.busyConnectionUid != null
        ) {
            return
        }
        pendingOAuthConnectionUid = connectionUid
        setRowBusy(connectionUid)
        oauthStartJob = viewModelScope.launch {
            try {
                val authorizationUri = oauthCoordinator.begin(connectionUid, mcpOAuthRedirectUri(connectionUid))
                _oauthLaunches.emit(authorizationUri)
            } catch (exception: CancellationException) {
                if (pendingOAuthConnectionUid == connectionUid) pendingOAuthConnectionUid = null
                throw exception
            } catch (throwable: Throwable) {
                showRowError(connectionUid, throwable)
                if (pendingOAuthConnectionUid == connectionUid) pendingOAuthConnectionUid = null
            } finally {
                clearRowBusy(connectionUid)
            }
        }
    }

    fun completeOAuthCallback(callbackUri: String?) {
        val pendingConnectionUid = pendingOAuthConnectionUid
        if (callbackUri == null) {
            showOAuthCallbackError(pendingConnectionUid, "OAuth authorization was canceled.")
            return
        }
        val connectionUid = mcpOAuthConnectionUid(callbackUri)
        if (connectionUid == null) {
            showOAuthCallbackError(pendingConnectionUid, "OAuth callback URI is invalid.")
            return
        }
        if (pendingConnectionUid != null && pendingConnectionUid != connectionUid) {
            showOAuthCallbackError(pendingConnectionUid, "OAuth callback did not match this connection.")
            return
        }
        if (_uiState.value.busyConnectionUid != null) return
        pendingOAuthConnectionUid = null
        setRowBusy(connectionUid)
        viewModelScope.launch {
            var didComplete = false
            try {
                oauthCoordinator.complete(connectionUid, callbackUri)
                didComplete = true
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                showRowError(connectionUid, throwable)
            } finally {
                clearRowBusy(connectionUid)
            }
            if (didComplete) refresh()
        }
    }

    fun failOAuthLaunch(message: String = "No browser is available for OAuth authorization.") {
        showOAuthCallbackError(pendingOAuthConnectionUid, message)
    }

    fun cancelPendingOAuth() {
        val pendingConnectionUid = pendingOAuthConnectionUid ?: return
        showOAuthCallbackError(pendingConnectionUid, "OAuth authorization was canceled.")
    }

    private fun showError(error: Throwable) {
        _uiState.update { it.copy(errorMessage = error.message ?: "Tool connection update failed.") }
    }

    private fun showOAuthCallbackError(connectionUid: String?, message: String) {
        if (connectionUid == null) {
            _uiState.update { it.copy(errorMessage = message) }
        } else {
            showRowError(connectionUid, message)
            if (pendingOAuthConnectionUid == connectionUid) pendingOAuthConnectionUid = null
            clearRowBusy(connectionUid)
        }
    }

    private fun setRowBusy(connectionUid: String) {
        _uiState.update {
            it.copy(
                busyConnectionUid = connectionUid,
                rowErrorConnectionUid = if (it.rowErrorConnectionUid == connectionUid) null else it.rowErrorConnectionUid,
                rowErrorMessage = if (it.rowErrorConnectionUid == connectionUid) null else it.rowErrorMessage
            )
        }
    }

    private fun clearRowBusy(connectionUid: String) {
        _uiState.update {
            if (it.busyConnectionUid == connectionUid) it.copy(busyConnectionUid = null) else it
        }
    }

    private fun showRowError(connectionUid: String, throwable: Throwable) {
        showRowError(connectionUid, throwable.message ?: "Tool connection update failed.")
    }

    private fun showRowError(connectionUid: String, message: String) {
        _uiState.update {
            it.copy(
                rowErrorConnectionUid = connectionUid,
                rowErrorMessage = message
            )
        }
    }

    data class ToolConnectionsUiState(
        val connections: List<ToolConnection> = emptyList(),
        val isLoading: Boolean = true,
        val busyConnectionUid: String? = null,
        val rowErrorConnectionUid: String? = null,
        val rowErrorMessage: String? = null,
        val isSaving: Boolean = false,
        val errorMessage: String? = null
    )

    companion object {
        val providers = listOf(
            ToolConnectionProvider("Firecrawl", ToolConnectionType.FIRECRAWL, "https://api.firecrawl.dev/v2/search", ToolConnectionAuthType.BEARER),
            ToolConnectionProvider("Perplexity", ToolConnectionType.PERPLEXITY, "https://api.perplexity.ai/search", ToolConnectionAuthType.BEARER),
            ToolConnectionProvider("Exa", ToolConnectionType.EXA, "https://api.exa.ai/search", ToolConnectionAuthType.API_KEY),
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
