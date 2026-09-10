package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.database.dao.LocalModelDao
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalModelFileReplacementTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun replacementStoredInternally_removesLegacyOnlyAfterRecordSwitch() = runTest {
        val internal = temporary.newFolder("internal")
        val legacy = temporary.newFolder("external")
        val previous = File(legacy, "models/model/hash/old.litertlm").apply {
            parentFile?.mkdirs()
            writeText("old")
        }
        val partial = File(internal, "models/model/hash/new.litertlm.part").apply {
            parentFile?.mkdirs()
            writeText("new!")
        }
        val staleInternal = File(internal, "models/model/hash/old.litertlm").apply { writeText("x") }
        val dao = RecordingDao(storedModel()) { assertTrue(previous.exists()) }

        commitLocalModelDownload(internal, dao, "model", target(), partial, legacyRoot = legacy)

        assertFalse(previous.exists())
        assertFalse(staleInternal.exists())
        assertEquals("new!", File(internal, "models/model/hash/new.litertlm").readText())
        assertEquals("new.litertlm", dao.model.fileName)
    }

    @Test
    fun verifiedReplacement_switchesRecordBeforeDeletingOldArtifact() = runTest {
        val root = temporary.root
        val old = storedModel()
        val previous = File(root, "models/model/hash/old.litertlm").apply {
            parentFile.mkdirs()
            writeText("old")
        }
        val partial = File(previous.parentFile, "new.litertlm.part").apply { writeText("new!") }
        val dao = RecordingDao(old) { assertTrue(previous.exists()) }

        commitLocalModelDownload(root, dao, "model", target(), partial)

        assertEquals("new.litertlm", dao.model.fileName)
        assertEquals(LocalModelStatus.READY, dao.model.status)
        assertFalse(previous.exists())
        assertEquals("new!", File(previous.parentFile, "new.litertlm").readText())
    }

    @Test
    fun incompleteReplacement_keepsPreviousFileAndRecord() = runTest {
        val previous = File(temporary.root, "models/model/hash/old.litertlm").apply {
            parentFile.mkdirs()
            writeText("old")
        }
        val partial = File(previous.parentFile, "new.litertlm.part").apply { writeText("n") }
        val dao = RecordingDao(storedModel())

        try {
            commitLocalModelDownload(temporary.root, dao, "model", target(), partial)
            org.junit.Assert.fail("Expected incomplete download")
        } catch (_: IOException) {
            assertEquals("old", previous.readText())
            assertEquals("old.litertlm", dao.model.fileName)
        }
    }

    @Test
    fun databaseFailure_keepsOldModelUsable() = runTest {
        val previous = File(temporary.root, "models/model/hash/old.litertlm").apply {
            parentFile.mkdirs()
            writeText("old")
        }
        val partial = File(previous.parentFile, "new.litertlm.part").apply { writeText("new!") }
        val dao = RecordingDao(storedModel()) { throw IOException("DB full") }

        try {
            commitLocalModelDownload(temporary.root, dao, "model", target(), partial)
            org.junit.Assert.fail("Expected database failure")
        } catch (_: IOException) {
            assertEquals("old", previous.readText())
            assertEquals("old.litertlm", dao.model.fileName)
        }
    }

    private fun storedModel() = LocalModel("model", "hash", "old.litertlm", "models/model/hash", 3, LocalModelStatus.READY)
    private fun target() = ResolvedModelDownload("new.litertlm", "https://example.com/new", "hash", 4)

    private class RecordingDao(var model: LocalModel, val beforeWrite: () -> Unit = {}) : LocalModelDao {
        override fun observeAll() = flowOf(listOf(model))
        override suspend fun getAll() = listOf(model)
        override suspend fun getById(catalogEntryId: String) = model
        override suspend fun upsert(model: LocalModel) {
            beforeWrite()
            this.model = model
        }
        override suspend fun updateStatus(catalogEntryId: String, status: String, updatedAt: Long) = Unit
        override suspend fun deleteById(catalogEntryId: String) = Unit
    }
}
