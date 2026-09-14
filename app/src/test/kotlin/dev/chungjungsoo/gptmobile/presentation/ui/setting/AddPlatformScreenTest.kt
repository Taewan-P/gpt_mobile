package dev.chungjungsoo.gptmobile.presentation.ui.setting

import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddPlatformScreenTest {
    @Test
    fun `Mistral save gate requires v1 URL and key`() {
        assertFalse(canSavePlatform(false, ClientType.MISTRAL, "Mistral", "https://api.mistral.ai/", "secret", "mistral-large-latest", false))
        assertFalse(canSavePlatform(false, ClientType.MISTRAL, "Mistral", "https://api.mistral.ai/v1/", "", "mistral-large-latest", false))
        assertTrue(canSavePlatform(false, ClientType.MISTRAL, "Mistral", "https://api.mistral.ai/v1/", "secret", "mistral-large-latest", false))
    }

    @Test
    fun `Custom save gate keeps API key optional`() {
        assertTrue(canSavePlatform(false, ClientType.CUSTOM, "Custom", "https://example.com/v1/", "", "model", false))
    }
}
