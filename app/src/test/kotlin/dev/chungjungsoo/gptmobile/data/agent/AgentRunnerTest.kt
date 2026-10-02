package dev.chungjungsoo.gptmobile.data.agent

import dev.chungjungsoo.gptmobile.data.network.error.CircuitBreakerOpenException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunnerTest {
    @Test
    fun `context guard reserves a final answer and asks to continue`() = runBlocking {
        val exposed = mutableListOf<Int>()
        var rounds = 0
        val session = session { tools, exchanges ->
            exposed += tools.size
            if (rounds++ == 0) {
                flow {
                    emit(toolCall("context-call"))
                    emit(ProviderEvent.Completed)
                }
            } else {
                flow {
                    assertTrue(exchanges.last().results.last().content.toString().contains("ask whether the user wants to continue"))
                    emit(ProviderEvent.TextDelta("Here are my findings. Continue?"))
                    emit(ProviderEvent.Completed)
                }
            }
        }
        val tool = tool { id, _ -> AgentToolResult(id, ToolResultContent.Text("source ".repeat(40)), false) }
        val events = AgentRunner(AgentRunLimits(contextTokens = 2048, initialContextTokens = 1600, finalResponseReserveTokens = 256)).run(session, listOf(tool)).toList()
        assertEquals(listOf(1, 0), exposed)
        assertEquals(AgentRunEvent.Provider(ProviderEvent.Completed), events.last())
        assertFalse(events.any { it is AgentRunEvent.Provider && it.event is ProviderEvent.Failed })
    }

    @Test
    fun `exhausted search output closes tool use and requests the final answer`() = runBlocking {
        val limits = AgentRunLimits(maxToolOutputBytes = 32)
        val bound = ToolExecutionBudget(limits).bind(
            tool("web_search") { callId, _ ->
                AgentToolResult(callId, ToolResultContent.Text("Useful source " + "x".repeat(100)), false)
            }
        )
        val events = AgentRunner(limits).run(
            session { tools, exchanges ->
                flow {
                    if (exchanges.isEmpty()) {
                        emit(toolCall("search", "web_search"))
                    } else {
                        assertTrue(tools.isEmpty())
                        assertTrue((exchanges.single().results.single().content as ToolResultContent.Text).text.contains("Useful source"))
                        emit(ProviderEvent.TextDelta("Answer from the source"))
                    }
                    emit(ProviderEvent.Completed)
                }
            },
            listOf(bound)
        ).toList()
        assertTrue(events.filterIsInstance<AgentRunEvent.Provider>().any { it.event is ProviderEvent.TextDelta })
    }

    @Test
    fun `approval latency is not charged to tool timeout by the runner`() = runBlocking {
        val limits = AgentRunLimits(toolTimeoutMillis = 5)
        val bound = ToolExecutionBudget(limits).bind(
            tool("write") { callId, _ ->
                AgentToolResult(callId, ToolResultContent.Text("saved"), isError = false)
            }
        ) { _, _ ->
            delay(40)
            true
        }
        val events = AgentRunner(limits).run(
            session { _, exchanges ->
                if (exchanges.isEmpty()) {
                    flow {
                        emit(toolCall("call", "write"))
                        emit(ProviderEvent.Completed)
                    }
                } else {
                    flow {
                        emit(ProviderEvent.TextDelta("done"))
                        emit(ProviderEvent.Completed)
                    }
                }
            },
            listOf(bound)
        ).toList()
        assertFalse(events.filterIsInstance<AgentRunEvent.ToolFinished>().single().result.isError)
    }

    @Test
    fun `no tool run calls provider once and preserves text completion`() = runBlocking {
        val calls = AtomicInteger()
        val session = session { tools, exchanges ->
            assertTrue(tools.isEmpty())
            assertTrue(exchanges.isEmpty())
            calls.incrementAndGet()
            flow {
                emit(ProviderEvent.TextDelta("answer"))
                emit(ProviderEvent.Completed)
            }
        }

        val events = AgentRunner().run(session, emptyList()).toList()

        assertEquals(1, calls.get())
        assertEquals(
            listOf(
                AgentRunEvent.Provider(ProviderEvent.TextDelta("answer")),
                AgentRunEvent.Provider(ProviderEvent.Completed)
            ),
            events
        )
    }

    @Test
    fun `independent tool calls execute with at most four concurrent calls`() = runBlocking {
        val providerCalls = AtomicInteger()
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val session = session { _, exchanges ->
            when (providerCalls.getAndIncrement()) {
                0 -> flow {
                    repeat(6) { index ->
                        emit(toolCall("call_$index", "lookup", buildJsonObject { put("index", index) }))
                    }
                    emit(ProviderEvent.Completed)
                }

                else -> flow {
                    assertEquals((0 until 6).map { "call_$it" }, exchanges.single().calls.map { it.callId })
                    assertEquals((0 until 6).map { "call_$it" }, exchanges.single().results.map { it.callId })
                    emit(ProviderEvent.TextDelta("done"))
                    emit(ProviderEvent.Completed)
                }
            }
        }
        val tool = tool("lookup") { callId, _ ->
            val current = active.incrementAndGet()
            maxActive.updateAndGet { maxOf(it, current) }
            delay(30)
            active.decrementAndGet()
            AgentToolResult(callId, ToolResultContent.Text("ok"), isError = false)
        }

        val events = AgentRunner(
            limits = AgentRunLimits(maxConcurrentTools = 4)
        ).run(session, listOf(tool)).toList()

        assertEquals(4, maxActive.get())
        assertEquals(6, events.filterIsInstance<AgentRunEvent.ToolFinished>().size)
        assertTrue(events.contains(AgentRunEvent.Provider(ProviderEvent.TextDelta("done"))))
    }

    @Test
    fun `usage snapshots collapse within a round and add across tool rounds`() = runBlocking {
        val providerCalls = AtomicInteger()
        val session = session { _, _ ->
            when (providerCalls.getAndIncrement()) {
                0 -> flow {
                    emit(ProviderEvent.Usage(inputTokens = 100, outputTokens = 10, totalTokens = 110))
                    emit(ProviderEvent.Usage(inputTokens = 100, outputTokens = 20, totalTokens = 120))
                    emit(toolCall("usage_call"))
                    emit(ProviderEvent.Completed)
                }

                else -> flow {
                    emit(ProviderEvent.Usage(inputTokens = 150, outputTokens = 25, totalTokens = 175))
                    emit(ProviderEvent.Usage(inputTokens = 150, outputTokens = 30, totalTokens = 180))
                    emit(ProviderEvent.TextDelta("done"))
                    emit(ProviderEvent.Completed)
                }
            }
        }

        val events = AgentRunner().run(session, listOf(tool())).toList()
        val usage = events
            .filterIsInstance<AgentRunEvent.Provider>()
            .mapNotNull { it.event as? ProviderEvent.Usage }

        assertEquals(
            listOf(
                ProviderEvent.Usage(inputTokens = 100, outputTokens = 20, totalTokens = 120, cumulative = false),
                ProviderEvent.Usage(inputTokens = 150, outputTokens = 30, totalTokens = 180, cumulative = false)
            ),
            usage
        )
        assertEquals(300, usage.sumOf { it.totalTokens ?: 0 })
    }

    @Test
    fun `provider completion is emitted only after the final tool round`() = runBlocking {
        val providerCalls = AtomicInteger()
        val session = session { _, _ ->
            when (providerCalls.getAndIncrement()) {
                0 -> flow {
                    emit(toolCall("call_1"))
                    emit(ProviderEvent.Completed)
                }

                else -> flow {
                    emit(ProviderEvent.TextDelta("final"))
                    emit(ProviderEvent.Completed)
                }
            }
        }

        val events = AgentRunner().run(session, listOf(tool())).toList()

        assertEquals(1, events.count { it == AgentRunEvent.Provider(ProviderEvent.Completed) })
        assertTrue(events.indexOf(AgentRunEvent.Provider(ProviderEvent.TextDelta("final"))) < events.indexOf(AgentRunEvent.Provider(ProviderEvent.Completed)))
    }

    @Test
    fun `provider failure terminates the run without completion or another round`() = runBlocking {
        val providerCalls = AtomicInteger()
        val session = session { _, _ ->
            providerCalls.incrementAndGet()
            flow {
                emit(ProviderEvent.Failed("provider failed"))
                emit(ProviderEvent.Completed)
            }
        }

        val events = AgentRunner().run(session, emptyList()).toList()

        assertEquals(1, providerCalls.get())
        assertEquals(
            listOf(AgentRunEvent.Provider(ProviderEvent.Failed("provider failed"))),
            events
        )
    }

    @Test
    fun `circuit breaker exception in provider stream emits classified error`() = runBlocking {
        val providerCalls = AtomicInteger()
        val session = session { _, _ ->
            providerCalls.incrementAndGet()
            flow {
                throw CircuitBreakerOpenException(3000L, "test-provider")
            }
        }

        val events = AgentRunner().run(session, emptyList()).toList()

        assertEquals(1, providerCalls.get())
        assertEquals(
            listOf(AgentRunEvent.Provider(ProviderEvent.Failed("Service temporarily unavailable due to high error rates. Please wait a moment before trying again."))),
            events
        )
    }

    @Test
    fun `tool call ceiling executes available calls and finalizes gracefully`() = runBlocking {
        val executions = AtomicInteger()
        val providerCalls = AtomicInteger()
        val exposedToolCounts = mutableListOf<Int>()
        val session = session { tools, exchanges ->
            exposedToolCounts += tools.size
            when (providerCalls.getAndIncrement()) {
                0 -> flow {
                    repeat(12) { emit(toolCall("call_$it")) }
                    emit(ProviderEvent.Completed)
                }
                else -> flow {
                    assertEquals(12, exchanges.single().results.size)
                    emit(ProviderEvent.TextDelta("final"))
                    emit(ProviderEvent.Completed)
                }
            }
        }
        val tool = tool { callId, _ ->
            executions.incrementAndGet()
            AgentToolResult(callId, ToolResultContent.Text("ok"), isError = false)
        }

        val events = AgentRunner(AgentRunLimits(maxToolCalls = 12)).run(session, listOf(tool)).toList()

        assertEquals(12, executions.get())
        assertEquals(listOf(1, 0), exposedToolCounts)
        assertTrue(events.any { it is AgentRunEvent.Notice })
        assertFalse(events.any { it is AgentRunEvent.Provider && it.event is ProviderEvent.Failed })
        assertEquals(AgentRunEvent.Provider(ProviderEvent.Completed), events.last())
    }

    @Test
    fun `round ceiling requests one final no-tools synthesis round`() = runBlocking {
        val providerCalls = AtomicInteger()
        val executions = AtomicInteger()
        val exposedToolCounts = mutableListOf<Int>()
        val session = session { tools, _ ->
            val round = providerCalls.getAndIncrement()
            exposedToolCounts += tools.size
            flow {
                if (round < 2) {
                    emit(toolCall("call_$round"))
                } else {
                    emit(ProviderEvent.TextDelta("Final answer from available results."))
                }
                emit(ProviderEvent.Completed)
            }
        }
        val tool = tool { callId, _ ->
            executions.incrementAndGet()
            AgentToolResult(callId, ToolResultContent.Text("ok"), isError = false)
        }

        val events = AgentRunner(
            limits = AgentRunLimits(maxRounds = 2)
        ).run(session, listOf(tool)).toList()

        assertEquals(3, providerCalls.get())
        assertEquals(2, executions.get())
        assertEquals(listOf(1, 1, 0), exposedToolCounts)
        assertTrue(events.any { it is AgentRunEvent.Notice })
        assertFalse(events.any { it is AgentRunEvent.Provider && it.event is ProviderEvent.Failed })
        assertEquals(AgentRunEvent.Provider(ProviderEvent.Completed), events.last())
    }

    @Test
    fun `tool output is bounded before persistence and provider replay`() = runBlocking {
        val providerCalls = AtomicInteger()
        var replayedResult: AgentToolResult? = null
        val session = session { _, exchanges ->
            when (providerCalls.getAndIncrement()) {
                0 -> flow {
                    emit(toolCall("bounded_call"))
                    emit(ProviderEvent.Completed)
                }

                else -> flow {
                    replayedResult = exchanges.single().results.single()
                    emit(ProviderEvent.Completed)
                }
            }
        }
        val tool = tool { callId, _ ->
            AgentToolResult(callId, ToolResultContent.Text("abcdefghij"), isError = false)
        }

        val events = AgentRunner(
            limits = AgentRunLimits(maxToolOutputBytes = 5)
        ).run(session, listOf(tool)).toList()

        assertEquals(ToolResultContent.Text("abcde"), replayedResult?.content)
        assertEquals(
            ToolResultContent.Text("abcde"),
            events.filterIsInstance<AgentRunEvent.ToolFinished>().single().result.content
        )
    }

    @Test
    fun `tool definition rejection retries once without tools before execution`() = runBlocking {
        val exposedTools = mutableListOf<List<AgentToolDefinition>>()
        val session = session { tools, _ ->
            exposedTools += tools
            if (tools.isNotEmpty()) {
                throw ToolDefinitionsRejectedException("unsupported")
            }
            flow {
                emit(ProviderEvent.TextDelta("chat fallback"))
                emit(ProviderEvent.Completed)
            }
        }

        val events = AgentRunner().run(session, listOf(tool())).toList()

        assertEquals(listOf(1, 0), exposedTools.map { it.size })
        assertTrue(events.contains(AgentRunEvent.Notice("Tools unavailable for this model.", persistent = true)))
        assertTrue(events.contains(AgentRunEvent.Provider(ProviderEvent.TextDelta("chat fallback"))))
    }

    @Test
    fun `tool definition fallback cannot execute an unexposed tool call`() = runBlocking {
        val providerCalls = AtomicInteger()
        val executions = AtomicInteger()
        val session = session { tools, exchanges ->
            when (providerCalls.getAndIncrement()) {
                0 -> throw ToolDefinitionsRejectedException("unsupported")

                1 -> flow {
                    assertTrue(tools.isEmpty())
                    emit(toolCall("unexpected_call"))
                    emit(ProviderEvent.Completed)
                }

                else -> flow {
                    assertTrue(tools.isEmpty())
                    assertTrue(exchanges.single().results.single().isError)
                    emit(ProviderEvent.TextDelta("chat fallback"))
                    emit(ProviderEvent.Completed)
                }
            }
        }
        val tool = tool { callId, _ ->
            executions.incrementAndGet()
            AgentToolResult(callId, ToolResultContent.Text("executed"), isError = false)
        }

        val events = AgentRunner().run(session, listOf(tool)).toList()

        assertEquals(0, executions.get())
        assertTrue(events.contains(AgentRunEvent.Notice("Tools unavailable for this model.", persistent = true)))
        assertTrue(events.contains(AgentRunEvent.Provider(ProviderEvent.TextDelta("chat fallback"))))
    }

    @Test
    fun `tool definition rejection never retries after a tool executes`() = runBlocking {
        val providerCalls = AtomicInteger()
        val executions = AtomicInteger()
        val session = session { _, _ ->
            when (providerCalls.getAndIncrement()) {
                0 -> flow {
                    emit(toolCall("side_effect_call"))
                    emit(ProviderEvent.Completed)
                }

                else -> throw ToolDefinitionsRejectedException("unsupported after call")
            }
        }
        val tool = tool { callId, _ ->
            executions.incrementAndGet()
            AgentToolResult(callId, ToolResultContent.Text("done"), isError = false)
        }

        val events = AgentRunner().run(session, listOf(tool)).toList()

        assertEquals(2, providerCalls.get())
        assertEquals(1, executions.get())
        assertFalse(events.any { it is AgentRunEvent.Notice })
        assertTrue((events.last() as AgentRunEvent.Provider).event is ProviderEvent.Failed)
    }

    @Test
    fun `tool timeout becomes an error result and the model can continue`() = runBlocking {
        val providerCalls = AtomicInteger()
        var replayedResult: AgentToolResult? = null
        val session = session { _, exchanges ->
            when (providerCalls.getAndIncrement()) {
                0 -> flow {
                    emit(toolCall("slow_call"))
                    emit(ProviderEvent.Completed)
                }

                else -> flow {
                    replayedResult = exchanges.single().results.single()
                    emit(ProviderEvent.Completed)
                }
            }
        }
        val tool = tool { _, _ -> awaitCancellation() }

        AgentRunner(
            limits = AgentRunLimits(toolTimeoutMillis = 20)
        ).run(session, listOf(tool)).toList()

        assertEquals(true, replayedResult?.isError)
        assertEquals(
            ToolResultContent.Text("Tool 'lookup' timed out after 20 ms."),
            replayedResult?.content
        )
    }

    @Test
    fun `run timeout message uses configured duration`() = runBlocking {
        val session = session { _, _ ->
            flow { awaitCancellation() }
        }

        val events = AgentRunner(
            limits = AgentRunLimits(runTimeoutMillis = 20)
        ).run(session, emptyList()).toList()

        assertEquals(
            AgentRunEvent.Provider(ProviderEvent.Failed("Agent run timed out after 20 ms.")),
            events.single()
        )
    }

    @Test
    fun `external cancellation keeps already emitted partial text and does not complete`() = runBlocking {
        val partialSeen = CompletableDeferred<Unit>()
        val events = mutableListOf<AgentRunEvent>()
        val session = session { _, _ ->
            flow {
                emit(ProviderEvent.TextDelta("partial"))
                awaitCancellation()
            }
        }

        val job = launch {
            AgentRunner().run(session, emptyList()).collect { event ->
                events += event
                partialSeen.complete(Unit)
            }
        }
        partialSeen.await()
        job.cancelAndJoin()

        assertEquals(
            listOf(AgentRunEvent.Provider(ProviderEvent.TextDelta("partial"))),
            events
        )
    }

    @Test
    fun `engine owned session forwards tool timeline events without another round`() = runBlocking {
        val providerCalls = AtomicInteger()
        val session = object : AgentProviderSession {
            override val handlesToolsInternally: Boolean = true

            override fun streamRound(
                tools: List<AgentToolDefinition>,
                exchanges: List<AgentToolExchange>
            ): Flow<ProviderEvent> {
                providerCalls.incrementAndGet()
                assertTrue(exchanges.isEmpty())
                return flow {
                    emit(toolCall("engine_call"))
                    emit(
                        ProviderEvent.ToolResult(
                            toolCall("engine_call"),
                            AgentToolResult("engine_call", ToolResultContent.Text("from-engine"), isError = false)
                        )
                    )
                    emit(ProviderEvent.TextDelta("final"))
                    emit(ProviderEvent.Completed)
                }
            }
        }

        val events = AgentRunner().run(session, listOf(tool())).toList()

        assertEquals(1, providerCalls.get())
        assertEquals(
            listOf(
                AgentRunEvent.Provider(toolCall("engine_call")),
                AgentRunEvent.ToolFinished(
                    toolCall("engine_call"),
                    AgentToolResult("engine_call", ToolResultContent.Text("from-engine"), isError = false)
                ),
                AgentRunEvent.Provider(ProviderEvent.TextDelta("final")),
                AgentRunEvent.Provider(ProviderEvent.Completed)
            ),
            events
        )
    }

    @Test
    fun `external cancellation during a tool is not converted to a tool error`() = runBlocking {
        val toolStarted = CompletableDeferred<Unit>()
        val events = mutableListOf<AgentRunEvent>()
        val session = session { _, _ ->
            flow {
                emit(toolCall("cancel_call"))
                emit(ProviderEvent.Completed)
            }
        }
        val tool = tool { _, _ ->
            toolStarted.complete(Unit)
            awaitCancellation()
        }

        val job = launch {
            AgentRunner().run(session, listOf(tool)).collect { events += it }
        }
        toolStarted.await()
        job.cancelAndJoin()

        assertFalse(events.any { it is AgentRunEvent.ToolFinished })
        assertFalse(events.any { it == AgentRunEvent.Provider(ProviderEvent.Completed) })
    }

    @Test
    fun `backend throughput survives aggregated usage from a buffered response`() = runBlocking {
        val session = session { _, _ ->
            flow {
                emit(ProviderEvent.TextDelta("one buffered answer"))
                emit(ProviderEvent.Usage(inputTokens = 20, outputTokens = 10, decodeTokensPerSecond = 42.5))
                emit(ProviderEvent.Completed)
            }
        }
        val usage = AgentRunner().run(session, emptyList()).toList()
            .filterIsInstance<AgentRunEvent.Provider>().map { it.event }.filterIsInstance<ProviderEvent.Usage>().single()
        assertEquals(42.5, usage.decodeTokensPerSecond!!, .01)
        assertEquals(10, usage.outputTokens)
    }

    private fun session(
        stream: (List<AgentToolDefinition>, List<AgentToolExchange>) -> Flow<ProviderEvent>
    ): AgentProviderSession = object : AgentProviderSession {
        override fun streamRound(
            tools: List<AgentToolDefinition>,
            exchanges: List<AgentToolExchange>
        ): Flow<ProviderEvent> = stream(tools, exchanges)
    }

    private fun toolCall(
        callId: String,
        name: String = "lookup",
        arguments: JsonObject = buildJsonObject {}
    ) = ProviderEvent.ToolCall(callId, name, arguments)

    private fun tool(
        name: String = "lookup",
        execute: suspend (String, JsonObject) -> AgentToolResult = { callId, _ ->
            AgentToolResult(callId, ToolResultContent.Text("ok"), isError = false)
        }
    ): AgentTool = object : AgentTool {
        override val definition = AgentToolDefinition(
            name = name,
            description = "test tool",
            inputSchema = buildJsonObject { put("type", "object") }
        )

        override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult = execute(callId, arguments)
    }
}
