package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceLiteRtResult
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelPackagesTest {
    private val model = CatalogEntry(
        id = "gemma",
        displayName = "Gemma",
        downloadUrl = "https://huggingface.co/example/gemma/resolve/main/gemma.litertlm",
        supportedAccelerators = listOf("gpu", "cpu", "npu"),
        socToModelFiles = mapOf("SM8750" to SocVariant(modelFile = "gemma_SM8750.litertlm", contextSize = 1280))
    )

    @Test fun gpuDownloadNeverResolvesToNpuEvenOnSupportedSnapdragon() {
        val entries = LocalModelPackages.forDevice(listOf(model), "SM8750-AB")
        assertEquals(2, entries.size)
        val gpu = entries.single { it.id == "gemma-litert" }
        assertEquals("gemma.litertlm", SocVariantResolver.resolveForRuntime(gpu, "SM8750-AB").fileName)
        assertEquals(listOf("gpu", "cpu"), gpu.supportedAccelerators)
        val npu = entries.single { it.id == "gemma" }
        assertEquals("gemma_SM8750.litertlm", SocVariantResolver.resolveForRuntime(npu, "SM8750-AB").fileName)
        assertEquals(listOf("npu"), npu.supportedAccelerators)
    }

    @Test fun unsupportedPhoneSeesOnlyGenericPackages() {
        val entries = LocalModelPackages.forDevice(listOf(model, model.copy(id = "npu-only", supportedAccelerators = listOf("npu"))), "Exynos 2400")
        assertEquals(1, entries.size)
        assertFalse(entries.single().supportedAccelerators.contains("npu"))
        assertEquals("gemma.litertlm", SocVariantResolver.resolveForRuntime(entries.single(), "Exynos 2400").fileName)
    }

    @Test fun hubNpuFilesRequireMatchingChipsetAndNeverAdvertiseGpu() {
        val result = HuggingFaceLiteRtResult("litert-community/Gemma", "revision", "gemma_qualcomm_sm8750_ctx1280.litertlm", 800000000, 100, false, listOf("tool-use"))
        val entry = result.toCatalogEntry()
        assertEquals(listOf("npu"), entry.supportedAccelerators)
        assertTrue(LocalAccelerators.isNpuEligible(entry.supportedAccelerators, entry.socToModelFiles, "SM8750-AB"))
        assertFalse(LocalAccelerators.isNpuEligible(entry.supportedAccelerators, entry.socToModelFiles, "SM8650"))
        assertEquals(1280, SocVariantResolver.resolveForRuntime(entry, "SM8750").contextSize)
        assertTrue(entry.capabilities.tools)
    }

    @Test fun legacyInstalledNpuPackageIsNeverOfferedAsGpu() {
        assertEquals(listOf("npu"), LocalModelPackages.forInstalledFile(model, "gemma_SM8750.litertlm").supportedAccelerators)
        assertEquals(listOf("gpu", "cpu"), LocalModelPackages.forInstalledFile(model, "gemma.litertlm").supportedAccelerators)
    }
}
