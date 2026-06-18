package dev.chungjungsoo.gptmobile.data.dto.openai.request

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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

    @SerialName("conversation")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val conversation: String? = null,

    @SerialName("previous_response_id")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val previousResponseId: String? = null,

    @SerialName("parallel_tool_calls")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val parallelToolCalls: Boolean? = null,

    @SerialName("tools")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tools: List<ResponseTool>? = null
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
@Serializable
data class ResponseInputMessage(
    @SerialName("role")
    val role: String,

    @SerialName("content")
    val content: ResponseInputContent
)

@Serializable(with = ResponseInputItemSerializer::class)
sealed class ResponseInputItem {
    data class Message(val message: ResponseInputMessage) : ResponseInputItem()
    data class FunctionCall(
        val id: String?,
        val callId: String,
        val name: String,
        val arguments: String,
        val status: String? = null
    ) : ResponseInputItem()
    data class FunctionCallOutput(val callId: String, val output: String) : ResponseInputItem()
}

object ResponseInputItemSerializer : KSerializer<ResponseInputItem> {
    private val messageSerializer = ResponseInputMessage.serializer()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ResponseInputItem")

    override fun serialize(encoder: Encoder, value: ResponseInputItem) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is ResponseInputItem.Message -> jsonEncoder.encodeSerializableValue(messageSerializer, value.message)
            is ResponseInputItem.FunctionCall -> {
                val jsonObject = buildJsonObject {
                    put("type", JsonPrimitive("function_call"))
                    value.id?.takeIf { it.isNotBlank() }?.let { put("id", JsonPrimitive(it)) }
                    put("call_id", JsonPrimitive(value.callId))
                    put("name", JsonPrimitive(value.name))
                    put("arguments", JsonPrimitive(value.arguments))
                    value.status?.takeIf { it.isNotBlank() }?.let { put("status", JsonPrimitive(it)) }
                }
                jsonEncoder.encodeJsonElement(jsonObject)
            }
            is ResponseInputItem.FunctionCallOutput -> {
                val jsonObject = buildJsonObject {
                    put("type", JsonPrimitive("function_call_output"))
                    put("call_id", JsonPrimitive(value.callId))
                    put("output", JsonPrimitive(value.output))
                }
                jsonEncoder.encodeJsonElement(jsonObject)
            }
        }
    }

    override fun deserialize(decoder: Decoder): ResponseInputItem {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        val jsonObject = element as? JsonObject ?: throw SerializationException("Unexpected item type")

        return when (jsonObject["type"]?.jsonPrimitive?.content) {
            "function_call" -> ResponseInputItem.FunctionCall(
                id = jsonObject["id"]?.jsonPrimitive?.content,
                callId = jsonObject.requiredString("call_id"),
                name = jsonObject.requiredString("name"),
                arguments = jsonObject.requiredString("arguments"),
                status = jsonObject["status"]?.jsonPrimitive?.content
            )
            "function_call_output" -> ResponseInputItem.FunctionCallOutput(
                callId = jsonObject.requiredString("call_id"),
                output = jsonObject.requiredString("output")
            )
            else -> ResponseInputItem.Message(
                jsonDecoder.json.decodeFromJsonElement(messageSerializer, jsonObject)
            )
        }
    }
}

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.content
        ?: throw SerializationException("Missing required Responses input field: $name")

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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResponseTool(
    @SerialName("type")
    val type: String = "function",

    @SerialName("name")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val name: String? = null,

    @SerialName("description")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val description: String? = null,

    @SerialName("parameters")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val parameters: JsonObject? = null,

    @SerialName("strict")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val strict: Boolean? = null
) {
    companion object {
        fun function(
            name: String,
            description: String,
            parameters: JsonObject
        ): ResponseTool = ResponseTool(
            type = "function",
            name = name,
            description = description,
            parameters = parameters,
            strict = false
        )
    }
}
