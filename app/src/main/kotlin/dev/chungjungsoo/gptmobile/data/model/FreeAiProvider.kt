package dev.chungjungsoo.gptmobile.data.model

import dev.chungjungsoo.gptmobile.BuildConfig
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import java.net.URI

/** App-owned presets: no credentials, paid routes or automatic provider fallback. */
enum class FreeAiProvider(
    val displayName: String,
    val apiUrl: String,
    val model: String,
    val supportsTools: Boolean,
    val requestsPerMinute: Int,
    val requestsPerHour: Int,
    val maxOutputTokens: Int = 2048
) {
    KILO("Kilo", "https://api.kilo.ai/api/gateway", "kilo-auto/free", true, 0, 200),
    POLLINATIONS("Pollinations", "https://text.pollinations.ai", "openai-fast", true, 0, 0),
    OVHCLOUD("OVHcloud", "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1", "Meta-Llama-3_3-70B-Instruct", true, 2, 0),
    LLM7("LLM7", "https://api.llm7.io/v1", "mistral-Nemo-Instruct-2407", true, 10, 60);

    val isAvailable: Boolean
        get() = this != LLM7 || BuildConfig.FREE_LLM7_APPROVED

    val chatCompletionsUrl: String
        get() = if (this == POLLINATIONS) "$apiUrl/openai" else "$apiUrl/chat/completions"

    fun applyTo(platform: PlatformV2): PlatformV2 = platform.copy(
        name = platform.name.takeUnless { it.isBlank() || it == "Free Models" } ?: displayName,
        compatibleType = ClientType.FREE,
        apiUrl = apiUrl,
        token = null,
        secretRef = null,
        model = model,
        maxTokens = (platform.maxTokens ?: maxOutputTokens).coerceIn(1, maxOutputTokens),
        batchMode = false,
        batchApiUrl = null,
        openRouterRouting = null,
        ollamaOptions = null,
        topK = null
    )

    companion object {
        val default = KILO

        fun fromApiUrl(url: String): FreeAiProvider? {
            val normalized = url.trim().trimEnd('/')
            return entries.firstOrNull { it.apiUrl == normalized || it.chatCompletionsUrl == normalized }
        }

        fun requireFor(platform: PlatformV2): FreeAiProvider = requireNotNull(fromApiUrl(platform.apiUrl)) {
            "Choose a Free provider in the AI profile settings."
        }
    }
}

/** Also protects imported/manual profiles that use known free routes. */
fun PlatformV2.excludesMemory(): Boolean {
    if (compatibleType == ClientType.FREE) return true
    if (model.endsWith(":free") || model == "openrouter/free" || model == "kilo-auto/free") return true
    val host = runCatching { URI(apiUrl.trim()).host?.lowercase() }.getOrNull()
    return host in setOf("text.pollinations.ai", "api.llm7.io") ||
        (host == "oai.endpoints.kepler.ai.cloud.ovh.net" && token.isNullOrBlank())
}
