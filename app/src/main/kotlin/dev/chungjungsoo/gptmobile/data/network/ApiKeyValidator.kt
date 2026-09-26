package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight probe validator for AI Platform API keys.
 * Tests connection and authentication against provider endpoints.
 */
object ApiKeyValidator {

    sealed class ValidationResult {
        data object Idle : ValidationResult()
        data object Validating : ValidationResult()
        data class Success(val message: String) : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }

    suspend fun validate(clientType: ClientType, apiUrl: String, apiKey: String): ValidationResult = withContext(Dispatchers.IO) {
        if (clientType == ClientType.FREE) return@withContext validateFreeProvider(apiUrl)
        if (apiKey.isBlank()) {
            return@withContext ValidationResult.Error("API key cannot be empty")
        }

        val testUrl = when (clientType) {
            ClientType.OPENAI -> "https://api.openai.com/v1/models"
            ClientType.ANTHROPIC -> "https://api.anthropic.com/v1/models"
            ClientType.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            ClientType.GROQ -> "https://api.groq.com/openai/v1/models"
            ClientType.OPENROUTER -> "https://openrouter.ai/api/v1/auth/key"
            ClientType.LLAMA -> {
                val base = apiUrl.trim().trimEnd('/')
                if (base.isNotEmpty()) "$base/models" else "https://api.llama.com/v1/models"
            }
            ClientType.OLLAMA -> {
                val base = apiUrl.trim().trimEnd('/')
                if (base.isNotEmpty()) "$base/api/tags" else "http://localhost:11434/api/tags"
            }
            ClientType.FREE, ClientType.CUSTOM -> {
                val base = apiUrl.trim().trimEnd('/')
                if (base.isNotEmpty()) "$base/models" else return@withContext ValidationResult.Success("Custom URL accepted")
            }
            ClientType.LITERT_LM -> return@withContext ValidationResult.Success("Local model does not require key validation")
        }

        try {
            val url = URL(testUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "GPTMobile/1.0")
                when (clientType) {
                    ClientType.OPENAI, ClientType.GROQ, ClientType.LLAMA, ClientType.CUSTOM -> {
                        setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                    ClientType.ANTHROPIC -> {
                        setRequestProperty("x-api-key", apiKey)
                        setRequestProperty("anthropic-version", "2023-06-01")
                    }
                    ClientType.OPENROUTER -> {
                        setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                    ClientType.GOOGLE, ClientType.OLLAMA, ClientType.LITERT_LM, ClientType.FREE -> {}
                }
            }

            val code = connection.responseCode
            if (code in 200..299) {
                ValidationResult.Success("API Key is valid and active (HTTP $code)")
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() }
                when (code) {
                    401 -> ValidationResult.Error("Unauthorized (401): Invalid API Key")
                    403 -> ValidationResult.Error("Forbidden (403): Key lacks permissions or is disabled")
                    429 -> ValidationResult.Error("Rate Limited (429): Quota exhausted or rate limit hit")
                    else -> ValidationResult.Error("Server returned HTTP $code: ${errorMsg?.take(100) ?: "Check key and configuration"}")
                }
            }
        } catch (e: Exception) {
            ValidationResult.Error("Connection error: ${e.localizedMessage ?: "Unable to reach server"}")
        }
    }
    private suspend fun validateFreeProvider(apiUrl: String): ValidationResult {
        val provider = dev.chungjungsoo.gptmobile.data.model.FreeAiProvider.fromApiUrl(apiUrl)
            ?: return ValidationResult.Error("Choose a Free provider first.")
        if (!provider.isAvailable) return ValidationResult.Error("This provider is awaiting approval. Choose another Free provider.")
        return try {
            FreeAiRequestLimiter.shared.withRequest(provider) {
                val legacy = provider == dev.chungjungsoo.gptmobile.data.model.FreeAiProvider.POLLINATIONS
                val endpoint = if (legacy) "${provider.apiUrl}/Reply%20OK?model=${provider.model}" else provider.chatCompletionsUrl
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 30000
                    requestMethod = if (legacy) "GET" else "POST"
                    setRequestProperty("Accept", if (legacy) "text/plain" else "application/json")
                }
                try {
                    if (!legacy) {
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        val payload = "{\"model\":\"${provider.model}\",\"messages\":[{\"role\":\"user\",\"content\":\"Reply OK.\"}],\"stream\":false,\"max_tokens\":128}"
                        connection.outputStream.use { it.write(payload.toByteArray()) }
                    }
                    val code = connection.responseCode
                    when {
                        code == 429 -> {
                            val wait = FreeAiRequestLimiter.shared.defer(provider, connection.getHeaderField("Retry-After"))
                            ValidationResult.Error("${provider.displayName} is rate limited. Retry in $wait seconds or choose another provider.")
                        }
                        code in 200..299 -> {
                            val body = connection.inputStream.bufferedReader().use { it.readText() }
                            val answer = if (legacy) {
                                body.takeUnless { it.trimStart().startsWith("<") }
                            } else {
                                runCatching {
                                    NetworkClient.openAIJson.decodeFromString<dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk>(body)
                                        .choices.orEmpty().joinToString("") { it.effectiveDelta.content.orEmpty() }
                                }.getOrNull()
                            }
                            if (answer.isNullOrBlank()) {
                                ValidationResult.Error("Connected, but the model returned no answer. Try another Free provider.")
                            } else {
                                ValidationResult.Success("${provider.displayName} answered the test request.")
                            }
                        }
                        else -> ValidationResult.Error("${provider.displayName} is unavailable (HTTP $code). Try later or choose another Free provider.")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ValidationResult.Error(error.message ?: "Could not reach this Free provider.")
        }
    }
}
