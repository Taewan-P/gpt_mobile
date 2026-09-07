package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.decodeURLPart
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class McpOAuthDiscovery(
    val resource: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val registrationEndpoint: String?,
    val scopes: List<String>
)

@Serializable
data class McpOAuthPending(
    val connectionUid: String? = null,
    val clientId: String,
    val tokenEndpoint: String,
    val resource: String,
    val redirectUri: String,
    val state: String,
    val codeVerifier: String
)

data class McpOAuthStart(
    val authorizationUri: String,
    val pending: McpOAuthPending
)

@Serializable
data class McpOAuthCredential(
    val clientId: String,
    val tokenEndpoint: String,
    val resource: String,
    val accessToken: String,
    val tokenType: String,
    val refreshToken: String? = null,
    val expiresAtEpochSeconds: Long? = null,
    val scope: String? = null
)

class McpOAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class McpOAuthClient internal constructor(
    private val httpClient: HttpClient,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val randomBytes: (Int) -> ByteArray = { size -> ByteArray(size).also(SecureRandom()::nextBytes) },
    private val discoveryTimeoutMillis: Long = DEFAULT_DISCOVERY_TIMEOUT_MILLIS
) {
    @Inject
    constructor(networkClient: NetworkClient) : this(networkClient())

    suspend fun discover(resourceUrl: String, allowCleartext: Boolean): McpOAuthDiscovery {
        val resourceUri = resourceUrl.validatedUrl(allowCleartext, "MCP resource")
        val challenge = discovering("OAuth challenge") {
            httpClient.get(resourceUrl) { accept(ContentType.Application.Json) }
        }
        val advertisedMetadata = if (challenge.status == HttpStatusCode.Unauthorized) {
            challenge.headers[HttpHeaders.WWWAuthenticate]?.resourceMetadataUrl()
        } else {
            null
        }
        discovering("OAuth challenge") { challenge.readBoundedBody() }
        advertisedMetadata?.validatedUrl(allowCleartext, "OAuth protected-resource metadata")?.let { metadataUri ->
            if (!metadataUri.sameOrigin(resourceUri)) {
                throw McpOAuthException("OAuth protected-resource metadata must use the MCP resource same origin.")
            }
        }
        val resourceMetadata = firstMetadata(
            buildList {
                advertisedMetadata?.let(::add)
                add(resourceUri.wellKnown("oauth-protected-resource", resourceUri.path))
                add(resourceUri.wellKnown("oauth-protected-resource", ""))
            }.distinct(),
            allowCleartext,
            "OAuth protected-resource metadata"
        )
        val resource = resourceMetadata.requiredString("resource")
        val discoveredResource = resource.validatedUrl(allowCleartext, "OAuth resource")
        if (!discoveredResource.sameOrigin(resourceUri)) {
            throw McpOAuthException("OAuth resource must use the MCP resource same origin.")
        }
        val authorizationServer = resourceMetadata.requiredStringList("authorization_servers").firstOrNull()
            ?: throw McpOAuthException("OAuth protected-resource metadata has no authorization server.")
        val issuerUri = authorizationServer.validatedUrl(allowCleartext, "OAuth issuer")
        val authorizationMetadata = firstMetadata(
            listOf(
                issuerUri.wellKnown("oauth-authorization-server", issuerUri.path),
                issuerUri.wellKnown("openid-configuration", issuerUri.path),
                authorizationServer.trimEnd('/') + "/.well-known/openid-configuration"
            ).distinct(),
            allowCleartext,
            "OAuth authorization-server metadata"
        )
        if (authorizationMetadata.requiredString("issuer") != authorizationServer) {
            throw McpOAuthException("OAuth authorization-server issuer did not match discovery metadata.")
        }
        if ("S256" !in authorizationMetadata.requiredStringList("code_challenge_methods_supported")) {
            throw McpOAuthException("OAuth authorization server does not advertise PKCE S256.")
        }
        val tokenAuthMethods = authorizationMetadata.stringList("token_endpoint_auth_methods_supported")
        if (tokenAuthMethods.isNotEmpty() && "none" !in tokenAuthMethods) {
            throw McpOAuthException("OAuth authorization server does not support public clients.")
        }
        val authorizationEndpoint = authorizationMetadata.requiredString("authorization_endpoint")
        val tokenEndpoint = authorizationMetadata.requiredString("token_endpoint")
        authorizationEndpoint.validatedUrl(allowCleartext, "OAuth authorization endpoint")
        tokenEndpoint.validatedUrl(allowCleartext, "OAuth token endpoint")
        val registrationEndpoint = authorizationMetadata.string("registration_endpoint")?.also {
            it.validatedUrl(allowCleartext, "OAuth registration endpoint")
        }
        val scopes = resourceMetadata.stringList("scopes_supported")
        if (scopes.size > MAX_SCOPES || scopes.any { it.length > MAX_SCOPE_ITEM_LENGTH || it.any(Char::isISOControl) }) {
            throw McpOAuthException("OAuth protected-resource scopes are invalid.")
        }
        return McpOAuthDiscovery(
            resource = resource,
            authorizationEndpoint = authorizationEndpoint,
            tokenEndpoint = tokenEndpoint,
            registrationEndpoint = registrationEndpoint,
            scopes = scopes
        )
    }

    suspend fun beginAuthorization(
        discovery: McpOAuthDiscovery,
        redirectUri: String,
        suppliedClientId: String?
    ): McpOAuthStart {
        require(redirectUri.isNotBlank()) { "OAuth redirect URI is required." }
        require(redirectUri.length <= MAX_OAUTH_URL_LENGTH) { "OAuth redirect URI is too long." }
        val clientId = suppliedClientId?.takeIf { it.isNotBlank() }
            ?: registerClient(discovery.registrationEndpoint, redirectUri)
        clientId.validateClientId()
        val state = randomToken(32)
        val verifier = randomToken(64)
        val challenge = verifier.sha256Base64Url()
        val authorizationUri = URLBuilder(discovery.authorizationEndpoint).apply {
            parameters.append("response_type", "code")
            parameters.append("client_id", clientId)
            parameters.append("redirect_uri", redirectUri)
            parameters.append("code_challenge", challenge)
            parameters.append("code_challenge_method", "S256")
            parameters.append("state", state)
            parameters.append("resource", discovery.resource)
            if (discovery.scopes.isNotEmpty()) parameters.append("scope", discovery.scopes.joinToString(" "))
        }.buildString()
        if (authorizationUri.length > MAX_CALLBACK_URI_LENGTH) throw McpOAuthException("OAuth authorization URI is too long.")
        return McpOAuthStart(
            authorizationUri = authorizationUri,
            pending = McpOAuthPending(
                clientId = clientId,
                tokenEndpoint = discovery.tokenEndpoint,
                resource = discovery.resource,
                redirectUri = redirectUri,
                state = state,
                codeVerifier = verifier
            )
        )
    }

    suspend fun completeAuthorization(pending: McpOAuthPending, callbackUri: String): McpOAuthCredential {
        if (callbackUri.length > MAX_CALLBACK_URI_LENGTH) throw McpOAuthException("OAuth callback URI is too long.")
        val callback = runCatching { URI(callbackUri) }.getOrNull()
            ?: throw McpOAuthException("OAuth callback URI is invalid.")
        val expectedCallback = runCatching { URI(pending.redirectUri) }.getOrNull()
            ?: throw McpOAuthException("OAuth redirect URI is invalid.")
        if (callback.scheme != expectedCallback.scheme || callback.authority != expectedCallback.authority || callback.path != expectedCallback.path) {
            throw McpOAuthException("OAuth callback did not match the redirect URI.")
        }
        val pairs = callback.rawQuery.orEmpty().formPairs()
        if (pairs.groupingBy { it.first }.eachCount().values.any { it > 1 }) {
            throw McpOAuthException("OAuth callback repeated a parameter.")
        }
        val parameters = pairs.toMap()
        parameters["error"]?.let { throw McpOAuthException("OAuth authorization failed: $it") }
        if (parameters["state"] != pending.state) throw McpOAuthException("OAuth callback state did not match.")
        val code = parameters["code"]?.takeIf { it.isNotBlank() && it.length <= MAX_AUTHORIZATION_CODE_LENGTH }
            ?: throw McpOAuthException("OAuth callback did not include an authorization code.")
        return requestToken(
            endpoint = pending.tokenEndpoint,
            parameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("client_id", pending.clientId)
                append("code", code)
                append("redirect_uri", pending.redirectUri)
                append("code_verifier", pending.codeVerifier)
                append("resource", pending.resource)
            },
            clientId = pending.clientId,
            resource = pending.resource,
            previousRefreshToken = null
        )
    }

    suspend fun refresh(credential: McpOAuthCredential): McpOAuthCredential {
        val refreshToken = credential.refreshToken
            ?: throw McpOAuthException("OAuth credential has no refresh token.")
        return requestToken(
            endpoint = credential.tokenEndpoint,
            parameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("client_id", credential.clientId)
                append("refresh_token", refreshToken)
                append("resource", credential.resource)
            },
            clientId = credential.clientId,
            resource = credential.resource,
            previousRefreshToken = refreshToken
        )
    }

    private suspend fun registerClient(registrationEndpoint: String?, redirectUri: String): String {
        val endpoint = registrationEndpoint
            ?: throw McpOAuthException("OAuth requires a pre-registered client ID or Dynamic Client Registration.")
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("client_name", "GPT Mobile")
                    put("redirect_uris", buildJsonArray { add(JsonPrimitive(redirectUri)) })
                    put("token_endpoint_auth_method", "none")
                    put(
                        "grant_types",
                        buildJsonArray {
                            add(JsonPrimitive("authorization_code"))
                            add(JsonPrimitive("refresh_token"))
                        }
                    )
                    put("response_types", buildJsonArray { add(JsonPrimitive("code")) })
                }.toString()
            )
        }
        return response.successfulJson("OAuth client registration").requiredString("client_id").also(String::validateClientId)
    }

    private suspend fun requestToken(
        endpoint: String,
        parameters: Parameters,
        clientId: String,
        resource: String,
        previousRefreshToken: String?
    ): McpOAuthCredential {
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(parameters))
        }.successfulJson("OAuth token request")
        val tokenType = response.requiredString("token_type")
        if (!tokenType.equals("Bearer", ignoreCase = true)) {
            throw McpOAuthException("OAuth token type is not supported.")
        }
        val expiresIn = response["expires_in"]?.jsonPrimitive?.content?.toLongOrNull()
        if (expiresIn != null && expiresIn !in 0..MAX_EXPIRES_IN_SECONDS) {
            throw McpOAuthException("OAuth token lifetime is invalid.")
        }
        val accessToken = response.requiredString("access_token").validatedToken("access token")
        val refreshToken = (response.string("refresh_token") ?: previousRefreshToken)?.validatedToken("refresh token")
        val scope = response.string("scope")?.also {
            if (it.length > MAX_SCOPE_LENGTH) throw McpOAuthException("OAuth token scope is too long.")
        }
        return McpOAuthCredential(
            clientId = clientId,
            tokenEndpoint = endpoint,
            resource = resource,
            accessToken = accessToken,
            tokenType = tokenType,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresIn?.let { nowEpochSeconds() + it },
            scope = scope
        )
    }

    private suspend fun firstMetadata(
        candidates: List<String>,
        allowCleartext: Boolean,
        label: String
    ): JsonObject {
        candidates.forEach { candidate ->
            candidate.validatedUrl(allowCleartext, label)
            val response = runCatching {
                discovering(label) { httpClient.get(candidate) { accept(ContentType.Application.Json) } }
            }.getOrNull()
            if (response != null && response.status.value in 200..299) {
                return response.successfulJson(label)
            }
            if (response != null) runCatching { response.readBoundedBody() }
        }
        throw McpOAuthException("$label could not be discovered.")
    }

    private suspend fun <T> discovering(label: String, block: suspend () -> T): T = try {
        withTimeout(discoveryTimeoutMillis) { block() }
    } catch (error: TimeoutCancellationException) {
        throw McpOAuthException("$label timed out while discovering.", error)
    }

    private suspend fun HttpResponse.successfulJson(label: String): JsonObject {
        if (status.value !in 200..299) throw McpOAuthException("$label failed with HTTP ${status.value}.")
        return try {
            NetworkClient.json.parseToJsonElement(discovering(label) { readBoundedBody() }.decodeToString()) as? JsonObject
                ?: throw McpOAuthException("$label returned invalid JSON.")
        } catch (error: McpOAuthException) {
            throw error
        } catch (error: Exception) {
            throw McpOAuthException("$label returned invalid JSON.", error)
        }
    }

    private fun randomToken(size: Int): String = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(size))
}

