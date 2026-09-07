package dev.chungjungsoo.gptmobile.data.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunDraft
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventError
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentPersistenceDaoInstrumentedTest {
    private lateinit var database: ChatDatabaseV2

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ChatDatabaseV2::class.java
        ).addCallback(ChatDatabaseV2Migrations.AGENT_TOOL_BINDING_CALLBACK).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun persistAgentTurn_assignsIdsAndQueuesEveryProfileInOneTransaction() = runBlocking {
        val result = database.agentPersistenceDao().persistAgentTurn(
            PersistAgentTurnRequest(
                chatRoom = ChatRoomV2(
                    title = "Untitled Chat",
                    enabledPlatform = listOf("profile-1", "profile-2"),
                    createdAt = 100L,
                    updatedAt = 100L
                ),
                userMessage = MessageV2(
                    content = "Question",
                    platformType = null,
                    createdAt = 101L
                ),
                runs = listOf(
                    AgentRunDraft("run-1", "profile-1", "OPENAI", "gpt-5"),
                    AgentRunDraft("run-2", "profile-2", "ANTHROPIC", "claude")
                ),
                chatPlatformModels = mapOf(
                    "profile-1" to "gpt-5",
                    "profile-2" to "claude"
                )
            )
        )

        assertTrue(result.chatRoom.id > 0)
        assertTrue(result.userMessage.id > 0)
        assertEquals(listOf("profile-1", "profile-2"), result.assistantMessages.map { it.platformType })
        assertTrue(result.assistantMessages.all { it.id > 0 })
        assertEquals(listOf("run-1", "run-2"), result.assistantMessages.map { it.currentRunId })
        assertEquals(listOf(AgentRunStatus.QUEUED, AgentRunStatus.QUEUED), result.runs.map { it.status })
        assertEquals(result.userMessage.id, result.runs[0].userMessageId)
        assertEquals(result.assistantMessages[0].id, result.runs[0].assistantMessageId)

        assertEquals(3, database.messageDao().loadMessages(result.chatRoom.id).size)
        assertEquals(2, database.agentRunDao().getByChatId(result.chatRoom.id).size)
        assertEquals(2, database.chatPlatformModelDao().getByChatId(result.chatRoom.id).size)
    }

    @Test
    fun interruptActiveRuns_marksQueuedAndRunningWithoutTouchingTerminalRuns() = runBlocking {
        val persisted = database.agentPersistenceDao().persistAgentTurn(
            PersistAgentTurnRequest(
                chatRoom = ChatRoomV2(title = "Chat", enabledPlatform = listOf("profile-1")),
                userMessage = MessageV2(content = "Question", platformType = null),
                runs = listOf(AgentRunDraft("run-1", "profile-1", "OPENAI", "gpt-5")),
                chatPlatformModels = mapOf("profile-1" to "gpt-5")
            )
        )
        assertEquals(1, database.agentRunDao().markRunning("run-1", 200L))
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO tool_events (
                event_id, run_id, sequence, call_id, connection_uid_snapshot,
                connection_name_snapshot, tool_name, model_tool_name, arguments,
                result, result_type, status, is_error, started_at, completed_at, error
            ) VALUES (
                'event-active', 'run-1', 0, 'call-active', NULL, NULL,
                'read_url', 'read_url', '{}', NULL, NULL, 'RUNNING', 0, 201, NULL, NULL
            )
            """.trimIndent()
        )

        val interrupted = database.agentRunDao().interruptActiveRuns(completedAt = 300L)
        database.agentPersistenceDao().cancelInterruptedToolEvents(completedAt = 300L)
        val lateCompletion = database.agentRunDao().finishRunning(
            runId = "run-1",
            status = AgentRunStatus.COMPLETED,
            completedAt = 301L,
            terminalError = null
        )

        assertEquals(1, interrupted)
        assertEquals(0, lateCompletion)
        assertEquals(AgentRunStatus.INTERRUPTED, database.agentRunDao().getById("run-1")?.status)
        assertEquals(300L, database.agentRunDao().getById("run-1")?.completedAt)
        assertEquals(persisted.chatRoom.id, database.agentRunDao().getById("run-1")?.chatId)
        database.openHelper.writableDatabase.query(
            "SELECT status, completed_at, error FROM tool_events WHERE event_id = 'event-active'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("CANCELED", cursor.getString(0))
            assertEquals(300L, cursor.getLong(1))
            assertEquals(ToolEventError.INTERRUPTED_APP_STOPPED, cursor.getString(2))
        }
    }

    @Test
    fun finishActiveRun_terminalizesRunningRunBeforeLateCompletion() = runBlocking {
        database.agentPersistenceDao().persistAgentTurn(
            PersistAgentTurnRequest(
                chatRoom = ChatRoomV2(title = "Chat", enabledPlatform = listOf("profile-1")),
                userMessage = MessageV2(content = "Question", platformType = null),
                runs = listOf(AgentRunDraft("run-1", "profile-1", "OPENAI", "gpt-5")),
                chatPlatformModels = mapOf("profile-1" to "gpt-5")
            )
        )
        assertEquals(1, database.agentRunDao().markRunning("run-1", 200L))

        val canceled = database.agentRunDao().finishActive(
            runId = "run-1",
            status = AgentRunStatus.CANCELED,
            completedAt = 300L,
            terminalError = null
        )
        val lateCompletion = database.agentRunDao().finishRunning(
            runId = "run-1",
            status = AgentRunStatus.COMPLETED,
            completedAt = 301L,
            terminalError = null
        )

        assertEquals(1, canceled)
        assertEquals(0, lateCompletion)
        assertEquals(AgentRunStatus.CANCELED, database.agentRunDao().getById("run-1")?.status)
        assertEquals(300L, database.agentRunDao().getById("run-1")?.completedAt)
    }

    @Test
    fun persistAgentRetry_preservesPriorRunRevisionAndQueuesNewRun() = runBlocking {
        val persisted = database.agentPersistenceDao().persistAgentTurn(
            PersistAgentTurnRequest(
                chatRoom = ChatRoomV2(title = "Chat", enabledPlatform = listOf("profile-1")),
                userMessage = MessageV2(content = "Question", platformType = null),
                runs = listOf(AgentRunDraft("run-1", "profile-1", "OPENAI", "gpt-5")),
                chatPlatformModels = mapOf("profile-1" to "gpt-5")
            )
        )
        val firstAnswer = persisted.assistantMessages.single().copy(
            content = "First answer",
            thoughts = "First thoughts",
            attachments = listOf(ChatAttachment("/tmp/stale.txt", "/tmp/stale.txt", "stale.txt", "text/plain", 1)),
            revisions = listOf(AssistantRevision(content = "Older answer", createdAt = 90L)),
            activeRevisionIndex = 0
        )
        database.messageDao().editMessages(firstAnswer)
        database.agentRunDao().updateStatus("run-1", AgentRunStatus.COMPLETED, 200L, 250L, null)

        val retried = database.agentPersistenceDao().persistAgentRetry(
            PersistAgentRetryRequest(
                userMessage = persisted.userMessage,
                assistantMessage = firstAnswer,
                run = AgentRunDraft("run-2", "profile-1", "OPENAI", "gpt-5", createdAt = 300L)
            )
        )

        assertEquals(firstAnswer.id, retried.assistantMessage.id)
        assertEquals("", retried.assistantMessage.content)
        assertEquals(emptyList<ChatAttachment>(), retried.assistantMessage.attachments)
        assertEquals(-1, retried.assistantMessage.activeRevisionIndex)
        assertEquals("run-2", retried.assistantMessage.currentRunId)
        assertEquals("First answer", retried.assistantMessage.revisions.first().content)
        assertEquals("run-1", retried.assistantMessage.revisions.first().runId)
        assertEquals(AgentRunStatus.COMPLETED, database.agentRunDao().getById("run-1")?.status)
        assertEquals(AgentRunStatus.QUEUED, database.agentRunDao().getById("run-2")?.status)
        assertEquals(firstAnswer.id, database.agentRunDao().getById("run-2")?.assistantMessageId)
    }

    @Test
    fun finishAgentRun_persistsAssistantAndTerminalStatusTogether() = runBlocking {
        val persisted = persistTurn(newChat(), "Question", "run-1", 100L)
        database.agentRunDao().updateStatus("run-1", AgentRunStatus.RUNNING, 101L, null, null)
        val answer = persisted.assistantMessages.single().copy(content = "Final answer", thoughts = "Reasoning")

        database.agentPersistenceDao().finishAgentRun(
            assistantMessage = answer,
            runId = "run-1",
            status = AgentRunStatus.COMPLETED,
            startedAt = 101L,
            completedAt = 102L,
            terminalError = null
        )

        assertEquals("Final answer", database.messageDao().loadMessages(persisted.chatRoom.id).first { it.id == answer.id }.content)
        assertEquals(AgentRunStatus.COMPLETED, database.agentRunDao().getById("run-1")?.status)
    }

    @Test
    fun editedTurn_truncatesFutureHistoryBeforeQueuingReplacementRun() = runBlocking {
        val first = persistTurn(chatRoom = newChat(), question = "First", runId = "run-1", createdAt = 100L)
        val second = persistTurn(
            chatRoom = first.chatRoom,
            question = "Second",
            runId = "run-2",
            createdAt = 200L
        )
        persistTurn(
            chatRoom = first.chatRoom,
            question = "Future",
            runId = "run-3",
            createdAt = 300L
        )
        val editedUser = second.userMessage.copy(content = "Edited second", createdAt = 400L)

        database.agentPersistenceDao().saveChatSnapshot(
            chatRoom = first.chatRoom.copy(updatedAt = 400L),
            messages = listOf(first.userMessage, first.assistantMessages.single(), editedUser),
            chatPlatformModels = mapOf("profile-1" to "gpt-5")
        )
        val replacement = database.agentPersistenceDao().persistAgentTurn(
            PersistAgentTurnRequest(
                chatRoom = first.chatRoom.copy(updatedAt = 400L),
                userMessage = editedUser,
                runs = listOf(AgentRunDraft("run-4", "profile-1", "OPENAI", "gpt-5", createdAt = 401L)),
                chatPlatformModels = mapOf("profile-1" to "gpt-5")
            )
        )

        val messages = database.messageDao().loadMessages(first.chatRoom.id).sortedWith(compareBy(MessageV2::createdAt, MessageV2::id))
        assertEquals(listOf("First", "", "Edited second", ""), messages.map { it.content })
        assertEquals(second.userMessage.id, replacement.userMessage.id)
        assertEquals("run-4", replacement.assistantMessages.single().currentRunId)
        assertEquals(listOf("run-1", "run-4"), database.agentRunDao().getByChatId(first.chatRoom.id).map { it.runId })
    }

    @Test
    fun duplicateChatWithHistory_regeneratesMessageRunAndEventIds() = runBlocking {
        val persisted = database.agentPersistenceDao().persistAgentTurn(
            PersistAgentTurnRequest(
                chatRoom = ChatRoomV2(title = "Chat", enabledPlatform = listOf("profile-1")),
                userMessage = MessageV2(content = "Question", platformType = null, createdAt = 100L),
                runs = listOf(AgentRunDraft("run-1", "profile-1", "OPENAI", "gpt-5", createdAt = 101L)),
                chatPlatformModels = mapOf("profile-1" to "gpt-5")
            )
        )
        database.messageDao().editMessages(persisted.assistantMessages.single().copy(content = "Answer"))
        database.agentRunDao().updateStatus("run-1", AgentRunStatus.COMPLETED, 102L, 103L, null)
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO tool_events (
                event_id, run_id, sequence, call_id, connection_uid_snapshot,
                connection_name_snapshot, tool_name, model_tool_name, arguments,
                result, result_type, status, is_error, started_at, completed_at, error
            ) VALUES (
                'event-1', 'run-1', 0, 'call-1', 'connection-1', 'Fixture',
                'lookup', 'fixture__lookup', '{}', 'ok', 'TEXT', 'COMPLETED', 0, 102, 103, NULL
            )
            """.trimIndent()
        )

        val duplicate = database.agentPersistenceDao().duplicateChatWithHistory(
            sourceChatId = persisted.chatRoom.id,
            title = "Chat (copy)",
            timestamp = 500L
        )
        val duplicateMessages = database.messageDao().loadMessages(duplicate.id)
        val duplicateRuns = database.agentRunDao().getByChatId(duplicate.id)

        assertTrue(duplicate.id != persisted.chatRoom.id)
        assertEquals(listOf("Question", "Answer"), duplicateMessages.sortedBy { it.createdAt }.map { it.content })
        assertTrue(duplicateMessages.none { it.id in listOf(persisted.userMessage.id, persisted.assistantMessages.single().id) })
        assertEquals(1, duplicateRuns.size)
        assertTrue(duplicateRuns.single().runId != "run-1")
        assertEquals(AgentRunStatus.COMPLETED, duplicateRuns.single().status)
        assertEquals(duplicateRuns.single().runId, duplicateMessages.first { it.platformType != null }.currentRunId)
        database.openHelper.writableDatabase.query(
            "SELECT event_id, run_id, call_id FROM tool_events WHERE run_id = ?",
            arrayOf(duplicateRuns.single().runId)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getString(0) != "event-1")
            assertEquals(duplicateRuns.single().runId, cursor.getString(1))
            assertEquals("call-1", cursor.getString(2))
        }
        assertEquals(mapOf("profile-1" to "gpt-5"), database.chatPlatformModelDao().getByChatId(duplicate.id).associate { it.platformUid to it.model })
    }

    @Test
    fun deletePlatform_removesItsToolBindings() = runBlocking {
        val platform = PlatformV2(
            uid = "profile-1",
            name = "OpenAI",
            compatibleType = ClientType.OPENAI,
            apiUrl = "https://api.openai.com/v1/",
            model = "gpt-5"
        )
        val platformId = database.platformDao().addPlatform(platform)
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO agent_tool_bindings (binding_uid, profile_uid, connection_uid, tool_name, created_at) " +
                "VALUES ('binding-1', 'profile-1', NULL, 'read_url', 1)"
        )

        database.platformDao().deletePlatform(platform.copy(id = platformId.toInt()))

        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM agent_tool_bindings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        assertEquals(emptyList<PlatformV2>(), database.platformDao().getPlatforms())
    }

    @Test
    fun builtInToolBinding_rejectsDuplicateProfileAndTool() {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO agent_tool_bindings (binding_uid, profile_uid, connection_uid, tool_name, created_at) " +
                "VALUES ('binding-1', 'profile-1', NULL, 'read_url', 1)"
        )

        var error: Throwable? = null
        try {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO agent_tool_bindings (binding_uid, profile_uid, connection_uid, tool_name, created_at) " +
                    "VALUES ('binding-2', 'profile-1', NULL, 'read_url', 2)"
            )
        } catch (thrown: Throwable) {
            error = thrown
        }

        assertTrue(error is SQLiteConstraintException)
    }

    private suspend fun persistTurn(
        chatRoom: ChatRoomV2,
        question: String,
        runId: String,
        createdAt: Long
    ) = database.agentPersistenceDao().persistAgentTurn(
        PersistAgentTurnRequest(
            chatRoom = chatRoom,
            userMessage = MessageV2(
                chatId = chatRoom.id,
                content = question,
                platformType = null,
                createdAt = createdAt
            ),
            runs = listOf(
                AgentRunDraft(
                    runId = runId,
                    profileUid = "profile-1",
                    providerSnapshot = "OPENAI",
                    modelSnapshot = "gpt-5",
                    createdAt = createdAt + 1
                )
            ),
            chatPlatformModels = mapOf("profile-1" to "gpt-5")
        )
    )

    private fun newChat() = ChatRoomV2(
        title = "Chat",
        enabledPlatform = listOf("profile-1"),
        createdAt = 99L,
        updatedAt = 99L
    )
}
