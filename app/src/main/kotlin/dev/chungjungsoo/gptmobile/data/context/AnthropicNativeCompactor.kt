package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.agent.provider.AnthropicEventAssembler
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.CompactionContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.AnthropicCompactTrigger
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.AnthropicContextEdit
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.AnthropicContextManagement
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class AnthropicNativeCompactor(
    private val api: AnthropicAPI,
    private val encodeMessages: suspend (CompactInput) -> List<InputMessage>
) : ContextCompactor {
    override fun supports(platform: PlatformV2, estimatedTokens: Int): Boolean {
        if (platform.compatibleType != ClientType.ANTHROPIC) return false
        if (platform.apiUrl.trimEnd('/') != ModelConstants.ANTHROPIC_API_URL.trimEnd('/')) return false
        if (estimatedTokens < MIN_TRIGGER_TOKENS) return false
        return SUPPORTED_MODELS.any { supported -> platform.model.startsWith(supported) }
    }

    override suspend fun compact(input: CompactInput): CompactOutput {
        val triggerValue = maxOf(MIN_TRIGGER_TOKENS, input.targetTokens)
        val existing = input.existingNativeJson?.let { json ->
            NetworkClient.json.decodeFromString<List<InputMessage>>(json)
        }.orEmpty()
        val request = MessageRequest(
            model = input.platform.model,
            messages = existing + encodeMessages(input.copy(existingNativeJson = null)),
            maxTokens = resolvedOutputTokenCap(input.platform) ?: OutputTokenBudget.ANTHROPIC_DEFAULT_OUTPUT_TOKENS,
            stream = true,
            systemPrompt = input.instructions,
            contextManagement = AnthropicContextManagement(
                edits = listOf(
                    AnthropicContextEdit(
                        trigger = AnthropicCompactTrigger(value = triggerValue),
                        pauseAfterCompaction = true
                    )
                )
            )
        )
        val assembler = AnthropicEventAssembler()
        api.streamChatMessage(
            request,
            input.platform.timeout,
            ProviderRequestConfig(
                apiUrl = input.platform.apiUrl,
                token = input.platform.token,
                anthropicBetaFeatures = setOf(BETA)
            )
        ).collect { chunk ->
            assembler.accept(chunk)
        }
        val compacted = assembler.replayContent().filterIsInstance<CompactionContent>()
        if (compacted.isEmpty()) {
            throw IllegalStateException("Anthropic native compaction did not return a compaction block")
        }
        val nativeMessages = listOf(InputMessage(MessageRole.ASSISTANT, compacted))
        return CompactOutput(
            representation = CompactionRepresentation.NATIVE_ANTHROPIC,
            nativeItemsJson = NetworkClient.json.encodeToString(nativeMessages),
            coveredTurnCount = input.turns.size
        )
    }

    companion object {
        const val BETA = "compact-2026-01-12"
        const val MIN_TRIGGER_TOKENS = 50_000
        val SUPPORTED_MODELS = listOf(
            "claude-sonnet-5",
            "claude-opus-5",
            "claude-sonnet-4-6",
            "claude-opus-4-6",
            "claude-opus-4-7",
            "claude-opus-4-8",
            "claude-fable-5",
            "claude-mythos-5",
            "claude-mythos-preview"
        )
    }
}
