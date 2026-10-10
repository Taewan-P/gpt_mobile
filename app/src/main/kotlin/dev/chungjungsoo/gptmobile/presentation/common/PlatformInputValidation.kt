package dev.chungjungsoo.gptmobile.presentation.common

import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.net.URI

internal fun isPlatformApiUrlValid(
    clientType: ClientType?,
    apiUrl: String
): Boolean = when (clientType) {
    ClientType.LITERT_LM -> true

    ClientType.MISTRAL -> runCatching { URI(apiUrl.trim()) }.getOrNull()?.let { url ->
        url.scheme.equals("https", ignoreCase = true) &&
            !url.host.isNullOrBlank() &&
            url.rawPath.endsWith("/v1/") &&
            url.rawQuery == null &&
            url.rawFragment == null
    } ?: false

    else -> apiUrl.isNotBlank()
}

internal fun isPlatformApiKeyValid(
    clientType: ClientType?,
    apiKey: String
): Boolean = clientType != ClientType.MISTRAL || apiKey.isNotBlank()
