package dev.chungjungsoo.gptmobile.presentation.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamPresentationTest {
    @Test
    fun nextPresentedText_doesNotSplitSurrogatePairs() {
        val emoji = "\uD83D\uDE00"
        val received = "a$emoji"
        val visible = nextPresentedText(
            received = received,
            currentlyVisible = "a",
            frameDeltaMillis = 16L,
            catchUpMillis = 100L
        )

        assertTrue(visible.startsWith("a"))
        assertFalse(visible.endsWith("\uD83D"))
        assertEquals(received, nextPresentedText(received, "a", frameDeltaMillis = 100L, catchUpMillis = 100L))
    }

    @Test
    fun nextPresentedText_keepsCombiningClustersIntact() {
        val cluster = "e\u0301"
        val received = "c$cluster"
        val visible = nextPresentedText(
            received = received,
            currentlyVisible = "c",
            frameDeltaMillis = 16L,
            catchUpMillis = 100L
        )
        assertFalse(visible.endsWith("e") && visible != received)
        assertEquals(received, nextPresentedText(received, "c", frameDeltaMillis = 100L, catchUpMillis = 100L))
    }

    @Test
    fun nextPresentedText_catchesUpWithinBoundedDelay() {
        val received = "x".repeat(1000)
        var visible = ""
        var elapsed = 0L
        var remaining = 100L
        while (visible != received && elapsed < 200L) {
            visible = nextPresentedText(received, visible, frameDeltaMillis = 16L, catchUpMillis = remaining)
            remaining = (remaining - 16L).coerceAtLeast(1L)
            elapsed += 16L
        }
        assertEquals(received, visible)
        assertTrue(elapsed <= 112L)
    }

    @Test
    fun nextPresentedText_flushClearsDisplayBacklog() {
        assertEquals(
            "done",
            nextPresentedText(
                received = "done",
                currentlyVisible = "d",
                frameDeltaMillis = 16L,
                catchUpMillis = 100L,
                flush = true
            )
        )
    }

    @Test
    fun nextPresentedText_keepsZwjFamilyClusterIntact() {
        val family = "👨‍👩‍👧‍👦"
        val received = "가" + family + "日本語"
        val afterFirst = nextPresentedText(
            received = received,
            currentlyVisible = "가",
            frameDeltaMillis = 16L,
            catchUpMillis = 100L
        )
        assertFalse(afterFirst.contains("👨") && !afterFirst.contains(family))
        assertEquals(family, takeGraphemes(family + "x", 1))
        assertEquals(
            received,
            nextPresentedText(received, "가", frameDeltaMillis = 100L, catchUpMillis = 100L)
        )
    }

    @Test
    fun nextPresentedText_continuousGrowthWithoutCaret_keepsPrefix() {
        val visible = nextPresentedText(
            received = "Hello w",
            currentlyVisible = "Hello",
            frameDeltaMillis = 16L,
            catchUpMillis = 100L
        )
        assertTrue(visible.startsWith("Hello"))
        assertTrue("Hello w".startsWith(visible))
        assertFalse(visible.endsWith("●"))
        assertTrue(visible.length < "Hello w".length || visible == "Hello w")
    }

    @Test
    fun nextPresentedText_trailingCaretEachToken_isNotAStablePrefix() {
        val dumped = nextPresentedText(
            received = "Hello w●",
            currentlyVisible = "Hello●",
            frameDeltaMillis = 16L,
            catchUpMillis = 100L
        )
        assertEquals("Hello w●", dumped)
    }
}
