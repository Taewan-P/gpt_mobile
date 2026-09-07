package dev.chungjungsoo.gptmobile.data.dto.openai.request

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Request body for OpenAI Responses API.
 * Used for reasoning models (o1, o3, etc.) to get reasoning content in streaming responses.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResponsesRequest(
    @SerialName("model")
    val model: String,

    @SerialName("input")
    val input: List<ResponseInputItem>,

    @SerialName("stream")
    val stream: Boolean = true,

    @SerialName("instructions")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val instructions: String? = null,

    @SerialName("max_output_tokens")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxOutputTokens: Int? = null,

    @SerialName("temperature")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val temperature: Float? = null,

    @SerialName("top_p")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val topP: Float? = null,

    @SerialName("reasoning")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val reasoning: ReasoningConfig? = null,

    @SerialName("previous_response_id")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val previousResponseId: String? = null,

    @SerialName("tools")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tools: List<ResponseFunctionTool>? = null
)

@Serializable
data class ResponseFunctionTool(
    @SerialName("name")
    val name: String,

    @SerialName("description")
    val description: String,

    @SerialName("parameters")
    val parameters: JsonObject,

    @SerialName("type")
    val type: String = "function"
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ReasoningConfig(
    @SerialName("effort")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val effort: String? = null,

    @SerialName("summary")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val summary: String? = null
)

/**
 * Message format for Responses API input.
 * Content can be a string (text only) or a list of content parts (text + images).
 */
@Serializable(with = ResponseInputItemSerializer::class)
sealed interface ResponseInputItem

@Serializable
data class ResponseInputMessage(
    @SerialName("role")
    val role: String,

    @SerialName("content")
    val content: ResponseInputContent
) : ResponseInputItem

@Serializable
data class ResponseFunctionCallOutput(
    @SerialName("call_id")
    val callId: String,

    @SerialName("output")
    val output: String,

    @SerialName("type")
    val type: String = "function_call_output"
) : ResponseInputItem

object ResponseInputItemSerializer : KSerializer<ResponseInputItem> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ResponseInputItem")

    override fun serialize(encoder: Encoder, value: ResponseInputItem) {
        val jsonEncoder = encoder as JsonEncoder
        val element = when (value) {
            is ResponseInputMessage -> jsonEncoder.json.encodeToJsonElement(ResponseInputMessage.serializer(), value)
            is ResponseFunctionCallOutput -> jsonEncoder.json.encodeToJsonElement(ResponseFunctionCallOutput.serializer(), value)
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): ResponseInputItem {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement().jsonObject
        return if (element["type"]?.jsonPrimitive?.content == "function_call_output") {
            jsonDecoder.json.decodeFromJsonElement(ResponseFunctionCallOutput.serializer(), element)
        } else {
            jsonDecoder.json.decodeFromJsonElement(ResponseInputMessage.serializer(), element)
        }
    }
}

/**
 * Content can be either a simple string or a list of content parts.
 * Serializes as JSON string for Text, JSON array for Parts.
 */
@Serializable(with = ResponseInputContentSerializer::class)
sealed class ResponseInputContent {
    data class Text(val text: String) : ResponseInputContent()
    data class Parts(val parts: List<ResponseContentPart>) : ResponseInputContent()

    companion object {
        fun text(text: String): ResponseInputContent = Text(text)
        fun parts(parts: List<ResponseContentPart>): ResponseInputContent = Parts(parts)
    }
}

/**
 * Custom serializer that outputs string for Text and array for Parts.
 */
object ResponseInputContentSerializer : KSerializer<ResponseInputContent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ResponseInputContent")

    override fun serialize(encoder: Encoder, value: ResponseInputContent) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is ResponseInputContent.Text -> jsonEncoder.encodeJsonElement(JsonPrimitive(value.text))

            is ResponseInputContent.Parts -> {
                val jsonArray = jsonEncoder.json.encodeToJsonElement(
                    ListSerializer(ResponseContentPart.serializer()),
                    value.parts
                )
                jsonEncoder.encodeJsonElement(jsonArray)
            }
        }
    }

    override fun deserialize(decoder: Decoder): ResponseInputContent {
        val jsonDecoder = decoder as JsonDecoder
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> ResponseInputContent.Text(element.jsonPrimitive.content)

            is JsonArray -> {
                val parts = jsonDecoder.json.decodeFromJsonElement(
                    ListSerializer(ResponseContentPart.serializer()),
                    element
                )
                ResponseInputContent.Parts(parts)
            }

            else -> throw IllegalArgumentException("Unexpected JSON element type")
        }
    }
}

/**
 * Content part for multi-modal input (text or image).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResponseContentPart(
    @SerialName("type")
    val type: String,

    @SerialName("text")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val text: String? = null,

    @SerialName("image_url")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val imageUrl: String? = null,

    @SerialName("file_id")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fileId: String? = null,

    @SerialName("detail")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val detail: String? = null
) {
    companion object {
        fun text(text: String) = ResponseContentPart(type = "input_text", text = text)
        fun image(url: String, detail: String = "auto") = ResponseContentPart(
            type = "input_image",
            imageUrl = url,
            detail = detail
        )
        fun imageFile(fileId: String, detail: String = "auto") = ResponseContentPart(
            type = "input_image",
            fileId = fileId,
            detail = detail
        )
    }
}
