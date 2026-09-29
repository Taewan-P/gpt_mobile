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
            compactionThresholdCharacters = 999999,
            localRetryLimit = 99
        ).normalized()

        assertEquals(300, normalized.timeoutSeconds)
        assertEquals(16, normalized.maxCallsPerTurn)
        assertEquals(48, normalized.maxLocalModelCalls)
        assertEquals(20, normalized.maxSearchQueries)
        assertEquals(28, normalized.searchResultsPerEngine)
        assertEquals(32, normalized.maxPages)
        assertEquals(8, normalized.crawlDepth)
        assertEquals(16, normalized.pageFetchConcurrency)
        assertEquals(96000, normalized.maxPageCharacters)
        assertEquals(8192, normalized.handoffTokens)
        assertEquals(48000, normalized.compactionThresholdCharacters)
        assertEquals(1, normalized.localRetryLimit)
        assertEquals(15, normalized.lowBatteryThresholdPercent)
        assertEquals(256, normalized.remoteSynthesisOutputTokens)
    }

    @Test
    fun defaultsKeepResearchScopedAndHandoffCompact() {
        val defaults = ModelDelegationSettings()
        assertEquals(6, defaults.maxSearchQueries)
        assertEquals(10, defaults.searchResultsPerEngine)
        assertEquals(10, defaults.maxPages)
        assertEquals(2, defaults.crawlDepth)
        assertEquals(4, defaults.pageFetchConcurrency)
        assertEquals(36000, defaults.maxPageCharacters)
        assertEquals(256, defaults.handoffTokens)
        assertEquals(false, defaults.allowRemoteWorkers)
        assertEquals(1, defaults.maxDelegationDepth)
        assertEquals(500, defaults.compactionThresholdCharacters)
        assertEquals(0, defaults.localRetryLimit)
        assertEquals(15, defaults.lowBatteryThresholdPercent)
        assertEquals(256, defaults.remoteSynthesisOutputTokens)
    }
}
