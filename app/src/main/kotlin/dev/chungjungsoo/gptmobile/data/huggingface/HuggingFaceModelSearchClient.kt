package dev.chungjungsoo.gptmobile.data.huggingface

import dev.chungjungsoo.gptmobile.data.catalog.CatalogCapabilities
import dev.chungjungsoo.gptmobile.data.catalog.CatalogDefaultConfig
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class HuggingFaceLiteRtResult(
    val repoId: String,
    val sha: String,
    val filePath: String,
    val sizeInBytes: Long,
    val downloads: Long,
    val gated: Boolean,
    val tags: List<String>
) {
    val fileName: String get() = filePath.substringAfterLast('/')
    val displayName: String get() = repoId.substringAfter('/')

    fun toCatalogEntry(): CatalogEntry {
        val stableId = buildString {
            append("hf_")
            append(
                (repoId + "_" + filePath)
                    .lowercase()
                    .replace(Regex("[^a-z0-9._-]+"), "_")
                    .trim('_')
                    .take(110)
            )
        }
        val revision = sha.ifBlank { "main" }
        val encodedPath = filePath.split('/').joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
        }
        val npuOptimized = dev.chungjungsoo.gptmobile.data.localmodel.LocalModelPackages.isNpuFile(filePath)
        val soc = dev.chungjungsoo.gptmobile.data.localmodel.LocalModelPackages.npuSoc(filePath)
        val url = "https://huggingface.co/$repoId/resolve/$revision/$encodedPath?download=true"

        return CatalogEntry(
            id = stableId,
            displayName = displayName,
            downloadUrl = url,
            sizeInBytes = sizeInBytes,
            minRamGb = estimateMinRamGb(sizeInBytes),
            isGated = gated,
            capabilities = CatalogCapabilities(
                vision = tags.any { it.contains("vision", ignoreCase = true) },
                tools = tags.any { it.contains("tool", ignoreCase = true) },
                thinking = tags.any {
                    it.contains("reasoning", ignoreCase = true) ||
                        it.contains("thinking", ignoreCase = true)
                }
            ),
            supportedAccelerators = if (npuOptimized) listOf("npu") else listOf("gpu", "cpu"),
            socToModelFiles = if (soc != null) {
                mapOf(
                    soc to dev.chungjungsoo.gptmobile.data.catalog.SocVariant(
                        modelFile = fileName,
                        downloadUrl = url,
                        commitHash = revision,
                        sizeInBytes = sizeInBytes,
                        contextSize = Regex("(?i)(?:ctx|ekv)([0-9]+)").find(fileName)?.groupValues?.get(1)?.toIntOrNull() ?: 1024
                    )
                )
            } else {
                emptyMap()
            },
            defaultConfig = CatalogDefaultConfig(maxTokens = 4096),
            minAppVersion = "0.9.0"
        )
    }

    private fun estimateMinRamGb(bytes: Long): Int = when {
        bytes <= 0L -> 0
        bytes < 1_000_000_000L -> 4
        bytes < 2_500_000_000L -> 6
        bytes < 4_500_000_000L -> 8
        bytes < 7_500_000_000L -> 12
        else -> 16
    }
}

/**
 * Searches the Hugging Face Hub using its public model listing API and keeps only
 * repositories that expose LiteRT-LM package files consumable by this app.
 *
 * The Hub's list-models endpoint supports search/sort/limit and full model data;
 * full data includes repository siblings so the client can identify .litertlm files.
 */
