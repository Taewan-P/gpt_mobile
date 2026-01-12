package dev.chungjungsoo.gptmobile.data.dto.google.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Content(
    @SerialName("role")
    val role: Role? = null,

    @SerialName("parts")
    val parts: List<Part>
)

@Serializable
sealed class Part

@Serializable
@SerialName("text")
data class TextPart(
    @SerialName("text")
    val text: String
) : Part()

@Serializable
@SerialName("inline_data")
data class InlineDataPart(
    @SerialName("inline_data")
    val inlineData: InlineData
) : Part()

@Serializable
data class InlineData(
    @SerialName("mime_type")
    val mimeType: String,

    // Base64-encoded
    @SerialName("data")
    val data: String
)
