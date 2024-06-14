package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Usage(

    @SerialName("input_tokens")
    val inputTokens: Int,

    @SerialName("output_tokens")
    val outputTokens: Int
)
