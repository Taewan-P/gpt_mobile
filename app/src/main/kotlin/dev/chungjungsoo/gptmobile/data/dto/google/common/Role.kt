package dev.chungjungsoo.gptmobile.data.dto.google.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    @SerialName("user")
    USER,

    @SerialName("model")
    MODEL // Gemini uses "model" instead of "assistant"
}
