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

/** Direct GitHub integration shared by the workspace UI and AI tools; no MCP server required. */
class GitHubWorkspaceClient(private val token: String, private val client: HttpClient = sharedClient) {
    companion object {
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
            "read_code", "get_branch_head", "get_pull_request_files", "get_commit_checks", "compare_refs"
        )
        val actions = readActions + "commit_files"

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
        val page = field("page").ifBlank { "1" }.toInt()
        require(page in 1..1000) { "Page must be between 1 and 1000." }
        val pagination = "per_page=30&page=$page"
        if (action == "get_account") return request("/user", authenticated = true)
        if (action == "list_repositories") return pageResult(request("/user/repos?sort=updated&$pagination", authenticated = true), page, "name", "full_name", "private", "owner", "default_branch", "permissions", "html_url")
        val owner = required("owner")
        val repo = required("repo")
        require(Regex("[A-Za-z0-9_.-]+").matches(owner) && Regex("[A-Za-z0-9_.-]+").matches(repo)) { "Invalid repository owner or name." }
        val root = "/repos/${segment(owner)}/${segment(repo)}"
        val ref = field("ref").ifBlank { field("branch") }
        val refQuery = ref.takeIf { it.isNotBlank() }?.let { "?ref=${segment(it)}" }.orEmpty()
        return when (action) {
            "get_repository" -> request(root)
            "list_branches" -> pageResult(request("$root/branches?$pagination"), page)
            "get_branch_head" -> request("$root/git/ref/heads/${segment(required("branch"))}")
            "browse_files" -> {
                val path = field("path").takeIf { it.isNotBlank() }?.let { "/${filePath(it)}" }.orEmpty()
                val result = request("$root/contents$path$refQuery")
                require(result is JsonArray) { "This path is a file. Use read_code to open it." }
                buildJsonObject {
                    put(
                        "entries",
                        buildJsonArray {
                            result.drop((page - 1) * 30).take(30).forEach { item -> add(project(item.jsonObject, "name", "path", "type", "sha", "size", "html_url")) }
                        }
                    )
                    put("directory_limit_reached", result.size >= 1000)
                    put("page", page)
                    put("has_more", page * 30 < result.size)
                }
            }
            "read_code" -> {
                val result = request("$root/contents/${filePath(required("path"))}$refQuery").jsonObject
                require(result["type"]?.jsonPrimitive?.content == "file") { "Only regular text files can be opened." }
                require(result["encoding"]?.jsonPrimitive?.content == "base64") { "File is too large for this workspace. Read a smaller file." }
                val bytes = Base64.getMimeDecoder().decode(result["content"]?.jsonPrimitive?.content.orEmpty())
                require(bytes.size <= 256_000 && bytes.none { it == 0.toByte() }) { "Workspace supports text files up to 256 KB." }
                val content = bytes.decodeToString(throwOnInvalidSequence = true)
                val lines = content.split('\n')
                val start = field("start_line").ifBlank { "1" }.toInt()
                val end = field("end_line").ifBlank { (start + 299).toString() }.toInt()
                require(start >= 1 && end >= start && end - start < 2000) { "Use a valid range of at most 2,000 lines." }
                val selected = lines.drop(start - 1).take(end - start + 1).joinToString("\n")
                require(selected.length <= 256_000) { "Selected lines exceed the text budget." }
                buildJsonObject {
                    put("path", result["path"] ?: JsonPrimitive(required("path")))
                    put("sha", result["sha"] ?: JsonNull)
                    put("ref", ref)
                    put("html_url", result["html_url"] ?: JsonNull)
                    put("start_line", start)
                    put("end_line", minOf(end, lines.size))
                    put("total_lines", lines.size)
                    put("has_more", end < lines.size)
                    put("content", selected)
                }
            }
            "get_pull_request_files" -> {
                val number = required("pull_number").toInt().also { require(it > 0) }
                pageResult(request("$root/pulls/$number/files?$pagination"), page)
            }
            "get_commit_checks" -> request("$root/commits/${segment(required("ref"))}/check-runs?$pagination")
            "compare_refs" -> request("$root/compare/${segment(required("base"))}...${segment(required("head"))}?$pagination")
            "commit_files" -> commitFiles(root, args)
            else -> error("Unsupported workspace action: $action")
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
        require(contents.all { it.toByteArray().size <= 256_000 && '\u0000' !in it } && contents.sumOf { it.toByteArray().size } <= 1_000_000) { "Text change set exceeds the workspace budget." }
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
                    paths.forEachIndexed { index, path ->
                        val existing = entries[path]
                        val mode = existing?.get("mode")?.jsonPrimitive?.content ?: "100644"
                        require(mode in setOf("100644", "100755")) { "Cannot replace a directory, symlink or submodule: $path" }
                        require(
                            path.split('/').dropLast(1).indices.all { depth ->
                                entries[path.split('/').take(depth + 1).joinToString("/")]?.get("type")?.jsonPrimitive?.content.let { it == null || it == "tree" }
                            }
                        ) { "A parent path is not a directory: $path" }
                        add(
                            buildJsonObject {
                                put("path", path)
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
        // Never force: a concurrent writer causes a non-fast-forward failure, preserving their work.
        request(
            "$root/git/refs/heads/${segment(branch)}",
            HttpMethod.Patch,
            buildJsonObject {
                put("sha", sha)
                put("force", false)
            }
        )
        return buildJsonObject {
            put("sha", sha)
            put("branch", branch)
            put("files_changed", files.size)
        }
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

    private suspend fun request(path: String, method: HttpMethod = HttpMethod.Get, body: JsonObject? = null, authenticated: Boolean = false): JsonElement {
        if (authenticated || method != HttpMethod.Get) require(token.isNotBlank()) { "A GitHub credential is required." }
        val response = client.request("https://api.github.com$path") {
            this.method = method
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "GPT-Mobile-App")
            header("X-GitHub-Api-Version", "2022-11-28")
            if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val hint = when (response.status.value) {
                401 -> "Reconnect your GitHub account."
                403, 429 -> "Check token permissions or GitHub rate limits. Retry after: ${response.headers["Retry-After"] ?: response.headers["X-RateLimit-Reset"] ?: "not supplied"}."
                404 -> "Check repository access, branch and path."
                409, 422 -> "Refresh the branch and review the change before retrying."
                else -> "Try again later."
            }
            error("GitHub HTTP ${response.status.value}. $hint")
        }
        return json.parseToJsonElement(text)
    }
}
