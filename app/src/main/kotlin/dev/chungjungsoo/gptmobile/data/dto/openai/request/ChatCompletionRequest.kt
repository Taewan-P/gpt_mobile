package dev.chungjungsoo.gptmobile.data.dto.openai.request

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ChatCompletionRequest(
    @SerialName("model")
    val model: String,

    @SerialName("messages")
    val messages: List<ChatMessage>,

    @SerialName("stream")
    val stream: Boolean = true,

    @SerialName("temperature")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val temperature: Float? = null,

    @SerialName("top_p")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val topP: Float? = null,

    @SerialName("max_tokens")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxTokens: Int? = null,

    @SerialName("max_completion_tokens")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxCompletionTokens: Int? = null,

    @SerialName("reasoning_effort")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val reasoningEffort: String? = null,

    @SerialName("presence_penalty")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val presencePenalty: Float? = null,

    @SerialName("frequency_penalty")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val frequencyPenalty: Float? = null,

    @SerialName("stop")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val stop: List<String>? = null,

    @SerialName("tools")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tools: List<ChatCompletionTool>? = null,

    @SerialName("tool_choice")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val toolChoice: ChatToolChoice? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ChatCompletionTool(
    @SerialName("type")
    val type: String = "function",

    @SerialName("function")
    val function: ChatCompletionToolFunction
) {
    companion object {
        fun function(
            name: String,
            description: String,
            parameters: JsonObject
        ): ChatCompletionTool = ChatCompletionTool(
            function = ChatCompletionToolFunction(
                name = name,
                description = description,
                parameters = parameters
            )
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ChatCompletionToolFunction(
    @SerialName("name")
    val name: String,

    @SerialName("description")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val description: String? = null,

    @SerialName("parameters")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val parameters: JsonObject? = null,

    @SerialName("strict")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val strict: Boolean? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = ChatToolChoiceSerializer::class)
sealed class ChatToolChoice {
    data object Auto : ChatToolChoice()

    data object None : ChatToolChoice()

    data object Required : ChatToolChoice()

    data class Function(val function: ChatToolChoiceFunctionSpec) : ChatToolChoice()

    companion object {
        val auto: ChatToolChoice = Auto
        val none: ChatToolChoice = None
        val required: ChatToolChoice = Required

        fun function(name: String): ChatToolChoice = Function(ChatToolChoiceFunctionSpec(name))
    }
}

object ChatToolChoiceSerializer : KSerializer<ChatToolChoice> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ChatToolChoice")

    override fun serialize(encoder: Encoder, value: ChatToolChoice) {
        when (value) {
            ChatToolChoice.Auto -> encoder.encodeString("auto")
            ChatToolChoice.None -> encoder.encodeString("none")
            ChatToolChoice.Required -> encoder.encodeString("required")
            is ChatToolChoice.Function -> ChatToolChoiceFunctionSerializer.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): ChatToolChoice {
        val jsonDecoder = decoder as? JsonDecoder ?: return when (val value = decoder.decodeString()) {
            "auto" -> ChatToolChoice.Auto
            "none" -> ChatToolChoice.None
            "required" -> ChatToolChoice.Required
            else -> throw SerializationException("Unexpected tool choice value: $value")
        }
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonPrimitive) {
            return when (element.content) {
                "auto" -> ChatToolChoice.Auto
                "none" -> ChatToolChoice.None
                "required" -> ChatToolChoice.Required
                else -> throw SerializationException("Unexpected tool choice value: ${element.content}")
            }
        }
        return ChatToolChoiceFunctionSerializer.deserializeFromElement(jsonDecoder, element)
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ChatToolChoiceFunctionSpec(
    @SerialName("name")
    val name: String
)

object ChatToolChoiceFunctionSerializer : KSerializer<ChatToolChoice.Function> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ChatToolChoiceFunction")

    override fun serialize(encoder: Encoder, value: ChatToolChoice.Function) {
        val jsonEncoder = encoder as JsonEncoder
        val jsonObject = buildJsonObject {
            put("type", JsonPrimitive("function"))
            put(
                "function",
                jsonEncoder.json.encodeToJsonElement(ChatToolChoiceFunctionSpec.serializer(), value.function)
            )
        }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): ChatToolChoice.Function {
        val jsonDecoder = decoder as JsonDecoder
        return deserializeFromElement(jsonDecoder, jsonDecoder.decodeJsonElement())
    }

    fun deserializeFromElement(
        jsonDecoder: JsonDecoder,
        element: JsonElement
    ): ChatToolChoice.Function {
        val jsonObject = element as? JsonObject
            ?: throw SerializationException("Unexpected tool choice value")
        val function = jsonObject["function"] ?: throw SerializationException("Missing function tool choice")
        return ChatToolChoice.Function(
            jsonDecoder.json.decodeFromJsonElement(ChatToolChoiceFunctionSpec.serializer(), function)
        )
    }
}
