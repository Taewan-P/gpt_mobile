package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("content_block_delta")
data class ContentDeltaResponseChunk(

    @SerialName("index")
    val index: Int,

    @SerialName("delta")
    val delta: ContentBlock
) : MessageResponseChunk()
