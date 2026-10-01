package dev.chungjungsoo.gptmobile.data.github

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonElement

/**
 * Small in-memory conditional-request cache for GitHub JSON responses.
 *
 * GitHub returns ETags for many REST resources. Keeping the parsed value next
 * to the ETag lets callers send If-None-Match and reuse the parsed response on
 * HTTP 304 instead of spending quota and model context on duplicate payloads.
 */
class GitHubResponseCache(
    private val maxEntries: Int = 256
) {
    data class Entry(
        val etag: String,
        val value: JsonElement,
        val storedAtMillis: Long = System.currentTimeMillis()
    )

    private val entries = ConcurrentHashMap<String, Entry>()
    private val decodedBlobs = ConcurrentHashMap<String, String>()

    fun get(key: String): Entry? = entries[key]

    fun put(key: String, etag: String?, value: JsonElement) {
        if (etag.isNullOrBlank()) return
        if (entries.size >= maxEntries && !entries.containsKey(key)) {
            entries.entries.minByOrNull { it.value.storedAtMillis }?.key?.let(entries::remove)
        }
        entries[key] = Entry(etag, value)
    }

    fun getDecodedBlob(sha: String): String? = decodedBlobs[sha]

    fun putDecodedBlob(sha: String, content: String) {
        if (sha.isBlank()) return
        if (decodedBlobs.size >= maxEntries && !decodedBlobs.containsKey(sha)) {
            decodedBlobs.keys.firstOrNull()?.let(decodedBlobs::remove)
        }
        decodedBlobs[sha] = content
    }

    fun clear() {
        entries.clear()
        decodedBlobs.clear()
    }
}
