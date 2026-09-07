package dev.chungjungsoo.gptmobile.presentation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GPTMobileAppTest {
    @Test
    fun `startup recovery gate waits for current maintenance job`() = runTest {
        val events = mutableListOf<String>()

        StartupRecoveryGate.start(this) { events += "recover" }
        StartupRecoveryGate.await()
        events += "observe"

        assertEquals(listOf("recover", "observe"), events)
    }

    @Test
    fun `startup interrupts persisted work before migrating credentials`() = runTest {
        val events = mutableListOf<String>()

        runStartupMaintenance(
            interruptPersistedWork = { events += "interrupt" },
            migrateSecrets = {
                events += "migrate"
                emptyList()
            }
        )

        assertEquals(listOf("interrupt", "migrate"), events)
    }
}
