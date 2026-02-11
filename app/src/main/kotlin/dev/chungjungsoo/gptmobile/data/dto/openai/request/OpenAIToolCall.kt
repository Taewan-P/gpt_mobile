package dev.chungjungsoo.gptmobile.data.dto.openai.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIToolCall(
    @SerialName("id")
    val id: String,

    @SerialName("type")
    val type: String = "function",

    @SerialName("function")
    val function: OpenAIFunctionCall
)

@Serializable
data class OpenAIFunctionCall(
    @SerialName("name")
    val name: String,

    @SerialName("arguments")
    val arguments: String
)

