package dev.chungjungsoo.gptmobile.data.network

import io.ktor.client.plugins.retry
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlinx.coroutines.CancellationException

private const val MAX_RETRY_AFTER_MILLIS = 30_000L

/** Applies only to generation requests, before their response body reaches the caller. */
internal fun HttpRequestBuilder.retryGenerationRequest(retryHttpResponses: Boolean = true) {
    retry {
        maxRetries = 2
        retryIf { _, response ->
            retryHttpResponses &&
                response.status.value in setOf(408, 429, 500, 502, 503, 504) &&
                (generationRetryAfterMillis(response.headers[HttpHeaders.RetryAfter]) ?: 0L) <= MAX_RETRY_AFTER_MILLIS
        }
        retryOnExceptionIf { _, error ->
            // A failed connection is safe to retry. A timeout/reset after sending a POST can
            // leave its outcome unknown, so response resumption handles that separately.
            error !is CancellationException &&
                generateSequence(error) { it.cause }.take(8).any {
                    it is ConnectException || it is UnknownHostException || it is UnresolvedAddressException
                }
        }
        delayMillis(respectRetryAfterHeader = false) { attempt ->
            val backoff = (500L shl (attempt - 1)) + Random.nextLong(251L)
            maxOf(backoff, generationRetryAfterMillis(response?.headers?.get(HttpHeaders.RetryAfter)) ?: 0L)
                .coerceAtMost(MAX_RETRY_AFTER_MILLIS)
        }
    }
}

internal fun generationRetryAfterMillis(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
    val header = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    header.toLongOrNull()?.let { seconds ->
        return seconds.takeIf { it >= 0 }?.coerceAtMost(Long.MAX_VALUE / 1000)?.times(1000)
    }
    if (header.all { it in '0'..'9' }) return Long.MAX_VALUE
    return runCatching {
        val retryAt = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        (retryAt - nowMillis).coerceAtLeast(0L)
    }.getOrNull()
}
