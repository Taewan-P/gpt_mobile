package dev.chungjungsoo.gptmobile.data.dto.openai.request

import dev.chungjungsoo.gptmobile.data.dto.openai.common.MessageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class ChatMessage(
    @SerialName("role")
    val role: Role,

    // OpenAI content is a string or a part array. Text-only messages stay strings so
    // gateways that reject object lists for system and assistant roles, such as NVIDIA, accept them.
    @Serializable(with = OpenAIChatContentSerializer::class)
    @SerialName("content")
    val content: List<MessageContent>? = null,

    @SerialName("tool_calls")
    val toolCalls: List<ChatToolCall>? = null,

    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

@Serializable
data class ChatToolCall(
    @SerialName("id")
    val id: String,

    @SerialName("function")
    val function: ChatFunction,

    @SerialName("type")
    val type: String = "function"
)

@Serializable
data class ChatFunction(
    @SerialName("name")
    val name: String,

    @SerialName("arguments")
    val arguments: String
)

object OpenAIChatContentSerializer : KSerializer<List<MessageContent>?> {
    private val listSerializer = ListSerializer(MessageContent.serializer())
    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun serialize(encoder: Encoder, value: List<MessageContent>?) {
        val jsonEncoder = encoder as JsonEncoder
        val text = value?.singleOrNull() as? TextContent
        when {
            value == null -> jsonEncoder.encodeJsonElement(JsonNull)
            text != null -> jsonEncoder.encodeString(text.text)
            else -> jsonEncoder.encodeJsonElement(jsonEncoder.json.encodeToJsonElement(listSerializer, value))
        }
    }

    override fun deserialize(decoder: Decoder): List<MessageContent>? {
        val jsonDecoder = decoder as JsonDecoder
        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> null

            is JsonPrimitive -> {
                if (!element.isString) throw SerializationException("Chat content must be a string or array")
                listOf(TextContent(element.content))
            }

            is JsonArray -> jsonDecoder.json.decodeFromJsonElement(listSerializer, element)

            else -> throw SerializationException("Chat content must be a string or array")
        }
    }
}
