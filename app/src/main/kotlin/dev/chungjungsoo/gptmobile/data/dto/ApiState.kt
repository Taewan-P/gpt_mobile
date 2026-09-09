package dev.chungjungsoo.gptmobile.data.dto

import dev.chungjungsoo.gptmobile.data.context.ApiErrorKind

sealed class ApiState {
    data object Loading : ApiState()
    data class Thinking(val thinkingChunk: String) : ApiState()
    data class Success(val textChunk: String) : ApiState()
    data class ToolCall(val toolSequence: Int) : ApiState()
    data class Notice(val message: String, val persistent: Boolean = false) : ApiState()
    data class Compaction(val active: Boolean) : ApiState()
    data class Error(
        val message: String,
        val kind: ApiErrorKind = ApiErrorKind.GENERIC,
        val platformUid: String? = null,
        val model: String? = null,
        val endpoint: String? = null
    ) : ApiState()
    data object Done : ApiState()
}
