package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubToolTest {

    @Test
    fun `workflow dispatch carries user ref and typed inputs and accepts no-content response`() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("/repos/owner/repo/actions/workflows/build.yml/dispatches", request.url.encodedPath)
                assertEquals(io.ktor.http.HttpMethod.Post, request.method)
                assertEquals("Bearer user-token", request.headers[HttpHeaders.Authorization])
                val body = Json.parseToJsonElement((request.body as io.ktor.http.content.TextContent).text).jsonObject
                assertEquals("main", body["ref"]?.toString()?.trim('"'))
                assertEquals("true", body["inputs"]?.jsonObject?.get("debug")?.toString())
                respond("", HttpStatusCode.NoContent)
            }
        )
        try {
            val result = GitHubTool("user-token", client).execute(
                "dispatch",
                buildJsonObject {
                    put("action", "dispatch_workflow")
                    put("owner", "owner")
                    put("repo", "repo")
                    put("workflow_id", "build.yml")
                    put("ref", "main")
                    put("inputs", buildJsonObject { put("debug", true) })
                }
            )
            assertFalse(result.isError)
            assertTrue((result.content as ToolResultContent.Text).text.contains("accepted"))
        } finally {
            client.close()
        }
    }

    @Test
    fun `workflow writes cannot run without credentials and run IDs must be positive`() = runTest {
        val client = HttpClient(MockEngine { error("Invalid requests must not reach GitHub") })
        try {
            val tool = GitHubTool(httpClient = client)
            listOf("cancel_workflow", "rerun_workflow", "rerun_failed_jobs").forEach { action ->
                val result = tool.execute(
                    "write",
                    buildJsonObject {
                        put("action", action)
                        put("owner", "owner")
                        put("repo", "repo")
                        put("run_id", 10)
                    }
                )
                assertTrue(result.isError)
            }
            val result = tool.execute(
                "read",
                buildJsonObject {
                    put("action", "get_workflow_run")
                    put("owner", "owner")
                    put("repo", "repo")
                    put("run_id", -1)
                }
            )
            assertTrue(result.isError)
        } finally {
            client.close()
        }
    }

    @Test
    fun `job log previews stop at the output budget`() = runTest {
        val client = HttpClient(MockEngine { respond("L".repeat(100_000), HttpStatusCode.OK) })
        try {
            val result = GitHubTool(httpClient = client).execute(
                "logs",
                buildJsonObject {
                    put("action", "get_job_logs")
                    put("owner", "owner")
                    put("repo", "repo")
                    put("job_id", 20)
                }
            )
            assertFalse(result.isError)
            val text = (result.content as ToolResultContent.Text).text
            assertTrue(text.length < 33_000)
            assertTrue(text.contains("preview limited"))
        } finally {
            client.close()
        }
    }

    @Test
    fun `definition exposes correct name and required action property`() {
        val tool = GitHubTool()
        assertEquals(BuiltInAgentTool.GITHUB, tool.definition.name)
        assertTrue(tool.definition.description.contains("GitHub", ignoreCase = true))
        val properties = tool.definition.inputSchema["properties"]?.jsonObject
        assertTrue(properties?.containsKey("action") == true)
        assertTrue(properties?.containsKey("query") == true)
        assertTrue(properties?.containsKey("owner") == true)
        assertTrue(properties?.containsKey("repo") == true)
        assertTrue(properties?.containsKey("path") == true)
        assertTrue(properties?.containsKey("issue_number") == true)
    }

    @Test
    fun `missing action returns error result`() = runTest {
        val tool = GitHubTool()
        val result = tool.execute("call-1", buildJsonObject {})
        assertTrue(result.isError)
        assertEquals("call-1", result.callId)
        val text = (result.content as ToolResultContent.Text).text
        assertTrue(text.contains("Missing required parameter: 'action'"))
    }

    @Test
    fun `unknown action returns error result`() = runTest {
        val tool = GitHubTool()
        val result = tool.execute("call-1", buildJsonObject { put("action", "unknown_action") })
        assertTrue(result.isError)
        assertEquals("call-1", result.callId)
        val text = (result.content as ToolResultContent.Text).text
        assertTrue(text.contains("Unknown GitHub action"))
    }

    @Test
    fun `search_repositories parses and formats search response`() = runTest {
        val mockEngine = MockEngine { request ->
            assertTrue(request.url.encodedPath.contains("/search/repositories"))
            respond(
                content = """
                    {
                        "total_count": 1,
                        "items": [
                            {
                                "full_name": "tailscale-signin/GPT_Mobile_AI-improved",
                                "description": "AI Chat Assistant for Android",
                                "stargazers_count": 42,
                                "language": "Kotlin",
                                "html_url": "https://github.com/tailscale-signin/GPT_Mobile_AI-improved"
                            }
                        ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine)
        val tool = GitHubTool(httpClient = client)

        val result = tool.execute(
            "call-search",
            buildJsonObject {
                put("action", "search_repositories")
                put("query", "GPT_Mobile")
            }
        )

        assertFalse(result.isError)
        assertEquals("call-search", result.callId)
        val text = (result.content as ToolResultContent.Text).text
        val json = Json.parseToJsonElement(text).jsonObject
        assertTrue(json.containsKey("items"))
        assertTrue(text.contains("tailscale-signin/GPT_Mobile_AI-improved"))
    }

    @Test
    fun `get_file_contents decodes base64 encoded content`() = runTest {
        val rawContent = "Hello from GitHub agent tool test!"
        val base64Encoded = java.util.Base64.getEncoder().encodeToString(rawContent.toByteArray())
        val mockEngine = MockEngine { request ->
            assertTrue(request.url.encodedPath.contains("/repos/owner/repo/contents/README.md"))
            respond(
                content = """
                    {
                        "name": "README.md",
                        "path": "README.md",
                        "encoding": "base64",
                        "content": "$base64Encoded"
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine)
        val tool = GitHubTool(httpClient = client)

        val result = tool.execute(
            "call-file",
            buildJsonObject {
                put("action", "get_file_contents")
                put("owner", "owner")
                put("repo", "repo")
                put("path", "README.md")
            }
        )

        assertFalse(result.isError)
        assertEquals("call-file", result.callId)
        val text = (result.content as ToolResultContent.Text).text
        assertEquals("Hello from GitHub agent tool test!", text)
    }

    @Test
    fun `get_issue formats issue summary`() = runTest {
        val mockEngine = MockEngine { request ->
            assertTrue(request.url.encodedPath.contains("/repos/owner/repo/issues/101"))
            respond(
                content = """
                    {
                        "number": 101,
                        "title": "Bug in tool execution",
                        "state": "open",
                        "user": { "login": "octocat" },
                        "created_at": "2026-09-14T12:00:00Z",
                        "body": "Detailed description of the issue.",
                        "html_url": "https://github.com/owner/repo/issues/101"
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine)
        val tool = GitHubTool(httpClient = client)

        val result = tool.execute(
            "call-issue",
            buildJsonObject {
                put("action", "get_issue")
                put("owner", "owner")
                put("repo", "repo")
                put("issue_number", 101)
            }
        )

        assertFalse(result.isError)
        assertEquals("call-issue", result.callId)
        val text = (result.content as ToolResultContent.Text).text
        val json = Json.parseToJsonElement(text).jsonObject
        assertEquals("Bug in tool execution", json["title"]?.toString()?.replace("\"", ""))
        assertEquals("open", json["state"]?.toString()?.replace("\"", ""))
        assertEquals("octocat", json["user"]?.toString()?.replace("\"", ""))
    }
}
