package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.dao.AgentToolBindingWithConnection
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.model.ChatMcpToolConfig
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.isPrivateDestination
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.memory.MemoryGraphRepository
import dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ResolvedAgentTool(
    val tool: AgentTool,
    val connectionUid: String?,
    val connectionName: String?,
    val realToolName: String,
    val modelToolName: String,
    val shareableReadOnly: Boolean = false
)

class AgentToolResolver @Inject constructor(
    private val toolConnectionRepository: ToolConnectionRepository,
    private val settingRepository: SettingRepository,
    private val secretVault: SecretVault,
    private val networkClient: NetworkClient,
    private val mcpClientManager: McpClientManager,
    private val mcpOAuthCoordinator: McpOAuthCoordinator,
    private val deviceLocationTool: DeviceLocationTool,
    private val factVault: FactVaultRepository? = null,
    private val memoryGraph: MemoryGraphRepository? = null,
    private val memoryDocuments: dev.chungjungsoo.gptmobile.data.knowledge.MemoryDocumentRepository? = null,
    private val freeModelToolConsentStore: dev.chungjungsoo.gptmobile.data.permissions.FreeModelToolConsentStore? = null,
    private val gitHubWorkspaceStore: dev.chungjungsoo.gptmobile.data.github.GitHubWorkspaceStore? = null
) {
    suspend fun discoverMcpTools(connection: ToolConnection, forceRefresh: Boolean = false): List<Tool> {
        val config = mcpConfig(connection)
        return try {
            mcpClientManager.listTools(config, forceRefresh)
        } catch (error: Exception) {
            if (connection.authType != ToolConnectionAuthType.OAUTH || !error.isUnauthorized()) throw error
            mcpClientManager.listTools(
                mcpConfig(
                    connection,
                    forceOAuthRefresh = true,
                    rejectedAuthorizationHeader = config.authorizationHeader
                ),
                forceRefresh = true
            )
        }
    }

    suspend fun resolve(
        profileUid: String,
        chatToolConfig: ChatMcpToolConfig? = null,
        userMessage: MessageV2? = null,
        delegate: (suspend (PlatformV2, String, Int) -> String)? = null,
        onConnectionError: (String) -> Unit = {}
    ): List<ResolvedAgentTool> {
        val platforms = settingRepository.fetchPlatformV2s()
        val platform = platforms.firstOrNull { it.uid == profileUid }

        // If master disableAllTools is toggled, return no tools immediately
        if (platform?.disableAllTools == true) {
            return emptyList()
        }

        val freeProfile = platform?.compatibleType == ClientType.FREE
        val disableRemote = platform?.disableRemoteTools == true
        val disableLocal = platform?.disableLocalTools == true
        val featureSettings = settingRepository.getFeatureSettings()
        val allowRemoteMcp = !disableRemote && featureSettings.remoteMcpConnections
        val allowDeviceLocation = !disableLocal && featureSettings.deviceLocationTool
        val connections = toolConnectionRepository.listConnections()
        val nativeGitHubConnections = connections.filter { it.type == ToolConnectionType.GITHUB }

        // Baseline zero-config tools available out of the box to all models
        val defaultWebSearch = WebSearchTool(
            config = WebSearchProviderConfig(
                provider = WebSearchProvider.AUTO,
                bearerToken = "",
                endpointUrl = "",
                allowLocalSearch = false
            ),
            networkClient = networkClient
        )

        val resolved = mutableListOf<ResolvedAgentTool>()

        if (!disableLocal) {
            // Keep explicitly enabled delegation available in small on-device context windows.
            if ((chatToolConfig?.effectiveDelegation(featureSettings.delegation) ?: featureSettings.delegation).enabled && delegate != null && platform != null) {
                val tool = ModelDelegationTool(
                    platform,
                    {
                        val defaults = settingRepository.getFeatureSettings().delegation
                        chatToolConfig?.effectiveDelegation(defaults) ?: defaults
                    },
                    { settingRepository.fetchPlatformV2s() },
                    delegate
                )
                resolved += tool.resolved(null, "Model delegation", tool.definition.name)
            }
            if (factVault != null && userMessage != null && platform != null) {
                val memoryAvailable = try {
                    factVault.load()
                    factVault.state.value.enabled
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    false
                }
                if (memoryAvailable) {
                    listOf(true, false).forEach { capture ->
                        val tool = LocalMemoryTool(factVault, userMessage, platform.isPrivateDestination(), capture)
                        resolved += tool.resolved(null, "Local memory", tool.definition.name)
                    }
                    LocalMemoryGraphTool.operations.forEach { operation ->
                        val tool = LocalMemoryGraphTool(factVault, memoryGraph, memoryDocuments, userMessage, platform.isPrivateDestination(), operation)
                        resolved += tool.resolved(null, "Local memory", tool.definition.name)
                    }
                }
            }
            resolved += CurrentDateTool().resolved(null, null, BuiltInAgentTool.CURRENT_DATE)
            resolved += CalculatorTool().resolved(null, null, BuiltInAgentTool.CALCULATE_EXPRESSION)
            resolved += ReadFileSliceTool().resolved(null, null, BuiltInAgentTool.READ_FILE_SLICE)
        }

        if (!disableRemote) {
            resolved += ReadUrlTool().resolved(null, null, BuiltInAgentTool.READ_URL)
            // Do not expose an anonymous GitHub tool beside an authenticated native
            // connection. The overlapping surfaces caused duplicate repo/API calls.
            if (nativeGitHubConnections.isEmpty()) {
                resolved += GitHubTool().resolved(null, null, BuiltInAgentTool.GITHUB)
            }
            nativeGitHubConnections.forEach { connection -> resolved += resolveGitHub(connection) }
            resolved += defaultWebSearch.resolved(null, null, WEB_SEARCH_TOOL)
        }

        val bindings = toolConnectionRepository.listBindingsWithConnections(profileUid)
            .sortedWith(compareBy<AgentToolBindingWithConnection> { it.binding.toolName }.thenBy { it.binding.connectionUid ?: "" }.thenBy { it.binding.bindingUid })
        bindings
            .filterNot { it.connection?.type == ToolConnectionType.MCP }
            .distinctBy { if (it.binding.toolName == WEB_SEARCH_TOOL) "${it.binding.toolName}:${it.binding.connectionUid}" else it.binding.toolName }
            .forEach { binding ->
                val isRemoteBinding = binding.binding.toolName in setOf(WEB_SEARCH_TOOL, BuiltInAgentTool.READ_URL, BuiltInAgentTool.GITHUB)
                val isLocalBinding = !isRemoteBinding
                if ((isRemoteBinding && !disableRemote) || (isLocalBinding && !disableLocal)) {
                    if (binding.binding.toolName == BuiltInAgentTool.DEVICE_LOCATION && !allowDeviceLocation) {
                        return@forEach
                    }
                    resolveBinding(binding)?.let { customResolvedTool ->
                        resolved.removeAll { it.modelToolName == customResolvedTool.modelToolName }
                        resolved += customResolvedTool
                    }
                }
            }

        if (allowRemoteMcp) {
            val mcpGroups = bindings
                .filter { it.connection?.type == ToolConnectionType.MCP }
                .filterNot { binding ->
                    // Prefer the native GitHub API surface when it is connected.
                    // Keeping GitHub Official MCP visible at the same time causes
                    // agents to repeat searches/PR reads through both providers.
                    nativeGitHubConnections.isNotEmpty() && binding.connection?.isGitHubMcpEndpoint() == true
                }
                .groupBy { requireNotNull(it.connection).connectionUid }
                .toSortedMap()
                .values
                .toList()
            val mcpResults = coroutineScope {
                mcpGroups.map { mcpBindings ->
                    async {
                        val connection = requireNotNull(mcpBindings.first().connection)
                        try {
                            resolveMcpTools(connection, mcpBindings) to null
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            emptyList<ResolvedAgentTool>() to "${connection.name}: tools unavailable. Check authentication and connection diagnostics."
                        }
                    }
                }.awaitAll()
            }
            mcpResults.forEach { (tools, error) ->
                resolved += tools
                error?.let(onConnectionError)
            }
        }

        return resolved.distinctBy { it.modelToolName }
            .filter { tool ->
                // Discovery stays visible, but runtime consent cannot be bypassed by
                // Select all, imported chat options or a restored pending prompt.
                !freeProfile ||
                    chatToolConfig == null ||
                    tool.connectionUid == null ||
                    bindings.none { it.connection?.type == ToolConnectionType.MCP && it.connection?.connectionUid == tool.connectionUid } ||
                    freeModelToolConsentStore?.isGranted(profileUid, "${tool.connectionUid}:${tool.realToolName}") == true
            }
            .filter { tool ->
                if (chatToolConfig == null) {
                    true
                } else {
                    val candidateIds = listOfNotNull(
                        tool.connectionUid?.let { "$it:${tool.realToolName}" },
                        tool.modelToolName,
                        tool.realToolName,
                        tool.connectionUid,
                        "web_search".takeIf { tool.isWebSearchEngine() }
                    )
                    chatToolConfig.isToolEnabled(candidateIds)
                }
            }
            .sortedBy { it.modelToolName }
    }

    private suspend fun resolveBinding(binding: AgentToolBindingWithConnection): ResolvedAgentTool? = when (binding.binding.toolName) {
        WEB_SEARCH_TOOL -> resolveWebSearch(binding.connection)

        BuiltInAgentTool.READ_URL -> if (binding.binding.connectionUid == null) {
            ReadUrlTool().resolved(null, null, BuiltInAgentTool.READ_URL)
        } else {
            null
        }

        BuiltInAgentTool.READ_FILE_SLICE -> if (binding.binding.connectionUid == null) {
            ReadFileSliceTool().resolved(null, null, BuiltInAgentTool.READ_FILE_SLICE)
        } else {
            null
        }

        BuiltInAgentTool.DEVICE_LOCATION -> if (binding.binding.connectionUid == null) {
            deviceLocationTool.resolved(null, null, BuiltInAgentTool.DEVICE_LOCATION)
        } else {
            null
        }

        BuiltInAgentTool.CALCULATE_EXPRESSION -> if (binding.binding.connectionUid == null) {
            CalculatorTool().resolved(null, null, BuiltInAgentTool.CALCULATE_EXPRESSION)
        } else {
            null
        }

        BuiltInAgentTool.GITHUB -> resolveGitHub(binding.connection)

        else -> null
    }

    private suspend fun resolveGitHub(connection: ToolConnection?): ResolvedAgentTool {
        val actualConnection = connection
        val token = actualConnection?.secretRef?.let { secretRef ->
            secretVault.read(secretRef)?.let { bytes ->
                try {
                    String(bytes, StandardCharsets.UTF_8).trim()
                } finally {
                    bytes.fill(0)
                }
            }
        }.orEmpty()

        val tool = GitHubTool(
            apiToken = token,
            modelToolName = actualConnection?.let { "github__${it.alias}" } ?: BuiltInAgentTool.GITHUB,
            accountName = actualConnection?.name,
            repositoryContext = actualConnection?.let { gitHubWorkspaceStore?.get(it.connectionUid) }
        )
        return tool.resolved(actualConnection?.connectionUid, actualConnection?.name, BuiltInAgentTool.GITHUB)
    }

    private suspend fun resolveWebSearch(connection: ToolConnection?): ResolvedAgentTool? {
        val actualConnection = connection ?: return null
        val provider = SEARCH_PROVIDERS[actualConnection.type] ?: return null
        val endpointUrl = provider.defaultEndpointUrl
        val modelToolName = "web_search_" + actualConnection.connectionUid.replace("-", "_")
        val definition = WebSearchTool(
            config = WebSearchProviderConfig(provider.provider, "", endpointUrl),
            modelToolName = modelToolName,
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
                        modelToolName = modelToolName,
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
                    outputSchema = remoteTool.outputSchema,
                    clientManager = mcpClientManager
                )
                ResolvedAgentTool(
                    tool = tool,
                    connectionUid = connection.connectionUid,
                    connectionName = connection.name,
                    realToolName = remoteTool.name,
                    modelToolName = tool.definition.name,
                    shareableReadOnly = remoteTool.name in connection.approvedReadTools.lines().map(String::trim)
                )
            }
    }

    suspend fun mcpConfig(
        connection: ToolConnection,
        forceOAuthRefresh: Boolean = false,
        rejectedAuthorizationHeader: String? = null
    ): McpConnectionConfig {
        val authorization = when (connection.authType) {
            ToolConnectionAuthType.NONE -> null

            ToolConnectionAuthType.BEARER, ToolConnectionAuthType.API_KEY -> readBearerHeader(connection)

            ToolConnectionAuthType.OAUTH -> mcpOAuthCoordinator.authorizationHeader(
                connection,
                forceOAuthRefresh,
                rejectedAuthorizationHeader
            )

            else -> throw IllegalArgumentException("Unsupported MCP authentication type.")
        }
        return McpConnectionConfig(
            connectionUid = connection.connectionUid,
            endpointUrl = dev.chungjungsoo.gptmobile.data.security.EndpointSecrets.resolve(connection, secretVault),
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
        modelToolName = definition.name,
        shareableReadOnly = realToolName in SHAREABLE_BUILT_IN_TOOLS
    )

    private companion object {
        const val WEB_SEARCH_TOOL = "web_search"
        val SHAREABLE_BUILT_IN_TOOLS = setOf(
            BuiltInAgentTool.CURRENT_DATE,
            BuiltInAgentTool.CALCULATE_EXPRESSION,
            BuiltInAgentTool.READ_FILE_SLICE,
            BuiltInAgentTool.READ_URL,
            BuiltInAgentTool.DEVICE_LOCATION,
            WEB_SEARCH_TOOL
        )
        val SEARCH_PROVIDERS = mapOf(
            ToolConnectionType.FIRECRAWL to SearchProvider(WebSearchProvider.FIRECRAWL, "https://api.firecrawl.dev/v2/search"),
            ToolConnectionType.PERPLEXITY to SearchProvider(WebSearchProvider.PERPLEXITY, "https://api.perplexity.ai/search"),
            ToolConnectionType.EXA to SearchProvider(WebSearchProvider.EXA, "https://api.exa.ai/search"),
            ToolConnectionType.BRAVE to SearchProvider(WebSearchProvider.BRAVE, "https://api.search.brave.com/res/v1/web/search")
        )
    }
}

