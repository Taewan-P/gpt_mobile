package dev.chungjungsoo.gptmobile.data.benchmark

import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.security.DiagnosticRedactor
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class BenchmarkCase(val id: String, val label: String, val category: String, val prompt: String)

fun benchmarkSuite(mode: BenchmarkMode): List<BenchmarkCase> = buildList {
    repeat(if (mode == BenchmarkMode.FULL) 3 else 1) { index ->
        add(BenchmarkCase("speed-$index", "Generation ${index + 1}", "speed", "Write about 120 words explaining how rain forms, in plain English. No heading."))
    }
    add(BenchmarkCase("instruction", "Exact instruction", "task", "Reply with exactly BENCHMARK_READY and nothing else."))
    add(BenchmarkCase("json", "Structured JSON", "json", "Return only this JSON object, without markdown or explanation: {\"status\":\"ready\",\"count\":3,\"items\":[\"red\",\"green\",\"blue\"]}"))
    add(BenchmarkCase("arithmetic", "Arithmetic", "task", "What is 17 * 23 + 9? Reply with only the integer."))
    if (mode == BenchmarkMode.FULL) add(BenchmarkCase("context", "Conversation recall", "task", "What was the parcel code I gave you? Reply with only the code."))
    add(BenchmarkCase("tool", "Tool round trip", "tools", "Call benchmark_lookup with key=\"parcel\". Reply with only the code returned by that tool. Do not guess."))
}

/** Runs only synthetic prompts and an in-memory fixture. No chat history, MCP or device tools. */
class BenchmarkRunner(
    private val openSession: suspend (List<ConversationTurn>, List<AgentTool>) -> AgentProviderSession,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val timeoutMs: Long = 90_000
) {
    suspend fun run(test: BenchmarkCase, supportsTools: Boolean): BenchmarkSample {
        if (test.category == "tools" && !supportsTools) {
            return BenchmarkSample(test.id, test.label, test.category, BenchmarkOutcome.UNSUPPORTED, error = "This model does not advertise tool support; excluded from the rating.")
        }
        val started = now()
        val text = StringBuilder()
        var first: Long? = null
        var lastChunk: Long? = null
        var longestGap: Long? = null
        var chunks = 0
        var tokens = 0
        var estimated = false
        var roundTokens: Int? = null
        var roundStart = 0
        var roundAccounted = true
        var nativeMetrics: dev.chungjungsoo.gptmobile.data.localruntime.NativeInferenceMetrics? = null
        val calls = linkedMapOf<String, ProviderEvent.ToolCall>()
        val successful = mutableSetOf<String>()
        val code = "PKG-${UUID.randomUUID().toString().take(8)}"
        val fixture = fixtureTool(code)
        fun sample(outcome: BenchmarkOutcome, error: String? = null) = BenchmarkSample(
            test.id, test.label, test.category, outcome, (now() - started).coerceAtLeast(0), first,
            tokens + if (roundAccounted) 0 else roundTokens ?: ((text.length - roundStart + 3) / 4),
            estimated || (!roundAccounted && roundTokens == null), text.length, chunks, longestGap, calls.size, successful.size,
            text.take(1000).toString(), error, lastChunk?.let { it - started }, nativeMetrics
        )
        try {
            val finished = withTimeoutOrNull(timeoutMs) {
                val turns = buildList {
                    if (test.id == "context") add(ConversationTurn(message("Remember this parcel code: ORCHID-742."), message("I will remember ORCHID-742."), false))
                    add(ConversationTurn(message(test.prompt), null, true))
                }
                val tools = if (test.category == "tools") listOf(fixture) else emptyList()
                val session = openSession(turns, tools)
                val exchanges = mutableListOf<AgentToolExchange>()
                var done = false
                repeat(3) {
                    if (done) return@repeat
                    val roundCalls = linkedMapOf<String, ProviderEvent.ToolCall>()
                    roundStart = text.length
                    roundTokens = null
                    roundAccounted = false
                    var completed = false
                    session.streamRound(tools.map { it.definition }, exchanges.toList()).collect { event ->
                        when (event) {
                            is ProviderEvent.TextDelta -> if (event.text.isNotEmpty()) {
                                val time = now()
                                if (first == null) first = (time - started).coerceAtLeast(0)
                                lastChunk?.let { longestGap = maxOf(longestGap ?: 0, time - it) }
                                lastChunk = time
                                chunks++
                                check(text.length + event.text.length <= 16_384) { "Response exceeded the benchmark size limit" }
                                text.append(event.text)
                            }
                            is ProviderEvent.ToolCall -> {
                                check(test.category == "tools") { "Unexpected tool call in a text test" }
                                calls[event.callId] = event
                                roundCalls[event.callId] = event
                                check(calls.size <= 3) { "Tool call limit reached" }
                            }
                            is ProviderEvent.ToolResult -> if (!event.result.isError && event.call.name == fixture.definition.name && validArguments(event.call.arguments)) {
                                successful.add(event.call.callId)
                            }
                            is ProviderEvent.Usage -> event.outputTokens?.takeIf { it >= 0 }?.let { count ->
                                roundTokens = if (event.cumulative) count else (roundTokens ?: 0) + count
                            }
                            is ProviderEvent.LocalMetrics -> nativeMetrics = event.metrics.native?.takeIf { it.isValid }
                            is ProviderEvent.Failed -> error(event.message)
                            ProviderEvent.Completed -> completed = true
                            else -> Unit
                        }
                    }
                    check(completed) { "Provider ended without a completion event" }
                    if (roundTokens == null) estimated = true
                    tokens += roundTokens ?: ((text.length - roundStart + 3) / 4)
                    roundAccounted = true
                    if (roundCalls.isEmpty() || session.handlesToolsInternally) {
                        done = true
                    } else {
                        val results = roundCalls.values.map { call ->
                            val result = if (call.name == fixture.definition.name) {
                                fixture.execute(call.callId, call.arguments)
                            } else {
                                AgentToolResult(call.callId, ToolResultContent.Text("Unknown fixture tool"), true)
                            }
                            if (!result.isError) successful.add(call.callId)
                            result
                        }
                        exchanges.add(AgentToolExchange(roundCalls.values.toList(), results))
                        // Preserve unsolicited prose so strict final-output checks can detect it.
                        if (text.isNotEmpty()) text.append('\n')
                    }
                }
                check(done) { "Tool round limit reached" }
                check(text.isNotBlank()) { "Provider returned no answer" }
                true
            }
            if (finished != true) return sample(BenchmarkOutcome.TIMED_OUT, "Exceeded ${timeoutMs / 1000}s per-test limit")
            val answer = text.toString().trim()
            val passed = when (test.category) {
                "speed" -> answer.length >= 100
                "json" -> runCatching { Json.parseToJsonElement(answer) == Json.parseToJsonElement("{\"status\":\"ready\",\"count\":3,\"items\":[\"red\",\"green\",\"blue\"]}") }.getOrDefault(false)
                "tools" -> calls.isNotEmpty() && successful.size == calls.size && answer == code
                else -> answer == when (test.id) {
                    "instruction" -> "BENCHMARK_READY"
                    "arithmetic" -> "400"
                    "context" -> "ORCHID-742"
                    else -> ""
                }
            }
            return sample(if (passed) BenchmarkOutcome.PASSED else BenchmarkOutcome.FAILED, if (passed) null else "Response did not meet the fixture's expected result.")
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            return sample(BenchmarkOutcome.ERROR, DiagnosticRedactor.redact(error.message ?: "Benchmark request failed").take(500))
        }
    }
}

