package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
sealed class MessageResponseChunk {

    abstract val type: EventType
}
