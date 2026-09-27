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
