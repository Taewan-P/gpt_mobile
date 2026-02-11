package dev.chungjungsoo.gptmobile.data.dto.tool

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents an LLM's request to execute a tool
 */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject
)
