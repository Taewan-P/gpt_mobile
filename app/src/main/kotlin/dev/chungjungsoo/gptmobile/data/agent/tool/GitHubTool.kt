package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Built-in agent tool for querying the GitHub REST API.
 * Supports searching repositories, searching issues/pull requests, fetching file contents,
 * and reading issue/PR details.
 */
class GitHubTool(
    private val apiToken: String = "",
    private val httpClient: HttpClient = defaultHttpClient
) : AgentTool {

    companion object {
        private const val BASE_URL = "https://api.github.com"
        private const val MAX_OUTPUT_CHARS = 32_000

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val defaultHttpClient: HttpClient by lazy {
            HttpClient(OkHttp) {
                engine {
                    config {
                        retryOnConnectionFailure(true)
                    }
                }
            }
        }
    }

    override val definition: AgentToolDefinition = AgentToolDefinition(
        name = BuiltInAgentTool.GITHUB,
        description = "Work with GitHub repositories: search code/issues, read files and pull requests, inspect Actions workflows, create branches, update files, and open pull requests.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "action",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "Action to perform: search_repositories, search_issues, search_code, get_file_contents, get_issue, get_pull_request, list_pull_requests, list_workflow_runs, create_branch, update_file, or create_pull_request."
                            )
                            put(
                                "enum",
                                buildJsonArray {
                                    add(JsonPrimitive("search_repositories"))
                                    add(JsonPrimitive("search_issues"))
                                    add(JsonPrimitive("get_file_contents"))
                                    add(JsonPrimitive("get_issue"))
                                    add(JsonPrimitive("search_code"))
                                    add(JsonPrimitive("get_pull_request"))
                                    add(JsonPrimitive("list_pull_requests"))
                                    add(JsonPrimitive("list_workflow_runs"))
                                    add(JsonPrimitive("create_branch"))
                                    add(JsonPrimitive("update_file"))
                                    add(JsonPrimitive("create_pull_request"))
                                }
                            )
                        }
                    )
                    put(
                        "query",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Search query when action is 'search_repositories' or 'search_issues'.")
                        }
                    )
                    put(
                        "owner",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Repository owner (user or organization). Required for get_file_contents and get_issue.")
                        }
                    )
                    put(
                        "repo",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Repository name. Required for get_file_contents and get_issue.")
                        }
                    )
                    put(
                        "path",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "File path within repository. Required for get_file_contents.")
                        }
                    )
                    put(
                        "ref",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Branch name, commit SHA, or tag for get_file_contents. Defaults to default branch.")
                        }
                    )
                    put(
                        "issue_number",
                        buildJsonObject {
                            put("type", "integer")
                            put("description", "Issue or pull request number. Required for get_issue.")
                        }
                    )
                    put(
                        "content",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "UTF-8 file content for update_file.")
                        }
                    )
                    put(
                        "message",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Commit message for update_file.")
                        }
                    )
                    put(
                        "branch",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Branch name for create_branch or update_file.")
                        }
                    )
                    put(
                        "base",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Base branch for create_branch or create_pull_request.")
                        }
                    )
                    put(
                        "head",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Head branch for create_pull_request.")
                        }
                    )
                    put(
                        "title",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Pull request title.")
                        }
                    )
                    put(
                        "body",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Pull request body.")
                        }
                    )
                    put(
                        "file_sha",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Existing blob SHA when replacing a file.")
                        }
                    )
                    put(
                        "pull_number",
                        buildJsonObject {
                            put("type", "integer")
                            put("description", "Pull request number.")
                        }
                    )
                }
            )
            put(
                "required",
                buildJsonArray {
                    add(JsonPrimitive("action"))
                }
            )
        }
    )

    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        val action = arguments["action"]?.jsonPrimitive?.content?.trim()
            ?: return errorResult(callId, "Missing required parameter: 'action'.")

        return try {
            when (action) {
                "search_repositories" -> handleSearchRepositories(callId, arguments)
                "search_issues" -> handleSearchIssues(callId, arguments)
                "get_file_contents" -> handleGetFileContents(callId, arguments)
                "get_issue" -> handleGetIssue(callId, arguments)
                "search_code" -> handleSearchCode(callId, arguments)
                "get_pull_request" -> handleGetPullRequest(callId, arguments)
                "list_pull_requests" -> handleListPullRequests(callId, arguments)
                "list_workflow_runs" -> handleListWorkflowRuns(callId, arguments)
                "create_branch" -> handleCreateBranch(callId, arguments)
                "update_file" -> handleUpdateFile(callId, arguments)
                "create_pull_request" -> handleCreatePullRequest(callId, arguments)
                else -> errorResult(callId, "Unknown GitHub action: '$action'.")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            errorResult(callId, "GitHub request failed: ${throwable.localizedMessage ?: throwable.message ?: "Unknown error"}")
        }
    }

    private suspend fun handleSearchRepositories(callId: String, arguments: JsonObject): AgentToolResult {
        val query = arguments["query"]?.jsonPrimitive?.content?.trim()
        if (query.isNullOrEmpty()) {
            return errorResult(callId, "Parameter 'query' is required for action 'search_repositories'.")
        }
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "$BASE_URL/search/repositories?q=$encodedQuery&per_page=10"
        val response = getGitHubApi(url)
        val text = response.bodyAsText()

        if (!response.status.isSuccess()) {
            return errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }

        val json = jsonParser.parseToJsonElement(text).jsonObject
        val items = json["items"]?.jsonArray ?: JsonArray(emptyList())
        val summary = buildJsonObject {
            put("total_count", json["total_count"] ?: JsonPrimitive(0))
            put(
                "items",
                buildJsonArray {
                    items.take(10).forEach { item ->
                        val obj = item.jsonObject
                        add(
                            buildJsonObject {
                                put("full_name", obj["full_name"] ?: JsonPrimitive(""))
                                put("description", obj["description"] ?: JsonPrimitive(""))
                                put("stargazers_count", obj["stargazers_count"] ?: JsonPrimitive(0))
                                put("language", obj["language"] ?: JsonPrimitive(""))
                                put("html_url", obj["html_url"] ?: JsonPrimitive(""))
                            }
                        )
                    }
                }
            )
        }
        return successResult(callId, summary.toString())
    }

    private suspend fun handleSearchIssues(callId: String, arguments: JsonObject): AgentToolResult {
        val query = arguments["query"]?.jsonPrimitive?.content?.trim()
        if (query.isNullOrEmpty()) {
            return errorResult(callId, "Parameter 'query' is required for action 'search_issues'.")
        }
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "$BASE_URL/search/issues?q=$encodedQuery&per_page=10"
        val response = getGitHubApi(url)
        val text = response.bodyAsText()

        if (!response.status.isSuccess()) {
            return errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }

        val json = jsonParser.parseToJsonElement(text).jsonObject
        val items = json["items"]?.jsonArray ?: JsonArray(emptyList())
        val summary = buildJsonObject {
            put("total_count", json["total_count"] ?: JsonPrimitive(0))
            put(
                "items",
                buildJsonArray {
                    items.take(10).forEach { item ->
                        val obj = item.jsonObject
                        add(
                            buildJsonObject {
                                put("number", obj["number"] ?: JsonPrimitive(0))
                                put("title", obj["title"] ?: JsonPrimitive(""))
                                put("state", obj["state"] ?: JsonPrimitive(""))
                                put("html_url", obj["html_url"] ?: JsonPrimitive(""))
                                put("created_at", obj["created_at"] ?: JsonPrimitive(""))
                            }
                        )
                    }
                }
            )
        }
        return successResult(callId, summary.toString())
    }

    private suspend fun handleGetFileContents(callId: String, arguments: JsonObject): AgentToolResult {
        val owner = arguments["owner"]?.jsonPrimitive?.content?.trim()
        val repo = arguments["repo"]?.jsonPrimitive?.content?.trim()
        val path = arguments["path"]?.jsonPrimitive?.content?.trim()
        val ref = arguments["ref"]?.jsonPrimitive?.content?.trim()

        if (owner.isNullOrEmpty() || repo.isNullOrEmpty() || path.isNullOrEmpty()) {
            return errorResult(callId, "Parameters 'owner', 'repo', and 'path' are required for action 'get_file_contents'.")
        }

        val cleanPath = path.removePrefix("/")
        val refQuery = if (!ref.isNullOrEmpty()) "?ref=${URLEncoder.encode(ref, StandardCharsets.UTF_8.name())}" else ""
        val url = "$BASE_URL/repos/$owner/$repo/contents/$cleanPath$refQuery"
        val response = getGitHubApi(url)
        val text = response.bodyAsText()

        if (!response.status.isSuccess()) {
            return errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }

        val json = jsonParser.parseToJsonElement(text).jsonObject
        val encoding = json["encoding"]?.jsonPrimitive?.content
        val contentBase64 = json["content"]?.jsonPrimitive?.content?.replace("\n", "") ?: ""

        val decodedContent = if (encoding == "base64" && contentBase64.isNotEmpty()) {
            try {
                String(Base64.getDecoder().decode(contentBase64), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                "[Failed to decode base64 content: ${e.message}]"
            }
        } else {
            text
        }

        val truncated = truncate(decodedContent, MAX_OUTPUT_CHARS)
        return successResult(callId, truncated)
    }

    private suspend fun handleGetIssue(callId: String, arguments: JsonObject): AgentToolResult {
        val owner = arguments["owner"]?.jsonPrimitive?.content?.trim()
        val repo = arguments["repo"]?.jsonPrimitive?.content?.trim()
        val issueNumber = arguments["issue_number"]?.jsonPrimitive?.intOrNull

        if (owner.isNullOrEmpty() || repo.isNullOrEmpty() || issueNumber == null) {
            return errorResult(callId, "Parameters 'owner', 'repo', and 'issue_number' are required for action 'get_issue'.")
        }

        val url = "$BASE_URL/repos/$owner/$repo/issues/$issueNumber"
        val response = getGitHubApi(url)
        val text = response.bodyAsText()

        if (!response.status.isSuccess()) {
            return errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }

        val json = jsonParser.parseToJsonElement(text).jsonObject
        val summary = buildJsonObject {
            put("number", json["number"] ?: JsonPrimitive(issueNumber))
            put("title", json["title"] ?: JsonPrimitive(""))
            put("state", json["state"] ?: JsonPrimitive(""))
            put("user", json["user"]?.jsonObject?.get("login") ?: JsonPrimitive(""))
            put("created_at", json["created_at"] ?: JsonPrimitive(""))
            put("body", JsonPrimitive(truncate(json["body"]?.jsonPrimitive?.content ?: "", 4000)))
            put("html_url", json["html_url"] ?: JsonPrimitive(""))
        }
        return successResult(callId, summary.toString())
    }

    private suspend fun handleSearchCode(callId: String, arguments: JsonObject): AgentToolResult {
        val query = arguments["query"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (query.isEmpty()) {
            return errorResult(callId, "Parameter 'query' is required.")
        }
        val owner = arguments["owner"]?.jsonPrimitive?.content?.trim()
        val repo = arguments["repo"]?.jsonPrimitive?.content?.trim()
        val scoped =
            if (!owner.isNullOrBlank() && !repo.isNullOrBlank()) {
                "$query repo:$owner/$repo"
            } else {
                query
            }
        val response = getGitHubApi(
            "$BASE_URL/search/code?q=${URLEncoder.encode(scoped, StandardCharsets.UTF_8.name())}&per_page=20"
        )
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }
        val json = jsonParser.parseToJsonElement(text).jsonObject
        val summary = buildJsonObject {
            put("total_count", json["total_count"] ?: JsonPrimitive(0))
            put(
                "items",
                buildJsonArray {
                    (json["items"]?.jsonArray ?: JsonArray(emptyList())).take(20).forEach { item ->
                        val obj = item.jsonObject
                        add(
                            buildJsonObject {
                                put("name", obj["name"] ?: JsonPrimitive(""))
                                put("path", obj["path"] ?: JsonPrimitive(""))
                                put("html_url", obj["html_url"] ?: JsonPrimitive(""))
                                put("repository", obj["repository"]?.jsonObject?.get("full_name") ?: JsonPrimitive(""))
                            }
                        )
                    }
                }
            )
        }
        return successResult(callId, summary.toString())
    }

    private suspend fun handleListPullRequests(callId: String, arguments: JsonObject): AgentToolResult {
        val owner = arguments["owner"]?.jsonPrimitive?.content?.trim().orEmpty()
        val repo = arguments["repo"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (owner.isEmpty() || repo.isEmpty()) {
            return errorResult(callId, "Parameters 'owner' and 'repo' are required.")
        }
        val response = getGitHubApi("$BASE_URL/repos/$owner/$repo/pulls?state=all&per_page=20")
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }
        return successResult(callId, truncate(text, MAX_OUTPUT_CHARS))
    }

    private suspend fun handleGetPullRequest(callId: String, arguments: JsonObject): AgentToolResult {
        val owner = arguments["owner"]?.jsonPrimitive?.content?.trim().orEmpty()
        val repo = arguments["repo"]?.jsonPrimitive?.content?.trim().orEmpty()
        val number = arguments["pull_number"]?.jsonPrimitive?.intOrNull ?: arguments["issue_number"]?.jsonPrimitive?.intOrNull
        if (owner.isEmpty() || repo.isEmpty() || number == null) {
            return errorResult(callId, "Parameters 'owner', 'repo', and 'pull_number' are required.")
        }
        val response = getGitHubApi("$BASE_URL/repos/$owner/$repo/pulls/$number")
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }
        return successResult(callId, truncate(text, MAX_OUTPUT_CHARS))
    }

    private suspend fun handleListWorkflowRuns(callId: String, arguments: JsonObject): AgentToolResult {
        val owner = arguments["owner"]?.jsonPrimitive?.content?.trim().orEmpty()
        val repo = arguments["repo"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (owner.isEmpty() || repo.isEmpty()) {
            return errorResult(callId, "Parameters 'owner' and 'repo' are required.")
        }
        val branch = arguments["branch"]?.jsonPrimitive?.content?.trim()
        val query = branch
            ?.takeIf(String::isNotBlank)
            ?.let { "?branch=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}&per_page=20" }
            ?: "?per_page=20"
        val response = getGitHubApi("$BASE_URL/repos/$owner/$repo/actions/runs$query")
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }
        return successResult(callId, truncate(text, MAX_OUTPUT_CHARS))
    }

    private suspend fun handleCreateBranch(callId: String, arguments: JsonObject): AgentToolResult {
        requireToken()
        val owner = arguments["owner"]?.jsonPrimitive?.content?.trim().orEmpty()
        val repo = arguments["repo"]?.jsonPrimitive?.content?.trim().orEmpty()
        val branch = arguments["branch"]?.jsonPrimitive?.content?.trim().orEmpty()
        val base = arguments["base"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "main" }
        if (owner.isEmpty() || repo.isEmpty() || branch.isEmpty()) {
            return errorResult(callId, "Parameters 'owner', 'repo', and 'branch' are required.")
        }
        val baseResponse = getGitHubApi(
            "$BASE_URL/repos/$owner/$repo/git/ref/heads/${URLEncoder.encode(base, StandardCharsets.UTF_8.name())}"
        )
        val baseText = baseResponse.bodyAsText()
        if (!baseResponse.status.isSuccess()) {
            return errorResult(callId, "Could not resolve base branch: ${truncate(baseText, 500)}")
        }
        val sha = jsonParser.parseToJsonElement(baseText).jsonObject["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
            ?: return errorResult(callId, "Base branch did not return a commit SHA.")
        val response = writeGitHubApi(
            "$BASE_URL/repos/$owner/$repo/git/refs",
            HttpMethod.Post,
            buildJsonObject {
                put("ref", "refs/heads/$branch")
                put("sha", sha)
            }
        )
        val text = response.bodyAsText()
        return if (response.status.isSuccess()) {
            successResult(callId, truncate(text, MAX_OUTPUT_CHARS))
        } else {
            errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }
    }

    private suspend fun handleUpdateFile(callId: String, arguments: JsonObject): AgentToolResult {
        requireToken()
        val owner = arguments["owner"]?.jsonPrimitive?.content?.trim().orEmpty()
        val repo = arguments["repo"]?.jsonPrimitive?.content?.trim().orEmpty()
        val path = arguments["path"]?.jsonPrimitive?.content?.trim().orEmpty().removePrefix("/")
        val content = arguments["content"]?.jsonPrimitive?.content
            ?: return errorResult(callId, "Parameter 'content' is required.")
        val message = arguments["message"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "Update $path" }
        val branch = arguments["branch"]?.jsonPrimitive?.content?.trim()
        val fileSha = arguments["file_sha"]?.jsonPrimitive?.content?.trim()
        if (owner.isEmpty() || repo.isEmpty() || path.isEmpty()) {
            return errorResult(callId, "Parameters 'owner', 'repo', and 'path' are required.")
        }
        val body = buildJsonObject {
            put("message", message)
            put("content", Base64.getEncoder().encodeToString(content.toByteArray(StandardCharsets.UTF_8)))
            branch?.takeIf(String::isNotBlank)?.let { put("branch", it) }
            fileSha?.takeIf(String::isNotBlank)?.let { put("sha", it) }
        }
        val response = writeGitHubApi("$BASE_URL/repos/$owner/$repo/contents/$path", HttpMethod.Put, body)
        val text = response.bodyAsText()
        return if (response.status.isSuccess()) {
            successResult(callId, truncate(text, MAX_OUTPUT_CHARS))
        } else {
            errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }
    }

    private suspend fun handleCreatePullRequest(callId: String, arguments: JsonObject): AgentToolResult {
        requireToken()
        val owner = arguments["owner"]?.jsonPrimitive?.content?.trim().orEmpty()
        val repo = arguments["repo"]?.jsonPrimitive?.content?.trim().orEmpty()
        val title = arguments["title"]?.jsonPrimitive?.content?.trim().orEmpty()
        val head = arguments["head"]?.jsonPrimitive?.content?.trim().orEmpty()
        val base = arguments["base"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "main" }
        val bodyText = arguments["body"]?.jsonPrimitive?.content.orEmpty()
        if (owner.isEmpty() || repo.isEmpty() || title.isEmpty() || head.isEmpty()) {
            return errorResult(callId, "Parameters 'owner', 'repo', 'title', and 'head' are required.")
        }
        val response = writeGitHubApi(
            "$BASE_URL/repos/$owner/$repo/pulls",
            HttpMethod.Post,
            buildJsonObject {
                put("title", title)
                put("head", head)
                put("base", base)
                put("body", bodyText)
            }
        )
        val text = response.bodyAsText()
        return if (response.status.isSuccess()) successResult(callId, truncate(text, MAX_OUTPUT_CHARS))
        else errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
    }

    private fun requireToken() {
        require(apiToken.isNotBlank()) { "A GitHub token is required for repository write actions." }
    }

    private suspend fun writeGitHubApi(url: String, method: HttpMethod, body: JsonObject): HttpResponse =
        httpClient.request(url) {
            this.method = method
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "GPT-Mobile-App")
            header(HttpHeaders.Authorization, "Bearer $apiToken")
            setBody(body.toString())
        }

    private suspend fun getGitHubApi(url: String): HttpResponse {
        return httpClient.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            header(HttpHeaders.UserAgent, "GPT-Mobile-App")
            if (apiToken.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $apiToken")
            }
        }
    }

    private fun truncate(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) text else text.take(maxLength) + "\n...[truncated]"
    }

    private fun successResult(callId: String, text: String): AgentToolResult =
        AgentToolResult(
            callId = callId,
            content = ToolResultContent.Text(text),
            isError = false
        )

    private fun errorResult(callId: String, errorMessage: String): AgentToolResult =
        AgentToolResult(
            callId = callId,
            content = ToolResultContent.Text(errorMessage),
            isError = true
        )
}
