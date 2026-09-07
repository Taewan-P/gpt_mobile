package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant

data class ResolvedModelDownload(
    val fileName: String,
    val downloadUrl: String,
    val commitHash: String,
    val sizeInBytes: Long,
    val contextSize: Int = 0
)

object SocVariantResolver {
    fun resolve(entry: CatalogEntry, deviceSocModel: String): ResolvedModelDownload {
        val default = ResolvedModelDownload(
            fileName = LocalModelDownloadPaths.fileNameFromUrl(entry.downloadUrl),
            downloadUrl = entry.downloadUrl,
            commitHash = LocalModelDownloadPaths.commitHashFromUrl(entry.downloadUrl),
            sizeInBytes = entry.sizeInBytes
        )
        val variant = matchingVariant(entry.socToModelFiles, deviceSocModel) ?: return default
        val fileName = variant.modelFile.ifBlank {
            LocalModelDownloadPaths.fileNameFromUrl(variant.downloadUrl).ifBlank { default.fileName }
        }
        val commitHash = variant.commitHash.ifBlank {
            LocalModelDownloadPaths.commitHashFromUrl(variant.downloadUrl).ifBlank { default.commitHash }
        }
        val downloadUrl = variant.downloadUrl.ifBlank {
            LocalModelDownloadPaths.rewriteResolveUrl(default.downloadUrl, fileName, commitHash)
        }
        return ResolvedModelDownload(
            fileName = fileName,
            downloadUrl = downloadUrl,
            commitHash = commitHash,
            sizeInBytes = variant.sizeInBytes.takeIf { it > 0L } ?: default.sizeInBytes,
            contextSize = variant.contextSize
        )
    }

    fun hasMatchingVariant(socToModelFiles: Map<String, *>, deviceSocModel: String): Boolean = matchingVariantKey(socToModelFiles, deviceSocModel) != null

    private fun matchingVariant(
        socToModelFiles: Map<String, SocVariant>,
        deviceSocModel: String
    ): SocVariant? {
        val key = matchingVariantKey(socToModelFiles, deviceSocModel) ?: return null
        return socToModelFiles[key]
    }

    private fun matchingVariantKey(socToModelFiles: Map<String, *>, deviceSocModel: String): String? {
        if (deviceSocModel.isBlank() || socToModelFiles.isEmpty()) return null
        val needle = normalizeSocKey(deviceSocModel)
        if (needle.isEmpty()) return null
        return socToModelFiles.keys.firstOrNull { normalizeSocKey(it) == needle }
    }

    internal fun normalizeSocKey(value: String): String = value
        .trim()
        .lowercase()
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
}
