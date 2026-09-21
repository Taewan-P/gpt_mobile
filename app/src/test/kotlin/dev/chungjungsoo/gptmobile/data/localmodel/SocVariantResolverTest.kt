package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocVariantResolverTest {

    @Test
    fun `matching device SOC selects the variant file url and size`() {
        val resolved = SocVariantResolver.resolve(entryWithVariant(), deviceSocModel = "SM8650")

        assertEquals("qwen-sm8650.litertlm", resolved.fileName)
        assertEquals(VARIANT_URL, resolved.downloadUrl)
        assertEquals("abc", resolved.commitHash)
        assertEquals(1_597_931_520L, resolved.sizeInBytes)
    }

    @Test
    fun `SOC match is case insensitive`() {
        val resolved = SocVariantResolver.resolve(entryWithVariant(), deviceSocModel = "sm8650")

        assertEquals("qwen-sm8650.litertlm", resolved.fileName)
        assertEquals(VARIANT_URL, resolved.downloadUrl)
    }

    @Test
    fun `unknown device SOC falls back to the default file`() {
        val resolved = SocVariantResolver.resolve(entryWithVariant(), deviceSocModel = "SM8750")

        assertEquals(DEFAULT_FILE, resolved.fileName)
        assertEquals(DEFAULT_URL, resolved.downloadUrl)
        assertEquals(DEFAULT_COMMIT, resolved.commitHash)
        assertEquals(DEFAULT_SIZE, resolved.sizeInBytes)
    }

    @Test
    fun `blank device SOC falls back to the default file`() {
        val resolved = SocVariantResolver.resolve(entryWithVariant(), deviceSocModel = "")

        assertEquals(DEFAULT_FILE, resolved.fileName)
        assertEquals(DEFAULT_URL, resolved.downloadUrl)
    }

    @Test
    fun `entry without variants always uses the default file`() {
        val resolved = SocVariantResolver.resolve(entryWithoutVariants(), deviceSocModel = "SM8650")

        assertEquals(DEFAULT_FILE, resolved.fileName)
        assertEquals(DEFAULT_URL, resolved.downloadUrl)
        assertEquals(DEFAULT_COMMIT, resolved.commitHash)
        assertEquals(DEFAULT_SIZE, resolved.sizeInBytes)
        assertEquals(0, resolved.contextSize)
    }

    @Test
    fun `matching Tensor G5 selects the variant file size and context`() {
        val resolved = SocVariantResolver.resolve(npuEntry(), deviceSocModel = "Tensor G5")

        assertEquals("Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm", resolved.fileName)
        assertEquals(1_678_542_365L, resolved.sizeInBytes)
        assertEquals(1280, resolved.contextSize)
        assertTrue(resolved.downloadUrl.contains("Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm"))
        assertEquals(GEMMA3_NPU_COMMIT, resolved.commitHash)
    }

    @Test
    fun `matching SM8750 is case insensitive and returns the Qualcomm file`() {
        val resolved = SocVariantResolver.resolve(npuEntry(), deviceSocModel = "sm8750")

        assertEquals("Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm", resolved.fileName)
        assertEquals(689_291_264L, resolved.sizeInBytes)
        assertEquals(1280, resolved.contextSize)
    }

    @Test
    fun `SOC match treats spaces and underscores as equivalent`() {
        val resolved = SocVariantResolver.resolve(npuEntry(), deviceSocModel = "Tensor_G5")

        assertEquals("Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm", resolved.fileName)
        assertEquals(1_678_542_365L, resolved.sizeInBytes)
    }

    @Test
    fun `unknown device SOC including Tensor G4 falls back to the default file`() {
        val resolved = SocVariantResolver.resolve(npuEntry(), deviceSocModel = "Tensor G4")

        assertEquals("gemma3-1b-it-int4.litertlm", resolved.fileName)
        assertEquals(GEMMA3_DEFAULT_URL, resolved.downloadUrl)
        assertEquals("42d538a932e8d5b12e6b3b455f5572560bd60b2c", resolved.commitHash)
        assertEquals(584_417_280L, resolved.sizeInBytes)
        assertEquals(0, resolved.contextSize)
    }

    @Test
    fun `variant without its own URL rewrites the default resolve URL onto the same commit`() {
        val resolved = SocVariantResolver.resolve(
            CatalogEntry(
                id = "gemma3-1b-it",
                downloadUrl = DEFAULT_URL,
                sizeInBytes = DEFAULT_SIZE,
                socToModelFiles = mapOf(
                    "SM8750" to SocVariant(
                        modelFile = "Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm",
                        sizeInBytes = 689_291_264L,
                        contextSize = 1280
                    )
                )
            ),
            deviceSocModel = "SM8750"
        )

        assertEquals("Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm", resolved.fileName)
        assertEquals(DEFAULT_COMMIT, resolved.commitHash)
        assertEquals(
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm?download=true",
            resolved.downloadUrl
        )
        assertEquals(689_291_264L, resolved.sizeInBytes)
        assertEquals(1280, resolved.contextSize)
    }

    private fun npuEntry() = CatalogEntry(
        id = "gemma3-1b-it",
        downloadUrl = GEMMA3_DEFAULT_URL,
        sizeInBytes = 584_417_280L,
        socToModelFiles = mapOf(
            "SM8750" to SocVariant(
                modelFile = "Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/$GEMMA3_NPU_COMMIT/Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm?download=true",
                commitHash = GEMMA3_NPU_COMMIT,
                sizeInBytes = 689_291_264L,
                contextSize = 1280,
                quantization = "q4"
            ),
            "Tensor G5" to SocVariant(
                modelFile = "Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/$GEMMA3_NPU_COMMIT/Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm?download=true",
                commitHash = GEMMA3_NPU_COMMIT,
                sizeInBytes = 1_678_542_365L,
                contextSize = 1280,
                quantization = "q8"
            )
        )
    )

    private fun entryWithVariant() = CatalogEntry(
        id = "qwen2.5-1.5b-instruct",
        downloadUrl = DEFAULT_URL,
        sizeInBytes = DEFAULT_SIZE,
        socToModelFiles = mapOf(
            "SM8650" to SocVariant(
                modelFile = "qwen-sm8650.litertlm",
                downloadUrl = VARIANT_URL,
                commitHash = "abc",
                sizeInBytes = 1_597_931_520L
            )
        )
    )

    private fun entryWithoutVariants() = CatalogEntry(
        id = "gemma3-1b-it",
        downloadUrl = DEFAULT_URL,
        sizeInBytes = DEFAULT_SIZE
    )

    private companion object {
        const val DEFAULT_FILE = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        const val DEFAULT_COMMIT = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614"
        const val DEFAULT_SIZE = 1_597_931_520L
        const val DEFAULT_URL =
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true"
        const val VARIANT_URL =
            "https://huggingface.co/example/qwen/resolve/abc/qwen-sm8650.litertlm?download=true"
        const val GEMMA3_NPU_COMMIT = "6d54daa71cfbffba6b2843c08eeb1a27e7430bf0"
        const val GEMMA3_DEFAULT_URL =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true"
    }
}
