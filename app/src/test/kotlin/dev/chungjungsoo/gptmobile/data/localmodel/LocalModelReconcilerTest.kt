package dev.chungjungsoo.gptmobile.data.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelReconcilerTest {

    @Test
    fun `downloaded row whose file is present is left unchanged`() {
        val row = downloadedRow()
        val actions = LocalModelReconciler.reconcile(
            rows = listOf(row),
            diskFiles = setOf(finalPath(row)),
            activeDownloadIds = emptySet()
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `downloaded row whose file is missing is removed`() {
        val row = downloadedRow()
        val actions = LocalModelReconciler.reconcile(
            rows = listOf(row),
            diskFiles = emptySet(),
            activeDownloadIds = emptySet()
        )

        assertEquals(listOf(ReconcileAction.DeleteRow(row.catalogEntryId)), actions)
    }

    @Test
    fun `leftover partial for a failed download is kept so restart can resume`() {
        val row = failedRow()
        val partial = partialPath(row)
        val actions = LocalModelReconciler.reconcile(
            rows = listOf(row),
            diskFiles = setOf(partial),
            activeDownloadIds = emptySet()
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `partial for an active download is kept`() {
        val row = downloadingRow()
        val actions = LocalModelReconciler.reconcile(
            rows = listOf(row),
            diskFiles = setOf(partialPath(row)),
            activeDownloadIds = setOf(row.catalogEntryId)
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `downloading row that is no longer active is marked failed and its partial is kept`() {
        val row = downloadingRow()
        val partial = partialPath(row)
        val actions = LocalModelReconciler.reconcile(
            rows = listOf(row),
            diskFiles = setOf(partial),
            activeDownloadIds = emptySet()
        )

        assertEquals(listOf(ReconcileAction.MarkFailed(row.catalogEntryId)), actions)
    }

    @Test
    fun `user cancel keeps the row and partial file with a failed status`() {
        val plan = LocalModelReconciler.planUserCancel()

        assertEquals(LocalModelStatus.FAILED, plan.newStatus)
        assertFalse(plan.deleteRow)
        assertFalse(plan.deleteFiles)
    }

    @Test
    fun `orphaned partial without a row is deleted when that model is not downloading`() {
        val orphan = "models/smollm/abc123/smollm.litertlm.part"
        val actions = LocalModelReconciler.reconcile(
            rows = emptyList(),
            diskFiles = setOf(orphan),
            activeDownloadIds = emptySet()
        )

        assertEquals(listOf(ReconcileAction.DeleteFile(orphan)), actions)
    }

    @Test
    fun `orphaned complete files without a row are left on disk`() {
        val orphan = "models/smollm/abc123/smollm.litertlm"
        val actions = LocalModelReconciler.reconcile(
            rows = emptyList(),
            diskFiles = setOf(orphan),
            activeDownloadIds = emptySet()
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `active orphaned partial is kept so a resumed worker can continue`() {
        val orphan = "models/smollm/abc123/smollm.litertlm.part"
        val actions = LocalModelReconciler.reconcile(
            rows = emptyList(),
            diskFiles = setOf(orphan),
            activeDownloadIds = setOf("smollm")
        )

        assertTrue(actions.isEmpty())
    }

    private fun downloadedRow() = LocalModelRecord(
        catalogEntryId = "qwen2.5-1.5b-instruct",
        commitHash = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
        fileName = "model.litertlm",
        relativeDirectory = "models/qwen2.5-1.5b-instruct/19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
        status = LocalModelStatus.READY
    )

    private fun downloadingRow() = downloadedRow().copy(status = LocalModelStatus.DOWNLOADING)

    private fun failedRow() = downloadedRow().copy(status = LocalModelStatus.FAILED)

    private fun finalPath(row: LocalModelRecord): String = LocalModelDownloadPaths.relativeFilePath(row.catalogEntryId, row.commitHash, row.fileName)

    private fun partialPath(row: LocalModelRecord): String = LocalModelDownloadPaths.relativePartialFilePath(row.catalogEntryId, row.commitHash, row.fileName)
}
