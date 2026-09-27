package dev.chungjungsoo.gptmobile.data.benchmark

import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarkRunnerTest {
    private val instruction = benchmarkSuite(BenchmarkMode.QUICK).first { it.id == "instruction" }
    private val toolTest = benchmarkSuite(BenchmarkMode.QUICK).first { it.id == "tool" }

    @Test
    fun `latency ignores thinking and token usage is not double counted`() = runTest {
        val runner = BenchmarkRunner({ _, tools ->
            assertTrue(tools.isEmpty())
            session(
                flow {
                    emit(ProviderEvent.ThinkingDelta("thinking"))
                    delay(100)
                    emit(ProviderEvent.TextDelta("BENCHMARK_"))
                    emit(ProviderEvent.Usage(outputTokens = 5))
                    delay(200)
                    emit(ProviderEvent.TextDelta("READY"))
                    emit(ProviderEvent.Usage(outputTokens = 10))
                    emit(ProviderEvent.Completed)
                }
            )
        }, now = { testScheduler.currentTime })
        val result = runner.run(instruction, true)
        assertEquals(BenchmarkOutcome.PASSED, result.outcome)
        assertEquals(100L, result.firstTextMs)
        assertEquals(300L, result.durationMs)
        assertEquals(200L, result.longestGapMs)
        assertEquals(10, result.outputTokens)
        assertEquals(50.0, result.decodeTokensPerSecond!!, .001)
        assertFalse(result.estimatedTokens)
    }

    @Test
    fun `remote tools execute only the fixture and send its result back`() = runTest {
        var rounds = 0
        val runner = BenchmarkRunner({ _, tools ->
            assertEquals(listOf("benchmark_lookup"), tools.map { it.definition.name })
            object : AgentProviderSession {
                override fun streamRound(tools: List<AgentToolDefinition>, exchanges: List<AgentToolExchange>) = flow {
                    rounds++
                    if (exchanges.isEmpty()) {
                        emit(ProviderEvent.ToolCall("call-1", "benchmark_lookup", buildJsonObject { put("key", "parcel") }))
                    } else {
                        val result = exchanges.single().results.single()
                        assertFalse(result.isError)
                        emit(ProviderEvent.TextDelta((result.content as ToolResultContent.Text).text))
                    }
                    emit(ProviderEvent.Completed)
                }
            }
        })
        val result = runner.run(toolTest, true)
        assertEquals(BenchmarkOutcome.PASSED, result.outcome)
        assertEquals(2, rounds)
        assertEquals(1, result.toolCalls)
        assertEquals(1, result.successfulToolCalls)
        assertTrue(result.estimatedTokens)
        assertTrue(result.preview.startsWith("PKG-"))
    }

    @Test
    fun `local internally executed tools are not executed a second time`() = runTest {
        var rounds = 0
        val runner = BenchmarkRunner({ _, bound ->
            object : AgentProviderSession {
                override val handlesToolsInternally = true
                override fun streamRound(tools: List<AgentToolDefinition>, exchanges: List<AgentToolExchange>) = flow {
                    rounds++
                    assertTrue(exchanges.isEmpty())
                    val call = ProviderEvent.ToolCall("local-1", "benchmark_lookup", buildJsonObject { put("key", "parcel") })
                    emit(call)
                    val result = bound.single().execute(call.callId, call.arguments)
                    emit(ProviderEvent.ToolResult(call, result))
                    emit(ProviderEvent.TextDelta((result.content as ToolResultContent.Text).text))
                    emit(ProviderEvent.Completed)
                }
            }
        })
        assertEquals(BenchmarkOutcome.PASSED, runner.run(toolTest, true).outcome)
        assertEquals(1, rounds)
    }

    @Test
    fun `claiming a tool result without calling it fails the task`() = runTest {
        val runner = BenchmarkRunner({ _, _ -> session(flowOf(ProviderEvent.TextDelta("PKG-guess"), ProviderEvent.Completed)) })
        assertEquals(BenchmarkOutcome.FAILED, runner.run(toolTest, true).outcome)
        assertEquals(BenchmarkOutcome.UNSUPPORTED, runner.run(toolTest, false).outcome)
    }

    @Test
    fun `timeouts are recorded but caller cancellation propagates`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val runner = BenchmarkRunner({ _, _ ->
            session(
                flow {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            )
        }, timeoutMs = 500)
        val timeout = runner.run(instruction, true)
        assertEquals(BenchmarkOutcome.TIMED_OUT, timeout.outcome)
        var returned = false
        val cancelRunner = BenchmarkRunner({ _, _ -> session(flow { awaitCancellation() }) })
        val job = launch {
            cancelRunner.run(instruction, true)
            returned = true
        }
        testScheduler.runCurrent()
        job.cancelAndJoin()
        assertFalse(returned)
    }

    @Test
    fun `empty and abruptly ended streams never count as successful completions`() = runTest {
        val empty = BenchmarkRunner({ _, _ -> session(flowOf(ProviderEvent.Completed)) }).run(instruction, true)
        val abrupt = BenchmarkRunner({ _, _ -> session(flowOf(ProviderEvent.TextDelta("BENCHMARK_READY"))) }).run(instruction, true)
        assertEquals(BenchmarkOutcome.ERROR, empty.outcome)
        assertEquals(BenchmarkOutcome.ERROR, abrupt.outcome)
    }

    @Test
    fun `one chunk output has no fabricated decode speed`() = runTest {
        val runner = BenchmarkRunner({ _, _ -> session(flowOf(ProviderEvent.TextDelta("BENCHMARK_READY"), ProviderEvent.Completed)) })
        val result = runner.run(instruction, true)
        assertEquals(BenchmarkOutcome.PASSED, result.outcome)
        assertTrue(result.estimatedTokens)
        assertNull(result.decodeTokensPerSecond)
    }

    @Test
    fun `partial failed responses retain estimated token observations`() = runTest {
        val result = BenchmarkRunner({ _, _ -> session(flowOf(ProviderEvent.TextDelta("partial text"), ProviderEvent.Failed("Server failed"))) }).run(instruction, true)
        assertEquals(BenchmarkOutcome.ERROR, result.outcome)
        assertEquals(3, result.outputTokens)
        assertTrue(result.estimatedTokens)
    }

    @Test
    fun `a parent timeout is not mistaken for the per-test limit`() = runTest {
        val runner = BenchmarkRunner({ _, _ -> session(flow { awaitCancellation() }) }, timeoutMs = 10_000)
        var parentCanceled = false
        try {
            withTimeout(100) { runner.run(instruction, true) }
        } catch (_: TimeoutCancellationException) {
            parentCanceled = true
        }
        assertTrue(parentCanceled)
    }

    @Test
    fun `strict JSON rejects code fences and wrong values`() = runTest {
        val test = benchmarkSuite(BenchmarkMode.QUICK).first { it.id == "json" }
        suspend fun grade(text: String) = BenchmarkRunner({ _, _ -> session(flowOf(ProviderEvent.TextDelta(text), ProviderEvent.Completed)) }).run(test, true).outcome
        assertEquals(BenchmarkOutcome.PASSED, grade("{\"items\":[\"red\",\"green\",\"blue\"],\"count\":3,\"status\":\"ready\"}"))
        assertEquals(BenchmarkOutcome.FAILED, grade("```json\n{\"count\":3}\n```"))
        assertEquals(BenchmarkOutcome.FAILED, grade("{\"items\":[\"red\",\"green\",\"blue\"],\"count\":4,\"status\":\"ready\"}"))
    }

    @Test
    fun `full suite supplies recall history without user conversation content`() = runTest {
        val test = benchmarkSuite(BenchmarkMode.FULL).first { it.id == "context" }
        val runner = BenchmarkRunner({ turns, tools ->
            assertEquals(2, turns.size)
            assertTrue(turns.first().userMessage.content.contains("ORCHID-742"))
            assertFalse(turns.first().isCurrentTurn)
            assertTrue(turns.last().isCurrentTurn)
            assertTrue(tools.isEmpty())
            session(flowOf(ProviderEvent.TextDelta("ORCHID-742"), ProviderEvent.Completed))
        })
        assertEquals(BenchmarkOutcome.PASSED, runner.run(test, true).outcome)
    }

    private fun session(events: Flow<ProviderEvent>) = object : AgentProviderSession {
        override fun streamRound(tools: List<AgentToolDefinition>, exchanges: List<AgentToolExchange>) = events
    }
}
