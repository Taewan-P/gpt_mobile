package dev.chungjungsoo.gptmobile.data.model

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.google.request.ThinkingConfig
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatReasoningConfig
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ReasoningConfig

/**
 * Base effort is stored on the provider profile, next to the existing extended-thinking
 * switch. A chat may override that base; a null override keeps each profile's own value.
 */
fun applyChatThinkingEffort(platform: PlatformV2, chatThinkingEffort: String?): PlatformV2 {
    val override = ThinkingEffort.fromStorageOrNull(chatThinkingEffort) ?: return platform
    if (override.storageValue == platform.thinkingEffort) return platform
    return platform.copy(thinkingEffort = override.storageValue)
}

fun requestThinkingEffort(platform: PlatformV2): ThinkingEffort? {
    if (!platform.reasoning) return null
    if (!supportsAdjustableThinkingEffort(platform.compatibleType, platform.model)) return null
    return ThinkingEffort.fromStorage(platform.thinkingEffort)
}

fun supportsAdjustableThinkingEffort(clientType: ClientType, model: String): Boolean = when (clientType) {
    ClientType.OPENAI -> supportsOpenAiReasoningEffort(model)
    ClientType.ANTHROPIC -> supportsAnthropicThinkingAdjustment(model)
    ClientType.GOOGLE -> geminiThinkingMode(model) != null
    ClientType.GROQ -> supportsGroqReasoningEffort(model)
    ClientType.OPENROUTER -> true
    ClientType.OLLAMA -> true

    ClientType.CUSTOM, ClientType.LITERT_LM -> false
}

enum class ThinkingEffortControlStatus {
    ADJUSTABLE,
    REQUIRES_EXTENDED_THINKING,
    UNSUPPORTED
}

data class ThinkingEffortControlState(
    val enabled: Boolean,
    val selectedEffort: ThinkingEffort?,
    val followsProfileDefault: Boolean,
    val status: ThinkingEffortControlStatus
)

fun thinkingEffortControlState(
    platforms: List<PlatformV2>,
    chatOverride: String?
): ThinkingEffortControlState {
    val supporting = platforms.filter { platform ->
        supportsAdjustableThinkingEffort(platform.compatibleType, platform.model)
    }
    val active = supporting.filter { platform -> platform.reasoning }
    if (active.isEmpty()) {
        val status = if (supporting.isNotEmpty()) {
            ThinkingEffortControlStatus.REQUIRES_EXTENDED_THINKING
        } else {
            ThinkingEffortControlStatus.UNSUPPORTED
        }
        return ThinkingEffortControlState(
            enabled = false,
            selectedEffort = null,
            followsProfileDefault = true,
            status = status
        )
    }
    val override = ThinkingEffort.fromStorageOrNull(chatOverride)
    if (override != null) {
        return ThinkingEffortControlState(
            enabled = true,
            selectedEffort = override,
            followsProfileDefault = false,
            status = ThinkingEffortControlStatus.ADJUSTABLE
        )
    }
    val bases = active.map { platform -> ThinkingEffort.fromStorage(platform.thinkingEffort) }.distinct()
    return ThinkingEffortControlState(
        enabled = true,
        selectedEffort = bases.singleOrNull(),
        followsProfileDefault = true,
        status = ThinkingEffortControlStatus.ADJUSTABLE
    )
}

fun platformThinkingEffortEditable(platform: PlatformV2): Boolean =
    platform.compatibleType != ClientType.LITERT_LM &&
        platform.reasoning &&
        supportsAdjustableThinkingEffort(platform.compatibleType, platform.model)

fun openAiResponsesReasoning(platform: PlatformV2): ReasoningConfig? {
    if (platform.compatibleType != ClientType.OPENAI) return null
    val effort = requestThinkingEffort(platform) ?: return null
    return ReasoningConfig(
        effort = openAiReasoningEffort(platform.model, effort),
        summary = "auto"
    )
}

data class OpenAIChatReasoningFields(
    val reasoningEffort: String? = null,
    val reasoning: ChatReasoningConfig? = null
)

fun openAIChatReasoningFields(platform: PlatformV2): OpenAIChatReasoningFields {
    val effort = requestThinkingEffort(platform) ?: return OpenAIChatReasoningFields()
    return when (platform.compatibleType) {
        ClientType.OPENROUTER -> OpenAIChatReasoningFields(
            reasoning = ChatReasoningConfig(effort = openRouterReasoningEffort(effort))
        )

        ClientType.OLLAMA -> OpenAIChatReasoningFields(
            reasoningEffort = ollamaReasoningEffort(platform.model, effort)
        )

        ClientType.OPENAI -> OpenAIChatReasoningFields(
            reasoningEffort = openAiReasoningEffort(platform.model, effort)
        )

        else -> OpenAIChatReasoningFields()
    }
}

