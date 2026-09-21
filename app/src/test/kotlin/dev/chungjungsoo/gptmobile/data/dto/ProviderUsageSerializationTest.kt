package dev.chungjungsoo.gptmobile.data.dto

import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.Usage
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import dev.chungjungsoo.gptmobile.data.dto.groq.response.GroqChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseCompletedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderUsageSerializationTest {

    @Test
    fun `openai responses completed usage is optional`() {
        val withUsage = NetworkClient.openAIJson.decodeFromString<ResponsesStreamEvent>(
            """{"type":"response.completed","response":{"id":"resp_1","usage":{"input_tokens":12,"output_tokens":4,"input_tokens_details":{"cached_tokens":5}}}}"""
        ) as ResponseCompletedEvent
        val withoutUsage = NetworkClient.openAIJson.decodeFromString<ResponsesStreamEvent>(
            """{"type":"response.completed","response":{"id":"resp_1"}}"""
        ) as ResponseCompletedEvent

        assertEquals(12, withUsage.response.usage?.inputTokens)
        assertEquals(4, withUsage.response.usage?.outputTokens)
        assertEquals(5, withUsage.response.usage?.inputTokensDetails?.cachedTokens)
        assertNull(withoutUsage.response.usage)
    }

    @Test
    fun `openai chat completion usage is optional on terminal chunks`() {
        val withUsage = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(
            """{"choices":[],"usage":{"prompt_tokens":9,"completion_tokens":2,"prompt_tokens_details":{"cached_tokens":3}}}"""
        )
        val withoutUsage = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(
            """{"choices":[]}"""
        )

        assertEquals(9, withUsage.usage?.promptTokens)
        assertEquals(2, withUsage.usage?.completionTokens)
        assertEquals(3, withUsage.usage?.promptTokensDetails?.cachedTokens)
        assertNull(withoutUsage.usage)
    }

    @Test
    fun `gemini usageMetadata is optional`() {
        val withUsage = NetworkClient.json.decodeFromString<GenerateContentResponse>(
            """{"usageMetadata":{"promptTokenCount":11,"cachedContentTokenCount":4,"candidatesTokenCount":2,"thoughtsTokenCount":3}}"""
        )
        val withoutUsage = NetworkClient.json.decodeFromString<GenerateContentResponse>("{}")

        assertEquals(11, withUsage.usageMetadata?.promptTokenCount)
        assertEquals(4, withUsage.usageMetadata?.cachedContentTokenCount)
        assertEquals(2, withUsage.usageMetadata?.candidatesTokenCount)
        assertEquals(3, withUsage.usageMetadata?.thoughtsTokenCount)
        assertNull(withoutUsage.usageMetadata)
    }

    @Test
    fun `groq x_groq usage is optional`() {
        val withUsage = NetworkClient.json.decodeFromString<GroqChatCompletionChunk>(
            """{"x_groq":{"usage":{"prompt_tokens":8,"completion_tokens":3,"cache_read_input_tokens":5,"cached_tokens":2}}}"""
        )
        val withoutUsage = NetworkClient.json.decodeFromString<GroqChatCompletionChunk>("{}")

        assertEquals(8, withUsage.xGroq?.usage?.promptTokens)
        assertEquals(3, withUsage.xGroq?.usage?.completionTokens)
        assertEquals(5, withUsage.xGroq?.usage?.cacheReadInputTokens)
        assertEquals(2, withUsage.xGroq?.usage?.cachedTokens)
        assertNull(withoutUsage.xGroq)
        assertNull(withoutUsage.usage)
    }

    @Test
    fun `anthropic usage keeps additive cache fields`() {
        val usage = NetworkClient.json.decodeFromString<Usage>(
            """{"input_tokens":10,"cache_creation_input_tokens":4,"cache_read_input_tokens":20,"output_tokens":7}"""
        )

        assertEquals(10, usage.inputTokens)
        assertEquals(4, usage.cacheCreationInputTokens)
        assertEquals(20, usage.cacheReadInputTokens)
        assertEquals(7, usage.outputTokens)
    }
}
