package dev.chungjungsoo.gptmobile.data.agent.tool

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.decodeURLPart
import io.ktor.serialization.kotlinx.json.json
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpOAuthClientTest {
    @Test
    fun `callback filter accepts only MCP OAuth redirect URIs`() {
        assertTrue(isMcpOAuthCallbackUri("dev.chungjungsoo.gptmobile://oauth/mcp/connection-1?code=code&state=state"))
        assertFalse(isMcpOAuthCallbackUri("dev.chungjungsoo.gptmobile://oauth/other/connection-1"))
        assertFalse(isMcpOAuthCallbackUri("https://example.com/mcp/connection-1"))
        assertFalse(isMcpOAuthCallbackUri(null))
    }

    @Test
    fun `discovers registers completes PKCE and refreshes public client`() = runBlocking {
        OAuthFixtureServer().use { server ->
            val httpClient = HttpClient(CIO) {
                expectSuccess = false
                install(ContentNegotiation) { json(JSON) }
            }
            val client = McpOAuthClient(httpClient, nowEpochSeconds = { 1_000 })
            val discovery = client.discover(server.mcpUrl, allowCleartext = true)
            val started = client.beginAuthorization(
                discovery = discovery,
                redirectUri = "dev.chungjungsoo.gptmobile://oauth/mcp",
                suppliedClientId = null
            )
            val authorization = URI(started.authorizationUri).rawQuery.formValues()

            assertEquals(server.mcpUrl, discovery.resource)
            assertEquals("fixture-client", started.pending.clientId)
            assertEquals("S256", authorization["code_challenge_method"])
            assertEquals(server.mcpUrl, authorization["resource"])
            assertEquals(started.pending.state, authorization["state"])
            assertNotEquals(started.pending.codeVerifier, authorization["code_challenge"])

            val tokens = client.completeAuthorization(
                started.pending,
                "dev.chungjungsoo.gptmobile://oauth/mcp?code=auth-code&state=${started.pending.state}"
            )
            val refreshed = client.refresh(tokens)

            assertEquals("access-1", tokens.accessToken)
            assertEquals("refresh-1", tokens.refreshToken)
            assertEquals(1_120L, tokens.expiresAtEpochSeconds)
            assertEquals("access-2", refreshed.accessToken)
            assertEquals("refresh-2", refreshed.refreshToken)
            assertTrue(server.tokenForms.single { it["grant_type"] == "authorization_code" }["code_verifier"]!!.length >= 43)
            assertEquals(server.mcpUrl, server.tokenForms.last()["resource"])
            httpClient.close()
        }
    }

    @Test
    fun `callback with wrong state is rejected before token request`() = runBlocking {
        OAuthFixtureServer().use { server ->
            val httpClient = HttpClient(CIO) { expectSuccess = false }
            val client = McpOAuthClient(httpClient)
            val started = client.beginAuthorization(
                client.discover(server.mcpUrl, allowCleartext = true),
                "dev.chungjungsoo.gptmobile://oauth/mcp",
                "manual-client"
            )

            val error = runCatching {
                client.completeAuthorization(
                    started.pending,
                    "dev.chungjungsoo.gptmobile://oauth/mcp?code=auth-code&state=wrong"
                )
            }.exceptionOrNull()

            assertTrue(error is McpOAuthException)
            assertTrue(server.tokenForms.isEmpty())
            httpClient.close()
        }
    }

    @Test
    fun `callback for another redirect path is rejected before token request`() = runBlocking {
        OAuthFixtureServer().use { server ->
            val httpClient = HttpClient(CIO) { expectSuccess = false }
            val client = McpOAuthClient(httpClient)
            val started = client.beginAuthorization(
                client.discover(server.mcpUrl, allowCleartext = true),
                "dev.chungjungsoo.gptmobile://oauth/mcp/connection-1",
                "manual-client"
            )

            val error = runCatching {
                client.completeAuthorization(
                    started.pending,
                    "dev.chungjungsoo.gptmobile://oauth/mcp/connection-2?code=auth-code&state=${started.pending.state}"
                )
            }.exceptionOrNull()

            assertTrue(error is McpOAuthException)
            assertTrue(server.tokenForms.isEmpty())
            httpClient.close()
        }
    }

    @Test
    fun `challenge discovery times out instead of hanging authorization`() = runBlocking {
        HangingChallengeServer().use { server ->
            val httpClient = HttpClient(CIO) { expectSuccess = false }
            val client = McpOAuthClient(httpClient, discoveryTimeoutMillis = 50)

            val error = runCatching { client.discover(server.mcpUrl, allowCleartext = true) }.exceptionOrNull()

            assertTrue(error is McpOAuthException)
            assertTrue(error!!.message!!.contains("timed out"))
            httpClient.close()
        }
    }

    @Test
    fun `advertised resource metadata must stay on the MCP resource origin`() = runBlocking {
        OAuthFixtureServer().use { metadataServer ->
            CrossOriginMetadataServer(metadataServer).use { server ->
                val httpClient = HttpClient(CIO) { expectSuccess = false }
                val client = McpOAuthClient(httpClient)

                val error = runCatching { client.discover(server.mcpUrl, allowCleartext = true) }.exceptionOrNull()

                assertTrue(error is McpOAuthException)
                assertTrue(error!!.message!!.contains("same origin"))
                httpClient.close()
            }
        }
    }

    internal class OAuthFixtureServer : AutoCloseable {
        val tokenForms = CopyOnWriteArrayList<Map<String, String>>()
        val protectedResourceRequests = AtomicInteger()
        private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        private val baseUrl = "http://127.0.0.1:${server.address.port}"

        init {
            server.createContext("/mcp", ::protectedResource)
            server.createContext("/.well-known/oauth-protected-resource/mcp", ::resourceMetadata)
            server.createContext("/.well-known/oauth-authorization-server/issuer", ::authorizationMetadata)
            server.createContext("/register", ::register)
            server.createContext("/token", ::token)
            server.start()
        }
        val mcpUrl: String get() = "$baseUrl/mcp"

        private fun protectedResource(exchange: HttpExchange) {
            protectedResourceRequests.incrementAndGet()
            exchange.responseHeaders.add(
                "WWW-Authenticate",
                "Bearer resource_metadata=\"$baseUrl/.well-known/oauth-protected-resource/mcp\""
            )
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
        }

        private fun resourceMetadata(exchange: HttpExchange) = respond(
            exchange,
            """{"resource":"$mcpUrl","authorization_servers":["$baseUrl/issuer"],"scopes_supported":["mcp:tools"]}"""
        )

        private fun authorizationMetadata(exchange: HttpExchange) = respond(
            exchange,
            """{"issuer":"$baseUrl/issuer","authorization_endpoint":"$baseUrl/authorize","token_endpoint":"$baseUrl/token","registration_endpoint":"$baseUrl/register","code_challenge_methods_supported":["S256"],"token_endpoint_auth_methods_supported":["none"]}"""
        )

        private fun register(exchange: HttpExchange) = respond(exchange, """{"client_id":"fixture-client"}""")

        private fun token(exchange: HttpExchange) {
            val form = exchange.requestBody.bufferedReader().readText().formValues()
            tokenForms += form
            val body = if (form["grant_type"] == "refresh_token") {
                """{"access_token":"access-2","token_type":"Bearer","refresh_token":"refresh-2","expires_in":120}"""
            } else {
                """{"access_token":"access-1","token_type":"Bearer","refresh_token":"refresh-1","expires_in":120,"scope":"mcp:tools"}"""
            }
            respond(exchange, body)
        }

        private fun respond(exchange: HttpExchange, body: String) {
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }

        override fun close() {
            server.stop(0)
        }
    }

    private class HangingChallengeServer : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/mcp") { exchange ->
                Thread.sleep(2_000)
                exchange.sendResponseHeaders(401, -1)
                exchange.close()
            }
            start()
        }
        val mcpUrl: String = "http://127.0.0.1:${server.address.port}/mcp"

        override fun close() {
            server.stop(0)
        }
    }

    private class CrossOriginMetadataServer(
        metadataServer: OAuthFixtureServer
    ) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/mcp") { exchange ->
                exchange.responseHeaders.add(
                    "WWW-Authenticate",
                    "Bearer resource_metadata=\"${metadataServer.mcpUrl.replace("/mcp", "/.well-known/oauth-protected-resource/mcp")}\""
                )
                exchange.sendResponseHeaders(401, -1)
                exchange.close()
            }
            start()
        }
        val mcpUrl: String = "http://127.0.0.1:${server.address.port}/mcp"

        override fun close() {
            server.stop(0)
        }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

private fun String.formValues(): Map<String, String> = split('&')
    .filter { it.isNotBlank() }
    .associate { item ->
        val parts = item.split('=', limit = 2)
        parts[0].decodeURLPart() to parts.getOrElse(1) { "" }.decodeURLPart()
    }
