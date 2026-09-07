package dev.chungjungsoo.gptmobile.data.huggingface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceOAuthConfigTest {

    @Test
    fun `placeholder client id is not configured`() {
        assertFalse(
            HuggingFaceOAuthConfig.isConfigured(
                clientId = "REPLACE_WITH_YOUR_CLIENT_ID_IN_HUGGINGFACE_APP",
                redirectUri = "dev.chungjungsoo.gptmobile://oauth/huggingface"
            )
        )
    }

    @Test
    fun `placeholder redirect uri is not configured`() {
        assertFalse(
            HuggingFaceOAuthConfig.isConfigured(
                clientId = "real-client-id",
                redirectUri = "REPLACE_WITH_YOUR_REDIRECT_URI_IN_HUGGINGFACE_APP"
            )
        )
    }

    @Test
    fun `PLACEHOLDER literals are not configured`() {
        assertFalse(HuggingFaceOAuthConfig.isConfigured(clientId = "PLACEHOLDER", redirectUri = "https://example/callback"))
        assertFalse(HuggingFaceOAuthConfig.isConfigured(clientId = "real-client-id", redirectUri = "PLACEHOLDER"))
    }

    @Test
    fun `blank values are not configured`() {
        assertFalse(HuggingFaceOAuthConfig.isConfigured(clientId = " ", redirectUri = "dev.chungjungsoo.gptmobile://oauth/huggingface"))
        assertFalse(HuggingFaceOAuthConfig.isConfigured(clientId = "real-client-id", redirectUri = ""))
    }

    @Test
    fun `registered client id and redirect uri are configured`() {
        assertTrue(
            HuggingFaceOAuthConfig.isConfigured(
                clientId = "hf-app-client-id",
                redirectUri = "dev.chungjungsoo.gptmobile://oauth/huggingface"
            )
        )
    }

    @Test
    fun `oauth endpoints and scope match Hugging Face`() {
        assertEquals("https://huggingface.co/oauth/authorize", HuggingFaceOAuthConfig.AUTHORIZATION_ENDPOINT)
        assertEquals("https://huggingface.co/oauth/token", HuggingFaceOAuthConfig.TOKEN_ENDPOINT)
        assertEquals("read-repos", HuggingFaceOAuthConfig.SCOPE)
    }
}
