package dev.chungjungsoo.gptmobile.data.localmodel

import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalModelStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private val path = "models/model/hash/model.litertlm"

    @Test
    fun legacyModel_migratesVerifiedBytesAndRemovesOnlySource() = runTest {
        val internal = temporary.newFolder("internal")
        val external = temporary.newFolder("external")
        val source = File(external, path).apply {
            parentFile?.mkdirs()
            writeText("model-data")
        }
        val storage = LocalModelStorage(internal, external)
        assertEquals(source, storage.find(path))

        val result = storage.migrate(path, 10)

        assertEquals(File(internal, path), result)
        assertEquals("model-data", result?.readText())
        assertFalse(source.exists())
        assertEquals(setOf(path), storage.files())
    }

    @Test
    fun wrongSize_preservesLegacyFile() = runTest {
        val internal = temporary.newFolder("internal")
        val external = temporary.newFolder("external")
        val source = File(external, path).apply {
            parentFile?.mkdirs()
            writeText("model-data")
        }
        try {
            LocalModelStorage(internal, external).migrate(path, 99)
            org.junit.Assert.fail("Expected failed verification")
        } catch (_: IOException) {
            assertTrue(source.exists())
            assertFalse(File(internal, path).exists())
        }
    }

    @Test
    fun unavailableExternalStorage_keepsInternalModelResolvable() {
        val internal = temporary.newFolder("internal")
        val model = File(internal, path).apply {
            parentFile?.mkdirs()
            writeText("model")
        }
        assertEquals(model, LocalModelStorage(internal, null).find(path))
    }

    @Test
    fun incompleteInternalFile_doesNotHideValidLegacyModel() {
        val internal = temporary.newFolder("internal")
        val external = temporary.newFolder("external")
        File(internal, path).apply {
            parentFile?.mkdirs()
            writeText("bad")
        }
        val source = File(external, path).apply {
            parentFile?.mkdirs()
            writeText("model-data")
        }
        assertEquals(source, LocalModelStorage(internal, external).find(path, 10))
    }

    @Test
    fun completedMigration_removesLegacyDuplicateAndDeleteCoversBothRoots() = runTest {
        val internal = temporary.newFolder("internal")
        val external = temporary.newFolder("external")
        val destination = File(internal, path).apply {
            parentFile?.mkdirs()
            writeText("model-data")
        }
        val source = File(external, path).apply {
            parentFile?.mkdirs()
            writeText("model-data")
        }
        val storage = LocalModelStorage(internal, external)
        assertEquals(destination, storage.migrate(path, 10))
        assertFalse(source.exists())
        source.writeText("model-data")
        assertEquals(setOf(path), storage.files())
        storage.delete(path)
        assertFalse(source.exists())
        assertFalse(destination.exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun traversalPath_isRejected() {
        LocalModelStorage(temporary.root, null).find("models/../../secret")
    }
}
