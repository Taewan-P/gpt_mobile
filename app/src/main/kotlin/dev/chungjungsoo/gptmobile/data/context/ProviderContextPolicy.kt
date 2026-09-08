package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.model.ClientType

data class ProviderContextPolicy(
    val maxInlineAttachmentBytes: Long? = null
) {
    companion object {
        private const val INLINE_ATTACHMENT_LIMIT_BYTES = 12L * 1024 * 1024

        fun forClientType(clientType: ClientType): ProviderContextPolicy = when (clientType) {
            ClientType.GROQ, ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM ->
                ProviderContextPolicy(maxInlineAttachmentBytes = INLINE_ATTACHMENT_LIMIT_BYTES)

            else -> ProviderContextPolicy()
        }
    }
}
