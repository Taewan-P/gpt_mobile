package dev.chungjungsoo.gptmobile.data.mcp

import android.util.Log
import dev.chungjungsoo.gptmobile.data.database.dao.McpServerDao
import dev.chungjungsoo.gptmobile.data.database.entity.McpServerConfig
import dev.chungjungsoo.gptmobile.data.database.entity.McpTransportType
import dev.chungjungsoo.gptmobile.data.dto.tool.Tool
import dev.chungjungsoo.gptmobile.data.dto.tool.ToolCall
import dev.chungjungsoo.gptmobile.data.dto.tool.ToolResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.WebSocketClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ResourceLink
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class McpManager @Inject constructor(
    private val mcpServerDao: McpServerDao
) {
    private val connections = mutableMapOf<Int, McpConnection>()
    private val toolToServer = mutableMapOf<String, Int>()
    private val lock = Mutex()

    private val httpClient = HttpClient {
        expectSuccess = false
        install(SSE)
        install(WebSockets)
    }

    private val _availableTools = MutableStateFlow<List<Tool>>(emptyList())
    val availableTools = _availableTools.asStateFlow()
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    suspend fun connectAll(forceRefresh: Boolean = false) {
        lock.withLock {
            val servers = mcpServerDao.getEnabledServers()
            Log.i(TAG, "connectAll enabledServers=${servers.size}")
            if (!forceRefresh && isAlreadyConnectedLocked(servers)) {
                _connectionState.value = _connectionState.value.copy(
                    isConnecting = false,
                    totalServers = servers.size,
                    attemptedServers = servers.size,
                    connectedServers = servers.size,
                    failedServers = 0
                )
                Log.i(TAG, "connectAll skipped: already connected")
                return
            }

            val enabledServerIds = servers.map { it.id }.toSet()
            val staleServerIds = connections.keys.filterNot { it in enabledServerIds }
            staleServerIds.forEach { staleId ->
                connections.remove(staleId)?.let { staleConnection ->
                    runCatching { staleConnection.client.close() }
                }
                removeServerToolsLocked(staleId)
            }
            _connectionState.value = ConnectionState(
                isConnecting = true,
                totalServers = servers.size,
                attemptedServers = 0,
                connectedServers = 0,
                failedServers = 0
            )
            servers.forEach { config ->
                runCatching { connectInternal(config) }
                    .onSuccess {
                        _connectionState.value = _connectionState.value.copy(
                            attemptedServers = _connectionState.value.attemptedServers + 1,
                            connectedServers = connections.size
                        )
                    }
                    .onFailure { throwable ->
                        _connectionState.value = _connectionState.value.copy(
                            attemptedServers = _connectionState.value.attemptedServers + 1,
                            failedServers = _connectionState.value.failedServers + 1,
                            lastError = throwable.message
                        )
                        Log.e(TAG, "connectAll failed serverId=${config.id} name=${config.name}", throwable)
                    }
            }
            refreshToolListLocked()
            _connectionState.value = _connectionState.value.copy(
                isConnecting = false,
                connectedServers = connections.size
            )
            Log.i(TAG, "connectAll complete availableMcpTools=${_availableTools.value.size} names=${_availableTools.value.joinToString { it.name }}")
        }
    }

    suspend fun connect(config: McpServerConfig): Result<Unit> = runCatching {
        lock.withLock {
            connectInternal(config)
            refreshServerToolsLocked(config.id)
            _connectionState.value = _connectionState.value.copy(
                connectedServers = connections.size,
                totalServers = maxOf(_connectionState.value.totalServers, connections.size),
                isConnecting = false
            )
        }
    }.onSuccess {
        Log.i(TAG, "connect success serverId=${config.id} name=${config.name} toolsNow=${_availableTools.value.size}")
    }.onFailure { throwable ->
        Log.e(TAG, "connect failed serverId=${config.id} name=${config.name}", throwable)
    }

    suspend fun refreshTools() {
        lock.withLock {
            refreshToolListLocked()
        }
    }

    suspend fun callTool(toolCall: ToolCall): ToolResult {
        lock.withLock {
            val serverId = toolToServer[toolCall.name] ?: return noToolResult(toolCall)
            val connection = connections[serverId] ?: return disconnectedResult(toolCall)

            return try {
                val result = connection.client.callTool(
                    name = toolCall.name,
                    arguments = toolCall.arguments.toKotlinMap()
                )
                val output = sanitizeToolResult(result)
                ToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    output = output,
                    isError = result.isError == true
                )
            } catch (e: Exception) {
                ToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    output = jsonError(e.message ?: "Unknown MCP error"),
                    isError = true
                )
            }
        }
    }

    suspend fun disconnectAll() {
        lock.withLock {
            connections.values.forEach { connection ->
                runCatching { connection.client.close() }
            }
            connections.clear()
            toolToServer.clear()
            _availableTools.value = emptyList()
            _connectionState.value = ConnectionState()
        }
    }

    suspend fun disconnect(serverId: Int) {
        lock.withLock {
            connections.remove(serverId)?.let { connection ->
                runCatching { connection.client.close() }
            }
            removeServerToolsLocked(serverId)
            val totalAfterDisconnect = maxOf(0, _connectionState.value.totalServers - 1)
            _connectionState.value = _connectionState.value.copy(
                connectedServers = connections.size,
                totalServers = totalAfterDisconnect,
                attemptedServers = minOf(_connectionState.value.attemptedServers, totalAfterDisconnect)
            )
            Log.i(TAG, "disconnect serverId=$serverId toolsNow=${_availableTools.value.size}")
        }
    }

    private fun removeServerToolsLocked(serverId: Int) {
        val removedNames = toolToServer
            .filterValues { it == serverId }
            .keys
            .toSet()
        if (removedNames.isEmpty()) {
            return
        }

        removedNames.forEach { toolToServer.remove(it) }
        _availableTools.value = _availableTools.value.filterNot { it.name in removedNames }
    }

    private suspend fun refreshServerToolsLocked(serverId: Int) {
        removeServerToolsLocked(serverId)

        val connection = connections[serverId] ?: return
        val mcpTools = listAllTools(connection.client)
        val filteredTools = connection.config.allowedTools?.let { allowed ->
            mcpTools.filter { it.name in allowed }
        } ?: mcpTools

        val mergedTools = _availableTools.value.toMutableList()
        filteredTools.forEach { mcpTool ->
            val existing = toolToServer[mcpTool.name]
            if (existing != null && existing != serverId) {
                Log.w(TAG, "Tool name collision: " + mcpTool.name + " from server " + serverId + " overrides " + existing)
                mergedTools.removeAll { it.name == mcpTool.name }
            }
            toolToServer[mcpTool.name] = serverId
            mergedTools.add(mcpTool.toUnifiedTool())
        }

        _availableTools.value = mergedTools
        Log.i(TAG, "refreshServerTools serverId=$serverId discovered=${filteredTools.size} toolsNow=${_availableTools.value.size}")
    }

    private suspend fun connectInternal(config: McpServerConfig) {
        if (!config.enabled) {
            return
        }

        connections.remove(config.id)?.let { previous ->
            runCatching { previous.client.close() }
        }

        val transport = createTransport(config)
        val client = Client(
            clientInfo = Implementation(
                name = "GPTMobile",
                version = "0.7.1"
            )
        )
        client.connect(transport)
        connections[config.id] = McpConnection(client = client, config = config)
    }

    private fun isAlreadyConnectedLocked(servers: List<McpServerConfig>): Boolean {
        if (servers.isEmpty()) {
            return connections.isEmpty()
        }
        if (_availableTools.value.isEmpty()) {
            return false
        }
        if (connections.size != servers.size) {
            return false
        }
        return servers.all { config ->
            val existing = connections[config.id]
            existing != null && existing.config == config
        }
    }

    private fun createTransport(config: McpServerConfig): Transport {
        return when (config.type) {
            McpTransportType.WEBSOCKET -> {
                val url = requireNotNull(config.url) { "WebSocket MCP server requires URL" }
                WebSocketClientTransport(httpClient, url) {
                    config.headers.forEach { (key, value) ->
                        header(key, value)
                    }
                }
            }
            McpTransportType.STREAMABLE_HTTP -> {
                val url = requireNotNull(config.url) { "HTTP MCP server requires URL" }
                StreamableHttpClientTransport(httpClient, url) {
                    config.headers.forEach { (key, value) ->
                        header(key, value)
                    }
                }
            }
            McpTransportType.SSE -> {
                val url = requireNotNull(config.url) { "SSE MCP server requires URL" }
                SseClientTransport(httpClient, url) {
                    config.headers.forEach { (key, value) ->
                        header(key, value)
                    }
                }
            }
            McpTransportType.STDIO -> {
                throw UnsupportedOperationException("STDIO MCP transport requires native process support")
            }
        }
    }

    private suspend fun refreshToolListLocked() {
        val allTools = mutableListOf<Tool>()
        toolToServer.clear()

        connections.forEach { (serverId, connection) ->
            val mcpTools = listAllTools(connection.client)
            val filteredTools = connection.config.allowedTools?.let { allowed ->
                mcpTools.filter { it.name in allowed }
            } ?: mcpTools

            filteredTools.forEach { mcpTool ->
                val existing = toolToServer[mcpTool.name]
                if (existing != null && existing != serverId) {
                    Log.w(TAG, "Tool name collision: " + mcpTool.name + " from server " + serverId + " overrides " + existing)
                }
                toolToServer[mcpTool.name] = serverId
                allTools.add(mcpTool.toUnifiedTool())
            }
        }

        _availableTools.value = allTools
    }

    private suspend fun listAllTools(client: Client): List<io.modelcontextprotocol.kotlin.sdk.types.Tool> {
        val results = mutableListOf<io.modelcontextprotocol.kotlin.sdk.types.Tool>()
        var cursor: String? = null

        do {
            val response = client.listTools(ListToolsRequest(PaginatedRequestParams(cursor = cursor)))
            results += response.tools
            cursor = response.nextCursor
        } while (cursor != null)

        Log.i(TAG, "listAllTools count=${results.size} names=${results.joinToString { it.name }}")
        return results
    }

    private fun noToolResult(toolCall: ToolCall): ToolResult = ToolResult(
        callId = toolCall.id,
        name = toolCall.name,
        output = jsonError("No server provides tool: " + toolCall.name),
        isError = true
    )

    private fun disconnectedResult(toolCall: ToolCall): ToolResult = ToolResult(
        callId = toolCall.id,
        name = toolCall.name,
        output = jsonError("Server not connected"),
        isError = true
    )

    private fun jsonError(message: String): String =
        buildJsonObject { put("error", message) }.toString()

    private fun sanitizeToolResult(result: CallToolResult): String {
        val textOutput = buildString {
            result.content.forEachIndexed { index, block ->
                if (index > 0) {
                    append("\n")
                }
                when (block) {
                    is TextContent -> append(block.text)
                    is ImageContent -> append("[Image content: " + block.mimeType + "]")
                    is AudioContent -> append("[Audio content: " + block.mimeType + "]")
                    is ResourceLink -> append("[Resource link: " + block.uri + "]")
                    is EmbeddedResource -> append("[Embedded resource: " + block.resource.uri + "]")
                }
            }
        }

        val resolved = if (textOutput.isNotBlank()) {
            textOutput
        } else {
            result.structuredContent?.toString().orEmpty()
        }

        return if (resolved.length > MAX_TOOL_OUTPUT_CHARS) {
            resolved.take(MAX_TOOL_OUTPUT_CHARS) + "\n[truncated]"
        } else {
            resolved
        }
    }

    private fun io.modelcontextprotocol.kotlin.sdk.types.Tool.toUnifiedTool(): Tool = Tool(
        name = name,
        description = description ?: name,
        parameters = inputSchema.toJsonObject()
    )

    private fun ToolSchema.toJsonObject(): JsonObject = buildJsonObject {
        put("type", "object")
        properties?.let { put("properties", it) }
        required?.let {
            put("required", buildJsonArray {
                it.forEach { requiredProperty ->
                    add(JsonPrimitive(requiredProperty))
                }
            })
        }
    }

    private fun JsonObject.toKotlinMap(): Map<String, Any?> =
        entries.associate { it.key to it.value.toKotlinValue() }

    private fun JsonElement.toKotlinValue(): Any? {
        return when (this) {
            is JsonObject -> this.toKotlinMap()
            is JsonArray -> map { it.toKotlinValue() }
            is JsonPrimitive -> {
                when {
                    isString -> content
                    content == "null" -> null
                    content.toBooleanStrictOrNull() != null -> content.toBooleanStrict()
                    content.toLongOrNull() != null -> content.toLong()
                    content.toDoubleOrNull() != null -> content.toDouble()
                    else -> content
                }
            }
        }
    }

    private data class McpConnection(
        val client: Client,
        val config: McpServerConfig
    )

    data class ConnectionState(
        val isConnecting: Boolean = false,
        val totalServers: Int = 0,
        val attemptedServers: Int = 0,
        val connectedServers: Int = 0,
        val failedServers: Int = 0,
        val lastError: String? = null
    )

    companion object {
        private const val TAG = "McpManager"
        private const val MAX_TOOL_OUTPUT_CHARS = 12_000
    }
}
