package dev.chungjungsoo.gptmobile.data.tool

import dev.chungjungsoo.gptmobile.data.dto.tool.ToolCall
import dev.chungjungsoo.gptmobile.data.dto.tool.ToolResult

interface ToolExecutor {
    suspend fun execute(toolCall: ToolCall): ToolResult
}

