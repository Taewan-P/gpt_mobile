package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object HuggingFaceDownloadAuth {
    private const val HF_HOST = "huggingface.co"
    private const val MAX_REDIRECTS = 8

    fun shouldAttachBearerToken(url: String): Boolean {
        val parsed = runCatching { URL(url) }.getOrNull() ?: return false
        if (!parsed.protocol.equals("https", ignoreCase = true)) return false
        val host = parsed.host?.lowercase() ?: return false
        return host == HF_HOST || host.endsWith(".$HF_HOST")
    }

    fun openConnection(
        url: String,
        accessToken: String?,
        extraHeaders: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        var currentUrl = url
        var remainingToken = accessToken
        repeat(MAX_REDIRECTS + 1) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            extraHeaders.forEach { (header, value) ->
                connection.setRequestProperty(header, value)
            }
            if (!remainingToken.isNullOrBlank() && shouldAttachBearerToken(currentUrl)) {
                connection.setRequestProperty("Authorization", HuggingFaceTokenStore.bearerHeader(remainingToken))
            }
            connection.connect()
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("HTTP error code: $code")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                remainingToken = null
            } else {
                return connection
            }
        }
        throw IOException("Too many redirects")
    }
}
