package dev.chungjungsoo.gptmobile.data.backup

import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated complete-backup crypto.
 *
 * GPTFULL1: legacy password-derived AES-256-GCM.
 * GPTFULL2: legacy restore-only passwordless AES-256-GCM that used an
 * installation-bound app-managed key. Production code must never create this
 * format because it cannot survive reinstall/migration without the old key.
 *
 * Each 1 MiB chunk authenticates its position and the complete header.
 * Decryption always targets staging so a failed authentication never mutates
 * live application data.
 */
internal object CompleteBackupCrypto {
    private val legacyMagic = "GPTFULL1".toByteArray()
    private val passwordlessMagic = "GPTFULL2".toByteArray()
    private const val CHUNK = 1024 * 1024
    private const val LEGACY_HEADER = 40
    private const val PASSWORDLESS_HEADER = 24
    private const val MAX_BYTES = 1024L * 1024 * 1024 * 1024
    private const val GCM_TAG_BYTES = 16
    private const val RAW_KEY_BYTES = 32

    fun encrypt(source: File, output: OutputStream, password: String) {
        require(password.length >= 8) { "Use a backup password with at least 8 characters." }
        val size = source.length()
        require(size in 1..MAX_BYTES)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val prefix = ByteArray(8).also(SecureRandom()::nextBytes)
        val header = ByteBuffer.allocate(LEGACY_HEADER)
            .put(legacyMagic)
            .put(salt)
            .put(prefix)
            .putLong(size)
            .array()
        val key = passwordKey(password, salt)
        try {
            encryptChunks(source, output, key, prefix, header)
        } finally {
            key.fill(0)
            salt.fill(0)
            prefix.fill(0)
        }
    }

    fun decrypt(
        input: InputStream,
        target: File,
        password: String,
        maxBytes: Long = MAX_BYTES
    ) {
        require(password.isNotEmpty()) { "Enter the original backup password." }
        val data = DataInputStream(input)
        val header = ByteArray(LEGACY_HEADER).also(data::readFully)
        val buffer = ByteBuffer.wrap(header)
        require(ByteArray(8).also(buffer::get).contentEquals(legacyMagic)) {
            "Select a complete GPT Mobile backup."
        }
        val salt = ByteArray(16).also(buffer::get)
        val prefix = ByteArray(8).also(buffer::get)
        val size = buffer.long
        require(size in 1..minOf(maxBytes, MAX_BYTES)) {
            "Invalid backup size or insufficient free space."
        }
        val key = passwordKey(password, salt)
        try {
            decryptChunks(data, target, key, prefix, header, size)
        } finally {
            key.fill(0)
            salt.fill(0)
            prefix.fill(0)
        }
    }

    // GPTFULL2 encryption was retired. Decryption remains for legacy recovery only.
    fun decryptWithKey(
        input: InputStream,
        target: File,
        rawKey: ByteArray,
        maxBytes: Long = MAX_BYTES
    ) {
        requireRawKey(rawKey)
        val data = DataInputStream(input)
        val header = ByteArray(PASSWORDLESS_HEADER).also(data::readFully)
        val buffer = ByteBuffer.wrap(header)
        require(ByteArray(8).also(buffer::get).contentEquals(passwordlessMagic)) {
            "Select a passwordless encrypted GPT Mobile backup."
        }
        val prefix = ByteArray(8).also(buffer::get)
        val size = buffer.long
        require(size in 1..minOf(maxBytes, MAX_BYTES)) {
            "Invalid backup size or insufficient free space."
        }
        try {
            decryptChunks(data, target, rawKey, prefix, header, size)
        } finally {
            prefix.fill(0)
        }
    }

    private fun encryptChunks(
        source: File,
        output: OutputStream,
        rawKey: ByteArray,
        prefix: ByteArray,
        header: ByteArray
    ) {
        output.write(header)
        DataInputStream(source.inputStream().buffered()).use { input ->
            var remaining = source.length()
            var index = 0
            while (remaining > 0) {
                val bytes = ByteArray(minOf(remaining, CHUNK.toLong()).toInt()).also(input::readFully)
                try {
                    output.write(cipher(Cipher.ENCRYPT_MODE, rawKey, prefix, header, index++).doFinal(bytes))
                } finally {
                    bytes.fill(0)
                }
                remaining -= bytes.size
            }
            check(input.read() == -1) { "Backup changed while saving. Please try again." }
        }
        output.flush()
    }

    private fun decryptChunks(
        data: DataInputStream,
        target: File,
        rawKey: ByteArray,
        prefix: ByteArray,
        header: ByteArray,
        size: Long
    ) {
        try {
            target.outputStream().buffered().use { output ->
                var remaining = size
                var index = 0
                while (remaining > 0) {
                    val count = minOf(remaining, CHUNK.toLong()).toInt()
                    val encrypted = ByteArray(count + GCM_TAG_BYTES).also(data::readFully)
                    val bytes = try {
                        cipher(Cipher.DECRYPT_MODE, rawKey, prefix, header, index++).doFinal(encrypted)
                    } finally {
                        encrypted.fill(0)
                    }
                    try {
                        output.write(bytes)
                    } finally {
                        bytes.fill(0)
                    }
                    remaining -= count
                }
                require(data.read() == -1) { "Unexpected data after the backup." }
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun cipher(
        mode: Int,
        rawKey: ByteArray,
        prefix: ByteArray,
        header: ByteArray,
        index: Int
    ) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(
            mode,
            SecretKeySpec(rawKey, "AES"),
            GCMParameterSpec(128, ByteBuffer.allocate(12).put(prefix).putInt(index).array())
        )
        updateAAD(header)
        updateAAD(ByteBuffer.allocate(4).putInt(index).array())
    }

    private fun requireRawKey(rawKey: ByteArray) {
        require(rawKey.size == RAW_KEY_BYTES) { "Invalid complete-backup encryption key." }
    }

    private fun passwordKey(password: String, salt: ByteArray): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, 600_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            chars.fill('\u0000')
            spec.clearPassword()
        }
    }
}
