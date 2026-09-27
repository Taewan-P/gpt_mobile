package dev.chungjungsoo.gptmobile.data.localruntime

import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags

/** LiteRT flags are process globals. Restore them even when native initialization fails. */
@OptIn(ExperimentalApi::class)
internal object LiteRtExperimentalSettings {
    private val lock = Any()

    fun <T> engine(spec: LocalEngineSpec, block: () -> T): T = synchronized(lock) {
        val benchmark = ExperimentalFlags.enableBenchmark
        val speculative = ExperimentalFlags.enableSpeculativeDecoding
        try {
            ExperimentalFlags.enableBenchmark = spec.nativeMetricsEnabled
            ExperimentalFlags.enableSpeculativeDecoding = spec.speculativeDecoding
            block()
        } finally {
            ExperimentalFlags.enableBenchmark = benchmark
            ExperimentalFlags.enableSpeculativeDecoding = speculative
        }
    }

    fun <T> conversation(constrained: Boolean, block: () -> T): T = synchronized(lock) {
        val previous = ExperimentalFlags.enableConversationConstrainedDecoding
        try {
            ExperimentalFlags.enableConversationConstrainedDecoding = constrained
            block()
        } finally {
            ExperimentalFlags.enableConversationConstrainedDecoding = previous
        }
    }
}
