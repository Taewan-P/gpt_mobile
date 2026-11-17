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
    if (platform.omitThinkingEffort || thinkingEffortSuppressed(platform)) return null
    if (!platform.reasoning) return null
    val offered = offeredThinkingEfforts(platform)
    if (offered.isEmpty()) return null
    return nearestThinkingEffort(ThinkingEffort.fromStorage(platform.thinkingEffort), offered)
}

fun thinkingEffortSuppressed(platform: PlatformV2): Boolean =
    platform.model in platform.effortUnsupportedModels

/**
 * Levels the picker may offer. A saved profile override replaces the automatic
 * set. First-party providers use their model table. Every other provider gets
 * low, medium, and high until a request shows the server rejects the parameter.
 */
fun offeredThinkingEfforts(platform: PlatformV2): List<ThinkingEffort> {
    if (platform.compatibleType == ClientType.LITERT_LM) return emptyList()
    if (thinkingEffortSuppressed(platform)) return emptyList()
    parseSupportedEffortOverride(platform.supportedEffortLevels)?.let { return it }
    return automaticOfferedThinkingEfforts(platform.compatibleType, platform.model)
}

fun parseSupportedEffortOverride(stored: String?): List<ThinkingEffort>? {
    if (stored == null) return null
    return stored.split(',')
        .mapNotNull { token -> ThinkingEffort.fromStorageOrNull(token) }
        .distinct()
        .sortedBy { effort -> effort.ordinal }
        .takeIf { levels -> levels.isNotEmpty() }
}

fun nearestThinkingEffort(stored: ThinkingEffort, offered: List<ThinkingEffort>): ThinkingEffort {
    if (offered.isEmpty() || stored in offered) return stored
    return offered.minWith(
        compareBy<ThinkingEffort> { effort -> kotlin.math.abs(effort.ordinal - stored.ordinal) }
            .thenByDescending { effort -> effort.ordinal }
    )
}

fun automaticOfferedThinkingEfforts(clientType: ClientType, model: String): List<ThinkingEffort> = when (clientType) {
    ClientType.OPENAI -> openAiOfferedEfforts(model)
    ClientType.ANTHROPIC -> anthropicOfferedEfforts(model)
    ClientType.GOOGLE -> geminiOfferedEfforts(model)
    ClientType.GROQ, ClientType.OPENROUTER, ClientType.OLLAMA, ClientType.CUSTOM -> GENERIC_EFFORTS
    ClientType.LITERT_LM -> emptyList()
}

fun supportsAdjustableThinkingEffort(clientType: ClientType, model: String): Boolean =
    automaticOfferedThinkingEfforts(clientType, model).isNotEmpty()

enum class ThinkingEffortControlStatus {
    ADJUSTABLE,
    REQUIRES_EXTENDED_THINKING,
    UNSUPPORTED
}

data class ThinkingEffortControlState(
    val enabled: Boolean,
    val selectedEffort: ThinkingEffort?,
    val followsProfileDefault: Boolean,
    val status: ThinkingEffortControlStatus,
    val offered: List<ThinkingEffort> = emptyList(),
    val storedEffort: ThinkingEffort? = null,
    val adjustedToNearest: Boolean = false,
    val rejectedByModel: Boolean = false
)

fun thinkingEffortControlState(
    platforms: List<PlatformV2>,
    chatOverride: String?
): ThinkingEffortControlState {
    val reasoningPlatforms = platforms.filter { platform -> platform.reasoning }
    val rejectedByModel = reasoningPlatforms.any { platform -> thinkingEffortSuppressed(platform) }
    if (reasoningPlatforms.isEmpty()) {
        val couldSupport = platforms.any { platform ->
            automaticOfferedThinkingEfforts(platform.compatibleType, platform.model).isNotEmpty()
        }
        return ThinkingEffortControlState(
            enabled = false,
            selectedEffort = null,
            followsProfileDefault = true,
            status = if (couldSupport) {
                ThinkingEffortControlStatus.REQUIRES_EXTENDED_THINKING
            } else {
                ThinkingEffortControlStatus.UNSUPPORTED
            }
        )
    }
    val active = reasoningPlatforms.filter { platform -> offeredThinkingEfforts(platform).isNotEmpty() }
    if (active.isEmpty()) {
        return ThinkingEffortControlState(
            enabled = false,
            selectedEffort = null,
            followsProfileDefault = true,
            status = ThinkingEffortControlStatus.UNSUPPORTED,
            rejectedByModel = rejectedByModel
        )
    }
    val offered = active
        .map { platform -> offeredThinkingEfforts(platform) }
        .reduce { shared, next -> shared.filter { effort -> effort in next } }
    if (offered.isEmpty()) {
        return ThinkingEffortControlState(
            enabled = false,
            selectedEffort = null,
            followsProfileDefault = true,
            status = ThinkingEffortControlStatus.UNSUPPORTED,
            rejectedByModel = rejectedByModel
        )
    }
    val override = ThinkingEffort.fromStorageOrNull(chatOverride)
    if (override != null) {
        val effective = nearestThinkingEffort(override, offered)
        return ThinkingEffortControlState(
            enabled = true,
            selectedEffort = effective,
            followsProfileDefault = false,
            status = ThinkingEffortControlStatus.ADJUSTABLE,
            offered = offered,
            storedEffort = override,
            adjustedToNearest = effective != override,
            rejectedByModel = rejectedByModel
        )
    }
    val effectiveBases = active.map { platform ->
        nearestThinkingEffort(ThinkingEffort.fromStorage(platform.thinkingEffort), offeredThinkingEfforts(platform))
    }.distinct()
    val adjusted = active.any { platform ->
        val stored = ThinkingEffort.fromStorage(platform.thinkingEffort)
        nearestThinkingEffort(stored, offeredThinkingEfforts(platform)) != stored
    }
    return ThinkingEffortControlState(
        enabled = true,
        selectedEffort = effectiveBases.singleOrNull(),
        followsProfileDefault = true,
        status = ThinkingEffortControlStatus.ADJUSTABLE,
        offered = offered,
        storedEffort = effectiveBases.singleOrNull(),
        adjustedToNearest = adjusted,
        rejectedByModel = rejectedByModel
    )
}

