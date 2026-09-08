package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatStreamPresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun highThroughputBurstCatchesUpOnFrameClockWithoutSplittingEmoji() {
        // Reproducible 60 Hz emulator scenario. Do not treat this as 90/120 Hz evidence.
        composeRule.mainClock.autoAdvance = false
        val emoji = "\uD83D\uDE00"
        val family = "👨‍👩‍👧‍👦"
        var received by mutableStateOf("Hi")
        var presented = ""

        composeRule.setContent {
            GPTMobileTheme {
                presented = rememberPresentedStreamText(
                    received = received,
                    isTerminal = false,
                    contentIdentity = "stream-1"
                )
                Text(presented)
            }
        }

        composeRule.mainClock.advanceTimeBy(16L)
        composeRule.runOnIdle { assertEquals("Hi", presented) }

        val burst = "Hi 한국어 日本語 " + "token ".repeat(80) + family + emoji
        composeRule.runOnIdle { received = burst }

        val lengths = mutableListOf<Int>()
        var elapsed = 0L
        while (elapsed <= 160L) {
            composeRule.mainClock.advanceTimeBy(16L)
            elapsed += 16L
            composeRule.runOnIdle { lengths += presented.length }
            if (presented == burst) break
        }

        composeRule.runOnIdle {
            assertEquals(burst, presented)
            assertTrue("catch-up frames=$lengths elapsed=${elapsed}ms", elapsed <= 128L)
            assertTrue(presented.contains(family))
            assertTrue(presented.endsWith(emoji))
            assertTrue(lengths.zipWithNext().all { (previous, next) -> next >= previous })
        }
    }

    @Test
    fun terminalFlushShowsAllReceivedTextImmediately() {
        composeRule.mainClock.autoAdvance = false
        var received by mutableStateOf("partial")
        var isTerminal by mutableStateOf(false)
        var presented = ""

        composeRule.setContent {
            GPTMobileTheme {
                presented = rememberPresentedStreamText(
                    received = received,
                    isTerminal = isTerminal,
                    contentIdentity = "stream-2"
                )
                Text(presented)
            }
        }

        composeRule.runOnIdle { received = "partial answer with a long trailing burst of tokens" }
        composeRule.runOnIdle { isTerminal = true }
        composeRule.mainClock.advanceTimeBy(1L)
        composeRule.runOnIdle {
            assertEquals("partial answer with a long trailing burst of tokens", presented)
        }
    }
}
