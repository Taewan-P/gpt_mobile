package dev.chungjungsoo.gptmobile.data.github

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubIntegrationV2Test {
    @Test
    fun operation_planner_includes_safe_write_sequence() {
        val steps = GitHubOperationPlanner.plan("Fix the bug, merge it, then publish a release")
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }

        assertTrue("repo_status" in steps)
        assertTrue("branch_head" in steps)
        assertTrue("commit" in steps)
        assertTrue("checks" in steps)
        assertTrue("draft_pr" in steps)
        assertTrue("merge_gate" in steps)
        assertTrue("release_status" in steps)
        assertTrue("publish_release" in steps)
        assertTrue("release_verify" in steps)
        assertTrue("release_status" in GitHubWorkspaceClient.readActions)
        assertFalse("publish_release" in GitHubWorkspaceClient.readActions)
        assertTrue("publish_release" in GitHubWorkspaceClient.writeActions)
    }

    @Test
    fun repository_index_strips_raw_payload_and_ranks_related_files() {
        val tree = Json.parseToJsonElement(
            """
            {
              "truncated": false,
              "tree": [
                {"path":"app/src/main/Foo.kt","type":"blob","mode":"100644","sha":"a","size":100},
                {"path":"app/src/test/FooTest.kt","type":"blob","mode":"100644","sha":"b","size":120},
                {"path":"app/src/main/res/icon.png","type":"blob","mode":"100644","sha":"c","size":5000},
                {"path":"app/src/main","type":"tree","mode":"040000","sha":"d"}
              ]
            }
            """.trimIndent()
        ).jsonObject

        val compact = GitHubRepositoryIndex.compact(tree, page = 1)
        assertEquals(3, compact["entries"]!!.jsonArray.size)
        assertFalse(compact.toString().contains("icon.png"))

        val related = GitHubRepositoryIndex.related(tree, "app/src/main/Foo.kt")
        assertEquals("app/src/test/FooTest.kt", related[0].jsonObject["path"]!!.jsonPrimitive.content)
    }

    @Test
    fun conditional_get_reuses_cached_response_and_sends_current_api_version() = runTest {
        var requests = 0
        val http = HttpClient(
            MockEngine { request ->
                assertEquals(GitHubWorkspaceClient.API_VERSION, request.headers["X-GitHub-Api-Version"])
                when (requests++) {
                    0 -> respond(
                        """{"full_name":"owner/repo","default_branch":"main"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ETag, "\"repo-v1\"")
                    )
                    1 -> {
                        assertEquals("\"repo-v1\"", request.headers[HttpHeaders.IfNoneMatch])
                        respond("", HttpStatusCode.NotModified)
                    }
                    else -> error("Unexpected request")
                }
            }
        )
        try {
            val client = GitHubWorkspaceClient("token", http)
            val args = buildJsonObject {
                put("owner", "owner")
                put("repo", "repo")
            }
            val first = client.execute("get_repository", args)
            val second = client.execute("get_repository", args)
            assertEquals(first, second)
            assertEquals(2, requests)
        } finally {
            http.close()
        }
    }

    @Test
    fun repo_map_returns_compact_source_index() = runTest {
        val http = HttpClient(
            MockEngine { request ->
                assertTrue(request.url.encodedPath.endsWith("/git/trees/main"))
                assertEquals("1", request.url.parameters["recursive"])
                respond(
                    """
                    {
                      "truncated": false,
                      "tree": [
                        {"path":"src/App.kt","type":"blob","mode":"100644","sha":"a","size":100},
                        {"path":"assets/huge.bin","type":"blob","mode":"100644","sha":"b","size":500000}
                      ]
                    }
                    """.trimIndent()
                )
            }
        )
        try {
            val result = GitHubWorkspaceClient("token", http).execute(
                "repo_map",
                buildJsonObject {
                    put("owner", "owner")
                    put("repo", "repo")
                    put("ref", "main")
                }
            ).jsonObject

            assertEquals(1, result["entries"]!!.jsonArray.size)
            assertEquals("src/App.kt", result["entries"]!!.jsonArray[0].jsonObject["path"]!!.jsonPrimitive.content)
        } finally {
            http.close()
        }
    }

    @Test
    fun repo_status_uses_graphql_aggregation_and_compact_workflow_state() = runTest {
        var requests = 0
        val http = HttpClient(
            MockEngine { request ->
                when {
                    request.url.encodedPath == "/graphql" -> {
                        requests++
                        respond(
                            """
                            {
                              "data": {
                                "repository": {
                                  "nameWithOwner": "owner/repo",
                                  "url": "https://github.com/owner/repo",
                                  "isPrivate": false,
                                  "defaultBranchRef": {
                                    "name": "main",
                                    "target": {
                                      "oid": "abc123",
                                      "committedDate": "2026-10-01T00:00:00Z",
                                      "messageHeadline": "Latest"
                                    }
                                  },
                                  "issues": {"totalCount": 4},
                                  "pullRequests": {
                                    "totalCount": 2,
                                    "nodes": [
                                      {
                                        "number": 12,
                                        "title": "Improve GitHub",
                                        "isDraft": true,
                                        "updatedAt": "2026-10-01T00:00:00Z",
                                        "headRefName": "feature",
                                        "baseRefName": "main",
                                        "url": "https://github.com/owner/repo/pull/12"
                                      }
                                    ]
                                  }
                                },
                                "rateLimit": {"cost": 1, "remaining": 4999, "resetAt": "2026-10-01T01:00:00Z"}
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    request.url.encodedPath.endsWith("/actions/runs") -> {
                        requests++
                        respond(
                            """
                            {
                              "workflow_runs": [
                                {
                                  "id": 99,
                                  "name": "PR validation",
                                  "status": "completed",
                                  "conclusion": "success",
                                  "head_branch": "feature",
                                  "head_sha": "abc123"
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    }
                    else -> error("Unexpected request")
                }
            }
        )
        try {
            val result = GitHubWorkspaceClient("token", http).execute(
                "repo_status",
                buildJsonObject {
                    put("owner", "owner")
                    put("repo", "repo")
                }
            ).jsonObject

            assertEquals(4, result["open_issue_count"]!!.jsonPrimitive.content.toInt())
            assertEquals(2, result["open_pull_request_count"]!!.jsonPrimitive.content.toInt())
            assertEquals("success", result["latest_workflow"]!!.jsonObject["conclusion"]!!.jsonPrimitive.content)
            assertEquals(2, requests)
        } finally {
            http.close()
        }
    }

    @Test
    fun pull_request_file_projection_keeps_patch_for_workspace() = runTest {
        val http = HttpClient(
            MockEngine {
                respond(
                    """
                    [
                      {
                        "filename": "src/App.kt",
                        "status": "modified",
                        "additions": 3,
                        "deletions": 1,
                        "changes": 4,
                        "sha": "abc",
                        "patch": "@@ -1 +1 @@"
                      }
                    ]
                    """.trimIndent()
                )
            }
        )
        try {
            val result = GitHubWorkspaceClient("token", http).execute(
                "get_pull_request_files",
                buildJsonObject {
                    put("owner", "owner")
                    put("repo", "repo")
                    put("pull_number", 7)
                }
            ).jsonObject
            val file = result["items"]!!.jsonArray.single().jsonObject
            assertEquals("@@ -1 +1 @@", file["patch"]!!.jsonPrimitive.content)
        } finally {
            http.close()
        }
    }

    @Test
    fun create_tag_resolves_target_commit_before_creating_ref() = runTest {
        var requests = 0
        val targetSha = "0123456789abcdef0123456789abcdef01234567"
        val http = HttpClient(
            MockEngine { request ->
                requests++
                when {
                    request.url.encodedPath == "/repos/owner/repo/commits/main" -> respond("""{"sha":"$targetSha"}""")
                    request.url.encodedPath == "/repos/owner/repo/git/refs" -> {
                        assertEquals("POST", request.method.value)
                        respond("""{"ref":"refs/tags/v1.2.3","url":"https://api.github.com/repos/owner/repo/git/refs/tags/v1.2.3"}""", HttpStatusCode.Created)
                    }
                    else -> error("Unexpected request: ${request.method.value} ${request.url}")
                }
            }
        )
        try {
            val result = GitHubWorkspaceClient("token", http).execute(
                "create_tag",
                buildJsonObject {
                    put("owner", "owner")
                    put("repo", "repo")
                    put("tag_name", "v1.2.3")
                    put("target_commitish", "main")
                }
            ).jsonObject

            assertEquals(targetSha, result["sha"]!!.jsonPrimitive.content)
            assertEquals("v1.2.3", result["tag_name"]!!.jsonPrimitive.content)
            assertEquals(2, requests)
        } finally {
            http.close()
        }
    }

    @Test
    fun create_release_uses_native_releases_api_and_returns_compact_release() = runTest {
        val http = HttpClient(
            MockEngine { request ->
                assertEquals("/repos/owner/repo/releases", request.url.encodedPath)
                assertEquals("POST", request.method.value)
                respond(
                    """
                    {
                      "id": 321,
                      "tag_name": "v1.2.3",
                      "target_commitish": "main",
                      "name": "v1.2.3",
                      "draft": false,
                      "prerelease": false,
                      "html_url": "https://github.com/owner/repo/releases/tag/v1.2.3",
                      "assets": []
                    }
                    """.trimIndent(),
                    HttpStatusCode.Created
                )
            }
        )
        try {
            val result = GitHubWorkspaceClient("token", http).execute(
                "create_release",
                buildJsonObject {
                    put("owner", "owner")
                    put("repo", "repo")
                    put("tag_name", "v1.2.3")
                    put("target_commitish", "main")
                }
            ).jsonObject

            assertEquals("v1.2.3", result["tag_name"]!!.jsonPrimitive.content)
            assertEquals("0", result["asset_count"]!!.jsonPrimitive.content)
        } finally {
            http.close()
        }
    }

    @Test
    fun publish_release_prefers_active_release_workflow() = runTest {
        var requests = 0
        val http = HttpClient(
            MockEngine { request ->
                requests++
                when {
                    request.url.encodedPath == "/repos/owner/repo/releases" -> {
                        assertEquals("100", request.url.parameters["per_page"])
                        assertEquals("1", request.url.parameters["page"])
                        respond("[]")
                    }
                    request.url.encodedPath == "/repos/owner/repo/actions/workflows" -> {
                        assertEquals("100", request.url.parameters["per_page"])
                        respond(
                            """
                            {
                              "total_count": 1,
                              "workflows": [
                                {
                                  "id": 42,
                                  "name": "Publish Signed Release",
                                  "path": ".github/workflows/release-build.yml",
                                  "state": "active",
                                  "html_url": "https://github.com/owner/repo/actions/workflows/release-build.yml"
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    }
                    request.url.encodedPath == "/repos/owner/repo/contents/app/build.gradle.kts" -> {
                        assertEquals("main", request.url.parameters["ref"])
                        val gradle = """android { defaultConfig { versionName = "1.2.3" } }"""
                        val encoded = java.util.Base64.getEncoder().encodeToString(gradle.toByteArray())
                        respond("""{"type":"file","encoding":"base64","content":"$encoded"}""")
                    }
                    request.url.encodedPath == "/repos/owner/repo/actions/workflows/42/dispatches" -> {
                        assertEquals("POST", request.method.value)
                        respond("", HttpStatusCode.NoContent)
                    }
                    else -> error("Unexpected request: ${request.url}")
                }
            }
        )
        try {
            val result = GitHubWorkspaceClient("token", http).execute(
                "publish_release",
                buildJsonObject {
                    put("owner", "owner")
                    put("repo", "repo")
                    put("tag_name", "v1.2.3")
                    put("ref", "main")
                }
            ).jsonObject

            assertEquals("workflow_dispatched", result["status"]!!.jsonPrimitive.content)
            assertEquals("workflow", result["strategy"]!!.jsonPrimitive.content)
            assertEquals("release_status", result["verification_action"]!!.jsonPrimitive.content)
            assertEquals("v1.2.3", result["version_preflight"]!!.jsonObject["expected_tag"]!!.jsonPrimitive.content)
            assertEquals(4, requests)
        } finally {
            http.close()
        }
    }

    @Test
    fun publish_release_rejects_workflow_when_android_version_does_not_match_tag() = runTest {
        val gradle = """
            android {
                defaultConfig {
                    versionCode = 91
                    versionName = "0.9.22.0"
                }
            }
        """.trimIndent()
        val encodedGradle = java.util.Base64.getEncoder().encodeToString(gradle.toByteArray())
        var dispatched = false
        val http = HttpClient(
            MockEngine { request ->
                when {
                    request.url.encodedPath == "/repos/owner/repo/releases" -> {
                        assertEquals("1", request.url.parameters["page"])
                        respond("[]")
                    }
                    request.url.encodedPath == "/repos/owner/repo/actions/workflows" -> respond(
                        """
                        {
                          "workflows": [
                            {
                              "id": 42,
                              "name": "Publish Signed Release",
                              "path": ".github/workflows/release-build.yml",
                              "state": "active"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                    request.url.encodedPath == "/repos/owner/repo/contents/app/build.gradle.kts" -> respond(
                        """{"type":"file","encoding":"base64","content":"$encodedGradle"}"""
                    )
                    request.url.encodedPath.endsWith("/dispatches") -> {
                        dispatched = true
                        respond("", HttpStatusCode.NoContent)
                    }
                    else -> error("Unexpected request: ${request.url}")
                }
            }
        )
        try {
            val failure = runCatching {
                GitHubWorkspaceClient("token", http).execute(
                    "publish_release",
                    buildJsonObject {
                        put("owner", "owner")
                        put("repo", "repo")
                        put("tag_name", "v0.9.30.0")
                        put("ref", "main")
                    }
                )
            }.exceptionOrNull()

            assertTrue(failure?.message?.contains("does not match app versionName") == true)
            assertFalse(dispatched)
        } finally {
            http.close()
        }
    }

    @Test
    fun publish_release_auto_fails_closed_when_workflow_discovery_fails() = runTest {
        var releaseCreated = false
        val http = HttpClient(
            MockEngine { request ->
                when {
                    request.url.encodedPath == "/repos/owner/repo/releases" && request.method.value == "GET" -> respond("[]")
                    request.url.encodedPath == "/repos/owner/repo/actions/workflows" -> respond("forbidden", HttpStatusCode.Forbidden)
                    request.url.encodedPath == "/repos/owner/repo/releases" && request.method.value == "POST" -> {
                        releaseCreated = true
                        respond("""{"id":1,"tag_name":"v1.0.0","assets":[]}""", HttpStatusCode.Created)
                    }
                    else -> error("Unexpected request: ${request.method.value} ${request.url}")
                }
            }
        )
        try {
            val failure = runCatching {
                GitHubWorkspaceClient("token", http).execute(
                    "publish_release",
                    buildJsonObject {
                        put("owner", "owner")
                        put("repo", "repo")
                        put("tag_name", "v1.0.0")
                    }
                )
            }.exceptionOrNull()

            assertTrue(failure?.message?.contains("GitHub HTTP 403") == true)
            assertFalse(releaseCreated)
        } finally {
            http.close()
        }
    }

    @Test
    fun publish_release_direct_strategy_skips_workflow_discovery() = runTest {
        var requests = 0
        val http = HttpClient(
            MockEngine { request ->
                requests++
                when {
                    request.url.encodedPath == "/repos/owner/repo/releases" && request.method.value == "GET" -> {
                        assertEquals("1", request.url.parameters["page"])
                        respond("[]")
                    }
                    request.url.encodedPath == "/repos/owner/repo/releases" && request.method.value == "POST" -> respond(
                        """
                        {
                          "id": 322,
                          "tag_name": "v2.0.0",
                          "name": "v2.0.0",
                          "draft": false,
                          "prerelease": false,
                          "assets": []
                        }
                        """.trimIndent(),
                        HttpStatusCode.Created
                    )
                    else -> error("Unexpected request: ${request.method.value} ${request.url}")
                }
            }
        )
        try {
            val result = GitHubWorkspaceClient("token", http).execute(
                "publish_release",
                buildJsonObject {
                    put("owner", "owner")
                    put("repo", "repo")
                    put("tag_name", "v2.0.0")
                    put("release_strategy", "direct")
                }
            ).jsonObject

            assertEquals("created", result["status"]!!.jsonPrimitive.content)
            assertEquals("release_api", result["strategy"]!!.jsonPrimitive.content)
            assertEquals(2, requests)
        } finally {
            http.close()
        }
    }

    @Test
    fun rate_limit_status_does_not_reuse_cached_body() = runTest {
        var requests = 0
        val http = HttpClient(
            MockEngine {
                requests++
                respond(
                    """{"resources":{"core":{"limit":5000,"remaining":${5000 - requests}}}}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ETag, "\"quota\"")
                )
            }
        )
        try {
            val client = GitHubWorkspaceClient("token", http)
            val first = client.execute("rate_limit_status", buildJsonObject {}).jsonObject
            val second = client.execute("rate_limit_status", buildJsonObject {}).jsonObject
            assertEquals(2, requests)
            assertFalse(first == second)
        } finally {
            http.close()
        }
    }
}