private suspend fun HttpResponse.readBoundedBody(): ByteArray {
    val channel = bodyAsChannel()
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = channel.readAvailable(buffer, 0, buffer.size)
        if (read == -1) break
        if (read == 0) {
            yield()
            continue
        }
        output.write(buffer, 0, read)
        if (output.size() > MAX_OAUTH_RESPONSE_BYTES) throw McpOAuthException("OAuth response is too large.")
    }
    return output.toByteArray()
}

private fun String.validatedUrl(allowCleartext: Boolean, label: String): URI {
    if (length > MAX_OAUTH_URL_LENGTH) throw McpOAuthException("$label URL is too long.")
    val uri = runCatching { URI(this) }.getOrNull() ?: throw McpOAuthException("$label URL is invalid.")
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && (scheme != "http" || !allowCleartext)) {
        throw McpOAuthException("$label must use HTTPS.")
    }
    if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) {
        throw McpOAuthException("$label URL is invalid.")
    }
    return uri
}

private fun URI.wellKnown(name: String, suffixPath: String): String = URI(
    scheme,
    null,
    host,
    port,
    "/.well-known/$name" + suffixPath.takeUnless { it.isBlank() || it == "/" }.orEmpty(),
    null,
    null
).toString()

private fun URI.sameOrigin(other: URI): Boolean = scheme.equals(other.scheme, ignoreCase = true) &&
    host.equals(other.host, ignoreCase = true) &&
    effectivePort() == other.effectivePort()

