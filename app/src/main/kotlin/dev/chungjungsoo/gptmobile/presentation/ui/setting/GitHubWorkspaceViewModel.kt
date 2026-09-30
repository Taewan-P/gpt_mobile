package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.agent.tool.GitHubTool
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.github.GitHubRepositoryContext
import dev.chungjungsoo.gptmobile.data.github.GitHubWorkspaceClient
import dev.chungjungsoo.gptmobile.data.github.GitHubWorkspaceStore
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class GitHubStagedFile(val path: String, val original: String, val content: String)

data class GitHubWorkspaceState(
    val connection: ToolConnection? = null,
    val login: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val repositories: List<JsonObject> = emptyList(),
    val repositoryPage: Int = 1,
    val moreRepositories: Boolean = false,
    val selection: GitHubRepositoryContext? = null,
    val defaultBranch: String = "",
    val canWrite: Boolean = false,
    val branches: List<String> = emptyList(),
    val branchPage: Int = 1,
    val moreBranches: Boolean = false,
    val headSha: String = "",
    val directory: String = "",
    val entries: List<JsonObject> = emptyList(),
    val directoryPage: Int = 1,
    val moreEntries: Boolean = false,
    val file: JsonObject? = null,
    val staged: List<GitHubStagedFile> = emptyList(),
    val pullRequests: List<JsonObject> = emptyList(),
    val pullDetail: String? = null,
    val pullPage: Int = 1,
    val morePullRequests: Boolean = false
)

