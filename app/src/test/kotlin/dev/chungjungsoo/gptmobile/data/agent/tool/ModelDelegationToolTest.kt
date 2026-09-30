package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDelegationToolTest {
    private val source = PlatformV2(uid = "source", name = "Main", compatibleType = ClientType.OPENAI)
    private val target = PlatformV2(uid = "target", name = "Local", compatibleType = ClientType.LLAMA, apiUrl = "http://192.168.1.20:8080/v1")
    private val enabled = ModelDelegationSettings(enabled = true, processingOwnership = 50, targetProfileUid = target.uid, localPlatformsOnly = false, maxCallsPerTurn = 1)
    private val task = buildJsonObject { put("task", "Summarize this text") }

    @Test
    fun onDeviceSourceCanDelegateToOllamaAndLlamaWhileThePhoneEngineIsBusy() = runTest {
        for (type in listOf(ClientType.OLLAMA, ClientType.LLAMA)) {
            var called = false
            val tool = ModelDelegationTool(source.copy(compatibleType = ClientType.LITERT_LM), { enabled }, { listOf(target.copy(compatibleType = type)) }) { _, _, _ ->
                called = true
                "Second opinion"
            }
            assertFalse(tool.execute("local-$type", task).isError)
            assertTrue(called)
        }
    }

    @Test
    fun delegatesToConfiguredProfileAndEnforcesPerTurnBudget() = runTest {
        var calls = 0
        val tool = ModelDelegationTool(source, { enabled }, { listOf(target) }) { p, text, tokens ->
            assertEquals(target.uid, p.uid)
            assertEquals("Summarize this text", text)
            assertEquals(512, tokens)
            calls++
            "Summary"
        }
        assertFalse(tool.execute("1", task).isError)
        assertTrue(tool.execute("2", task).isError)
        assertEquals(1, calls)
    }

    @Test
    fun blocksDisabledSelfCloudAndBusyOnDeviceTargets() = runTest {
        suspend fun blocked(s: PlatformV2, p: PlatformV2, config: ModelDelegationSettings) {
            var called = false
            val tool = ModelDelegationTool(s, { config }, { listOf(p) }) { _, _, _ ->
                called = true
                "unexpected"
            }
            assertTrue(tool.execute("1", task).isError)
            assertFalse(called)
        }
        blocked(source, target, enabled.copy(enabled = false))
        blocked(source, source, enabled.copy(targetProfileUid = source.uid))
        blocked(source, target.copy(enabled = false), enabled)
        blocked(source, target.copy(compatibleType = ClientType.GOOGLE, apiUrl = "https://generativelanguage.googleapis.com"), enabled.copy(localPlatformsOnly = true))
        blocked(source, target.copy(apiUrl = "https://public.example.com/v1"), enabled.copy(localPlatformsOnly = true))
        blocked(source.copy(compatibleType = ClientType.LITERT_LM), target.copy(compatibleType = ClientType.LITERT_LM), enabled)
    }

    @Test
    fun permitsNonFreeProviderTypesWhenConfiguredAndRejectsOversizedInput() = runTest {
        for (type in ClientType.entries.filterNot { it == ClientType.FREE }) {
            val tool = ModelDelegationTool(source, { enabled.copy(localPlatformsOnly = false) }, { listOf(target.copy(compatibleType = type)) }) { _, _, _ -> "OK" }
            assertFalse(type.name, tool.execute("1", task).isError)
        }
        val tool = ModelDelegationTool(source, { enabled }, { listOf(target) }) { _, _, _ -> error("Must not run") }
        assertTrue(tool.execute("1", buildJsonObject { put("task", "x".repeat(8001)) }).isError)
    }

    @Test
    fun staleConfiguredTargetFallsBackToEligibleHelper() = runTest {
        var selected: String? = null
        val tool = ModelDelegationTool(
            source,
            { enabled.copy(targetProfileUid = "missing") },
            { listOf(target) }
        ) { profile, _, _ ->
            selected = profile.uid
            "Recovered"
        }

        val result = tool.execute("stale-target", task)

        assertFalse(result.isError)
        assertEquals(target.uid, selected)
    }

    @Test
    fun remoteWorkerOptInAllowsRemoteToRemoteDelegation() = runTest {
        val remote = target.copy(compatibleType = ClientType.OPENROUTER, apiUrl = "https://openrouter.ai/api/v1")
        var called = false
        val tool = ModelDelegationTool(
            source.copy(compatibleType = ClientType.OPENAI),
            { enabled.copy(allowRemoteWorkers = true, localPlatformsOnly = true) },
            { listOf(remote) }
        ) { profile, _, _ ->
            called = profile.uid == remote.uid
            "Remote result"
        }
        assertFalse(tool.execute("remote-remote", task).isError)
        assertTrue(called)
    }

    @Test
    fun freeTargetsNeverReceiveDelegatedContextEvenWhenCloudDelegationIsEnabled() = runTest {
        val targets = listOf(
            target.copy(compatibleType = ClientType.FREE),
            target.copy(compatibleType = ClientType.OPENROUTER, model = "model:free"),
            target.copy(compatibleType = ClientType.CUSTOM, model = "kilo-auto/free")
        )
        for (free in targets) {
            val tool = ModelDelegationTool(source, { enabled.copy(localPlatformsOnly = false) }, { listOf(free) }) { _, _, _ -> error("Free targets must not receive delegated context") }
            assertTrue(tool.execute("free", task).isError)
        }
    }

    @Test
    fun timesOutAndPropagatesParentCancellation() = runTest {
        val slow = ModelDelegationTool(
            source,
            { enabled.copy(timeoutSeconds = 5, maxLocalModelCalls = 1) },
            { listOf(target) }
        ) { _, _, _ ->
            delay(60_000)
            "late"
        }
        assertTrue(slow.execute("1", task).isError)
        val canceled = ModelDelegationTool(source, { enabled }, { listOf(target) }) { _, _, _ -> throw CancellationException("Stopped") }
        assertTrue(runCatching { canceled.execute("1", task) }.exceptionOrNull() is CancellationException)
    }

    @Test
    fun failedDelegationRestoresExplicitCallBudgetForRetry() = runTest {
        var attempts = 0
        val tool = ModelDelegationTool(source, { enabled }, { listOf(target) }) { _, _, _ ->
            attempts++
            if (attempts == 1) error("temporary delegate failure")
            "Recovered"
        }

        assertTrue(tool.execute("first", task).isError)
        assertFalse(tool.execute("retry", task).isError)
        assertEquals(2, attempts)
    }

    @Test
    fun localFirstOwnershipRaisesExplicitDelegationAllowance() = runTest {
        var attempts = 0
        val localFirst = enabled.copy(processingOwnership = 0, maxCallsPerTurn = 1)
        val tool = ModelDelegationTool(source, { localFirst }, { listOf(target) }) { _, _, _ ->
            attempts++
            "OK"
        }

        repeat(4) { index ->
            assertFalse(tool.execute("call-$index", task).isError)
        }
        assertTrue(tool.execute("call-5", task).isError)
        assertEquals(4, attempts)
    }

    @Test
    fun runtimeRechecksSettingsBeforeEveryCall() = runTest {
        var config = enabled.copy(maxCallsPerTurn = 3)
        var count = 0
        val tool = ModelDelegationTool(source, { config }, { listOf(target) }) { _, _, _ ->
            count++
            "OK"
        }
        assertFalse(tool.execute("1", task).isError)
        config = config.copy(enabled = false)
        assertTrue(tool.execute("2", task).isError)
        assertEquals(1, count)
    }
}