private fun message(text: String) = MessageV2(content = text, platformType = null)

private fun validArguments(arguments: JsonObject): Boolean = arguments == buildJsonObject { put("key", "parcel") }

private fun fixtureTool(code: String): AgentTool = object : AgentTool {
    override val definition = AgentToolDefinition(
        "benchmark_lookup",
        "Look up a code in the benchmark's temporary in-memory fixture.",
        buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "key",
                        buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray { add(JsonPrimitive("parcel")) })
                        }
                    )
                }
            )
            put("required", buildJsonArray { add(JsonPrimitive("key")) })
            put("additionalProperties", false)
        }
    )
    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        val valid = validArguments(arguments)
        return AgentToolResult(callId, ToolResultContent.Text(if (valid) code else "Expected exactly key=parcel"), !valid)
    }
}

/** Preserve completed samples, but do not send the rest of a suite to a blocked provider. */
suspend fun runBenchmarkSuite(
    suite: List<BenchmarkCase>,
    runCase: suspend (Int, BenchmarkCase) -> BenchmarkSample,
    onSample: suspend (BenchmarkSample) -> Unit
): String? {
    for ((index, test) in suite.withIndex()) {
        val sample = runCase(index, test)
        onSample(sample)
        if (sample.outcome == BenchmarkOutcome.ERROR || sample.outcome == BenchmarkOutcome.TIMED_OUT) {
            return sample.error ?: "The provider could not complete the benchmark request."
        }
    }
    return null
}
