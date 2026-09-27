package dev.chungjungsoo.gptmobile.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool

@Singleton
class NetworkClient @Inject constructor(
    private val httpEngine: HttpClientEngineFactory<*>
) {

    private val client by lazy {
        HttpClient(httpEngine) {
            expectSuccess = false

            // Optimize CIO engine with TCP keep-alive and connection limits when CIO is used
            if (httpEngine == CIO) {
                engine {
                    (this as? CIOEngineConfig)?.apply {
                        maxConnectionsCount = 1000
                        endpoint {
                            maxConnectionsPerRoute = 100
                            pipelineMaxSize = 20
                            keepAliveTime = 300_000L
                            connectTimeout = 60_000L
                            connectAttempts = 5
                        }
                    }
                }
            } else if (httpEngine == OkHttp) {
                engine {
                    (this as? OkHttpConfig)?.config {
                        retryOnConnectionFailure(true)
                        connectTimeout(60, TimeUnit.SECONDS)
                        readTimeout(5, TimeUnit.MINUTES)
                        writeTimeout(5, TimeUnit.MINUTES)
                        callTimeout(0, TimeUnit.MILLISECONDS)
                        pingInterval(30, TimeUnit.SECONDS)
                        connectionPool(ConnectionPool(32, 10, TimeUnit.MINUTES))
                    }
                }
            }

            install(ContentNegotiation) {
                json(json)
            }

            install(SSE)

            install(HttpTimeout) {
                // Non-streaming calls get a generous ceiling. Streaming requests override
                // this with an unlimited request deadline and a five-minute idle timeout.
                requestTimeoutMillis = 900_000L
                connectTimeoutMillis = 60_000L
                socketTimeoutMillis = 300_000L
            }

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        dev.chungjungsoo.gptmobile.data.diagnostics.AppLogRecorder.record("Network", message)
                        if (diagnosticsEnabled && !dev.chungjungsoo.gptmobile.data.diagnostics.AppLogRecorder.enabled.value) Logger.DEFAULT.log(dev.chungjungsoo.gptmobile.data.diagnostics.redactLogMessage(message))
                    }
                }
                level = LogLevel.HEADERS
                filter { diagnosticsEnabled || dev.chungjungsoo.gptmobile.data.diagnostics.AppLogRecorder.enabled.value }
                sanitizeHeader { header -> isSensitiveHeader(header) }
            }

            install(DefaultRequest) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
        }
    }

    operator fun invoke(): HttpClient = client

    companion object {
        @Volatile var diagnosticsEnabled: Boolean = false

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

        internal fun resolveNetworkLogLevel(): LogLevel = if (dev.chungjungsoo.gptmobile.BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE

        internal fun isSensitiveHeader(header: String): Boolean =
            header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                header.equals(HttpHeaders.ProxyAuthorization, ignoreCase = true) ||
                header.equals(HttpHeaders.Cookie, ignoreCase = true) ||
                header.equals(HttpHeaders.SetCookie, ignoreCase = true) ||
                header.equals("x-goog-api-key", ignoreCase = true) ||
                header.equals("x-api-key", ignoreCase = true) ||
                header.equals("x-subscription-token", ignoreCase = true) ||
                header.equals("Mcp-Session-Id", ignoreCase = true)
    }
}
