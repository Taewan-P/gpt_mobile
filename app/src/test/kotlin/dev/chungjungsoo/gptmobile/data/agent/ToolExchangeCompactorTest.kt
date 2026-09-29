package dev.chungjungsoo.gptmobile.data.agent

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolExchangeCompactorTest {
    @Test
    fun compact_bounds_old_tool_payloads_and_keeps_pairing() {
        val exchanges = (0 until 6).map { index ->
            val call = ProviderEvent.ToolCall(
                callId = "call-$index",
                name = "github",
                arguments = buildJsonObject { put("query", "q$index") }
            )
            AgentToolExchange(
                calls = listOf(call),
                results = listOf(
                    AgentToolResult(
                        callId = call.callId,
                        content = ToolResultContent.Text("result-$index\n" + "x".repeat(12_000)),
                        isError = false
                    )
                )
            )
        }

        val compacted = ToolExchangeCompactor.compact(
            exchanges = exchanges,
            maxReplayTokens = 4_000,
            maxResultTokens = 400
        )

        assertEquals(exchanges.size, compacted.size)
        assertTrue(ToolExchangeCompactor.estimateTokens(compacted) < ToolExchangeCompactor.estimateTokens(exchanges))
        compacted.zip(exchanges).forEach { (after, before) ->
            assertEquals(before.calls.map { it.callId }, after.calls.map { it.callId })
            assertEquals(before.results.map { it.callId }, after.results.map { it.callId })
        }
    }

    @Test
    fun compact_preserves_unmodified_json_result_type() {
        val call = ProviderEvent.ToolCall(
            "location",
            "device_location",
            buildJsonObject { }
        )
        val json = buildJsonObject { put("accuracy_meters", JsonPrimitive(8.0)) }
        val exchange = AgentToolExchange(
            calls = listOf(call),
            results = listOf(AgentToolResult(call.callId, ToolResultContent.Json(json), false))
        )

        val compacted = ToolExchangeCompactor.compact(
            exchanges = listOf(exchange),
            maxReplayTokens = 4_000,
            maxResultTokens = 800
        )

        val content = compacted.single().results.single().content
        assertTrue(content is ToolResultContent.Json)
        assertEquals(json, (content as ToolResultContent.Json).value)
    }

    @Test
    fun compact_deduplicates_older_identical_results() {
        val payload = "same payload " + "z".repeat(5_000)
        fun exchange(id: String) = AgentToolExchange(
            calls = listOf(
                ProviderEvent.ToolCall(id, "read_url", buildJsonObject { put("url", "https://example.com") })
            ),
            results = listOf(AgentToolResult(id, ToolResultContent.Text(payload), false))
        )

        val compacted = ToolExchangeCompactor.compact(
            exchanges = listOf(exchange("old"), exchange("new")),
            maxReplayTokens = 3_000,
            maxResultTokens = 800
        )

        val oldText = (compacted.first().results.first().content as ToolResultContent.Text).text
        val newText = (compacted.last().results.first().content as ToolResultContent.Text).text
        assertTrue(oldText.contains("duplicate tool result omitted", ignoreCase = true))
        assertTrue(newText.contains("same payload"))
    }
}
