package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType

data class ProviderContextPolicy(
    val maxInlineAttachmentBytes: Long? = null
) {
    companion object {
        private const val INLINE_ATTACHMENT_LIMIT_BYTES = 12L * 1024 * 1024

        fun forPlatform(platform: PlatformV2): ProviderContextPolicy = when {
            platform.compatibleType == ClientType.OPENAI && ModelConstants.isOfficialOpenAiApiUrl(platform.apiUrl) ->
                ProviderContextPolicy()

            platform.compatibleType == ClientType.GROQ ||
                platform.compatibleType == ClientType.OLLAMA ||
                platform.compatibleType == ClientType.OPENROUTER ||
                platform.compatibleType == ClientType.CUSTOM ||
                platform.compatibleType == ClientType.OPENAI ->
                ProviderContextPolicy(maxInlineAttachmentBytes = INLINE_ATTACHMENT_LIMIT_BYTES)

            else -> ProviderContextPolicy()
        }
    }
}
