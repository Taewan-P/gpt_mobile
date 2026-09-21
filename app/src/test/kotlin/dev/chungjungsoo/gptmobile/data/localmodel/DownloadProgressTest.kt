package dev.chungjungsoo.gptmobile.data.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressTest {
    @Test
    fun `seeds received bytes from the partial file when work progress has not arrived`() {
        assertEquals(34_000L, DownloadProgress.receivedBytes(workReceived = 0L, diskPartialBytes = 34_000L))
        assertEquals(50_000L, DownloadProgress.receivedBytes(workReceived = 50_000L, diskPartialBytes = 34_000L))
    }

    @Test
    fun `progress fraction stays inside zero to one and is indeterminate without totals`() {
        assertEquals(0.34f, DownloadProgress.fraction(receivedBytes = 34L, totalBytes = 100L)!!, 0.0001f)
        assertEquals(1f, DownloadProgress.fraction(receivedBytes = 200L, totalBytes = 100L)!!, 0.0001f)
        assertNull(DownloadProgress.fraction(receivedBytes = 0L, totalBytes = 100L))
        assertNull(DownloadProgress.fraction(receivedBytes = 34L, totalBytes = 0L))
        assertEquals(34, DownloadProgress.percent(receivedBytes = 34L, totalBytes = 100L))
    }
}
