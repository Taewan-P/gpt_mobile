package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalEngineMaxTokensTest {
    @Test
    fun `compiled GPU context wins over high RAM profile setting`() {
        assertEquals(
            1280,
            resolvedEngineMaxTokens(
                requestedMaxTokens = 8192,
                accelerator = LocalAccelerators.GPU,
                entry = CatalogEntry(maxContextTokens = 1280),
                deviceSocModel = "SM8750",
                deviceRamGb = 16L
            )
        )
    }

    @Test
    fun `NPU clamps requested max tokens to the matching SOC variant context`() {
        val maxTokens = resolvedEngineMaxTokens(
            requestedMaxTokens = 4096,
            accelerator = LocalAccelerators.NPU,
            entry = npuEntry(),
            deviceSocModel = "SM8750"
        )

        assertEquals(1280, maxTokens)
    }

    @Test
    fun `NPU still clamps when the profile max tokens is already below the catalog default`() {
        val maxTokens = resolvedEngineMaxTokens(
            requestedMaxTokens = 2048,
            accelerator = LocalAccelerators.NPU,
            entry = npuEntry(),
            deviceSocModel = "Tensor G5"
        )

        assertEquals(1280, maxTokens)
    }

    @Test
    fun `NPU keeps a requested limit that is already inside the variant context`() {
        val maxTokens = resolvedEngineMaxTokens(
            requestedMaxTokens = 512,
            accelerator = LocalAccelerators.NPU,
            entry = npuEntry(),
            deviceSocModel = "SM8750"
        )

        assertEquals(512, maxTokens)
    }

    @Test
    fun `GPU does not clamp to the NPU variant context`() {
        val maxTokens = resolvedEngineMaxTokens(
            requestedMaxTokens = 4096,
            accelerator = LocalAccelerators.GPU,
            entry = npuEntry(),
            deviceSocModel = "SM8750"
        )

        assertEquals(4096, maxTokens)
    }

    @Test
    fun `GPU on high RAM device allows expanded context up to 8192 tokens`() {
        val maxTokens = resolvedEngineMaxTokens(
            requestedMaxTokens = 8192,
            accelerator = LocalAccelerators.GPU,
            entry = npuEntry(),
            deviceSocModel = "SM8750",
            deviceRamGb = 16L
        )

        assertEquals(8192, maxTokens)
    }

    @Test
    fun `GPU on high RAM device clamps when requested tokens exceed high RAM limit`() {
        val maxTokens = resolvedEngineMaxTokens(
            requestedMaxTokens = 16384,
            accelerator = LocalAccelerators.GPU,
            entry = npuEntry(),
            deviceSocModel = "SM8750",
            deviceRamGb = 16L
        )

        assertEquals(MAX_HIGH_RAM_CONTEXT_TOKENS, maxTokens)
    }

    @Test
    fun `NPU on high RAM device still respects hardware NPU context clamp`() {
        val maxTokens = resolvedEngineMaxTokens(
            requestedMaxTokens = 8192,
            accelerator = LocalAccelerators.NPU,
            entry = npuEntry(),
            deviceSocModel = "SM8750",
            deviceRamGb = 16L
        )

        assertEquals(1280, maxTokens)
    }

    @Test
    fun `NPU without a matching variant uses the requested max tokens`() {
        val maxTokens = resolvedEngineMaxTokens(
            requestedMaxTokens = 4096,
            accelerator = LocalAccelerators.NPU,
            entry = npuEntry(),
            deviceSocModel = "Tensor G4"
        )

        assertEquals(4096, maxTokens)
    }

    @Test
    fun `low and mid RAM limits are bounded and invalid input is normalized`() {
        assertEquals(1024, resolvedEngineMaxTokens(32768, "gpu", null, "", 4))
        assertEquals(4096, resolvedEngineMaxTokens(32768, "gpu", null, "", 8))
        assertEquals(1024, resolvedEngineMaxTokens(0, "cpu", null, "", 16))
        assertEquals(1024, resolvedEngineMaxTokens(-1, "cpu", null, "", 16))
    }

    private fun npuEntry() = CatalogEntry(
        id = "gemma3-1b-it",
        supportedAccelerators = listOf("gpu", "cpu", "npu"),
        socToModelFiles = mapOf(
            "SM8750" to SocVariant(modelFile = "npu-sm8750.litertlm", contextSize = 1280),
            "Tensor G5" to SocVariant(modelFile = "npu-tensor-g5.litertlm", contextSize = 1280)
        )
    )
}
