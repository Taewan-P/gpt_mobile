package dev.chungjungsoo.gptmobile.data.dto.openai.response

import dev.chungjungsoo.gptmobile.data.network.gateway.GatewayResponseMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatCompletionChunk(
    @SerialName("id")
    val id: String? = null,

    @SerialName("object")
    val objectType: String? = null,

    @SerialName("created")
    val created: Long? = null,

    @SerialName("model")
    val model: String? = null,

    @SerialName("choices")
    val choices: List<Choice>? = null,

    @SerialName("gateway_progress")
    val gatewayProgress: GatewayProgress? = null,

    @SerialName("gateway_metadata")
    val gatewayMetadata: GatewayResponseMetadata? = null,

    @SerialName("error")
    val error: ErrorDetail? = null,

    @SerialName("usage")
    val usage: ChatCompletionUsage? = null,

    @SerialName("timings")
    val timings: ChatCompletionTimings? = null,

    // Transport-owned signal: only an explicit SSE [DONE], never an arbitrary socket EOF.
    @Transient
    val streamFinished: Boolean = false
)

@Serializable
data class ChatCompletionUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,

    @SerialName("completion_tokens")
    val completionTokens: Int? = null,

    @SerialName("total_tokens")
    val totalTokens: Int? = null
)

@Serializable
data class GatewayProgressUi(
    @SerialName("icon")
    val icon: String? = null,

    @SerialName("title")
    val title: String? = null,

    @SerialName("show_origin_text")
    val showOriginText: Boolean? = null,

    @SerialName("show_server_text")
    val showServerText: Boolean? = null,

    @SerialName("show_mcp_badge")
    val showMcpBadge: Boolean? = null
)

@Serializable
data class GatewayProgress(
    @SerialName("protocol")
    val protocol: String? = null,

    @SerialName("origin")
    val origin: String? = null,

    @SerialName("event")
    val event: String? = null,

    @SerialName("job_id")
    val jobId: String? = null,

    @SerialName("sequence")
    val sequence: Int? = null,

    @SerialName("phase")
    val phase: String? = null,

    @SerialName("stage")
    val stage: String? = null,

    @SerialName("message")
    val message: String? = null,

    @SerialName("timestamp")
    val timestamp: Double? = null,

    @SerialName("status")
    val status: String? = null,

    @SerialName("tool_call_id")
    val toolCallId: String? = null,

    @SerialName("tool_name")
    val toolName: String? = null,

    @SerialName("display_title")
    val displayTitle: String? = null,

    @SerialName("ui")
    val ui: GatewayProgressUi? = null,

    @SerialName("tool_source")
    val toolSource: String? = null,

    @SerialName("server")
    val server: String? = null,

    @SerialName("route")
    val route: String? = null,

    @SerialName("tool_args")
    val toolArgs: JsonObject? = null,

    @SerialName("result_quality")
    val resultQuality: String? = null,

    @SerialName("duration_ms")
    val durationMs: Long? = null,

    @SerialName("round")
    val round: Int? = null,

    @SerialName("total_tool_calls")
    val totalToolCalls: Int? = null,

    @SerialName("useful_tool_calls")
    val usefulToolCalls: Int? = null,

    @SerialName("repository_tool_calls")
    val repositoryToolCalls: Int? = null,

    @SerialName("no_progress")
    val noProgress: Int? = null,

    @SerialName("checkpoint")
    val checkpoint: Int? = null,

    @SerialName("workflow_profile")
    val workflowProfile: String? = null,

    @SerialName("full_tool_count")
    val fullToolCount: Int? = null,

    @SerialName("selected_tool_count")
    val selectedToolCount: Int? = null,

    @SerialName("recovery_attempt")
    val recoveryAttempt: Int? = null,

    @SerialName("empty_count")
    val emptyCount: Int? = null,

    @SerialName("consecutive_failures")
    val consecutiveFailures: Int? = null

)

@Serializable
data class Choice(
    @SerialName("index")
    val index: Int = 0,

    @SerialName("delta")
    val delta: Delta = Delta(),

    @SerialName("finish_reason")
    val finishReason: String? = null,

    @SerialName("message")
    val message: Delta? = null
) {
    // Complete chat messages omit the streaming-only tool-call index.
    val effectiveDelta: Delta
        get() = message?.let { complete ->
            complete.copy(toolCalls = complete.toolCalls?.mapIndexed { index, call -> call.copy(index = index) })
        } ?: delta
}

@Serializable
data class Delta(
    @SerialName("role")
    val role: String? = null,

    @SerialName("content")
    @Serializable(with = OpenAITextContentSerializer::class)
    val content: String? = null,

    @SerialName("reasoning")
    val reasoning: String? = null,

    @SerialName("reasoning_content")
    val reasoningContent: String? = null,

    @SerialName("tool_calls")
    val toolCalls: List<ChatToolCallDelta>? = null
) {
    val effectiveReasoning: String?
        get() = reasoning ?: reasoningContent
}

@Serializable
data class ChatToolCallDelta(
    @SerialName("index")
    val index: Int = 0,
    @SerialName("id")
    val id: String? = null,
    @SerialName("type")
    val type: String? = null,
    @SerialName("function")
    val function: ChatFunctionDelta? = null
)

@Serializable
data class ChatFunctionDelta(
    @SerialName("name")
    val name: String? = null,
    @SerialName("arguments")
    val arguments: String? = null
)

@Serializable
data class ErrorDetail(
    @SerialName("message")
    val message: String,

    @SerialName("type")
    val type: String? = null,

    @SerialName("code")
    val code: String? = null
)

/** llama.cpp timing data survives a gateway's buffered SSE response. */
@Serializable
data class ChatCompletionTimings(
    @SerialName("predicted_per_second")
    val predictedPerSecond: Double? = null,
    @SerialName("predicted_n")
    val predictedTokens: Int? = null,
    @SerialName("predicted_ms")
    val predictedMs: Double? = null
) {
    val decodeTokensPerSecond: Double?
        get() = predictedPerSecond?.takeIf { it.isFinite() && it > 0 }
            ?: predictedMs?.takeIf { it.isFinite() && it > 0 }?.let { ms ->
                predictedTokens?.takeIf { it > 0 }?.let { it * 1000.0 / ms }
            }
}
