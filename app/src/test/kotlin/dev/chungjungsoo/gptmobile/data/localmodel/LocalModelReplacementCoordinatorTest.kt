package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.presentation.ui.setup.wizardCatalogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelReplacementCoordinatorTest {
    @Test
    fun request_queuesUniqueTargetsAndDismissesByIdentity() {
        val coordinator = LocalModelReplacementCoordinator()
        val first = request("npu-model", "https://example/npu", "npu.litertlm", "npu")
        val duplicate = request("npu-model", "https://example/npu", "npu.litertlm", "npu")
        val second = request("npu-model", "https://example/cpu", "cpu.litertlm", "cpu")

        coordinator.request(first.entry, first.target, first.accelerator)
        coordinator.request(duplicate.entry, duplicate.target, duplicate.accelerator)
        coordinator.request(second.entry, second.target, second.accelerator)

        assertEquals(listOf("npu", "cpu"), coordinator.pending.value.map { it.accelerator })

        coordinator.dismiss(first)

        assertEquals(listOf("cpu"), coordinator.pending.value.map { it.accelerator })
    }

    private fun request(
        id: String,
        url: String,
        fileName: String,
        accelerator: String
    ) = LocalModelReplacementRequest(
        entry = wizardCatalogEntry(id),
        target = ResolvedModelDownload(
            fileName = fileName,
            downloadUrl = url,
            commitHash = "abc",
            sizeInBytes = 4_000_000L
        ),
        accelerator = accelerator
    )
}
