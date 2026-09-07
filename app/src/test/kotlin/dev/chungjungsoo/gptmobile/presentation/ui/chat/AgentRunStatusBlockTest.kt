package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentRunStatusBlockTest {
    @Test
    fun `duration clamps clock skew and requires terminal timing`() {
        val run = AgentRun(
            runId = "run-1",
            chatId = 7,
            userMessageId = 1,
            assistantMessageId = 2,
            profileUid = "profile-1",
            providerSnapshot = "OPENAI",
            modelSnapshot = "model",
            startedAt = 20,
            completedAt = 10
        )

        assertEquals(0L, agentRunDurationSeconds(run))
        assertEquals(null, agentRunDurationSeconds(run.copy(completedAt = null)))
    }
}
