package dev.chungjungsoo.gptmobile.data.localruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationFingerprintTest {
    @Test
    fun `fingerprint is order sensitive`() {
        val userThenModel = conversationFingerprint(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.USER, "hello"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "hi")
            )
        )
        val modelThenUser = conversationFingerprint(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.MODEL, "hi"),
                LocalHistoryMessage(LocalHistoryRole.USER, "hello")
            )
        )

        assertFalse(userThenModel == modelThenUser)
        assertFalse(userThenModel.isPrefixOf(modelThenUser))
    }

    @Test
    fun `fingerprint is content sensitive`() {
        val original = conversationFingerprint(
            listOf(LocalHistoryMessage(LocalHistoryRole.USER, "hello"))
        )
        val edited = conversationFingerprint(
            listOf(LocalHistoryMessage(LocalHistoryRole.USER, "hello!"))
        )

        assertFalse(original == edited)
        assertFalse(original.isPrefixOf(edited))
    }

    @Test
    fun `equal histories match and are prefixes of each other`() {
        val first = conversationFingerprint(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.USER, "first"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "answer")
            )
        )
        val second = conversationFingerprint(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.USER, "first"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "answer")
            )
        )

        assertEquals(first, second)
        assertTrue(first.isPrefixOf(second))
        assertTrue(second.isPrefixOf(first))
        assertTrue(incomingHistoryExtendsConsumed(consumed = first, incomingPrior = second))
    }

    @Test
    fun `prefix extension is accepted only when incoming starts with consumed`() {
        val consumed = conversationFingerprint(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.USER, "first"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "answer")
            )
        )
        val extended = conversationFingerprint(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.USER, "first"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "answer"),
                LocalHistoryMessage(LocalHistoryRole.USER, "second"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "next")
            )
        )
        val diverged = conversationFingerprint(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.USER, "edited"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "answer")
            )
        )

        assertTrue(consumed.isPrefixOf(extended))
        assertFalse(extended.isPrefixOf(consumed))
        assertFalse(consumed.isPrefixOf(diverged))
        assertTrue(incomingHistoryExtendsConsumed(consumed = consumed, incomingPrior = consumed))
        assertTrue(incomingHistoryExtendsConsumed(consumed = consumed, incomingPrior = extended))
        assertFalse(incomingHistoryExtendsConsumed(consumed = consumed, incomingPrior = diverged))
    }

    @Test
    fun `fingerprint includes image identity`() {
        val withImage = conversationFingerprint(
            listOf(LocalHistoryMessage(LocalHistoryRole.USER, "hello", imageIds = listOf("/tmp/photo.png|image/png|12")))
        )
        val withoutImage = conversationFingerprint(
            listOf(LocalHistoryMessage(LocalHistoryRole.USER, "hello"))
        )
        val otherImage = conversationFingerprint(
            listOf(LocalHistoryMessage(LocalHistoryRole.USER, "hello", imageIds = listOf("/tmp/other.png|image/png|12")))
        )

        assertFalse(withImage == withoutImage)
        assertFalse(withImage == otherImage)
        assertFalse(withImage.isPrefixOf(withoutImage))
    }
}
