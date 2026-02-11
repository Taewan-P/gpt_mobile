package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ContentBlockType {

    @SerialName("text")
    TEXT,

    @SerialName("text_delta")
    DELTA,

    @SerialName("thinking")
    THINKING,

    @SerialName("thinking_delta")
    THINKING_DELTA,

    @SerialName("signature")
    SIGNATURE,

    @SerialName("signature_delta")
    SIGNATURE_DELTA,

    @SerialName("tool_use")
    TOOL_USE,

    @SerialName("input_json_delta")
    INPUT_JSON_DELTA
}
