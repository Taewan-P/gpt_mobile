package dev.chungjungsoo.gptmobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDelegationSettingsTest {
    @Test
    fun normalizedClampsExtendedDelegationControls() {
        val normalized = ModelDelegationSettings(
            timeoutSeconds = 999,
            maxCallsPerTurn = 99,
            searchBreadth = 99,
            maxPageReads = 99,
            crawlDepth = 99,
            parallelism = 99,
            localWorkloadPercent = 999,
            remoteBriefCharacters = 999999
        ).normalized()

        assertEquals(120, normalized.timeoutSeconds)
        assertEquals(8, normalized.maxCallsPerTurn)
        assertEquals(12, normalized.searchBreadth)
        assertEquals(20, normalized.maxPageReads)
        assertEquals(4, normalized.crawlDepth)
        assertEquals(8, normalized.parallelism)
        assertEquals(100, normalized.localWorkloadPercent)
        assertEquals(16000, normalized.remoteBriefCharacters)
    }

    @Test
    fun defaultsFavorLocalDelegationAndCompactRemoteBriefs() {
        val defaults = ModelDelegationSettings()
        assertEquals(4, defaults.searchBreadth)
        assertEquals(6, defaults.maxPageReads)
        assertEquals(1, defaults.crawlDepth)
        assertEquals(2, defaults.parallelism)
        assertEquals(70, defaults.localWorkloadPercent)
        assertEquals(6000, defaults.remoteBriefCharacters)
    }
}
