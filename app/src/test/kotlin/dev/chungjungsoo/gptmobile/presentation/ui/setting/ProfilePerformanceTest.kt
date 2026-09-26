package dev.chungjungsoo.gptmobile.presentation.ui.setting

import dev.chungjungsoo.gptmobile.data.accounting.ModelInvocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilePerformanceTest {
    private fun sample(id: String, uid: String?, duration: Long = 1000, status: String = "COMPLETED", estimated: Boolean = false, tokens: Int = 100) =
        ModelInvocation(id, "run", "turn", "provider", "same-model", "primary", 20, tokens, estimated, status, durationMs = duration, profileUid = uid)

    @Test fun `same model in different profiles and unassigned history remain separate`() {
        val data = listOf(sample("a", "one"), sample("b", "two"), sample("c", null), sample("a", "one"))
        val rows = profilePerformance(data, mapOf("one" to "Research", "two" to "Coding"))
        assertEquals(3, rows.size)
        assertEquals(setOf("Research", "Coding", "Unassigned historical requests"), rows.map { it.name }.toSet())
        assertEquals(3, rows.sumOf { it.metrics.requests })
    }

    @Test fun `success excludes cancellation unknown legacy outcomes and active requests`() {
        val row = profilePerformance(listOf(sample("1", "p"), sample("2", "p", status = "FAILED"), sample("3", "p", status = "INTERRUPTED"), sample("4", "p", status = "CANCELED"), sample("5", "p", status = "RUNNING"), sample("6", "p", status = "STOPPED")), emptyMap()).single()
        assertEquals(5, row.metrics.requests)
        assertEquals(100.0 / 3, row.successPercent!!, .0001)
        assertEquals(1, row.canceled)
        assertEquals(1, row.unknown)
    }

    @Test fun `ranking uses measured values and puts missing metrics last`() {
        val rows = profilePerformance(listOf(sample("1", "fast", duration = 1000, tokens = 10), sample("2", "throughput", duration = 2000, tokens = 200), sample("3", "estimate", duration = 3000, estimated = true), sample("4", "failed", status = "FAILED")), emptyMap())
        assertEquals("fast", rankPerformance(rows, PerformanceOrder.LATENCY).first().profileUid)
        assertEquals("throughput", rankPerformance(rows, PerformanceOrder.THROUGHPUT).first().profileUid)
        assertEquals("failed", rankPerformance(rows, PerformanceOrder.SUCCESS).last().profileUid)
        assertNull(sample("x", "p", estimated = true).reportedThroughput())
        assertNull(sample("x", "p", duration = 0).reportedThroughput())
    }
}
