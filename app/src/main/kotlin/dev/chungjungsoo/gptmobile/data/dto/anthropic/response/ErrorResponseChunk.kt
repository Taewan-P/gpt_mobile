package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseChunk(

    override val type: EventType = EventType.ERROR,

    @SerialName("error")
    val error: ErrorDetail
) : MessageResponseChunk()
