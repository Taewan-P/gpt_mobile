package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.repository.FakeLocalModelRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalModelSelectionTest {
    @Test
    fun `missing download fails preflight without starting a download`() {
        val repo = FakeLocalModelRepository()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.resolveLocalModelSelection("missing", "gpu") }
        }
        assertEquals(emptyList<String>(), repo.startDownloadCalls)
    }

    @Test
    fun `legacy GPU profile resolves the installed GPU edition`() = runBlocking {
        val repo = FakeLocalModelRepository(downloadedPaths = mapOf("gemma" to "/models/gemma-sm8750.litertlm", "gemma-litert" to "/models/gemma-gpu.litertlm"))
        assertEquals("gemma-litert", repo.resolveLocalModelSelection("gemma", "GPU").modelId)
        assertEquals("gemma", repo.resolveLocalModelSelection("gemma", "NPU").modelId)
    }
}
