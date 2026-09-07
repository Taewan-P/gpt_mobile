package dev.chungjungsoo.gptmobile.data.dto.google.request

import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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

    @SerialName("safetySettings")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val safetySettings: List<SafetySetting>? = null,

    @SerialName("tools")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tools: List<GoogleTool>? = null,

    @SerialName("toolConfig")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val toolConfig: GoogleToolConfig? = null
)

@Serializable
data class GoogleTool(
    @SerialName("functionDeclarations")
    val functionDeclarations: List<FunctionDeclaration>
)

@Serializable
data class FunctionDeclaration(
    @SerialName("name")
    val name: String,

    @SerialName("description")
    val description: String,

    @SerialName("parameters")
    val parameters: JsonObject
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
data class SafetySetting(
    @SerialName("category")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val category: String,

    @SerialName("threshold")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val threshold: String
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

@Serializable
data class GoogleToolConfig(
    @SerialName("functionCallingConfig")
    val functionCallingConfig: GoogleFunctionCallingConfig
)

@Serializable
data class GoogleFunctionCallingConfig(
    @SerialName("mode")
    val mode: String
)
