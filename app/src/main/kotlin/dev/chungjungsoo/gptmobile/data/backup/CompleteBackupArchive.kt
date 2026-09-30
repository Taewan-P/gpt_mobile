package dev.chungjungsoo.gptmobile.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class CompleteBackupManifest(
    val version: Int = 2,
    val preferences: Map<String, BackupValue> = emptyMap(),
    val sharedPreferences: Map<String, Map<String, BackupValue>> = emptyMap(),
    val secrets: Map<String, String> = emptyMap(),
    val files: Map<String, Long> = emptyMap(),
    val sections: Set<String> = emptySet(),
    val protection: BackupProtection? = null
)

@Serializable
internal data class BackupValue(val type: String, val value: String = "", val values: Set<String> = emptySet())

internal object CompleteBackupArchive {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private const val MANIFEST = "manifest.json"
    private const val MAX_MANIFEST = 16L * 1024 * 1024

    fun write(file: File, manifest: CompleteBackupManifest, sources: Map<String, File>) {
        require(sources.keys == manifest.files.keys)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            val metadata = json.encodeToString(manifest).toByteArray()
            require(metadata.size <= MAX_MANIFEST) { "Backup metadata exceeds the size limit." }
            zip.write(metadata)
            zip.closeEntry()
            sources.forEach { (path, source) ->
                validatePath(path)
                zip.putNextEntry(ZipEntry(path))
                source.inputStream().buffered().use { copyExactly(it, zip, manifest.files.getValue(path)) }
                zip.closeEntry()
            }
        }
    }

    fun read(file: File, staging: File, maxFileBytes: Long): CompleteBackupManifest = ZipFile(file).use { zip ->
        val entries = zip.entries().asSequence().toList()
        require(entries.size <= 100_000 && entries.map { it.name }.toSet().size == entries.size) { "Invalid or duplicate archive entries." }
        val metadata = requireNotNull(zip.getEntry(MANIFEST)) { "This file is not a complete backup." }
        require(metadata.size in 1..MAX_MANIFEST) { "Invalid backup metadata." }
        val manifest = zip.getInputStream(metadata).use { input ->
            val out = java.io.ByteArrayOutputStream()
            copyExactly(input, out, metadata.size)
            json.decodeFromString<CompleteBackupManifest>(out.toString(Charsets.UTF_8.name()))
        }
        require(manifest.version in 1..2) { "This backup requires a newer app version." }
        if (manifest.version == 1) {
            require("database.sqlite" in manifest.files) { "Incomplete legacy complete backup." }
        }
        require(entries.map { it.name }.toSet() == manifest.files.keys + MANIFEST) { "Incomplete backup archive." }
        var remaining = maxFileBytes
        manifest.files.forEach { (path, size) ->
            validatePath(path)
            require(size >= 0 && size <= remaining) { "Insufficient space to restore the backup." }
            remaining -= size
            val entry = requireNotNull(zip.getEntry(path))
            require(!entry.isDirectory && entry.size == size) { "Invalid file size in backup." }
            val target = File(staging, path)
            check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
            zip.getInputStream(entry).use { input -> target.outputStream().buffered().use { output -> copyExactly(input, output, size) } }
        }
        manifest
    }

    fun validatePath(path: String) {
        if (path == "database.sqlite") return
        val parts = path.split('/')
        require(parts.size >= 2 && parts.first() in setOf("internal", "external")) { "Invalid backup location." }
        require(parts.none { it.isBlank() || it == "." || it == ".." || it.startsWith(".full-restore-") || '\\' in it || ':' in it || '\u0000' in it }) { "Invalid backup path." }
        require(parts[1] !in setOf("datastore", "backup", "backups")) { "Invalid backup location." }
    }

    private fun copyExactly(input: InputStream, output: OutputStream, size: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = size
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            require(count > 0) { "Incomplete file or a file changed during backup." }
            output.write(buffer, 0, count)
            remaining -= count
        }
        require(input.read() == -1) { "A file changed size during backup." }
    }
}
