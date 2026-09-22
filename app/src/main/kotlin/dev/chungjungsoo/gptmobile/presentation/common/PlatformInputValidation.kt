package dev.chungjungsoo.gptmobile.presentation.common

import dev.chungjungsoo.gptmobile.data.model.ClientType

internal fun isPlatformApiUrlValid(
    clientType: ClientType?,
    apiUrl: String
): Boolean = when (clientType) {
    ClientType.LITERT_LM -> true
    ClientType.MISTRAL -> apiUrl.trim().endsWith("/v1/")
    else -> apiUrl.isNotBlank()
}

internal fun isPlatformApiKeyValid(
    clientType: ClientType?,
    apiKey: String
): Boolean = clientType != ClientType.MISTRAL || apiKey.isNotBlank()
