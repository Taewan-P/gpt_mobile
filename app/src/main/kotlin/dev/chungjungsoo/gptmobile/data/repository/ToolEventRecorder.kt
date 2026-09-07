package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.agent.AgentResourceLink
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventResultType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ToolEventRecorder @Inject constructor(
    private val dao: AgentPersistenceDao
) {
    private var eventIdFactory: () -> String = { UUID.randomUUID().toString() }

    internal constructor(
        dao: AgentPersistenceDao,
        eventIdFactory: () -> String
    ) : this(dao) {
        this.eventIdFactory = eventIdFactory
    }

    suspend fun startTool(
        runId: String,
        sequence: Int,
        callId: String,
        toolName: String,
        modelToolName: String,
        arguments: JsonObject,
        connectionUid: String? = null,
        connectionName: String? = null,
        startedAt: Long
    ): ToolEvent {
        val event = ToolEvent(
            eventId = eventIdFactory(),
            runId = runId,
            sequence = sequence,
            callId = callId,
            connectionUidSnapshot = connectionUid,
            connectionNameSnapshot = connectionName,
            toolName = toolName,
            modelToolName = modelToolName,
            arguments = arguments.toString().boundUtf8(),
            result = null,
            resultType = null,
            status = ToolEventStatus.RUNNING,
            startedAt = startedAt
        )
        dao.insertToolEvent(event)
        return event
    }

    suspend fun finishTool(
        eventId: String,
        result: AgentToolResult,
        completedAt: Long,
        error: String? = null
    ): ToolEvent? {
        val content = (result.traceContent ?: result.content).serialized().forStorage()
        val affectedRows = dao.finishToolEvent(
            eventId = eventId,
            callId = result.callId,
            result = content.value,
            resultType = content.type,
            status = if (result.isError) ToolEventStatus.FAILED else ToolEventStatus.COMPLETED,
            isError = result.isError,
            completedAt = completedAt,
            error = error
        )
        if (affectedRows != 1) return null
        return dao.getToolEventById(eventId)
    }

    suspend fun cancelRun(runId: String, completedAt: Long) {
        dao.cancelActiveToolEvents(runId, completedAt)
    }

    suspend fun getEvents(runId: String): List<ToolEvent> = dao.getToolEventsForRun(runId)

    suspend fun getEvents(runIds: Set<String>): List<ToolEvent> {
        if (runIds.isEmpty()) return emptyList()
        return dao.getToolEvents(runIds.toList())
    }

    fun observeChat(chatId: Int): Flow<List<ToolEvent>> {
        if (chatId <= 0) return flowOf(emptyList())
        return dao.observeToolEventsForChat(chatId)
    }

    private fun ToolResultContent.serialized(): SerializedResult = when (this) {
        is ToolResultContent.Text -> SerializedResult(text, ToolEventResultType.TEXT)
        is ToolResultContent.Json -> SerializedResult(Json.encodeToString(value), ToolEventResultType.JSON)
        is ToolResultContent.ResourceLinks -> SerializedResult(links.serialize(), ToolEventResultType.RESOURCE_LINKS)
    }

    private fun List<AgentResourceLink>.serialize(): String = buildJsonArray {
        forEach { link ->
            add(
                buildJsonObject {
                    put("uri", link.uri)
                    if (link.name == null) {
                        put("name", JsonNull)
                    } else {
                        put("name", link.name)
                    }
                    if (link.mimeType == null) {
                        put("mimeType", JsonNull)
                    } else {
                        put("mimeType", link.mimeType)
                    }
                }
            )
        }
    }.toString()

    private fun SerializedResult.forStorage(): SerializedResult {
        val bounded = value.boundUtf8()
        return SerializedResult(
            value = bounded,
            type = if (type != ToolEventResultType.TEXT && bounded != value) ToolEventResultType.TEXT else type
        )
    }

    private fun String.boundUtf8(maxBytes: Int = MAX_STORED_BYTES): String {
        var bytes = 0
        val bounded = StringBuilder()
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            val chars = Character.toChars(codePoint)
            val size = String(chars).toByteArray(Charsets.UTF_8).size
            if (bytes + size > maxBytes) break
            bounded.append(chars)
            bytes += size
            index += chars.size
        }
        return bounded.toString()
    }

    private data class SerializedResult(val value: String, val type: String)

    private companion object {
        const val MAX_STORED_BYTES = 64 * 1024
    }
}
