package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.truncateUtf8
import dev.chungjungsoo.gptmobile.data.rag.FactVaultSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Automatic recall only reads explicitly selected MCP connections; it never uploads saved facts. */
internal object ConnectedMemoryRecall {
    val supportedTools = setOf("search_memories", "search_memory", "search_memory_facts")

    fun select(tools: List<ResolvedAgentTool>, settings: FactVaultSettings): List<ResolvedAgentTool> {
        if (!settings.externalRecallEnabled || !settings.recallEnabled || !settings.allowCloudRecall || settings.sameChatOnly || settings.reviewBeforeRecall) return emptyList()
        return tools.filter { it.connectionUid in settings.externalMemoryConnections && it.realToolName in supportedTools }
            .distinctBy { it.connectionUid }.take(3)
    }

    fun arguments(tool: ResolvedAgentTool, query: String, scope: String, limit: Int): JsonObject? {
        if (tool.connectionUid == null || tool.realToolName !in supportedTools) return null
        val schema = tool.tool.definition.inputSchema
        val properties = schema["properties"] as? JsonObject ?: return null
        fun accepts(key: String, type: String): Boolean {
            val value = properties[key] as? JsonObject ?: return false
            return value["type"] == JsonPrimitive(type) || (value["anyOf"] as? JsonArray).orEmpty().any { (it as? JsonObject)?.get("type") == JsonPrimitive(type) }
        }
        if (!accepts("query", "string")) return null
        val result = mutableMapOf<String, kotlinx.serialization.json.JsonElement>("query" to JsonPrimitive(query.take(500)))
        for (key in listOf("limit", "max_facts", "top_k")) {
            if (accepts(key, "integer")) {
                val field = properties[key] as JsonObject
                val minimum = (field["minimum"] as? JsonPrimitive)?.intOrNull ?: 1
                val maximum = (field["maximum"] as? JsonPrimitive)?.intOrNull ?: 20
                if (minimum > maximum || minimum > 20) return null
                result[key] = JsonPrimitive(limit.coerceIn(minimum, maximum.coerceAtMost(20)))
            }
        }
        if (scope.isNotBlank()) {
            val key = listOf("user_id", "containerTag", "group_ids", "group_id").firstOrNull { it in properties }
            if (key != null) {
                result[key] = when {
                    accepts(key, "string") -> JsonPrimitive(scope.take(200))
                    accepts(key, "array") -> JsonArray(listOf(JsonPrimitive(scope.take(200))))
                    else -> return null
                }
            } else if (tool.realToolName == "search_memories" && accepts("filters", "object")) {
                result["filters"] = buildJsonObject { put("user_id", scope.take(200)) }
            } else {
                // Never silently discard the user's chosen memory namespace.
                return null
            }
        }
        for (required in (schema["required"] as? JsonArray).orEmpty()) {
            val key = (required as? JsonPrimitive)?.contentOrNull ?: return null
            if (key !in result) result[key] = (properties[key] as? JsonObject)?.get("default") ?: return null
        }
        return JsonObject(result)
    }

    suspend fun recall(
        tools: List<ResolvedAgentTool>,
        query: String,
        settings: FactVaultSettings,
        callId: String,
        stillEnabled: suspend () -> Boolean,
        execute: suspend (ResolvedAgentTool, String, JsonObject) -> AgentToolResult = { tool, id, arguments -> tool.tool.execute(id, arguments) }
    ): String {
        val selected = select(tools, settings)
        if (selected.isEmpty() || query.isBlank() || Regex("(?i)\\b(?:password|api[_ -]?key|access[_ -]?token|bearer)\\b|\\bsk-[A-Za-z0-9]{16,}").containsMatchIn(query)) return ""
        val rows = mutableListOf<JsonObject>()
        val issues = mutableListOf<String>()
        val allowance = settings.normalized().recallTokens * 3
        val perSource = ((allowance - 300) / selected.size - 200).coerceAtLeast(0)
        val completed = withTimeoutOrNull(15_000L) {
            for ((index, tool) in selected.withIndex()) {
                if (!stillEnabled()) break
                val args = arguments(tool, query, settings.externalMemoryScopes[tool.connectionUid].orEmpty(), settings.maxRecall)
                if (args == null) {
                    issues += "${tool.connectionName.orEmpty().take(40)} needs compatible search inputs or a memory scope."
                    continue
                }
                val result = try {
                    execute(tool, "$callId:$index", args)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (result == null || result.isError) {
                    issues += "${tool.connectionName.orEmpty().take(40)} memory recall was unavailable."
                } else {
                    val raw = result.content.researchText()
                    rows += buildJsonObject {
                        put("provider", tool.connectionName.orEmpty().take(60))
                        put("evidence", truncateUtf8(raw, perSource))
                        put("excerpted", raw.toByteArray().size > perSource)
                    }
                }
                if (result?.outputBudgetExhausted == true) {
                    issues += "Shared tool budget reached."
                    break
                }
            }
            true
        }
        if (completed == null) issues += "Connected memory recall timed out; completed results are retained."
        // JSON escaping can expand data. Remove complete rows rather than emitting invalid JSON.
        fun render() = buildJsonObject {
            put("kind", "untrusted_connected_memory")
            put("results", JsonArray(rows))
            put("limitations", JsonArray(issues.take(3).map { JsonPrimitive(it.take(100)) }))
        }.toString()
        while (render().toByteArray().size > allowance && rows.isNotEmpty()) {
            rows.removeAt(rows.lastIndex)
            if ("Some results did not fit the memory budget." !in issues) issues += "Some results did not fit the memory budget."
        }
        while (render().toByteArray().size > allowance && issues.isNotEmpty()) issues.removeAt(issues.lastIndex)
        return render()
    }
}
