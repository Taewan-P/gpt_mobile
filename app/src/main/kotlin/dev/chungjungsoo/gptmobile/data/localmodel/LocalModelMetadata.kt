package dev.chungjungsoo.gptmobile.data.localmodel

import android.content.Context
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import java.io.File
import kotlinx.serialization.json.Json

/** Store capabilities alongside each package so Hub downloads still support tools after restarting. */
internal object LocalModelMetadata {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private const val FILE_NAME = ".catalog.json"

    fun save(directory: File, entry: CatalogEntry) {
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, FILE_NAME).writeText(json.encodeToString(entry))
    }

    fun read(context: Context): List<CatalogEntry> {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(root, "models").listFiles().orEmpty().filter { it.isDirectory }.flatMap { model ->
            model.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { revision ->
                val file = File(revision, FILE_NAME)
                if (!file.isFile || file.length() > 256 * 1024) return@mapNotNull null
                runCatching { json.decodeFromString<CatalogEntry>(file.readText()) }.getOrNull()
                    ?.takeIf { it.id == model.name }
            }
        }
    }
}
