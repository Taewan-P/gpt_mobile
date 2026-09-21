package dev.chungjungsoo.gptmobile.presentation.ui.setting

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformSettingScreenTest {
    @Test
    fun apiKeySummaryNeverReturnsTokenCharacters() {
        assertEquals("Key set", apiKeySummary("secret", "Key set", "Key not set"))
        assertEquals("Key not set", apiKeySummary(null, "Key set", "Key not set"))
        assertEquals("Key not set", apiKeySummary("", "Key set", "Key not set"))
    }

    @Test
    fun advancedGenerationSummaryIncludesTemperatureAndTopP() {
        assertEquals(
            "Temperature 0.7 · Top p 0.9",
            advancedGenerationSummary(listOf("Temperature" to "0.7", "Top p" to "0.9"))
        )
        assertEquals(
            "Temperature Not set · Top p 1.0",
            advancedGenerationSummary(listOf("Temperature" to "Not set", "Top p" to "1.0"))
        )
    }

    @Test
    fun advancedGenerationSummaryIncludesTopKAndMaxTokensWhenProvided() {
        assertEquals(
            "Temperature 0.7 · Top p 0.9 · Top-k 40 · Max tokens 2048",
            advancedGenerationSummary(
                listOf(
                    "Temperature" to "0.7",
                    "Top p" to "0.9",
                    "Top-k" to "40",
                    "Max tokens" to "2048"
                )
            )
        )
        assertEquals(
            "Temperature 1.0 · Top p Not set · Top-k 16",
            advancedGenerationSummary(
                listOf(
                    "Temperature" to "1.0",
                    "Top p" to "Not set",
                    "Top-k" to "16",
                    "Max tokens" to null
                )
            )
        )
    }
}
