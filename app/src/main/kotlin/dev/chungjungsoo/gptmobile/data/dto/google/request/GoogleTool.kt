package dev.chungjungsoo.gptmobile.data.dto.google.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Tool definition for Google Gemini API
 */
@Serializable
data class GoogleTool(
    @SerialName("function_declarations")
    val functionDeclarations: List<GoogleFunctionDeclaration>
)

@Serializable
data class GoogleFunctionDeclaration(
    @SerialName("name")
    val name: String,

    @SerialName("description")
    val description: String? = null,

    @SerialName("parameters")
    val parameters: JsonObject? = null
)
