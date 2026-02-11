package dev.chungjungsoo.gptmobile.data.dto.tool

import dev.chungjungsoo.gptmobile.data.model.ClientType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolConverterTest {

    private val weatherTool = Tool(
        name = "get_weather",
        description = "Get weather by city",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
        }
    )

    @Test
    fun convertToolsForProvider_openAiCompatibleClients_returnsOpenAiTools() {
        val clients = listOf(
            ClientType.OPENAI,
            ClientType.GROQ,
            ClientType.OLLAMA,
            ClientType.OPENROUTER,
            ClientType.CUSTOM
        )

        clients.forEach { clientType ->
            val result = ToolConverter.convertToolsForProvider(listOf(weatherTool), clientType)

            assertTrue(result is ProviderTools.OpenAI)

            val openAiTools = (result as ProviderTools.OpenAI).tools
            assertEquals(1, openAiTools.size)
            assertEquals("function", openAiTools.first().type)
            assertEquals(weatherTool.name, openAiTools.first().function.name)
            assertEquals(weatherTool.description, openAiTools.first().function.description)
            assertEquals(weatherTool.parameters, openAiTools.first().function.parameters)
        }
    }

    @Test
    fun convertToolsForProvider_anthropic_returnsAnthropicTools() {
        val result = ToolConverter.convertToolsForProvider(listOf(weatherTool), ClientType.ANTHROPIC)

        assertTrue(result is ProviderTools.Anthropic)

        val anthropicTools = (result as ProviderTools.Anthropic).tools
        assertEquals(1, anthropicTools.size)
        assertEquals(weatherTool.name, anthropicTools.first().name)
        assertEquals(weatherTool.description, anthropicTools.first().description)
        assertEquals(weatherTool.parameters, anthropicTools.first().inputSchema)
    }

    @Test
    fun convertToolsForProvider_google_returnsGoogleFunctionDeclarations() {
        val result = ToolConverter.convertToolsForProvider(listOf(weatherTool), ClientType.GOOGLE)

        assertTrue(result is ProviderTools.Google)

        val googleTools = (result as ProviderTools.Google).tools
        assertEquals(1, googleTools.size)
        assertEquals(1, googleTools.first().functionDeclarations.size)

        val function = googleTools.first().functionDeclarations.first()
        assertEquals(weatherTool.name, function.name)
        assertEquals(weatherTool.description, function.description)
        assertEquals(weatherTool.parameters, function.parameters)
    }
}

