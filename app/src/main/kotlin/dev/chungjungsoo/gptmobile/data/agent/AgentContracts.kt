package dev.chungjungsoo.gptmobile.data.agent

import dev.chungjungsoo.gptmobile.data.dto.openai.response.GatewayProgress
import dev.chungjungsoo.gptmobile.data.network.gateway.GatewayResponseMetadata
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
    data class PhaseChanged(val phase: dev.chungjungsoo.gptmobile.data.localruntime.LocalInferencePhase) : ProviderEvent
    data class LocalMetrics(val metrics: dev.chungjungsoo.gptmobile.data.localruntime.LocalInferenceMetrics) : ProviderEvent
    data class Usage(
        val inputTokens: Int? = null,
        val outputTokens: Int? = null,
        val totalTokens: Int? = null,
        val cumulative: Boolean = true,
        val decodeTokensPerSecond: Double? = null
    ) : ProviderEvent

    /** Effective provider request settings after profile preferences and hard request constraints are resolved. */
    data class RequestConfigured(
        val configuredProfileOutputTokens: Int? = null,
        val requestedOutputTokens: Int? = null,
        val effectiveOutputTokens: Int? = null
    ) : ProviderEvent

    // GatewayProgressUpdate is observational only. AgentRunner must never
    // execute it as a client-owned tool call.
    data class GatewayProgressUpdate(val progress: GatewayProgress) : ProviderEvent

    // GatewayMetadataCaptured informs repository and coordinator of Gateway headers (jobId, etc.)
    data class GatewayMetadataCaptured(val metadata: GatewayResponseMetadata) : ProviderEvent

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
    val traceContent: ToolResultContent? = null,
    val measurement: ToolPayloadMetrics? = null,
    val sharedResult: Boolean = false,
    val outputBudgetExhausted: Boolean = false
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

    /** The shared budget times execution only; waiting for a user decision must not time out. */
    val managesExecutionBudget: Boolean get() = false
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