@Singleton
class HuggingFaceModelSearchClient @Inject constructor(
    private val networkClient: NetworkClient,
    private val tokenStore: HuggingFaceTokenStore
) {
    suspend fun searchNpu(query: String, deviceSoc: String): List<HuggingFaceLiteRtResult> {
        if (dev.chungjungsoo.gptmobile.data.localruntime.QualcommSocSupport.htpVersion(deviceSoc) == null) return emptyList()
        val requested = query.trim()
        val candidates = coroutineScope {
            listOf(requested.ifBlank { "litert" }, deviceSoc.lowercase(), "litert").distinct().map { term ->
                async { search(term, MAX_LIMIT) }
            }.awaitAll().flatten()
        }
        return candidates.distinctBy { it.repoId to it.filePath }.filter { result ->
            val entry = result.toCatalogEntry()
            dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators.isNpuEligible(entry.supportedAccelerators, entry.socToModelFiles, deviceSoc) &&
                (requested.isBlank() || (result.repoId + " " + result.filePath).contains(requested, true))
        }.take(DEFAULT_LIMIT)
    }

    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): List<HuggingFaceLiteRtResult> {
        val normalized = query.trim()
        val token = tokenStore.readAccessToken()
        val response = networkClient().get(MODELS_API) {
            parameter("search", normalized.ifBlank { DEFAULT_DISCOVERY_QUERY })
            parameter("sort", "downloads")
            parameter("direction", "-1")
            parameter("limit", limit.coerceIn(1, MAX_LIMIT))
            parameter("full", "true")
            timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
            token?.takeIf(String::isNotBlank)?.let { bearerAuth(it) }
        }
        check(response.status.isSuccess()) {
            "Hugging Face search failed: HTTP ${response.status.value}"
        }

        val root = NetworkClient.json.parseToJsonElement(response.bodyAsText())
        val models = root as? JsonArray ?: return emptyList()
        // Listing siblings omit sizes. Resolve metadata at the pinned revision before
        // showing a package as suitable for the phone's memory budget.
        val permits = Semaphore(4)
        val files = coroutineScope {
            models.filter { parseCompatibleFiles(it).isNotEmpty() }.map { model ->
                async {
                    permits.withPermit {
                        val obj = model as JsonObject
                        val repo = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val revision = obj["sha"]?.jsonPrimitive?.contentOrNull?.takeIf { it.matches(Regex("[a-fA-F0-9]{40}")) }
                            ?: return@withPermit emptyList<HuggingFaceLiteRtResult>()
                        val metadata = networkClient().get("$MODELS_API/$repo/revision/$revision") {
                            parameter("blobs", "true")
                            timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
                            token?.takeIf(String::isNotBlank)?.let { bearerAuth(it) }
                        }
                        if (!metadata.status.isSuccess()) return@withPermit emptyList<HuggingFaceLiteRtResult>()
                        parseCompatibleFiles(NetworkClient.json.parseToJsonElement(metadata.bodyAsText()))
                            .filter { it.sizeInBytes > 0 }
                    }
                }
            }.awaitAll().flatten()
        }
        return files.sortedWith(
            compareByDescending<HuggingFaceLiteRtResult> { it.downloads }
                .thenBy { it.repoId.lowercase() }
                .thenBy { it.filePath.lowercase() }
        )
    }

    private fun parseCompatibleFiles(element: kotlinx.serialization.json.JsonElement): List<HuggingFaceLiteRtResult> {
        val model = element as? JsonObject ?: return emptyList()
        val repoId = model["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return emptyList()
        val sha = model["sha"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val downloads = model["downloads"]?.jsonPrimitive?.longOrNull ?: 0L
        val gatedElement = model["gated"]
        val gated = gatedElement?.jsonPrimitive?.booleanOrNull
            ?: gatedElement?.jsonPrimitive?.contentOrNull?.let { value ->
                value.equals("manual", true) || value.equals("auto", true) || value.equals("true", true)
            }
            ?: false
        val tags = model["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val siblings = model["siblings"]?.jsonArray.orEmpty()

        return siblings.mapNotNull { siblingElement ->
            val sibling = siblingElement as? JsonObject ?: return@mapNotNull null
            val path = sibling["rfilename"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!path.endsWith(LITERTLM_EXTENSION, ignoreCase = true)) return@mapNotNull null
            if (!isSupportedHubPackage(repoId, path, tags)) return@mapNotNull null

            val size = sibling["size"]?.jsonPrimitive?.longOrNull
                ?: (sibling["lfs"] as? JsonObject)?.get("size")?.jsonPrimitive?.longOrNull
                ?: 0L

            HuggingFaceLiteRtResult(
                repoId = repoId,
                sha = sha,
                filePath = path,
                sizeInBytes = size,
                downloads = downloads,
                gated = gated,
                tags = tags
            )
        }
    }

    private companion object {
        const val MODELS_API = "https://huggingface.co/api/models"
        const val LITERTLM_EXTENSION = ".litertlm"
        const val DEFAULT_DISCOVERY_QUERY = "litert"
        const val DEFAULT_LIMIT = 24
        const val MAX_LIMIT = 50
        const val REQUEST_TIMEOUT_MS = 20_000L
    }
}

/** Conservative discovery: arbitrary checkpoint archives are not executable model packages. */
internal fun isSupportedHubPackage(repoId: String, path: String, tags: List<String>): Boolean {
    if (!path.endsWith(".litertlm", true)) return false
    // These containers are not Android conversational models, even from trusted publishers.
    if (Regex("(?i)(?:^|[/_.-])(?:web|intel|qcs[0-9]+)(?:[/_.-]|$)").containsMatchIn(path)) return false
    if (tags.any { it.equals("feature-extraction", true) || it.equals("sentence-similarity", true) } ||
        repoId.contains("embedding", true)
    ) {
        return false
    }
    val packages = dev.chungjungsoo.gptmobile.data.localmodel.LocalModelPackages
    if (packages.isNpuFile(path)) return packages.npuSoc(path) != null
    // Explicit CPU-only exports cannot be advertised as GPU models.
    if (Regex("(?i)(?:^|[/_.-])cpu(?:[/_.-]|$)").containsMatchIn(path)) return false
    val publisher = repoId.substringBefore('/').lowercase()
    return publisher in setOf("litert-community", "google", "google-ai-edge") ||
        tags.any { it.equals("gpu", true) || it.equals("litert-lm-gpu", true) } ||
        Regex("(?i)(?:^|[/_.-])gpu(?:[/_.-]|$)").containsMatchIn(path)
}