fun groqReasoningEffort(model: String, effort: ThinkingEffort): String? {
    if (!supportsGroqReasoningEffort(model)) return null
    val supportsXhigh = model.lowercase().contains("muse")
    return when (effort) {
        ThinkingEffort.MINIMAL, ThinkingEffort.LOW -> "low"
        ThinkingEffort.MEDIUM -> "medium"
        ThinkingEffort.HIGH -> "high"
        ThinkingEffort.MAX -> if (supportsXhigh) "xhigh" else "high"
    }
}

fun googleThinkingConfig(platform: PlatformV2): ThinkingConfig? {
    if (platform.compatibleType != ClientType.GOOGLE) return null
    val effort = requestThinkingEffort(platform) ?: return null
    return when (geminiThinkingMode(platform.model)) {
        GeminiThinkingMode.LEVEL -> ThinkingConfig(
            thinkingLevel = geminiThinkingLevel(platform.model, effort),
            includeThoughts = true
        )

        GeminiThinkingMode.BUDGET -> ThinkingConfig(
            thinkingBudget = geminiThinkingBudget(platform.model, effort),
            includeThoughts = true
        )

        null -> null
    }
}

fun anthropicOutputEffort(model: String, effort: ThinkingEffort): String? {
    if (!supportsAnthropicOutputEffort(model)) return null
    return when (effort) {
        ThinkingEffort.MINIMAL, ThinkingEffort.LOW -> "low"
        ThinkingEffort.MEDIUM -> "medium"
        ThinkingEffort.HIGH -> "high"
        ThinkingEffort.MAX -> if (supportsAnthropicMaxEffort(model)) "max" else "high"
    }
}

fun manualThinkingBudget(effort: ThinkingEffort): Int = when (effort) {
    ThinkingEffort.MINIMAL -> 1_024
    ThinkingEffort.LOW -> 2_048
    ThinkingEffort.MEDIUM -> 10_000
    ThinkingEffort.HIGH -> 16_000
    ThinkingEffort.MAX -> 32_000
}

internal enum class GeminiThinkingMode {
    LEVEL,
    BUDGET
}

internal fun geminiThinkingMode(model: String): GeminiThinkingMode? {
    val normalized = model.lowercase()
    if (!normalized.contains("gemini")) return null
    if (Regex("""gemini-(?:[3-9]|\d{2,})""").containsMatchIn(normalized)) return GeminiThinkingMode.LEVEL
    if (normalized.contains("gemini-2.5") || normalized.contains("gemini-2-5")) {
        if (normalized.contains("image")) return null
        return GeminiThinkingMode.BUDGET
    }
    return null
}

internal fun supportsOpenAiReasoningEffort(model: String): Boolean {
    val normalized = model.lowercase()
    if (OPENAI_O_SERIES.containsMatchIn(normalized)) return true
    return normalized.contains("gpt-5") || normalized.contains("gpt-6")
}

internal fun openAiReasoningEffort(model: String, effort: ThinkingEffort): String {
    val supported = openAiSupportedEfforts(model)
    val preferred = when (effort) {
        ThinkingEffort.MINIMAL -> listOf("minimal", "low", "medium", "high")
        ThinkingEffort.LOW -> listOf("low", "medium", "high")
        ThinkingEffort.MEDIUM -> listOf("medium", "high", "low")
        ThinkingEffort.HIGH -> listOf("high", "medium", "xhigh", "max", "low")
        ThinkingEffort.MAX -> listOf("max", "xhigh", "high", "medium", "low")
    }
    return preferred.first { candidate -> candidate in supported }
}

internal fun supportsGroqReasoningEffort(model: String): Boolean {
    val normalized = model.lowercase()
    return normalized.contains("gpt-oss") ||
        normalized.contains("qwen3.8") ||
        normalized.contains("qwen-3.8") ||
        normalized.contains("muse")
}

internal fun supportsAnthropicThinkingAdjustment(model: String): Boolean {
    val normalized = model.lowercase()
    return ANTHROPIC_ADAPTIVE.containsMatchIn(normalized) ||
        ANTHROPIC_MANUAL.containsMatchIn(normalized)
}

private fun openRouterReasoningEffort(effort: ThinkingEffort): String = effort.storageValue

private fun ollamaReasoningEffort(model: String, effort: ThinkingEffort): String {
    if (model.lowercase().contains("gpt-oss")) {
        return when (effort) {
            ThinkingEffort.MINIMAL, ThinkingEffort.LOW -> "low"
            ThinkingEffort.MEDIUM -> "medium"
            ThinkingEffort.HIGH, ThinkingEffort.MAX -> "high"
        }
    }
    return effort.storageValue
}

