package dev.chungjungsoo.gptmobile.data.dto.tool

import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.AnthropicTool
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleFunctionDeclaration
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.OpenAIFunction
import dev.chungjungsoo.gptmobile.data.dto.openai.request.OpenAITool
import dev.chungjungsoo.gptmobile.data.model.ClientType

/**
 * Converts unified Tool definitions to provider-specific formats
 */
object ToolConverter {

    fun convertToolsForProvider(tools: List<Tool>, clientType: ClientType): ProviderTools {
        return when (clientType) {
            ClientType.OPENAI,
            ClientType.GROQ,
            ClientType.OLLAMA,
            ClientType.OPENROUTER,
            ClientType.CUSTOM -> {
                ProviderTools.OpenAI(tools.map { it.toOpenAITool() })
            }
            ClientType.ANTHROPIC -> {
                ProviderTools.Anthropic(tools.map { it.toAnthropicTool() })
            }
            ClientType.GOOGLE -> {
                ProviderTools.Google(listOf(GoogleTool(tools.map { it.toGoogleFunction() })))
            }
        }
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
}

/**
 * Provider-specific tool containers
 */
sealed class ProviderTools {
    data class OpenAI(val tools: List<OpenAITool>) : ProviderTools()
    data class Anthropic(val tools: List<AnthropicTool>) : ProviderTools()
    data class Google(val tools: List<GoogleTool>) : ProviderTools()
}
