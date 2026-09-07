package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.repository.mcpOAuthPendingSecretRef
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Singleton
class McpOAuthCoordinator @Inject constructor(
    private val oauthClient: McpOAuthClient,
    private val connectionRepository: ToolConnectionRepository,
    private val secretVault: SecretVault,
    private val mcpClientManager: McpClientManager
) {
    private val credentialMutex = Mutex()

    suspend fun begin(connectionUid: String, redirectUri: String): String {
        val connection = requireOAuthConnection(connectionUid)
        val discovery = oauthClient.discover(
            resourceUrl = connection.endpointUrl ?: throw McpOAuthException("MCP endpoint is required."),
            allowCleartext = connection.allowCleartext
        )
        val start = oauthClient.beginAuthorization(discovery, redirectUri, connection.oauthClientId)
        if (start.pending.clientId != connection.oauthClientId) {
            connectionRepository.upsertConnection(connection.copy(oauthClientId = start.pending.clientId))
        }
        storePending(start.pending.copy(connectionUid = connectionUid))
        return start.authorizationUri
    }

    suspend fun complete(connectionUid: String, callbackUri: String) {
        val connection = requireOAuthConnection(connectionUid)
        val pendingRef = mcpOAuthPendingSecretRef(connectionUid)
        val pending = readSecret<McpOAuthPending>(pendingRef)
            ?: throw McpOAuthException("OAuth authorization is no longer pending.")
        if (pending.connectionUid != connectionUid) throw McpOAuthException("OAuth pending connection did not match.")
        val credential = oauthClient.completeAuthorization(pending, callbackUri)
        connectionRepository.upsertConnection(
            connection.copy(oauthClientId = credential.clientId),
            credential = NetworkClient.json.encodeToString(credential).encodeToByteArray()
        )
        secretVault.delete(pendingRef)
        mcpClientManager.close(connectionUid)
    }

    suspend fun authorizationHeader(
        connection: ToolConnection,
        forceRefresh: Boolean = false,
        rejectedAuthorizationHeader: String? = null
    ): String {
        credentialMutex.lock()
        try {
            require(connection.authType == ToolConnectionAuthType.OAUTH) { "OAuth connection required." }
            val secretRef = connection.secretRef ?: throw McpOAuthException("MCP OAuth connection is not authorized.")
            var credential = readSecret<McpOAuthCredential>(secretRef)
                ?: throw McpOAuthException("MCP OAuth credential is missing.")
            val currentHeader = "${credential.tokenType} ${credential.accessToken}"
            val isExpired = credential.expiresAtEpochSeconds?.let { it <= nowEpochSeconds() + REFRESH_SKEW_SECONDS } == true
            val shouldRefreshRejected = forceRefresh && (rejectedAuthorizationHeader == null || rejectedAuthorizationHeader == currentHeader)
            if (isExpired || shouldRefreshRejected) {
                credential = oauthClient.refresh(credential)
                connectionRepository.upsertConnection(
                    connection.copy(oauthClientId = credential.clientId),
                    credential = NetworkClient.json.encodeToString(credential).encodeToByteArray()
                )
                mcpClientManager.close(connection.connectionUid)
            }
            return "${credential.tokenType} ${credential.accessToken}"
        } finally {
            credentialMutex.unlock()
        }
    }

    private suspend fun requireOAuthConnection(connectionUid: String): ToolConnection {
        val connection = connectionRepository.getConnection(connectionUid)
            ?: throw McpOAuthException("MCP connection was not found.")
        if (connection.type != ToolConnectionType.MCP || connection.authType != ToolConnectionAuthType.OAUTH) {
            throw McpOAuthException("Connection is not configured for MCP OAuth.")
        }
        return connection
    }

    private suspend fun storePending(pending: McpOAuthPending) {
        val bytes = NetworkClient.json.encodeToString(pending).encodeToByteArray()
        try {
            secretVault.put(mcpOAuthPendingSecretRef(requireNotNull(pending.connectionUid)), bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private suspend inline fun <reified T> readSecret(secretRef: String): T? {
        val bytes = secretVault.read(secretRef) ?: return null
        return try {
            NetworkClient.json.decodeFromString<T>(bytes.decodeToString())
        } catch (error: Exception) {
            throw McpOAuthException("MCP OAuth credential is invalid.", error)
        } finally {
            bytes.fill(0)
        }
    }

    private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

    private companion object {
        const val REFRESH_SKEW_SECONDS = 60
    }
}

fun mcpOAuthRedirectUri(connectionUid: String): String {
    require(MCP_CONNECTION_UID_PATTERN.matches(connectionUid)) { "MCP connection ID is invalid." }
    return "$MCP_OAUTH_SCHEME://$MCP_OAUTH_HOST/mcp/$connectionUid"
}

fun mcpOAuthConnectionUid(callbackUri: String): String? = runCatching { URI(callbackUri) }
    .getOrNull()
    ?.takeIf { it.scheme == MCP_OAUTH_SCHEME && it.host == MCP_OAUTH_HOST && it.path?.startsWith("/mcp/") == true }
    ?.path
    ?.substringAfterLast('/')
    ?.takeIf(MCP_CONNECTION_UID_PATTERN::matches)

fun isMcpOAuthCallbackUri(callbackUri: String?): Boolean = callbackUri?.let(::mcpOAuthConnectionUid) != null

const val MCP_OAUTH_SCHEME = "dev.chungjungsoo.gptmobile"
const val MCP_OAUTH_HOST = "oauth"

private val MCP_CONNECTION_UID_PATTERN = Regex("[A-Za-z0-9_-]{1,96}")
