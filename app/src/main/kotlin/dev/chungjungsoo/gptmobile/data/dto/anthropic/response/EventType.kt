package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EventType {

    @SerialName("message_start")
    MESSAGE_START,

    @SerialName("content_block_start")
    CONTENT_START,

    @SerialName("content_block_delta")
    CONTENT_DELTA,

    @SerialName("content_block_stop")
    CONTENT_STOP,

    @SerialName("message_delta")
    MESSAGE_DELTA,

    @SerialName("message_stop")
    MESSAGE_STOP,

    @SerialName("ping")
    PING,

    @SerialName("error")
    ERROR
}
