package dev.chungjungsoo.gptmobile.data.dto.openai.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("image_url")
data class ImageContent(
    @SerialName("image_url")
    val imageUrl: ImageUrl
) : MessageContent()

@Serializable
data class ImageUrl(
    // data:image/jpeg;base64,{base64_data} or https://...
    @SerialName("url")
    val url: String,

    // "low", "high", "auto" - for vision models
    @SerialName("detail")
    val detail: String? = null
)
