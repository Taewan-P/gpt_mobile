package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDelegationCoordinatorTest {
    @Test fun `benchmark runtime honors configured allowance while chat keeps workload limit`() = runTest {
        for (benchmark in listOf(false, true)) {
            val coordinator = LocalDelegationCoordinator(
                source,
                { config.copy(researchEnabled = false, timeoutSeconds = 75, maxDelegateRuntimeSeconds = 120) },
                { listOf(target) },
                { _, _, _ ->
                    delay(50_000)
                    "done"
                },
                useWorkloadRuntimeLimit = !benchmark
            )
            val result = runCatching { coordinator.delegate(target, "small task", 128, emptyList(), "runtime") }
            if (benchmark) assertEquals("done", result.getOrNull()) else assertTrue(result.isFailure)
        }
    }

    private val source = PlatformV2(uid = "remote", name = "Remote", compatibleType = ClientType.OPENAI, apiUrl = "https://api.example.com")
    private val target = PlatformV2(uid = "local", name = "Local", compatibleType = ClientType.LLAMA, apiUrl = "http://192.168.1.2:8080")
    private val config = ModelDelegationSettings(enabled = true, processingOwnership = 50, targetProfileUid = "local", maxLocalModelCalls = 2)
    private val raw = ToolResultContent.Text("Completed action: id=42. " + "Details. ".repeat(1000))
    private fun tool(action: () -> Unit = {}): ResolvedAgentTool {
        val tool = object : AgentTool {
            override val definition = AgentToolDefinition("update_document", "", buildJsonObject {})
            override val managesExecutionBudget = true
            override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
                action()
                return AgentToolResult(callId, raw, false)
            }
        }
        return ResolvedAgentTool(tool, "docs", "Documents", "update_document", "update_document")
    }

    @Test fun `research transforms and direct tasks use separate worker paths`() = runTest {
        var textCalls = 0
        var toolCalls = 0
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false) },
            { listOf(target) },
            { _, _, _ -> error("Must use explicit worker path") },
            generateWithProgress = { _, _, _, _, _ ->
                toolCalls++
                "tool answer"
            },
            generateTextWithProgress = { _, _, _, _, _ ->
                textCalls++
                "text answer"
            }
        )
        assertEquals("text answer", coordinator.processText("Extract a code", 256))
        assertEquals("tool answer", coordinator.executeTask(target, "Inspect a repository", 256))
        assertEquals(1, textCalls)
        assertEquals(1, toolCalls)
    }

    @Test fun `intermittent malformed calls still quarantine the worker`() = runTest {
        var calls = 0
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxLocalModelCalls = 10) },
            { listOf(target) },
            { _, _, _ ->
                calls++
                if (calls % 2 == 1) error("DELEGATION_FAILED: Tool arguments were not valid JSON.")
                "usable answer"
            }
        )
        repeat(5) { index -> runCatching { coordinator.delegate(target, "Task", 256, emptyList(), "case-$index") } }
        assertFalse(coordinator.researchAvailable())
        runCatching { coordinator.delegate(target, "Task", 256, emptyList(), "blocked") }
        assertEquals(5, calls)
    }

    @Test fun `parallel result processing serializes local inference and shares its call allowance`() = runTest {
        var calls = 0
        var active = 0
        var peak = 0
        val coordinator = LocalDelegationCoordinator(source, { config }, { listOf(target) }, { _, _, _ ->
            calls++
            active++
            peak = maxOf(peak, active)
            delay(10)
            active--
            "Completed action id=42."
        })
        val wrapped = coordinator.processToolResults(tool(), "What changed?").tool
        val results = (1..3).map { index -> async { wrapped.execute("$index", buildJsonObject {}) } }.awaitAll()
        assertEquals(2, calls)
        assertEquals(1, peak)
        assertTrue(results.none { it.isError })
        assertTrue(results.all { it.traceContent == raw })
        assertTrue(results.all { it.content.researchText().toByteArray().size <= config.handoffTokens * 3 })
        assertTrue(results.last().content.researchText().contains("exact excerpts"))
    }

    @Test fun `local processing rechecks actual destination and preserves completed action status on failure`() = runTest {
        var profileReads = 0
        var generations = 0
        var actions = 0
        val coordinator = LocalDelegationCoordinator(source, { config.copy(localPlatformsOnly = false) }, {
            profileReads++
            listOf(if (profileReads == 1) target else target.copy(apiUrl = "https://public.example.com"))
        }, { _, _, _ ->
            generations++
            error("Must not send local evidence to a changed cloud destination")
        })
        val result = coordinator.processToolResults(tool { actions++ }, "Inspect the action").tool.execute("result", buildJsonObject {})
        assertEquals(1, actions)
        assertEquals(0, generations)
        assertFalse(result.isError)
        assertTrue(result.content.researchText().contains("do not repeat"))
    }

    @Test fun `disabled same-profile free and unavailable local destinations do not replace remote search`() = runTest {
        for ((helper, settings) in listOf(target to config.copy(enabled = false), source to config.copy(targetProfileUid = source.uid), target.copy(compatibleType = ClientType.FREE) to config, target.copy(apiUrl = "https://public.example.com") to config)) {
            assertFalse(LocalDelegationCoordinator(source, { settings }, { listOf(helper) }, { _, _, _ -> "unused" }).researchAvailable())
        }
        assertFalse(LocalDelegationCoordinator(source, { config }, { listOf(target) }, { _, _, _ -> "unused" }, inputBudget = { _, _ -> error("not downloaded") }).researchAvailable())
    }

    @Test fun `profile local-tool switch prevents result processing and research`() = runTest {
        val coordinator = LocalDelegationCoordinator(source.copy(disableLocalTools = true), { config }, { listOf(target) }, { _, _, _ -> error("Disabled") })
        assertFalse(coordinator.researchAvailable())
        assertEquals(raw, coordinator.processToolResults(tool(), "task").tool.execute("call", buildJsonObject {}).content)
    }

    @Test fun `worker prompt remains bounded when global input budget is unlimited`() = runTest {
        var dispatchedPrompt = ""
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxInputCharacters = 64_000) },
            { listOf(target) },
            { _, prompt, _ ->
                dispatchedPrompt = prompt
                "done"
            },
            inputBudget = { _, _ -> Int.MAX_VALUE }
        )

        coordinator.delegate(target, "x".repeat(30_000), 512, emptyList(), "bounded")

        assertTrue(dispatchedPrompt.length <= 24_000)
    }

    @Test fun `oversized direct delegation is chunked and never dispatches the original giant payload`() = runTest {
        val prompts = mutableListOf<String>()
        val safe = config.copy(
            researchEnabled = false,
            maxLocalModelCalls = 8,
            maxInputCharacters = 64_000,
            maxInputTokensPerDelegate = 2_000,
            chunkSizeTokens = 1_500,
            retryChunkSizeTokens = 750
        )
        val coordinator = LocalDelegationCoordinator(
            source,
            { safe },
            { listOf(target) },
            { _, prompt, _ ->
                prompts += prompt
                "summary-${prompts.size}"
            }
        )

        val original = "x".repeat(20_000)
        val result = coordinator.delegate(target, original, 512, emptyList(), "oversized")

        assertTrue(prompts.size >= 2)
        assertTrue(prompts.none { it == original })
        assertTrue(prompts.all { (it.length + 3) / 4 <= safe.maxInputTokensPerDelegate })
        assertTrue(result.contains("summary"))
    }

    @Test fun `exhausted worker budget does not drift or invite another delegation`() = runTest {
        var generations = 0
        val oneCall = config.copy(researchEnabled = false, maxLocalModelCalls = 1)
        val coordinator = LocalDelegationCoordinator(
            source,
            { oneCall },
            { listOf(target) },
            { _, _, _ ->
                generations++
                "done"
            }
        )

        assertEquals("done", coordinator.delegate(target, "first", 128, emptyList(), "first"))
        val exhausted = coordinator.delegate(target, "second", 128, emptyList(), "second")

        assertEquals(1, generations)
        assertTrue(exhausted.contains("allowance for this turn is exhausted"))
        assertFalse(coordinator.researchAvailable())
    }

    @Test fun `two consecutive empty delegated responses open the worker circuit`() = runTest {
        var calls = 0
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxLocalModelCalls = 6) },
            { listOf(target) },
            { _, _, _ -> error("progressive path expected") },
            generateWithProgress = { _, _, _, _, progress ->
                calls++
                progress(DelegateProgress(DelegateProgressKind.USAGE, inputTokens = 4_800, outputTokens = 256, totalTokens = 5_056))
                error("EMPTY_RESPONSE: delegated provider completed without usable content.")
            }
        )

        val first = runCatching { coordinator.delegate(target, "first", 256, emptyList(), "first") }.exceptionOrNull()
        val second = runCatching { coordinator.delegate(target, "second", 256, emptyList(), "second") }.exceptionOrNull()
        val third = runCatching { coordinator.delegate(target, "third", 256, emptyList(), "third") }.exceptionOrNull()

        assertTrue(first?.message.orEmpty().contains("CANCELED_NO_RESULT"))
        assertTrue(second?.message.orEmpty().contains("CANCELED_NO_RESULT"))
        assertTrue(third?.message.orEmpty().contains("CANCELED_NO_RESULT"))
        assertEquals(2, calls)
        assertFalse(coordinator.researchAvailable())
    }

    @Test fun `two watchdog timeouts stop further dispatches to the same worker`() = runTest {
        var calls = 0
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = true, maxLocalModelCalls = 6, timeoutSeconds = 5, maxDelegateRuntimeSeconds = 5) },
            { listOf(target) },
            { _, _, _ ->
                calls++
                delay(60_000)
                "too late"
            }
        )

        repeat(3) {
            val failure = runCatching { coordinator.delegate(target, "task", 128, emptyList(), "timeout-$it") }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("CANCELED_NO_RESULT"))
        }
        assertEquals(2, calls)
        assertFalse(coordinator.researchAvailable())
    }

    @Test fun `watchdog timeout circuit fails over within the same request`() = runTest {
        val fallback = target.copy(uid = "fallback")
        val dispatched = mutableListOf<String>()
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxLocalModelCalls = 6, timeoutSeconds = 5, maxDelegateRuntimeSeconds = 5) },
            { listOf(target, fallback) },
            { profile, _, _ ->
                dispatched += profile.uid
                if (profile.uid == target.uid) delay(60_000)
                "recovered"
            }
        )

        assertTrue(runCatching { coordinator.delegate(target, "first", 128, emptyList(), "first") }.isFailure)
        assertEquals("recovered", coordinator.delegate(target, "second", 128, emptyList(), "second"))
        assertEquals("recovered", coordinator.delegate(target, "third", 128, emptyList(), "third"))
        assertEquals(listOf("local", "local", "fallback", "fallback"), dispatched)
    }

    @Test fun `successful response resets consecutive timeout circuit`() = runTest {
        var calls = 0
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxLocalModelCalls = 6, timeoutSeconds = 5) },
            { listOf(target) },
            { _, _, _ ->
                calls++
                if (calls % 2 == 1) delay(60_000)
                "done"
            }
        )

        assertTrue(runCatching { coordinator.delegate(target, "first", 128, emptyList(), "first") }.isFailure)
        assertEquals("done", coordinator.delegate(target, "second", 128, emptyList(), "second"))
        assertTrue(runCatching { coordinator.delegate(target, "third", 128, emptyList(), "third") }.isFailure)
        assertEquals("done", coordinator.delegate(target, "fourth", 128, emptyList(), "fourth"))
        assertEquals(4, calls)
    }

    @Test fun `connection abort quarantines worker immediately`() = runTest {
        var calls = 0
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = true, maxLocalModelCalls = 6) },
            { listOf(target) },
            { _, _, _ ->
                calls++
                error("DELEGATION_FAILED: Software caused connection abort")
            }
        )

        repeat(2) {
            assertTrue(runCatching { coordinator.delegate(target, "task", 128, emptyList(), "abort-$it") }.isFailure)
        }
        assertEquals(1, calls)
        assertFalse(coordinator.researchAvailable())
    }

    @Test fun `socket timeout quarantines worker immediately and uses fallback`() = runTest {
        val fallback = target.copy(uid = "fallback")
        val dispatched = mutableListOf<String>()
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxLocalModelCalls = 4) },
            { listOf(target, fallback) },
            { profile, _, _ ->
                dispatched += profile.uid
                if (profile.uid == target.uid) throw java.net.SocketTimeoutException("Read timed out")
                "recovered"
            }
        )

        assertEquals("recovered", coordinator.executeTask(target, "first", 128))
        assertEquals("recovered", coordinator.executeTask(target, "second", 128))
        assertEquals(listOf("local", "fallback", "fallback"), dispatched)
    }

    @Test fun `stale configured target falls back to an eligible helper`() = runTest {
        val stale = config.copy(targetProfileUid = "missing", researchEnabled = true)
        val coordinator = LocalDelegationCoordinator(
            source,
            { stale },
            { listOf(target) },
            { _, _, _ -> "done" }
        )

        assertTrue(coordinator.researchAvailable())
    }

    @Test fun `single reasoning only response does not quarantine worker`() = runTest {
        var calls = 0
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxLocalModelCalls = 4) },
            { listOf(target) },
            { _, _, _ -> error("progressive path expected") },
            generateWithProgress = { _, _, _, _, progress ->
                calls++
                progress(DelegateProgress(DelegateProgressKind.USAGE, inputTokens = 1_000, outputTokens = 256, totalTokens = 1_256))
                if (calls == 1) error("REASONING_ONLY_RESPONSE: delegated provider produced reasoning tokens but no usable final answer.")
                "recovered"
            }
        )

        val first = runCatching { coordinator.delegate(target, "first", 256, emptyList(), "first") }.exceptionOrNull()
        val second = coordinator.delegate(target, "second", 256, emptyList(), "second")

        assertTrue(first?.message.orEmpty().contains("CANCELED_NO_RESULT"))
        assertEquals("recovered", second)
        assertEquals(2, calls)
    }

    @Test fun `authorization failure quarantines delegated worker for the rest of the turn`() = runTest {
        var calls = 0
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxLocalModelCalls = 6) },
            { listOf(target) },
            { _, _, _ ->
                calls++
                error("OpenRouter denied access (HTTP 403)")
            }
        )

        val first = runCatching { coordinator.delegate(target, "first", 256, emptyList(), "first") }.exceptionOrNull()
        val second = runCatching { coordinator.delegate(target, "second", 256, emptyList(), "second") }.exceptionOrNull()

        assertTrue(first?.message.orEmpty().contains("CANCELED_NO_RESULT"))
        assertTrue(second?.message.orEmpty().contains("CANCELED_NO_RESULT"))
        assertEquals(1, calls)
    }

    @Test fun `retired model is quarantined and delegation immediately fails over`() = runTest {
        val fallback = target.copy(
            uid = "fallback",
            name = "Fallback",
            compatibleType = ClientType.LLAMA,
            apiUrl = "http://192.168.1.3:8080"
        )
        val dispatched = mutableListOf<String>()
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxLocalModelCalls = 4) },
            { listOf(target, fallback) },
            { profile, _, _ ->
                dispatched += profile.uid
                if (profile.uid == target.uid) {
                    error("HTTP 410 Gone: model retired and no longer available")
                }
                "recovered"
            }
        )

        assertEquals("recovered", coordinator.delegate(target, "first", 256, emptyList(), "first"))
        assertEquals("recovered", coordinator.delegate(target, "second", 256, emptyList(), "second"))
        assertEquals(listOf("local", "fallback", "fallback"), dispatched)
    }

    @Test fun `llama prompt evaluation is not canceled by the generic first token watchdog`() = runTest {
        val coordinator = LocalDelegationCoordinator(
            source,
            {
                config.copy(
                    researchEnabled = false,
                    maxLocalModelCalls = 2,
                    timeoutSeconds = 30,
                    maxDelegateRuntimeSeconds = 30,
                    timeToFirstTokenTimeoutSeconds = 5,
                    idleTokenTimeoutSeconds = 5
                )
            },
            { listOf(target) },
            { _, _, _ -> error("progressive path expected") },
            generateWithProgress = { _, _, _, _, progress ->
                progress(DelegateProgress(DelegateProgressKind.REQUEST_STARTED))
                delay(6_000)
                progress(DelegateProgress(DelegateProgressKind.OUTPUT))
                "done"
            }
        )

        assertEquals("done", coordinator.delegate(target, "slow prompt evaluation", 128, emptyList(), "llama-warmup"))
    }

    @Test fun `settings failure after completed action returns original success without reexecution`() = runTest {
        var actions = 0
        val coordinator = LocalDelegationCoordinator(source, { error("Settings unavailable") }, { listOf(target) }, { _, _, _ -> error("Unused") })
        val result = coordinator.processToolResults(tool { actions++ }, "task").tool.execute("call", buildJsonObject {})
        assertEquals(1, actions)
        assertFalse(result.isError)
        assertEquals(raw, result.content)
    }

    @Test fun `named missing model is quarantined on the first failure`() = runTest {
        var failedCalls = 0
        val fallback = target.copy(uid = "working", model = "available-model")
        val coordinator = LocalDelegationCoordinator(source, { config.copy(researchEnabled = false) }, { listOf(target, fallback) }, { profile, _, _ ->
            if (profile.uid == target.uid) {
                failedCalls++
                error("DELEGATION_FAILED: model 'jackod' not found")
            }
            "usable fallback answer"
        })
        assertEquals("usable fallback answer", coordinator.executeTask(target, "Read a page", 256))
        assertEquals(1, failedCalls)
        assertFalse(coordinator.researchAvailable())
    }

    @Test fun `not downloaded local model is quarantined before repeated fallback attempts`() = runTest {
        var missingGenerations = 0
        var fallbackGenerations = 0
        val missing = target.copy(uid = "missing-local", compatibleType = ClientType.LITERT_LM, model = "missing-model")
        val fallback = target.copy(uid = "working-local", model = "available-model")
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(targetProfileUid = missing.uid, researchEnabled = false, maxLocalModelCalls = 4) },
            { listOf(missing, fallback) },
            { profile, _, _ ->
                if (profile.uid == missing.uid) {
                    missingGenerations++
                    error("Missing worker must never reach generation")
                }
                fallbackGenerations++
                "usable fallback answer"
            },
            inputBudget = { profile, _ ->
                if (profile.uid == missing.uid) {
                    error("This Local Model is not downloaded. Download it from Settings → Local Models.")
                }
                Int.MAX_VALUE
            }
        )

        assertEquals("usable fallback answer", coordinator.executeTask(missing, "Read a page", 256))
        assertEquals("usable fallback answer", coordinator.executeTask(missing, "Read another page", 256))
        assertEquals(0, missingGenerations)
        assertEquals(2, fallbackGenerations)
    }

    @Test fun `reasoning only recovery expands the next output cap`() = runTest {
        val caps = mutableListOf<Int>()
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxLocalModelCalls = 4, maxOutputTokens = 768) },
            { listOf(target) },
            { _, _, _ -> error("progressive path expected") },
            generateWithProgress = { _, _, cap, _, progress ->
                caps += cap
                progress(DelegateProgress(DelegateProgressKind.USAGE, inputTokens = 3_900, outputTokens = cap.toLong(), totalTokens = 3_900L + cap))
                if (caps.size == 1) {
                    error("REASONING_ONLY_RESPONSE: delegated provider produced reasoning tokens but no usable final answer.")
                }
                "recovered"
            }
        )

        assertTrue(runCatching { coordinator.delegate(target, "first", 128, emptyList(), "first") }.isFailure)
        assertEquals("recovered", coordinator.delegate(target, "second", 128, emptyList(), "second"))
        assertEquals(listOf(128, 768), caps)
    }

    @Test fun `Google missing identity quarantines worker immediately`() = runTest {
        var calls = 0
        val coordinator = LocalDelegationCoordinator(source, { config.copy(researchEnabled = false) }, { listOf(target) }, { _, _, _ ->
            calls++
            error("Method doesn't allow unregistered callers. Please use API Key.")
        })
        coordinator.executeTask(target, "first", 256)
        coordinator.executeTask(target, "second", 256)
        assertEquals(1, calls)
    }

    @Test fun `pinned unavailable worker never substitutes another provider`() = runTest {
        var calls = 0
        val fallback = target.copy(uid = "other")
        val coordinator = LocalDelegationCoordinator(source, { config.copy(fallbackToAnotherProfile = false) }, { listOf(fallback) }, { _, _, _ ->
            calls++
            "wrong provider"
        })
        assertNull(coordinator.executeTask(target, "task", 256))
        assertEquals(0, calls)
    }

    @Test fun `interactive recovery switches to the user selected delegate`() = runTest {
        val fallback = target.copy(uid = "fallback", name = "Fallback", apiUrl = "http://192.168.1.3:8080")
        val dispatched = mutableListOf<String>()
        var recoveryPrompts = 0
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxLocalModelCalls = 4, fallbackToAnotherProfile = false) },
            { listOf(target, fallback) },
            { profile, _, _ ->
                dispatched += profile.uid
                if (profile.uid == target.uid) error("HTTP 410 Gone: model retired")
                "recovered"
            },
            onRecoveryRequired = { failed, candidates, reason ->
                recoveryPrompts++
                assertEquals(target.uid, failed.uid)
                assertTrue(reason.contains("failed", ignoreCase = true))
                assertEquals(listOf(fallback.uid), candidates.map { it.uid })
                DelegationRecoveryDecision.SwitchProfile(fallback.uid)
            }
        )

        assertEquals("recovered", coordinator.delegate(target, "task", 256, emptyList(), "interactive"))
        assertEquals("recovered", coordinator.delegate(target, "follow-up", 256, emptyList(), "interactive-follow-up"))
        assertEquals(listOf(target.uid, fallback.uid, fallback.uid), dispatched)
        assertEquals(1, recoveryPrompts)
    }

    @Test fun `canceling interactive recovery disables delegation for the rest of the turn`() = runTest {
        val fallback = target.copy(uid = "fallback", name = "Fallback", apiUrl = "http://192.168.1.3:8080")
        var generations = 0
        var recoveryPrompts = 0
        val coordinator = LocalDelegationCoordinator(
            source,
            { config.copy(researchEnabled = false, maxLocalModelCalls = 4) },
            { listOf(target, fallback) },
            { _, _, _ ->
                generations++
                error("HTTP 410 Gone: model retired")
            },
            onRecoveryRequired = { _, _, _ ->
                recoveryPrompts++
                DelegationRecoveryDecision.PrimaryOnly
            }
        )

        val first = coordinator.delegate(target, "task", 256, emptyList(), "cancel")
        val second = coordinator.delegate(target, "another task", 256, emptyList(), "cancel-again")

        assertTrue(first.contains("primary model only"))
        assertTrue(second.contains("primary model only"))
        assertEquals(1, generations)
        assertEquals(1, recoveryPrompts)
        assertFalse(coordinator.researchAvailable())
    }
}
