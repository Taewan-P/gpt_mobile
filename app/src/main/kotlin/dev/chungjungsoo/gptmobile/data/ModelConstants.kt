package dev.chungjungsoo.gptmobile.data

import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType

object ModelConstants {
    // LinkedHashSet should be used to guarantee item order
    const val OPENAI_DEFAULT_MODEL = "gpt-5.6"
    const val ANTHROPIC_DEFAULT_MODEL = "claude-sonnet-5"
    const val GOOGLE_DEFAULT_MODEL = "gemini-3.7-flash"
    const val GROQ_DEFAULT_MODEL = "openai/gpt-oss-120b"
    const val OLLAMA_DEFAULT_MODEL = "gpt-oss"
    const val LLAMA_DEFAULT_MODEL = "llama3.3"

    // OpenRouter has no unsuffixed alias; only the explicit tier slugs are served.
    const val OPENROUTER_DEFAULT_MODEL = "openai/gpt-5.6-sol"

    val openaiModels = linkedSetOf(
        OPENAI_DEFAULT_MODEL,
        "gpt-5.4",
        "gpt-5.4-mini",
        "gpt-5.4-nano",
        "o3-mini",
        "o1"
    )
    val anthropicModels = linkedSetOf(
        ANTHROPIC_DEFAULT_MODEL,
        "claude-opus-5",
        "claude-sonnet-4-6",
        "claude-haiku-4-5-20251001",
        "claude-3-7-sonnet-20250219"
    )
    val googleModels = linkedSetOf(
        GOOGLE_DEFAULT_MODEL,
        "gemini-3.1-pro-preview",
        "gemini-3-flash-preview",
        "gemini-2.5-flash",
        "gemini-2.0-flash-exp"
    )
    val groqModels = linkedSetOf(
        GROQ_DEFAULT_MODEL,
        "openai/gpt-oss-20b",
        "qwen/qwen3.6-27b",
        "llama-3.3-70b-versatile",
        "deepseek-r1-distill-llama-70b"
    )
    val ollamaModels = linkedSetOf(OLLAMA_DEFAULT_MODEL)
    val llamaModels = linkedSetOf(
        LLAMA_DEFAULT_MODEL,
        "llama3.2",
        "llama3.1",
        "llama3"
    )

    const val OPENAI_API_URL = "https://api.openai.com/v1/"
    const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/"
    const val GOOGLE_API_URL = "https://generativelanguage.googleapis.com/"
    const val GROQ_API_URL = "https://api.groq.com/openai/v1/"
    const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/"
    const val OLLAMA_API_URL = "http://localhost:11434/v1/"
    const val LLAMA_API_URL = "http://localhost:11434/v1/"

    fun getDefaultAPIUrl(apiType: ApiType) = when (apiType) {
        ApiType.OPENAI -> OPENAI_API_URL
        ApiType.ANTHROPIC -> ANTHROPIC_API_URL
        ApiType.GOOGLE -> GOOGLE_API_URL
        ApiType.GROQ -> GROQ_API_URL
        ApiType.OLLAMA -> OLLAMA_API_URL
    }

    fun normalizeLegacyAPIUrl(apiUrl: String): String = when (apiUrl.trim()) {
        "https://api.openai.com", "https://api.openai.com/" -> OPENAI_API_URL
        "https://api.anthropic.com", "https://api.anthropic.com/" -> ANTHROPIC_API_URL
        "https://generativelanguage.googleapis.com", "https://generativelanguage.googleapis.com/" -> GOOGLE_API_URL
        "https://api.groq.com/openai", "https://api.groq.com/openai/" -> GROQ_API_URL
        "https://openrouter.ai/api", "https://openrouter.ai/api/" -> OPENROUTER_API_URL
        "http://localhost:11434", "http://localhost:11434/" -> OLLAMA_API_URL
        else -> apiUrl
    }

    fun defaultPlatformName(clientType: ClientType): String = when (clientType) {
        ClientType.OPENAI -> "OpenAI"
        ClientType.ANTHROPIC -> "Anthropic"
        ClientType.GOOGLE -> "Google"
        ClientType.GROQ -> "Groq"
        ClientType.OLLAMA -> "Ollama"
        ClientType.OPENROUTER -> "OpenRouter"
        ClientType.FREE -> "Free Models"
        ClientType.CUSTOM -> ""
        ClientType.LITERT_LM -> "Local"
        ClientType.LLAMA -> "Llama"
    }

    fun defaultApiUrl(clientType: ClientType): String = when (clientType) {
        ClientType.OPENAI -> OPENAI_API_URL
        ClientType.ANTHROPIC -> ANTHROPIC_API_URL
        ClientType.GOOGLE -> GOOGLE_API_URL
        ClientType.GROQ -> GROQ_API_URL
        ClientType.OLLAMA -> OLLAMA_API_URL
        ClientType.OPENROUTER -> OPENROUTER_API_URL
        ClientType.FREE -> dev.chungjungsoo.gptmobile.data.model.FreeAiProvider.default.apiUrl
        ClientType.CUSTOM -> ""
        ClientType.LITERT_LM -> ""
        ClientType.LLAMA -> LLAMA_API_URL
    }

    fun defaultModel(clientType: ClientType): String = when (clientType) {
        ClientType.OPENAI -> OPENAI_DEFAULT_MODEL
        ClientType.ANTHROPIC -> ANTHROPIC_DEFAULT_MODEL
        ClientType.GOOGLE -> GOOGLE_DEFAULT_MODEL
        ClientType.GROQ -> GROQ_DEFAULT_MODEL
        ClientType.OLLAMA -> OLLAMA_DEFAULT_MODEL
        ClientType.OPENROUTER -> OPENROUTER_DEFAULT_MODEL
        ClientType.FREE -> dev.chungjungsoo.gptmobile.data.model.FreeAiProvider.default.model
        ClientType.CUSTOM -> ""
        ClientType.LITERT_LM -> ""
        ClientType.LLAMA -> LLAMA_DEFAULT_MODEL
    }

    const val ANTHROPIC_MAXIMUM_TOKEN = 8192

    const val OPENAI_PROMPT =
        "You are a helpful, clever, and very friendly assistant. " +
            "You are familiar with various languages in the world. " +
            "You are to answer my questions precisely. "

    const val DEFAULT_PROMPT = "Your task is to answer my questions precisely."

    const val CHAT_TITLE_GENERATE_PROMPT =
        "Create a title that summarizes the chat. " +
            "The output must match the language that the user and the opponent is using, and should be less than 50 letters. " +
            "The output should only include the sentence in plain text without bullets or double asterisks. Do not use markdown syntax.\n" +
            "[Chat Content]\n"
}
