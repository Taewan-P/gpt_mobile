package dev.chungjungsoo.gptmobile.data.dto.openai.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Tool definition for OpenAI API
 */
@Serializable
data class OpenAITool(
    @SerialName("type")
    val type: String = "function",

    @SerialName("function")
    val function: OpenAIFunction
)

@Serializable
data class OpenAIFunction(
    @SerialName("name")
    val name: String,

    @SerialName("description")
    val description: String? = null,

    @SerialName("parameters")
    val parameters: JsonObject? = null
)
