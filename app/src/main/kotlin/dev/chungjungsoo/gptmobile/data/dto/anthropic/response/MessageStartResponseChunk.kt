package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageStartResponseChunk(

    override val type: EventType = EventType.MESSAGE_START,

    @SerialName("message")
    val message: MessageResponse
) : MessageResponseChunk()
