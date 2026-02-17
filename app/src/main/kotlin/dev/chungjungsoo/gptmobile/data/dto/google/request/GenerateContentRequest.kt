package dev.chungjungsoo.gptmobile.data.dto.google.request

import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GenerateContentRequest(
    @SerialName("contents")
    val contents: List<Content>,

    @SerialName("generationConfig")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val generationConfig: GenerationConfig? = null,

    @SerialName("systemInstruction")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val systemInstruction: Content? = null,

    @SerialName("tools")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tools: List<GoogleTool>? = null,

    @SerialName("toolConfig")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val toolConfig: ToolConfig? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ToolConfig(
    @SerialName("functionCallingConfig")
    val functionCallingConfig: FunctionCallingConfig
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FunctionCallingConfig(
    @SerialName("mode")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val mode: String = "AUTO"
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GenerationConfig(
    @SerialName("temperature")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val temperature: Float? = null,

    @SerialName("topP")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val topP: Float? = null,

    @SerialName("topK")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val topK: Int? = null,

    @SerialName("maxOutputTokens")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxOutputTokens: Int? = null,

    @SerialName("stopSequences")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val stopSequences: List<String>? = null,

    @SerialName("thinkingConfig")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val thinkingConfig: ThinkingConfig? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThinkingConfig(
    @SerialName("thinkingBudget")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val thinkingBudget: Int = -1,

    @SerialName("includeThoughts")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val includeThoughts: Boolean = false
)
