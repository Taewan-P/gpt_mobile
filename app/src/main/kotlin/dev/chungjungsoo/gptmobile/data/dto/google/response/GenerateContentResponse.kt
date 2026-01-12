package dev.chungjungsoo.gptmobile.data.dto.google.response

import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GenerateContentResponse(
    @SerialName("candidates")
    val candidates: List<Candidate>? = null,

    @SerialName("promptFeedback")
    val promptFeedback: PromptFeedback? = null,

    @SerialName("error")
    val error: ErrorDetail? = null
)

@Serializable
data class Candidate(
    @SerialName("content")
    val content: Content,

    @SerialName("finishReason")
    val finishReason: String? = null,

    @SerialName("index")
    val index: Int = 0
)

@Serializable
data class PromptFeedback(
    @SerialName("blockReason")
    val blockReason: String? = null
)

@Serializable
data class ErrorDetail(
    @SerialName("message")
    val message: String,

    @SerialName("code")
    val code: Int? = null,

    @SerialName("status")
    val status: String? = null
)
