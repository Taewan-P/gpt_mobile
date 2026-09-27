package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Shared across profiles and tool rounds. An IP's other apps can still consume its quota. */
class FreeAiRequestLimiter(
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private class Bucket {
        val permit = Semaphore(1)
        val requests = ArrayDeque<Long>()
        var retryAt = 0L
    }

    private val buckets = FreeAiProvider.entries.associateWith { Bucket() }

    suspend fun <T> withRequest(provider: FreeAiProvider, request: suspend () -> T): T =
        buckets.getValue(provider).permit.withPermit {
            // Short local spacing is scheduling, not an exhausted provider quota.
            // Keep the permit while waiting so parallel profiles cannot race the next slot.
            delay(spacingDelayMillis(provider))
            reserve(provider)
            request()
        }

    @Synchronized
    private fun spacingDelayMillis(provider: FreeAiProvider): Long {
        val previous = buckets.getValue(provider).requests.peekLast() ?: return 0L
        return if (provider == FreeAiProvider.LLM7) (previous + 1000 - nowMillis()).coerceIn(0, 1000) else 0L
    }

    @Synchronized
    internal fun reserve(provider: FreeAiProvider) {
        val bucket = buckets.getValue(provider)
        val now = nowMillis()
        while (bucket.requests.isNotEmpty() && bucket.requests.first <= now - HOUR_MS) bucket.requests.removeFirst()
        val minuteRequests = bucket.requests.filter { it > now - MINUTE_MS }
        var retryAt = bucket.retryAt
        if (provider == FreeAiProvider.LLM7 && bucket.requests.isNotEmpty()) {
            retryAt = maxOf(retryAt, bucket.requests.last + 1000)
        }
        if (provider.requestsPerMinute > 0 && minuteRequests.size >= provider.requestsPerMinute) {
            retryAt = maxOf(retryAt, minuteRequests.first() + MINUTE_MS)
        }
        if (provider.requestsPerHour > 0 && bucket.requests.size >= provider.requestsPerHour) {
            retryAt = maxOf(retryAt, bucket.requests.first + HOUR_MS)
        }
        if (retryAt > now) throw FreeAiRateLimitException(provider, (retryAt - now + 999) / 1000)
        bucket.requests.addLast(now)
    }

    @Synchronized
    fun defer(provider: FreeAiProvider, retryAfter: String?): Long {
        val now = nowMillis()
        val seconds = retryAfter?.toLongOrNull()?.coerceAtLeast(1)
        val date = runCatching { ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        val wait = (seconds?.coerceAtMost(86400)?.times(1000) ?: date?.minus(now) ?: MINUTE_MS).coerceIn(1000, 86400000)
        val bucket = buckets.getValue(provider)
        bucket.retryAt = maxOf(bucket.retryAt, now + wait)
        return (bucket.retryAt - now + 999) / 1000
    }

    companion object {
        val shared = FreeAiRequestLimiter()
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 3_600_000L
    }
}

class FreeAiRateLimitException(provider: FreeAiProvider, retrySeconds: Long) :
    IllegalStateException(
        "${provider.displayName} has reached its free allowance. Try again in $retrySeconds seconds, or select another Free provider."
    )
