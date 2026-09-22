package dev.chungjungsoo.gptmobile.data.localmodel

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Internal storage supports native NPU reads; legacy external files remain readable until migrated. */
internal class LocalModelStorage(val root: File, val legacyRoot: File?) {
    private val roots: List<File> get() = listOfNotNull(root, legacyRoot).distinct()

    fun find(relativePath: String, expectedSize: Long = 0L): File? = roots.map { file(it, relativePath) }
        .firstOrNull { it.isFile && (expectedSize <= 0L || it.length() == expectedSize) }

    fun files(): Set<String> = roots.flatMap { base ->
        val models = File(base, LocalModelDownloadPaths.MODELS_DIR)
        if (!models.isDirectory) {
            emptyList()
        } else {
            models.walkTopDown()
                .filter { it.isFile }.map { it.relativeTo(base).invariantSeparatorsPath }.toList()
        }
    }.toSet()

    fun delete(relativePath: String) {
        roots.forEach { file(it, relativePath).delete() }
    }

    /** Caller holds the file-access and generation locks. */
    suspend fun migrate(relativePath: String, expectedSize: Long): File? {
        val destination = file(root, relativePath)
        val source = legacyRoot?.let { file(it, relativePath) }?.takeIf { it.isFile } ?: return find(relativePath)
        if (source.canonicalFile == destination.canonicalFile) return source
        if (expectedSize <= 0L || source.length() != expectedSize) throw IOException("Legacy Local Model size does not match its record")
        if (destination.isFile && destination.length() == expectedSize && digest(source).contentEquals(digest(destination))) {
            source.delete()
            return destination
        }
        if (root.usableSpace < expectedSize) throw IOException("Not enough space to migrate Local Model")
        check(destination.parentFile?.let { it.isDirectory || it.mkdirs() } == true) { "Unable to create Local Model directory" }
        val partial = File(destination.path + ".migrating")
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            source.inputStream().use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        hash.update(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (partial.length() != expectedSize || !hash.digest().contentEquals(digest(partial))) throw IOException("Local Model migration verification failed")
            coroutineContext.ensureActive()
            Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            source.delete()
            return destination
        } finally {
            partial.delete()
        }
    }

    private fun file(base: File, relativePath: String): File {
        require(relativePath.startsWith("models/") && relativePath.split('/').all(LocalModelDownloadPaths::isValidPathSegment))
        val result = File(base, relativePath)
        require(result.canonicalFile.toPath().startsWith(base.canonicalFile.toPath())) { "Invalid Local Model path" }
        return result
    }

    private suspend fun digest(file: File): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest()
    }
}
