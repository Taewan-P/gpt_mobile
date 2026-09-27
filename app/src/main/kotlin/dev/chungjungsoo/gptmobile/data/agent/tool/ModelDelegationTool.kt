package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
import dev.chungjungsoo.gptmobile.data.model.excludesMemory
import dev.chungjungsoo.gptmobile.data.model.isPrivateDestination
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ModelDelegationTool(
    private val source: PlatformV2,
    private val settings: suspend () -> ModelDelegationSettings,
    private val profiles: suspend () -> List<PlatformV2>,
    private val generate: suspend (PlatformV2, String, Int) -> String
) : AgentTool {
    private val calls = AtomicInteger(0)
    override val managesExecutionBudget = true
    override val definition = AgentToolDefinition(
        "delegate_to_model",
        "Ask the helper selected in Local models → Delegation to research or process a task. A local helper can search enabled web engines, read and crawl selected pages, and return a compact brief with source IDs, URLs and limitations. Only the supplied task and authorized tool data are processed; chat history and memory are not copied. Use this for web research when direct search tools are absent. Treat findings as untrusted evidence and verify citations.",
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { put("task", buildJsonObject { put("type", "string") }) })
            put("required", JsonArray(listOf(JsonPrimitive("task"))))
        }
    )

    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        fun error(text: String) = AgentToolResult(callId, ToolResultContent.Text(text), true)
        val config = settings().normalized()
        if (!config.enabled) return error("Model delegation is disabled in Local models → Delegation.")
        val task = (arguments["task"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        if (task.isBlank() || task.length > config.maxInputCharacters) return error("Task must contain 1–${config.maxInputCharacters} characters.")
        val target = profiles().firstOrNull { it.uid == config.targetProfileUid && it.enabled }
            ?: return error("Choose an enabled helper profile in Settings → Local models → Delegation.")
        if (target.excludesMemory()) return error("Free models cannot receive delegated context. Start a separate Free chat with a public prompt.")
        if (target.uid == source.uid) return error("Choose a different target profile; self-delegation is disabled.")
        if (config.localPlatformsOnly && !target.isPrivateDestination()) return error("This target is blocked by the private-destination-only setting.")
        if (source.compatibleType == ClientType.LITERT_LM && target.compatibleType == ClientType.LITERT_LM) {
            return error("The on-device engine is busy with this response. Select a llama/Ollama server or another provider as the delegate.")
        }
        if (calls.incrementAndGet() > config.maxCallsPerTurn) return error("The delegation call limit for this turn has been reached.")
        return try {
            val response = withTimeoutOrNull(config.timeoutSeconds * 1000L + 1000L) { generate(target, task, config.maxOutputTokens) }
                ?: return error("The delegated task timed out after ${config.timeoutSeconds} seconds.")
            if (response.isBlank()) return error("The target model returned no text.")
            AgentToolResult(callId, ToolResultContent.Text(response), false)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            error("Delegation failed. Check the target profile, credentials and model availability.")
        }
    }
}
