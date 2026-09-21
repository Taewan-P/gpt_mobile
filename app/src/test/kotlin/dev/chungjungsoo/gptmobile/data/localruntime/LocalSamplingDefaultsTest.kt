package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.catalog.CatalogDefaultConfig
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalSamplingDefaultsTest {
    @Test
    fun `prefers GPU when the catalog lists it`() {
        val defaults = localSamplingDefaults(
            CatalogEntry(
                id = "gemma3-1b-it",
                supportedAccelerators = listOf("cpu", "gpu"),
                defaultConfig = CatalogDefaultConfig(topK = 64, topP = 0.95f, temperature = 1.0f, maxTokens = 1024)
            )
        )

        assertEquals(64, defaults.topK)
        assertEquals(0.95f, defaults.topP)
        assertEquals(1.0f, defaults.temperature)
        assertEquals(1024, defaults.maxTokens)
        assertEquals(LocalAccelerators.GPU, defaults.accelerator)
    }

    @Test
    fun `falls back to the first eligible accelerator`() {
        val defaults = localSamplingDefaults(
            entry = CatalogEntry(
                supportedAccelerators = listOf("npu", "cpu"),
                socToModelFiles = mapOf("SM8650" to SocVariant(modelFile = "npu.litertlm"))
            ),
            deviceSocModel = "SM8650"
        )

        assertEquals(LocalAccelerators.NPU, defaults.accelerator)
    }

    @Test
    fun `does not default to NPU when the device has no SOC variant`() {
        val defaults = localSamplingDefaults(
            CatalogEntry(supportedAccelerators = listOf("npu", "cpu"))
        )

        assertEquals(LocalAccelerators.CPU, defaults.accelerator)
    }
}
