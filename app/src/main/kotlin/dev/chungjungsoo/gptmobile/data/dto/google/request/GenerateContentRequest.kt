package dev.chungjungsoo.gptmobile.data.dto.google.request

import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GenerateContentRequest(
    @SerialName("contents")
    val contents: List<Content>,

    @SerialName("generationConfig")
    val generationConfig: GenerationConfig? = null,

    @SerialName("systemInstruction")
    val systemInstruction: Content? = null
)

@Serializable
data class GenerationConfig(
    @SerialName("temperature")
    val temperature: Float? = null,

    @SerialName("topP")
    val topP: Float? = null,

    @SerialName("topK")
    val topK: Int? = null,

    @SerialName("maxOutputTokens")
    val maxOutputTokens: Int? = null,

    @SerialName("stopSequences")
    val stopSequences: List<String>? = null
)
