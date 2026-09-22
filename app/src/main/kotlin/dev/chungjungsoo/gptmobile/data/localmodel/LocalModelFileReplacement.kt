package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.database.dao.LocalModelDao
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Caller holds the local generation lock so no engine is using the replaced file. */
internal suspend fun commitLocalModelDownload(
    root: File,
    dao: LocalModelDao,
    catalogEntryId: String,
    download: ResolvedModelDownload,
    partial: File,
    legacyRoot: File? = null
) {
    LocalModelDownloadPaths.requireValidPathSegments(catalogEntryId, download.commitHash, download.fileName)
    if (download.sizeInBytes <= 0L || partial.length() != download.sizeInBytes) throw IOException("Incomplete Local Model download")
    val previous = dao.getById(catalogEntryId) ?: throw IOException("Local Model download was removed")
    val destination = File(root, LocalModelDownloadPaths.relativeFilePath(catalogEntryId, download.commitHash, download.fileName))
    val previousPath = LocalModelDownloadPaths.relativeFilePath(catalogEntryId, previous.commitHash, previous.fileName)
    val previousFiles = listOfNotNull(root, legacyRoot).distinct().map { File(it, previousPath) }
    withContext(NonCancellable) {
        Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        dao.upsert(
            previous.copy(
                commitHash = download.commitHash,
                fileName = download.fileName,
                relativeDirectory = LocalModelDownloadPaths.relativeDirectory(catalogEntryId, download.commitHash),
                totalBytes = download.sizeInBytes,
                status = LocalModelStatus.READY,
                updatedAt = System.currentTimeMillis() / 1000
            )
        )
        previousFiles.filter { it != destination }.forEach { previousFile ->
            previousFile.delete()
            previousFile.parentFile?.takeIf { it != destination.parentFile && it.list().isNullOrEmpty() }?.delete()
        }
    }
}
