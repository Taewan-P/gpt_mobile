package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.Serializable

@Serializable
data class MessageStopResponseChunk(

    override val type: EventType = EventType.MESSAGE_STOP
) : MessageResponseChunk()
