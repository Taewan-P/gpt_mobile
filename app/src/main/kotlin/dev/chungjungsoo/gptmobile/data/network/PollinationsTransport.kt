package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import java.net.URLEncoder
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

internal data class PollinationsResponse(val status: Int, val body: String, val contentType: String? = null, val retryAfter: String? = null)

/** Keep anonymous requests on the anonymous service; never send chat history to a fallback provider. */
internal fun pollinationsPromptUrl(prompt: String): String {
    val encoded = URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
    require(encoded.length <= 24_000) { "This conversation exceeds Pollinations' URL limit. Start a new chat or choose another Free provider." }
    return "${FreeAiProvider.POLLINATIONS.apiUrl}/$encoded?model=${FreeAiProvider.POLLINATIONS.model}"
}

/** Retry one transient server failure. Rate limits, invalid requests and cancellation are never retried. */
internal suspend fun pollinationsCompletion(request: suspend () -> PollinationsResponse): String {
    repeat(2) { attempt ->
        val response = request()
        if (response.status == 429) {
            val provider = FreeAiProvider.POLLINATIONS
            throw FreeAiRateLimitException(provider, FreeAiRequestLimiter.shared.defer(provider, response.retryAfter))
        }
        if (attempt == 0 && response.status in setOf(500, 502, 503, 504)) {
            delay(1250)
        } else {
            check(response.status in 200..299) { pollinationsError(response) }
            val body = response.body.trim()
            val json = runCatching { NetworkClient.openAIJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
            check(json?.containsKey("error") != true || json?.get("status") == null) { pollinationsError(response) }
            check(!response.contentType.orEmpty().contains("text/html", true) && !body.startsWith("<!DOCTYPE html", true) && !body.startsWith("<html", true)) {
                "Pollinations returned a service page instead of an answer. Try later or select another provider under Free Models."
            }
            check(body.isNotBlank()) { "Pollinations returned no answer. Try later or select another provider under Free Models." }
            return body
        }
    }
    error("Pollinations request did not complete.")
}

private fun pollinationsError(response: PollinationsResponse): String = when {
    response.body.contains("ENOSPC", true) || response.body.contains("no space left on device", true) ->
        "Pollinations is temporarily offline because its server storage is full. Your phone's storage is not the cause. Select Kilo or OVHcloud under Free Models, or retry when Pollinations recovers."
    response.status in setOf(401, 403) ->
        "Pollinations declined this anonymous request. Select another provider under Free Models. An API key is not sent by this connection."
    response.status in setOf(413, 414) ->
        "This conversation exceeds Pollinations' request limit. Start a new chat or choose another Free provider."
    else -> "Pollinations could not answer (HTTP ${response.status}). Retry later or select another provider under Free Models."
}
