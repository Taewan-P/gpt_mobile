package dev.chungjungsoo.gptmobile.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface SecretVault {
    suspend fun put(secretRef: String, secret: ByteArray)
    suspend fun read(secretRef: String): ByteArray?
    suspend fun delete(secretRef: String)
}

class SecretVaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

class AndroidSecretVault private constructor(
    private val directory: File,
    private val keyAlias: String
) : SecretVault {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        directory = File(context.noBackupFilesDir, DEFAULT_DIRECTORY_NAME),
        keyAlias = DEFAULT_KEY_ALIAS
    )

    internal constructor(context: Context, keyAlias: String, directoryName: String) : this(
        directory = File(context.noBackupFilesDir, directoryName),
        keyAlias = keyAlias
    )

    private val mutex = Mutex()

    override suspend fun put(secretRef: String, secret: ByteArray) = withContext(Dispatchers.IO) {
        requireValidReference(secretRef)
        require(secret.size <= MAX_SECRET_BYTES) { "Secret exceeds the vault size limit." }

        mutex.withLock {
            try {
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                cipher.bindTo(secretRef)
                val ciphertext = cipher.doFinal(secret)
                val iv = cipher.iv
                try {
                    writeRecord(secretRef, iv, ciphertext)
                } finally {
                    iv.fill(0)
                    ciphertext.fill(0)
                }
            } catch (error: SecretVaultException) {
                throw error
            } catch (error: Exception) {
                throw SecretVaultException("Unable to store the credential.", error)
            }
        }
    }

    override suspend fun read(secretRef: String): ByteArray? = withContext(Dispatchers.IO) {
        requireValidReference(secretRef)

        mutex.withLock {
            val atomicFile = AtomicFile(recordFile(secretRef))

            try {
                val record = try {
                    atomicFile.openRead().use { input -> input.readBytes() }
                } catch (_: FileNotFoundException) {
                    return@withLock null
                }
                try {
                    val (iv, ciphertext) = decodeRecord(record)
                    try {
                        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                        val key = getExistingKey() ?: run {
                            atomicFile.delete()
                            return@withLock null
                        }
                        try {
                            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                        } catch (_: KeyPermanentlyInvalidatedException) {
                            atomicFile.delete()
                            return@withLock null
                        }
                        cipher.bindTo(secretRef)
                        cipher.doFinal(ciphertext).also { plaintext ->
                            if (plaintext.size > MAX_SECRET_BYTES) {
                                plaintext.fill(0)
                                throw SecretVaultException("Credential record is invalid.")
                            }
                        }
                    } finally {
                        iv.fill(0)
                        ciphertext.fill(0)
                    }
                } finally {
                    record.fill(0)
                }
            } catch (error: SecretVaultException) {
                throw error
            } catch (error: Exception) {
                throw SecretVaultException("Unable to read the credential.", error)
            }
        }
    }

    override suspend fun delete(secretRef: String) = withContext(Dispatchers.IO) {
        requireValidReference(secretRef)
        mutex.withLock { AtomicFile(recordFile(secretRef)).delete() }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun getExistingKey(): SecretKey? = KeyStore.getInstance(KEYSTORE_PROVIDER)
        .apply { load(null) }
        .getKey(keyAlias, null) as? SecretKey

    private fun writeRecord(secretRef: String, iv: ByteArray, ciphertext: ByteArray) {
        if (iv.size !in MIN_IV_BYTES..MAX_IV_BYTES || ciphertext.size !in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES) {
            throw SecretVaultException("Credential record is invalid.")
        }

        val record = ByteBuffer.allocate(HEADER_BYTES + iv.size + ciphertext.size)
            .put(MAGIC)
            .put(RECORD_VERSION)
            .put(iv.size.toByte())
            .putInt(ciphertext.size)
            .put(iv)
            .put(ciphertext)
            .array()
        val atomicFile = AtomicFile(recordFile(secretRef))
        directory.mkdirs()
        val output = atomicFile.startWrite()
        try {
            output.write(record)
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        } finally {
            record.fill(0)
        }
    }

    private fun decodeRecord(record: ByteArray): Pair<ByteArray, ByteArray> {
        if (record.size !in MIN_RECORD_BYTES..MAX_RECORD_BYTES) {
            throw SecretVaultException("Credential record is invalid.")
        }
        val buffer = ByteBuffer.wrap(record)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        if (!magic.contentEquals(MAGIC) || buffer.get() != RECORD_VERSION) {
            throw SecretVaultException("Credential record is invalid.")
        }
        val ivSize = buffer.get().toInt() and 0xff
        val ciphertextSize = buffer.int
        if (ivSize !in MIN_IV_BYTES..MAX_IV_BYTES ||
            ciphertextSize !in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES ||
            buffer.remaining() != ivSize + ciphertextSize
        ) {
            throw SecretVaultException("Credential record is invalid.")
        }
        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(ciphertextSize).also(buffer::get)
        return iv to ciphertext
    }

    private fun recordFile(secretRef: String): File = File(directory, "$secretRef.vault")

    private fun Cipher.bindTo(secretRef: String) {
        val reference = secretRef.encodeToByteArray()
        try {
            updateAAD(reference)
        } finally {
            reference.fill(0)
        }
    }

    private fun requireValidReference(secretRef: String) {
        require(SECRET_REF_PATTERN.matches(secretRef)) { "Invalid credential reference." }
    }

    companion object {
        internal const val MAX_SECRET_BYTES = 64 * 1024
        private const val DEFAULT_DIRECTORY_NAME = "secret-vault"
        private const val DEFAULT_KEY_ALIAS = "gpt-mobile-secret-vault-v1"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val MIN_IV_BYTES = 12
        private const val MAX_IV_BYTES = 32
        private const val MIN_CIPHERTEXT_BYTES = GCM_TAG_BITS / 8
        private const val MAX_CIPHERTEXT_BYTES = MAX_SECRET_BYTES + GCM_TAG_BITS / 8
        private const val HEADER_BYTES = 10
        private const val MIN_RECORD_BYTES = HEADER_BYTES + MIN_IV_BYTES + MIN_CIPHERTEXT_BYTES
        private const val MAX_RECORD_BYTES = HEADER_BYTES + MAX_IV_BYTES + MAX_CIPHERTEXT_BYTES
        private const val RECORD_VERSION: Byte = 1
        private val MAGIC = byteArrayOf(0x47, 0x50, 0x54, 0x56)
        private val SECRET_REF_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
    }
}
