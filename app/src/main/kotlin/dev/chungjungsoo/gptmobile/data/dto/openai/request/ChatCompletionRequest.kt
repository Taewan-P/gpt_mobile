package dev.chungjungsoo.gptmobile.data.dto.openai.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    @SerialName("model")
    val model: String,

    @SerialName("messages")
    val messages: List<ChatMessage>,

    @SerialName("stream")
    val stream: Boolean = true,

    @SerialName("temperature")
    val temperature: Float? = null,

    @SerialName("top_p")
    val topP: Float? = null,

    @SerialName("max_tokens")
    val maxTokens: Int? = null,

    @SerialName("presence_penalty")
    val presencePenalty: Float? = null,

    @SerialName("frequency_penalty")
    val frequencyPenalty: Float? = null,

    @SerialName("stop")
    val stop: List<String>? = null
)
