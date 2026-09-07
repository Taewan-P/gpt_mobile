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
import kotlinx.serialization.json.JsonObject

class McpConnectionConfig(
    val connectionUid: String,
    val endpointUrl: String,
    val allowCleartext: Boolean,
    val authorizationHeader: String? = null
)

@Singleton
class McpClientManager internal constructor(
    private val httpClient: HttpClient
) {
    @Inject
    constructor(networkClient: NetworkClient) : this(networkClient())

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
        client.callTool(toolName, arguments)
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
                val client = Client(Implementation(name = CLIENT_NAME, version = CLIENT_VERSION))
                try {
                    client.connect(transport)
                    Session(key, client)
                } catch (error: Exception) {
                    withContext(NonCancellable) { runCatching { client.close() } }
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
        const val CLIENT_VERSION = "0.8.0"
        const val MAX_TOOL_PAGES = 100
        const val MAX_DISCOVERED_TOOLS = 512
        const val MAX_ENDPOINT_LENGTH = 4 * 1024
        const val MAX_AUTHORIZATION_HEADER_LENGTH = 32 * 1024
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
