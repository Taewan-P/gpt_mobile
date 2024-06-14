package dev.chungjungsoo.gptmobile.data.dto.anthropic.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImageSource(
    @SerialName("type")
    val type: ImageSourceType,

    @SerialName("media_type")
    val mediaType: MediaType,

    @SerialName("data")
    val data: String
)
