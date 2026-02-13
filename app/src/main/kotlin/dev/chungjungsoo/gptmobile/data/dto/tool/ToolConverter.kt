package dev.chungjungsoo.gptmobile.data.dto.tool

import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.AnthropicTool
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleFunctionDeclaration
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.OpenAIFunction
import dev.chungjungsoo.gptmobile.data.dto.openai.request.OpenAITool
import dev.chungjungsoo.gptmobile.data.model.ClientType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Converts unified Tool definitions to provider-specific formats
 */
object ToolConverter {

    fun convertToolsForProvider(tools: List<Tool>, clientType: ClientType): ProviderTools {
        val normalizedTools = tools.map { it.normalizeForModel() }
        return when (clientType) {
            ClientType.OPENAI,
            ClientType.GROQ,
            ClientType.OLLAMA,
            ClientType.OPENROUTER,
            ClientType.CUSTOM -> {
                ProviderTools.OpenAI(normalizedTools.map { it.toOpenAITool() })
            }
            ClientType.ANTHROPIC -> {
                ProviderTools.Anthropic(normalizedTools.map { it.toAnthropicTool() })
            }
            ClientType.GOOGLE -> {
                ProviderTools.Google(listOf(GoogleTool(normalizedTools.map { it.toGoogleFunction() })))
            }
        }
    }

    private fun Tool.normalizeForModel(): Tool = copy(
        description = normalizeText(description, MAX_TOOL_DESCRIPTION_CHARS),
        parameters = normalizeSchema(parameters)
    )

    private fun normalizeSchema(element: JsonElement): JsonObject {
        val normalized = normalizeJsonElement(element, parentKey = null)
        return normalized as? JsonObject ?: buildJsonObject { put("type", "object") }
    }

    private fun normalizeJsonElement(element: JsonElement, parentKey: String?): JsonElement {
        return when (element) {
            is JsonObject -> buildJsonObject {
                element.forEach { (key, value) ->
                    put(key, normalizeJsonElement(value, key))
                }
            }

            is JsonArray -> buildJsonArray {
                element.forEach { value ->
                    add(normalizeJsonElement(value, parentKey))
                }
            }

            is JsonPrimitive -> {
                if (element.isString && parentKey == "description") {
                    JsonPrimitive(normalizeText(element.content, MAX_PARAM_DESCRIPTION_CHARS))
                } else {
                    element
                }
            }
        }
    }

    private fun normalizeText(value: String, maxChars: Int): String {
        val compacted = value.replace(Regex("\\s+"), " ").trim()
        if (compacted.length <= maxChars) {
            return compacted
        }
        return compacted.take(maxChars - 3) + "..."
    }

    private fun Tool.toOpenAITool(): OpenAITool = OpenAITool(
        function = OpenAIFunction(
            name = name,
            description = description,
            parameters = parameters
        )
    )

    private fun Tool.toAnthropicTool(): AnthropicTool = AnthropicTool(
        name = name,
        description = description,
        inputSchema = parameters
    )

    private fun Tool.toGoogleFunction(): GoogleFunctionDeclaration = GoogleFunctionDeclaration(
        name = name,
        description = description,
        parameters = parameters
    )

    private const val MAX_TOOL_DESCRIPTION_CHARS = 280
    private const val MAX_PARAM_DESCRIPTION_CHARS = 220
}

/**
 * Provider-specific tool containers
 */
sealed class ProviderTools {
    data class OpenAI(val tools: List<OpenAITool>) : ProviderTools()
    data class Anthropic(val tools: List<AnthropicTool>) : ProviderTools()
    data class Google(val tools: List<GoogleTool>) : ProviderTools()
}
