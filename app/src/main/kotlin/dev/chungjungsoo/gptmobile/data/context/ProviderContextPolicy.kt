package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.model.ClientType

data class ProviderContextPolicy(
    val recentTurnWindow: Int,
    val historicalImageTurnWindow: Int,
    val maxInlineAttachmentBytes: Long? = null,
    val preferProviderFileRefs: Boolean = false,
    val maxHistoryCharBudget: Int = DEFAULT_MAX_HISTORY_CHAR_BUDGET
) {
    companion object {
        const val UNBOUNDED_TURN_WINDOW = Int.MAX_VALUE
        const val UNBOUNDED_CHAR_BUDGET = Int.MAX_VALUE
        const val DEFAULT_MAX_HISTORY_CHAR_BUDGET = 32_000
        private const val INLINE_ATTACHMENT_LIMIT_BYTES = 12L * 1024 * 1024

        fun forClientType(clientType: ClientType): ProviderContextPolicy = when (clientType) {
            ClientType.OPENAI -> {
                ProviderContextPolicy(
                    recentTurnWindow = 10,
                    historicalImageTurnWindow = 10,
                    preferProviderFileRefs = true,
                    maxHistoryCharBudget = 64_000
                )
            }

            ClientType.ANTHROPIC -> {
                ProviderContextPolicy(
                    recentTurnWindow = 10,
                    historicalImageTurnWindow = 10,
                    preferProviderFileRefs = true,
                    maxHistoryCharBudget = 64_000
                )
            }

            ClientType.GOOGLE -> {
                ProviderContextPolicy(
                    recentTurnWindow = 10,
                    historicalImageTurnWindow = 10,
                    preferProviderFileRefs = true,
                    maxHistoryCharBudget = 64_000
                )
            }

            ClientType.GROQ -> {
                ProviderContextPolicy(
                    recentTurnWindow = 8,
                    historicalImageTurnWindow = 0,
                    maxInlineAttachmentBytes = INLINE_ATTACHMENT_LIMIT_BYTES,
                    maxHistoryCharBudget = 24_000
                )
            }

            ClientType.NVIDIA, ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM, ClientType.LLAMA, ClientType.FREE -> {
                ProviderContextPolicy(
                    recentTurnWindow = 6,
                    historicalImageTurnWindow = 0,
                    maxInlineAttachmentBytes = INLINE_ATTACHMENT_LIMIT_BYTES,
                    maxHistoryCharBudget = 20_000
                )
            }

            ClientType.LITERT_LM -> {
                ProviderContextPolicy(
                    recentTurnWindow = UNBOUNDED_TURN_WINDOW,
                    historicalImageTurnWindow = UNBOUNDED_TURN_WINDOW,
                    maxHistoryCharBudget = UNBOUNDED_CHAR_BUDGET
                )
            }
        }
    }
}
