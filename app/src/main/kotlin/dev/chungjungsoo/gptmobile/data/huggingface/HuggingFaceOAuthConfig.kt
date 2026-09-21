package dev.chungjungsoo.gptmobile.data.huggingface

object HuggingFaceOAuthConfig {
    const val AUTHORIZATION_ENDPOINT = "https://huggingface.co/oauth/authorize"
    const val TOKEN_ENDPOINT = "https://huggingface.co/oauth/token"
    const val SCOPE = "read-repos"

    fun isConfigured(clientId: String, redirectUri: String): Boolean = isUsableCredential(clientId) && isUsableCredential(redirectUri)

    private fun isUsableCredential(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.equals("PLACEHOLDER", ignoreCase = true)) return false
        return !trimmed.contains("REPLACE_WITH", ignoreCase = true)
    }
}
