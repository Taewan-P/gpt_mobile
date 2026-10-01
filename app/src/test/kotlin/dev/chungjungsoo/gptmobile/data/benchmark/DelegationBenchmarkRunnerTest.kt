package dev.chungjungsoo.gptmobile.data.benchmark

import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.agent.tool.LocalDelegationCoordinator
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegationBenchmarkRunnerTest {
    @Test fun `parcel validation accepts formatting but rejects missing wrong and extra codes`() {
        val code = "PKG-a1b2c3d4"
        assertTrue(hasExpectedParcelCode("The code is **$code**.", code))
        assertEquals(false, hasExpectedParcelCode("No result", code))
        assertEquals(false, hasExpectedParcelCode("PKG-00000000", code))
        assertEquals(false, hasExpectedParcelCode("$code or PKG-00000000", code))
        assertEquals(false, hasExpectedParcelCode("${code}00", code))
    }

    @Test fun `tool benchmark requests are unique and accept formatted tool results`() = runTest {
        val prompts = mutableListOf<String>()
        repeat(2) {
            val runner = DelegationBenchmarkRunner(
                createCoordinator = { tools ->
                    LocalDelegationCoordinator(source, { config }, { listOf(helper) }, { _, prompt, _ ->
                        prompts += prompt
                        val result = tools.single().execute("fixture", buildJsonObject { put("key", "parcel") })
                        "Code: **${(result.content as ToolResultContent.Text).text}**"
                    })
                },
                target = helper,
                config = config,
                openPrimary = { _, _ -> error("Tool test must not call primary") },
                workerTokens = { 0L to 0L },
                workerCalls = { 0 }
            )
            assertEquals(BenchmarkOutcome.PASSED, runner.run(delegationBenchmarkSuite()[1]).outcome)
        }
        assertEquals(2, prompts.distinct().size)
    }

    @Test fun `delegation samples persist diagnostic events for troubleshooting`() = runTest {
        val runner = DelegationBenchmarkRunner(
            createCoordinator = { tools ->
                LocalDelegationCoordinator(source, { config }, { listOf(helper) }, { _, _, _ ->
                    val result = tools.single().execute("fixture", buildJsonObject { put("key", "parcel") })
                    (result.content as ToolResultContent.Text).text
                })
            },
            target = helper,
            config = config,
            openPrimary = { _, _ -> error("Tool test must not call primary") },
            workerTokens = { 100L to 20L },
            workerCalls = { 1 },
            telemetry = {
                WorkerBenchmarkTelemetry(
                    durationMs = 250,
                    firstTextMs = 80,
                    decodeTokensPerSecond = 42.0,
                    speedUsesReportedTokens = true,
                    events = listOf(DelegationBenchmarkEvent(80, "FIRST_TEXT", message = "latencyMs=80"))
                )
            }
        )
        val sample = runner.run(delegationBenchmarkSuite()[1])
        val metrics = sample.delegation!!
        assertTrue(metrics.workerSpeedUsesReportedTokens)
        assertTrue(metrics.diagnosticEvents.any { it.type == "CASE_START" })
        assertTrue(metrics.diagnosticEvents.any { it.type == "FIXTURE_TOOL" })
        assertTrue(metrics.diagnosticEvents.any { it.type == "FIRST_TEXT" })
        assertTrue(metrics.diagnosticEvents.any { it.type == "CASE_COMPLETE" })
    }

    @Test fun `transport failures retain the root cause instead of failing tool capability validation`() = runTest {
        val runner = DelegationBenchmarkRunner(
            createCoordinator = {
                LocalDelegationCoordinator(source, { config.copy(fallbackToAnotherProfile = false) }, { listOf(helper) }, { _, _, _ ->
                    error("DELEGATION_FAILED: Software caused connection abort")
                })
            },
            target = helper,
            config = config,
            openPrimary = { _, _ -> error("Must not call primary") },
            workerTokens = { 0L to 0L },
            workerCalls = { 1 }
        )
        val result = runner.run(delegationBenchmarkSuite()[1])
        assertEquals(BenchmarkOutcome.ERROR, result.outcome)
        assertTrue(result.error.orEmpty().contains("Software caused connection abort"))
        assertEquals(0, result.delegation!!.fixtureCalls)
    }

    private val source = PlatformV2(uid = "primary", name = "Primary", compatibleType = ClientType.OPENAI)
    private val helper = PlatformV2(uid = "helper", name = "Helper", compatibleType = ClientType.LLAMA, apiUrl = "http://192.168.1.2:8080")
    private val config = ModelDelegationSettings(enabled = true, targetProfileUid = "helper", maxPages = 1, crawlDepth = 0, maxLocalModelCalls = 10)

    @Test fun `pipeline reads fixture then preserves code and source through primary synthesis`() = runTest {
        var calls = 0
        var primaryCalls = 0
        val runner = DelegationBenchmarkRunner(
            createCoordinator = {
                LocalDelegationCoordinator(source, { config }, { listOf(helper) }, { _, prompt, _ ->
                    calls++
                    when {
                        prompt.startsWith("Plan public-web") -> """{"queries":[],"urls":["https://example.org/parcel"]}"""
                        prompt.startsWith("Choose up to") -> """{"ids":["S1"]}"""
                        else -> Regex("PKG-[a-f0-9]{8}").find(prompt)?.value?.let { "$it https://example.org/parcel" } ?: "No evidence"
                    }
                })
            },
            target = helper,
            config = config,
            openPrimary = { turns, tools ->
                primaryCalls++
                assertTrue(tools.isEmpty())
                val prompt = turns.single().userMessage.content
                val code = Regex("PKG-[a-f0-9]{8}").find(prompt)!!.value
                object : AgentProviderSession {
                    override fun streamRound(tools: List<AgentToolDefinition>, exchanges: List<AgentToolExchange>) = flowOf(
                        ProviderEvent.TextDelta("Parcel code $code, source https://example.org/parcel."),
                        ProviderEvent.Usage(inputTokens = 100, outputTokens = 20),
                        ProviderEvent.Completed
                    )
                }
            },
            workerTokens = { calls * 100L to calls * 20L },
            workerCalls = { calls }
        )
        val result = runner.run(delegationBenchmarkSuite().last())
        assertEquals(BenchmarkOutcome.PASSED, result.outcome)
        assertEquals(1, primaryCalls)
        assertEquals(1, result.delegation!!.pagesRead)
        assertEquals(100L, result.delegation!!.primaryInputTokens)
        assertEquals(20L, result.delegation!!.primaryOutputTokens)
        assertEquals(1, result.delegation!!.fixtureCalls)
        assertEquals(1, result.delegation!!.successfulFixtureCalls)
    }

    @Test fun `tool test uses an isolated random fixture and detects invalid arguments`() = runTest {
        for (valid in listOf(true, false)) {
            val runner = DelegationBenchmarkRunner(
                createCoordinator = { tools ->
                    assertEquals(listOf("benchmark_lookup"), tools.map { it.definition.name })
                    LocalDelegationCoordinator(source, { config }, { listOf(helper) }, { _, _, _ ->
                        val result = tools.single().execute("fixture", buildJsonObject { put("key", if (valid) "parcel" else "wrong") })
                        (result.content as ToolResultContent.Text).text
                    })
                },
                target = helper,
                config = config,
                openPrimary = { _, _ -> error("Tool test must not call primary") },
                workerTokens = { 0L to 0L },
                workerCalls = { 0 }
            )
            val result = runner.run(delegationBenchmarkSuite()[1])
            assertEquals(if (valid) BenchmarkOutcome.PASSED else BenchmarkOutcome.FAILED, result.outcome)
            assertEquals(1, result.delegation!!.fixtureCalls)
            assertEquals(if (valid) 1 else 0, result.delegation!!.successfulFixtureCalls)
        }
    }
}
