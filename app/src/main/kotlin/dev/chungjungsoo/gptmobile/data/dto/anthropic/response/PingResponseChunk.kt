package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.Serializable

@Serializable
data class PingResponseChunk(

    override val type: EventType = EventType.PING
) : MessageResponseChunk()
