package dev.chungjungsoo.gptmobile.presentation.service

import dev.chungjungsoo.gptmobile.data.agent.AgentRunLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunForegroundServiceTest {
    @Test
    fun `completion notification posts only for background nonempty to empty transition`() {
        assertTrue(shouldNotifyAgentRunsCompleted(wasActive = true, isActive = false, isAppBackground = true))
    }

    @Test
    fun `completion notification skips initial empty and foreground completion`() {
        assertFalse(shouldNotifyAgentRunsCompleted(wasActive = false, isActive = false, isAppBackground = true))
        assertFalse(shouldNotifyAgentRunsCompleted(wasActive = true, isActive = false, isAppBackground = false))
        assertFalse(shouldNotifyAgentRunsCompleted(wasActive = true, isActive = true, isAppBackground = true))
    }

    @Test
    fun `idle stop resumes only when stopSelfResult failed and work is active`() {
        assertTrue(shouldResumeAfterIdleStop(stopSelfSucceeded = false, hasActiveRuns = true))
        assertFalse(shouldResumeAfterIdleStop(stopSelfSucceeded = true, hasActiveRuns = true))
        assertFalse(shouldResumeAfterIdleStop(stopSelfSucceeded = false, hasActiveRuns = false))
        assertFalse(shouldResumeAfterIdleStop(stopSelfSucceeded = true, hasActiveRuns = false))
    }

    @Test
    fun `staggered active runs refresh the bounded lease until the last run ends`() {
        val events = mutableListOf<String>()
        var held = false
        val lock = AgentRunCpuWakeLock(
            acquire = { timeoutMs ->
                events += "acquire:$timeoutMs"
                held = true
            },
            release = {
                events += "release"
                held = false
            },
            isHeld = { held }
        )

        lock.onActiveChanged(true)
        lock.onActiveChanged(true)
        lock.onActiveChanged(false)
        lock.onActiveChanged(false)
        lock.releaseHeld()

        assertEquals(
            listOf(
                "acquire:$AGENT_RUN_WAKE_LOCK_TIMEOUT_MS",
                "acquire:$AGENT_RUN_WAKE_LOCK_TIMEOUT_MS",
                "release"
            ),
            events
        )
        assertFalse(held)
    }

    @Test
    fun `os expiry while still active reacquires a fresh lease`() {
        val events = mutableListOf<String>()
        var held = false
        val lock = AgentRunCpuWakeLock(
            acquire = { timeoutMs ->
                events += "acquire:$timeoutMs"
                held = true
            },
            release = {
                events += "release"
                held = false
            },
            isHeld = { held }
        )

        lock.onActiveChanged(true)
        held = false
        lock.onActiveChanged(true)
        lock.onActiveChanged(false)

        assertEquals(
            listOf(
                "acquire:$AGENT_RUN_WAKE_LOCK_TIMEOUT_MS",
                "acquire:$AGENT_RUN_WAKE_LOCK_TIMEOUT_MS",
                "release"
            ),
            events
        )
    }

    @Test
    fun `cpu wake lock timeout tracks AgentRunLimits and renews before expiry`() {
        assertEquals(AgentRunLimits().runTimeoutMillis, AGENT_RUN_WAKE_LOCK_TIMEOUT_MS)
        assertEquals(AGENT_RUN_WAKE_LOCK_TIMEOUT_MS / 2, AGENT_RUN_WAKE_LOCK_RENEWAL_DELAY_MS)
        assertTrue(AGENT_RUN_WAKE_LOCK_RENEWAL_DELAY_MS < AGENT_RUN_WAKE_LOCK_TIMEOUT_MS)
    }
}
