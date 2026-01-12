package dev.chungjungsoo.gptmobile.data.dto.openai.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("text")
data class TextContent(
    @SerialName("type")
    val type: String = "text",

    @SerialName("text")
    val text: String
) : MessageContent()
