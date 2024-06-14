package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContentDeltaResponseChunk(

    override val type: EventType = EventType.CONTENT_DELTA,

    @SerialName("index")
    val index: Int,

    @SerialName("delta")
    val delta: ContentBlock
) : MessageResponseChunk()
