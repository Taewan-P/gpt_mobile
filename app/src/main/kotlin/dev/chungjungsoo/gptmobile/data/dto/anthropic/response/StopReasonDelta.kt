package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StopReasonDelta(

    @SerialName("stop_reason")
    val stopReason: StopReason,

    @SerialName("stop_sequence")
    val stopSequence: String? = null
)
