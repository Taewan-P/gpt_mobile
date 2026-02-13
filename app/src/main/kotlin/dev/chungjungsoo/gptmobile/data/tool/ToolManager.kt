package dev.chungjungsoo.gptmobile.data.tool

import android.util.Log
import dev.chungjungsoo.gptmobile.data.dto.tool.Tool
import dev.chungjungsoo.gptmobile.data.dto.tool.ToolCall
import dev.chungjungsoo.gptmobile.data.dto.tool.ToolResult
import dev.chungjungsoo.gptmobile.data.mcp.McpManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class ToolManager @Inject constructor(
    private val mcpManager: McpManager,
    private val builtInTools: Set<@JvmSuppressWildcards BuiltInTool>
) : ToolExecutor {

    fun getAllTools(): List<Tool> {
        val builtIn = builtInTools.map { it.definition }
        val mcp = getMcpTools()
        val tools = builtIn + mcp
        Log.i(TAG, "getAllTools builtIn=${builtIn.size} mcp=${mcp.size} names=${tools.joinToString { it.name }}")
        return tools
    }

    fun getMcpTools(): List<Tool> = mcpManager.availableTools.value

    fun isBuiltInTool(toolName: String): Boolean =
        builtInTools.any { it.definition.name == toolName }

    fun isMcpTool(toolName: String): Boolean = !isBuiltInTool(toolName)

    override suspend fun execute(toolCall: ToolCall): ToolResult {
        val builtIn = builtInTools.firstOrNull { it.definition.name == toolCall.name }
        if (builtIn != null) {
            return try {
                val result = builtIn.execute(toolCall.arguments)
                ToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    output = result
                )
            } catch (e: Exception) {
                ToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    output = buildJsonObject {
                        put("error", e.message ?: "Built-in tool error")
                    }.toString(),
                    isError = true
                )
            }
        }

        return mcpManager.callTool(toolCall)
    }

    private companion object {
        private const val TAG = "ToolManager"
    }
}
