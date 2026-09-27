package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

class McpConnectionConfig(
    val connectionUid: String,
    val endpointUrl: String,
    val allowCleartext: Boolean,
    val authorizationHeader: String? = null
)

@Singleton
class McpClientManager internal constructor(
    private val httpClient: HttpClient,
    private val mediaStore: McpMediaStore? = null,
    private val interactions: McpInteractions? = null,
    private val sessionConnectTimeoutMs: Long = 15_000
) {
    @Inject
    constructor(networkClient: NetworkClient, mediaStore: McpMediaStore, interactions: McpInteractions) : this(networkClient(), mediaStore, interactions)

    private val mutex = Mutex()

    // ponytail: one global lock serializes session setup only; use per-connection locks if startup contention becomes measurable.
    private val sessions = mutableMapOf<String, Session>()
    private val inFlight = mutableMapOf<String, InFlight>()

    suspend fun listTools(config: McpConnectionConfig): List<Tool> = withSession(config) { client ->
        val tools = mutableListOf<Tool>()
        val seenCursors = mutableSetOf<String>()
        var pageCount = 0
        var cursor: String? = null
        do {
            check(++pageCount <= MAX_TOOL_PAGES) { "MCP server returned too many tool pages." }
            val page = client.listTools(
                request = if (cursor == null) ListToolsRequest() else ListToolsRequest(PaginatedRequestParams(cursor))
            )
            tools += page.tools
            check(tools.size <= MAX_DISCOVERED_TOOLS) { "MCP server returned too many tools." }
            cursor = page.nextCursor
            check(cursor == null || seenCursors.add(cursor)) { "MCP server returned a repeated tools cursor." }
        } while (cursor != null)
        tools
    }

    suspend fun callTool(
        config: McpConnectionConfig,
        toolName: String,
        arguments: JsonObject
    ): CallToolResult = withSession(config) { client ->
        client.callTool(toolName, arguments).let { mediaStore?.materialize(it) ?: it }
    }

    suspend fun browse(config: McpConnectionConfig): McpBrowserData = withSession(config) { client ->
        val resources = mutableListOf<io.modelcontextprotocol.kotlin.sdk.types.Resource>()
        val prompts = mutableListOf<io.modelcontextprotocol.kotlin.sdk.types.Prompt>()
        if (client.serverCapabilities?.resources != null) {
            var cursor: String? = null
            val seen = mutableSetOf<String>()
            do {
                val page = client.listResources(io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest(PaginatedRequestParams(cursor)))
                resources += page.resources
                cursor = page.nextCursor
                check(resources.size <= 500 && (cursor == null || seen.add(cursor)) && seen.size <= 20) { "Resource catalog is too large or repeats pages." }
            } while (cursor != null)
        }
        if (client.serverCapabilities?.prompts != null) {
            var cursor: String? = null
            val seen = mutableSetOf<String>()
            do {
                val page = client.listPrompts(io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest(PaginatedRequestParams(cursor)))
                prompts += page.prompts
                cursor = page.nextCursor
                check(prompts.size <= 500 && (cursor == null || seen.add(cursor)) && seen.size <= 20) { "Prompt catalog is too large or repeats pages." }
            } while (cursor != null)
        }
        McpBrowserData(resources, prompts, client.serverVersion?.name.orEmpty(), System.currentTimeMillis())
    }

    suspend fun readResource(config: McpConnectionConfig, uri: String): String = withSession(config) { client ->
        val result = client.readResource(io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest(io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams(uri)))
        result.contents.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents>().joinToString("\n") { it.text.take(32000) }.take(64000)
    }

    suspend fun getPrompt(config: McpConnectionConfig, name: String, arguments: Map<String, String>): String = withSession(config) { client ->
        val result = client.getPrompt(io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest(io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams(name, arguments)))
        result.messages.joinToString("\n\n") { message ->
            "${message.role}: ${(message.content as? io.modelcontextprotocol.kotlin.sdk.types.TextContent)?.text.orEmpty().take(32000)}"
        }.take(64000)
    }

    suspend fun close(connectionUid: String) {
        val session = takeSession(connectionUid) ?: return
        runCatching { session.client.close() }
    }

    suspend fun closeAll() {
        mutex.lock()
        val active = try {
            sessions.values.toList().also { sessions.clear() }
        } finally {
            mutex.unlock()
        }
        active.forEach { session -> runCatching { session.client.close() } }
    }

    private suspend fun <T> withSession(config: McpConnectionConfig, block: suspend (Client) -> T): T {
        val session = session(config)
        return try {
            block(session.client)
        } catch (error: CancellationException) {
            withContext(NonCancellable) { invalidate(config.connectionUid, session) }
            throw error
        } catch (error: Exception) {
            invalidate(config.connectionUid, session)
            throw error
        }
    }

    private suspend fun session(config: McpConnectionConfig): Session {
        val key = config.validatedKey()
        while (true) {
            val created = CompletableDeferred<Session>()
            var stale: Session? = null
            var awaiting: CompletableDeferred<Session>? = null
            mutex.lock()
            try {
                sessions[config.connectionUid]?.takeIf { it.key == key }?.let { return it }
                inFlight[config.connectionUid]?.let { existing ->
                    awaiting = existing.deferred
                } ?: run {
                    stale = sessions.remove(config.connectionUid)
                    inFlight[config.connectionUid] = InFlight(key, created)
                }
            } finally {
                mutex.unlock()
            }
            awaiting?.await()
            if (awaiting != null) continue
            withContext(NonCancellable) { stale?.let { runCatching { it.client.close() } } }

            val result = runCatching {
                val transport = StreamableHttpClientTransport(httpClient, config.endpointUrl) {
                    config.authorizationHeader?.let { header(HttpHeaders.Authorization, it) }
                }
                val client = Client(
                    Implementation(name = CLIENT_NAME, version = CLIENT_VERSION),
                    io.modelcontextprotocol.kotlin.sdk.client.ClientOptions(
                        capabilities = io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities(
                            elicitation = if (interactions == null) null else io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities.Elicitation(form = JsonObject(emptyMap()))
                        )
                    )
                )
                interactions?.let { handler -> client.setElicitationHandler { handler.request(dev.chungjungsoo.gptmobile.data.security.DiagnosticRedactor.redact(config.endpointUrl), it) } }
                try {
                    val connected = withTimeoutOrNull(sessionConnectTimeoutMs) {
                        client.connect(transport)
                        true
                    }
                    check(connected == true) { "MCP connection timed out. Check the server address and VPN connection, then retry." }
                    Session(key, client)
                } catch (error: Exception) {
                    withContext(NonCancellable) { runCatching { withTimeoutOrNull(2_000) { client.close() } } }
                    throw error
                }
            }
            withContext(NonCancellable) {
                mutex.lock()
                try {
                    if (inFlight[config.connectionUid]?.deferred === created) {
                        inFlight.remove(config.connectionUid)
                        result.getOrNull()?.let { sessions[config.connectionUid] = it }
                    }
                } finally {
                    mutex.unlock()
                }
                result.fold(created::complete, created::completeExceptionally)
            }
            return result.getOrThrow()
        }
    }

    private suspend fun invalidate(connectionUid: String, expected: Session) {
        mutex.lock()
        val removed = try {
            if (sessions[connectionUid] === expected) sessions.remove(connectionUid) else null
        } finally {
            mutex.unlock()
        }
        withContext(NonCancellable) { removed?.let { runCatching { it.client.close() } } }
    }

    private suspend fun takeSession(connectionUid: String): Session? {
        mutex.lock()
        return try {
            sessions.remove(connectionUid)
        } finally {
            mutex.unlock()
        }
    }

    private fun McpConnectionConfig.validatedKey(): String {
        require(connectionUid.isNotBlank()) { "MCP connection ID is required." }
        require(endpointUrl.length <= MAX_ENDPOINT_LENGTH) { "MCP endpoint URL is too long." }
        val uri = runCatching { URI(endpointUrl) }.getOrNull()
            ?: throw IllegalArgumentException("MCP endpoint must be a valid URL.")
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || scheme == "http") { "MCP endpoint must use HTTP or HTTPS." }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) { "MCP endpoint URL is invalid." }
        require(scheme != "http" || allowCleartext) { "Cleartext MCP requires explicit user approval." }
        require(authorizationHeader == null || authorizationHeader.isNotBlank()) { "MCP authorization header is required." }
        require(authorizationHeader?.contains('\r') != true && authorizationHeader?.contains('\n') != true) {
            "MCP authorization header is invalid."
        }
        require(authorizationHeader == null || authorizationHeader.length <= MAX_AUTHORIZATION_HEADER_LENGTH) {
            "MCP authorization header is too long."
        }
        return "$endpointUrl|${authorizationHeader.orEmpty().sha256()}"
    }

    private data class Session(val key: String, val client: Client)
    private data class InFlight(val key: String, val deferred: CompletableDeferred<Session>)

    private companion object {
        const val CLIENT_NAME = "gpt-mobile"
        const val CLIENT_VERSION = "0.9.10"

        // Bounded (not Int.MAX_VALUE): remote MCP server responses are untrusted input. Without a
        // finite cap, a misbehaving or malicious server (e.g. a compromised or misconfigured
        // Streamable HTTP endpoint such as a GitHub MCP preset) could force unbounded pagination
        // or an unbounded in-memory tool list. These values comfortably exceed any realistic
        // MCP server's tool catalog while restoring the intended safety guarantee.
        const val MAX_TOOL_PAGES = 200
        const val MAX_DISCOVERED_TOOLS = 1000
        const val MAX_ENDPOINT_LENGTH = 32 * 1024
        const val MAX_AUTHORIZATION_HEADER_LENGTH = 128 * 1024
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

data class McpBrowserData(
    val resources: List<io.modelcontextprotocol.kotlin.sdk.types.Resource> = emptyList(),
    val prompts: List<io.modelcontextprotocol.kotlin.sdk.types.Prompt> = emptyList(),
    val serverName: String = "",
    val verifiedAt: Long = 0
)
