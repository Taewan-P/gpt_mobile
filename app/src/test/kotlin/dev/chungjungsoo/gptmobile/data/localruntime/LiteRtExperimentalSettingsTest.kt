package dev.chungjungsoo.gptmobile.data.localruntime

import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalApi::class)
class LiteRtExperimentalSettingsTest {
    @Test
    fun `failed engine initialization restores process flags`() {
        val benchmark = ExperimentalFlags.enableBenchmark
        val speculative = ExperimentalFlags.enableSpeculativeDecoding
        val spec = LocalEngineSpec("test.litertlm", "cpu", 1024, speculativeDecoding = true, nativeMetricsEnabled = true)
        val result = runCatching {
            LiteRtExperimentalSettings.engine(spec) {
                assertTrue(ExperimentalFlags.enableBenchmark)
                assertEquals(true, ExperimentalFlags.enableSpeculativeDecoding)
                error("Unsupported model")
            }
        }
        assertTrue(result.isFailure)
        assertEquals(benchmark, ExperimentalFlags.enableBenchmark)
        assertEquals(speculative, ExperimentalFlags.enableSpeculativeDecoding)
    }

    @Test
    fun `nested conversation setup restores its own flags without changing engine options`() {
        val original = ExperimentalFlags.enableConversationConstrainedDecoding
        LiteRtExperimentalSettings.conversation(true) {
            assertTrue(ExperimentalFlags.enableConversationConstrainedDecoding)
            runCatching { LiteRtExperimentalSettings.conversation(false) { error("Failed") } }
            assertTrue(ExperimentalFlags.enableConversationConstrainedDecoding)
        }
        assertEquals(original, ExperimentalFlags.enableConversationConstrainedDecoding)
    }
}
