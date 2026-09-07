package dev.chungjungsoo.gptmobile.data.catalog

import java.util.Locale
import kotlin.math.max
import kotlinx.serialization.json.Json

object ModelCatalogParser {
    const val SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parse(rawJson: String): ModelCatalog = json.decodeFromString(rawJson)

    fun visibleEntries(catalog: ModelCatalog, currentAppVersion: String): List<CatalogEntry> {
        if (catalog.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return emptyList()
        }
        return catalog.models.filter { entry ->
            entry.id.isNotBlank() && compareAppVersions(entry.minAppVersion, currentAppVersion) <= 0
        }
    }

    fun formatDownloadSize(sizeInBytes: Long): String {
        val kilobyte = 1024.0
        val megabyte = kilobyte * 1024
        val gigabyte = megabyte * 1024
        return when {
            sizeInBytes >= gigabyte -> {
                val value = sizeInBytes / gigabyte
                val pattern = if (value >= 10) "%.0f GB" else "%.1f GB"
                String.format(Locale.US, pattern, value)
            }

            sizeInBytes >= megabyte -> "${(sizeInBytes / megabyte).toInt()} MB"

            else -> "${(sizeInBytes / kilobyte).toInt()} KB"
        }
    }

    internal fun compareAppVersions(left: String, right: String): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        val length = max(leftParts.size, rightParts.size)
        for (index in 0 until length) {
            val leftValue = leftParts.getOrElse(index) { 0 }
            val rightValue = rightParts.getOrElse(index) { 0 }
            if (leftValue != rightValue) {
                return leftValue.compareTo(rightValue)
            }
        }
        return 0
    }

    private fun versionParts(version: String): List<Int> = version.split('.').map { part ->
        part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
}