fun platformThinkingEffortEditable(platform: PlatformV2): Boolean =
    platform.reasoning && offeredThinkingEfforts(platform).isNotEmpty()

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
            reasoningEffort = ollamaReasoningEffort(effort)
        )

        ClientType.OPENAI -> OpenAIChatReasoningFields(
            reasoningEffort = openAiReasoningEffort(platform.model, effort)
        )

        ClientType.CUSTOM -> OpenAIChatReasoningFields(reasoningEffort = effort.storageValue)

        else -> OpenAIChatReasoningFields()
    }
}

fun groqReasoningEffort(model: String, effort: ThinkingEffort): String? {
    if (!supportsAdjustableThinkingEffort(ClientType.GROQ, model)) return null
    return effort.storageValue
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

internal fun supportsAnthropicThinkingAdjustment(model: String): Boolean {
    val normalized = model.lowercase()
    return ANTHROPIC_ADAPTIVE.containsMatchIn(normalized) ||
        ANTHROPIC_MANUAL.containsMatchIn(normalized)
}

private fun openRouterReasoningEffort(effort: ThinkingEffort): String = effort.storageValue

private fun ollamaReasoningEffort(effort: ThinkingEffort): String = effort.storageValue

private val GENERIC_EFFORTS = listOf(ThinkingEffort.LOW, ThinkingEffort.MEDIUM, ThinkingEffort.HIGH)

private fun openAiOfferedEfforts(model: String): List<ThinkingEffort> {
    if (!supportsOpenAiReasoningEffort(model)) return emptyList()
    val wire = openAiSupportedEfforts(model)
    return ThinkingEffort.entries.filter { effort ->
        when (effort) {
            ThinkingEffort.MINIMAL -> "minimal" in wire
            ThinkingEffort.LOW -> "low" in wire
            ThinkingEffort.MEDIUM -> "medium" in wire
            ThinkingEffort.HIGH -> "high" in wire
            ThinkingEffort.MAX -> "max" in wire || "xhigh" in wire
        }
    }
}

private fun anthropicOfferedEfforts(model: String): List<ThinkingEffort> {
    if (!supportsAnthropicThinkingAdjustment(model)) return emptyList()
    if (!supportsAnthropicOutputEffort(model)) return ThinkingEffort.entries
    val levels = mutableListOf(ThinkingEffort.LOW, ThinkingEffort.MEDIUM, ThinkingEffort.HIGH)
    if (supportsAnthropicMaxEffort(model)) levels += ThinkingEffort.MAX
    return levels
}

private fun geminiOfferedEfforts(model: String): List<ThinkingEffort> {
    when (geminiThinkingMode(model)) {
        null -> return emptyList()
        GeminiThinkingMode.BUDGET -> return ThinkingEffort.entries
        GeminiThinkingMode.LEVEL -> Unit
    }
    val wire = geminiSupportedLevels(model)
    return listOf(
        ThinkingEffort.MINIMAL to "MINIMAL",
        ThinkingEffort.LOW to "LOW",
        ThinkingEffort.MEDIUM to "MEDIUM",
        ThinkingEffort.HIGH to "HIGH"
    ).mapNotNull { (effort, token) -> effort.takeIf { token in wire } }
}

fun thinkingEffortParameterRejected(message: String): Boolean {
    val normalized = message.lowercase()
    val status = HTTP_STATUS.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (status != null && status !in EFFORT_REJECTION_STATUSES) return false
    if (EFFORT_VALUE_ERRORS.any(normalized::contains)) return false
    val namesParameter = EFFORT_PARAMETER_NAMES.any(normalized::contains)
    val rejectsParameter = EFFORT_REJECTION_PHRASES.any(normalized::contains)
    return namesParameter && rejectsParameter
}

private val HTTP_STATUS = Regex("""http\s+(\d{3})""")
private val EFFORT_REJECTION_STATUSES = setOf(400, 422)
private val EFFORT_PARAMETER_NAMES = listOf(
    "reasoning_effort",
    "reasoning.effort",
    "reasoning effort",
    "output_config",
    "thinking_level",
    "thinkinglevel",
    "thinking_budget",
    "thinkingbudget",
    "budget_tokens"
)
private val EFFORT_REJECTION_PHRASES = listOf(
    "not supported",
    "isn't supported",
    "unsupported",
    "unknown parameter",
    "unknown field",
    "unexpected parameter",
    "unexpected field",
    "unrecognized",
    "extra field",
    "additional propert",
    "not allowed",
    "invalid parameter",
    "does not support",
    "doesn't support",
    "is not permitted"
)
private val EFFORT_VALUE_ERRORS = listOf(
    "must be one of",
    "supported values",
    "invalid value",
    "not a valid",
    "invalid enum",
    "expected one of"
)

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