private class McpAgentTool(
    override val definition: AgentToolDefinition,
    private val authType: String,
    private val config: suspend (Boolean, String?) -> McpConnectionConfig,
    private val remoteToolName: String,
    private val clientManager: McpClientManager,
    private val outputSchema: io.modelcontextprotocol.kotlin.sdk.types.ToolSchema? = null
) : AgentTool {
    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        val isFileTool = isFileReadingTool(remoteToolName)
        val startLine = if (isFileTool) {
            arguments["start_line"]?.jsonPrimitive?.intOrNull
        } else {
            null
        }
        val endLine = if (isFileTool) {
            arguments["end_line"]?.jsonPrimitive?.intOrNull
        } else {
            null
        }

        val remoteArguments = if (isFileTool && (startLine != null || endLine != null)) {
            JsonObject(arguments.filterKeys { it != "start_line" && it != "end_line" })
        } else {
            arguments
        }

        val initialConfig = config(false, null)
        val result = try {
            clientManager.callTool(initialConfig, remoteToolName, remoteArguments)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (authType != ToolConnectionAuthType.OAUTH || !error.isUnauthorized()) throw error
            clientManager.callTool(
                config(true, initialConfig.authorizationHeader),
                remoteToolName,
                remoteArguments
            )
        }
        if (outputSchema != null && result.isError != true) {
            val schema = kotlinx.serialization.json.Json.encodeToJsonElement(io.modelcontextprotocol.kotlin.sdk.types.ToolSchema.serializer(), outputSchema) as JsonObject
            val invalid = result.structuredContent?.let { schemaError(schema, it) } ?: "The tool did not return its declared structured output."
            if (invalid.isNotEmpty() && result.structuredContent == null) return AgentToolResult(callId, ToolResultContent.Text(invalid), true)
            result.structuredContent?.let { schemaError(schema, it) }?.let { error -> return AgentToolResult(callId, ToolResultContent.Text(error), true) }
        }
        return mapMcpToolResult(callId, result, startLine, endLine)
    }
}

