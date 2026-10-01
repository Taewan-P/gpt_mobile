package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class ProviderSamplingPolicyTest {
    private val request = ChatCompletionRequest(
        model = "moonshotai/kimi-k3",
        messages = emptyList(),
        temperature = 1.5f,
        topP = .9f,
        topK = 40,
        presencePenalty = .5f,
        frequencyPenalty = .5f
    )

    @Test
    fun `reasoning model capabilities apply with the reasoning toggle off`() {
        for (model in listOf("o1", "o3-mini", "o4-mini", "gpt-5", "gpt-5.2", "openai/gpt-5-mini")) {
            val normalized = request.copy(model = model).withEndpointSamplingPolicy("https://api.openai.com/v1")
            assertEquals(null, normalized.temperature)
            assertEquals(null, normalized.topP)
        }
        assertFalse(openAIModelOmitsSampling("gpt-5-chat-latest"))
        assertFalse(openAIModelOmitsSampling("gpt-4.1"))
    }

    @Test
    fun `only explicit unsupported sampling errors qualify for sanitization`() {
        assertEquals("temperature", rejectedSamplingParameter("Unsupported parameter: 'temperature' is not supported with this model"))
        assertEquals(null, rejectedSamplingParameter("Invalid temperature value"))
        assertEquals(null, rejectedSamplingParameter("Unsupported parameter: 'tools'"))
    }

    @Test
    fun `NVIDIA Kimi K3 wire request omits immutable sampling fields`() {
        val normalized = request.withEndpointSamplingPolicy("https://integrate.api.nvidia.com/v1")
        val json = NetworkClient.openAIJson.parseToJsonElement(NetworkClient.openAIJson.encodeToString(normalized)).jsonObject
        assertEquals(1f, normalized.temperature)
        for (key in listOf("top_p", "top_k", "presence_penalty", "frequency_penalty")) assertFalse(json.containsKey(key))
        assertEquals(.9f, request.topP)
    }

    @Test
    fun `other models and providers keep their sampling settings`() {
        assertSame(request, request.withEndpointSamplingPolicy("https://other.example/v1"))
        assertSame(request, request.withEndpointSamplingPolicy("https://integrate.api.nvidia.com.other.example/v1"))
        val other = request.copy(model = "moonshotai/kimi-k2.5")
        assertSame(other, other.withEndpointSamplingPolicy("https://integrate.api.nvidia.com/v1"))
    }
}
