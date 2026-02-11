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

    suspend fun connectAll() {
        lock.withLock {
            val servers = mcpServerDao.getEnabledServers()
            servers.forEach { config ->
                connectInternal(config)
            }
            refreshToolListLocked()
        }
    }

    suspend fun connect(config: McpServerConfig): Result<Unit> = runCatching {
        lock.withLock {
            connectInternal(config)
            refreshToolListLocked()
        }
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
        }
        runCatching { httpClient.close() }
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

    companion object {
        private const val TAG = "McpManager"
        private const val MAX_TOOL_OUTPUT_CHARS = 12_000
    }
}
