package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.github.GitHubRepositoryContext
import dev.chungjungsoo.gptmobile.data.github.GitHubWorkspaceClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield
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
    private val httpClient: HttpClient = defaultHttpClient,
    private val modelToolName: String = BuiltInAgentTool.GITHUB,
    private val accountName: String? = null,
    private val repositoryContext: GitHubRepositoryContext? = null
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
        name = modelToolName,
        description = "Work with GitHub repositories: search code/issues, read files and pull requests, inspect Actions workflows, create branches, update files, and open pull requests." + (accountName?.let { " Authenticated connection: $it. Use this tool for repositories available to this account." } ?: " Public read access; configure a GitHub API connection for private repositories and writes.") + (repositoryContext?.let { " Selected repository: ${it.fullName}, branch: ${it.ref}. Omitted owner/repo/ref use this selection. Read code in line ranges and include source paths. Read get_branch_head before commit_files; supply expected_head_sha. Commit only to a working branch and create a draft PR for review." } ?: ""),
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
                                "GitHub action. Use browse_files for directory navigation, read_code for line ranges with metadata, get_branch_head then commit_files for atomic changes on a working branch. Additional actions: " + GitHubWorkspaceClient.actions.sorted().joinToString(", ")
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
                                    add(JsonPrimitive("list_workflows"))
                                    add(JsonPrimitive("get_workflow_run"))
                                    add(JsonPrimitive("list_workflow_jobs"))
                                    add(JsonPrimitive("list_workflow_artifacts"))
                                    add(JsonPrimitive("get_job_logs"))
                                    add(JsonPrimitive("dispatch_workflow"))
                                    add(JsonPrimitive("rerun_workflow"))
                                    add(JsonPrimitive("rerun_failed_jobs"))
                                    add(JsonPrimitive("cancel_workflow"))

                                    add(JsonPrimitive("create_branch"))
                                    add(JsonPrimitive("update_file"))
                                    add(JsonPrimitive("create_pull_request"))
                                    GitHubWorkspaceClient.actions.sorted().forEach { add(JsonPrimitive(it)) }
                                }
                            )
                        }
                    )
                    listOf("run_id", "job_id", "page").forEach { name ->
                        put(
                            name,
                            buildJsonObject {
                                put("type", "integer")
                                put("minimum", 1)
                            }
                        )
                    }
                    put(
                        "workflow_id",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Workflow ID or filename, such as build.yml, for dispatch_workflow.")
                        }
                    )
                    put(
                        "inputs",
                        buildJsonObject {
                            put("type", "object")
                            put("description", "Input values declared by workflow_dispatch in the workflow file.")
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
                            put("description", "Head branch for create_pull_request or compare_refs.")
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
                    listOf("start_line", "end_line").forEach { key ->
                        put(
                            key,
                            buildJsonObject {
                                put("type", "integer")
                                put("minimum", 1)
                            }
                        )
                    }
                    put(
                        "draft",
                        buildJsonObject {
                            put("type", "boolean")
                            put("description", "Create a draft pull request; defaults to true.")
                        }
                    )
                    put(
                        "expected_head_sha",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Exact branch commit SHA read before preparing these changes; required for commit_files.")
                        }
                    )
                    put(
                        "files",
                        buildJsonObject {
                            put("type", "array")
                            put("minItems", 1)
                            put("maxItems", 20)
                            put("description", "UTF-8 file replacements committed together on an explicit working branch.")
                            put(
                                "items",
                                buildJsonObject {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        buildJsonObject {
                                            put("path", buildJsonObject { put("type", "string") })
                                            put("content", buildJsonObject { put("type", "string") })
                                        }
                                    )
                                    put(
                                        "required",
                                        buildJsonArray {
                                            add(JsonPrimitive("path"))
                                            add(JsonPrimitive("content"))
                                        }
                                    )
                                }
                            )
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
        val effectiveArguments = buildJsonObject {
            arguments.forEach { (key, value) -> put(key, value) }
            repositoryContext?.let { selected ->
                if (arguments["owner"] == null && arguments["repo"] == null) {
                    put("owner", selected.owner)
                    put("repo", selected.repo)
                }
                val sameRepo = (arguments["owner"]?.jsonPrimitive?.content ?: selected.owner) == selected.owner &&
                    (arguments["repo"]?.jsonPrimitive?.content ?: selected.repo) == selected.repo
                if (sameRepo && arguments["ref"] == null) put("ref", selected.ref)
            }
        }
        return executeEffective(callId, effectiveArguments)
    }

    private suspend fun executeEffective(callId: String, arguments: JsonObject): AgentToolResult {
        val action = arguments["action"]?.jsonPrimitive?.content?.trim()
            ?: return errorResult(callId, "Missing required parameter: 'action'.")

        return try {
            if (action in GitHubWorkspaceClient.actions) {
                val result = GitHubWorkspaceClient(apiToken, httpClient).execute(action, arguments)
                return successResult(callId, result.toString())
            }
            when (action) {
                "search_repositories" -> handleSearchRepositories(callId, arguments)
                "search_issues" -> handleSearchIssues(callId, arguments)
                "get_file_contents" -> handleGetFileContents(callId, arguments)
                "get_issue" -> handleGetIssue(callId, arguments)
                "search_code" -> handleSearchCode(callId, arguments)
                "get_pull_request" -> handleGetPullRequest(callId, arguments)
                "list_pull_requests" -> handleListPullRequests(callId, arguments)
                "list_workflow_runs" -> handleListWorkflowRuns(callId, arguments)
                "list_workflows", "get_workflow_run", "list_workflow_jobs", "list_workflow_artifacts", "get_job_logs", "dispatch_workflow", "rerun_workflow", "rerun_failed_jobs", "cancel_workflow" -> handleWorkflowAction(callId, action, arguments)
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
        val page = arguments["page"]?.jsonPrimitive?.intOrNull ?: 1
        require(page in 1..1000)
        val response = getGitHubApi("$BASE_URL/repos/$owner/$repo/pulls?state=all&per_page=20&page=$page")
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }
        val items = jsonParser.parseToJsonElement(text).jsonArray
        val summary = buildJsonArray {
            items.forEach { item ->
                add(
                    buildJsonObject {
                        listOf("number", "title", "state", "draft", "html_url", "head", "base").forEach { key -> item.jsonObject[key]?.let { put(key, it) } }
                    }
                )
            }
        }
        return successResult(callId, summary.toString())
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
        val result = jsonParser.parseToJsonElement(text).jsonObject
        val summary = buildJsonObject {
            listOf("number", "title", "state", "draft", "html_url", "head", "base", "mergeable", "mergeable_state", "merged", "changed_files").forEach { key -> result[key]?.let { put(key, it) } }
            put("body", truncate(result["body"]?.jsonPrimitive?.content.orEmpty(), 8000))
        }
        return successResult(callId, summary.toString())
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

    private suspend fun handleWorkflowAction(callId: String, action: String, arguments: JsonObject): AgentToolResult {
        val owner = arguments["owner"]?.jsonPrimitive?.content.orEmpty()
        val repo = arguments["repo"]?.jsonPrimitive?.content.orEmpty()
        require(Regex("[A-Za-z0-9_.-]+").matches(owner) && Regex("[A-Za-z0-9_.-]+").matches(repo)) { "Valid owner and repo are required." }
        fun positiveId(name: String): Long = requireNotNull(arguments[name]?.jsonPrimitive?.content?.toLongOrNull()?.takeIf { it > 0 }) { "A positive $name is required." }
        val base = "$BASE_URL/repos/$owner/$repo/actions"
        val page = arguments["page"]?.jsonPrimitive?.intOrNull ?: 1
        require(page in 1..1000) { "Page must be between 1 and 1000." }
        val pagination = "?per_page=30&page=$page"
        val write = action in setOf("dispatch_workflow", "rerun_workflow", "rerun_failed_jobs", "cancel_workflow")
        val path = when (action) {
            "list_workflows" -> "/workflows$pagination"
            "get_workflow_run" -> "/runs/${positiveId("run_id")}"
            "list_workflow_jobs" -> "/runs/${positiveId("run_id")}/jobs$pagination"
            "list_workflow_artifacts" -> "/runs/${positiveId("run_id")}/artifacts$pagination"
            "get_job_logs" -> "/jobs/${positiveId("job_id")}/logs"
            "dispatch_workflow" -> {
                val workflow = arguments["workflow_id"]?.jsonPrimitive?.content.orEmpty()
                require(Regex("[A-Za-z0-9_.-]+").matches(workflow)) { "A workflow ID or filename is required." }
                "/workflows/$workflow/dispatches"
            }
            "rerun_workflow" -> "/runs/${positiveId("run_id")}/rerun"
            "rerun_failed_jobs" -> "/runs/${positiveId("run_id")}/rerun-failed-jobs"
            "cancel_workflow" -> "/runs/${positiveId("run_id")}/cancel"
            else -> error("Unsupported workflow action.")
        }
        val response = if (write) {
            requireToken()
            val body = if (action == "dispatch_workflow") {
                buildJsonObject {
                    val ref = arguments["ref"]?.jsonPrimitive?.content.orEmpty()
                    require(ref.isNotBlank()) { "A branch or tag ref is required for dispatch_workflow." }
                    put("ref", ref)
                    arguments["inputs"]?.let {
                        require(it is JsonObject) { "Inputs must be an object." }
                        put("inputs", it)
                    }
                }
            } else {
                buildJsonObject {}
            }
            writeGitHubApi(base + path, HttpMethod.Post, body)
        } else {
            getGitHubApi(base + path)
        }
        val text = if (action == "get_job_logs") readLogPreview(response) else response.bodyAsText()
        return if (response.status.isSuccess()) {
            successResult(
                callId,
                if (text.isBlank()) {
                    "GitHub accepted $action. Inspect the workflow run to confirm its result."
                } else if (action == "get_job_logs") {
                    text
                } else {
                    truncate(text, MAX_OUTPUT_CHARS)
                }
            )
        } else {
            errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }
    }

    private suspend fun readLogPreview(response: HttpResponse): String {
        val channel = response.bodyAsChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            while (output.size() <= MAX_OUTPUT_CHARS) {
                val count = channel.readAvailable(buffer, 0, minOf(buffer.size, MAX_OUTPUT_CHARS + 1 - output.size()))
                if (count < 0) break
                if (count == 0) {
                    yield()
                    continue
                }
                output.write(buffer, 0, count)
            }
            val bytes = output.toByteArray()
            return bytes.copyOf(minOf(bytes.size, MAX_OUTPUT_CHARS)).decodeToString() +
                if (bytes.size > MAX_OUTPUT_CHARS) "\n[Log preview limited to 32 KB. Open the workflow job on GitHub for the full log.]" else ""
        } finally {
            channel.cancel(null)
        }
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
                put("draft", arguments["draft"] ?: JsonPrimitive(true))
            }
        )
        val text = response.bodyAsText()
        return if (response.status.isSuccess()) {
            successResult(callId, truncate(text, MAX_OUTPUT_CHARS))
        } else {
            errorResult(callId, "GitHub API returned HTTP ${response.status.value}: ${truncate(text, 500)}")
        }
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

    private suspend fun getGitHubApi(url: String): HttpResponse = httpClient.get(url) {
        header(HttpHeaders.Accept, "application/vnd.github.v3+json")
        header(HttpHeaders.UserAgent, "GPT-Mobile-App")
        if (apiToken.isNotBlank()) {
            header(HttpHeaders.Authorization, "Bearer $apiToken")
        }
    }

    private fun truncate(text: String, maxLength: Int): String = if (text.length <= maxLength) text else text.take(maxLength) + "\n...[truncated]"

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
