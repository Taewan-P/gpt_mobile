package dev.chungjungsoo.gptmobile.data.dto.anthropic.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("thinking")
data class ThinkingContent(
    @SerialName("thinking")
    val thinking: String,

    @SerialName("signature")
    val signature: String
) : MessageContent()
