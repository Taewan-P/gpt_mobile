package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelExecutionTargetTest {
    @Test
    fun auto_withMatchingNpuRuntime_ordersCompatibleArtifacts() {
        val targets = localModelExecutionTargets(entry(), "auto", "SM8750", true)

        assertEquals(listOf("npu", "gpu", "cpu"), targets.map { it.accelerator })
        assertEquals(listOf("npu.litertlm", "default.litertlm", "default.litertlm"), targets.map { it.download.fileName })
    }

    @Test
    fun auto_withoutMatchingRuntime_doesNotSelectNpuArtifact() {
        assertEquals(listOf("gpu", "cpu"), localModelExecutionTargets(entry(), "auto", "SM8750", false).map { it.accelerator })
        assertEquals(listOf("gpu", "cpu"), localModelExecutionTargets(entry(), "auto", "unknown", true).map { it.accelerator })
    }

    @Test
    fun manualCpu_doesNotUpgradeOrSelectSocArtifact() {
        val target = localModelExecutionTargets(entry(), "cpu", "SM8750", true).single()

        assertEquals("cpu", target.accelerator)
        assertEquals("default.litertlm", target.download.fileName)
    }

    private fun entry() = CatalogEntry(
        id = "model",
        downloadUrl = "https://huggingface.co/test/model/resolve/hash/default.litertlm",
        supportedAccelerators = listOf("cpu", "gpu", "npu"),
        socToModelFiles = mapOf("SM8750" to SocVariant(modelFile = "npu.litertlm", commitHash = "npu-hash"))
    )
}
