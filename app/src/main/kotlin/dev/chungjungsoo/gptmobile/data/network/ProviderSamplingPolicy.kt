package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import java.net.URI

/** NVIDIA's Kimi K3 endpoint fixes nucleus sampling and penalties server-side. */
internal fun ChatCompletionRequest.withEndpointSamplingPolicy(apiUrl: String): ChatCompletionRequest {
    if (openAIModelOmitsSampling(model)) return copy(temperature = null, topP = null)
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

/** Sampling is a model capability, independent of the user's reasoning toggle. */
internal fun openAIModelOmitsSampling(model: String): Boolean {
    val id = model.lowercase().removePrefix("openai/")
    return Regex("^(?:o[134](?:-|$)|gpt-5(?:[.-]|$))").containsMatchIn(id) && !id.contains("chat")
}

internal fun ResponsesRequest.withModelSamplingPolicy(): ResponsesRequest =
    if (openAIModelOmitsSampling(model)) copy(temperature = null, topP = null) else this

/** Only retry explicit unsupported-sampling errors; never strip tools or output budgets. */
internal fun rejectedSamplingParameter(error: String): String? {
    val message = error.lowercase()
    if (!listOf("unsupported", "not supported", "does not support").any { it in message }) return null
    return listOf("temperature", "top_p").firstOrNull { parameter ->
        Regex("[\"']$parameter[\"']").containsMatchIn(message) || "parameter: $parameter" in message
    }
}
