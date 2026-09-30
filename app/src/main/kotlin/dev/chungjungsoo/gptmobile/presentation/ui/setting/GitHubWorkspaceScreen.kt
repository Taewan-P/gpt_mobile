package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubWorkspaceScreen(connection: ToolConnection, onDismiss: () -> Unit, viewModel: GitHubWorkspaceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var repositoryMenu by remember { mutableStateOf(false) }
    var branchMenu by remember { mutableStateOf(false) }
    var repositoryQuery by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var prTitle by remember { mutableStateOf("") }
    var prBody by remember { mutableStateOf("") }
    var confirmCommit by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var editorDirty by remember { mutableStateOf(false) }
    LaunchedEffect(connection.connectionUid) { viewModel.open(connection) }
    fun close() {
        if (state.busy) return
        if (state.staged.isNotEmpty() || editorDirty) {
            confirmClose = true
        } else {
            viewModel.close()
            onDismiss()
        }
    }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(title = { Text("GitHub workspace") }, navigationIcon = { TextButton(onClick = { close() }, enabled = !state.busy) { Text("Close") } })
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.login.isBlank()) connection.name else "${connection.name} · @${state.login}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        TextButton(onClick = { repositoryMenu = true }, enabled = !state.busy && !editorDirty && state.staged.isEmpty()) { Text(state.selection?.fullName ?: "Select repository") }
                        DropdownMenu(expanded = repositoryMenu, onDismissRequest = { repositoryMenu = false }) {
                            OutlinedTextField(value = repositoryQuery, onValueChange = { repositoryQuery = it }, label = { Text("Filter loaded repositories") }, modifier = Modifier.padding(8.dp), singleLine = true)
                            state.repositories.filter { it.text("full_name").contains(repositoryQuery, ignoreCase = true) }.forEach { repository ->
                                DropdownMenuItem(text = { Text(repository.text("full_name") + if (repository.flag("private")) " · Private" else "") }, onClick = {
                                    repositoryMenu = false
                                    viewModel.selectRepository(repository["owner"]!!.jsonObject.text("login"), repository.text("name"))
                                })
                            }
                            if (state.moreRepositories) DropdownMenuItem(text = { Text("Load more repositories") }, enabled = !state.busy, onClick = viewModel::moreRepositories)
                        }
                    }
                    Row {
                        TextButton(onClick = { branchMenu = true }, enabled = !state.busy && !editorDirty && state.selection != null && state.staged.isEmpty()) { Text("Branch: ${state.selection?.ref ?: "—"}") }
                        DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                            state.branches.forEach { branch ->
                                DropdownMenuItem(text = { Text(branch) }, onClick = {
                                    branchMenu = false
                                    viewModel.selectBranch(branch)
                                })
                            }
                            if (state.moreBranches) DropdownMenuItem(text = { Text("Load more branches") }, enabled = !state.busy, onClick = viewModel::moreBranches)
                        }
                        TextButton(onClick = viewModel::refreshBranch, enabled = !state.busy && !editorDirty && state.selection != null && state.staged.isEmpty()) { Text("Refresh") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = viewModel::useInChats, enabled = !state.busy && state.headSha.isNotBlank()) { Text("Use repository in chats") }
                        TextButton(onClick = viewModel::clearChatContext, enabled = !state.busy) { Text("Clear chat context") }
                    }
                    state.error?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = viewModel::clearError) { Text("Dismiss error") }
                    }
                    state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 80.dp).verticalScroll(rememberScrollState())) }
                }
                TabRow(selectedTabIndex = tab) {
                    listOf("Code", "Changes (${state.staged.size})", "Pull requests").forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = {
                            tab = index
                            if (index == 2 && state.selection != null) viewModel.loadPullRequests()
                        }, text = { Text(label) })
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.selection == null) {
                        Text("Choose a repository to browse code, prepare changes and open a pull request. Your account's repository permissions apply.")
                    } else {
                        when (tab) {
                            0 -> {
                                Text("${state.directory.ifBlank { "/" }} · Snapshot ${state.headSha.take(8)}", style = MaterialTheme.typography.labelMedium)
                                if (state.directory.isNotBlank()) TextButton(onClick = { viewModel.browse(state.directory.substringBeforeLast('/', "")) }, enabled = !state.busy) { Text("↑ Parent directory") }
                                if (state.file == null) {
                                    state.entries.forEach { entry ->
                                        TextButton(onClick = {
                                            if (entry.text("type") == "dir") viewModel.browse(entry.text("path")) else viewModel.readFile(entry.text("path"))
                                        }, enabled = !state.busy) { Text((if (entry.text("type") == "dir") "▸ " else "") + entry.text("name")) }
                                    }
                                } else {
                                    TextButton(onClick = { viewModel.browse(state.directory) }, enabled = !state.busy && !editorDirty) { Text("Back to files") }
                                    val file = requireNotNull(state.file)
                                    val original = file.text("content")
                                    val path = file.text("path")
                                    var edited by remember(path, state.headSha) { mutableStateOf(state.staged.firstOrNull { it.path == path }?.content ?: original) }
                                    Text(path, style = MaterialTheme.typography.titleMedium)
                                    if (file.flag("has_more")) {
                                        Text("Preview: first 2,000 lines. Open the complete file on GitHub to edit safely.")
                                        WorkspaceCode(original)
                                    } else {
                                        OutlinedTextField(value = edited, onValueChange = {
                                            if (it.toByteArray().size <= 256_000) {
                                                edited = it
                                                editorDirty = edited != original
                                            }
                                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 480.dp), label = { Text("File content") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), readOnly = !state.canWrite || state.busy)
                                        Button(onClick = { if (viewModel.stage(path, original, edited)) editorDirty = false }, enabled = state.canWrite && !state.busy && state.selection?.ref != state.defaultBranch) { Text("Stage changes") }
                                        TextButton(onClick = {
                                            edited = original
                                            editorDirty = false
                                        }, enabled = !state.busy) { Text("Reset editor") }
                                        if (state.selection?.ref == state.defaultBranch) Text("Create a working branch in Changes before staging edits.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            1 -> {
                                Text("Review and commit", style = MaterialTheme.typography.titleLarge)
                                Text("Changes stay in this workspace until you commit. Closing discards uncommitted edits.", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(value = branchName, onValueChange = { branchName = it }, label = { Text("New working branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Button(onClick = { viewModel.createBranch(branchName) }, enabled = state.canWrite && !state.busy && branchName.isNotBlank() && state.staged.isEmpty()) { Text("Create branch") }
                                state.staged.forEach { file ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(file.path, style = MaterialTheme.typography.titleMedium)
                                            Text("Before", style = MaterialTheme.typography.labelMedium)
                                            WorkspaceCode(file.original)
                                            Text("After", style = MaterialTheme.typography.labelMedium)
                                            WorkspaceCode(file.content)
                                        }
                                    }
                                }
                                OutlinedTextField(value = message, onValueChange = { message = it.take(4000) }, label = { Text("Commit message") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = { confirmCommit = true }, enabled = state.canWrite && !state.busy && state.staged.isNotEmpty() && message.isNotBlank()) { Text("Commit ${state.staged.size} file(s)") }
                                TextButton(onClick = viewModel::discard, enabled = !state.busy && state.staged.isNotEmpty()) { Text("Discard staged changes") }
                            }
                            2 -> {
                                OutlinedTextField(value = prTitle, onValueChange = { prTitle = it }, label = { Text("Pull request title") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = prBody, onValueChange = { prBody = it }, label = { Text("Description and validation") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = { viewModel.createPullRequest(prTitle, prBody) }, enabled = state.canWrite && !state.busy && !editorDirty && state.staged.isEmpty() && state.selection?.ref != state.defaultBranch && prTitle.isNotBlank()) { Text("Create draft pull request") }
                                Text("Target: ${state.defaultBranch}", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = viewModel::loadPullRequests, enabled = !state.busy) { Text("Refresh pull requests") }
                                state.pullRequests.forEach { pr ->
                                    TextButton(onClick = { viewModel.inspectPullRequest(pr.text("number").toInt()) }, enabled = !state.busy) { Text("#${pr.text("number")} ${pr.text("title")}") }
                                }
                                if (state.morePullRequests) TextButton(onClick = viewModel::morePullRequests, enabled = !state.busy) { Text("Load more pull requests") }
                                state.pullDetail?.let { WorkspaceCode(it) }
                            }
                        }
                    }
                }
            }
        }
        if (confirmCommit) {
            AlertDialog(
                onDismissRequest = { confirmCommit = false },
                title = { Text("Commit reviewed changes?") },
                text = { Text("${state.staged.size} file(s) → ${state.selection?.fullName} @ ${state.selection?.ref}\n\n$message") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmCommit = false
                        viewModel.commit(message)
                    }) { Text("Commit") }
                },
                dismissButton = { TextButton(onClick = { confirmCommit = false }) { Text("Cancel") } }
            )
        }
        if (confirmClose) {
            AlertDialog(
                onDismissRequest = { confirmClose = false },
                title = { Text("Discard uncommitted changes?") },
                text = { Text("Uncommitted editor changes and ${state.staged.size} staged file(s) will be discarded.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.close()
                        onDismiss()
                    }) { Text("Discard and close") }
                },
                dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } }
            )
        }
    }
}

@Composable
private fun WorkspaceCode(content: String) {
    SelectionContainer {
        Text(content, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(8.dp))
    }
}
