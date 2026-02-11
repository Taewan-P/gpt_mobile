package dev.chungjungsoo.gptmobile.data.dto.tool

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Unified tool definition (provider-agnostic)
 * Used to define tools that LLMs can call during chat
 */
@Serializable
data class Tool(
    val name: String,
    val description: String,
    val parameters: JsonObject
)
