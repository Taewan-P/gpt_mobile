package dev.chungjungsoo.gptmobile.data.dto

import dev.chungjungsoo.gptmobile.data.agent.ToolPayloadMetrics
import dev.chungjungsoo.gptmobile.data.dto.openai.response.GatewayProgress
import dev.chungjungsoo.gptmobile.data.localruntime.LocalInferencePhase
import dev.chungjungsoo.gptmobile.data.rag.RecalledFactRef

sealed class ApiState {
    data class TokenUsage(val inputTokens: Int?, val outputTokens: Int?, val totalTokens: Int?) : ApiState()
    data object Loading : ApiState()
    data class ProgressCheckpoint(val text: String, val modelAuthored: Boolean = false) : ApiState()
    data class ActivitySummary(val text: String, val modelAuthored: Boolean = false) : ApiState()
    data class DelegationText(val invocationId: String, val profileName: String, val text: String, val remote: Boolean) : ApiState()
    data class Thinking(val thinkingChunk: String) : ApiState()
    data class Success(val textChunk: String) : ApiState()
    data class ToolCall(val toolSequence: Int, val metrics: ToolPayloadMetrics? = null, val delegated: Boolean = false) : ApiState()
    data class MemoryRecalled(val facts: List<RecalledFactRef>) : ApiState()
    data class Notice(val message: String, val persistent: Boolean = false) : ApiState()
    data class PhaseChanged(val phase: LocalInferencePhase) : ApiState()
    data class GatewayProgressChanged(val progress: GatewayProgress) : ApiState()
    data class Error(val message: String) : ApiState()
    data object Done : ApiState()
}
