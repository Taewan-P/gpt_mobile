package dev.chungjungsoo.gptmobile.data.dto.openai.request

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CompactResponsesRequest(
    @SerialName("model")
    val model: String,

    @SerialName("input")
    val input: JsonArray,

    @SerialName("instructions")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val instructions: String? = null
)

@Serializable
data class CompactResponsesResult(
    @SerialName("output")
    val output: JsonArray = JsonArray(emptyList())
)
