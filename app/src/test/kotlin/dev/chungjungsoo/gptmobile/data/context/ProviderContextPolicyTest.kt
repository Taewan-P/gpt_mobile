package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.AttachmentUploadCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderContextPolicyTest {
    @Test
    fun `official openai has no inline attachment cap`() {
        listOf(ModelConstants.OPENAI_API_URL, ModelConstants.OPENAI_API_URL.trimEnd('/')).forEach { apiUrl ->
            assertNull(apiUrl, inlineCap(ClientType.OPENAI, apiUrl))
        }
    }

    @Test
    fun `openai profile on another host gets the inline attachment cap`() {
        assertEquals(
            AttachmentUploadCoordinator.MAX_SAFE_INLINE_BYTES,
            inlineCap(ClientType.OPENAI, "https://integrate.api.nvidia.com/v1")
        )
    }

    @Test
    fun `chat completions providers keep the inline attachment cap`() {
        listOf(ClientType.GROQ, ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM).forEach { clientType ->
            assertEquals(
                clientType.name,
                AttachmentUploadCoordinator.MAX_SAFE_INLINE_BYTES,
                inlineCap(clientType, ModelConstants.defaultApiUrl(clientType))
            )
        }
    }

    @Test
    fun `anthropic google and local profiles have no inline attachment cap`() {
        listOf(ClientType.ANTHROPIC, ClientType.GOOGLE, ClientType.LITERT_LM).forEach { clientType ->
            assertNull(clientType.name, inlineCap(clientType, ModelConstants.defaultApiUrl(clientType)))
        }
    }

    private fun inlineCap(clientType: ClientType, apiUrl: String): Long? = ProviderContextPolicy.forPlatform(
        PlatformV2(name = "platform", compatibleType = clientType, apiUrl = apiUrl, model = "model")
    ).maxInlineAttachmentBytes
}