private fun URI.effectivePort(): Int = when {
    port != -1 -> port
    scheme.equals("https", ignoreCase = true) -> 443
    scheme.equals("http", ignoreCase = true) -> 80
    else -> -1
}

private fun String.resourceMetadataUrl(): String? = RESOURCE_METADATA_PATTERN.find(this)?.groupValues?.get(1)?.ifBlank { null }

private fun JsonObject.requiredString(name: String): String = string(name)
    ?: throw McpOAuthException("OAuth metadata is missing $name.")

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.requiredStringList(name: String): List<String> = stringList(name).takeIf { it.isNotEmpty() }
    ?: throw McpOAuthException("OAuth metadata is missing $name.")

private fun JsonObject.stringList(name: String): List<String> = (this[name] as? JsonArray)
    ?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
    .orEmpty()

private fun String.formPairs(): List<Pair<String, String>> = split('&')
    .filter(String::isNotBlank)
    .map { item ->
        val parts = item.split('=', limit = 2)
        parts[0].decodeURLPart() to parts.getOrElse(1) { "" }.replace('+', ' ').decodeURLPart()
    }

private fun String.validateClientId() {
    if (isBlank() || length > MAX_CLIENT_ID_LENGTH || any(Char::isISOControl)) {
        throw McpOAuthException("OAuth client ID is invalid.")
    }
}

private fun String.validatedToken(label: String): String {
    if (length > MAX_TOKEN_LENGTH || contains('\r') || contains('\n')) {
        throw McpOAuthException("OAuth $label is invalid.")
    }
    return this
}

private fun String.sha256Base64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
    MessageDigest.getInstance("SHA-256").digest(toByteArray())
)

private val RESOURCE_METADATA_PATTERN = Regex("""resource_metadata\s*=\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
private const val MAX_OAUTH_URL_LENGTH = 4 * 1024
private const val MAX_CALLBACK_URI_LENGTH = 16 * 1024
private const val MAX_CLIENT_ID_LENGTH = 1024
private const val MAX_AUTHORIZATION_CODE_LENGTH = 8 * 1024
private const val MAX_TOKEN_LENGTH = 32 * 1024
private const val MAX_SCOPE_LENGTH = 4 * 1024
private const val MAX_SCOPES = 32
private const val MAX_SCOPE_ITEM_LENGTH = 256
private const val MAX_OAUTH_RESPONSE_BYTES = 64 * 1024
private const val MAX_EXPIRES_IN_SECONDS = 10L * 365 * 24 * 60 * 60
private const val DEFAULT_DISCOVERY_TIMEOUT_MILLIS = 10_000L
