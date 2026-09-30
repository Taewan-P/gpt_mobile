package dev.chungjungsoo.gptmobile.data.openrouter

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OpenRouterProviderRouting(
    @SerialName("allow_fallbacks")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val allowFallbacks: Boolean? = null,

    @SerialName("require_parameters")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val requireParameters: Boolean? = null,

    @SerialName("data_collection")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val dataCollection: String? = null,

    @SerialName("sort")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val sort: String? = null,

    @SerialName("ignore")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val ignore: List<String>? = null,

    @SerialName("quantizations")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val quantizations: List<String>? = null,

    @SerialName("z_weight")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val zWeight: Float? = null
) {
    fun normalized(): OpenRouterProviderRouting = copy(
        sort = when (sort) {
            "price-asc" -> "price"
            else -> sort
        }
    )
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OpenRouterReasoning(
    @SerialName("enabled")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val enabled: Boolean? = null,

    @SerialName("effort")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val effort: String? = null,

    @SerialName("max_tokens")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxTokens: Int? = null,

    @SerialName("exclude")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val exclude: Boolean? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OpenRouterPlugin(
    @SerialName("id")
    val id: String,

    @SerialName("max_results")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxResults: Int? = null,

    @SerialName("search_prompt")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val searchPrompt: String? = null
)

/**
 * OpenRouter advanced platform configuration options and defaults.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OpenRouterOptions(
    @SerialName("stream")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val stream: Boolean? = DEFAULT_STREAM,

    @SerialName("max_tokens")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxTokens: Int? = DEFAULT_MAX_TOKENS,

    @SerialName("temperature")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val temperature: Float? = DEFAULT_TEMPERATURE,

    @SerialName("top_p")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val topP: Float? = DEFAULT_TOP_P,

    @SerialName("top_k")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val topK: Int? = DEFAULT_TOP_K,

    @SerialName("frequency_penalty")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val frequencyPenalty: Float? = DEFAULT_FREQUENCY_PENALTY,

    @SerialName("presence_penalty")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val presencePenalty: Float? = DEFAULT_PRESENCE_PENALTY,

    @SerialName("repetition_penalty")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val repetitionPenalty: Float? = DEFAULT_REPETITION_PENALTY,

    @SerialName("seed")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val seed: Int? = DEFAULT_SEED,

    @SerialName("provider")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val provider: OpenRouterProviderRouting? = DEFAULT_PROVIDER
) {
    companion object {
        const val DEFAULT_STREAM = true
        const val DEFAULT_MAX_TOKENS = 4096
        const val DEFAULT_TEMPERATURE = 0.2f
        const val DEFAULT_TOP_P = 0.15f
        const val DEFAULT_TOP_K = 30
        const val DEFAULT_FREQUENCY_PENALTY = 0.0f
        const val DEFAULT_PRESENCE_PENALTY = 0.0f
        const val DEFAULT_REPETITION_PENALTY = 1.03f
        const val DEFAULT_SEED = 42
        const val DEFAULT_PROVIDER_SORT = "price"
        const val DEFAULT_PROVIDER_ALLOW_FALLBACKS = true

        val DEFAULT_PROVIDER = OpenRouterProviderRouting(
            sort = DEFAULT_PROVIDER_SORT,
            allowFallbacks = DEFAULT_PROVIDER_ALLOW_FALLBACKS
        )

        fun createDefault(): OpenRouterOptions = OpenRouterOptions(
            stream = DEFAULT_STREAM,
            maxTokens = DEFAULT_MAX_TOKENS,
            temperature = DEFAULT_TEMPERATURE,
            topP = DEFAULT_TOP_P,
            topK = DEFAULT_TOP_K,
            frequencyPenalty = DEFAULT_FREQUENCY_PENALTY,
            presencePenalty = DEFAULT_PRESENCE_PENALTY,
            repetitionPenalty = DEFAULT_REPETITION_PENALTY,
            seed = DEFAULT_SEED,
            provider = DEFAULT_PROVIDER
        )
    }
}
