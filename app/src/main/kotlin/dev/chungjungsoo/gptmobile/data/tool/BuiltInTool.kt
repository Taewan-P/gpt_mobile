package dev.chungjungsoo.gptmobile.data.tool

import dev.chungjungsoo.gptmobile.data.dto.tool.Tool
import kotlinx.serialization.json.JsonObject

interface BuiltInTool {
    val definition: Tool

    suspend fun execute(arguments: JsonObject): String
}
