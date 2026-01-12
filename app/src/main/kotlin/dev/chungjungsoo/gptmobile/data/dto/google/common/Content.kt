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

/**
 * Google's Part uses field presence to distinguish types, not a type discriminator.
 * text field is for text content, inline_data field is for binary/image data.
 * Only one should be non-null at a time.
 */
@Serializable
data class Part(
    @SerialName("text")
    val text: String? = null,

    @SerialName("inline_data")
    val inlineData: InlineData? = null
) {
    companion object {
        fun text(text: String) = Part(text = text)
        fun inlineData(mimeType: String, data: String) = Part(inlineData = InlineData(mimeType, data))
    }
}

@Serializable
data class InlineData(
    @SerialName("mime_type")
    val mimeType: String,

    // Base64-encoded
    @SerialName("data")
    val data: String
)
