package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import java.net.URI

/** NVIDIA's Kimi K3 endpoint fixes nucleus sampling and penalties server-side. */
internal fun ChatCompletionRequest.withEndpointSamplingPolicy(apiUrl: String): ChatCompletionRequest {
    val host = runCatching { URI(apiUrl).host }.getOrNull()
    if (!host.equals("integrate.api.nvidia.com", ignoreCase = true) || model != "moonshotai/kimi-k3") return this
    // https://docs.api.nvidia.com/nim/reference/moonshotai-kimi-k3-infer
    return copy(
        temperature = temperature?.coerceIn(0f, 1f),
        topP = null,
        topK = null,
        frequencyPenalty = null,
        presencePenalty = null
    )
}
