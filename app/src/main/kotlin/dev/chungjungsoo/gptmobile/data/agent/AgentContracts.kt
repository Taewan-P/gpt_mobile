package dev.chungjungsoo.gptmobile.data.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed interface ProviderEvent {
    data class ThinkingDelta(val text: String) : ProviderEvent
    data class TextDelta(val text: String) : ProviderEvent
    data class ToolCall(val callId: String, val name: String, val arguments: JsonObject) : ProviderEvent
    data class ToolResult(val call: ToolCall, val result: AgentToolResult) : ProviderEvent
    data class Failed(val message: String) : ProviderEvent
    data class Notice(val message: String, val persistent: Boolean = false) : ProviderEvent
    data object Completed : ProviderEvent
}

data class AgentToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

data class AgentToolResult(
    val callId: String,
    val content: ToolResultContent,
    val isError: Boolean,
    val traceContent: ToolResultContent? = null
)

sealed interface ToolResultContent {
    data class Text(val text: String) : ToolResultContent
    data class Json(val value: JsonElement) : ToolResultContent
    data class ResourceLinks(val links: List<AgentResourceLink>) : ToolResultContent
}

data class AgentResourceLink(
    val uri: String,
    val name: String? = null,
    val mimeType: String? = null
)

enum class AgentRunStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED,
    INTERRUPTED
}

interface AgentTool {
    val definition: AgentToolDefinition
    suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult
}

data class AgentToolExchange(
    val calls: List<ProviderEvent.ToolCall>,
    val results: List<AgentToolResult>
)

interface AgentProviderSession {
    val handlesToolsInternally: Boolean
        get() = false

    fun streamRound(
        tools: List<AgentToolDefinition>,
        exchanges: List<AgentToolExchange>
    ): Flow<ProviderEvent>
}

sealed interface AgentRunEvent {
    data class Provider(val event: ProviderEvent) : AgentRunEvent
    data class ToolStarted(val call: ProviderEvent.ToolCall) : AgentRunEvent
    data class ToolFinished(val call: ProviderEvent.ToolCall, val result: AgentToolResult) : AgentRunEvent
    data class Notice(val message: String, val persistent: Boolean = false) : AgentRunEvent
}

class ToolDefinitionsRejectedException(message: String, cause: Throwable? = null) : Exception(message, cause)
