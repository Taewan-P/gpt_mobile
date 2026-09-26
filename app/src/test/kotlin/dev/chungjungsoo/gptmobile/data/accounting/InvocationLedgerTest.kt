package dev.chungjungsoo.gptmobile.data.accounting

import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvocationLedgerTest {
    @Test fun `outcome attribution and reservations survive completion failure and cancellation`() = runTest {
        for (outcome in listOf("COMPLETED", "FAILED", "CANCELED", "INTERRUPTED")) {
            val saved = mutableListOf<ModelInvocation>()
            var reserved: ModelInvocation? = null
            val dao = mockk<InvocationDao>()
            every { dao.recent() } returns flowOf(emptyList())
            coEvery { dao.reserve(any(), any()) } answers { reserved = firstArg() }
            coEvery { dao.save(any()) } answers {
                saved.add(firstArg())
                Unit
            }
            val database = mockk<ChatDatabaseV2>()
            every { database.invocationDao() } returns dao
            val ledger = InvocationLedger(database)
            val session = object : AgentProviderSession {
                override val handlesToolsInternally = false
                override fun streamRound(tools: List<AgentToolDefinition>, exchanges: List<AgentToolExchange>): Flow<ProviderEvent> = flow {
                    emit(ProviderEvent.TextDelta("Hello"))
                    assertEquals(100, reserved!!.outputTokens)
                    assertEquals("profile", ledger.active.value.values.single().profileUid)
                    when (outcome) {
                        "COMPLETED" -> emit(ProviderEvent.Completed)
                        "FAILED" -> {
                            emit(ProviderEvent.Failed("Upstream"))
                            emit(ProviderEvent.Completed)
                        }
                        "CANCELED" -> throw CancellationException("User stopped")
                    }
                }
            }
            val result = runCatching { ledger.wrap(session, "parent", "turn", "provider", "model", "delegate", 10, 100, Int.MAX_VALUE, "profile").streamRound(emptyList(), emptyList()).toList() }
            if (outcome == "CANCELED") assertTrue(result.exceptionOrNull() is CancellationException) else assertTrue(result.isSuccess)
            assertEquals(outcome, saved.single().status)
            assertEquals("profile", saved.single().profileUid)
            assertTrue(saved.single().outputTokens < 100)
            assertTrue(ledger.active.value.isEmpty())
        }
    }
}
