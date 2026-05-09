package dev.chungjungsoo.gptmobile.data.tool

import kotlinx.serialization.json.JsonObject

interface ToolExecutor {
    suspend fun execute(toolName: String, arguments: JsonObject): ToolExecutionResult
}

