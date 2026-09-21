package dev.chungjungsoo.gptmobile.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogRepositoryTest {

    @Test
    fun `successful fetch returns remote entries and writes cache`() = runBlocking {
        var cached: String? = null
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { remoteCatalog("remote-model", "0.1.0") },
            readCacheJson = { cached },
            writeCacheJson = { cached = it },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("remote-model"), entries.map { it.id })
        assertEquals(remoteCatalog("remote-model", "0.1.0"), cached)
    }

    @Test
    fun `failed fetch uses last cached catalog`() = runBlocking {
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { error("offline") },
            readCacheJson = { remoteCatalog("cached-model", "0.1.0") },
            writeCacheJson = { error("cache should not be written") },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("cached-model"), entries.map { it.id })
    }

    @Test
    fun `failed fetch with no cache uses bundled snapshot`() = runBlocking {
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { error("offline") },
            readCacheJson = { null },
            writeCacheJson = { error("cache should not be written") },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("bundled-model"), entries.map { it.id })
    }

    @Test
    fun `invalid remote json falls back to cache instead of writing it`() = runBlocking {
        var cached = remoteCatalog("cached-model", "0.1.0")
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { "{ not-json" },
            readCacheJson = { cached },
            writeCacheJson = { cached = it },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("cached-model"), entries.map { it.id })
        assertEquals(remoteCatalog("cached-model", "0.1.0"), cached)
    }

    @Test
    fun `invalid cache falls through to bundled snapshot`() = runBlocking {
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { error("offline") },
            readCacheJson = { "{ not-json" },
            writeCacheJson = { error("cache should not be written") },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("bundled-model"), entries.map { it.id })
    }

    @Test
    fun `unknown remote schema falls back to cache without poisoning it`() = runBlocking {
        val cachedJson = remoteCatalog("cached-model", "0.1.0")
        var cached: String? = cachedJson
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { unknownSchemaCatalog() },
            readCacheJson = { cached },
            writeCacheJson = { cached = it },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("cached-model"), entries.map { it.id })
        assertEquals(cachedJson, cached)
    }

    @Test
    fun `unknown schema in cache falls through to bundled snapshot`() = runBlocking {
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { error("offline") },
            readCacheJson = { unknownSchemaCatalog() },
            writeCacheJson = { error("cache should not be written") },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("bundled-model"), entries.map { it.id })
    }

    private fun unknownSchemaCatalog(): String = """
        {
          "schemaVersion": 2,
          "models": [
            {
              "id": "future-model",
              "displayName": "Future",
              "minAppVersion": "0.1.0"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `cache-first read uses cache and never touches the network`() = runBlocking {
        var remoteCalls = 0
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = {
                remoteCalls += 1
                error("network should not be called")
            },
            readCacheJson = { remoteCatalog("cached-model", "0.1.0") },
            writeCacheJson = { error("cache should not be written") },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getCachedVisibleEntries()

        assertEquals(listOf("cached-model"), entries.map { it.id })
        assertEquals(0, remoteCalls)
    }

    @Test
    fun `cache-first read falls back to bundled snapshot without a network call`() = runBlocking {
        var remoteCalls = 0
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = {
                remoteCalls += 1
                error("network should not be called")
            },
            readCacheJson = { null },
            writeCacheJson = { error("cache should not be written") },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getCachedVisibleEntries()

        assertEquals(listOf("bundled-model"), entries.map { it.id })
        assertEquals(0, remoteCalls)
    }

    @Test
    fun `entries above the running app version are hidden`() = runBlocking {
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { remoteCatalog("future-model", "9.0.0") },
            readCacheJson = { null },
            writeCacheJson = {},
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        assertTrue(repository.getVisibleEntries().isEmpty())
    }

    private fun remoteCatalog(id: String, minAppVersion: String): String = """
        {
          "schemaVersion": 1,
          "models": [
            {
              "id": "$id",
              "displayName": "$id",
              "minAppVersion": "$minAppVersion"
            }
          ]
        }
    """.trimIndent()
}
