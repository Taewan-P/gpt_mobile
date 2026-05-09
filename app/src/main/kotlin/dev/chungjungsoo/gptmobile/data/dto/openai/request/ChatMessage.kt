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
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ChatMessage(
    @SerialName("role")
    val role: Role,

    @SerialName("content")
    @Serializable(with = ChatMessageContentSerializer::class)
    val content: List<MessageContent>,

    @SerialName("tool_calls")
    val toolCalls: List<ChatToolCall>? = null,

    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

@Serializable
data class ChatToolCall(
    @SerialName("id")
    val id: String? = null,

    @SerialName("type")
    val type: String = "function",

    @SerialName("function")
    val function: ChatToolCallFunction
)

@Serializable
data class ChatToolCallFunction(
    @SerialName("name")
    val name: String? = null,

    @SerialName("arguments")
    val arguments: String? = null
)

object ChatMessageContentSerializer : KSerializer<List<MessageContent>> {
    private val listSerializer = ListSerializer(MessageContent.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ChatMessageContent")

    override fun serialize(encoder: Encoder, value: List<MessageContent>) {
        val jsonEncoder = encoder as JsonEncoder
        if (value.size == 1 && value.first() is TextContent) {
            jsonEncoder.encodeJsonElement(JsonPrimitive((value.first() as TextContent).text))
            return
        }

        val jsonArray = jsonEncoder.json.encodeToJsonElement(listSerializer, value)
        jsonEncoder.encodeJsonElement(jsonArray)
    }

    override fun deserialize(decoder: Decoder): List<MessageContent> {
        val jsonDecoder = decoder as JsonDecoder
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> listOf(TextContent(element.jsonPrimitive.content))
            is JsonArray -> jsonDecoder.json.decodeFromJsonElement(listSerializer, element)
            else -> throw SerializationException("Unexpected content type")
        }
    }
}
