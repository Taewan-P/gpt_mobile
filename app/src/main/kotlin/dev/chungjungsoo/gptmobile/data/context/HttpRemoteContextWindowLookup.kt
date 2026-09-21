package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.googleApiRoot
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun interface RemoteContextWindowLookup {
    suspend fun lookup(platform: PlatformV2): Int?
}

class HttpRemoteContextWindowLookup @Inject constructor(
    private val networkClient: NetworkClient
) : RemoteContextWindowLookup {
    override suspend fun lookup(platform: PlatformV2): Int? = try {
        when (platform.compatibleType) {
            ClientType.GOOGLE -> fetchGemini(platform)
            ClientType.OPENROUTER -> fetchOpenRouter(platform)
            ClientType.OLLAMA -> fetchOllama(platform)
            ClientType.GROQ -> fetchGroq(platform)
            ClientType.ANTHROPIC -> fetchAnthropic(platform)
            else -> null
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun fetchGemini(platform: PlatformV2): Int? {
        val endpoint = googleApiRoot(platform.apiUrl) + "/v1beta/models/" + encodedModel(platform.model)
        val body = get(endpoint) { builder ->
            builder.header("x-goog-api-key", platform.token ?: "")
        } ?: return null
        return RemoteContextWindowParser.geminiInputTokenLimit(body)
    }

    private suspend fun fetchOpenRouter(platform: PlatformV2): Int? {
        val directBody = get(modelsUrl(platform, platform.model)) { builder ->
            platform.token?.let { builder.bearerAuth(it) }
        }
        RemoteContextWindowParser.openRouterContextLength(directBody.orEmpty(), platform.model)?.let { return it }
        val listBody = get(modelsUrl(platform)) { builder ->
            platform.token?.let { builder.bearerAuth(it) }
        } ?: return null
        return RemoteContextWindowParser.openRouterContextLength(listBody, platform.model)
    }

    private suspend fun fetchGroq(platform: PlatformV2): Int? {
        val directBody = get(modelsUrl(platform, platform.model)) { builder ->
            platform.token?.let { builder.bearerAuth(it) }
        }
        RemoteContextWindowParser.groqContextWindow(directBody.orEmpty(), platform.model)?.let { return it }
        val listBody = get(modelsUrl(platform)) { builder ->
            platform.token?.let { builder.bearerAuth(it) }
        } ?: return null
        return RemoteContextWindowParser.groqContextWindow(listBody, platform.model)
    }

    private suspend fun fetchAnthropic(platform: PlatformV2): Int? {
        val body = get(modelsUrl(platform, platform.model)) { builder ->
            builder.header("x-api-key", platform.token ?: "")
            builder.header("anthropic-version", "2023-06-01")
        } ?: return null
        return RemoteContextWindowParser.anthropicMaxInputTokens(body, platform.model)
    }

    private suspend fun fetchOllama(platform: PlatformV2): Int? {
        val root = platform.apiUrl.trimEnd('/').removeSuffix("/v1")
        val endpoint = root + "/api/show"
        val responseBody = networkClient().preparePost(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("name", platform.model) }.toString())
        }.execute { response ->
            if (!response.status.isSuccess()) null else response.body<String>()
        }
        return responseBody?.let(RemoteContextWindowParser::ollamaContextLength)
    }

    private fun modelsUrl(platform: PlatformV2, model: String? = null): String {
        val root = platform.apiUrl.trimEnd('/') + "/models"
        return if (model == null) root else root + "/" + encodedModel(model)
    }

    private fun encodedModel(model: String): String = model.split("/").joinToString("/") { part ->
        java.net.URLEncoder.encode(part, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private suspend fun get(
        endpoint: String,
        headers: (HttpRequestBuilder) -> Unit
    ): String? = networkClient().prepareGet(endpoint) {
        headers(this)
    }.execute { response ->
        if (!response.status.isSuccess()) null else response.body<String>()
    }
}