private fun openAiSupportedEfforts(model: String): Set<String> {
    val normalized = model.lowercase()
    if (normalized.contains("gpt-5.2-pro")) return setOf("medium", "high", "xhigh")
    if (normalized.contains("gpt-5-pro") || normalized.contains("gpt-5.0-pro")) return setOf("high")
    if (OPENAI_O_SERIES.containsMatchIn(normalized)) return setOf("low", "medium", "high")
    // GPT-6.1 Sol rejects none and minimal (HTTP 400) and defaults to medium.
    if (normalized.contains("gpt-6.1-sol")) return setOf("low", "medium", "high", "xhigh", "max")
    if (normalized.contains("gpt-6")) return OPENAI_FULL_EFFORTS
    val minor = GPT5_MINOR.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
    return when {
        minor == null && normalized.contains("gpt-5") -> setOf("minimal", "low", "medium", "high")
        minor == 1 -> setOf("none", "low", "medium", "high")
        // GPT-5.2 through GPT-5.4 document none, low, medium, high, and xhigh.
        minor != null && minor < 6 -> OPENAI_XHIGH_EFFORTS
        // GPT-5.6 adds max and still does not list minimal.
        minor != null -> OPENAI_MAX_EFFORTS
        normalized.contains("gpt-5") -> setOf("minimal", "low", "medium", "high")
        else -> setOf("low", "medium", "high")
    }
}

private fun geminiSupportedLevels(model: String): Set<String> {
    val normalized = model.lowercase()
    if (normalized.contains("flash-lite") && normalized.contains("image")) {
        return setOf("MINIMAL", "HIGH")
    }
    val isGemini3Pro = normalized.contains("pro") &&
        !normalized.contains("flash") &&
        !normalized.contains("3.1")
    if (isGemini3Pro) return setOf("LOW", "HIGH")
    if (normalized.contains("3.1") && normalized.contains("pro")) {
        return setOf("LOW", "MEDIUM", "HIGH")
    }
    if (Regex("""gemini-3\.[78]""").containsMatchIn(normalized) && normalized.contains("flash")) {
        return setOf("LOW", "MEDIUM", "HIGH")
    }
    return setOf("MINIMAL", "LOW", "MEDIUM", "HIGH")
}

private fun geminiThinkingLevel(model: String, effort: ThinkingEffort): String {
    val supported = geminiSupportedLevels(model)
    val preferred = when (effort) {
        ThinkingEffort.MINIMAL -> listOf("MINIMAL", "LOW", "MEDIUM", "HIGH")
        ThinkingEffort.LOW -> listOf("LOW", "MEDIUM", "MINIMAL", "HIGH")
        ThinkingEffort.MEDIUM -> listOf("MEDIUM", "HIGH", "LOW", "MINIMAL")
        ThinkingEffort.HIGH, ThinkingEffort.MAX -> listOf("HIGH", "MEDIUM", "LOW", "MINIMAL")
    }
    return preferred.first { candidate -> candidate in supported }
}

private fun geminiThinkingBudget(model: String, effort: ThinkingEffort): Int {
    val normalized = model.lowercase()
    val isPro = normalized.contains("pro")
    val isLite = normalized.contains("lite")
    val minimum = when {
        isPro -> 128
        isLite -> 512
        else -> 1
    }
    val maximum = if (isPro) 32_768 else 24_576
    val raw = when (effort) {
        ThinkingEffort.MINIMAL -> minimum
        ThinkingEffort.LOW -> 2_048
        ThinkingEffort.MEDIUM -> 8_192
        ThinkingEffort.HIGH -> 16_384
        ThinkingEffort.MAX -> maximum
    }
    return raw.coerceIn(minimum, maximum)
}

private fun supportsAnthropicOutputEffort(model: String): Boolean =
    ANTHROPIC_OUTPUT_EFFORT.containsMatchIn(model.lowercase())

private fun supportsAnthropicMaxEffort(model: String): Boolean =
    ANTHROPIC_MAX_EFFORT.containsMatchIn(model.lowercase())

private val OPENAI_FULL_EFFORTS = setOf("none", "minimal", "low", "medium", "high", "xhigh", "max")
private val OPENAI_XHIGH_EFFORTS = setOf("none", "low", "medium", "high", "xhigh")
private val OPENAI_MAX_EFFORTS = setOf("none", "low", "medium", "high", "xhigh", "max")
private val OPENAI_O_SERIES = Regex("""(?:^|[^a-z0-9])o[1-9](?:[^a-z0-9]|$)""")
private val GPT5_MINOR = Regex("""gpt-5\.(\d+)""")
private val ANTHROPIC_ADAPTIVE = Regex(
    """(?:^|-)4-(?:6|7|8)(?:-|$)|claude-(?:opus|sonnet|haiku)-5(?:-|$)|claude-5-(?:opus|sonnet|haiku)(?:-|$)|mythos|fable"""
)
private val ANTHROPIC_MANUAL = Regex("""(?:^|-)3-7(?:-|$)|(?:^|-)4(?:-|$)""")
private val ANTHROPIC_OUTPUT_EFFORT = Regex(
    """fable|mythos|claude-(?:opus|sonnet|haiku)-5(?:-|$)|claude-5-|opus-4-(?:5|6|7|8)|sonnet-4-6"""
)
private val ANTHROPIC_MAX_EFFORT = Regex(
    """fable|mythos|claude-(?:opus|sonnet)-5(?:-|$)|claude-5-(?:opus|sonnet)|opus-4-(?:6|7|8)|sonnet-4-6|haiku-5-5|haiku-5\.5|sonnet-5-5|opus-5-5"""
)
