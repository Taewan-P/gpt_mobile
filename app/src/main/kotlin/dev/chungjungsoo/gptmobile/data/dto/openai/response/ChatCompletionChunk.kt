package dev.chungjungsoo.gptmobile.data.dto.openai.response

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ChatCompletionChunk(
    @SerialName("id")
    val id: String? = null,

    @SerialName("object")
    val objectType: String? = null,

    @SerialName("created")
    val created: Long? = null,

    @SerialName("model")
    val model: String? = null,

    @SerialName("choices")
    val choices: List<Choice>? = null,

    @SerialName("error")
    val error: ErrorDetail? = null
)

@Serializable
data class Choice(
    @SerialName("index")
    val index: Int? = null,

    @SerialName("delta")
    val delta: Delta? = null,

    @SerialName("message")
    val message: Delta? = null,

    @SerialName("text")
    @Serializable(with = FlexibleStringContentSerializer::class)
    val text: String? = null,

    @SerialName("tool_calls")
    val toolCalls: List<ToolCallDelta>? = null,

    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class Delta(
    @SerialName("role")
    val role: String? = null,

    @SerialName("content")
    @Serializable(with = FlexibleStringContentSerializer::class)
    val content: String? = null,

    @SerialName("reasoning_content")
    @Serializable(with = FlexibleStringContentSerializer::class)
    val reasoningContent: String? = null,

    @SerialName("tool_calls")
    val toolCalls: List<ToolCallDelta>? = null
)

@Serializable
data class ToolCallDelta(
    @SerialName("index")
    val index: Int? = null,

    @SerialName("id")
    val id: String? = null,

    @SerialName("type")
    val type: String? = null,

    @SerialName("function")
    val function: ToolFunctionDelta? = null
)

@Serializable
data class ToolFunctionDelta(
    @SerialName("name")
    val name: String? = null,

    @SerialName("arguments")
    @Serializable(with = FlexibleJsonStringSerializer::class)
    val arguments: String? = null
)

@Serializable
data class ErrorDetail(
    @SerialName("message")
    val message: String,

    @SerialName("type")
    val type: String? = null,

    @SerialName("code")
    val code: String? = null
)

object FlexibleStringContentSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleStringContent", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value.orEmpty())
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeString() }.getOrNull()
        return extractText(jsonDecoder.decodeJsonElement())
    }
}

object FlexibleJsonStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleJsonString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value.orEmpty())
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeString() }.getOrNull()
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            JsonNull -> null
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
    }
}

private fun extractText(element: JsonElement?): String? = when (element) {
    null, JsonNull -> null
    is JsonPrimitive -> element.contentOrNull ?: element.toString()
    is JsonArray -> element.mapNotNull { extractText(it) }
        .joinToString(separator = "")
        .takeIf { it.isNotEmpty() }
    is JsonObject -> {
        extractText(element["text"])
            ?: extractText(element["value"])
            ?: extractText(element["content"])
            ?: extractText(element["output_text"])
            ?: extractText(element["message"])
    }
}

private val JsonPrimitive.contentOrNull: String?
    get() = runCatching { content }.getOrNull()
