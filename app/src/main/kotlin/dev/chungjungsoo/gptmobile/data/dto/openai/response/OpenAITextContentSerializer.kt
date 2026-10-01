package dev.chungjungsoo.gptmobile.data.dto.openai.response

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Compatible providers may return text blocks instead of a single content string. */
object OpenAITextContentSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("OpenAITextContent", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String =
        if (decoder is JsonDecoder) text(decoder.decodeJsonElement()) else decoder.decodeString()

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    private fun text(value: JsonElement): String = when (value) {
        is JsonPrimitive -> value.contentOrNull.orEmpty()
        is JsonArray -> value.joinToString("") { text(it) }
        is JsonObject -> value["text"]?.let(::text) ?: value["value"]?.let(::text).orEmpty()
    }
}