private fun Tool.isSafelyShareableReadOnly(): Boolean {
    if (annotations?.readOnlyHint != true) return false

    val tokens = name.lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter(String::isNotBlank)
        .toSet()
    if (tokens.any { it in MUTATING_TOOL_TOKENS }) return false
    return tokens.any { it in READ_ONLY_TOOL_TOKENS }
}

private val MUTATING_TOOL_TOKENS = setOf(
    "add",
    "book",
    "buy",
    "cancel",
    "commit",
    "create",
    "delete",
    "edit",
    "execute",
    "install",
    "move",
    "order",
    "patch",
    "post",
    "publish",
    "purchase",
    "remove",
    "rename",
    "report",
    "restore",
    "run",
    "send",
    "set",
    "submit",
    "trigger",
    "update",
    "upload",
    "write"
)

private val READ_ONLY_TOOL_TOKENS = setOf(
    "check",
    "current",
    "date",
    "describe",
    "fetch",
    "find",
    "get",
    "inspect",
    "list",
    "location",
    "lookup",
    "query",
    "read",
    "retrieve",
    "search",
    "status",
    "time",
    "view",
    "weather"
)

private fun Throwable.isUnauthorized(): Boolean = generateSequence(this) { it.cause }
    .any { error -> error is StreamableHttpError && error.code == 401 }

private fun ToolConnection.isGitHubMcpEndpoint(): Boolean {
    val endpoint = endpointUrl?.lowercase().orEmpty()
    return "api.githubcopilot.com/mcp" in endpoint ||
        "github-mcp-server" in endpoint
}

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
