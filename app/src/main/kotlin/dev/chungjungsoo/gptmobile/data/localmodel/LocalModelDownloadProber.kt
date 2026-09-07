package dev.chungjungsoo.gptmobile.data.localmodel

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

fun interface LocalModelDownloadProber {
    fun probe(downloadUrl: String, accessToken: String?): Int
}

@Singleton
class LocalModelDownloadProberImpl @Inject constructor() : LocalModelDownloadProber {
    override fun probe(downloadUrl: String, accessToken: String?): Int = try {
        val connection = HuggingFaceDownloadAuth.openConnection(
            url = downloadUrl,
            accessToken = accessToken,
            extraHeaders = mapOf(
                LocalModelDownloadPaths.RANGE_HEADER to PROBE_RANGE,
                LocalModelDownloadPaths.ACCEPT_ENCODING_HEADER to LocalModelDownloadPaths.IDENTITY_ENCODING
            ),
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS
        )
        try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        NETWORK_ERROR
    }

    companion object {
        const val NETWORK_ERROR = -1
        private const val PROBE_RANGE = "bytes=0-0"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}
