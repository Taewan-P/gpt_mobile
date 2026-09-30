package dev.chungjungsoo.gptmobile.data.dto

import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.openrouter.OpenRouterPlugin
import dev.chungjungsoo.gptmobile.data.openrouter.OpenRouterProviderRouting
import dev.chungjungsoo.gptmobile.data.openrouter.OpenRouterReasoning
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterAdvancedOptionsTest {

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    private fun userMessage(text: String) = ChatMessage(
        role = Role.USER,
        content = listOf(TextContent(text))
    )

    @Test
    fun `reasoning off is explicit rather than leaving provider default enabled`() {
        val encoded = json.encodeToString(OpenRouterReasoning(enabled = false))
        assertTrue(encoded.contains("\"enabled\":false"))
        assertFalse(encoded.contains("\"effort\""))
    }

    @Test
    fun `standard request does not serialize openrouter fields when null`() {
        val request = ChatCompletionRequest(
            model = "openai/gpt-4o",
            messages = listOf(userMessage("Hello"))
        )

        val encoded = json.encodeToString(request)

        assertFalse(encoded.contains("\"models\""))
        assertFalse(encoded.contains("\"provider\""))
        assertFalse(encoded.contains("\"transforms\""))
        assertFalse(encoded.contains("\"reasoning\""))
        assertFalse(encoded.contains("\"plugins\""))
        assertFalse(encoded.contains("\"session_id\""))
    }

    @Test
    fun `openrouter session id serializes as top level routing field`() {
        val request = ChatCompletionRequest(
            model = "anthropic/claude-sonnet-4.6",
            messages = listOf(userMessage("Hello")),
            sessionId = "gptmobile-chat-42-profile"
        )

        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"session_id\":\"gptmobile-chat-42-profile\""))
    }

    @Test
    fun `openrouter advanced fields are correctly serialized when specified`() {
        val request = ChatCompletionRequest(
            model = "openai/gpt-4o",
            messages = listOf(userMessage("Hello")),
            models = listOf("anthropic/claude-3.5-sonnet", "google/gemini-pro-1.5"),
            provider = OpenRouterProviderRouting(
                allowFallbacks = false,
                requireParameters = true,
                dataCollection = "deny",
                ignore = listOf("Together"),
                quantizations = listOf("fp16", "int8"),
                sort = "price",
                zWeight = 0.5f
            ),
            transforms = listOf("middle-out"),
            reasoning = OpenRouterReasoning(
                effort = "high",
                maxTokens = 2048,
                exclude = false
            ),
            plugins = listOf(
                OpenRouterPlugin(
                    id = "web",
                    maxResults = 5,
                    searchPrompt = "search the web"
                )
            )
        )

        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"models\":[\"anthropic/claude-3.5-sonnet\",\"google/gemini-pro-1.5\"]"))
        assertTrue(encoded.contains("\"transforms\":[\"middle-out\"]"))
        assertTrue(encoded.contains("\"provider\":{"))
        assertTrue(encoded.contains("\"allow_fallbacks\":false"))
        assertTrue(encoded.contains("\"require_parameters\":true"))
        assertTrue(encoded.contains("\"data_collection\":\"deny\""))
        assertTrue(encoded.contains("\"ignore\":[\"Together\"]"))
        assertTrue(encoded.contains("\"quantizations\":[\"fp16\",\"int8\"]"))
        assertTrue(encoded.contains("\"sort\":\"price\""))
        assertTrue(encoded.contains("\"z_weight\":0.5"))
        assertTrue(encoded.contains("\"reasoning\":{"))
        assertTrue(encoded.contains("\"effort\":\"high\""))
        assertTrue(encoded.contains("\"max_tokens\":2048"))
        assertTrue(encoded.contains("\"exclude\":false"))
        assertTrue(encoded.contains("\"plugins\":[{"))
        assertTrue(encoded.contains("\"id\":\"web\""))
        assertTrue(encoded.contains("\"max_results\":5"))
        assertTrue(encoded.contains("\"search_prompt\":\"search the web\""))
    }
}
