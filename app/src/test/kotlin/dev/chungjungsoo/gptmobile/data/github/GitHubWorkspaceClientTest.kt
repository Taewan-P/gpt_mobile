package dev.chungjungsoo.gptmobile.data.github

import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.agent.tool.GitHubTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubWorkspaceClientTest {
    private val head = "a".repeat(40)

    private fun changeSet(branch: String = "feature/code"): JsonObject = buildJsonObject {
        put("owner", "owner")
        put("repo", "repo")
        put("branch", branch)
        put("expected_head_sha", head)
        put("message", "Update script and README together")
        put(
            "files",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("path", "run.sh")
                        put("content", "#!/bin/sh\necho updated")
                    }
                )
                add(
                    buildJsonObject {
                        put("path", "README.md")
                        put("content", "Updated docs")
                    }
                )
            }
        )
    }

    @Test
    fun `multi file commit preserves base tree executable mode parent and non force update`() = runTest {
        var step = 0
        val http = HttpClient(
            MockEngine { request ->
                val body = (request.body as? TextContent)?.text?.let { Json.parseToJsonElement(it).jsonObject }
                val response = when (step++) {
                    0 -> {
                        assertEquals(HttpMethod.Get, request.method)
                        """{"default_branch":"main"}"""
                    }
                    1 -> """{"object":{"sha":"$head"}}"""
                    2 -> """{"tree":{"sha":"base-tree"}}"""
                    3 -> """{"truncated":false,"tree":[{"path":"run.sh","type":"blob","mode":"100755"}]}"""
                    4 -> {
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("base-tree", body!!["base_tree"]!!.jsonPrimitive.content)
                        val entries = body["tree"]!!.jsonArray
                        assertEquals(2, entries.size)
                        assertEquals("100755", entries[0].jsonObject["mode"]!!.jsonPrimitive.content)
                        assertEquals("100644", entries[1].jsonObject["mode"]!!.jsonPrimitive.content)
                        """{"sha":"new-tree"}"""
                    }
                    5 -> {
                        assertEquals(head, body!!["parents"]!!.jsonArray[0].jsonPrimitive.content)
                        assertEquals("new-tree", body["tree"]!!.jsonPrimitive.content)
                        """{"sha":"new-commit"}"""
                    }
                    6 -> {
                        assertEquals(HttpMethod.Patch, request.method)
                        assertEquals("false", body!!["force"].toString())
                        assertEquals("new-commit", body["sha"]!!.jsonPrimitive.content)
                        "{}"
                    }
                    else -> error("Unexpected API call")
                }
                respond(response)
            }
        )
        try {
            val result = GitHubWorkspaceClient("token", http).execute("commit_files", changeSet()).jsonObject
            assertEquals("new-commit", result["sha"]!!.jsonPrimitive.content)
            assertEquals(7, step)
        } finally {
            http.close()
        }
    }

    @Test
    fun `stale branch rejects changes before creating git objects`() = runTest {
        var requests = 0
        val http = HttpClient(
            MockEngine { request ->
                assertEquals(HttpMethod.Get, request.method)
                respond(if (requests++ == 0) """{"default_branch":"main"}""" else """{"object":{"sha":"${"b".repeat(40)}"}}""")
            }
        )
        try {
            val error = runCatching { GitHubWorkspaceClient("token", http).execute("commit_files", changeSet()) }.exceptionOrNull()
            assertTrue(error?.message?.contains("Branch changed") == true)
            assertEquals(2, requests)
        } finally {
            http.close()
        }
    }

    @Test
    fun `default branch writes and unauthenticated writes are refused`() = runTest {
        val http = HttpClient(
            MockEngine { request ->
                assertEquals(HttpMethod.Get, request.method)
                respond("""{"default_branch":"main"}""")
            }
        )
        try {
            assertTrue(runCatching { GitHubWorkspaceClient("token", http).execute("commit_files", changeSet("main")) }.isFailure)
            assertTrue(runCatching { GitHubWorkspaceClient("", http).execute("commit_files", changeSet()) }.isFailure)
        } finally {
            http.close()
        }
    }

    @Test
    fun `line reads use selected context and keep remaining lines discoverable`() = runTest {
        val content = Base64.getEncoder().encodeToString("one\ntwo\nthree\nfour".toByteArray())
        val http = HttpClient(
            MockEngine { request ->
                assertEquals("feature/read", request.url.parameters["ref"])
                assertTrue(request.url.encodedPath.endsWith("/contents/src/file%23.kt"))
                respond("""{"type":"file","path":"src/file#.kt","encoding":"base64","sha":"blob","content":"$content"}""")
            }
        )
        try {
            val tool = GitHubTool("token", http, repositoryContext = GitHubRepositoryContext("owner", "repo", "feature/read"))
            val result = tool.execute(
                "read",
                buildJsonObject {
                    put("action", "read_code")
                    put("path", "src/file#.kt")
                    put("start_line", 2)
                    put("end_line", 3)
                }
            )
            assertFalse(result.isError)
            val json = Json.parseToJsonElement((result.content as ToolResultContent.Text).text).jsonObject
            assertEquals("two\nthree", json["content"]!!.jsonPrimitive.content)
            assertEquals("true", json["has_more"].toString())
            assertEquals("4", json["total_lines"].toString())
        } finally {
            http.close()
        }
    }

    @Test
    fun `invalid paths cannot reach GitHub`() = runTest {
        val http = HttpClient(MockEngine { error("Invalid path reached network") })
        try {
            listOf("../secret", "src/../secret", "/absolute", "src//file", "src\\file").forEach { path ->
                assertTrue(
                    runCatching {
                        GitHubWorkspaceClient("token", http).execute(
                            "read_code",
                            buildJsonObject {
                                put("owner", "owner")
                                put("repo", "repo")
                                put("path", path)
                            }
                        )
                    }.isFailure
                )
            }
        } finally {
            http.close()
        }
    }

    @Test
    fun `permission failures return actionable errors without leaking response bodies`() = runTest {
        val http = HttpClient(MockEngine { respond("sensitive response details", HttpStatusCode.Forbidden) })
        try {
            val error = runCatching { GitHubWorkspaceClient("token", http).execute("get_account", buildJsonObject {}) }.exceptionOrNull()
            assertTrue(error?.message?.contains("permissions") == true)
            assertFalse(error?.message?.contains("sensitive") == true)
        } finally {
            http.close()
        }
    }

    @Test
    fun `directory paging returns complete entries and continuation without oversized payloads`() = runTest {
        val response = buildJsonArray {
            repeat(65) { index ->
                add(
                    buildJsonObject {
                        put("name", "file$index")
                        put("path", "file$index")
                        put("type", "file")
                    }
                )
            }
        }.toString()
        val http = HttpClient(MockEngine { respond(response) })
        try {
            val client = GitHubWorkspaceClient("token", http)
            val result = client.execute(
                "browse_files",
                buildJsonObject {
                    put("owner", "owner")
                    put("repo", "repo")
                    put("page", 2)
                }
            ).jsonObject
            assertEquals(30, result["entries"]!!.jsonArray.size)
            assertEquals("file30", result["entries"]!!.jsonArray[0].jsonObject["path"]!!.jsonPrimitive.content)
            assertEquals("true", result["has_more"].toString())
            val last = client.execute(
                "browse_files",
                buildJsonObject {
                    put("owner", "owner")
                    put("repo", "repo")
                    put("page", 3)
                }
            ).jsonObject
            assertEquals(5, last["entries"]!!.jsonArray.size)
            assertEquals("false", last["has_more"].toString())
        } finally {
            http.close()
        }
    }
}
