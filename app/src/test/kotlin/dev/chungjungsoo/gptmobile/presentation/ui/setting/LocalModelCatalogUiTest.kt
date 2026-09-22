package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.work.Data
import androidx.work.WorkInfo
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.worker.LocalModelDownloadWorker
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelCatalogUiTest {
    @Test
    fun replacementWhileOldArtifactReady_showsProgressWithTargetSize() {
        val entry = CatalogEntry(id = "model", sizeInBytes = 100)
        val retained = LocalModel("model", "hash", "old.litertlm", "models/model/hash", 100, LocalModelStatus.READY)
        val progress = Data.Builder()
            .putLong(LocalModelDownloadWorker.KEY_TOTAL_BYTES, 200)
            .putLong(LocalModelDownloadWorker.KEY_RECEIVED_BYTES, 50)
            .build()
        val work = WorkInfo(UUID.randomUUID(), WorkInfo.State.RUNNING, emptySet(), progress = progress)

        val item = toLocalModelListItem(entry, retained, work)

        assertEquals(LocalModelItemStatus.DOWNLOADING, item.status)
        assertEquals(200L, item.downloadSizeBytes)
        assertEquals(50L, item.receivedBytes)
    }

    @Test
    fun failedReplacement_preservesReadyModel() {
        val retained = LocalModel("model", "hash", "old.litertlm", "models/model/hash", 100, LocalModelStatus.READY)
        val work = WorkInfo(UUID.randomUUID(), WorkInfo.State.FAILED, emptySet())

        assertEquals(LocalModelItemStatus.READY, toLocalModelListItem(CatalogEntry(id = "model"), retained, work).status)
    }
}
