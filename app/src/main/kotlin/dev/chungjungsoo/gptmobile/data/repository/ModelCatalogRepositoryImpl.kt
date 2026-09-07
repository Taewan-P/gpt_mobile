package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.ModelCatalog
import dev.chungjungsoo.gptmobile.data.catalog.ModelCatalogParser
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelCatalogRepositoryImpl(
    private val fetchRemoteJson: suspend () -> String,
    private val readCacheJson: () -> String?,
    private val writeCacheJson: (String) -> Unit,
    private val readBundledJson: () -> String,
    private val appVersionName: String
) : ModelCatalogRepository {

    constructor(
        context: Context,
        networkClient: NetworkClient,
        appVersionName: String
    ) : this(
        fetchRemoteJson = {
            val response = networkClient().get(HOSTED_CATALOG_URL) {
                timeout {
                    requestTimeoutMillis = CATALOG_REQUEST_TIMEOUT_MS
                }
            }
            check(response.status.isSuccess()) { "Model Catalog fetch failed: ${response.status}" }
            response.bodyAsText()
        },
        readCacheJson = {
            cacheFile(context).takeIf { it.exists() }?.readText()
        },
        writeCacheJson = { json ->
            cacheFile(context).writeText(json)
        },
        readBundledJson = {
            context.assets.open(CATALOG_FILE_NAME).bufferedReader().use { it.readText() }
        },
        appVersionName = appVersionName
    )

    override suspend fun getVisibleEntries(): List<CatalogEntry> = withContext(Dispatchers.IO) {
        visibleEntries(
            remote = {
                fetchParsableCatalog(
                    source = { fetchRemoteJson() },
                    onParsed = writeCacheJson
                )
            },
            cached = { fetchParsableCatalog(source = { readCacheJson() }) },
            bundled = { fetchParsableCatalog(source = { readBundledJson() }) }
        )
    }

    override suspend fun getCachedVisibleEntries(): List<CatalogEntry> = withContext(Dispatchers.IO) {
        visibleEntries(
            remote = { null },
            cached = { fetchParsableCatalog(source = { readCacheJson() }) },
            bundled = { fetchParsableCatalog(source = { readBundledJson() }) }
        )
    }

    private suspend fun visibleEntries(
        remote: suspend () -> ModelCatalog?,
        cached: suspend () -> ModelCatalog?,
        bundled: suspend () -> ModelCatalog?
    ): List<CatalogEntry> {
        val catalog = remote() ?: cached() ?: bundled() ?: return emptyList()
        return ModelCatalogParser.visibleEntries(catalog, appVersionName)
    }

    private suspend fun fetchParsableCatalog(
        source: suspend () -> String?,
        onParsed: (String) -> Unit = {}
    ): ModelCatalog? = runCatching {
        val rawJson = source() ?: return null
        val catalog = ModelCatalogParser.parse(rawJson)
        if (catalog.schemaVersion != ModelCatalogParser.SUPPORTED_SCHEMA_VERSION) {
            // An unsupported schema is not a usable source: fall through to the next
            // fallback instead of surfacing an empty catalog, and never cache it.
            return null
        }
        onParsed(rawJson)
        catalog
    }.getOrNull()

    companion object {
        const val HOSTED_CATALOG_URL = "https://raw.githubusercontent.com/Taewan-P/gpt_mobile/main/model_catalog.json"
        const val CATALOG_FILE_NAME = "model_catalog.json"
        private const val CATALOG_REQUEST_TIMEOUT_MS = 15_000L

        private fun cacheFile(context: Context): File = File(context.filesDir, CATALOG_FILE_NAME)
    }
}
