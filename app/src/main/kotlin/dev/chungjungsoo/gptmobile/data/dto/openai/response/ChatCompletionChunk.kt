package dev.chungjungsoo.gptmobile.data.dto.openai.response

import dev.chungjungsoo.gptmobile.data.dto.openai.request.OpenAIFunctionCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionChunk(
    @SerialName("id")
    val id: String? = null,

    @SerialName("object")
    val objectType: String? = null,

    @SerialName("created")
    val created: Long? = null,

    @SerialName("model")
    val model: String? = null,

    @SerialName("choices")
    val choices: List<Choice>? = null,

    @SerialName("error")
    val error: ErrorDetail? = null
)

@Serializable
data class Choice(
    @SerialName("index")
    val index: Int,

    @SerialName("delta")
    val delta: Delta,

    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class Delta(
    @SerialName("role")
    val role: String? = null,

    @SerialName("content")
    val content: String? = null,

    @SerialName("tool_calls")
    val toolCalls: List<ToolCallDelta>? = null
)

@Serializable
data class ToolCallDelta(
    @SerialName("index")
    val index: Int,

    @SerialName("id")
    val id: String? = null,

    @SerialName("type")
    val type: String? = null,

    @SerialName("function")
    val function: OpenAIFunctionCall? = null
)

@Serializable
data class ErrorDetail(
    @SerialName("message")
    val message: String,

    @SerialName("type")
    val type: String? = null,

    @SerialName("code")
    val code: String? = null
)
