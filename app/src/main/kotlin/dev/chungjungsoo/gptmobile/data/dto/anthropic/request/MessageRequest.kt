package dev.chungjungsoo.gptmobile.data.dto.anthropic.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 When certain value is used in the future, use @EncodeDefault or remove default values
 */

@Serializable
data class MessageRequest(
    @SerialName("model")
    val model: String,

    @SerialName("messages")
    val messages: List<InputMessage>,

    @SerialName("max_tokens")
    val maxTokens: Int,

    @SerialName("metadata")
    val metadata: RequestMetadata? = null,

    @SerialName("stop_sequences")
    val stopSequences: List<String>? = null,

    @SerialName("stream")
    val stream: Boolean,

    @SerialName("system")
    val systemPrompt: String? = null,

    @SerialName("temperature")
    val temperature: Float? = null,

    @SerialName("top_k")
    val topK: Int? = null,

    @SerialName("top_p")
    val topP: Int? = null
)
