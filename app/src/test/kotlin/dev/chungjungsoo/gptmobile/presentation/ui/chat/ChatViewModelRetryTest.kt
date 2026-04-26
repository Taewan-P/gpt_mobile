package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.database.entity.ACTIVE_REVISION_LATEST
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveThoughts
import dev.chungjungsoo.gptmobile.data.database.entity.resetActiveRevision
import dev.chungjungsoo.gptmobile.data.database.entity.selectRevision
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.repository.hasSendableAssistantPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelRetryTest {

    @Test
    fun `normalizeAssistantRow pads sparse rows and preserves overflow messages`() {
        val sparseAssistantRow = listOf(
            MessageV2(chatId = 7, content = "Platform 2 answer", platformType = "platform-2"),
            MessageV2(chatId = 7, content = "Legacy answer", platformType = "legacy-platform")
        )

        val normalizedRow = normalizeAssistantRow(
            assistantMessages = sparseAssistantRow,
            enabledPlatformsInChat = listOf("platform-1", "platform-2"),
            chatId = 7
        )

        assertEquals(3, normalizedRow.size)
        assertEquals("platform-1", normalizedRow[0].platformType)
        assertEquals("", normalizedRow[0].content)
        assertEquals("Platform 2 answer", normalizedRow[1].content)
        assertEquals("legacy-platform", normalizedRow[2].platformType)
        assertEquals("Legacy answer", normalizedRow[2].content)
    }

    @Test
    fun `updateAssistantSlot only resets the targeted turn and platform`() {
        val groupedMessages = ChatViewModel.GroupedMessages(
            userMessages = listOf(
                MessageV2(chatId = 7, content = "First", platformType = null),
                MessageV2(chatId = 7, content = "Second", platformType = null)
            ),
            assistantMessages = listOf(
                listOf(
                    MessageV2(chatId = 7, content = "Keep me", platformType = "platform-1"),
                    MessageV2(chatId = 7, content = "Keep me too", platformType = "platform-2")
                ),
                listOf(
                    MessageV2(chatId = 7, content = "Other turn", platformType = "platform-1"),
                    MessageV2(
                        chatId = 7,
                        content = "Partial answer\n\n[Response stopped: timeout]",
                        platformType = "platform-2"
                    )
                )
            )
        )

        val updatedMessages = updateAssistantSlot(
            groupedMessages = groupedMessages,
            turnIndex = 1,
            platformIndex = 1
        ) {
            createEmptyAssistantMessage(chatId = 7, platformUid = "platform-2")
        }

        assertEquals("Keep me", updatedMessages.assistantMessages[0][0].content)
        assertEquals("Keep me too", updatedMessages.assistantMessages[0][1].content)
        assertEquals("Other turn", updatedMessages.assistantMessages[1][0].content)
        assertEquals("", updatedMessages.assistantMessages[1][1].content)
        assertEquals("platform-2", updatedMessages.assistantMessages[1][1].platformType)
    }

    @Test
    fun `retry assistant replacement preserves historical revisions`() {
        val currentMessage = MessageV2(
            chatId = 7,
            content = "Current answer",
            revisions = listOf(
                AssistantRevision(content = "Older answer", createdAt = 100L)
            ),
            platformType = "platform-1"
        )

        val retryMessage = createRetryAssistantMessage(
            currentMessage = currentMessage,
            chatId = 7,
            platformUid = "platform-1"
        )

        assertEquals("", retryMessage.content)
        assertEquals("", retryMessage.thoughts)
        assertEquals(listOf(AssistantRevision(content = "Older answer", createdAt = 100L)), retryMessage.revisions)
        assertEquals(ACTIVE_REVISION_LATEST, retryMessage.activeRevisionIndex)
    }

    @Test
    fun `normalizeAssistantRow keeps known slots addressable when duplicates exist`() {
        val rebuiltRow = listOf(
            MessageV2(chatId = 9, content = "Primary answer", platformType = "platform-1"),
            MessageV2(chatId = 9, content = "Duplicate answer", platformType = "platform-1"),
            MessageV2(chatId = 9, content = "Second platform", platformType = "platform-2")
        )

        val normalizedRow = normalizeAssistantRow(
            assistantMessages = rebuiltRow,
            enabledPlatformsInChat = listOf("platform-1", "platform-2"),
            chatId = 9
        )

        assertEquals("Primary answer", normalizedRow[0].content)
        assertEquals("Second platform", normalizedRow[1].content)
        assertTrue(normalizedRow.drop(2).any { it.content == "Duplicate answer" })
    }

    @Test
    fun `effective assistant content follows selected revision`() {
        val assistantMessage = MessageV2(
            chatId = 3,
            content = "Latest answer",
            thoughts = "Latest thoughts",
            revisions = listOf(
                AssistantRevision(
                    content = "Previous answer",
                    thoughts = "Previous thoughts",
                    createdAt = 100L
                )
            ),
            activeRevisionIndex = 0,
            platformType = "platform-1"
        )

        assertEquals("Previous answer", assistantMessage.effectiveContent())
        assertEquals("Previous thoughts", assistantMessage.effectiveThoughts())
        assertEquals("Latest answer", assistantMessage.resetActiveRevision().effectiveContent())
        assertEquals(ACTIVE_REVISION_LATEST, assistantMessage.resetActiveRevision().activeRevisionIndex)
    }

    @Test
    fun `retry context trims future turns`() {
        val groupedMessages = ChatViewModel.GroupedMessages(
            userMessages = listOf(
                MessageV2(chatId = 7, content = "First", platformType = null),
                MessageV2(chatId = 7, content = "Second", platformType = null),
                MessageV2(chatId = 7, content = "Future", platformType = null)
            ),
            assistantMessages = listOf(
                listOf(MessageV2(chatId = 7, content = "First answer", platformType = "platform-1")),
                listOf(MessageV2(chatId = 7, content = "", platformType = "platform-1")),
                listOf(MessageV2(chatId = 7, content = "Future answer", platformType = "platform-1"))
            )
        )

        val retryContext = groupedMessagesThroughTurn(groupedMessages, turnIndex = 1)

        assertEquals(listOf("First", "Second"), retryContext.userMessages.map { it.content })
        assertEquals(2, retryContext.assistantMessages.size)
        assertEquals("", retryContext.assistantMessages[1][0].content)
    }

    @Test
    fun `persistable messages keep thought only assistant messages`() {
        val groupedMessages = ChatViewModel.GroupedMessages(
            userMessages = listOf(MessageV2(chatId = 7, content = "Question", platformType = null, createdAt = 1L)),
            assistantMessages = listOf(
                listOf(
                    MessageV2(chatId = 7, content = "", thoughts = "Internal reasoning", platformType = "platform-1", createdAt = 2L),
                    MessageV2(
                        chatId = 7,
                        content = "",
                        thoughts = "",
                        attachments = listOf(
                            ChatAttachment(
                                localFilePath = "/tmp/a.png",
                                preparedFilePath = "/tmp/a.png",
                                displayName = "a.png",
                                mimeType = "image/png",
                                sizeBytes = 1L
                            )
                        ),
                        platformType = "platform-2",
                        createdAt = 3L
                    ),
                    MessageV2(chatId = 7, content = "", thoughts = "", platformType = "platform-2", createdAt = 3L)
                )
            )
        )

        val messages = persistableMessages(groupedMessages)

        assertEquals(3, messages.size)
        assertEquals("Question", messages[0].content)
        assertEquals("Internal reasoning", messages[1].thoughts)
        assertEquals(1, messages[2].attachments.size)
    }

    @Test
    fun `selectRevision falls back to latest when index is invalid`() {
        val assistantMessage = MessageV2(
            chatId = 5,
            content = "Latest",
            revisions = listOf(
                AssistantRevision(content = "Older", createdAt = 10L)
            ),
            platformType = "platform-1"
        )

        assertEquals(0, assistantMessage.selectRevision(0).activeRevisionIndex)
        assertEquals(ACTIVE_REVISION_LATEST, assistantMessage.selectRevision(9).activeRevisionIndex)
    }

    @Test
    fun `sendable assistant payload ignores synthetic error only content`() {
        val errorOnlyMessage = MessageV2(
            chatId = 5,
            content = "Error: Request timed out.",
            platformType = "platform-1"
        )
        val partialErrorMessage = MessageV2(
            chatId = 5,
            content = "Partial answer\n\n[Response stopped: Request timed out.]",
            platformType = "platform-1"
        )
        val attachmentOnlyMessage = MessageV2(
            chatId = 5,
            content = "Error: Request timed out.",
            attachments = listOf(
                ChatAttachment(
                    localFilePath = "/tmp/image.png",
                    preparedFilePath = "/tmp/image.png",
                    displayName = "image.png",
                    mimeType = "image/png",
                    sizeBytes = 12L
                )
            ),
            platformType = "platform-1"
        )

        assertEquals(false, errorOnlyMessage.hasSendableAssistantPayload())
        assertEquals(true, partialErrorMessage.hasSendableAssistantPayload())
        assertEquals(true, attachmentOnlyMessage.hasSendableAssistantPayload())
    }
}
