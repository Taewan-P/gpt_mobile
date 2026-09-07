package dev.chungjungsoo.gptmobile.data.agent

import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

data class AgentRunLimits(
    val runTimeoutMillis: Long = 15 * 60 * 1000L,
    val maxRounds: Int = 8,
    val maxToolCalls: Int = 24,
    val maxConcurrentTools: Int = 4,
    val toolTimeoutMillis: Long = 60 * 1000L,
    val maxToolOutputBytes: Int = 64 * 1024
)

class AgentRunner(
    private val limits: AgentRunLimits = AgentRunLimits()
) {
    fun run(session: AgentProviderSession, tools: List<AgentTool>): Flow<AgentRunEvent> = flow {
        val toolByName = tools.associateBy { it.definition.name }
        var executableToolByName = toolByName
        var exposedDefinitions = tools.map { it.definition }
        val exchanges = mutableListOf<AgentToolExchange>()
        var rounds = 0
        var toolCallCount = 0
        var toolMayHaveExecuted = false
        var retriedWithoutTools = false

        val finishedInTime = withTimeoutOrNull(limits.runTimeoutMillis) {
            while (true) {
                if (rounds >= limits.maxRounds) {
                    emit(failed("Agent stopped after ${limits.maxRounds} model/tool rounds."))
                    return@withTimeoutOrNull
                }
                rounds += 1

                val calls = mutableListOf<ProviderEvent.ToolCall>()
                var completed = false
                var failed = false
                try {
                    session.streamRound(exposedDefinitions, exchanges)
                        .transformWhile { event ->
                            emit(event)
                            event !is ProviderEvent.Failed
                        }
                        .collect { event ->
                            when (event) {
                                is ProviderEvent.ToolCall -> {
                                    if (!session.handlesToolsInternally) {
                                        calls += event
                                    }
                                    emit(AgentRunEvent.Provider(event))
                                }

                                is ProviderEvent.ToolResult -> emit(AgentRunEvent.ToolFinished(event.call, event.result))

                                is ProviderEvent.Failed -> {
                                    failed = true
                                    emit(AgentRunEvent.Provider(event))
                                }

                                is ProviderEvent.Notice -> emit(AgentRunEvent.Notice(event.message, event.persistent))

                                ProviderEvent.Completed -> completed = true

                                else -> emit(AgentRunEvent.Provider(event))
                            }
                        }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: ToolDefinitionsRejectedException) {
                    if (exposedDefinitions.isNotEmpty() && !toolMayHaveExecuted && !retriedWithoutTools) {
                        retriedWithoutTools = true
                        exposedDefinitions = emptyList()
                        executableToolByName = emptyMap()
                        rounds -= 1
                        emit(AgentRunEvent.Notice(TOOLS_UNAVAILABLE_MESSAGE, persistent = true))
                        continue
                    }
                    emit(failed(error.message ?: "Tools are unavailable for this model."))
                    return@withTimeoutOrNull
                } catch (error: Exception) {
                    emit(failed(error.message ?: "Provider request failed."))
                    return@withTimeoutOrNull
                }

                if (failed) return@withTimeoutOrNull
                if (calls.isEmpty()) {
                    if (completed) emit(AgentRunEvent.Provider(ProviderEvent.Completed))
                    return@withTimeoutOrNull
                }
                if (rounds >= limits.maxRounds) {
                    emit(failed("Agent stopped after ${limits.maxRounds} model/tool rounds."))
                    return@withTimeoutOrNull
                }
                if (toolCallCount + calls.size > limits.maxToolCalls) {
                    emit(failed("Agent stopped before exceeding ${limits.maxToolCalls} tool calls."))
                    return@withTimeoutOrNull
                }

                calls.forEach { emit(AgentRunEvent.ToolStarted(it)) }
                toolMayHaveExecuted = true
                val semaphore = Semaphore(limits.maxConcurrentTools)
                val results = coroutineScope {
                    calls.map { call ->
                        async {
                            semaphore.withPermit {
                                executeBounded(call, executableToolByName[call.name])
                            }
                        }
                    }.awaitAll()
                }
                toolCallCount += calls.size
                calls.zip(results).forEach { (call, result) ->
                    emit(AgentRunEvent.ToolFinished(call, result))
                }
                exchanges += AgentToolExchange(calls, results)
            }
        }

        if (finishedInTime == null) {
            emit(failed("Agent run timed out after ${limits.runTimeoutMillis} ms."))
        }
    }

    private suspend fun executeBounded(
        call: ProviderEvent.ToolCall,
        tool: AgentTool?
    ): AgentToolResult {
        if (tool == null) {
            return AgentToolResult(
                callId = call.callId,
                content = ToolResultContent.Text("Tool '${call.name}' is not assigned to this profile."),
                isError = true
            )
        }

        return try {
            val result = withTimeoutOrNull(limits.toolTimeoutMillis) {
                tool.execute(call.callId, call.arguments)
            } ?: AgentToolResult(
                callId = call.callId,
                content = ToolResultContent.Text("Tool '${call.name}' timed out after ${limits.toolTimeoutMillis} ms."),
                isError = true
            )
            result.copy(content = boundContent(result.content))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AgentToolResult(
                callId = call.callId,
                content = ToolResultContent.Text(error.message ?: "Tool '${call.name}' failed."),
                isError = true
            ).let { it.copy(content = boundContent(it.content)) }
        }
    }

    private fun boundContent(content: ToolResultContent): ToolResultContent {
        val encoded = when (content) {
            is ToolResultContent.Text -> content.text
            is ToolResultContent.Json -> Json.encodeToString(content.value)
            is ToolResultContent.ResourceLinks -> Json.encodeToString(content.links.map { it.uri })
        }
        if (encoded.toByteArray(StandardCharsets.UTF_8).size <= limits.maxToolOutputBytes) return content
        return ToolResultContent.Text(truncateUtf8(encoded, limits.maxToolOutputBytes))
    }

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        val result = StringBuilder()
        var index = 0
        var bytes = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val chunk = String(Character.toChars(codePoint))
            val chunkBytes = chunk.toByteArray(StandardCharsets.UTF_8).size
            if (bytes + chunkBytes > maxBytes) break
            result.append(chunk)
            bytes += chunkBytes
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }

    private fun failed(message: String) = AgentRunEvent.Provider(ProviderEvent.Failed(message))

    private companion object {
        const val TOOLS_UNAVAILABLE_MESSAGE = "Tools unavailable for this model."
    }
}
