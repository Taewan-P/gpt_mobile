package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.database.entity.ACTIVE_REVISION_LATEST
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiStateFlowExtensionsTest {

    @Test
    fun `collectApiStateUpdates publishes provider text and thoughts without UI state`() = runBlocking {
        val updates = mutableListOf<Pair<String, String>>()

        val outcome = flowOf(
            ApiState.Thinking("Checking"),
            ApiState.Success("Final "),
            ApiState.Success("answer"),
            ApiState.Done
        ).collectApiStateUpdates(
            onUpdate = { content, thoughts, _ -> updates += content to thoughts },
            nanoTimeProvider = { 1L }
        )

        assertEquals(ApiStateFlowOutcome.Completed, outcome)
        assertEquals("Final answer" to "Checking", updates.last())
    }

    @Test
    fun `collectApiStateUpdates preserves interleaved reasoning chat and tool chronology`() = runBlocking {
        data class Update(
            val content: String,
            val thoughts: String,
            val timeline: List<AssistantTimelineItem>
        )

        val updates = mutableListOf<Update>()

        flowOf(
            ApiState.Thinking("Check "),
            ApiState.Thinking("sources"),
            ApiState.Success("I will search."),
            ApiState.ToolCall(toolSequence = 0),
            ApiState.Thinking("Compare results."),
            ApiState.Success("First result."),
            ApiState.ToolCall(toolSequence = 1),
            ApiState.Success("Final answer."),
            ApiState.Done
        ).collectApiStateUpdates(
            onUpdate = { content, thoughts, timeline -> updates += Update(content, thoughts, timeline) },
            nanoTimeProvider = { 1L },
            publishIntervalMillis = 0L
        )

        assertEquals("I will search.First result.Final answer.", updates.last().content)
        assertEquals("Check sourcesCompare results.", updates.last().thoughts)
        assertEquals(
            listOf(
                AssistantTimelineItem(AssistantTimelineItemType.THINKING, content = "Check sources"),
                AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "I will search."),
                AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 0),
                AssistantTimelineItem(AssistantTimelineItemType.THINKING, content = "Compare results."),
                AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "First result."),
                AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 1),
                AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Final answer.")
            ),
            updates.last().timeline
        )
    }

    @Test
    fun `collectApiStateUpdates flushes partial text when canceled`() = runBlocking {
        val updates = mutableListOf<Pair<String, String>>()
        var wasCanceled = false

        try {
            flow {
                emit(ApiState.Success("Partial "))
                emit(ApiState.Success("answer"))
                throw CancellationException("stop")
            }.collectApiStateUpdates(
                onUpdate = { content, thoughts, _ -> updates += content to thoughts },
                nanoTimeProvider = { 1L }
            )
        } catch (_: CancellationException) {
            wasCanceled = true
        }

        assertTrue(wasCanceled)
        assertEquals("Partial answer" to "", updates.last())
    }

    @Test
    fun `collectApiStateUpdates persists informational notices on the timeline`() = runBlocking {
        data class Update(
            val content: String,
            val thoughts: String,
            val timeline: List<AssistantTimelineItem>
        )

        val updates = mutableListOf<Update>()
        val notices = mutableListOf<Pair<String, Boolean>>()

        flowOf(
            ApiState.Notice("Loading local model…", persistent = false),
            ApiState.Notice("The local platform ignored attachments", persistent = true),
            ApiState.Success("hello"),
            ApiState.Done
        ).collectApiStateUpdates(
            onUpdate = { content, thoughts, timeline -> updates += Update(content, thoughts, timeline) },
            onNotice = { message, persistent -> notices += message to persistent },
            nanoTimeProvider = { 1L },
            publishIntervalMillis = 0L
        )

        assertEquals(
            listOf("Loading local model…" to false, "The local platform ignored attachments" to true),
            notices
        )
        assertEquals(
            listOf(
                AssistantTimelineItem(AssistantTimelineItemType.NOTICE, content = "The local platform ignored attachments"),
                AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "hello")
            ),
            updates.last().timeline
        )
    }

    @Test
    fun `handleStates surfaces nonterminal agent notices`() = runBlocking {
        val notices = mutableListOf<String>()
        val messageFlow = MutableStateFlow(
            ChatViewModel.GroupedMessages(
                userMessages = listOf(MessageV2(content = "Hello", platformType = null)),
                assistantMessages = listOf(listOf(MessageV2(content = "", platformType = "platform-1")))
            )
        )

        val outcome = flowOf(
            ApiState.Notice("Tools unavailable for this model."),
            ApiState.Done
        ).handleStates(
            messageFlow = messageFlow,
            turnIndex = 0,
            platformIdx = 0,
            onLoadingComplete = {},
            onNotice = notices::add
        )

        assertEquals(ApiStateFlowOutcome.Completed, outcome)
        assertEquals(listOf("Tools unavailable for this model."), notices)
        assertEquals("", messageFlow.value.assistantMessages.single().single().content)
    }

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

        val outcome = flowOf(
            ApiState.Success("Partial answer"),
            ApiState.Error("Request timed out.")
        ).handleStates(
            messageFlow = messageFlow,
            turnIndex = 0,
            platformIdx = 0,
            onLoadingComplete = {}
        )

        val assistantMessage = messageFlow.value.assistantMessages.last().first()
        val assistantContent = assistantMessage.content
        assertTrue(assistantContent.contains("Partial answer"))
        assertTrue(assistantContent.contains("[Response stopped: Request timed out.]"))
        assertEquals(assistantContent, assistantMessage.timeline.single().content)
        assertEquals(ApiStateFlowOutcome.Failed("Request timed out."), outcome)
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
            turnIndex = 0,
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
                turnIndex = 0,
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
    fun `handleStates updates only the requested turn and platform`() = runBlocking {
        val untouchedTurn = listOf(
            MessageV2(content = "Other platform", platformType = "platform-1"),
            MessageV2(content = "Other turn", platformType = "platform-2")
        )
        val messageFlow = MutableStateFlow(
            ChatViewModel.GroupedMessages(
                userMessages = listOf(
                    MessageV2(content = "Hello", platformType = null),
                    MessageV2(content = "Again", platformType = null)
                ),
                assistantMessages = listOf(
                    listOf(
                        MessageV2(content = "Keep me", platformType = "platform-1"),
                        MessageV2(content = "", platformType = "platform-2")
                    ),
                    untouchedTurn
                )
            )
        )

        flowOf(
            ApiState.Success("Partial answer"),
            ApiState.Error("Request timed out.")
        ).handleStates(
            messageFlow = messageFlow,
            turnIndex = 0,
            platformIdx = 1,
            onLoadingComplete = {}
        )

        assertEquals("Keep me", messageFlow.value.assistantMessages[0][0].content)
        assertTrue(messageFlow.value.assistantMessages[0][1].content.contains("Partial answer"))
        assertEquals(untouchedTurn, messageFlow.value.assistantMessages[1])
    }

    @Test
    fun `handleStates completion timestamps only the requested turn and platform`() = runBlocking {
        val originalTimestamp = 10L
        val untouchedTimestamp = 20L
        val messageFlow = MutableStateFlow(
            ChatViewModel.GroupedMessages(
                userMessages = listOf(
                    MessageV2(content = "Hello", platformType = null),
                    MessageV2(content = "Again", platformType = null)
                ),
                assistantMessages = listOf(
                    listOf(
                        MessageV2(content = "Keep me", platformType = "platform-1", createdAt = untouchedTimestamp),
                        MessageV2(content = "Stamp me", platformType = "platform-2", createdAt = originalTimestamp)
                    ),
                    listOf(
                        MessageV2(content = "Other turn", platformType = "platform-1", createdAt = untouchedTimestamp),
                        MessageV2(content = "Leave me", platformType = "platform-2", createdAt = untouchedTimestamp)
                    )
                )
            )
        )

        val outcome = flowOf(ApiState.Done).handleStates(
            messageFlow = messageFlow,
            turnIndex = 0,
            platformIdx = 1,
            onLoadingComplete = {},
            currentTimeProvider = { 1234L }
        )

        assertEquals(untouchedTimestamp, messageFlow.value.assistantMessages[0][0].createdAt)
        assertEquals(1234L, messageFlow.value.assistantMessages[0][1].createdAt)
        assertEquals(untouchedTimestamp, messageFlow.value.assistantMessages[1][0].createdAt)
        assertEquals(untouchedTimestamp, messageFlow.value.assistantMessages[1][1].createdAt)
        assertEquals(ApiStateFlowOutcome.Completed, outcome)
    }

    @Test
    fun `handleStates reports incomplete when stream ends without terminal state`() = runBlocking {
        val messageFlow = MutableStateFlow(
            ChatViewModel.GroupedMessages(
                userMessages = listOf(MessageV2(content = "Hello", platformType = null)),
                assistantMessages = listOf(
                    listOf(MessageV2(content = "", platformType = "platform-1"))
                )
            )
        )

        val outcome = flowOf(ApiState.Success("Partial answer")).handleStates(
            messageFlow = messageFlow,
            turnIndex = 0,
            platformIdx = 0,
            onLoadingComplete = {}
        )

        assertEquals(ApiStateFlowOutcome.Incomplete, outcome)
    }

    @Test
    fun `handleStates appends retry revision only when stream succeeds`() = runBlocking {
        val messageFlow = MutableStateFlow(
            ChatViewModel.GroupedMessages(
                userMessages = listOf(MessageV2(content = "Hello", platformType = null)),
                assistantMessages = listOf(
                    listOf(
                        MessageV2(
                            content = "",
                            revisions = listOf(AssistantRevision(content = "Older answer", createdAt = 5L)),
                            platformType = "platform-1"
                        )
                    )
                )
            )
        )

        flowOf(
            ApiState.Success("New answer"),
            ApiState.Done
        ).handleStates(
            messageFlow = messageFlow,
            turnIndex = 0,
            platformIdx = 0,
            onLoadingComplete = {},
            nanoTimeProvider = { 1L },
            currentTimeProvider = { 1234L },
            revisionToAppendOnSuccess = AssistantRevision(content = "Previous answer", thoughts = "Previous thoughts", createdAt = 100L)
        )

        val assistantMessage = messageFlow.value.assistantMessages[0][0]
        assertEquals("New answer", assistantMessage.content)
        assertEquals(ACTIVE_REVISION_LATEST, assistantMessage.activeRevisionIndex)
        assertEquals(2, assistantMessage.revisions.size)
        assertEquals("Previous answer", assistantMessage.revisions[0].content)
        assertEquals("Previous thoughts", assistantMessage.revisions[0].thoughts)
        assertEquals("Older answer", assistantMessage.revisions[1].content)
    }

    @Test
    fun `handleStates preserves previous retry revision when stream errors`() = runBlocking {
        val messageFlow = MutableStateFlow(
            ChatViewModel.GroupedMessages(
                userMessages = listOf(MessageV2(content = "Hello", platformType = null)),
                assistantMessages = listOf(
                    listOf(
                        MessageV2(
                            content = "",
                            revisions = listOf(AssistantRevision(content = "Older answer", createdAt = 5L)),
                            platformType = "platform-1"
                        )
                    )
                )
            )
        )

        flowOf(
            ApiState.Success("Partial answer"),
            ApiState.Error("Request timed out.")
        ).handleStates(
            messageFlow = messageFlow,
            turnIndex = 0,
            platformIdx = 0,
            onLoadingComplete = {},
            revisionToAppendOnSuccess = AssistantRevision(content = "Previous answer", createdAt = 100L)
        )

        val assistantMessage = messageFlow.value.assistantMessages[0][0]
        assertTrue(assistantMessage.content.contains("Partial answer"))
        assertEquals(2, assistantMessage.revisions.size)
        assertEquals("Previous answer", assistantMessage.revisions[0].content)
        assertEquals("Older answer", assistantMessage.revisions[1].content)
    }
}
