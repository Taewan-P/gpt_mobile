package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.model.ClientType

fun getClientTypeDisplayName(clientType: ClientType): String = when (clientType) {
    ClientType.OPENAI -> "OpenAI"
    ClientType.ANTHROPIC -> "Anthropic"
    ClientType.GOOGLE -> "Google"
    ClientType.NVIDIA -> "NVIDIA"
    ClientType.GROQ -> "Groq"
    ClientType.OPENROUTER -> "OpenRouter"
    ClientType.OLLAMA -> "Ollama"
    ClientType.CUSTOM -> "Custom"
    ClientType.FREE -> "Free"
    ClientType.LITERT_LM -> "Local"
    ClientType.LLAMA -> "Llama"
}
