package dev.chungjungsoo.gptmobile.data.localmodel

object DownloadProgress {
    fun receivedBytes(workReceived: Long, diskPartialBytes: Long): Long = if (workReceived > 0L) workReceived else diskPartialBytes.coerceAtLeast(0L)

    fun fraction(receivedBytes: Long, totalBytes: Long): Float? {
        if (receivedBytes <= 0L || totalBytes <= 0L) return null
        return (receivedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun percent(receivedBytes: Long, totalBytes: Long): Int = fraction(receivedBytes, totalBytes)?.let { (it * 100f).toInt().coerceIn(0, 100) } ?: 0
}
