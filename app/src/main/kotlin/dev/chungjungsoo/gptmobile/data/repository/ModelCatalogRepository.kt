package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry

interface ModelCatalogRepository {
    suspend fun getVisibleEntries(): List<CatalogEntry>

    suspend fun getCachedVisibleEntries(): List<CatalogEntry> = getVisibleEntries()
}