@HiltViewModel
class GitHubWorkspaceViewModel @Inject constructor(
    private val connections: ToolConnectionRepository,
    private val vault: SecretVault,
    private val store: GitHubWorkspaceStore
) : ViewModel() {
    private val _state = MutableStateFlow(GitHubWorkspaceState())
    val state = _state.asStateFlow()
    private var client: GitHubWorkspaceClient? = null
    private var tool: GitHubTool? = null

    fun open(connection: ToolConnection) {
        if (_state.value.busy) return
        _state.value = GitHubWorkspaceState(connection = connection)
        work {
            val current = connections.getConnection(connection.connectionUid) ?: error("Connection was removed.")
            val bytes = current.secretRef?.let { vault.read(it) }
            val token = try {
                bytes?.decodeToString()?.trim().orEmpty()
            } finally {
                bytes?.fill(0)
            }
            require(token.isNotBlank()) { "Edit this GitHub connection and add a token first." }
            client = GitHubWorkspaceClient(token)
            tool = GitHubTool(token)
            val account = api("get_account").jsonObject
            _state.update { it.copy(connection = current, login = account.text("login")) }
            loadRepositories(1)
            store.get(current.connectionUid)?.let { selectRepositoryInternal(it.owner, it.repo, it.ref) }
        }
    }

    fun close() {
        client = null
        tool = null
        _state.value = GitHubWorkspaceState()
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun discard() = _state.update { it.copy(staged = emptyList()) }
    fun moreRepositories() = work { loadRepositories(_state.value.repositoryPage + 1) }

    private suspend fun loadRepositories(page: Int) {
        val result = api("list_repositories", buildJsonObject { put("page", page) }).jsonObject
        val items = result["items"]?.jsonArray.orEmpty().map { it.jsonObject }
        _state.update { it.copy(repositories = if (page == 1) items else (it.repositories + items).distinctBy { repo -> repo.text("full_name") }, repositoryPage = page, moreRepositories = result.flag("has_more")) }
    }

    fun selectRepository(owner: String, repo: String) = work {
        require(_state.value.staged.isEmpty()) { "Commit or discard staged edits before switching repositories." }
        selectRepositoryInternal(owner, repo)
    }

    private suspend fun selectRepositoryInternal(owner: String, repo: String, preferredRef: String? = null) {
        val metadata = requireNotNull(client).execute(
            "get_repository",
            buildJsonObject {
                put("owner", owner)
                put("repo", repo)
            }
        ).jsonObject
        val default = metadata.text("default_branch")
        require(default.isNotBlank()) { "Repository has no default branch." }
        val selected = GitHubRepositoryContext(owner, repo, preferredRef ?: default)
        val canWrite = metadata["permissions"]?.jsonObject?.flag("push") == true && !metadata.flag("archived") && _state.value.connection?.toolPolicy != "READ_ONLY"
        _state.update { it.copy(selection = selected, defaultBranch = default, canWrite = canWrite, branches = emptyList(), file = null, staged = emptyList(), pullRequests = emptyList(), pullDetail = null, directory = "", headSha = "", entries = emptyList()) }
        loadBranches(1)
        loadHeadAndDirectory()
    }

    fun moreBranches() = work { loadBranches(_state.value.branchPage + 1) }
    private suspend fun loadBranches(page: Int) {
        val result = api("list_branches", buildJsonObject { put("page", page) }).jsonObject
        val branches = result["items"]?.jsonArray.orEmpty().map { it.jsonObject.text("name") }
        _state.update { it.copy(branches = if (page == 1) branches else (it.branches + branches).distinct(), branchPage = page, moreBranches = result.flag("has_more")) }
    }

    fun selectBranch(branch: String) = work {
        require(_state.value.staged.isEmpty()) { "Commit or discard staged edits before switching branches." }
        _state.update { it.copy(selection = it.selection?.copy(ref = branch), directory = "", file = null, entries = emptyList(), headSha = "") }
        loadHeadAndDirectory()
    }

    fun refreshBranch() = work {
        require(_state.value.staged.isEmpty()) { "Discard or commit staged edits before refreshing the snapshot." }
        loadHeadAndDirectory()
    }

    private suspend fun loadHeadAndDirectory() {
        val selected = requireNotNull(_state.value.selection)
        val result = api("get_branch_head", buildJsonObject { put("branch", selected.ref) }).jsonObject
        val sha = result["object"]?.jsonObject?.text("sha").orEmpty()
        check(sha.isNotBlank()) { "Missing branch head." }
        _state.update { it.copy(headSha = sha, file = null) }
        browseInternal(_state.value.directory)
    }

    fun browse(path: String) = work { browseInternal(path) }
    fun moreFiles() = work { browseInternal(_state.value.directory, _state.value.directoryPage + 1) }
    private suspend fun browseInternal(path: String, page: Int = 1) {
        val result = api(
            "browse_files",
            buildJsonObject {
                put("path", path)
                put("page", page)
                put("ref", _state.value.headSha)
            }
        ).jsonObject
        _state.update { it.copy(directory = path, directoryPage = page, moreEntries = result.flag("has_more"), entries = ((if (page == 1) emptyList() else it.entries) + result["entries"]?.jsonArray.orEmpty().map { entry -> entry.jsonObject }).distinctBy { entry -> entry.text("path") }.sortedWith(compareBy<JsonObject> { entry -> entry.text("type") != "dir" }.thenBy { entry -> entry.text("name") }), file = null, notice = if (result.flag("directory_limit_reached")) "GitHub limited this directory to 1,000 entries." else it.notice) }
    }

    fun readFile(path: String) = work {
        val file = api(
            "read_code",
            buildJsonObject {
                put("path", path)
                put("ref", _state.value.headSha)
                put("end_line", 2000)
            }
        ).jsonObject
        _state.update { it.copy(file = file) }
    }

    fun stage(path: String, original: String, content: String): Boolean {
        if (content.toByteArray().size > 256_000) {
            _state.update { it.copy(error = "File exceeds the text budget.") }
            return false
        }
        var accepted = false
        _state.update { state ->
            val existing = state.staged.firstOrNull { it.path == path }
            val actualOriginal = existing?.original ?: original
            val remaining = state.staged.filterNot { it.path == path }
            val next = if (actualOriginal == content) remaining else remaining + GitHubStagedFile(path, actualOriginal, content)
            if (next.size > 20 || next.sumOf { it.content.toByteArray().size } > 1_000_000) {
                state.copy(error = "Stage at most 20 files and 1 MB of text.")
            } else {
                accepted = true
                state.copy(staged = next, notice = "Changes staged for review.")
            }
        }
        return accepted
    }

    fun useInChats() {
        val state = _state.value
        state.connection?.let { store.set(it.connectionUid, state.selection) }
        _state.update { it.copy(notice = "${it.selection?.fullName} @ ${it.selection?.ref} is now the default for this GitHub connection in chats.") }
    }

    fun clearChatContext() {
        _state.value.connection?.let { store.set(it.connectionUid, null) }
        _state.update { it.copy(notice = "Default GitHub repository context cleared.") }
    }

    fun createBranch(name: String) = work {
        val state = _state.value
        require(state.canWrite && state.staged.isEmpty()) { "Commit or discard staged changes first; write access is required." }
        toolRequest(
            "create_branch",
            buildJsonObject {
                put("branch", name.trim())
                put("base", requireNotNull(state.selection).ref)
            }
        )
        _state.update { it.copy(selection = it.selection?.copy(ref = name.trim()), notice = "Working branch created.", directory = "") }
        loadBranches(1)
        loadHeadAndDirectory()
    }

    fun commit(message: String) = work {
        val state = _state.value
        require(state.canWrite) { "Repository write access is required." }
        val result = api(
            "commit_files",
            buildJsonObject {
                put("branch", requireNotNull(state.selection).ref)
                put("expected_head_sha", state.headSha)
                put("message", message)
                put(
                    "files",
                    buildJsonArray {
                        state.staged.forEach { file ->
                            add(
                                buildJsonObject {
                                    put("path", file.path)
                                    put("content", file.content)
                                }
                            )
                        }
                    }
                )
            }
        ).jsonObject
        _state.update { it.copy(staged = emptyList(), headSha = result.text("sha"), file = null, notice = "Committed ${state.staged.size} file(s) to ${state.selection?.ref}.") }
        browseInternal(state.directory)
    }

    fun createPullRequest(title: String, body: String) = work {
        val state = _state.value
        require(state.canWrite && state.staged.isEmpty() && state.selection?.ref != state.defaultBranch) { "Commit changes on a working branch before opening a pull request." }
        val result = toolRequest(
            "create_pull_request",
            buildJsonObject {
                put("title", title)
                put("body", body)
                put("head", requireNotNull(state.selection).ref)
                put("base", state.defaultBranch)
                put("draft", true)
            }
        )
        val url = Json.parseToJsonElement(result).jsonObject.text("html_url")
        _state.update { it.copy(notice = "Draft pull request created: $url") }
        loadPullRequestsInternal(1)
    }

    fun loadPullRequests() = work { loadPullRequestsInternal(1) }
    fun morePullRequests() = work { loadPullRequestsInternal(_state.value.pullPage + 1) }
    private suspend fun loadPullRequestsInternal(page: Int) {
        val result = Json.parseToJsonElement(toolRequest("list_pull_requests", buildJsonObject { put("page", page) })).jsonArray.map { it.jsonObject }
        _state.update { it.copy(pullRequests = if (page == 1) result else (it.pullRequests + result).distinctBy { pr -> pr.text("number") }, pullPage = page, morePullRequests = result.size == 20, pullDetail = null) }
    }

    fun inspectPullRequest(number: Int, page: Int = 1) = work {
        val result = api(
            "get_pull_request_files",
            buildJsonObject {
                put("pull_number", number)
                put("page", page)
            }
        ).jsonObject
        val pr = Json.parseToJsonElement(toolRequest("get_pull_request", buildJsonObject { put("pull_number", number) })).jsonObject
        val sha = pr["head"]?.jsonObject?.text("sha").orEmpty()
        val checks = api("get_commit_checks", buildJsonObject { put("ref", sha) })
        val detail = buildString {
            appendLine("#${pr.text("number")} ${pr.text("title")}")
            appendLine("${pr.text("state")} · ${pr["head"]?.jsonObject?.text("ref")} → ${pr["base"]?.jsonObject?.text("ref")}")
            appendLine(pr.text("body"))
            result["items"]?.jsonArray?.forEach { item ->
                val file = item.jsonObject
                appendLine("\n${file.text("filename")} · ${file.text("status")}")
                appendLine(file["patch"]?.jsonPrimitive?.content ?: "Patch unavailable; open this file on GitHub.")
            }
            if (result.flag("has_more")) appendLine("\nMore changed files available on GitHub.")
            appendLine("\nChecks: $checks")
        }
        _state.update { it.copy(pullDetail = detail) }
    }

    private fun work(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "GitHub request failed.") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun arguments(extra: JsonObject): JsonObject = buildJsonObject {
        _state.value.selection?.let {
            put("owner", it.owner)
            put("repo", it.repo)
            put("ref", it.ref)
        }
        extra.forEach { (key, value) -> put(key, value) }
    }

    private suspend fun api(action: String, extra: JsonObject = buildJsonObject {}) = requireNotNull(client).execute(action, arguments(extra))

    private suspend fun toolRequest(action: String, extra: JsonObject): String {
        val result = requireNotNull(tool).execute(
            "workspace",
            buildJsonObject {
                arguments(extra).forEach { (key, value) -> put(key, value) }
                put("action", action)
            }
        )
        val text = (result.content as ToolResultContent.Text).text
        check(!result.isError) { text }
        return text
    }
}

internal fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.takeUnless { it.toString() == "null" }?.content.orEmpty()
internal fun JsonObject.flag(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull == true
