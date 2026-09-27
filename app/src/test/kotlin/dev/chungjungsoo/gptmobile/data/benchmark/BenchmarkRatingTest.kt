package dev.chungjungsoo.gptmobile.data.benchmark

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkRatingTest {
    private val profile = PlatformV2(uid = "profile", name = "Test", model = "model")
    private fun sample(category: String = "speed", outcome: BenchmarkOutcome = BenchmarkOutcome.PASSED, first: Long = 500, tokens: Int = 80) = BenchmarkSample(
        category, category, category, outcome, first + 1000, first, tokens, false, 320, 5
    )
    private fun run(samples: List<BenchmarkSample>) = BenchmarkRun("run", profile.uid, profile.name, profile.compatibleType.name, profile.model, benchmarkConfigKey(profile), false, BenchmarkMode.QUICK, 10, samples)

    @Test
    fun `rating normalizes missing dimensions instead of penalizing unavailable tools`() {
        val rating = benchmarkRating(listOf(run(listOf(sample(), sample("task"), sample("json"), sample("tools", BenchmarkOutcome.UNSUPPORTED)))))
        assertEquals(100, rating.score)
        assertEquals(75, rating.measuredWeight)
        assertEquals(3, rating.sampleCount)
        assertNull(rating.toolSuccessPercent)
        assertNull(rating.dimensions.first { it.label == "Consistency" }.score)
    }

    @Test
    fun `canceled and interrupted runs cannot improve a rating`() {
        val good = run(listOf(sample(), sample(), sample()))
        assertNull(benchmarkRating(listOf(good.copy(canceled = true), good.copy(finished = false))).score)
    }

    @Test
    fun `timeouts reduce reliability and failed tool tasks count even without calls`() {
        val rating = benchmarkRating(listOf(run(listOf(sample(), sample("task", BenchmarkOutcome.TIMED_OUT), sample("tools", BenchmarkOutcome.FAILED)))))
        assertEquals(2, rating.completed)
        assertEquals(0.0, rating.toolSuccessPercent!!, .001)
        assertEquals(100.0 * 2 / 3, rating.dimensions.first { it.label == "Completion reliability" }.score!!, .001)
        assertTrue(rating.score!! < 100)
    }

    @Test
    fun `speed consistency requires three samples and reflects variation`() {
        val stable = benchmarkRating(listOf(run(List(3) { sample() })))
        val varied = benchmarkRating(listOf(run(listOf(sample(tokens = 10), sample(tokens = 40), sample(tokens = 80)))))
        assertEquals(100.0, stable.dimensions.first { it.label == "Consistency" }.score!!, .001)
        assertTrue(varied.dimensions.first { it.label == "Consistency" }.score!! < 100)
        assertFalse(stable.estimatedSpeed)
        assertTrue(benchmarkRating(listOf(run(List(3) { sample().copy(estimatedTokens = true) }))).estimatedSpeed)
    }

    @Test
    fun `tiny samples remain unrated and latency percentiles are deterministic`() {
        assertNull(benchmarkRating(listOf(run(listOf(sample())))).score)
        val rating = benchmarkRating(listOf(run(listOf(sample(first = 100), sample(first = 300), sample(first = 200)))))
        assertEquals(200L, rating.medianFirstTextMs)
        assertEquals(300L, rating.p95FirstTextMs)
    }

    @Test
    fun `comparison only uses same current profile configuration suite and mode`() {
        val baseline = run(List(3) { sample() })
        val history = (0..6).map { baseline.copy(id = "$it", startedAt = it.toLong()) } + listOf(
            baseline.copy(id = "full", mode = BenchmarkMode.FULL),
            baseline.copy(id = "old-model", configKey = benchmarkConfigKey(profile.copy(model = "old"))),
            baseline.copy(id = "canceled", canceled = true),
            baseline.copy(id = "interrupted", finished = false),
            baseline.copy(id = "version", suiteVersion = 2),
            baseline.copy(id = "other", profileUid = "other")
        )
        assertEquals(listOf("6", "5", "4", "3", "2"), comparableRuns(history, profile, BenchmarkMode.QUICK).map { it.id })
    }

    @Test
    fun `config fingerprint ignores credentials but tracks endpoint options and local tuning`() {
        assertEquals(benchmarkConfigKey(profile), benchmarkConfigKey(profile.copy(token = "secret", name = "Renamed")))
        assertNotEquals(benchmarkConfigKey(profile), benchmarkConfigKey(profile.copy(apiUrl = "https://example.org")))
        assertNotEquals(benchmarkConfigKey(profile), benchmarkConfigKey(profile.copy(ollamaOptions = "{\"num_thread\":4}")))
        val local = profile.copy(compatibleType = ClientType.LITERT_LM)
        assertNotEquals(benchmarkConfigKey(local, "cpu4"), benchmarkConfigKey(local, "cpu8"))
        assertEquals(benchmarkConfigKey(profile, "cpu4"), benchmarkConfigKey(profile, "cpu8"))
    }
}
