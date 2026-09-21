package dev.chungjungsoo.gptmobile.data.dto.anthropic.request

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AnthropicContextManagement(
    @SerialName("edits")
    val edits: List<AnthropicContextEdit>
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AnthropicContextEdit(
    @SerialName("type")
    val type: String = "compact_20260112",

    @SerialName("trigger")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val trigger: AnthropicCompactTrigger? = null,

    @SerialName("pause_after_compaction")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pauseAfterCompaction: Boolean? = null,

    @SerialName("instructions")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val instructions: String? = null
)

@Serializable
data class AnthropicCompactTrigger(
    @SerialName("type")
    val type: String = "input_tokens",

    @SerialName("value")
    val value: Int
)
