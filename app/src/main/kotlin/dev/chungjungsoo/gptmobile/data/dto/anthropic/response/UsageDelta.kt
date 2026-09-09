package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UsageDelta(

    @SerialName("output_tokens")
    val outputTokens: Int,

    @SerialName("input_tokens")
    val inputTokens: Int? = null,

    @SerialName("cache_creation_input_tokens")
    val cacheCreationInputTokens: Int? = null,

    @SerialName("cache_read_input_tokens")
    val cacheReadInputTokens: Int? = null
)
