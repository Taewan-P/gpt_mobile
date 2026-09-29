package dev.chungjungsoo.gptmobile.data.agent

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.json.Json

/**
 * Builds a bounded replay view of tool exchanges for subsequent model rounds.
 *
 * Raw results remain in-memory for diagnostics/UI. Only the provider replay is compacted,
 * preventing every tool round from re-sending the complete accumulated payload.
 */
internal object ToolExchangeCompactor {
    private const val MIN_RESULT_TOKENS = 64
    private const val CHARS_PER_TOKEN = 3

    fun compact(
        exchanges: List<AgentToolExchange>,
        maxReplayTokens: Int,
        maxResultTokens: Int
    ): List<AgentToolExchange> {
        if (exchanges.isEmpty() || maxReplayTokens == Int.MAX_VALUE) return exchanges

        val hardBudget = maxReplayTokens.coerceAtLeast(512)
        val perResultBudget = maxResultTokens.coerceAtLeast(MIN_RESULT_TOKENS)
        val fingerprints = exchanges.flatMap { it.results }.map { fingerprint(render(it.content)) }
        val lastOccurrence = fingerprints.withIndex().associate { (index, value) -> value to index }
        var resultIndex = 0
        var remaining = hardBudget

        // Reserve call metadata first so tool-call/result pairing is never broken.
        exchanges.forEach { exchange ->
            remaining -= exchange.calls.sumOf { ContextTokenEstimate.estimate(it.name + it.arguments.toString()) + 24 }
        }
        remaining = remaining.coerceAtLeast(0)

        val resultBudgets = IntArray(fingerprints.size) { MIN_RESULT_TOKENS }
        for (index in fingerprints.indices.reversed()) {
            val duplicate = lastOccurrence[fingerprints[index]] != index
            if (duplicate) {
                resultBudgets[index] = MIN_RESULT_TOKENS
                continue
            }
            val allowance = minOf(perResultBudget, remaining.coerceAtLeast(MIN_RESULT_TOKENS))
            resultBudgets[index] = allowance
            remaining = (remaining - allowance).coerceAtLeast(0)
        }

        return exchanges.map { exchange ->
            val compacted = exchange.results.map { result ->
                val index = resultIndex++
                val raw = render(result.content)
                val duplicate = lastOccurrence[fingerprints[index]] != index
                val text = if (duplicate) {
                    "[Earlier duplicate tool result omitted; the newest identical result is retained.]"
                } else {
                    compactText(raw, resultBudgets[index] * CHARS_PER_TOKEN)
                }
                result.copy(content = ToolResultContent.Text(text))
            }
            exchange.copy(results = compacted)
        }
    }

    fun estimateTokens(exchanges: List<AgentToolExchange>): Int =
        exchanges.sumOf { exchange ->
            exchange.calls.sumOf { ContextTokenEstimate.estimate(it.name + it.arguments.toString()) + 24 } +
                exchange.results.sumOf { ContextTokenEstimate.estimate(render(it.content)) + 24 }
        }

    private fun compactText(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        val marker = "\n\n[Earlier tool result compacted after consumption.]\n\n"
        val available = (maxChars - marker.length).coerceAtLeast(64)
        val head = available * 2 / 3
        val tail = available - head
        return value.take(head) + marker + value.takeLast(tail)
    }

    private fun render(content: ToolResultContent): String = when (content) {
        is ToolResultContent.Text -> content.text
        is ToolResultContent.Json -> Json.encodeToString(content.value)
        is ToolResultContent.ResourceLinks -> Json.encodeToString(content.links.map { it.uri })
    }

    private fun fingerprint(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }
}

private object ContextTokenEstimate {
    fun estimate(text: String): Int = (text.toByteArray(StandardCharsets.UTF_8).size + 2) / 3
}
