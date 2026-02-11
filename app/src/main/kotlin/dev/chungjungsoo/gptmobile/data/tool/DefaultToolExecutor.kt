package dev.chungjungsoo.gptmobile.data.tool

import dev.chungjungsoo.gptmobile.data.dto.tool.ToolCall
import dev.chungjungsoo.gptmobile.data.dto.tool.ToolResult
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DefaultToolExecutor @Inject constructor() : ToolExecutor {

    override suspend fun execute(toolCall: ToolCall): ToolResult {
        val output = Json.encodeToString(
            buildJsonObject {
                put("error", "Tool execution is not configured")
                put("tool", toolCall.name)
            }
        )

        return ToolResult(
            callId = toolCall.id,
            name = toolCall.name,
            output = output,
            isError = true
        )
    }
}

