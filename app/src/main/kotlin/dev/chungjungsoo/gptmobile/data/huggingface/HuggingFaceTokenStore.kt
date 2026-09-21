package dev.chungjungsoo.gptmobile.data.huggingface

import dev.chungjungsoo.gptmobile.data.security.SecretVault
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuggingFaceTokenStore @Inject constructor(
    private val secretVault: SecretVault
) {
    suspend fun saveAccessToken(token: String) {
        secretVault.put(SECRET_REF, token.encodeToByteArray())
    }

    suspend fun readAccessToken(): String? {
        val bytes = secretVault.read(SECRET_REF) ?: return null
        return try {
            bytes.decodeToString().trim().takeIf { it.isNotEmpty() }
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun clear() {
        secretVault.delete(SECRET_REF)
    }

    companion object {
        const val SECRET_REF = "huggingface_oauth"

        fun bearerHeader(token: String): String = "Bearer $token"
    }
}
