package dev.chungjungsoo.gptmobile.data.benchmark

import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.agent.tool.LocalDelegationCoordinator
import dev.chungjungsoo.gptmobile.data.agent.tool.LocalResearchOutcome
import dev.chungjungsoo.gptmobile.data.agent.tool.ResolvedAgentTool
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
import dev.chungjungsoo.gptmobile.data.security.DiagnosticRedactor
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

fun delegationBenchmarkSuite(): List<BenchmarkCase> = listOf(
    BenchmarkCase("delegation-compact", "Evidence compaction", "delegation", ""),
    BenchmarkCase("delegation-tools", "Worker tool round trip", "delegation", ""),
    BenchmarkCase("delegation-research", "Research → handoff → final answer", "delegation", "")
)

/** Uses the conversation coordinator with synthetic evidence and no connected MCP/device tools. */
internal class DelegationBenchmarkRunner(
    private val createCoordinator: (List<AgentTool>) -> LocalDelegationCoordinator,
    private val target: PlatformV2,
    private val config: ModelDelegationSettings,
    private val openPrimary: suspend (List<ConversationTurn>, List<AgentTool>) -> AgentProviderSession,
    private val workerTokens: () -> Pair<Long, Long>,
    private val workerCalls: () -> Int,
    private val workerConfigKey: String = benchmarkConfigKey(target),
    private val telemetry: () -> WorkerBenchmarkTelemetry = { WorkerBenchmarkTelemetry() },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    suspend fun run(test: BenchmarkCase): BenchmarkSample {
        val started = now()
        val code = "PKG-${UUID.randomUUID().toString().take(8)}"
        var answer = ""
        var searches = 0
        var pages = 0
        var rawBytes = 0
        var handoffChars = 0
        var primaryInput = 0L
        var primaryOutput = 0L
        var primaryEstimated = false
        var fixtureCalls = 0
        var successfulCalls = 0
        val lookup = object : AgentTool {
            override val definition = AgentToolDefinition(
                "benchmark_lookup",
                "Return the parcel code. Call with key=parcel.",
                buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put(
                        "properties",
                        buildJsonObject {
                            put("key", buildJsonObject { put("type", "string") })
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("key")) })
                }
            )
            override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
                fixtureCalls++
                val valid = arguments == buildJsonObject { put("key", JsonPrimitive("parcel")) }
                if (valid) successfulCalls++
                return AgentToolResult(callId, ToolResultContent.Text(if (valid) code else "Expected key=parcel"), !valid)
            }
        }
        val coordinator = createCoordinator(listOf(lookup))
        val before = workerTokens()
        val callsBefore = workerCalls()
        fun sample(outcome: BenchmarkOutcome, error: String? = null): BenchmarkSample {
            val after = workerTokens()
            val timing = telemetry()
            return BenchmarkSample(
                test.id, test.label, test.category, outcome, durationMs = (now() - started).coerceAtLeast(0),
                outputCharacters = answer.length, preview = answer.take(1000), error = error,
                delegation = DelegationBenchmarkMetrics(
                    target.uid, target.name, target.compatibleType.name, workerCalls() - callsBefore,
                    after.first - before.first, after.second - before.second, primaryInput, primaryOutput,
                    searches, pages, rawBytes, handoffChars, fixtureCalls, successfulCalls, primaryEstimated,
                    target.model, workerConfigKey, timing.estimated, timing.durationMs,
                    timing.firstTextMs, timing.decodeTokensPerSecond, timing.outputCapViolations
                )
            )
        }
        try {
            val finished = withTimeoutOrNull(180_000) {
                when (test.id) {
                    "delegation-compact" -> {
                        val evidence = "Parcel code: $code. " + "Unrelated warehouse notes. ".repeat(150)
                        rawBytes = evidence.toByteArray().size
                        answer = coordinator.processText("Extract the parcel code from the supplied evidence. Reply with only the code.\n$evidence", config.handoffTokens).orEmpty()
                        handoffChars = answer.length
                        check(answer.trim() == code) { "Compaction lost the fixture's parcel code or returned no usable answer." }
                    }
                    "delegation-tools" -> {
                        // Direct delegation uses the same watchdog and worker gate as chat.
                        answer = coordinator.executeTask(target, "Call benchmark_lookup with key=\"parcel\" and reply with only the returned code. Do not guess.", config.maxOutputTokens).orEmpty()
                        check(answer.trim() == code && fixtureCalls > 0 && fixtureCalls == successfulCalls) { "Worker did not return the code from its isolated fixture tool." }
                    }
                    "delegation-research" -> {
                        val fixtures = listOf(
                            fixture("web_search", "Search the temporary parcel fixture.", "{\"results\":[{\"title\":\"Parcel fixture\",\"url\":\"https://example.org/parcel\",\"snippet\":\"Read the page for the parcel code.\"}]}"),
                            fixture("read_url", "Read the temporary parcel fixture.", "Parcel code is $code. This is synthetic benchmark evidence.")
                        ).map { fixture ->
                            val tool = object : AgentTool {
                                override val definition = fixture.definition
                                override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
                                    fixtureCalls++
                                    val result = fixture.execute(callId, arguments)
                                    if (!result.isError) successfulCalls++
                                    return result
                                }
                            }
                            ResolvedAgentTool(tool, null, "Benchmark fixture", tool.definition.name, tool.definition.name, true)
                        }
                        val result = coordinator.prepare("Research https://example.org/parcel using the supplied tools and report the parcel code with its source URL.", fixtures, "benchmark-research")
                        searches = result.searches
                        pages = result.pagesRead
                        rawBytes = result.rawBytes
                        handoffChars = result.handoff.length
                        check(result.outcome == LocalResearchOutcome.SUCCESS && pages > 0 && code in result.handoff) { "Research did not read and preserve the fixture evidence. Check helper settings and output limits." }
                        val primary = BenchmarkRunner(openPrimary, now).run(
                            BenchmarkCase("handoff", "Primary handoff", "speed", "Using only this reference evidence, report the parcel code and its source URL in one sentence.\n${result.handoff}"),
                            false
                        )
                        primaryInput = primary.inputTokens.toLong()
                        primaryOutput = primary.outputTokens.toLong()
                        primaryEstimated = primary.estimatedTokens
                        answer = primary.preview
                        check(primary.completed && code in answer && "https://example.org/parcel" in answer) { primary.error ?: "The primary answer lost the code or source URL during handoff." }
                    }
                    else -> error("Unknown delegation benchmark case")
                }
                true
            }
            return if (finished == true) sample(BenchmarkOutcome.PASSED) else sample(BenchmarkOutcome.TIMED_OUT, "Delegation benchmark exceeded its 180-second case limit.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IllegalStateException) {
            return sample(BenchmarkOutcome.FAILED, DiagnosticRedactor.redact(failure.message ?: "Fixture validation failed").take(500))
        } catch (failure: Exception) {
            return sample(BenchmarkOutcome.ERROR, DiagnosticRedactor.redact(failure.message ?: "Delegation benchmark failed").take(500))
        }
    }

    private fun fixture(name: String, description: String, response: String): AgentTool = object : AgentTool {
        override val definition = AgentToolDefinition(
            name,
            description,
            buildJsonObject {
                put("type", JsonPrimitive("object"))
                val key = if (name == "read_url") "url" else "query"
                put(
                    "properties",
                    buildJsonObject {
                        put(key, buildJsonObject { put("type", "string") })
                    }
                )
                put("required", buildJsonArray { add(JsonPrimitive(key)) })
            }
        )
        override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
            val key = if (name == "read_url") "url" else "query"
            val value = (arguments[key] as? JsonPrimitive)?.contentOrNull
            val valid = if (name == "read_url") value == "https://example.org/parcel" else !value.isNullOrBlank()
            return AgentToolResult(callId, ToolResultContent.Text(if (valid) response else "Invalid fixture $key"), !valid)
        }
    }
}
