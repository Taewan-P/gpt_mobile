package dev.chungjungsoo.gptmobile.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMessageUtilsTest {
    @Test
    fun `buildAssistantErrorContent returns plain error when no content exists`() {
        assertEquals(
            "Error: Request timed out.",
            buildAssistantErrorContent("", "Request timed out.")
        )
    }

    @Test
    fun `buildAssistantErrorContent appends stop marker when partial content exists`() {
        assertEquals(
            "Partial answer\n\n[Response stopped: Request timed out.]",
            buildAssistantErrorContent("Partial answer", "Request timed out.")
        )
    }

    @Test
    fun `stripAssistantErrorNote removes appended stop note from assistant history`() {
        val content = "Partial answer\n\n[Response stopped: Request timed out.]"
        assertEquals("Partial answer", stripAssistantErrorNote(content))
    }

    @Test
    fun `isAssistantErrorMessage detects explicit error marker`() {
        assertTrue(isAssistantErrorMessage("Error: Request timed out."))
        assertTrue(isAssistantErrorMessage("  Error: Network unavailable."))
        assertTrue(!isAssistantErrorMessage("Partial answer\n\n[Response stopped: Request timed out.]"))
    }
}
