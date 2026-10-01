package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.app.Application
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.chungjungsoo.gptmobile.data.agent.ActiveAgentRun
import dev.chungjungsoo.gptmobile.data.agent.AgentRunCoordinator
import dev.chungjungsoo.gptmobile.data.agent.AgentRunNotice
import dev.chungjungsoo.gptmobile.data.agent.AgentRunRequest
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.AgentToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.ConversationMode
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryResult
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnResult
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatPromptQueueTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val messages = MutableStateFlow<List<MessageV2>>(emptyList())
    private val runs = MutableStateFlow<List<AgentRun>>(emptyList())
    private val activeRuns = MutableStateFlow<Map<String, ActiveAgentRun>>(emptyMap())
    private val submissions = mutableListOf<PersistAgentTurnRequest>()
    private val starts = mutableListOf<List<AgentRunRequest>>()
    private val synthesisGate = CompletableDeferred<Unit>()
    private var membershipGate: CompletableDeferred<Unit>? = null
    private var failMembershipUpdate = false
    private var nextId = 10

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `queue drains when coordinator releases completed run and keeps the current draft`() = runTest(dispatcher) {
        val model = createViewModel()
        runCurrent()
        send(model, "Second prompt")
        send(model, "Third prompt")
        model.question.setTextAndPlaceCursorAtEnd("Unsent draft")
        assertEquals(2, model.queuedPromptCount.value)

        completePersistedRuns()
        runCurrent()
        assertTrue(submissions.isEmpty())
        activeRuns.value = emptyMap()
        runCurrent()

        assertEquals(listOf("Second prompt"), submissions.map { it.userMessage.content })
        assertEquals("Unsent draft", model.question.text.toString())
        assertEquals(1, model.queuedPromptCount.value)
        completePersistedRuns()
        runCurrent()
        activeRuns.value = emptyMap()
        runCurrent()

        assertEquals(listOf("Second prompt", "Third prompt"), submissions.map { it.userMessage.content })
        assertEquals("Unsent draft", model.question.text.toString())
        assertEquals(0, model.queuedPromptCount.value)
    }

    @Test
    fun `queue waits for persisted completion when coordinator finishes first`() = runTest(dispatcher) {
        val model = createViewModel()
        runCurrent()
        send(model, "Follow up")
        activeRuns.value = emptyMap()
        runCurrent()
        assertTrue(submissions.isEmpty())

        completePersistedRuns()
        runCurrent()
        assertEquals(listOf("Follow up"), submissions.map { it.userMessage.content })
        assertEquals(0, model.queuedPromptCount.value)
    }

    @Test
    fun `queued prompt waits through combined synthesis persistence and generation`() = runTest(dispatcher) {
        val model = createViewModel(combined = true)
        runCurrent()
        send(model, "Follow up")
        completePersistedRuns()
        runCurrent()
        activeRuns.value = emptyMap()
        runCurrent()
        assertTrue(submissions.isEmpty())
        assertTrue(starts.isEmpty())

        synthesisGate.complete(Unit)
        runCurrent()
        assertEquals(1, starts.size)
        assertTrue(starts.single().single().runId.startsWith("combined-"))
        assertTrue(submissions.isEmpty())

        completePersistedRuns()
        runCurrent()
        activeRuns.value = emptyMap()
        runCurrent()
        assertEquals(listOf("Follow up"), submissions.map { it.userMessage.content })
    }

    private fun send(model: ChatViewModel, text: String) {
        model.question.setTextAndPlaceCursorAtEnd(text)
        model.askQuestion()
    }

    @Test
    fun `queue waits while every profile is paused and resumes without losing the draft`() = runTest(dispatcher) {
        val model = createViewModel()
        runCurrent()
        model.togglePlatformDisabled("profile-1")
        send(model, "Queued while paused")
        model.question.setTextAndPlaceCursorAtEnd("Next draft")
        completePersistedRuns()
        activeRuns.value = emptyMap()
        runCurrent()
        assertTrue(submissions.isEmpty())
        assertEquals(1, model.queuedPromptCount.value)
        model.togglePlatformDisabled("profile-1")
        runCurrent()
        assertEquals(listOf("Queued while paused"), submissions.map { it.userMessage.content })
        assertEquals("Next draft", model.question.text.toString())
        assertEquals(0, model.queuedPromptCount.value)
    }

    @Test
    fun `sending during attachment preparation keeps the draft and does not enqueue incomplete content`() = runTest(dispatcher) {
        val model = createViewModel()
        runCurrent()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = java.io.File(context.cacheDir, "preparing.txt").apply { writeText("Document content") }
        try {
            model.addSelectedFile(file.absolutePath)
            model.question.setTextAndPlaceCursorAtEnd("Read this document")
            model.askQuestion()
            assertEquals("Read this document", model.question.text.toString())
            assertEquals(0, model.queuedPromptCount.value)
            assertTrue(submissions.isEmpty())
        } finally {
            store.clear()
            file.delete()
        }
    }

    @Test
    fun `adding a profile releases a paused queue only after membership is saved and refreshes its tools`() = runTest(dispatcher) {
        val model = createViewModel(availableProfileCount = 2)
        runCurrent()
        model.togglePlatformDisabled("profile-1")
        send(model, "Waiting for another profile")
        model.question.setTextAndPlaceCursorAtEnd("Unsent draft")
        completePersistedRuns()
        activeRuns.value = emptyMap()
        runCurrent()
        assertTrue(submissions.isEmpty())
        assertTrue(model.availableChatTools.value.none { it.id == "profile-2-tools:search" })

        membershipGate = CompletableDeferred()
        model.setPlatformMembership("profile-2", true)
        runCurrent()
        assertEquals(listOf("profile-1"), model.activePlatformUids.value)
        assertTrue(submissions.isEmpty())
        membershipGate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf("Waiting for another profile"), submissions.map { it.userMessage.content })
        assertEquals(listOf("profile-2"), starts.single().map { it.platform.uid })
        assertEquals(listOf("profile-1", "profile-2"), model.enabledPlatformsInChat)
        assertEquals("Unsent draft", model.question.text.toString())
        assertTrue(model.availableChatTools.value.any { it.id == "profile-2-tools:search" })
        assertEquals(0, model.queuedPromptCount.value)
    }

    @Test
    fun `rapid membership updates use the last selection before starting queued work`() = runTest(dispatcher) {
        val model = createViewModel(availableProfileCount = 3)
        runCurrent()
        model.togglePlatformDisabled("profile-1")
        completePersistedRuns()
        activeRuns.value = emptyMap()
        runCurrent()
        membershipGate = CompletableDeferred()

        model.setPlatformMembership("profile-2", true)
        runCurrent()
        model.setPlatformMembership("profile-3", true)
        model.setPlatformMembership("profile-2", false)
        send(model, "Use the final selection")
        runCurrent()
        assertTrue(submissions.isEmpty())
        membershipGate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf("profile-1", "profile-3"), model.activePlatformUids.value)
        assertEquals(listOf("profile-3"), starts.single().map { it.platform.uid })
        assertTrue(model.availableChatTools.value.none { it.id == "profile-2-tools:search" })
    }

    @Test
    fun `failed membership changes keep the paused queue intact for a successful retry`() = runTest(dispatcher) {
        val model = createViewModel(availableProfileCount = 2)
        runCurrent()
        model.togglePlatformDisabled("profile-1")
        send(model, "Keep this prompt")
        completePersistedRuns()
        activeRuns.value = emptyMap()
        runCurrent()
        failMembershipUpdate = true
        model.setPlatformMembership("profile-2", true)
        runCurrent()

        assertEquals(listOf("profile-1"), model.activePlatformUids.value)
        assertEquals(setOf("profile-1"), model.disabledPlatformUids.value)
        assertEquals(1, model.queuedPromptCount.value)
        assertTrue(submissions.isEmpty())
        failMembershipUpdate = false
        model.setPlatformMembership("profile-2", true)
        runCurrent()

        assertEquals(listOf("Keep this prompt"), submissions.map { it.userMessage.content })
        assertEquals(0, model.queuedPromptCount.value)
    }

    private fun completePersistedRuns() {
        messages.value = messages.value.map { message ->
            if (message.platformType != null) message.copy(content = "Finished response") else message
        }
        runs.value = runs.value.map { it.copy(status = AgentRunStatus.COMPLETED) }
    }

    private fun createViewModel(combined: Boolean = false, availableProfileCount: Int = if (combined) 2 else 1): ChatViewModel {
        val profiles = (1..availableProfileCount).map { index ->
            PlatformV2(
                uid = "profile-$index",
                name = "Profile $index",
                enabled = true,
                compatibleType = ClientType.OPENROUTER,
                apiUrl = "https://openrouter.ai/api/v1",
                model = "model-$index"
            )
        }
        val members = profiles.take(if (combined) 2 else 1)
        val room = ChatRoomV2(
            id = 7,
            title = "Queue test",
            enabledPlatform = members.map { it.uid },
            activePlatform = members.map { it.uid },
            conversationMode = if (combined) ConversationMode.COMBINED else ConversationMode.STANDARD
        )
        messages.value = listOf(MessageV2(id = 1, chatId = 7, content = "First prompt", platformType = null)) + members.mapIndexed { index, profile ->
            MessageV2(id = index + 2, chatId = 7, content = "", platformType = profile.uid, currentRunId = "first-$index")
        }
        runs.value = members.mapIndexed { index, profile ->
            AgentRun("first-$index", 7, 1, index + 2, profile.uid, profile.compatibleType.name, profile.model, AgentRunStatus.RUNNING)
        }
        activeRuns.value = runs.value.associate { it.runId to ActiveAgentRun(it.runId, 7, it.profileUid, it.assistantMessageId) }
        val repository = mockk<ChatRepository>(relaxed = true)
        every { repository.observeMessagesV2(7) } returns messages
        every { repository.observeMessageWindow(7, any()) } returns messages
        every { repository.observeTurnCount(7) } returns messages.map { rows -> rows.count { it.platformType == null } }
        every { repository.observeAgentRuns(7) } returns runs
        every { repository.observeToolEvents(7) } returns flowOf(emptyList())
        coEvery { repository.fetchChatListV2() } returns listOf(room)
        coEvery { repository.fetchMessagesV2(7) } answers { messages.value }
        coEvery { repository.fetchChatPlatformModels(7) } returns profiles.associate { it.uid to it.model }
        coEvery { repository.updateChatPlatforms(any(), any()) } coAnswers {
            membershipGate?.await()
            check(!failMembershipUpdate) { "Database unavailable" }
            val current = firstArg<ChatRoomV2>()
            val active = secondArg<List<String>>()
            current.copy(enabledPlatform = (current.enabledPlatform + active).distinct(), activePlatform = active)
        }
        coEvery { repository.persistAgentTurn(any()) } answers {
            val request = firstArg<PersistAgentTurnRequest>()
            submissions += request
            val user = request.userMessage.copy(id = nextId++, chatId = 7)
            val assistants = request.runs.map { draft ->
                MessageV2(id = nextId++, chatId = 7, content = "", platformType = draft.profileUid, currentRunId = draft.runId)
            }
            val persistedRuns = request.runs.zip(assistants).map { (draft, message) ->
                AgentRun(draft.runId, 7, user.id, message.id, draft.profileUid, draft.providerSnapshot, draft.modelSnapshot)
            }
            messages.value = messages.value + user + assistants
            runs.value = runs.value + persistedRuns
            PersistAgentTurnResult(request.chatRoom, user, assistants, persistedRuns)
        }
        coEvery { repository.persistAgentRetry(any()) } coAnswers {
            val request = firstArg<PersistAgentRetryRequest>()
            synthesisGate.await()
            val assistant = request.assistantMessage.copy(currentRunId = request.run.runId, content = "")
            val run = AgentRun(request.run.runId, 7, request.userMessage.id, assistant.id, request.run.profileUid, request.run.providerSnapshot, request.run.modelSnapshot)
            messages.value = messages.value.map { if (it.id == assistant.id) assistant else it }
            runs.value = runs.value + run
            PersistAgentRetryResult(assistant, run)
        }
        val settings = mockk<SettingRepository>(relaxed = true)
        coEvery { settings.fetchPlatformV2s() } returns profiles
        coEvery { settings.getFeatureSettings() } returns AppFeatureSettings()
        every { settings.observeDebugMode() } returns flowOf(false)
        every { settings.observeFeatureSettings() } returns flowOf(AppFeatureSettings(automaticConversationTitles = false))
        val coordinator = mockk<AgentRunCoordinator>(relaxed = true)
        every { coordinator.activeRuns } returns activeRuns
        every { coordinator.streamMessages } returns MutableStateFlow(emptyMap())
        every { coordinator.notices } returns MutableSharedFlow<AgentRunNotice>()
        every { coordinator.start(any()) } answers {
            val requests = firstArg<List<AgentRunRequest>>()
            starts += requests
            activeRuns.value = activeRuns.value + requests.associate { it.runId to ActiveAgentRun(it.runId, 7, it.platform.uid) }
        }
        val localModels = mockk<LocalModelRepository>(relaxed = true)
        every { localModels.observeAll() } returns flowOf(emptyList())
        val catalog = mockk<ModelCatalogRepository>(relaxed = true)
        coEvery { catalog.getCachedVisibleEntries() } returns emptyList()
        val tools = mockk<ToolConnectionRepository>(relaxed = true)
        coEvery { tools.getAllConnections() } returns listOf(
            ToolConnection("profile-2-tools", "research", "Research", ToolConnectionType.MCP, "https://example.com/mcp", ToolConnectionAuthType.NONE, null, null)
        )
        coEvery { tools.listBindingsByProfile("profile-2") } returns listOf(
            AgentToolBinding("binding", "profile-2", "profile-2-tools", "search")
        )
        return ChatViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("chatRoomId" to 7, "enabledPlatforms" to members.joinToString(",") { it.uid })
            ),
            context = ApplicationProvider.getApplicationContext(),
            chatRepository = repository,
            settingRepository = settings,
            delegationRecoveryInteractions = mockk(relaxed = true),
            attachmentUploadCoordinator = mockk(relaxed = true),
            agentRunCoordinator = coordinator,
            toolConnectionRepository = tools,
            localModelRepository = localModels,
            modelCatalogRepository = catalog,
            freeModelToolConsentStore = dev.chungjungsoo.gptmobile.data.permissions.FreeModelToolConsentStore(
                ApplicationProvider.getApplicationContext()
            ),
            conversationReadStateStore = dev.chungjungsoo.gptmobile.data.chat.ConversationReadStateStore(
                ApplicationProvider.getApplicationContext()
            )
        ).also { store.put("chat", it) }
    }
}
