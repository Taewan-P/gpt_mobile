package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class ProfileModelOption(val id: String, val name: String)

/** Uses the profile's resolved provider URL and credential, including custom deployments. */
class ProfileModelCatalog(private val client: OkHttpClient = sharedClient) {
    suspend fun load(profile: PlatformV2): List<ProfileModelOption> = withContext(Dispatchers.IO) {
        val type = profile.compatibleType
        if (type == ClientType.FREE) {
            val provider = FreeAiProvider.requireFor(profile)
            return@withContext listOf(ProfileModelOption(provider.model, provider.displayName))
        }
        require(type != ClientType.LITERT_LM) { "Select a downloaded model from the local model library." }
        val base = profile.apiUrl.trim().ifEmpty {
            when (type) {
                ClientType.OPENAI -> "https://api.openai.com/v1"
                ClientType.ANTHROPIC -> "https://api.anthropic.com/v1"
                ClientType.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta"
                ClientType.NVIDIA -> "https://integrate.api.nvidia.com/v1"
                ClientType.GROQ -> "https://api.groq.com/openai/v1"
                ClientType.OPENROUTER -> "https://openrouter.ai/api/v1"
                else -> error("Configure this provider's API address first.")
            }
        }.trimEnd('/')
        val root = base.removeSuffix("/chat/completions").removeSuffix("/responses").removeSuffix("/messages")
        val normalizedRoot = if (type == ClientType.GOOGLE && !Regex("/v[0-9]+(?:beta[0-9]*)?$").containsMatchIn(root)) "$root/v1beta" else root
        val endpoint = if (type == ClientType.OLLAMA) root.removeSuffix("/api").removeSuffix("/v1") + "/api/tags" else "$normalizedRoot/models"
        val models = mutableListOf<ProfileModelOption>()
        var cursor: String? = null
        val seenCursors = mutableSetOf<String>()
        do {
            val url = endpoint.toHttpUrl().newBuilder().apply {
                if (type == ClientType.GOOGLE) {
                    addQueryParameter("pageSize", "100")
                    cursor?.let { addQueryParameter("pageToken", it) }
                }
                if (type == ClientType.ANTHROPIC) {
                    addQueryParameter("limit", "100")
                    cursor?.let { addQueryParameter("after_id", it) }
                }
            }.build()
            val request = Request.Builder().url(url).apply {
                val token = dev.chungjungsoo.gptmobile.data.network.ApiCredentialRotator.parseKeys(profile.token).firstOrNull().orEmpty()
                when (type) {
                    ClientType.GOOGLE -> if (token.isNotEmpty()) header("x-goog-api-key", token)
                    ClientType.ANTHROPIC -> {
                        header("anthropic-version", "2023-06-01")
                        if (token.isNotEmpty()) header("x-api-key", token)
                    }
                    else -> if (token.isNotEmpty()) header("Authorization", "Bearer $token")
                }
            }.build()
            val payload = client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Model discovery returned HTTP ${response.code}. Check the provider address and credentials, or enter a model ID manually." }
                Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            }
            models += parseModels(payload, type)
            cursor = when (type) {
                ClientType.GOOGLE -> payload["nextPageToken"]?.jsonPrimitive?.contentOrNull
                ClientType.ANTHROPIC -> if (payload["has_more"]?.jsonPrimitive?.booleanOrNull == true) {
                    payload["last_id"]?.jsonPrimitive?.contentOrNull
                } else {
                    null
                }
                else -> null
            }?.takeIf { it.isNotBlank() }
        } while (cursor != null && seenCursors.add(cursor) && seenCursors.size < 30)
        models.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    internal fun parseModels(payload: JsonObject, type: ClientType): List<ProfileModelOption> {
        val entries = (payload[if (type in setOf(ClientType.GOOGLE, ClientType.OLLAMA)) "models" else "data"] as? JsonArray).orEmpty()
        return entries.mapNotNull { entry ->
            val item = entry as? JsonObject ?: return@mapNotNull null
            if (type == ClientType.GOOGLE) {
                val methods = item["supportedGenerationMethods"] as? JsonArray
                if (methods != null && methods.none { it.jsonPrimitive.contentOrNull == "generateContent" }) return@mapNotNull null
            }
            val id = item[if (type in setOf(ClientType.GOOGLE, ClientType.OLLAMA)) "name" else "id"]?.jsonPrimitive?.contentOrNull
                ?.removePrefix("models/")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = (item["displayName"] ?: item["display_name"] ?: item["name"])?.jsonPrimitive?.contentOrNull ?: id
            ProfileModelOption(id, name)
        }
    }

    private companion object {
        val sharedClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    }
}
