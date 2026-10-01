package dev.chungjungsoo.gptmobile.data.agent

import dev.chungjungsoo.gptmobile.data.diagnostics.AppLogRecorder
import dev.chungjungsoo.gptmobile.data.network.error.ErrorClassification
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

data class AgentRunLimits(
    val runTimeoutMillis: Long = Long.MAX_VALUE,
    val maxRounds: Int = Int.MAX_VALUE,
    val maxToolCalls: Int = 50,
    val maxConcurrentTools: Int = 32,
    val toolTimeoutMillis: Long = 45_000L,
    val maxToolOutputBytes: Int = 256 * 1024,
    val contextTokens: Int = Int.MAX_VALUE,
    val initialContextTokens: Int = 0,
    val finalResponseReserveTokens: Int = 2048,
    val finalResponseToolCallReserve: Int = 0,
    /** Maximum replay budget for prior tool exchanges sent on each provider round. */
    val maxReplayTokens: Int = 4_000,
    /** Maximum budget retained from any one consumed tool result on later rounds. */
    val maxReplayResultTokens: Int = 512
) {
    companion object {
        const val DEFAULT_MAX_TOOL_CALLS: Int = 50

        fun defaultMaxConcurrentTools(): Int = 32

        fun defaultMaxToolOutputBytes(): Int = 256 * 1024
    }
}

