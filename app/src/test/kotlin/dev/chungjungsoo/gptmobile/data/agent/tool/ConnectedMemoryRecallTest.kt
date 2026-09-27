package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.rag.FactVaultSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedMemoryRecallTest {
    private val config = FactVaultSettings(externalRecallEnabled = true, externalMemoryConnections = setOf("mem0", "supermemory", "graphiti"))
    private fun tool(provider: String, name: String, fields: String, required: String = "\"query\""): ResolvedAgentTool {
        val schema = Json.parseToJsonElement("""{"properties":{"query":{"type":"string"},$fields},"required":[$required]}""").jsonObject
        val tool = object : AgentTool {
            override val definition = AgentToolDefinition(name, "", schema)
            override suspend fun execute(callId: String, arguments: JsonObject) = AgentToolResult(callId, ToolResultContent.Text("Memory evidence"), false)
        }
        return ResolvedAgentTool(tool, provider, provider, name, "${provider}_$name")
    }
    private val mem0 get() = tool("mem0", "search_memories", "\"user_id\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}")
    private val supermemory get() = tool("supermemory", "search_memory", "\"containerTag\":{\"type\":\"string\"}")
    private val graphiti get() = tool("graphiti", "search_memory_facts", "\"group_ids\":{\"anyOf\":[{\"type\":\"array\"},{\"type\":\"null\"}]},\"max_facts\":{\"type\":\"integer\"}")

    @Test fun `provider arguments honor chosen namespace and discovered required fields`() {
        assertEquals("user", ConnectedMemoryRecall.arguments(mem0, "query", "user", 5)!!.getValue("user_id").jsonPrimitive.content)
        assertEquals("space", ConnectedMemoryRecall.arguments(supermemory, "query", "space", 5)!!.getValue("containerTag").jsonPrimitive.content)
        assertEquals("project", ConnectedMemoryRecall.arguments(graphiti, "query", "project", 5)!!.getValue("group_ids").jsonArray.single().jsonPrimitive.content)
        assertNull(ConnectedMemoryRecall.arguments(tool("mem0", "search_memories", "\"account\":{\"type\":\"string\"}", "\"query\",\"account\""), "query", "", 5))
        assertNull(ConnectedMemoryRecall.arguments(tool("mem0", "search_memories", "\"limit\":{\"type\":\"integer\"}"), "query", "must-not-disappear", 5))
    }

    @Test fun `automatic recall requires explicit selection and all memory privacy gates`() {
        val tools = listOf(mem0, supermemory, graphiti, tool("other", "search_memory", "\"limit\":{\"type\":\"integer\"}"))
        assertEquals(3, ConnectedMemoryRecall.select(tools, config).size)
        for (settings in listOf(config.copy(externalRecallEnabled = false), config.copy(externalMemoryConnections = emptySet()), config.copy(allowCloudRecall = false), config.copy(recallEnabled = false), config.copy(sameChatOnly = true), config.copy(reviewBeforeRecall = true))) assertTrue(ConnectedMemoryRecall.select(tools, settings).isEmpty())
    }

    @Test fun `one provider failure retains other evidence within budget and no writes are invoked`() = runTest {
        val calls = mutableListOf<String>()
        val result = ConnectedMemoryRecall.recall(listOf(mem0, supermemory, graphiti), "What are my preferences?", config.copy(recallTokens = 512), "turn", { true }) { tool, id, _ ->
            calls += tool.realToolName
            if (tool.connectionUid == "mem0") error("Unavailable")
            AgentToolResult(id, ToolResultContent.Text("Concise replies. ".repeat(1000)), false)
        }
        assertEquals(listOf("search_memories", "search_memory", "search_memory_facts"), calls)
        assertTrue(result.toByteArray().size <= 512 * 3)
        assertTrue(result.contains("Concise replies"))
        assertFalse(Json.parseToJsonElement(result).jsonObject.getValue("limitations").jsonArray.isEmpty())
        assertTrue(runCatching { ConnectedMemoryRecall.recall(listOf(mem0), "query", config, "turn", { true }) { _, _, _ -> throw CancellationException("Stop") } }.exceptionOrNull() is CancellationException)
    }
}
