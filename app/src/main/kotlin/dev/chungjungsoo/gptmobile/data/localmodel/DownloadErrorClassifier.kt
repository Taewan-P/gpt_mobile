package dev.chungjungsoo.gptmobile.data.localmodel

import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class DownloadRetryClass {
    TRANSIENT,
    PERMANENT
}

class DownloadAuthException(
    val kind: DownloadFailureKind,
    message: String
) : IOException(message)

object DownloadErrorClassifier {
    const val MAX_ATTEMPTS = 4
    private const val HTTP_TOO_MANY_REQUESTS = 429

    fun classify(error: Throwable, hadProgress: Boolean = true): DownloadRetryClass {
        if (error is DownloadAuthException) return DownloadRetryClass.PERMANENT
        val messages = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        if (isInsufficientStorage(messages)) return DownloadRetryClass.PERMANENT
        if (isTransientHttp(messages)) return DownloadRetryClass.TRANSIENT
        if (isPermanentHttp(messages)) return DownloadRetryClass.PERMANENT
        if (isTransientNetwork(error, messages)) return DownloadRetryClass.TRANSIENT
        return if (hadProgress) DownloadRetryClass.TRANSIENT else DownloadRetryClass.PERMANENT
    }

    fun shouldRetry(
        classification: DownloadRetryClass,
        runAttemptCount: Int,
        maxAttempts: Int = MAX_ATTEMPTS
    ): Boolean = classification == DownloadRetryClass.TRANSIENT && runAttemptCount < maxAttempts

    private fun isInsufficientStorage(messages: String): Boolean = "enospc" in messages ||
        "no space left" in messages ||
        "insufficient storage" in messages ||
        "not enough space" in messages

    private fun isTransientHttp(messages: String): Boolean {
        val code = httpStatusCode(messages) ?: return false
        return code == HTTP_TOO_MANY_REQUESTS || code == HttpURLConnection.HTTP_CLIENT_TIMEOUT
    }

    private fun isPermanentHttp(messages: String): Boolean {
        val code = httpStatusCode(messages) ?: return false
        return code == HttpURLConnection.HTTP_UNAUTHORIZED ||
            code == HttpURLConnection.HTTP_FORBIDDEN ||
            code == HttpURLConnection.HTTP_NOT_FOUND ||
            (code in 400..499 && !isTransientHttp(messages))
    }

    private fun isTransientNetwork(error: Throwable, messages: String): Boolean {
        val chain = generateSequence(error) { it.cause }
        if (chain.any { it is SocketTimeoutException || it is UnknownHostException || it is ConnectException || it is SocketException }) {
            return true
        }
        return listOf(
            "connection abort",
            "connection reset",
            "timeout",
            "timed out",
            "broken pipe",
            "unknown host",
            "unable to resolve host",
            "network is unreachable",
            "software caused connection abort"
        ).any { it in messages }
    }

    private fun httpStatusCode(messages: String): Int? {
        val match = Regex("http(?: error)?(?: code)?[: ]+(\\d{3})").find(messages) ?: return null
        return match.groupValues[1].toIntOrNull()
    }
}
