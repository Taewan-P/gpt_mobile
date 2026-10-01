package dev.chungjungsoo.gptmobile.data.benchmark

import dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegationBenchmarkRatingTest {
    private fun run(worker: String, toolPassed: Boolean = true, speed: Double = 50.0, duration: Long = 1000, key: String = "worker-config") = BenchmarkRun(
        worker, "primary", "Primary", "OPENAI", "primary-model", "primary-config", false, BenchmarkMode.DELEGATION, 1,
        suiteVersion = 2,
        delegationSettings = ModelDelegationSettings(targetProfileUid = worker),
        samples = delegationBenchmarkSuite().map { test ->
            BenchmarkSample(
                test.id,
                test.label,
                test.category,
                if (test.id == "delegation-tools" && !toolPassed) BenchmarkOutcome.FAILED else BenchmarkOutcome.PASSED,
                durationMs = duration,
                delegation = DelegationBenchmarkMetrics(
                    worker, worker, "LLAMA", 1, 100, 20, 0, 0, 0, 0, 100, 20,
                    if (test.id == "delegation-tools" && toolPassed) 1 else 0,
                    if (test.id == "delegation-tools" && toolPassed) 1 else 0,
                    workerModel = "model", workerConfigKey = key, workerFirstTextMs = 100,
                    workerDecodeTokensPerSecond = speed
                )
            )
        }
    )

    @Test fun `transport errors reduce reliability without reporting tool incapability`() {
        val complete = run("helper")
        val interrupted = complete.copy(
            samples = complete.samples.map { sample ->
                if (sample.testId == "delegation-tools") sample.copy(outcome = BenchmarkOutcome.ERROR) else sample
            }
        )
        val rating = delegationBenchmarkRating(listOf(interrupted))
        assertEquals(null, rating.toolTaskSuccessPercent)
        assertTrue(rating.score!! <= 67)
    }

    @Test fun `correct slower helper ranks above fast helper without tool usage`() {
        val rows = delegateRankings(listOf(run("fast", false, 1000.0), run("reliable", true, 40.0, 4000)), "primary-config")
        assertEquals("reliable", rows.first().run.id)
        assertTrue(rows.last().rating.score!! <= 67)
        assertEquals(0.0, rows.last().rating.toolTaskSuccessPercent!!, .01)
    }

    @Test fun `incomplete suite cannot produce a rating`() {
        val full = run("helper")
        assertNull(delegationBenchmarkRating(listOf(full.copy(samples = full.samples.take(1)))).score)
    }

    @Test fun `failed cases count against success and canceled runs are excluded`() {
        val failed = run("helper").copy(samples = run("helper").samples.map { it.copy(outcome = BenchmarkOutcome.TIMED_OUT) })
        val result = delegationBenchmarkRating(listOf(failed, run("canceled").copy(canceled = true)))
        assertEquals(3, result.attempts)
        assertEquals(0, result.passed)
        assertEquals(0, result.score)
        assertNull(result.medianDecodeSpeed)
    }

    @Test fun `rankings separate worker settings primary and legacy suites`() {
        val base = run("helper")
        val changed = base.copy(id = "new-config", samples = base.samples.map { it.copy(delegation = it.delegation!!.copy(workerConfigKey = "new")) })
        assertEquals(2, delegateRankings(listOf(base, changed, base.copy(suiteVersion = 1), base.copy(configKey = "other")), "primary-config").size)
        assertTrue(delegateRankings(listOf(base), "primary-config", ModelDelegationSettings(maxOutputTokens = 2048)).isEmpty())
    }

    @Test fun `delegation rating exposes tool call usability and diagnostic severity counts`() {
        val base = run("helper")
        val events = listOf(
            DelegationBenchmarkEvent(10, "FIRST_TEXT", "INFO", "fast"),
            DelegationBenchmarkEvent(20, "INSIGHT_LOW_THROUGHPUT", "WARN", "slow"),
            DelegationBenchmarkEvent(30, "WORKER_FAILURE", "ERROR", "failed")
        )
        val enriched = base.copy(
            samples = base.samples.map { sample ->
                sample.copy(delegation = sample.delegation!!.copy(diagnosticEvents = events))
            }
        )
        val result = delegationBenchmarkRating(listOf(enriched))
        assertEquals(100.0, result.toolTaskSuccessPercent!!, .01)
        assertEquals(100.0, result.toolCallSuccessPercent!!, .01)
        assertEquals(9, result.diagnosticEvents)
        assertEquals(3, result.warningEvents)
        assertEquals(3, result.errorEvents)
        assertTrue(result.dimensions.any { it.label == "Token throughput" })
        assertTrue(result.dimensions.any { it.label == "First-response latency" })
        assertTrue(result.dimensions.any { it.label == "End-to-end latency" })
    }

    @Test fun `single chunk missing speed is not fabricated and tokens remain visible`() {
        val base = run("helper")
        val result = delegationBenchmarkRating(listOf(base.copy(samples = base.samples.map { it.copy(delegation = it.delegation!!.copy(workerDecodeTokensPerSecond = null, workerEstimated = true)) })))
        assertNull(result.medianDecodeSpeed)
        assertEquals(300L, result.workerInputTokens)
        assertEquals(60L, result.workerOutputTokens)
        assertTrue(result.estimated)
    }
}
