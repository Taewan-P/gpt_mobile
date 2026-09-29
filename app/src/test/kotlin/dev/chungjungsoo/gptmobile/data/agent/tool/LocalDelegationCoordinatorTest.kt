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
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDelegationCoordinatorTest {
    private val source = PlatformV2(uid = "remote", name = "Remote", compatibleType = ClientType.OPENAI, apiUrl = "https://api.example.com")
    private val target = PlatformV2(uid = "local", name = "Local", compatibleType = ClientType.LLAMA, apiUrl = "http://192.168.1.2:8080")
    private val config = ModelDelegationSettings(enabled = true, targetProfileUid = "local", maxLocalModelCalls = 2)
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

        assertTrue(dispatchedPrompt.length <= 12_000)
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

    @Test fun `settings failure after completed action returns original success without reexecution`() = runTest {
        var actions = 0
        val coordinator = LocalDelegationCoordinator(source, { error("Settings unavailable") }, { listOf(target) }, { _, _, _ -> error("Unused") })
        val result = coordinator.processToolResults(tool { actions++ }, "task").tool.execute("call", buildJsonObject {})
        assertEquals(1, actions)
        assertFalse(result.isError)
        assertEquals(raw, result.content)
    }
}
