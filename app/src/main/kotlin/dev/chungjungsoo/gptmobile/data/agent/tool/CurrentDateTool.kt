package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CurrentDateTool(
    private val clock: Clock = Clock.systemDefaultZone()
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "current_date",
        description = "Returns the current local date, time, and time zone.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(emptyMap()))
            put("additionalProperties", false)
        }
    )

    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        val now = clock.instant().atZone(clock.zone)
        return AgentToolResult(
            callId = callId,
            content = ToolResultContent.Json(
                buildJsonObject {
                    put("date", now.toLocalDate().toString())
                    put("time", now.toLocalTime().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_TIME))
                    put("zone", now.zone.id)
                }
            ),
            isError = false
        )
    }
}
