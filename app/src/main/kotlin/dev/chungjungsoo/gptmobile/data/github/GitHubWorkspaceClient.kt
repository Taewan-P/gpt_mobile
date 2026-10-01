package dev.chungjungsoo.gptmobile.data.github

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
data class GitHubRepositoryContext(val owner: String, val repo: String, val ref: String) {
    val fullName: String get() = "$owner/$repo"
}

/**
 * Direct GitHub integration shared by the workspace UI and AI tools.
 *
 * Agent-facing actions return compact task-shaped payloads. Raw REST and
 * GraphQL responses stay inside this layer so they do not waste model context.
 */
class GitHubWorkspaceClient(
    private val token: String,
    private val client: HttpClient = sharedClient,
    private val responseCache: GitHubResponseCache = GitHubResponseCache(),
    private val rateLimits: GitHubRateLimitManager = GitHubRateLimitManager()
) {
    companion object {
        const val API_VERSION = "2026-03-10"

        private val sharedClient by lazy {
            HttpClient(OkHttp) {
                followRedirects = false
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 30_000
                }
            }
        }
        private val json = Json { ignoreUnknownKeys = true }
        val readActions = setOf(
            "get_account", "list_repositories", "get_repository", "list_branches", "browse_files",
            "read_code", "get_branch_head", "get_pull_request_files", "get_commit_checks", "compare_refs",
            "repo_status", "repo_map", "find_symbol", "find_references", "find_tests", "related_files",
            "changed_since", "pr_context", "rate_limit_status", "plan_change",
            "list_releases", "get_release", "get_release_by_tag", "list_tags", "release_status"
        )
        val writeActions = setOf(
            "commit_files",
            "create_tag",
            "create_release",
            "update_release",
            "publish_release"
        )
        val actions = readActions + writeActions

        fun segment(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

        fun filePath(value: String): String {
            require(value.isNotBlank() && !value.startsWith('/') && value.split('/').none { it.isBlank() || it == "." || it == ".." } && '\\' !in value) {
                "Use a relative repository file path without empty or traversal segments."
            }
            return value.split('/').joinToString("/", transform = ::segment)
        }
    }

    suspend fun execute(action: String, args: JsonObject): JsonElement {
        fun field(name: String): String = args[name]?.jsonPrimitive?.content.orEmpty()
        fun required(name: String): String = field(name).also { require(it.isNotBlank()) { "$name is required." } }

        if (action == "plan_change") return GitHubOperationPlanner.plan(field("query"))
        if (action == "rate_limit_status") return rateLimitStatus()

        val page = field("page").ifBlank { "1" }.toInt()
        require(page in 1..1000) { "Page must be between 1 and 1000." }
        val pagination = "per_page=30&page=$page"
        if (action == "get_account") return request("/user", authenticated = true)
        if (action == "list_repositories") {
            return pageResult(
                request("/user/repos?sort=updated&$pagination", authenticated = true),
                page,
                "name", "full_name", "private", "owner", "default_branch", "permissions", "html_url"
            )
        }

        val owner = required("owner")
        val repo = required("repo")
        require(Regex("[A-Za-z0-9_.-]+").matches(owner) && Regex("[A-Za-z0-9_.-]+").matches(repo)) {
            "Invalid repository owner or name."
        }
        val root = "/repos/${segment(owner)}/${segment(repo)}"
        val ref = field("ref").ifBlank { field("branch") }
        val refQuery = ref.takeIf { it.isNotBlank() }?.let { "?ref=${segment(it)}" }.orEmpty()

        return when (action) {
            "get_repository" -> request(root)
            "repo_status" -> repoStatus(owner, repo, root)
            "list_branches" -> pageResult(request("$root/branches?$pagination"), page)
            "get_branch_head" -> request("$root/git/ref/heads/${segment(required("branch"))}")
            "browse_files" -> browseFiles(root, refQuery, field("path"), page)
            "read_code" -> readCode(root, refQuery, ref, required("path"), field("start_line"), field("end_line"))
            "repo_map" -> repoMap(root, ref, page)
            "find_symbol" -> codeSearch(owner, repo, required("query"), testsOnly = false)
            "find_references" -> codeSearch(owner, repo, required("query"), testsOnly = false)
            "find_tests" -> codeSearch(owner, repo, required("query"), testsOnly = true)
            "related_files" -> relatedFiles(root, ref, required("path"))
            "get_pull_request_files" -> {
                val number = required("pull_number").toInt().also { require(it > 0) }
                pageResult(request("$root/pulls/$number/files?$pagination"), page, "filename", "status", "additions", "deletions", "changes", "sha", "previous_filename", "patch")
            }
            "pr_context" -> prContext(root, required("pull_number").toInt().also { require(it > 0) })
            "get_commit_checks" -> compactChecks(request("$root/commits/${segment(required("ref"))}/check-runs?$pagination").jsonObject)
            "compare_refs" -> compactCompare(request("$root/compare/${segment(required("base"))}...${segment(required("head"))}?$pagination").jsonObject)
            "changed_since" -> {
                val head = field("head").ifBlank { ref.ifBlank { "HEAD" } }
                compactCompare(request("$root/compare/${segment(required("base"))}...${segment(head)}?per_page=100").jsonObject)
            }
            "list_releases" -> releasePageResult(request("$root/releases?$pagination"), page)
            "get_release" -> compactRelease(
                request("$root/releases/${required("release_id").toLong().also { require(it > 0) }}").jsonObject
            )
            "get_release_by_tag" -> compactRelease(request("$root/releases/tags/${segment(required("tag_name"))}").jsonObject)
            "list_tags" -> pageResult(request("$root/tags?$pagination"), page, "name", "commit", "zipball_url", "tarball_url")
            "release_status" -> releaseStatus(root, args)
            "create_tag" -> createTag(root, args)
            "create_release" -> createRelease(root, args)
            "update_release" -> updateRelease(root, args)
            "publish_release" -> publishRelease(root, args)
            "commit_files" -> commitFiles(root, args)
            else -> error("Unsupported workspace action: $action")
        }
    }

    private suspend fun browseFiles(root: String, refQuery: String, rawPath: String, page: Int): JsonObject {
        val path = rawPath.takeIf { it.isNotBlank() }?.let { "/${filePath(it)}" }.orEmpty()
        val result = request("$root/contents$path$refQuery")
        require(result is JsonArray) { "This path is a file. Use read_code to open it." }
        return buildJsonObject {
            put(
                "entries",
                buildJsonArray {
                    result.drop((page - 1) * 30).take(30).forEach { item ->
                        add(project(item.jsonObject, "name", "path", "type", "sha", "size", "html_url"))
                    }
                }
            )
            put("directory_limit_reached", result.size >= 1000)
            put("page", page)
            put("has_more", page * 30 < result.size)
        }
    }

    private suspend fun readCode(
        root: String,
        refQuery: String,
        ref: String,
        path: String,
        startField: String,
        endField: String
    ): JsonObject {
        val result = request("$root/contents/${filePath(path)}$refQuery").jsonObject
        require(result["type"]?.jsonPrimitive?.content == "file") { "Only regular text files can be opened." }
        require(result["encoding"]?.jsonPrimitive?.content == "base64") { "File is too large for this workspace. Read a smaller file." }
        val sha = result["sha"]?.jsonPrimitive?.content.orEmpty()
        val content = responseCache.getDecodedBlob(sha) ?: run {
            val bytes = Base64.getMimeDecoder().decode(result["content"]?.jsonPrimitive?.content.orEmpty())
            require(bytes.size <= 256_000 && bytes.none { it == 0.toByte() }) { "Workspace supports text files up to 256 KB." }
            bytes.decodeToString(throwOnInvalidSequence = true).also { responseCache.putDecodedBlob(sha, it) }
        }
        val lines = content.split('\n')
        val start = startField.ifBlank { "1" }.toInt()
        val end = endField.ifBlank { (start + 299).toString() }.toInt()
        require(start >= 1 && end >= start && end - start < 2000) { "Use a valid range of at most 2,000 lines." }
        val selected = lines.drop(start - 1).take(end - start + 1).joinToString("\n")
        require(selected.length <= 256_000) { "Selected lines exceed the text budget." }
        return buildJsonObject {
            put("path", result["path"] ?: JsonPrimitive(path))
            put("sha", result["sha"] ?: JsonNull)
            put("ref", ref)
            put("html_url", result["html_url"] ?: JsonNull)
            put("start_line", start)
            put("end_line", minOf(end, lines.size))
            put("total_lines", lines.size)
            put("has_more", end < lines.size)
            put("cache_key", sha)
            put("content", selected)
        }
    }

    private suspend fun repoStatus(owner: String, repo: String, root: String): JsonObject {
        val graphql = if (token.isNotBlank()) runCatching { repoStatusGraphQl(owner, repo) }.getOrNull() else null
        val latestRun = runCatching { request("$root/actions/runs?per_page=1").jsonObject }.getOrNull()
        val workflow = latestRun?.get("workflow_runs")?.jsonArray?.firstOrNull()?.jsonObject
        if (graphql != null) {
            return buildJsonObject {
                graphql.forEach { (key, value) -> put(key, value) }
                workflow?.let {
                    put("latest_workflow", project(it, "id", "name", "event", "status", "conclusion", "head_branch", "head_sha", "html_url", "created_at", "updated_at"))
                }
            }
        }

        val metadata = request(root).jsonObject
        val pulls = request("$root/pulls?state=open&per_page=5").jsonArray
        val issueQuery = segment("repo:$owner/$repo is:issue is:open")
        val issues = runCatching { request("/search/issues?q=$issueQuery&per_page=1").jsonObject }.getOrNull()
        return buildJsonObject {
            put("repository", project(metadata, "full_name", "default_branch", "private", "visibility", "pushed_at", "html_url"))
            put("open_issue_count", issues?.get("total_count") ?: JsonNull)
            put("open_pull_requests", compactPullRequests(pulls))
            workflow?.let {
                put("latest_workflow", project(it, "id", "name", "event", "status", "conclusion", "head_branch", "head_sha", "html_url", "created_at", "updated_at"))
            }
        }
    }

    private suspend fun repoStatusGraphQl(owner: String, repo: String): JsonObject {
        val query = """
            query RepositoryStatus(${'$'}owner: String!, ${'$'}name: String!) {
              repository(owner: ${'$'}owner, name: ${'$'}name) {
                nameWithOwner
                url
                isPrivate
                defaultBranchRef {
                  name
                  target {
                    ... on Commit {
                      oid
                      committedDate
                      messageHeadline
                    }
                  }
                }
                issues(states: OPEN) { totalCount }
                pullRequests(states: OPEN, first: 5, orderBy: {field: UPDATED_AT, direction: DESC}) {
                  totalCount
                  nodes {
                    number
                    title
                    isDraft
                    updatedAt
                    headRefName
                    baseRefName
                    url
                  }
                }
              }
              rateLimit { cost remaining resetAt }
            }
        """.trimIndent()
        val result = graphQl(
            query,
            buildJsonObject {
                put("owner", owner)
                put("name", repo)
            }
        )
        val errors = result["errors"]?.jsonArray
        require(errors.isNullOrEmpty()) { "GitHub GraphQL returned an error." }
        val data = result["data"]?.jsonObject ?: error("GitHub GraphQL returned no data.")
        val repository = data["repository"]?.jsonObject ?: error("Repository was not returned by GitHub.")
        val defaultRef = repository["defaultBranchRef"]?.jsonObject
        val commit = defaultRef?.get("target")?.jsonObject
        val pulls = repository["pullRequests"]?.jsonObject
        return buildJsonObject {
            put(
                "repository",
                buildJsonObject {
                    put("full_name", repository["nameWithOwner"] ?: JsonPrimitive("$owner/$repo"))
                    put("url", repository["url"] ?: JsonNull)
                    put("private", repository["isPrivate"] ?: JsonNull)
                    put("default_branch", defaultRef?.get("name") ?: JsonNull)
                    put(
                        "head",
                        buildJsonObject {
                            put("sha", commit?.get("oid") ?: JsonNull)
                            put("committed_at", commit?.get("committedDate") ?: JsonNull)
                            put("message", commit?.get("messageHeadline") ?: JsonNull)
                        }
                    )
                }
            )
            put("open_issue_count", repository["issues"]?.jsonObject?.get("totalCount") ?: JsonPrimitive(0))
            put("open_pull_request_count", pulls?.get("totalCount") ?: JsonPrimitive(0))
            put("open_pull_requests", pulls?.get("nodes") ?: JsonArray(emptyList()))
            data["rateLimit"]?.let { put("graphql_rate_limit", it) }
        }
    }

    private suspend fun repoMap(root: String, ref: String, page: Int): JsonObject {
        val target = ref.ifBlank {
            request(root).jsonObject["default_branch"]?.jsonPrimitive?.content ?: "HEAD"
        }
        return GitHubRepositoryIndex.compact(request("$root/git/trees/${segment(target)}?recursive=1").jsonObject, page)
    }

    private suspend fun relatedFiles(root: String, ref: String, path: String): JsonObject {
        filePath(path)
        val target = ref.ifBlank {
            request(root).jsonObject["default_branch"]?.jsonPrimitive?.content ?: "HEAD"
        }
        val tree = request("$root/git/trees/${segment(target)}?recursive=1").jsonObject
        return buildJsonObject {
            put("path", path)
            put("related", GitHubRepositoryIndex.related(tree, path))
            put("truncated", tree["truncated"] ?: JsonPrimitive(false))
        }
    }

    private suspend fun codeSearch(owner: String, repo: String, query: String, testsOnly: Boolean): JsonObject {
        val scoped = "$query repo:$owner/$repo"
        val result = request("/search/code?q=${segment(scoped)}&per_page=30").jsonObject
        val items = result["items"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { item ->
                if (!testsOnly) {
                    true
                } else {
                    val itemPath = item["path"]?.jsonPrimitive?.content.orEmpty().lowercase()
                    "/test" in itemPath || "/androidtest" in itemPath || itemPath.endsWith("test.kt") || itemPath.endsWith("test.java")
                }
            }
        return buildJsonObject {
            put("query", query)
            put("total_count", if (testsOnly) JsonPrimitive(items.size) else result["total_count"] ?: JsonPrimitive(items.size))
            put(
                "items",
                buildJsonArray {
                    items.take(30).forEach { item -> add(project(item, "name", "path", "sha", "html_url")) }
                }
            )
        }
    }

    private suspend fun prContext(root: String, number: Int): JsonObject {
        val pr = request("$root/pulls/$number").jsonObject
        val files = request("$root/pulls/$number/files?per_page=100").jsonArray
        val headSha = pr["head"]?.jsonObject?.get("sha")?.jsonPrimitive?.content.orEmpty()
        val checks =
            if (headSha.isNotBlank()) {
                runCatching { compactChecks(request("$root/commits/${segment(headSha)}/check-runs?per_page=100").jsonObject) }.getOrNull()
            } else {
                null
            }
        return buildJsonObject {
            put(
                "pull_request",
                buildJsonObject {
                    listOf("number", "title", "state", "draft", "merged", "mergeable", "mergeable_state", "html_url", "changed_files", "additions", "deletions").forEach { key ->
                        pr[key]?.let { put(key, it) }
                    }
                    pr["head"]?.jsonObject?.let { put("head", project(it, "ref", "sha")) }
                    pr["base"]?.jsonObject?.let { put("base", project(it, "ref", "sha")) }
                }
            )
            put(
                "files",
                buildJsonArray {
                    files.take(100).forEach { item ->
                        add(project(item.jsonObject, "filename", "status", "additions", "deletions", "changes", "sha", "previous_filename"))
                    }
                }
            )
            checks?.let { put("checks", it) }
        }
    }

    private fun compactChecks(result: JsonObject): JsonObject {
        val runs = result["check_runs"]?.jsonArray.orEmpty()
        return buildJsonObject {
            put("total_count", result["total_count"] ?: JsonPrimitive(runs.size))
            put(
                "check_runs",
                buildJsonArray {
                    runs.take(50).forEach { run ->
                        add(project(run.jsonObject, "id", "name", "status", "conclusion", "started_at", "completed_at", "html_url"))
                    }
                }
            )
        }
    }

    private fun compactCompare(result: JsonObject): JsonObject = buildJsonObject {
        listOf("status", "ahead_by", "behind_by", "total_commits", "html_url").forEach { key -> result[key]?.let { put(key, it) } }
        val files = result["files"]?.jsonArray.orEmpty()
        put(
            "files",
            buildJsonArray {
                files.take(100).forEach { file ->
                    add(project(file.jsonObject, "filename", "status", "additions", "deletions", "changes", "previous_filename", "sha"))
                }
            }
        )
        put("files_returned", minOf(files.size, 100))
        put("files_truncated", files.size > 100)
    }

    private fun compactPullRequests(items: JsonArray): JsonArray = buildJsonArray {
        items.take(5).forEach { item ->
            val obj = item.jsonObject
            add(
                buildJsonObject {
                    listOf("number", "title", "state", "draft", "updated_at", "html_url").forEach { key -> obj[key]?.let { put(key, it) } }
                    obj["head"]?.jsonObject?.let { put("head", project(it, "ref", "sha")) }
                    obj["base"]?.jsonObject?.let { put("base", project(it, "ref", "sha")) }
                }
            )
        }
    }

    private suspend fun rateLimitStatus(): JsonObject {
        val response = runCatching { request("/rate_limit") }.getOrNull()?.jsonObject
        val resources = response?.get("resources")?.jsonObject
        return buildJsonObject {
            put("observed_headers", rateLimits.toJson())
            resources?.let {
                put(
                    "resources",
                    buildJsonObject {
                        listOf("core", "search", "graphql").forEach { key -> it[key]?.let { value -> put(key, value) } }
                    }
                )
            }
        }
    }

    private suspend fun commitFiles(root: String, args: JsonObject): JsonObject {
        require(token.isNotBlank()) { "Connect a GitHub account before committing." }
        val branch = args["branch"]?.jsonPrimitive?.content.orEmpty()
        require(branch.isNotBlank()) { "An explicit working branch is required." }
        val expected = args["expected_head_sha"]?.jsonPrimitive?.content.orEmpty()
        require(Regex("[a-fA-F0-9]{40}").matches(expected)) { "Read the branch head first and supply expected_head_sha." }
        val message = args["message"]?.jsonPrimitive?.content.orEmpty()
        require(message.isNotBlank() && message.length <= 4000) { "A commit message of at most 4,000 characters is required." }
        val files = args["files"]?.jsonArray ?: error("files is required.")
        require(files.size in 1..20) { "Commit between 1 and 20 text files at a time." }
        val paths = files.map { it.jsonObject["path"]?.jsonPrimitive?.content.orEmpty().also { filePath(it) } }
        require(paths.distinct().size == paths.size) { "Duplicate file paths are not allowed." }
        val contents = files.map { it.jsonObject["content"]?.jsonPrimitive?.content ?: error("Every file needs UTF-8 content.") }
        require(contents.all { it.toByteArray().size <= 256_000 && '\u0000' !in it } && contents.sumOf { it.toByteArray().size } <= 1_000_000) {
            "Text change set exceeds the workspace budget."
        }
        val metadata = request(root).jsonObject
        require(branch != metadata["default_branch"]?.jsonPrimitive?.content) { "Create a working branch before committing changes." }
        val refPath = "$root/git/ref/heads/${segment(branch)}"
        val head = request(refPath).jsonObject["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
        check(head == expected) { "Branch changed since it was read. Refresh and review changes before retrying." }
        val baseCommit = request("$root/git/commits/$expected").jsonObject
        val baseTree = baseCommit["tree"]?.jsonObject?.get("sha")?.jsonPrimitive?.content ?: error("Missing base tree.")
        val existingTree = request("$root/git/trees/$baseTree?recursive=1").jsonObject
        check(existingTree["truncated"]?.jsonPrimitive?.booleanOrNull != true) { "Repository tree is incomplete. Cannot safely preserve file modes." }
        val entries = existingTree["tree"]?.jsonArray.orEmpty().associate { it.jsonObject["path"]?.jsonPrimitive?.content to it.jsonObject }
        val tree = buildJsonObject {
            put("base_tree", baseTree)
            put(
                "tree",
                buildJsonArray {
                    paths.forEachIndexed { index, file ->
                        val existing = entries[file]
                        val mode = existing?.get("mode")?.jsonPrimitive?.content ?: "100644"
                        require(mode in setOf("100644", "100755")) { "Cannot replace a directory, symlink or submodule: $file" }
                        require(
                            file.split('/').dropLast(1).indices.all { depth ->
                                entries[file.split('/').take(depth + 1).joinToString("/")]?.get("type")?.jsonPrimitive?.content.let { it == null || it == "tree" }
                            }
                        ) { "A parent path is not a directory: $file" }
                        add(
                            buildJsonObject {
                                put("path", file)
                                put("mode", mode)
                                put("type", "blob")
                                put("content", contents[index])
                            }
                        )
                    }
                }
            )
        }
        val treeSha = request("$root/git/trees", HttpMethod.Post, tree).jsonObject["sha"] ?: error("Missing new tree SHA.")
        val commit = request(
            "$root/git/commits",
            HttpMethod.Post,
            buildJsonObject {
                put("message", message)
                put("tree", treeSha)
                put("parents", buildJsonArray { add(JsonPrimitive(expected)) })
            }
        ).jsonObject
        val sha = commit["sha"] ?: error("Missing commit SHA.")
        request(
            "$root/git/refs/heads/${segment(branch)}",
            HttpMethod.Patch,
            buildJsonObject {
                put("sha", sha)
                put("force", false)
            }
        )
        responseCache.clear()
        return buildJsonObject {
            put("sha", sha)
            put("branch", branch)
            put("files_changed", files.size)
        }
    }

    private suspend fun createTag(root: String, args: JsonObject): JsonObject {
        require(token.isNotBlank()) { "Connect a GitHub account before creating a tag." }
        val tagName = args["tag_name"]?.jsonPrimitive?.content.orEmpty()
        require(tagName.isNotBlank() && !tagName.startsWith("/") && ".." !in tagName && !tagName.endsWith(".")) {
            "A valid tag_name is required."
        }
        val target = args["target_commitish"]?.jsonPrimitive?.content.orEmpty()
            .ifBlank { args["ref"]?.jsonPrimitive?.content.orEmpty() }
        require(target.isNotBlank()) { "target_commitish or ref is required for create_tag." }
        val commit = request("$root/commits/${segment(target)}").jsonObject
        val sha = commit["sha"]?.jsonPrimitive?.content ?: error("Could not resolve target commit.")
        val created = request(
            "$root/git/refs",
            HttpMethod.Post,
            buildJsonObject {
                put("ref", "refs/tags/$tagName")
                put("sha", sha)
            }
        ).jsonObject
        responseCache.clear()
        return buildJsonObject {
            put("tag_name", tagName)
            put("target_commitish", target)
            put("sha", sha)
            created["url"]?.let { put("url", it) }
        }
    }

    private suspend fun createRelease(root: String, args: JsonObject): JsonObject {
        require(token.isNotBlank()) { "Connect a GitHub account before creating a release." }
        val tagName = args["tag_name"]?.jsonPrimitive?.content.orEmpty()
        require(tagName.isNotBlank()) { "tag_name is required." }
        val bodyText = args["body"]?.jsonPrimitive?.content.orEmpty()
        val releaseName = args["release_name"]?.jsonPrimitive?.content.orEmpty()
            .ifBlank { args["title"]?.jsonPrimitive?.content.orEmpty() }
            .ifBlank { tagName }
        val payload = buildJsonObject {
            put("tag_name", tagName)
            args["target_commitish"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { put("target_commitish", it) }
            put("name", releaseName)
            if (bodyText.isNotBlank()) put("body", bodyText)
            put("draft", args["draft"]?.jsonPrimitive?.booleanOrNull ?: false)
            put("prerelease", args["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false)
            put("generate_release_notes", args["generate_release_notes"]?.jsonPrimitive?.booleanOrNull ?: bodyText.isBlank())
            args["make_latest"]?.jsonPrimitive?.content?.takeIf { it in setOf("true", "false", "legacy") }?.let { put("make_latest", it) }
        }
        val created = request("$root/releases", HttpMethod.Post, payload).jsonObject
        responseCache.clear()
        return compactRelease(created)
    }

    private suspend fun updateRelease(root: String, args: JsonObject): JsonObject {
        require(token.isNotBlank()) { "Connect a GitHub account before updating a release." }
        val releaseId = args["release_id"]?.jsonPrimitive?.content?.toLongOrNull()?.takeIf { it > 0 }
            ?: error("A positive release_id is required.")
        val payload = buildJsonObject {
            args["tag_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { put("tag_name", it) }
            args["target_commitish"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { put("target_commitish", it) }
            val releaseName = args["release_name"]?.jsonPrimitive?.content
                ?: args["title"]?.jsonPrimitive?.content
            releaseName?.let { put("name", it) }
            args["body"]?.jsonPrimitive?.content?.let { put("body", it) }
            args["draft"]?.jsonPrimitive?.booleanOrNull?.let { put("draft", it) }
            args["prerelease"]?.jsonPrimitive?.booleanOrNull?.let { put("prerelease", it) }
            args["make_latest"]?.jsonPrimitive?.content?.takeIf { it in setOf("true", "false", "legacy") }?.let { put("make_latest", it) }
        }
        require(payload.isNotEmpty()) { "Provide at least one release field to update." }
        val updated = request("$root/releases/$releaseId", HttpMethod.Patch, payload).jsonObject
        responseCache.clear()
        return compactRelease(updated)
    }

    private suspend fun publishRelease(root: String, args: JsonObject): JsonObject {
        require(token.isNotBlank()) { "Connect a GitHub account before publishing a release." }
        val tagName = args["tag_name"]?.jsonPrimitive?.content.orEmpty()
        require(tagName.isNotBlank()) { "tag_name is required for publish_release." }
        val strategy = args["release_strategy"]?.jsonPrimitive?.content.orEmpty().ifBlank { "auto" }
        require(strategy in setOf("auto", "workflow", "direct")) {
            "release_strategy must be auto, workflow, or direct."
        }
        val existing = findReleaseByTag(root, tagName)
        if (existing != null) {
            val isDraft = existing["draft"]?.jsonPrimitive?.booleanOrNull == true
            val wantsDraft = args["draft"]?.jsonPrimitive?.booleanOrNull ?: false
            if (isDraft && !wantsDraft) {
                val id = existing["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: error("Existing draft release has no id.")
                val published = request("$root/releases/$id", HttpMethod.Patch, buildJsonObject { put("draft", false) }).jsonObject
                responseCache.clear()
                return buildJsonObject {
                    put("status", "published_existing_draft")
                    put("strategy", "release_api")
                    put("release", compactRelease(published))
                }
            }
            return buildJsonObject {
                put("status", if (isDraft) "draft_exists" else "already_published")
                put("strategy", "existing_release")
                put("release", compactRelease(existing))
            }
        }
        if (strategy != "direct") {
            val workflow = findReleaseWorkflow(root, args["workflow_id"]?.jsonPrimitive?.content.orEmpty())
            if (workflow != null) {
                val workflowId = workflow["id"]?.jsonPrimitive?.content ?: error("Release workflow has no id.")
                val ref = args["ref"]?.jsonPrimitive?.content.orEmpty()
                    .ifBlank { args["target_commitish"]?.jsonPrimitive?.content.orEmpty() }
                    .ifBlank { request(root).jsonObject["default_branch"]?.jsonPrimitive?.content.orEmpty() }
                require(ref.isNotBlank()) { "Could not resolve a ref for the release workflow." }
                val versionPreflight = validateReleaseVersionHint(root, ref, tagName)
                request(
                    "$root/actions/workflows/${segment(workflowId)}/dispatches",
                    HttpMethod.Post,
                    buildJsonObject {
                        put("ref", ref)
                        args["inputs"]?.let {
                            require(it is JsonObject) { "inputs must be an object." }
                            put("inputs", it)
                        }
                    }
                )
                responseCache.clear()
                return buildJsonObject {
                    put("status", "workflow_dispatched")
                    put("strategy", "workflow")
                    put("tag_name", tagName)
                    put("ref", ref)
                    put("workflow", workflow)
                    put("version_preflight", versionPreflight ?: JsonNull)
                    put("verification_action", "release_status")
                    put("message", "Release workflow accepted. Use release_status to verify publication and assets.")
                }
            }
            if (strategy == "workflow") error("No active release workflow was found. Specify workflow_id or use release_strategy=direct.")
        }
        val created = createRelease(root, args)
        return buildJsonObject {
            put("status", "created")
            put("strategy", "release_api")
            put("release", created)
        }
    }

    private suspend fun releaseStatus(root: String, args: JsonObject): JsonObject {
        val tagName = args["tag_name"]?.jsonPrimitive?.content.orEmpty()
        require(tagName.isNotBlank()) { "tag_name is required for release_status." }
        val release = findReleaseByTag(root, tagName)
        val workflowLookup = runCatching {
            findReleaseWorkflow(root, args["workflow_id"]?.jsonPrimitive?.content.orEmpty())
        }
        val workflow = workflowLookup.getOrNull()
        val latestRun = workflow?.get("id")?.jsonPrimitive?.content?.let { workflowId ->
            val runs = request("$root/actions/workflows/${segment(workflowId)}/runs?per_page=10").jsonObject["workflow_runs"]?.jsonArray ?: JsonArray(emptyList())
            val requestedRef = args["ref"]?.jsonPrimitive?.content.orEmpty()
            val match = if (requestedRef.isBlank()) runs.firstOrNull() else runs.firstOrNull { it.jsonObject["head_branch"]?.jsonPrimitive?.content == requestedRef }
            match?.jsonObject
        }
        return buildJsonObject {
            put("tag_name", tagName)
            put("published", release != null && release["draft"]?.jsonPrimitive?.booleanOrNull != true)
            put("release", release?.let(::compactRelease) ?: JsonNull)
            put("workflow", workflow ?: JsonNull)
            workflowLookup.exceptionOrNull()?.message?.let { put("workflow_discovery_error", it) }
            put("latest_workflow_run", latestRun?.let { project(it, "id", "name", "event", "status", "conclusion", "head_branch", "head_sha", "run_number", "run_attempt", "created_at", "updated_at", "html_url") } ?: JsonNull)
        }
    }

    private suspend fun findReleaseByTag(root: String, tagName: String): JsonObject? {
        for (page in 1..10) {
            val releases = request("$root/releases?per_page=100&page=$page").jsonArray
            releases.firstOrNull {
                it.jsonObject["tag_name"]?.jsonPrimitive?.content == tagName
            }?.jsonObject?.let { return it }
            if (releases.size < 100) return null
        }
        error("Release history exceeds the safe lookup limit. Narrow the repository release history before publishing this tag.")
    }

    private suspend fun validateReleaseVersionHint(root: String, ref: String, tagName: String): JsonObject? {
        val file = runCatching {
            request("$root/contents/app/build.gradle.kts?ref=${segment(ref)}").jsonObject
        }.getOrNull() ?: return null
        if (file["encoding"]?.jsonPrimitive?.content != "base64") return null
        val encoded = file["content"]?.jsonPrimitive?.content?.replace("\n", "").orEmpty()
        if (encoded.isBlank()) return null
        val text = runCatching { Base64.getDecoder().decode(encoded).decodeToString() }.getOrNull() ?: return null
        val version = Regex("versionName\\s*=\\s*\\\"([^\\\"]+)\\\"").find(text)?.groupValues?.getOrNull(1) ?: return null
        val expectedTag = "v$version"
        require(tagName == expectedTag) {
            "Requested release tag $tagName does not match app versionName $version ($expectedTag). Update versionName/versionCode before dispatching the release workflow."
        }
        return buildJsonObject {
            put("source", "app/build.gradle.kts")
            put("version_name", version)
            put("expected_tag", expectedTag)
            put("matched", true)
        }
    }

    private suspend fun findReleaseWorkflow(root: String, preferred: String): JsonObject? {
        if (preferred.isNotBlank()) {
            val workflow = request("$root/actions/workflows/${segment(preferred)}").jsonObject
            return project(workflow, "id", "name", "path", "state", "html_url")
        }
        val workflows = request("$root/actions/workflows?per_page=100").jsonObject["workflows"]?.jsonArray ?: JsonArray(emptyList())
        return workflows.map { it.jsonObject }
            .filter { it["state"]?.jsonPrimitive?.content == "active" }
            .filter {
                val haystack = listOf(it["name"]?.jsonPrimitive?.content.orEmpty(), it["path"]?.jsonPrimitive?.content.orEmpty()).joinToString(" ").lowercase()
                "release" in haystack || "publish" in haystack
            }
            .maxByOrNull {
                val name = it["name"]?.jsonPrimitive?.content.orEmpty().lowercase()
                val workflowPath = it["path"]?.jsonPrimitive?.content.orEmpty().lowercase()
                when {
                    "publish signed release" in name -> 100
                    workflowPath.endsWith("/release-build.yml") -> 90
                    "publish" in name -> 80
                    "release" in name -> 70
                    "publish" in workflowPath -> 60
                    else -> 50
                }
            }
            ?.let { project(it, "id", "name", "path", "state", "html_url") }
    }

    private fun releasePageResult(value: JsonElement, page: Int): JsonObject = buildJsonObject {
        val items = value.jsonArray
        put("items", buildJsonArray { items.forEach { add(compactRelease(it.jsonObject)) } })
        put("page", page)
        put("has_more", items.size == 30)
    }

    private fun compactRelease(value: JsonObject): JsonObject = buildJsonObject {
        listOf("id", "tag_name", "target_commitish", "name", "draft", "prerelease", "created_at", "published_at", "html_url").forEach { key -> value[key]?.let { put(key, it) } }
        val assets = value["assets"]?.jsonArray ?: JsonArray(emptyList())
        put("asset_count", assets.size)
        put(
            "assets",
            buildJsonArray {
                assets.take(30).forEach { asset ->
                    add(project(asset.jsonObject, "id", "name", "content_type", "state", "size", "digest", "download_count", "created_at", "updated_at", "browser_download_url"))
                }
            }
        )
    }

    private fun project(value: JsonObject, vararg keys: String): JsonObject = buildJsonObject {
        keys.forEach { key ->
            value[key]?.let { item ->
                put(key, if (key == "owner") buildJsonObject { item.jsonObject["login"]?.let { put("login", it) } } else item)
            }
        }
    }

    private fun pageResult(value: JsonElement, page: Int, vararg fields: String): JsonObject = buildJsonObject {
        val items = value.jsonArray
        put("items", if (fields.isEmpty()) items else buildJsonArray { items.forEach { add(project(it.jsonObject, *fields)) } })
        put("page", page)
        put("has_more", items.size == 30)
    }

    private suspend fun graphQl(query: String, variables: JsonObject): JsonObject {
        require(token.isNotBlank()) { "A GitHub credential is required for GraphQL." }
        val response = client.request("https://api.github.com/graphql") {
            method = HttpMethod.Post
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "GPT-Mobile-App")
            header("X-GitHub-Api-Version", API_VERSION)
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("query", query)
                    put("variables", variables)
                }.toString()
            )
        }
        rateLimits.record(response.headers)
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("GitHub GraphQL HTTP ${response.status.value}. ${requestHint(response.status.value, response.headers["Retry-After"], response.headers["X-RateLimit-Reset"])}")
        }
        return json.parseToJsonElement(text).jsonObject
    }

    private suspend fun request(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        body: JsonObject? = null,
        authenticated: Boolean = false
    ): JsonElement {
        if (authenticated || method != HttpMethod.Get) require(token.isNotBlank()) { "A GitHub credential is required." }
        val cacheKey = "${method.value}:$path"
        val cacheable = method == HttpMethod.Get && path != "/rate_limit"
        val cached = if (cacheable) responseCache.get(cacheKey) else null
        val response = client.request("https://api.github.com$path") {
            this.method = method
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "GPT-Mobile-App")
            header("X-GitHub-Api-Version", API_VERSION)
            if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
            cached?.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        rateLimits.record(response.headers)
        if (response.status.value == 304 && cached != null) return cached.value

        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("GitHub HTTP ${response.status.value}. ${requestHint(response.status.value, response.headers["Retry-After"], response.headers["X-RateLimit-Reset"])}")
        }
        val parsed = if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
        if (cacheable) responseCache.put(cacheKey, response.headers[HttpHeaders.ETag], parsed)
        return parsed
    }

    private fun requestHint(status: Int, retryAfter: String?, reset: String?): String = when (status) {
        401 -> "Reconnect your GitHub account."
        403, 429 -> "Check token permissions or GitHub rate limits. Retry after: ${retryAfter ?: reset ?: "not supplied"}."
        404 -> "Check repository access, branch and path."
        409, 422 -> "Refresh the branch and review the change before retrying."
        else -> "Try again later."
    }
}
