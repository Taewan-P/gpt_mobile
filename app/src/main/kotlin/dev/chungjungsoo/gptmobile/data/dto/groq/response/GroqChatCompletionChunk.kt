package dev.chungjungsoo.gptmobile.data.dto.groq.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroqChatCompletionChunk(
    @SerialName("id")
    val id: String? = null,

    @SerialName("object")
    val objectType: String? = null,

    @SerialName("created")
    val created: Long? = null,

    @SerialName("model")
    val model: String? = null,

    @SerialName("choices")
    val choices: List<GroqChoice>? = null,

    @SerialName("usage")
    val usage: GroqUsage? = null,

    @SerialName("x_groq")
    val xGroq: GroqXGroq? = null,

    @SerialName("error")
    val error: GroqErrorDetail? = null
)

@Serializable
data class GroqXGroq(
    @SerialName("id")
    val id: String? = null,

    @SerialName("usage")
    val usage: GroqUsage? = null
)

@Serializable
data class GroqUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,

    @SerialName("completion_tokens")
    val completionTokens: Int? = null,

    @SerialName("total_tokens")
    val totalTokens: Int? = null,

    @SerialName("cached_tokens")
    val cachedTokens: Int? = null,

    @SerialName("cache_creation_input_tokens")
    val cacheCreationInputTokens: Int? = null,

    @SerialName("cache_read_input_tokens")
    val cacheReadInputTokens: Int? = null
)
