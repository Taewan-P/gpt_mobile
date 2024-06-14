package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContentStartResponseChunk(

    override val type: EventType = EventType.CONTENT_START,

    @SerialName("index")
    val index: Int,

    @SerialName("content_block")
    val contentBlock: ContentBlock
) : MessageResponseChunk()
