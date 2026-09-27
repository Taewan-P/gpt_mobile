package dev.chungjungsoo.gptmobile.data.huggingface

import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFacePackageCompatibilityTest {
    @Test
    fun excludesRawArchivesCpuOnlyAndUnidentifiedExports() {
        assertFalse(isSupportedHubPackage("google/model", "weights.zip", emptyList()))
        assertFalse(isSupportedHubPackage("google/model", "model_cpu.litertlm", emptyList()))
        assertFalse(isSupportedHubPackage("unknown/model", "model.litertlm", emptyList()))
        assertTrue(isSupportedHubPackage("litert-community/model", "model.litertlm", emptyList()))
        assertTrue(isSupportedHubPackage("publisher/model", "model_gpu.litertlm", emptyList()))
    }

    @Test
    fun excludesNonChatAndNonAndroidContainers() {
        assertFalse(isSupportedHubPackage("litert-community/model", "model-web.litertlm", emptyList()))
        assertFalse(isSupportedHubPackage("litert-community/model", "model_intel_LNL.litertlm", emptyList()))
        assertFalse(isSupportedHubPackage("litert-community/EmbeddingGemma", "model.litertlm", emptyList()))
        assertFalse(isSupportedHubPackage("google/model", "model.litertlm", listOf("feature-extraction")))
        assertTrue(isSupportedHubPackage("litert-community/Qwen3-0.6B", "Qwen3-0.6B.litertlm", listOf("text-generation")))
    }

    @Test
    fun qualcommExportOnlyMatchesCompiledSoc() {
        val entry = HuggingFaceLiteRtResult("publisher/model", "abc", "model_sm8750.litertlm", 500_000_000, 1, false, emptyList()).toCatalogEntry()
        assertTrue(LocalAccelerators.isNpuEligible(entry.supportedAccelerators, entry.socToModelFiles, "SM8750"))
        assertFalse(LocalAccelerators.isNpuEligible(entry.supportedAccelerators, entry.socToModelFiles, "SM8650"))
        assertFalse("gpu" in entry.supportedAccelerators)
        assertFalse(isSupportedHubPackage("publisher/model", "model_npu.litertlm", emptyList()))
    }
}
