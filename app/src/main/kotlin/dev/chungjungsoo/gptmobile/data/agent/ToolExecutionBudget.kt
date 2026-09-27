package dev.chungjungsoo.gptmobile.data.agent

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

/** One budget per user turn, shared by every bound tool, including the native engine bridge. */
class ToolExecutionBudget(
    private val limits: AgentRunLimits,
    private val failureMessage: (Exception) -> String = { "Tool execution failed. Check the connection diagnostics before repeating an action." }
) {
    private val calls = AtomicInteger()
    private val completed = AtomicInteger()
    private val remainingBytes = AtomicInteger(limits.maxToolOutputBytes)
    private val permits = Semaphore(limits.maxConcurrentTools.coerceAtLeast(1))

    fun bind(
        tool: AgentTool,
        onFinished: suspend (String, Boolean) -> Unit = { _, _ -> },
        authorize: suspend (String, JsonObject) -> Boolean = { _, _ -> true }
    ): AgentTool = object : AgentTool {
        override val definition = tool.definition
        override val managesExecutionBudget = true

        override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
            fun failure(message: String) = AgentToolResult(callId, ToolResultContent.Text(message), true)
            if (limits.maxToolCalls != Int.MAX_VALUE && calls.getAndIncrement() >= limits.maxToolCalls.coerceAtLeast(0)) {
                return bounded(failure(AgentRunner.FINAL_RESPONSE_INSTRUCTION)).copy(outputBudgetExhausted = true)
            }
            if (remainingBytes.get() <= 0) return bounded(failure("Tool result budget exhausted. Answer using the results already available."))
            if (!authorize(callId, arguments)) return bounded(failure("Tool permission was denied or this action was already dispatched."))
            var success = false
            try {
                val result = if (tool.managesExecutionBudget) {
                    // Orchestrators own their deadline; their children use this same budget.
                    // Holding a permit here would deadlock nested calls at concurrency = 1.
                    tool.execute(callId, arguments)
                } else {
                    permits.withPermit {
                        withTimeoutOrNull(limits.toolTimeoutMillis) { tool.execute(callId, arguments) }
                            ?: failure("Tool timed out. Its outcome may be unknown; check before repeating a write.")
                    }
                }
                success = !result.isError
                return bounded(result)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                return bounded(failure(failureMessage(error)))
            } finally {
                withContext(NonCancellable) { onFinished(callId, success) }
            }
        }
    }

    private fun bounded(result: AgentToolResult): AgentToolResult {
        val text = when (val value = result.content) {
            is ToolResultContent.Text -> value.text
            is ToolResultContent.Json -> value.value.toString()
            is ToolResultContent.ResourceLinks -> value.links.joinToString("\n") { it.uri }
        }
        val checkpoint = if (completed.incrementAndGet() % ToolProgressTracker.INTERVAL == 0) {
            "\n\n" + ToolProgressTracker.SUMMARY_INSTRUCTION
        } else {
            ""
        }
        val size = (text + checkpoint).toByteArray(Charsets.UTF_8).size
        val available = remainingBytes.getAndUpdate { (it.toLong() - size).coerceAtLeast(0).toInt() }.coerceAtLeast(0)
        // Control messages are bounded separately: providers reject empty error results,
        // and a zero payload allowance must still let the model finish the turn.
        if (available == 0) {
            return result.copy(
                content = ToolResultContent.Text(OUTPUT_BUDGET_EXHAUSTED),
                traceContent = ToolResultContent.Text(OUTPUT_BUDGET_EXHAUSTED),
                isError = true,
                outputBudgetExhausted = true
            )
        }
        val checkpointBytes = checkpoint.toByteArray(Charsets.UTF_8).size
        val bounded = if (checkpointBytes <= available) {
            truncateUtf8(text, available - checkpointBytes) + checkpoint
        } else {
            truncateUtf8(text, available)
        }
        val safeText = bounded.ifBlank {
            if (size > available) {
                OUTPUT_BUDGET_EXHAUSTED
            } else if (result.isError) {
                "Tool failed without error details."
            } else {
                "Tool returned no content."
            }
        }
        val changed = size > available || checkpoint.isNotEmpty() || bounded.isBlank()
        val trace = if (size > available) {
            // Respect tools that deliberately supply a redacted trace; otherwise show
            // the useful search/page excerpt as well as the truncation notice.
            val excerpt = result.traceContent?.let { value ->
                when (value) {
                    is ToolResultContent.Text -> value.text
                    is ToolResultContent.Json -> value.value.toString()
                    is ToolResultContent.ResourceLinks -> value.links.joinToString("\n") { it.uri }
                }
            } ?: safeText
            ToolResultContent.Text(truncateUtf8(excerpt, available) + "\n\n[Result truncated to the run's output budget.]")
        } else {
            result.traceContent
        }
        return result.copy(
            content = if (changed) ToolResultContent.Text(safeText) else result.content,
            traceContent = trace,
            outputBudgetExhausted = result.outputBudgetExhausted ||
                size >= available ||
                (limits.maxToolCalls != Int.MAX_VALUE && calls.get() >= limits.maxToolCalls - 1)
        )
    }
}

internal const val OUTPUT_BUDGET_EXHAUSTED = "Tool result budget exhausted. Answer using the results already available; do not call more tools."

internal fun truncateUtf8(text: String, maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    var end = 0
    var used = 0
    while (end < text.length) {
        val point = text.codePointAt(end)
        val bytes = when {
            point < 0x80 -> 1
            point < 0x800 -> 2
            point < 0x10000 -> 3
            else -> 4
        }
        if (used + bytes > maxBytes) break
        used += bytes
        end += Character.charCount(point)
    }
    return text.substring(0, end)
}
