package dev.chungjungsoo.gptmobile.data.localmodel

import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadErrorClassifierTest {
    @Test
    fun `connection abort reset timeout and unknown host are transient when work had progressed`() {
        listOf(
            IOException("Software caused connection abort"),
            SocketException("Connection reset"),
            SocketTimeoutException("timeout"),
            UnknownHostException("huggingface.co")
        ).forEach { error ->
            assertEquals(DownloadRetryClass.TRANSIENT, DownloadErrorClassifier.classify(error, hadProgress = true))
        }
    }

    @Test
    fun `auth forbidden not found and storage errors are permanent`() {
        assertEquals(
            DownloadRetryClass.PERMANENT,
            DownloadErrorClassifier.classify(DownloadAuthException(DownloadFailureKind.AUTH_REQUIRED, "sign in"), hadProgress = true)
        )
        assertEquals(
            DownloadRetryClass.PERMANENT,
            DownloadErrorClassifier.classify(IOException("HTTP error code: 403"), hadProgress = true)
        )
        assertEquals(
            DownloadRetryClass.PERMANENT,
            DownloadErrorClassifier.classify(IOException("HTTP error code: 404"), hadProgress = true)
        )
        assertEquals(
            DownloadRetryClass.PERMANENT,
            DownloadErrorClassifier.classify(IOException("No space left on device"), hadProgress = true)
        )
        assertEquals(
            DownloadRetryClass.PERMANENT,
            DownloadErrorClassifier.classify(IOException("ENOSPC"), hadProgress = false)
        )
    }

    @Test
    fun `http 429 and 408 are transient while other 4xx stay permanent`() {
        assertEquals(
            DownloadRetryClass.TRANSIENT,
            DownloadErrorClassifier.classify(IOException("HTTP error code: 429"), hadProgress = false)
        )
        assertEquals(
            DownloadRetryClass.TRANSIENT,
            DownloadErrorClassifier.classify(IOException("HTTP error code: 408"), hadProgress = false)
        )
        assertEquals(
            DownloadRetryClass.PERMANENT,
            DownloadErrorClassifier.classify(IOException("HTTP error code: 400"), hadProgress = false)
        )
    }

    @Test
    fun `retries stop after the attempt cap`() {
        assertTrue(DownloadErrorClassifier.shouldRetry(DownloadRetryClass.TRANSIENT, runAttemptCount = 0))
        assertTrue(DownloadErrorClassifier.shouldRetry(DownloadRetryClass.TRANSIENT, runAttemptCount = 3))
        assertFalse(DownloadErrorClassifier.shouldRetry(DownloadRetryClass.TRANSIENT, runAttemptCount = 4))
        assertFalse(DownloadErrorClassifier.shouldRetry(DownloadRetryClass.PERMANENT, runAttemptCount = 0))
    }
}
