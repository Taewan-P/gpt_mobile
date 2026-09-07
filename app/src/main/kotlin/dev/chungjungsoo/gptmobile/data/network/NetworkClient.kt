package dev.chungjungsoo.gptmobile.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class NetworkClient @Inject constructor(
    private val httpEngine: HttpClientEngineFactory<*>
) {

    private val client by lazy {
        HttpClient(httpEngine) {
            expectSuccess = false

            install(ContentNegotiation) {
                json(json)
            }

            install(SSE)

            install(HttpTimeout)

            install(Logging) {
                logger = Logger.DEFAULT
                level = resolveNetworkLogLevel()
                sanitizeHeader { header -> isSensitiveHeader(header) }
            }

            install(DefaultRequest) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
        }
    }

    operator fun invoke(): HttpClient = client

    companion object {
        // Default JSON config (used for most APIs)
        val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
            allowSpecialFloatingPointValues = true
            useArrayPolymorphism = false
            encodeDefaults = true
            explicitNulls = false
        }

        // OpenAI-specific JSON config with "type" discriminator for MessageContent
        val openAIJson = Json {
            isLenient = true
            ignoreUnknownKeys = true
            allowSpecialFloatingPointValues = true
            useArrayPolymorphism = false
            classDiscriminator = "type"
            encodeDefaults = true
            explicitNulls = false
        }

        internal fun resolveNetworkLogLevel(): LogLevel = LogLevel.HEADERS

        internal fun isSensitiveHeader(header: String): Boolean = header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
            header.equals("x-goog-api-key", ignoreCase = true) ||
            header.equals("x-api-key", ignoreCase = true) ||
            header.equals("Mcp-Session-Id", ignoreCase = true)
    }
}
