package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTest {
    @Test
    fun `tool-only historical turn keeps its user message for follow-up context`() {
        val platform = PlatformV2(
            uid = "profile",
            name = "Provider",
            compatibleType = ClientType.CUSTOM,
            apiUrl = "https://provider.example/v1",
            model = "model"
        )

        val turns = ContextBuilder().build(
            userMessages = listOf(
                MessageV2(content = "What is the latest album of NMIXX?", platformType = null),
                MessageV2(content = "Where's the result?", platformType = null)
            ),
            assistantMessages = listOf(
                listOf(MessageV2(content = "", thoughts = "I searched for it.", platformType = platform.uid)),
                listOf(MessageV2(content = "", platformType = platform.uid))
            ),
            platform = platform
        )

        assertEquals(
            listOf("What is the latest album of NMIXX?", "Where's the result?"),
            turns.map { it.userMessage.content }
        )
        assertNull(turns.first().assistantMessage)
    }

    @Test
    fun `openai context retains a fact from beyond the former ten-turn window`() {
        val platform = openaiPlatform()
        val userMessages = (0 until 12).map { index ->
            MessageV2(
                content = if (index == 0) "Secret code is ORCHID-77" else "user-$index",
                platformType = null
            )
        }
        val assistantMessages = (0 until 12).map { index ->
            listOf(
                MessageV2(
                    content = if (index == 0) "Noted the secret code." else "reply-$index",
                    platformType = platform.uid
                )
            )
        }

        val turns = ContextBuilder().build(userMessages, assistantMessages, platform)

        assertEquals(12, turns.size)
        assertEquals("Secret code is ORCHID-77", turns.first().userMessage.content)
        assertEquals("Noted the secret code.", turns.first().assistantMessage?.content)
        assertEquals("user-11", turns.last().userMessage.content)
    }

    @Test
    fun `context uses only the selected platform and revision`() {
        val platform = openaiPlatform()
        val turns = ContextBuilder().build(
            userMessages = listOf(
                MessageV2(content = "Remember my cat is Miso", platformType = null),
                MessageV2(content = "What is my cat's name?", platformType = null)
            ),
            assistantMessages = listOf(
                listOf(
                    MessageV2(content = "Wrong platform secret", platformType = "other-platform"),
                    MessageV2(
                        content = "Latest name is discarded",
                        platformType = platform.uid,
                        revisions = listOf(
                            AssistantRevision(content = "Your cat is Miso.", createdAt = 1L)
                        ),
                        activeRevisionIndex = 0
                    )
                ),
                emptyList()
            ),
            platform = platform
        )

        assertEquals("Remember my cat is Miso", turns.first().userMessage.content)
        assertEquals("Your cat is Miso.", turns.first().assistantMessage?.content)
    }

    @Test
    fun `pure failed historical turns are dropped and partial answers keep stripped text`() {
        val platform = openaiPlatform()
        val turns = ContextBuilder().build(
            userMessages = listOf(
                MessageV2(content = "first", platformType = null),
                MessageV2(content = "second", platformType = null),
                MessageV2(content = "now", platformType = null)
            ),
            assistantMessages = listOf(
                listOf(MessageV2(content = "Error: boom", platformType = platform.uid)),
                listOf(
                    MessageV2(
                        content = "Partial answer\n\n[Response stopped: timeout]",
                        platformType = platform.uid
                    )
                ),
                emptyList()
            ),
            platform = platform
        )

        assertEquals(listOf("second", "now"), turns.map { it.userMessage.content })
        assertEquals("Partial answer", turns.first().assistantMessage?.content)
    }

    @Test
    fun `litert lm keeps the full persisted history beyond ten turns`() {
        val platform = localPlatform()
        val userMessages = (0 until 12).map { index ->
            MessageV2(content = "user-$index", platformType = null)
        }
        val assistantMessages = (0 until 12).map { index ->
            listOf(MessageV2(content = "reply-$index", platformType = platform.uid))
        }

        val turns = ContextBuilder().build(userMessages, assistantMessages, platform)

        assertEquals(12, turns.size)
        assertEquals("user-0", turns.first().userMessage.content)
        assertEquals("user-11", turns.last().userMessage.content)
    }

    @Test
    fun `litert lm keeps historical image attachments for rebuild`() {
        val platform = localPlatform()
        val photo = ChatAttachment(
            localFilePath = "/tmp/photo.png",
            preparedFilePath = "/tmp/photo.png",
            displayName = "photo.png",
            mimeType = "image/png",
            sizeBytes = 12
        )

        val turns = ContextBuilder().build(
            userMessages = listOf(
                MessageV2(content = "look", platformType = null, attachments = listOf(photo)),
                MessageV2(content = "follow up", platformType = null)
            ),
            assistantMessages = listOf(
                listOf(MessageV2(content = "a cat", platformType = platform.uid)),
                emptyList()
            ),
            platform = platform
        )

        assertEquals(1, turns.first().userMessage.attachments.size)
        assertTrue(turns.last().userMessage.attachments.isEmpty())
    }

    private fun localPlatform() = PlatformV2(
        uid = "local",
        name = "Local",
        compatibleType = ClientType.LITERT_LM,
        apiUrl = "",
        model = "gemma3-1b-it"
    )

    private fun openaiPlatform() = PlatformV2(
        uid = "openai",
        name = "OpenAI",
        compatibleType = ClientType.OPENAI,
        apiUrl = "https://api.openai.com/v1/",
        model = "gpt-5.6"
    )
}
