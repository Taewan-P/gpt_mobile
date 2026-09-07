package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.dao.AgentToolBindingWithConnection
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

data class ResolvedAgentTool(
    val tool: AgentTool,
    val connectionUid: String?,
    val connectionName: String?,
    val realToolName: String,
    val modelToolName: String
)

class AgentToolResolver @Inject constructor(
    private val toolConnectionRepository: ToolConnectionRepository,
    private val secretVault: SecretVault,
    private val networkClient: NetworkClient,
    private val mcpClientManager: McpClientManager,
    private val mcpOAuthCoordinator: McpOAuthCoordinator
) {
    suspend fun discoverMcpTools(connection: ToolConnection): List<Tool> {
        val config = mcpConfig(connection)
        return try {
            mcpClientManager.listTools(config)
        } catch (error: Exception) {
            if (connection.authType != ToolConnectionAuthType.OAUTH || !error.isUnauthorized()) throw error
            mcpClientManager.listTools(
                mcpConfig(
                    connection,
                    forceOAuthRefresh = true,
                    rejectedAuthorizationHeader = config.authorizationHeader
                )
            )
        }
    }

    suspend fun resolve(profileUid: String): List<ResolvedAgentTool> {
        val resolved = mutableListOf(
            CurrentDateTool().resolved(null, null, BuiltInAgentTool.CURRENT_DATE)
        )
        val bindings = toolConnectionRepository.listBindingsWithConnections(profileUid)
            .sortedWith(compareBy<AgentToolBindingWithConnection> { it.binding.toolName }.thenBy { it.binding.connectionUid ?: "" }.thenBy { it.binding.bindingUid })
        bindings
            .filterNot { it.connection?.type == ToolConnectionType.MCP }
            .forEach { binding ->
                resolveBinding(binding)?.let { resolved += it }
            }
        bindings.filter { it.connection?.type == ToolConnectionType.MCP }
            .groupBy { requireNotNull(it.connection).connectionUid }
            .toSortedMap()
            .values
            .forEach { mcpBindings ->
                try {
                    resolved += resolveMcpTools(requireNotNull(mcpBindings.first().connection), mcpBindings)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                }
            }
        return resolved.distinctBy { it.modelToolName }
            .sortedBy { it.modelToolName }
    }

    private suspend fun resolveBinding(binding: AgentToolBindingWithConnection): ResolvedAgentTool? = when (binding.binding.toolName) {
        WEB_SEARCH_TOOL -> resolveWebSearch(binding.connection)

        BuiltInAgentTool.READ_URL -> if (binding.binding.connectionUid == null) {
            ReadUrlTool().resolved(null, null, BuiltInAgentTool.READ_URL)
        } else {
            null
        }

        else -> null
    }

    private suspend fun resolveWebSearch(connection: ToolConnection?): ResolvedAgentTool? {
        val actualConnection = connection ?: return null
        val provider = SEARCH_PROVIDERS[actualConnection.type] ?: return null
        val endpointUrl = provider.defaultEndpointUrl
        val definition = WebSearchTool(
            config = WebSearchProviderConfig(provider.provider, "", endpointUrl),
            networkClient = networkClient
        ).definition
        val credential = actualConnection.secretRef?.let { secretRef ->
            secretVault.read(secretRef)
        }
        val tool = credential?.let { bytes ->
            try {
                val token = String(bytes, StandardCharsets.UTF_8)
                if (token.isBlank()) {
                    MissingCredentialTool(definition)
                } else {
                    WebSearchTool(
                        config = WebSearchProviderConfig(
                            provider = provider.provider,
                            bearerToken = token,
                            endpointUrl = endpointUrl
                        ),
                        networkClient = networkClient
                    )
                }
            } finally {
                bytes.fill(0)
            }
        } ?: MissingCredentialTool(definition)
        return tool.resolved(actualConnection.connectionUid, actualConnection.name, WEB_SEARCH_TOOL)
    }

    private suspend fun resolveMcpTools(
        connection: ToolConnection,
        bindings: List<AgentToolBindingWithConnection>
    ): List<ResolvedAgentTool> {
        val selectedNames = bindings.map { it.binding.toolName }.toSet()
        val remoteTools = discoverMcpTools(connection)
        return remoteTools
            .filter { it.name in selectedNames }
            .map { remoteTool ->
                val tool = McpAgentTool(
                    definition = mcpToolDefinition(connection.alias, remoteTool),
                    authType = connection.authType,
                    config = { forceRefresh, rejectedHeader -> mcpConfig(connection, forceRefresh, rejectedHeader) },
                    remoteToolName = remoteTool.name,
                    clientManager = mcpClientManager
                )
                ResolvedAgentTool(
                    tool = tool,
                    connectionUid = connection.connectionUid,
                    connectionName = connection.name,
                    realToolName = remoteTool.name,
                    modelToolName = tool.definition.name
                )
            }
    }

    private suspend fun mcpConfig(
        connection: ToolConnection,
        forceOAuthRefresh: Boolean = false,
        rejectedAuthorizationHeader: String? = null
    ): McpConnectionConfig {
        val authorization = when (connection.authType) {
            ToolConnectionAuthType.NONE -> null

            ToolConnectionAuthType.BEARER -> readBearerHeader(connection)

            ToolConnectionAuthType.OAUTH -> mcpOAuthCoordinator.authorizationHeader(
                connection,
                forceOAuthRefresh,
                rejectedAuthorizationHeader
            )

            else -> throw IllegalArgumentException("Unsupported MCP authentication type.")
        }
        return McpConnectionConfig(
            connectionUid = connection.connectionUid,
            endpointUrl = connection.endpointUrl ?: throw IllegalArgumentException("MCP endpoint is required."),
            allowCleartext = connection.allowCleartext,
            authorizationHeader = authorization
        )
    }

    private suspend fun readBearerHeader(connection: ToolConnection): String {
        val secretRef = connection.secretRef ?: throw IllegalArgumentException("MCP bearer credential is missing.")
        val bytes = secretVault.read(secretRef) ?: throw IllegalArgumentException("MCP bearer credential is missing.")
        return try {
            val token = bytes.decodeToString().trim()
            require(token.isNotEmpty()) { "MCP bearer credential is missing." }
            require('\r' !in token && '\n' !in token) { "MCP bearer credential is invalid." }
            "Bearer $token"
        } finally {
            bytes.fill(0)
        }
    }

    private fun AgentTool.resolved(
        connectionUid: String?,
        connectionName: String?,
        realToolName: String
    ) = ResolvedAgentTool(
        tool = this,
        connectionUid = connectionUid,
        connectionName = connectionName,
        realToolName = realToolName,
        modelToolName = definition.name
    )

    private companion object {
        const val WEB_SEARCH_TOOL = "web_search"
        val SEARCH_PROVIDERS = mapOf(
            ToolConnectionType.FIRECRAWL to SearchProvider(WebSearchProvider.FIRECRAWL, "https://api.firecrawl.dev/v2/search"),
            ToolConnectionType.PERPLEXITY to SearchProvider(WebSearchProvider.PERPLEXITY, "https://api.perplexity.ai/search"),
            ToolConnectionType.EXA to SearchProvider(WebSearchProvider.EXA, "https://api.exa.ai/search")
        )
    }
}

private class McpAgentTool(
    override val definition: AgentToolDefinition,
    private val authType: String,
    private val config: suspend (Boolean, String?) -> McpConnectionConfig,
    private val remoteToolName: String,
    private val clientManager: McpClientManager
) : AgentTool {
    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        val initialConfig = config(false, null)
        val result = try {
            clientManager.callTool(initialConfig, remoteToolName, arguments)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (authType != ToolConnectionAuthType.OAUTH || !error.isUnauthorized()) throw error
            clientManager.callTool(
                config(true, initialConfig.authorizationHeader),
                remoteToolName,
                arguments
            )
        }
        return mapMcpToolResult(callId, result)
    }
}

private fun Throwable.isUnauthorized(): Boolean = generateSequence(this) { it.cause }
    .any { error -> error is StreamableHttpError && error.code == 401 }

private data class SearchProvider(
    val provider: WebSearchProvider,
    val defaultEndpointUrl: String
)

private class MissingCredentialTool(
    override val definition: AgentToolDefinition
) : AgentTool {

    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult = AgentToolResult(
        callId = callId,
        content = ToolResultContent.Text("Tool web_search is unavailable: missing credential."),
        isError = true
    )
}
