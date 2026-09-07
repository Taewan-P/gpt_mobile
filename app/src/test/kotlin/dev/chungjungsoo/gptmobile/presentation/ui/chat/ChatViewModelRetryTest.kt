package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.agent.AgentRunTerminalUpdate
import dev.chungjungsoo.gptmobile.data.agent.toTerminalUpdate
import dev.chungjungsoo.gptmobile.data.database.entity.ACTIVE_REVISION_LATEST
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveRunId
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveThoughts
import dev.chungjungsoo.gptmobile.data.database.entity.rebuildAssistantTimelineForEdit
import dev.chungjungsoo.gptmobile.data.database.entity.resetActiveRevision
import dev.chungjungsoo.gptmobile.data.database.entity.selectRevision
import dev.chungjungsoo.gptmobile.data.database.entity.snapshotLatestAssistantRevision
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.hasSendableAssistantPayload
import dev.chungjungsoo.gptmobile.util.ApiStateFlowOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelRetryTest {

    @Test
    fun `loading state reattaches to queued and running profile runs`() {
        val latestRow = listOf(
            MessageV2(id = 10, chatId = 7, content = "", platformType = "profile-1", currentRunId = "run-1"),
            MessageV2(id = 11, chatId = 7, content = "", platformType = "profile-2", currentRunId = "run-2")
        )
        val runs = mapOf(
            "run-1" to agentRun("run-1", 10, AgentRunStatus.RUNNING),
            "run-2" to agentRun("run-2", 11, AgentRunStatus.CANCELED)
        )

        val states = loadingStatesForLatestAssistant(
            platformCount = 2,
            latestAssistantRow = latestRow,
            runsById = runs,
            activeRunIds = emptySet()
        )

        assertEquals(
            listOf(ChatViewModel.LoadingState.Loading, ChatViewModel.LoadingState.Idle),
            states
        )
        assertEquals(
            listOf(ChatViewModel.LoadingState.Loading, ChatViewModel.LoadingState.Idle),
            loadingStatesForLatestAssistant(
                platformCount = 2,
                latestAssistantRow = latestRow,
                runsById = emptyMap(),
                activeRunIds = setOf("run-1")
            )
        )
    }

    @Test
    fun `persisted message observer rebuilds normalized comparison rows`() {
        val grouped = groupPersistedMessages(
            messages = listOf(
                MessageV2(id = 1, chatId = 7, content = "First", platformType = null),
                MessageV2(id = 2, chatId = 7, content = "Second profile", platformType = "profile-2"),
                MessageV2(id = 3, chatId = 7, content = "First profile", platformType = "profile-1"),
                MessageV2(id = 4, chatId = 7, content = "Again", platformType = null),
                MessageV2(id = 5, chatId = 7, content = "Latest", platformType = "profile-1")
            ),
            enabledPlatformsInChat = listOf("profile-1", "profile-2"),
            chatId = 7
        )

        assertEquals(listOf("First", "Again"), grouped.userMessages.map { it.content })
        assertEquals(listOf("First profile", "Second profile"), grouped.assistantMessages[0].map { it.content })
        assertEquals(listOf("Latest", ""), grouped.assistantMessages[1].map { it.content })
    }

    @Test
    fun `per-chat model override for a local platform is applied to the resolved platform`() {
        val platform = PlatformV2(
            uid = "local-1",
            name = "Local",
            compatibleType = ClientType.LITERT_LM,
            enabled = true,
            apiUrl = "",
            model = "gemma3-1b-it"
        )

        val resolved = resolvePlatformModel(
            platform = platform,
            chatPlatformModels = mapOf("local-1" to "gemma-3n-e2b-it")
        )

        assertEquals("gemma-3n-e2b-it", resolved.model)
        assertEquals(ClientType.LITERT_LM, resolved.compatibleType)
    }

    @Test
    fun `selected profiles resolve from all configured profiles without losing slot indexes`() {
        val configured = listOf(
            PlatformV2(
                uid = "profile-2",
                name = "Disabled but selected",
                compatibleType = ClientType.OPENAI,
                enabled = false,
                apiUrl = "https://example.com",
                model = "model"
            )
        )

        val resolved = resolveSelectedPlatforms(
            selectedProfileUids = listOf("missing-profile", "profile-2"),
            configuredPlatforms = configured
        )

        assertEquals(listOf(1), resolved.map { it.index })
        assertEquals(listOf("profile-2"), resolved.map { it.value.uid })
    }

    @Test
    fun `persist before provider waits for persistence and skips provider on failure`() = runBlocking {
        val persistenceGate = CompletableDeferred<String>()
        var providerValue: String? = null
        var failure: Throwable? = null

        val successJob = launch {
            persistBeforeProvider(
                persist = { persistenceGate.await() },
                startProvider = { providerValue = it },
                onFailure = { failure = it }
            )
        }

        assertFalse(successJob.isCompleted)
        assertEquals(null, providerValue)
        persistenceGate.complete("persisted")
        successJob.join()
        assertEquals("persisted", providerValue)
        assertEquals(null, failure)

        providerValue = null
        persistBeforeProvider(
            persist = { throw IllegalStateException("database unavailable") },
            startProvider = { providerValue = it },
            onFailure = { failure = it }
        )

        assertEquals(null, providerValue)
        assertEquals("database unavailable", failure?.message)
    }

    @Test
    fun `persisted assistant rows retain unavailable profile slots`() {
        val currentRow = listOf(
            MessageV2(chatId = 7, content = "Profile unavailable", platformType = "missing-profile"),
            MessageV2(chatId = 7, content = "", platformType = "profile-2")
        )
        val persisted = listOf(
            MessageV2(
                id = 12,
                chatId = 7,
                content = "",
                platformType = "profile-2",
                currentRunId = "run-2"
            )
        )

        val merged = mergePersistedAssistantRow(
            currentRow = currentRow,
            selectedProfileUids = listOf("missing-profile", "profile-2"),
            persistedMessages = persisted,
            chatId = 7
        )

        assertEquals(listOf("missing-profile", "profile-2"), merged.map { it.platformType })
        assertEquals("Profile unavailable", merged[0].content)
        assertEquals("run-2", merged[1].currentRunId)
    }

    @Test
    fun `agent run terminal updates preserve provider failure details`() {
        assertEquals(
            AgentRunTerminalUpdate(AgentRunStatus.COMPLETED, null),
            ApiStateFlowOutcome.Completed.toTerminalUpdate()
        )
        assertEquals(
            AgentRunTerminalUpdate(AgentRunStatus.FAILED, "provider failed"),
            ApiStateFlowOutcome.Failed("provider failed").toTerminalUpdate()
        )
        assertEquals(
            AgentRunTerminalUpdate(AgentRunStatus.FAILED, "Provider stream ended without completion."),
            ApiStateFlowOutcome.Incomplete.toTerminalUpdate()
        )
    }

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
    fun `assistant revision preserves the run that produced the answer`() {
        val assistantMessage = MessageV2(
            chatId = 7,
            content = "Answer",
            platformType = "platform-1",
            currentRunId = "run-123"
        )

        val revision = assistantMessage.snapshotLatestAssistantRevision(timestamp = 100L)

        assertEquals("run-123", revision?.runId)
    }

    @Test
    fun `effective run follows the selected assistant revision`() {
        val assistantMessage = MessageV2(
            content = "Latest",
            revisions = listOf(AssistantRevision(content = "Previous", createdAt = 100L, runId = "run-old")),
            activeRevisionIndex = 0,
            platformType = "platform-1",
            currentRunId = "run-new"
        )

        assertEquals("run-old", assistantMessage.effectiveRunId())
        assertEquals("run-new", assistantMessage.resetActiveRevision().effectiveRunId())
    }

    @Test
    fun `assistant export includes only the selected revision trace`() {
        val message = MessageV2(
            content = "Latest",
            revisions = listOf(AssistantRevision(content = "Previous", createdAt = 100L, runId = "run-old")),
            activeRevisionIndex = 0,
            platformType = "platform-1",
            currentRunId = "run-new"
        )
        val traces = mapOf(
            "run-old" to listOf(toolEvent("old-event", "old result")),
            "run-new" to listOf(toolEvent("new-event", "new result"))
        )

        val markdown = formatAssistantExport("OpenAI", message, traces)

        assertTrue(markdown.contains("Previous"))
        assertTrue(markdown.contains("old result"))
        assertFalse(markdown.contains("Latest"))
        assertFalse(markdown.contains("new result"))
    }

    @Test
    fun `selected revision without a run does not inherit the latest trace`() {
        val message = MessageV2(
            content = "Latest",
            revisions = listOf(AssistantRevision(content = "Legacy", createdAt = 100L)),
            activeRevisionIndex = 0,
            platformType = "platform-1",
            currentRunId = "run-new"
        )
        val traces = mapOf("run-new" to listOf(toolEvent("new-event", "new result")))

        assertNull(message.effectiveRunId())
        assertFalse(formatAssistantExport("OpenAI", message, traces).contains("new result"))
    }

    @Test
    fun `assistant export preserves interleaved text thinking and tool order`() {
        val message = MessageV2(
            content = "BeforeBetweenAfter",
            thoughts = "Checking",
            timeline = listOf(
                AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Before"),
                AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 0),
                AssistantTimelineItem(AssistantTimelineItemType.THINKING, content = "Checking"),
                AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Between"),
                AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 1),
                AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "After")
            ),
            platformType = "platform-1",
            currentRunId = "run-new"
        )
        val traces = mapOf(
            "run-new" to listOf(
                toolEvent("new-event", "first result"),
                toolEvent("new-event-2", "second result").copy(sequence = 1)
            )
        )

        val markdown = formatAssistantExport("OpenAI", message, traces)

        val orderedMarkers = listOf("Before", "first result", "Checking", "Between", "second result", "After")
        assertTrue(orderedMarkers.zipWithNext().all { (first, second) -> markdown.indexOf(first) < markdown.indexOf(second) })
    }

    @Test
    fun `legacy assistant export preserves data while declaring unknown event order`() {
        val message = MessageV2(
            content = "Legacy answer",
            thoughts = "Legacy reasoning",
            platformType = "platform-1",
            currentRunId = "run-new"
        )
        val traces = mapOf("run-new" to listOf(toolEvent("new-event", "legacy tool result")))

        val markdown = formatAssistantExport("OpenAI", message, traces)

        assertTrue(markdown.contains("Original event order is unavailable"))
        assertTrue(markdown.contains("Legacy reasoning"))
        assertTrue(markdown.contains("Legacy answer"))
        assertTrue(markdown.contains("legacy tool result"))
    }

    @Test
    fun `edited assistant export uses rebuilt state and preserves tool trace without stale text`() {
        val originalTimeline = listOf(
            AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Old before"),
            AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 0),
            AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Old after")
        )
        val edited = MessageV2(
            content = "Edited answer",
            thoughts = "Edited reasoning",
            timeline = rebuildAssistantTimelineForEdit(
                currentTimeline = originalTimeline,
                updatedContent = "Edited answer",
                updatedThoughts = "Edited reasoning"
            ),
            platformType = "platform-1",
            currentRunId = "run-new"
        )

        val markdown = formatAssistantExport(
            "OpenAI",
            edited,
            mapOf("run-new" to listOf(toolEvent("new-event", "preserved tool result")))
        )

        assertTrue(markdown.contains("Original event order is unavailable"))
        assertTrue(markdown.contains("Edited reasoning"))
        assertTrue(markdown.contains("Edited answer"))
        assertTrue(markdown.contains("preserved tool result"))
        assertFalse(markdown.contains("Old before"))
        assertFalse(markdown.contains("Old after"))
    }

    private fun toolEvent(eventId: String, result: String) = ToolEvent(
        eventId = eventId,
        runId = if (eventId == "old-event") "run-old" else "run-new",
        sequence = 0,
        callId = "call-$eventId",
        connectionUidSnapshot = null,
        connectionNameSnapshot = null,
        toolName = "web_search",
        modelToolName = "web_search",
        arguments = "{}",
        result = result,
        resultType = "TEXT",
        status = ToolEventStatus.COMPLETED
    )

    private fun agentRun(runId: String, assistantMessageId: Int, status: String) = AgentRun(
        runId = runId,
        chatId = 7,
        userMessageId = 1,
        assistantMessageId = assistantMessageId,
        profileUid = "profile-$assistantMessageId",
        providerSnapshot = "OPENAI",
        modelSnapshot = "model",
        status = status
    )

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
    fun `persistable messages keep blank assistant placeholders linked to runs`() {
        val groupedMessages = ChatViewModel.GroupedMessages(
            userMessages = listOf(
                MessageV2(chatId = 7, content = "Question", platformType = null, createdAt = 1L)
            ),
            assistantMessages = listOf(
                listOf(
                    MessageV2(
                        chatId = 7,
                        content = "",
                        platformType = "platform-1",
                        currentRunId = "run-123",
                        createdAt = 2L
                    )
                )
            )
        )

        val messages = persistableMessages(groupedMessages)

        assertEquals(listOf(null, "run-123"), messages.map { it.currentRunId })
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