class AgentRunner(
    val limits: AgentRunLimits = AgentRunLimits()
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

        val finishedInTime = if (limits.runTimeoutMillis < Long.MAX_VALUE) {
            withTimeoutOrNull(limits.runTimeoutMillis) {
                executeLoop(session, toolByName, executableToolByName, exposedDefinitions, exchanges, rounds, toolCallCount, toolMayHaveExecuted, retriedWithoutTools)
            }
        } else {
            executeLoop(session, toolByName, executableToolByName, exposedDefinitions, exchanges, rounds, toolCallCount, toolMayHaveExecuted, retriedWithoutTools)
            true
        }

        if (finishedInTime == null) {
            emit(failed("Agent run timed out after ${limits.runTimeoutMillis} ms."))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentRunEvent>.executeLoop(
        session: AgentProviderSession,
        toolByName: Map<String, AgentTool>,
        initialExecutableToolByName: Map<String, AgentTool>,
        initialExposedDefinitions: List<AgentToolDefinition>,
        exchanges: MutableList<AgentToolExchange>,
        initialRounds: Int,
        initialToolCallCount: Int,
        initialToolMayHaveExecuted: Boolean,
        initialRetriedWithoutTools: Boolean
    ) {
        var executableToolByName = initialExecutableToolByName
        var exposedDefinitions = initialExposedDefinitions
        var rounds = initialRounds
        var toolCallCount = initialToolCallCount
        var toolMayHaveExecuted = initialToolMayHaveExecuted
        var retriedWithoutTools = initialRetriedWithoutTools
        var finalResponseRequested = false
        var roundLimitFinalizationAttempted = false
        var wrapUpNoticeEmitted = false
        var replayTokens = 0L
        val executionToolCallLimit = ToolBudgetPolicy.executionLimit(limits)

        while (true) {
            if (executionToolCallLimit < Int.MAX_VALUE && toolCallCount >= executionToolCallLimit) {
                exposedDefinitions = emptyList()
                executableToolByName = emptyMap()
                if (!finalResponseRequested) {
                    finalResponseRequested = true
                    emit(AgentRunEvent.Notice(FINAL_RESPONSE_NOTICE, persistent = false))
                }
            } else if (ToolBudgetPolicy.shouldEmitWrapUpNotice(executionToolCallLimit, limits, toolCallCount, wrapUpNoticeEmitted)) {
                wrapUpNoticeEmitted = true
                val remainingAllowance = ToolBudgetPolicy.remainingAllowance(executionToolCallLimit, toolCallCount)
                emit(AgentRunEvent.Notice("Approaching tool limit ($remainingAllowance remaining). Wrapping up.", persistent = false))
            }
            if (limits.maxRounds < Int.MAX_VALUE && rounds >= limits.maxRounds) {
                if (!roundLimitFinalizationAttempted) {
                    roundLimitFinalizationAttempted = true
                    finalResponseRequested = true
                    exposedDefinitions = emptyList()
                    executableToolByName = emptyMap()
                    emit(AgentRunEvent.Notice(ROUND_LIMIT_FINAL_RESPONSE_NOTICE, persistent = false))
                } else {
                    emit(failed("Agent could not finalize after ${limits.maxRounds} model/tool rounds."))
                    return
                }
            }
            rounds += 1

            val calls = mutableListOf<ProviderEvent.ToolCall>()
            var completed = false
            var failed = false
            var roundInputTokens = 0L
            var roundOutputTokens = 0L
            var roundTotalTokens = 0L
            var hasRoundInputUsage = false
            var hasRoundOutputUsage = false
            var hasRoundTotalUsage = false
            var roundDecodeSpeed: Double? = null

            suspend fun emitRoundUsage() {
                if (!hasRoundInputUsage && !hasRoundOutputUsage && !hasRoundTotalUsage && roundDecodeSpeed == null) return
                emit(
                    AgentRunEvent.Provider(
                        ProviderEvent.Usage(
                            inputTokens = roundInputTokens.takeIf { hasRoundInputUsage }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                            outputTokens = roundOutputTokens.takeIf { hasRoundOutputUsage }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                            totalTokens = roundTotalTokens.takeIf { hasRoundTotalUsage }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                            cumulative = false,
                            decodeTokensPerSecond = roundDecodeSpeed
                        )
                    )
                )
            }

            try {
                val rawReplayTokens = ToolExchangeCompactor.estimateTokens(exchanges)
                val replayExchanges = ToolExchangeCompactor.compact(
                    exchanges = exchanges,
                    maxReplayTokens = limits.maxReplayTokens,
                    maxResultTokens = limits.maxReplayResultTokens
                )
                val compactedReplayTokens = ToolExchangeCompactor.estimateTokens(replayExchanges)
                if (rawReplayTokens > compactedReplayTokens) {
                    AppLogRecorder.record(
                        "Agent",
                        "PRIMARY_REPLAY_COMPACTED · rawTokens=$rawReplayTokens · replayTokens=$compactedReplayTokens · savedTokens=${rawReplayTokens - compactedReplayTokens} · exchanges=${exchanges.size} · budget=${limits.maxReplayTokens}"
                    )
                }
                if (compactedReplayTokens > limits.maxReplayTokens) {
                    AppLogRecorder.record(
                        "Agent",
                        "PRIMARY_REPLAY_BUDGET_OVERRUN · replayTokens=$compactedReplayTokens · budget=${limits.maxReplayTokens} · exchanges=${exchanges.size}",
                        "W"
                    )
                }
                session.streamRound(exposedDefinitions, replayExchanges)
                    .collect { event ->
                        if (failed) return@collect
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

                            is ProviderEvent.Usage -> {
                                event.decodeTokensPerSecond?.let { roundDecodeSpeed = it }
                                event.inputTokens?.let { tokens ->
                                    roundInputTokens = if (event.cumulative) {
                                        maxOf(roundInputTokens, tokens.toLong())
                                    } else {
                                        roundInputTokens + tokens
                                    }
                                    hasRoundInputUsage = true
                                }
                                event.outputTokens?.let { tokens ->
                                    roundOutputTokens = if (event.cumulative) {
                                        maxOf(roundOutputTokens, tokens.toLong())
                                    } else {
                                        roundOutputTokens + tokens
                                    }
                                    hasRoundOutputUsage = true
                                }
                                event.totalTokens?.let { tokens ->
                                    roundTotalTokens = if (event.cumulative) {
                                        maxOf(roundTotalTokens, tokens.toLong())
                                    } else {
                                        roundTotalTokens + tokens
                                    }
                                    hasRoundTotalUsage = true
                                }
                            }

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
                return
            } catch (error: Throwable) {
                emitRoundUsage()
                val classifiedMessage = ErrorClassification.classify(error).userMessage
                emit(failed(classifiedMessage))
                return
            }

            emitRoundUsage()
            if (failed) return
            if (calls.isEmpty()) {
                if (completed) emit(AgentRunEvent.Provider(ProviderEvent.Completed))
                return
            }
            val roundLimitReached = limits.maxRounds < Int.MAX_VALUE && rounds >= limits.maxRounds

            val remainingCalls = if (executionToolCallLimit == Int.MAX_VALUE) {
                calls.size
            } else {
                (executionToolCallLimit - toolCallCount).coerceAtLeast(0)
            }
            val executableCalls = calls.take(remainingCalls)
            val deferredCalls = calls.drop(executableCalls.size)

            executableCalls.forEach { emit(AgentRunEvent.ToolStarted(it)) }
            if (executableCalls.isNotEmpty()) toolMayHaveExecuted = true
            val semaphore = Semaphore(limits.maxConcurrentTools)
            val executedResults = coroutineScope {
                executableCalls.map { call ->
                    async {
                        semaphore.withPermit {
                            executeBounded(call, executableToolByName[call.name])
                        }
                    }
                }.awaitAll()
            }
            toolCallCount += executableCalls.size

            val deferredResults = deferredCalls.map { call ->
                AgentToolResult(
                    callId = call.callId,
                    content = ToolResultContent.Text(FINAL_RESPONSE_INSTRUCTION),
                    isError = true
                )
            }
            val allResults = (executedResults + deferredResults).toMutableList()
            val outputBudgetExhausted = allResults.any { it.outputBudgetExhausted }
            val projectedExchanges = exchanges + AgentToolExchange(calls, allResults)
            replayTokens = ToolExchangeCompactor.estimateTokens(
                ToolExchangeCompactor.compact(
                    exchanges = projectedExchanges,
                    maxReplayTokens = limits.maxReplayTokens,
                    maxResultTokens = limits.maxReplayResultTokens
                )
            ).toLong()
            val contextNearLimit = limits.contextTokens != Int.MAX_VALUE &&
                limits.initialContextTokens.toLong() + replayTokens + limits.finalResponseReserveTokens + 256 >= limits.contextTokens
            val mustFinalize = roundLimitReached ||
                contextNearLimit ||
                outputBudgetExhausted ||
                (executionToolCallLimit < Int.MAX_VALUE && toolCallCount >= executionToolCallLimit)
            if (roundLimitReached) {
                exposedDefinitions = emptyList()
                executableToolByName = emptyMap()
                finalResponseRequested = true
                emit(AgentRunEvent.Notice(ROUND_LIMIT_FINAL_RESPONSE_NOTICE, persistent = false))
            } else if (outputBudgetExhausted || contextNearLimit) {
                exposedDefinitions = emptyList()
                executableToolByName = emptyMap()
                finalResponseRequested = true
                emit(AgentRunEvent.Notice("Response limit approaching. Finishing with the results already available.", persistent = false))
            }
            val remainingAllowance = ToolBudgetPolicy.remainingAllowance(executionToolCallLimit, toolCallCount)
            val shouldInjectWrapUp = ToolBudgetPolicy.shouldInjectWrapUpPrompt(executionToolCallLimit, limits, toolCallCount)

            if (mustFinalize && allResults.isNotEmpty()) {
                allResults[allResults.lastIndex] = when {
                    roundLimitReached -> appendInstruction(allResults.last(), ROUND_LIMIT_FINAL_RESPONSE_INSTRUCTION)
                    contextNearLimit -> appendInstruction(allResults.last(), "The context limit is approaching. Use the available findings to give a final response now and ask whether the user wants to continue. Do not call more tools.")
                    else -> appendFinalResponseInstruction(allResults.last())
                }
            } else if (shouldInjectWrapUp && allResults.isNotEmpty()) {
                val wrapUpPrompt = ToolBudgetPolicy.buildWrapUpPrompt(remainingAllowance)
                allResults[allResults.lastIndex] = appendInstruction(allResults.last(), wrapUpPrompt)
            }

            calls.zip(allResults).forEach { (call, result) ->
                emit(AgentRunEvent.ToolFinished(call, result))
            }
            exchanges += AgentToolExchange(calls, allResults)
        }
    }

    private fun appendInstruction(result: AgentToolResult, instruction: String): AgentToolResult {
        val existing = when (val content = result.content) {
            is ToolResultContent.Text -> content.text
            is ToolResultContent.Json -> Json.encodeToString(content.value)
            is ToolResultContent.ResourceLinks -> Json.encodeToString(content.links.map { it.uri })
        }
        if (existing.contains(instruction)) return result
        return result.copy(content = ToolResultContent.Text("$existing\n\n$instruction"))
    }

    private fun appendFinalResponseInstruction(result: AgentToolResult): AgentToolResult {
        val existing = when (val content = result.content) {
            is ToolResultContent.Text -> content.text
            is ToolResultContent.Json -> Json.encodeToString(content.value)
            is ToolResultContent.ResourceLinks -> Json.encodeToString(content.links.map { it.uri })
        }
        if (existing.contains(FINAL_RESPONSE_INSTRUCTION)) return result
        return result.copy(content = ToolResultContent.Text("$existing\n\n$FINAL_RESPONSE_INSTRUCTION"))
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
            val result = if (!tool.managesExecutionBudget && limits.toolTimeoutMillis < Long.MAX_VALUE) {
                withTimeoutOrNull(limits.toolTimeoutMillis) {
                    tool.execute(call.callId, call.arguments)
                } ?: AgentToolResult(
                    callId = call.callId,
                    content = ToolResultContent.Text("Tool '${call.name}' timed out after ${limits.toolTimeoutMillis} ms."),
                    isError = true
                )
            } else {
                tool.execute(call.callId, call.arguments)
            }
            if (tool.managesExecutionBudget) result else result.copy(content = boundContent(result.content))
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
        if (limits.maxToolOutputBytes == Int.MAX_VALUE) return content
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

    companion object {
        const val TOOLS_UNAVAILABLE_MESSAGE = "Tools unavailable for this model."
        const val FINAL_RESPONSE_NOTICE = "Tool-call limit is approaching; generating a final response."
        const val ROUND_LIMIT_FINAL_RESPONSE_NOTICE =
            "Agent work-round limit reached; generating a final response with the results already available."
        const val ROUND_LIMIT_FINAL_RESPONSE_INSTRUCTION =
            "The model/tool work-round allowance is exhausted. Do not request more tools. " +
                "Use the available findings to answer now. If additional tool work is required, " +
                "briefly state what remains and ask the user to reply exactly \"continue\"."
        const val FINAL_RESPONSE_INSTRUCTION =
            "Tool-call allowance is exhausted. Do not request more tools in this response. " +
                "Finish with a concise summary of what was completed and what remains. " +
                "If more tool work is required, ask the user to reply exactly \"continue\" so a new response can continue with a fresh tool-call allowance."
    }
}
