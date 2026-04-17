package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
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
}
