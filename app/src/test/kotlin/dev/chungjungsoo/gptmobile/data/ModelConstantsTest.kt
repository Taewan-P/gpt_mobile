package dev.chungjungsoo.gptmobile.data

import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.util.getClientTypeDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelConstantsTest {
    @Test
    fun `mistral defaults and display mapping`() {
        assertEquals("Mistral", ModelConstants.defaultPlatformName(ClientType.MISTRAL))
        assertEquals("https://api.mistral.ai/v1/", ModelConstants.defaultApiUrl(ClientType.MISTRAL))
        assertEquals("mistral-large-latest", ModelConstants.defaultModel(ClientType.MISTRAL))
        assertEquals("Mistral", getClientTypeDisplayName(ClientType.MISTRAL))
    }
}
