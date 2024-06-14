package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDeltaResponseChunk(

    override val type: EventType = EventType.MESSAGE_DELTA,

    @SerialName("delta")
    val delta: StopReasonDelta,

    @SerialName("usage")
    val usage: UsageDelta
) : MessageResponseChunk()
