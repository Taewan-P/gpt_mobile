package dev.chungjungsoo.gptmobile.data.dto.google.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
 * thought field indicates this is thinking content from Gemini thinking models.
 * Only one should be non-null at a time.
 */
@Serializable
data class Part(
    @SerialName("text")
    val text: String? = null,

    @SerialName("inline_data")
    val inlineData: InlineData? = null,

    @SerialName("file_data")
    val fileData: FileData? = null,

    @SerialName("thought")
    val thought: Boolean? = null,

    @SerialName("thoughtSignature")
    val thoughtSignature: String? = null,

    @SerialName("functionCall")
    val functionCall: FunctionCall? = null,

    @SerialName("functionResponse")
    val functionResponse: FunctionResponse? = null
) {
    companion object {
        fun text(text: String) = Part(text = text)
        fun inlineData(mimeType: String, data: String) = Part(inlineData = InlineData(mimeType, data))
        fun fileData(mimeType: String, fileUri: String) = Part(fileData = FileData(mimeType, fileUri))
    }
}

@Serializable
data class FunctionCall(
    @SerialName("id")
    val id: String? = null,
    @SerialName("name")
    val name: String,
    @SerialName("args")
    val args: JsonObject
)

@Serializable
data class FunctionResponse(
    @SerialName("id")
    val id: String? = null,
    @SerialName("name")
    val name: String,
    @SerialName("response")
    val response: JsonObject
)

@Serializable
data class InlineData(
    @SerialName("mime_type")
    val mimeType: String,

    // Base64-encoded
    @SerialName("data")
    val data: String
)

@Serializable
data class FileData(
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("file_uri")
    val fileUri: String
)
