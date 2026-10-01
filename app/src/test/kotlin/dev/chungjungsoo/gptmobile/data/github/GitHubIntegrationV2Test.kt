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
        assertTrue("release_gate" in steps)
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
}
