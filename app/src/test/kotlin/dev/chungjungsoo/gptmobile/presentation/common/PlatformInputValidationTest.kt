package dev.chungjungsoo.gptmobile.presentation.common

import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformInputValidationTest {
    @Test
    fun `Mistral requires a trimmed URL ending with v1 slash`() {
        assertTrue(isPlatformApiUrlValid(ClientType.MISTRAL, " https://api.mistral.ai/v1/ "))
        assertFalse(isPlatformApiUrlValid(ClientType.MISTRAL, "https://api.mistral.ai/"))
        assertFalse(isPlatformApiUrlValid(ClientType.MISTRAL, "https://api.mistral.ai/v1"))
    }

    @Test
    fun `Mistral requires a nonblank key while OpenAI remains optional`() {
        assertFalse(isPlatformApiKeyValid(ClientType.MISTRAL, "   "))
        assertTrue(isPlatformApiKeyValid(ClientType.MISTRAL, "secret"))
        assertTrue(isPlatformApiKeyValid(ClientType.OPENAI, ""))
    }
}
