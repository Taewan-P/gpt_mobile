package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.model.ClientType

data class ProviderContextPolicy(
    val recentTurnWindow: Int,
    val historicalImageTurnWindow: Int,
    val maxInlineAttachmentBytes: Long? = null,
    val preferProviderFileRefs: Boolean = false
) {
    companion object {
        private const val INLINE_ATTACHMENT_LIMIT_BYTES = 12L * 1024 * 1024

        fun forClientType(clientType: ClientType): ProviderContextPolicy = when (clientType) {
            ClientType.OPENAI -> {
                ProviderContextPolicy(
                    recentTurnWindow = 10,
                    historicalImageTurnWindow = 10,
                    preferProviderFileRefs = true
                )
            }

            ClientType.ANTHROPIC -> {
                ProviderContextPolicy(
                    recentTurnWindow = 10,
                    historicalImageTurnWindow = 10,
                    preferProviderFileRefs = true
                )
            }

            ClientType.GOOGLE -> {
                ProviderContextPolicy(
                    recentTurnWindow = 10,
                    historicalImageTurnWindow = 10,
                    preferProviderFileRefs = true
                )
            }

            ClientType.GROQ -> {
                ProviderContextPolicy(
                    recentTurnWindow = 8,
                    historicalImageTurnWindow = 0,
                    maxInlineAttachmentBytes = INLINE_ATTACHMENT_LIMIT_BYTES
                )
            }

            ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM -> {
                ProviderContextPolicy(
                    recentTurnWindow = 6,
                    historicalImageTurnWindow = 0,
                    maxInlineAttachmentBytes = INLINE_ATTACHMENT_LIMIT_BYTES
                )
            }
        }
    }
}
