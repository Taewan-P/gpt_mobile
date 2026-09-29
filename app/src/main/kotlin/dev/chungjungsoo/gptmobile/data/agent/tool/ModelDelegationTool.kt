package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.diagnostics.AppLogRecorder
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
        AppLogRecorder.record("Delegation", "Tool requested · call=$callId · source=${source.uid} · enabled=${config.enabled} · localOnly=${config.localPlatformsOnly} · remoteWorkers=${config.allowRemoteWorkers}")
        if (!config.enabled) return error("Model delegation is disabled in Local models → Delegation.")
        val task = (arguments["task"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        if (task.isBlank() || task.length > config.maxInputCharacters) return error("Task must contain 1–${config.maxInputCharacters} characters.")
        val target = profiles().firstOrNull { it.uid == config.targetProfileUid && it.enabled }
            ?: return error("Choose an enabled helper profile in Settings → Local models → Delegation.").also { AppLogRecorder.record("Delegation", "Rejected · no enabled target=${config.targetProfileUid}", "W") }
        AppLogRecorder.record("Delegation", "Target selected · target=${target.uid} · type=${target.compatibleType} · model=${target.model.take(96)} · taskChars=${task.length}")
        if (target.excludesMemory()) return error("Free models cannot receive delegated context. Start a separate Free chat with a public prompt.").also { AppLogRecorder.record("Delegation", "Rejected free target · target=${target.uid}", "W") }
        if (target.uid == source.uid) return error("Choose a different target profile; self-delegation is disabled.").also { AppLogRecorder.record("Delegation", "Rejected self-delegation · target=${target.uid}", "W") }
        // `localPlatformsOnly` governs the default destination policy. An explicit
        // remote-worker opt-in is the documented override used for remote→remote
        // delegation; previously this check ignored the opt-in and rejected every
        // external target before dispatch.
        if (config.localPlatformsOnly && !target.isPrivateDestination() && !config.allowRemoteWorkers) {
            return error("This target is blocked by the private-destination-only setting.").also { AppLogRecorder.record("Delegation", "Rejected privacy policy · target=${target.uid}", "W") }
        }
        if (source.compatibleType == ClientType.LITERT_LM && target.compatibleType == ClientType.LITERT_LM) {
            return error("The on-device engine is busy with this response. Select a llama/Ollama server or another provider as the delegate.").also { AppLogRecorder.record("Delegation", "Rejected dual LiteRT dispatch · target=${target.uid}", "W") }
        }
        if (calls.incrementAndGet() > config.maxCallsPerTurn) return error("The delegation call limit for this turn has been reached.").also { AppLogRecorder.record("Delegation", "Rejected call budget · target=${target.uid} · max=${config.maxCallsPerTurn}", "W") }
        AppLogRecorder.record("Delegation", "Dispatching · call=$callId · source=${source.compatibleType} · target=${target.compatibleType} · timeoutSec=${config.timeoutSeconds} · outputCap=${config.maxOutputTokens}")
        return try {
            val response = withTimeoutOrNull(config.timeoutSeconds * 1000L + 1000L) { generate(target, task, config.maxOutputTokens) }
                ?: return error("The delegated task timed out after ${config.timeoutSeconds} seconds.").also { AppLogRecorder.record("Delegation", "Timed out · call=$callId · target=${target.uid}", "E") }
            if (response.isBlank()) return error("The target model returned no text.").also { AppLogRecorder.record("Delegation", "Empty response · call=$callId · target=${target.uid}", "W") }
            AppLogRecorder.record("Delegation", "Completed · call=$callId · target=${target.uid} · outputChars=${response.length}")
            AgentToolResult(callId, ToolResultContent.Text(response), false)
        } catch (cancellation: CancellationException) {
            AppLogRecorder.record("Delegation", "Cancelled · call=$callId · target=${target.uid}", "W")
            throw cancellation
        } catch (failure: Exception) {
            AppLogRecorder.record("Delegation", "Failed · call=$callId · target=${target.uid} · ${failure.javaClass.simpleName}: ${failure.message.orEmpty()}", "E")
            error("Delegation failed. Check the target profile, credentials and model availability.")
        }
    }
}
