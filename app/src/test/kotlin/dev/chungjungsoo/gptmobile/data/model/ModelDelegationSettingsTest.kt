package dev.chungjungsoo.gptmobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDelegationSettingsTest {
    @Test
    fun normalizedClampsExtendedDelegationControls() {
        val normalized = ModelDelegationSettings(
            timeoutSeconds = 999,
            maxInputTokensPerDelegate = 999999,
            chunkSizeTokens = 999999,
            retryChunkSizeTokens = 999999,
            timeToFirstTokenTimeoutSeconds = 999,
            idleTokenTimeoutSeconds = 999,
            maxDelegateRuntimeSeconds = 999,
            maxConcurrentDelegates = 99,
            maxWastedLocalTokensPerTurn = 999999,
            evidenceSufficiencyPercent = 999,
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
        assertEquals(12000, normalized.maxInputTokensPerDelegate)
        assertEquals(12000, normalized.chunkSizeTokens)
        assertEquals(12000, normalized.retryChunkSizeTokens)
        assertEquals(90, normalized.timeToFirstTokenTimeoutSeconds)
        assertEquals(90, normalized.idleTokenTimeoutSeconds)
        assertEquals(120, normalized.maxDelegateRuntimeSeconds)
        assertEquals(4, normalized.maxConcurrentDelegates)
        assertEquals(64000, normalized.maxWastedLocalTokensPerTurn)
        assertEquals(100, normalized.evidenceSufficiencyPercent)
        assertEquals(16, normalized.maxCallsPerTurn)
        assertEquals(48, normalized.maxLocalModelCalls)
        assertEquals(20, normalized.maxSearchQueries)
        assertEquals(10, normalized.searchResultsPerEngine)
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
    fun localFirstOwnershipCannotBeStarvedByLowManualBudgets() {
        val localFirst = ModelDelegationSettings(
            processingOwnership = 0,
            maxLocalModelCalls = 4,
            maxInputTokensPerDelegate = 3000,
            maxWastedLocalTokensPerTurn = 4000
        ).normalized()

        assertEquals(16, localFirst.effectiveLocalModelCalls())
        assertEquals(8, localFirst.effectiveResearchCalls())
        assertEquals(8000, localFirst.effectiveLocalInputTokens())
        assertEquals(24000, localFirst.effectiveWastedLocalTokens())
    }

    @Test
    fun balancedOwnershipStillRespectsConfiguredBudgets() {
        val balanced = ModelDelegationSettings(
            processingOwnership = 50,
            maxLocalModelCalls = 4,
            maxInputTokensPerDelegate = 3000,
            maxWastedLocalTokensPerTurn = 4000
        ).normalized()

        assertEquals(4, balanced.effectiveLocalModelCalls())
        assertEquals(8, balanced.effectiveResearchCalls())
        assertEquals(3000, balanced.effectiveLocalInputTokens())
        assertEquals(4000, balanced.effectiveWastedLocalTokens())
    }

    @Test
    fun researchDepthPreservesCompressionAndWorkerBudgets() {
        val customized = ModelDelegationSettings(maxOutputTokens = 768, timeoutSeconds = 90)
        for (depth in listOf(0, 50, 100)) {
            val changed = customized.withStrategy(depth)
            assertEquals(true, changed.compactToolResults)
            assertEquals(customized.handoffTokens, changed.handoffTokens)
            assertEquals(customized.maxLocalModelCalls, changed.maxLocalModelCalls)
            assertEquals(768, changed.maxOutputTokens)
            assertEquals(90, changed.timeoutSeconds)
            assertEquals(10, changed.searchResultsPerEngine)
        }
    }

    @Test
    fun delegationAmountFavorsHelperWithoutChangingPerRequestLimits() {
        val defaults = ModelDelegationSettings()
        val high = defaults.withDelegationAmount(80)
        val low = defaults.withDelegationAmount(20)
        assertEquals(20, high.processingOwnership)
        assertEquals(16, high.maxLocalModelCalls)
        assertEquals(8, high.maxCallsPerTurn)
        assertEquals(defaults.maxInputCharacters, high.maxInputCharacters)
        assertEquals(defaults.maxOutputTokens, high.maxOutputTokens)
        assertEquals(80, low.processingOwnership)
        assertEquals(7, low.maxLocalModelCalls)
        assertEquals(defaults.maxConcurrentDelegates, high.maxConcurrentDelegates)
    }

    @Test
    fun defaultsKeepResearchScopedAndHandoffCompact() {
        val defaults = ModelDelegationSettings()
        assertEquals(9, defaults.maxSearchQueries)
        assertEquals(10, defaults.searchResultsPerEngine)
        assertEquals(11, defaults.maxPages)
        assertEquals(1, defaults.crawlDepth)
        assertEquals(4, defaults.pageFetchConcurrency)
        assertEquals(37200, defaults.maxPageCharacters)
        assertEquals(512, defaults.handoffTokens)
        assertEquals(6000, defaults.maxInputTokensPerDelegate)
        assertEquals(5000, defaults.chunkSizeTokens)
        assertEquals(2500, defaults.retryChunkSizeTokens)
        assertEquals(30, defaults.timeToFirstTokenTimeoutSeconds)
        assertEquals(20, defaults.idleTokenTimeoutSeconds)
        assertEquals(120, defaults.maxDelegateRuntimeSeconds)
        assertEquals(1, defaults.maxConcurrentDelegates)
        assertEquals(8000, defaults.maxWastedLocalTokensPerTurn)
        assertEquals(79, defaults.evidenceSufficiencyPercent)
        assertEquals(false, defaults.allowRemoteWorkers)
        assertEquals(1, defaults.maxDelegationDepth)
        assertEquals(500, defaults.compactionThresholdCharacters)
        assertEquals(0, defaults.localRetryLimit)
        assertEquals(15, defaults.lowBatteryThresholdPercent)
        assertEquals(256, defaults.remoteSynthesisOutputTokens)
        assertEquals(4000, defaults.primaryReplayTokens)
        assertEquals(512, defaults.primaryReplayResultTokens)
    }
}
