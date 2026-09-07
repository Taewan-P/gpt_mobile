package dev.chungjungsoo.gptmobile.data.dto.anthropic.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("redacted_thinking")
data class RedactedThinkingContent(
    @SerialName("data")
    val data: String
) : MessageContent()
