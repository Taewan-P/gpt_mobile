package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiStateFlowExtensionsTest {

    @Test
    fun `buildAssistantErrorContent returns plain error when no content exists`() {
        assertEquals(
            "Error: Request timed out.",
            buildAssistantErrorContent("", "Request timed out.")
        )
    }

    @Test
    fun `handleStates keeps partial content and appends failure note`() = runBlocking {
        val messageFlow = MutableStateFlow(
            ChatViewModel.GroupedMessages(
                userMessages = listOf(MessageV2(content = "Hello", platformType = null)),
                assistantMessages = listOf(
                    listOf(MessageV2(content = "", platformType = "platform-1"))
                )
            )
        )

        flowOf(
            ApiState.Success("Partial answer"),
            ApiState.Error("Request timed out.")
        ).handleStates(
            messageFlow = messageFlow,
            platformIdx = 0,
            onLoadingComplete = {}
        )

        val assistantContent = messageFlow.value.assistantMessages.last().first().content
        assertTrue(assistantContent.contains("Partial answer"))
        assertTrue(assistantContent.contains("[Response stopped: Request timed out.]"))
    }

    @Test
    fun `handleStates flushes buffered content when stream completes without terminal state`() = runBlocking {
        val messageFlow = MutableStateFlow(
            ChatViewModel.GroupedMessages(
                userMessages = listOf(MessageV2(content = "Hello", platformType = null)),
                assistantMessages = listOf(
                    listOf(MessageV2(content = "", platformType = "platform-1"))
                )
            )
        )
        var loadingCompleteCalls = 0

        flowOf(
            ApiState.Success("Partial "),
            ApiState.Success("answer")
        ).handleStates(
            messageFlow = messageFlow,
            platformIdx = 0,
            onLoadingComplete = { loadingCompleteCalls += 1 },
            nanoTimeProvider = { 1L }
        )

        assertEquals("Partial answer", messageFlow.value.assistantMessages.last().first().content)
        assertEquals(1, loadingCompleteCalls)
    }

    @Test
    fun `handleStates flushes buffered content and completes loading when collection throws`() = runBlocking {
        val messageFlow = MutableStateFlow(
            ChatViewModel.GroupedMessages(
                userMessages = listOf(MessageV2(content = "Hello", platformType = null)),
                assistantMessages = listOf(
                    listOf(MessageV2(content = "", platformType = "platform-1"))
                )
            )
        )
        var loadingCompleteCalls = 0
        var thrownMessage: String? = null

        try {
            flow {
                emit(ApiState.Success("Partial "))
                emit(ApiState.Success("answer"))
                throw IllegalStateException("boom")
            }.handleStates(
                messageFlow = messageFlow,
                platformIdx = 0,
                onLoadingComplete = { loadingCompleteCalls += 1 },
                nanoTimeProvider = { 1L }
            )
        } catch (error: IllegalStateException) {
            thrownMessage = error.message
        }

        assertEquals("boom", thrownMessage)
        assertEquals("Partial answer", messageFlow.value.assistantMessages.last().first().content)
        assertEquals(1, loadingCompleteCalls)
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
