package dev.chungjungsoo.gptmobile.data.dto.anthropic.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("compaction")
data class CompactionContent(
    @SerialName("content")
    val content: String? = null
) : MessageContent()
