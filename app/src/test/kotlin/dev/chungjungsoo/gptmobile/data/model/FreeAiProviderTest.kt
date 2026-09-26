package dev.chungjungsoo.gptmobile.data.model

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiProviderTest {
    @Test
    fun `imported free routes exclude memory without excluding ordinary paid or local models`() {
        val profile = PlatformV2(name = "Test", compatibleType = ClientType.CUSTOM)
        listOf("kilo-auto/free", "openrouter/free", "some-model:free").forEach {
            assertTrue(profile.copy(model = it).excludesMemory())
        }
        FreeAiProvider.entries.forEach { provider ->
            assertTrue(provider.applyTo(profile).excludesMemory())
            assertTrue(profile.copy(apiUrl = provider.apiUrl, model = provider.model).excludesMemory())
        }
        assertFalse(profile.copy(model = "paid-model").excludesMemory())
        assertFalse(profile.copy(compatibleType = ClientType.LITERT_LM, model = "gemma3").excludesMemory())
    }

    @Test
    fun `preset application removes credentials and paid settings but preserves profile identity`() {
        val profile = PlatformV2(name = "My profile", model = "paid", token = "secret", secretRef = "vault", maxTokens = 9000, batchMode = true)
        val free = FreeAiProvider.KILO.applyTo(profile)
        assertEquals(profile.uid, free.uid)
        assertEquals("Free Models", free.name)
        assertEquals(FreeAiProvider.KILO.model, free.model)
        assertEquals(2048, free.maxTokens)
        assertNull(free.token)
        assertNull(free.secretRef)
        assertFalse(free.batchMode)
    }

    @Test
    fun `only exact approved endpoints resolve to Free providers`() {
        assertEquals(FreeAiProvider.KILO, FreeAiProvider.fromApiUrl(" ${FreeAiProvider.KILO.apiUrl}/ "))
        assertEquals(FreeAiProvider.KILO, FreeAiProvider.fromApiUrl(FreeAiProvider.KILO.chatCompletionsUrl))
        assertNull(FreeAiProvider.fromApiUrl("${FreeAiProvider.KILO.apiUrl}.example"))
        assertNull(FreeAiProvider.fromApiUrl("http://api.kilo.ai/api/gateway"))
    }
}
