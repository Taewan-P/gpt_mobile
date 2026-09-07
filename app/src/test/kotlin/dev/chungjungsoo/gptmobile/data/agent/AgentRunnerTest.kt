package dev.chungjungsoo.gptmobile.data.agent

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

        val events = AgentRunner().run(session, listOf(tool)).toList()

        assertEquals(4, maxActive.get())
        assertEquals(6, events.filterIsInstance<AgentRunEvent.ToolFinished>().size)
        assertTrue(events.contains(AgentRunEvent.Provider(ProviderEvent.TextDelta("done"))))
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
    fun `tool call ceiling stops before any excess call executes`() = runBlocking {
        val executions = AtomicInteger()
        val session = session { _, _ ->
            flow {
                repeat(25) { emit(toolCall("call_$it")) }
                emit(ProviderEvent.Completed)
            }
        }
        val tool = tool { callId, _ ->
            executions.incrementAndGet()
            AgentToolResult(callId, ToolResultContent.Text("unexpected"), isError = false)
        }

        val events = AgentRunner().run(session, listOf(tool)).toList()

        assertEquals(0, executions.get())
        assertTrue(events.last() is AgentRunEvent.Provider)
        assertTrue((events.last() as AgentRunEvent.Provider).event is ProviderEvent.Failed)
    }

    @Test
    fun `round ceiling stops repeated tool loops`() = runBlocking {
        val providerCalls = AtomicInteger()
        val executions = AtomicInteger()
        val session = session { _, _ ->
            val round = providerCalls.getAndIncrement()
            flow {
                emit(toolCall("call_$round"))
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

        assertEquals(2, providerCalls.get())
        assertEquals(1, executions.get())
        assertTrue((events.last() as AgentRunEvent.Provider).event is ProviderEvent.Failed)
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
