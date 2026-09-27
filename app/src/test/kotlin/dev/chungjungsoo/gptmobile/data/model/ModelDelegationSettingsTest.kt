package dev.chungjungsoo.gptmobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDelegationSettingsTest {
    @Test
    fun normalizedClampsExtendedDelegationControls() {
        val normalized = ModelDelegationSettings(
            timeoutSeconds = 999,
            maxCallsPerTurn = 99,
            maxLocalModelCalls = 99,
            maxSearchQueries = 99,
            searchResultsPerEngine = 99,
            maxPages = 99,
            crawlDepth = 99,
            pageFetchConcurrency = 99,
            maxPageCharacters = 999999,
            handoffTokens = 999999,
            compactionThresholdCharacters = 999999
        ).normalized()

        assertEquals(300, normalized.timeoutSeconds)
        assertEquals(8, normalized.maxCallsPerTurn)
        assertEquals(24, normalized.maxLocalModelCalls)
        assertEquals(6, normalized.maxSearchQueries)
        assertEquals(10, normalized.searchResultsPerEngine)
        assertEquals(12, normalized.maxPages)
        assertEquals(2, normalized.crawlDepth)
        assertEquals(4, normalized.pageFetchConcurrency)
        assertEquals(48000, normalized.maxPageCharacters)
        assertEquals(4096, normalized.handoffTokens)
        assertEquals(24000, normalized.compactionThresholdCharacters)
    }

    @Test
    fun defaultsKeepResearchScopedAndHandoffCompact() {
        val defaults = ModelDelegationSettings()
        assertEquals(2, defaults.maxSearchQueries)
        assertEquals(4, defaults.searchResultsPerEngine)
        assertEquals(4, defaults.maxPages)
        assertEquals(0, defaults.crawlDepth)
        assertEquals(2, defaults.pageFetchConcurrency)
        assertEquals(12000, defaults.maxPageCharacters)
        assertEquals(1024, defaults.handoffTokens)
        assertEquals(3000, defaults.compactionThresholdCharacters)
    }
}
