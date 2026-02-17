package dev.chungjungsoo.gptmobile.data.dto

import dev.chungjungsoo.gptmobile.data.dto.tool.ToolCall
import dev.chungjungsoo.gptmobile.data.dto.tool.ToolResult

sealed class ApiState {
    data object Loading : ApiState()
    data class Thinking(val thinkingChunk: String) : ApiState()
    data class Success(val textChunk: String) : ApiState()
    data class Error(val message: String) : ApiState()
    data object Done : ApiState()

    data class ToolCallRequested(val toolCalls: List<ToolCall>) : ApiState()
    data class ToolExecuting(val toolName: String, val callId: String) : ApiState()
    data class ToolResultReceived(val results: List<ToolResult>) : ApiState()
}
