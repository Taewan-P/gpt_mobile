package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.agent.AgentResourceLink
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryResult
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnResult
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventResultType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolEventRecorderTest {
    private val dao = FakeAgentPersistenceDao()
    private val recorder = ToolEventRecorder(dao, eventIdFactory = { "event-${dao.rows.size + 1}" })

    @Test
    fun startTool_recordsRunningEventWithStableCallIdAndCallerSequence() = runBlocking {
        val event = recorder.startTool(
            runId = "run-1",
            sequence = 7,
            callId = "call-exact",
            toolName = "search",
            modelToolName = "web__search",
            arguments = buildJsonObject { put("query", "weather") },
            connectionUid = "connection-1",
            connectionName = "Web",
            startedAt = 100L
        )

        assertEquals("event-1", event.eventId)
        assertEquals("call-exact", event.callId)
        assertEquals(7, event.sequence)
        assertEquals("search", event.toolName)
        assertEquals("web__search", event.modelToolName)
        assertEquals("""{"query":"weather"}""", event.arguments)
        assertEquals("connection-1", event.connectionUidSnapshot)
        assertEquals("Web", event.connectionNameSnapshot)
        assertEquals(ToolEventStatus.RUNNING, event.status)
        assertEquals(100L, event.startedAt)
        assertEquals(event, dao.rows.single())
    }

    @Test
    fun startTool_boundsUtf8ArgumentsWithoutSplittingCodePoint() = runBlocking {
        val value = "a".repeat(65535) + "\uD83D\uDE00"

        val event = recorder.startTool(
            runId = "run-1",
            sequence = 0,
            callId = "call-1",
            toolName = "echo",
            modelToolName = "echo",
            arguments = buildJsonObject { put("value", value) },
            startedAt = 100L
        )

        assertTrue(event.arguments.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
        assertTrue('\uFFFD' !in event.arguments)
        assertTrue(!event.arguments.last().isSurrogate())
    }

    @Test
    fun finishTool_updatesStartedEventWithTextJsonAndResourceLinks() = runBlocking {
        val text = recorder.startTool("run-1", 0, "call-text", "echo", "echo", buildJsonObject {}, startedAt = 100L)
        recorder.finishTool(text.eventId, AgentToolResult("call-text", ToolResultContent.Text("ok"), isError = false), completedAt = 110L)

        val json = recorder.startTool("run-1", 1, "call-json", "lookup", "lookup", buildJsonObject {}, startedAt = 120L)
        recorder.finishTool(json.eventId, AgentToolResult("call-json", ToolResultContent.Json(buildJsonObject { put("answer", 42) }), isError = false), completedAt = 130L)

        val links = recorder.startTool("run-1", 2, "call-links", "files", "files", buildJsonObject {}, startedAt = 140L)
        recorder.finishTool(
            links.eventId,
            AgentToolResult(
                "call-links",
                ToolResultContent.ResourceLinks(
                    listOf(
                        AgentResourceLink(uri = "file://one", name = "One", mimeType = "text/plain"),
                        AgentResourceLink(uri = "file://two")
                    )
                ),
                isError = false
            ),
            completedAt = 150L
        )

        assertEquals(
            listOf(ToolEventResultType.TEXT, ToolEventResultType.JSON, ToolEventResultType.RESOURCE_LINKS),
            dao.rows.map { it.resultType }
        )
        assertEquals("ok", dao.rows[0].result)
        assertEquals("""{"answer":42}""", dao.rows[1].result)
        val serializedLinks = Json.parseToJsonElement(dao.rows[2].result!!).jsonArray[0].jsonObject
        assertEquals("file://one", serializedLinks.getValue("uri").jsonPrimitive.content)
        assertEquals("One", serializedLinks.getValue("name").jsonPrimitive.content)
        assertEquals("text/plain", serializedLinks.getValue("mimeType").jsonPrimitive.content)
        assertEquals(setOf("uri", "name", "mimeType"), Json.parseToJsonElement(dao.rows[2].result!!).jsonArray[1].jsonObject.keys)
        assertEquals(listOf(ToolEventStatus.COMPLETED, ToolEventStatus.COMPLETED, ToolEventStatus.COMPLETED), dao.rows.map { it.status })
        assertEquals(listOf("call-text", "call-json", "call-links"), dao.rows.map { it.callId })
    }

    @Test
    fun finishTool_boundsResultAndMarksErrorsFailed() = runBlocking {
        val event = recorder.startTool("run-1", 0, "call-error", "echo", "echo", buildJsonObject {}, startedAt = 100L)

        recorder.finishTool(
            eventId = event.eventId,
            result = AgentToolResult("call-error", ToolResultContent.Text("a".repeat(70 * 1024)), isError = true),
            completedAt = 110L,
            error = "boom"
        )

        val stored = dao.rows.single()
        assertEquals(ToolEventStatus.FAILED, stored.status)
        assertEquals(true, stored.isError)
        assertEquals("boom", stored.error)
        assertEquals(110L, stored.completedAt)
        assertTrue(stored.result!!.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
    }

    @Test
    fun finishTool_persistsTraceOnlyContentWithoutChangingModelContent() = runBlocking {
        val event = recorder.startTool("run-1", 0, "call-trace", "image", "image", buildJsonObject {}, startedAt = 100L)

        recorder.finishTool(
            eventId = event.eventId,
            result = AgentToolResult(
                callId = "call-trace",
                content = ToolResultContent.Text("safe model result"),
                isError = false,
                traceContent = ToolResultContent.Text("image/png omitted from model context")
            ),
            completedAt = 110L
        )

        assertEquals("image/png omitted from model context", dao.rows.single().result)
    }

    @Test
    fun finishTool_callIdMismatchReturnsNullAndDoesNotWrite() = runBlocking {
        val event = recorder.startTool("run-1", 0, "call-expected", "echo", "echo", buildJsonObject {}, startedAt = 100L)

        val finished = recorder.finishTool(
            eventId = event.eventId,
            result = AgentToolResult("call-other", ToolResultContent.Text("wrong"), isError = false),
            completedAt = 110L
        )

        assertEquals(null, finished)
        assertEquals(event, dao.rows.single())
    }

    @Test
    fun finishTool_cancelBeforeFinishReturnsNullAndPreservesCanceledRow() = runBlocking {
        val event = recorder.startTool("run-1", 0, "call-1", "echo", "echo", buildJsonObject {}, startedAt = 100L)
        recorder.cancelRun("run-1", completedAt = 105L)
        val canceled = dao.rows.single()

        val finished = recorder.finishTool(
            eventId = event.eventId,
            result = AgentToolResult("call-1", ToolResultContent.Text("late"), isError = false),
            completedAt = 110L
        )

        assertEquals(null, finished)
        assertEquals(canceled, dao.rows.single())
    }

    @Test
    fun finishTool_duplicateFinishReturnsNullAndPreservesFirstTerminalRow() = runBlocking {
        val event = recorder.startTool("run-1", 0, "call-1", "echo", "echo", buildJsonObject {}, startedAt = 100L)
        val first = recorder.finishTool(
            eventId = event.eventId,
            result = AgentToolResult("call-1", ToolResultContent.Text("first"), isError = false),
            completedAt = 110L
        )

        val second = recorder.finishTool(
            eventId = event.eventId,
            result = AgentToolResult("call-1", ToolResultContent.Text("second"), isError = false),
            completedAt = 120L
        )

        assertEquals(null, second)
        assertEquals(first, dao.rows.single())
    }

    @Test
    fun finishTool_downgradesOversizedJsonResultToBoundedTextPreview() = runBlocking {
        val event = recorder.startTool("run-1", 0, "call-json", "lookup", "lookup", buildJsonObject {}, startedAt = 100L)

        recorder.finishTool(
            eventId = event.eventId,
            result = AgentToolResult(
                "call-json",
                ToolResultContent.Json(buildJsonObject { put("value", "a".repeat(70 * 1024)) }),
                isError = false
            ),
            completedAt = 110L
        )

        val stored = dao.rows.single()
        assertEquals(ToolEventResultType.TEXT, stored.resultType)
        assertTrue(stored.result!!.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
    }

    @Test
    fun finishTool_downgradesOversizedResourceLinksResultToBoundedTextPreview() = runBlocking {
        val event = recorder.startTool("run-1", 0, "call-links", "files", "files", buildJsonObject {}, startedAt = 100L)

        recorder.finishTool(
            eventId = event.eventId,
            result = AgentToolResult(
                "call-links",
                ToolResultContent.ResourceLinks(
                    listOf(AgentResourceLink(uri = "file://${"a".repeat(70 * 1024)}"))
                ),
                isError = false
            ),
            completedAt = 110L
        )

        val stored = dao.rows.single()
        assertEquals(ToolEventResultType.TEXT, stored.resultType)
        assertTrue(stored.result!!.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
    }

    @Test
    fun cancelRun_marksOnlyActiveEventsCanceled() = runBlocking {
        dao.rows += event("done", "run-1", 0, ToolEventStatus.COMPLETED, completedAt = 90L)
        dao.rows += event("running", "run-1", 1, ToolEventStatus.RUNNING)
        dao.rows += event("pending", "run-1", 2, ToolEventStatus.PENDING)
        dao.rows += event("other", "run-2", 0, ToolEventStatus.RUNNING)

        recorder.cancelRun("run-1", completedAt = 200L)

        assertEquals(ToolEventStatus.COMPLETED, dao.rows[0].status)
        assertEquals(90L, dao.rows[0].completedAt)
        assertEquals(ToolEventStatus.CANCELED, dao.rows[1].status)
        assertEquals(200L, dao.rows[1].completedAt)
        assertEquals(ToolEventStatus.CANCELED, dao.rows[2].status)
        assertEquals(ToolEventStatus.RUNNING, dao.rows[3].status)
    }

    @Test
    fun getEventsReturnsOrderedRowsAndEmptySetDoesNotQueryDao() = runBlocking {
        dao.rows += event("event-3", "run-2", 1, ToolEventStatus.COMPLETED)
        dao.rows += event("event-1", "run-1", 2, ToolEventStatus.COMPLETED)
        dao.rows += event("event-2", "run-1", 1, ToolEventStatus.COMPLETED)

        assertEquals(listOf("event-2", "event-1"), recorder.getEvents("run-1").map { it.eventId })
        assertEquals(listOf("event-2", "event-1", "event-3"), recorder.getEvents(setOf("run-2", "run-1")).map { it.eventId })

        dao.bulkQueryCount = 0
        assertEquals(emptyList<ToolEvent>(), recorder.getEvents(emptySet()))
        assertEquals(0, dao.bulkQueryCount)
    }

    @Test
    fun observeChat_delegatesPositiveChatIdsToDao() = runBlocking {
        val orderedEvents = listOf(
            event("event-2", "run-a", 2, ToolEventStatus.RUNNING),
            event("event-1", "run-b", 1, ToolEventStatus.COMPLETED)
        )
        dao.observedToolEvents.value = orderedEvents

        assertEquals(orderedEvents.map { it.eventId }, recorder.observeChat(42).first().map { it.eventId })
        assertEquals(listOf(42), dao.observedChatIds)
    }

    @Test
    fun observeChat_zeroChatIdEmitsEmptyListWithoutQueryingDao() = runBlocking {
        dao.observedToolEvents.value = listOf(event("event-1", "run-1", 0, ToolEventStatus.RUNNING))

        assertEquals(emptyList<ToolEvent>(), recorder.observeChat(0).first())
        assertEquals(emptyList<Int>(), dao.observedChatIds)
    }

    @Test
    fun observeChat_negativeChatIdEmitsEmptyListWithoutQueryingDao() = runBlocking {
        dao.observedToolEvents.value = listOf(event("event-1", "run-1", 0, ToolEventStatus.RUNNING))

        assertEquals(emptyList<ToolEvent>(), recorder.observeChat(-1).first())
        assertEquals(emptyList<Int>(), dao.observedChatIds)
    }

    private fun event(
        eventId: String,
        runId: String,
        sequence: Int,
        status: String,
        completedAt: Long? = null
    ) = ToolEvent(
        eventId = eventId,
        runId = runId,
        sequence = sequence,
        callId = "call-$eventId",
        connectionUidSnapshot = null,
        connectionNameSnapshot = null,
        toolName = "tool",
        modelToolName = "tool",
        arguments = "{}",
        result = null,
        resultType = null,
        status = status,
        completedAt = completedAt
    )
}

private class FakeAgentPersistenceDao : AgentPersistenceDao {
    val rows = mutableListOf<ToolEvent>()
    val observedToolEvents = MutableStateFlow(emptyList<ToolEvent>())
    val observedChatIds = mutableListOf<Int>()
    var bulkQueryCount = 0

    override suspend fun insertChatRoom(chatRoom: ChatRoomV2): Long = unused()
    override suspend fun updateChatRoom(chatRoom: ChatRoomV2) = unused<Unit>()
    override suspend fun insertMessage(message: MessageV2): Long = unused()
    override suspend fun insertRun(run: AgentRun) = unused<Unit>()
    override suspend fun insertToolEvent(event: ToolEvent) {
        rows += event
    }
    override suspend fun deleteMessages(messages: List<MessageV2>) = unused<Unit>()
    override suspend fun upsertModels(models: List<ChatPlatformModelV2>) = unused<Unit>()
    override suspend fun getChatRoom(chatId: Int): ChatRoomV2? = unused()
    override suspend fun getMessages(chatId: Int): List<MessageV2> = unused()
    override suspend fun getModels(chatId: Int): List<ChatPlatformModelV2> = unused()
    override suspend fun getCompletedRuns(chatId: Int): List<AgentRun> = unused()
    override suspend fun getToolEvents(runIds: List<String>): List<ToolEvent> {
        bulkQueryCount++
        return rows
            .filter { it.runId in runIds }
            .sortedWith(compareBy(ToolEvent::runId, ToolEvent::sequence))
    }
    override suspend fun persistAgentTurn(request: PersistAgentTurnRequest): PersistAgentTurnResult = unused()
    override suspend fun saveChatSnapshot(chatRoom: ChatRoomV2, messages: List<MessageV2>, chatPlatformModels: Map<String, String>) = unused<Unit>()
    override suspend fun persistAgentRetry(request: PersistAgentRetryRequest): PersistAgentRetryResult = unused()
    override suspend fun duplicateChatWithHistory(sourceChatId: Int, title: String, timestamp: Long): ChatRoomV2 = unused()
    override suspend fun updateMessage(message: MessageV2) = unused<Unit>()
    override suspend fun updateRunStatus(
        runId: String,
        status: String,
        startedAt: Long?,
        completedAt: Long?,
        terminalError: String?
    ) = unused<Unit>()

    override suspend fun getToolEventsForRun(runId: String): List<ToolEvent> = rows
        .filter { it.runId == runId }
        .sortedBy { it.sequence }

    override suspend fun getToolEventById(eventId: String): ToolEvent? = rows.firstOrNull { it.eventId == eventId }

    override fun observeToolEventsForChat(chatId: Int): Flow<List<ToolEvent>> {
        observedChatIds += chatId
        return observedToolEvents
    }

    override suspend fun finishToolEvent(
        eventId: String,
        callId: String,
        result: String,
        resultType: String,
        status: String,
        isError: Boolean,
        completedAt: Long,
        error: String?
    ): Int {
        val index = rows.indexOfFirst {
            it.eventId == eventId &&
                it.callId == callId &&
                it.status in listOf(ToolEventStatus.PENDING, ToolEventStatus.RUNNING)
        }
        if (index < 0) return 0
        rows[index] = rows[index].copy(
            result = result,
            resultType = resultType,
            status = status,
            isError = isError,
            completedAt = completedAt,
            error = error
        )
        return 1
    }

    override suspend fun cancelActiveToolEvents(runId: String, completedAt: Long) {
        rows.replaceAll { event ->
            if (event.runId == runId && event.status in listOf(ToolEventStatus.PENDING, ToolEventStatus.RUNNING)) {
                event.copy(status = ToolEventStatus.CANCELED, completedAt = completedAt)
            } else {
                event
            }
        }
    }

    override suspend fun cancelInterruptedToolEvents(completedAt: Long) = Unit

    private fun <T> unused(): T = error("unused")
}

private fun Char.isSurrogate(): Boolean = this in '\uD800'..'\uDFFF'
