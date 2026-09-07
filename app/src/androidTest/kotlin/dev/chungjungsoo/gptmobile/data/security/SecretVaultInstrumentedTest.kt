package dev.chungjungsoo.gptmobile.data.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretVaultInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val suffix = UUID.randomUUID().toString()
    private val keyAlias = "gpt-mobile-test-$suffix"
    private val directoryName = "secret-vault-test-$suffix"
    private val vault = AndroidSecretVault(context, keyAlias, directoryName)

    @After
    fun cleanUp() {
        File(context.noBackupFilesDir, directoryName).deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(keyAlias)
    }

    @Test
    fun roundTripOverwriteAndDelete() = runBlocking {
        val secretRef = "profile_123"
        val first = "first-secret".encodeToByteArray()
        val second = "replacement-secret".encodeToByteArray()

        vault.put(secretRef, first)

        assertArrayEquals(first, vault.read(secretRef))
        assertTrue(File(context.noBackupFilesDir, "$directoryName/$secretRef.vault").isFile)
        assertFalse(File(context.filesDir, "$directoryName/$secretRef.vault").exists())

        vault.put(secretRef, second)
        assertArrayEquals(second, vault.read(secretRef))

        vault.delete(secretRef)
        assertNull(vault.read(secretRef))
    }

    @Test
    fun rejectsUnsafeReferencesAndOversizedSecrets() = runBlocking {
        assertFails<IllegalArgumentException> { vault.put("../secret", byteArrayOf(1)) }
        assertFails<IllegalArgumentException> { vault.read("") }
        assertFails<IllegalArgumentException> {
            vault.put("profile_oversized", ByteArray(AndroidSecretVault.MAX_SECRET_BYTES + 1))
        }
    }

    @Test
    fun rejectsMalformedRecords() = runBlocking {
        val secretRef = "profile_corrupt"
        val record = File(context.noBackupFilesDir, "$directoryName/$secretRef.vault")
        record.parentFile?.mkdirs()
        record.writeBytes(byteArrayOf(1, 2, 3, 4))

        assertFails<SecretVaultException> { vault.read(secretRef) }
    }

    @Test
    fun rejectsRecordMovedToAnotherReference() = runBlocking {
        val firstRef = "profile_first"
        val secondRef = "profile_second"
        vault.put(firstRef, "first-secret".encodeToByteArray())
        vault.put(secondRef, "second-secret".encodeToByteArray())

        val directory = File(context.noBackupFilesDir, directoryName)
        File(directory, "$secondRef.vault").copyTo(File(directory, "$firstRef.vault"), overwrite = true)

        assertFails<SecretVaultException> { vault.read(firstRef) }
    }

    @Test
    fun missingKeystoreKey_discardsIrrecoverableRecord() = runBlocking {
        val secretRef = "profile_missing_key"
        vault.put(secretRef, "secret".encodeToByteArray())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(keyAlias)

        assertNull(vault.read(secretRef))
        assertFalse(File(context.noBackupFilesDir, "$directoryName/$secretRef.vault").exists())
    }

    private suspend inline fun <reified T : Throwable> assertFails(crossinline block: suspend () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue("Expected ${T::class.java.simpleName}, got ${thrown?.javaClass?.simpleName}", thrown is T)
    }
}
