package dev.chungjungsoo.gptmobile.data.network

import java.io.IOException
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Resilient retry policy and backoff utility for SSE and streaming HTTP LLM endpoints.
 * Automatically backs off on transient errors (HTTP 429 Too Many Requests, 502/503/504 Bad Gateway/Unavailable,
 * and transient socket disconnects) without dropping active chat context.
 */
object ResilientStreamingClient {

    data class RetryConfig(
        val maxAttempts: Int = 4,
        val initialDelayMs: Long = 1000L,
        val maxDelayMs: Long = 16000L,
        val backoffFactor: Double = 2.0,
        val jitterRatio: Double = 0.2
    )

    /**
     * Executes a streaming operation with exponential backoff and jitter.
     *
     * @param config Retry limits and timings.
     * @param onRetry Callback invoked prior to each retry attempt with attempt count and delay in ms.
     * @param block The suspendable block performing the streaming request.
     */
    suspend fun <T> executeWithRetry(
        config: RetryConfig = RetryConfig(),
        onRetry: ((attempt: Int, delayMs: Long, reason: Throwable) -> Unit)? = null,
        shouldRetry: () -> Boolean = { true },
        block: suspend () -> T
    ): T {
        var currentAttempt = 0
        var currentDelay = config.initialDelayMs

        while (true) {
            try {
                return block()
            } catch (e: Throwable) {
                currentAttempt++
                if (currentAttempt >= config.maxAttempts || !shouldRetry() || !isRetryable(e)) {
                    throw e
                }

                // Calculate exponential backoff with jitter
                val jitter = (currentDelay * config.jitterRatio * (Math.random() * 2 - 1)).toLong()
                val sleepDuration = min(config.maxDelayMs, (currentDelay + jitter).coerceAtLeast(100L))

                onRetry?.invoke(currentAttempt, sleepDuration, e)
                delay(sleepDuration)

                currentDelay = (currentDelay * config.backoffFactor).toLong().coerceAtMost(config.maxDelayMs)
            }
        }
    }

    /**
     * Determine if an exception represents a transient network or server fault suitable for automatic retry.
     */
    fun isRetryable(throwable: Throwable): Boolean {
        if (throwable is CancellationException) return false
        var current: Throwable? = throwable
        repeat(8) {
            val value = current ?: return false
            if (value is CancellationException) return false
            val message = value.message?.lowercase().orEmpty()
            if (value is IOException ||
                message.contains("429") ||
                message.contains("rate limit") ||
                message.contains("503") ||
                message.contains("502") ||
                message.contains("504") ||
                message.contains("timeout") ||
                message.contains("timed out") ||
                message.contains("unable to resolve host") ||
                message.contains("connection abort") ||
                message.contains("connection closed") ||
                isPrematureConnectionClose(value)
            ) {
                return true
            }
            current = value.cause
        }
        return false
    }

    /**
     * Detects HTTP/TCP disconnects reported by Ktor/OkHttp when a peer closes a
     * streaming response without a clean protocol terminator. Walks the cause
     * chain because engines frequently wrap the underlying IOException.
     */
    fun shouldTreatPrematureCloseAsStreamEnd(receivedPayload: Boolean, throwable: Throwable, completed: Boolean = false): Boolean =
        receivedPayload && completed && isPrematureConnectionClose(throwable)

    fun isPrematureConnectionClose(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        repeat(8) {
            val message = current?.message?.lowercase().orEmpty()
            if (message.contains("prematurely closed") ||
                message.contains("failed to parse http response") ||
                message.contains("unexpected eof") ||
                message.contains("unexpected end of stream") ||
                message.contains("end of stream") ||
                message.contains("stream was reset") ||
                message.contains("connection reset") ||
                message.contains("reset by peer")
            ) {
                return true
            }
            current = current?.cause
            if (current == null) return false
        }
        return false
    }
}
