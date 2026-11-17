package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.ThinkingConfig
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.ThinkingEffort
import dev.chungjungsoo.gptmobile.data.model.anthropicOutputEffort
import dev.chungjungsoo.gptmobile.data.model.manualThinkingBudget

object OutputTokenBudget {
    const val DEFAULT_HOSTED_OUTPUT_TOKENS = 4_096
    const val ANTHROPIC_THINKING_OUTPUT_TOKENS = 16_000
    const val ANTHROPIC_DEFAULT_OUTPUT_TOKENS = 4_096
    const val GROQ_REASONING_OUTPUT_TOKENS = 8_192
    const val LOCAL_OUTPUT_RESERVE = 256

    fun hostedOutputCap(platform: PlatformV2): Int? = platform.maxTokens?.takeIf { it > 0 }
}

data class AnthropicThinkingPolicy(
    val config: ThinkingConfig?,
    val betaFeatures: Set<String>,
    val outputEffort: String? = null
) {
    val isThinkingActive: Boolean
        get() = config?.type?.let { it != "disabled" } == true
}

const val ANTHROPIC_INTERLEAVED_THINKING_BETA = "interleaved-thinking-2025-05-14"
private val ADAPTIVE_ANTHROPIC_MODEL_PATTERN = Regex(
    "(?:^|-)4-(?:6|7|8)(?:-|$)|claude-(?:opus|sonnet|haiku)-5(?:-|$)|claude-5-(?:opus|sonnet|haiku)(?:-|$)"
)
private val DEFAULT_ON_DISABLEABLE_ANTHROPIC_MODEL_PATTERN =
    Regex("claude-(?:opus|sonnet)-5(?:-|$)|claude-5-(?:opus|sonnet)(?:-|$)")
private val MANUAL_THINKING_ANTHROPIC_MODEL_PATTERN = Regex("(?:^|-)3-7(?:-|$)|(?:^|-)4(?:-|$)")
private val MANUAL_INTERLEAVED_ANTHROPIC_MODEL_PATTERN = Regex("(?:^|-)4(?:-|$)")

fun anthropicThinkingPolicy(
    model: String,
    reasoningEnabled: Boolean,
    hasTools: Boolean,
    effort: ThinkingEffort = ThinkingEffort.DEFAULT
): AnthropicThinkingPolicy {
    val normalizedModel = model.lowercase()
    if (!reasoningEnabled) {
        val config = ThinkingConfig(type = "disabled")
            .takeIf { DEFAULT_ON_DISABLEABLE_ANTHROPIC_MODEL_PATTERN.containsMatchIn(normalizedModel) }
        return AnthropicThinkingPolicy(config = config, betaFeatures = emptySet())
    }

    val outputEffort = anthropicOutputEffort(model, effort)
    val usesAdaptiveThinking = ADAPTIVE_ANTHROPIC_MODEL_PATTERN.containsMatchIn(normalizedModel) ||
        normalizedModel.contains("mythos") ||
        normalizedModel.contains("fable")
    if (usesAdaptiveThinking) {
        return AnthropicThinkingPolicy(
            config = ThinkingConfig(type = "adaptive", display = "summarized"),
            betaFeatures = emptySet(),
            outputEffort = outputEffort
        )
    }
    if (!MANUAL_THINKING_ANTHROPIC_MODEL_PATTERN.containsMatchIn(normalizedModel)) {
        return AnthropicThinkingPolicy(config = null, betaFeatures = emptySet())
    }

    val supportsManualInterleaving = hasTools &&
        (normalizedModel.contains("opus") || normalizedModel.contains("sonnet")) &&
        MANUAL_INTERLEAVED_ANTHROPIC_MODEL_PATTERN.containsMatchIn(normalizedModel)
    return AnthropicThinkingPolicy(
        config = ThinkingConfig(
            type = "enabled",
            budgetTokens = manualThinkingBudget(effort),
            display = "summarized"
        ),
        betaFeatures = if (supportsManualInterleaving) setOf(ANTHROPIC_INTERLEAVED_THINKING_BETA) else emptySet(),
        outputEffort = outputEffort
    )
}

fun resolvedOutputTokenCap(platform: PlatformV2, hasTools: Boolean = false): Int? {
    if (platform.compatibleType == ClientType.LITERT_LM) return null
    return when (platform.compatibleType) {
        ClientType.ANTHROPIC -> {
            val policy = anthropicThinkingPolicy(
                model = platform.model,
                reasoningEnabled = platform.reasoning,
                hasTools = hasTools,
                effort = ThinkingEffort.fromStorage(platform.thinkingEffort)
            )
            if (policy.isThinkingActive) {
                val budgetFloor = (policy.config?.budgetTokens ?: 0) + 1_024
                maxOf(platform.maxTokens ?: 0, OutputTokenBudget.ANTHROPIC_THINKING_OUTPUT_TOKENS, budgetFloor)
            } else {
                platform.maxTokens?.takeIf { it > 0 } ?: OutputTokenBudget.ANTHROPIC_DEFAULT_OUTPUT_TOKENS
            }
        }

        ClientType.GROQ -> if (platform.reasoning) {
            OutputTokenBudget.GROQ_REASONING_OUTPUT_TOKENS
        } else {
            OutputTokenBudget.hostedOutputCap(platform)
        }

        else -> OutputTokenBudget.hostedOutputCap(platform)
    }
}

fun outputReserveTokens(platform: PlatformV2, hasTools: Boolean = false): Int {
    if (platform.compatibleType == ClientType.LITERT_LM) return OutputTokenBudget.LOCAL_OUTPUT_RESERVE
    return resolvedOutputTokenCap(platform, hasTools) ?: OutputTokenBudget.DEFAULT_HOSTED_OUTPUT_TOKENS
}
