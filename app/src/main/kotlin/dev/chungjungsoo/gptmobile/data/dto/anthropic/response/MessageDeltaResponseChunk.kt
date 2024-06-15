package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("message_delta")
data class MessageDeltaResponseChunk(

    @SerialName("delta")
    val delta: StopReasonDelta,

    @SerialName("usage")
    val usage: UsageDelta
) : MessageResponseChunk()
