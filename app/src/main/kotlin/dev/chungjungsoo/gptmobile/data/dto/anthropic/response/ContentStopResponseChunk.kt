package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContentStopResponseChunk(

    override val type: EventType = EventType.CONTENT_STOP,

    @SerialName("index")
    val index: Int
) : MessageResponseChunk()
