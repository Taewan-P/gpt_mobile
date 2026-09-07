package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry

class FakeModelCatalogRepository(
    private val entries: List<CatalogEntry> = emptyList()
) : ModelCatalogRepository {
    var visibleEntriesCalls = 0
    var cachedVisibleEntriesCalls = 0

    override suspend fun getVisibleEntries(): List<CatalogEntry> {
        visibleEntriesCalls += 1
        return entries
    }

    override suspend fun getCachedVisibleEntries(): List<CatalogEntry> {
        cachedVisibleEntriesCalls += 1
        return entries
    }
}
