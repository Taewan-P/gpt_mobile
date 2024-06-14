package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ContentBlockType {

    @SerialName("text")
    TEXT,

    @SerialName("text_delta")
    DELTA
}
