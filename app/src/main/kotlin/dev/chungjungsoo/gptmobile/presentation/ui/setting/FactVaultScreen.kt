package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.rag.VaultFact
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactVaultScreen(viewModel: FactVaultViewModel, onBack: () -> Unit) {
    val vault by viewModel.vault.collectAsStateWithLifecycle()
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle(emptyList())
    val attachments by viewModel.attachments.collectAsStateWithLifecycle(emptyList())
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Memories") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<VaultFact?>(null) }
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = vault.settings
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Memory") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = {
                    adding = true
                    draft = ""
                }, enabled = !busy) { Icon(Icons.Default.Add, "Add a memory", tint = MaterialTheme.colorScheme.primary) }
            }
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("A memory you control", style = MaterialTheme.typography.titleLarge)
                                Text("Encrypted facts · local graph · document recall", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(vault.enabled, viewModel::setEnabled, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Enable local memory" })
                        }
                        Text("${vault.facts.size} memories · ${documents.size} indexed documents", style = MaterialTheme.typography.labelLarge)
                        Text("${vault.facts.count { !it.enabled }} awaiting review or disabled · Free profiles never receive memory", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Memories", "Documents", "Controls").forEach { label ->
                        FilterChip(tab == label, {
                            tab = label
                            query = ""
                        }, label = { Text(label) })
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let {
                item {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::refresh) { Text("Retry") }
                }
            }
            status?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) } }
            when (tab) {
                "Memories" -> {
                    item {
                        OutlinedTextField(query, { query = it }, label = { Text("Find a memory") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "Pinned", "Review").forEach { label -> FilterChip(filter == label, { filter = label }, label = { Text(label) }) }
                        }
                    }
                    val shown = vault.facts.filter {
                        "${it.fact.entity.name} ${it.fact.target.name}".contains(query, true) &&
                            (filter != "Pinned" || it.pinned) &&
                            (filter != "Review" || !it.enabled)
                    }.sortedWith(compareByDescending<VaultFact> { it.pinned }.thenByDescending { it.savedAtMillis })
                    if (shown.isEmpty()) item { Text("No memories here yet. Add one, or tell your AI what to remember.", style = MaterialTheme.typography.bodyMedium) }
                    items(shown, key = { it.id }) { entry ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (entry.fact.relation.relationType == "REMEMBERS" || entry.source == "local_model_observation") entry.fact.target.name else "${entry.fact.entity.name} ${entry.fact.relation.relationType.lowercase().replace('_', ' ')} ${entry.fact.target.name}", style = MaterialTheme.typography.titleMedium)
                                Text("${entry.source.replace('_', ' ')} · ${if (entry.sourceChatId > 0) "chat ${entry.sourceChatId}, message ${entry.sourceMessageId}" else "Added by you"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(entry.enabled, { viewModel.setFactEnabled(entry.id, it) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Recall this memory" })
                                    TextButton(onClick = {
                                        editing = entry
                                        draft = entry.fact.target.name
                                    }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Edit") }
                                    IconButton(onClick = { viewModel.pin(entry.id, !entry.pinned) }, enabled = !busy) { Icon(Icons.Default.PushPin, if (entry.pinned) "Unpin" else "Pin", tint = if (entry.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
                                    IconButton(onClick = { deleting = entry.id }, enabled = !busy) { Icon(Icons.Default.Delete, "Forget memory") }
                                }
                            }
                        }
                    }
                }
                "Documents" -> {
                    item {
                        Text("Conversation library", style = MaterialTheme.typography.titleLarge)
                        Text("All attachments stay linked to their original conversations. Index text documents to make them searchable by local memory tools.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = viewModel::indexDocuments, enabled = !busy && vault.enabled) { Text("Index document text locally") }
                        OutlinedTextField(query, { query = it }, label = { Text("Find an attachment") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (attachments.isEmpty()) item { Text("Attach a file in any conversation to see it here.") }
                    items(attachments.filter { it.attachment.resolvedDisplayName.contains(query, true) }, key = { "${it.chatId}:${it.attachment.filePathForDisplay}" }) { entry ->
                        val attachment = entry.attachment
                        val indexed = documents.firstOrNull { it.id == entry.documentId }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(attachment.resolvedDisplayName, style = MaterialTheme.typography.titleMedium)
                                }
                                Text("Chat ${entry.chatId} · ${attachment.sizeBytes / 1024} KB · ${if (indexed != null) "Indexed" else "Original attachment"}", style = MaterialTheme.typography.labelSmall)
                                Row {
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                val file = File(attachment.filePathForDisplay)
                                                require(file.isFile)
                                                val uri = try {
                                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                } catch (_: IllegalArgumentException) {
                                                    val preview = withContext(Dispatchers.IO) {
                                                        val folder = File(context.cacheDir, "attachment-previews").also { it.mkdirs() }
                                                        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
                                                        file.copyTo(File(folder, "${java.util.UUID.randomUUID()}-${file.name}"))
                                                    }
                                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", preview)
                                                }
                                                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "The original file or a compatible viewer is unavailable.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }) { Text("Open") }
                                    if (indexed != null) {
                                        TextButton(onClick = { context.startActivity(Intent(context, KnowledgeSourceActivity::class.java).setData(Uri.parse("gptmobile://knowledge/${indexed.id}"))) }) { Text("Read text") }
                                        TextButton(onClick = { viewModel.removeDocument(indexed.id) }, enabled = !busy) { Text("Remove index") }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Learning & privacy", style = MaterialTheme.typography.titleMedium)
                                var sensitivity by remember(settings.captureSensitivity) { mutableStateOf(settings.captureSensitivity.toFloat()) }
                                Text("Memory sensitivity · ${sensitivity.toInt()}%", style = MaterialTheme.typography.titleSmall)
                                Slider(
                                    value = sensitivity,
                                    onValueChange = { sensitivity = it },
                                    onValueChangeFinished = { viewModel.updateSettings(settings.copy(captureSensitivity = sensitivity.toInt())) },
                                    valueRange = 0f..100f,
                                    enabled = !busy && vault.enabled && settings.learningEnabled,
                                    modifier = Modifier.semantics { contentDescription = "Memory sensitivity" }
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Only key facts", style = MaterialTheme.typography.labelSmall)
                                    Text("More details", style = MaterialTheme.typography.labelSmall)
                                }
                                Text("Lower values keep stronger preferences and lasting facts. Higher values also capture stated projects, learning goals and needs. Existing memories and explicit save requests are unchanged.", style = MaterialTheme.typography.bodySmall)

                                VaultToggle("Learn from new messages", settings.learningEnabled, !busy) { viewModel.updateSettings(settings.copy(learningEnabled = it)) }
                                VaultToggle("Recall saved memories", settings.recallEnabled, !busy) { viewModel.updateSettings(settings.copy(recallEnabled = it)) }
                                VaultToggle("Allow recall in cloud requests", settings.allowCloudRecall, !busy) { viewModel.updateSettings(settings.copy(allowCloudRecall = it)) }
                                VaultToggle("Recall only within the original chat", settings.sameChatOnly, !busy) { viewModel.updateSettings(settings.copy(sameChatOnly = it)) }
                                VaultToggle("Review new memories before use", settings.reviewBeforeRecall, !busy) { viewModel.updateSettings(settings.copy(reviewBeforeRecall = it)) }
                                VaultToggle("Learn preferences", settings.learnPreferences, !busy) { viewModel.updateSettings(settings.copy(learnPreferences = it)) }
                                VaultToggle("Learn relationships", settings.learnRelationships, !busy) { viewModel.updateSettings(settings.copy(learnRelationships = it)) }
                                VaultToggle("Use local model for richer learning", settings.localModelLearning, !busy) { viewModel.updateSettings(settings.copy(localModelLearning = it)) }
                                Text("Uses the private helper selected in Local models → Delegation when delegation is enabled. It selects exact user statements, with a text-only fallback when no helper is available.", style = MaterialTheme.typography.bodySmall)
                                VaultToggle("Make room for new automatic memories", settings.rotateAutomaticFacts, !busy) { viewModel.updateSettings(settings.copy(rotateAutomaticFacts = it)) }
                                Text("Replaces the oldest automatic memories at capacity. Pinned and manually saved memories are kept.", style = MaterialTheme.typography.bodySmall)
                                VaultLimit("New facts per message", settings.maxCapturePerMessage, 1..16, !busy) { viewModel.updateSettings(settings.copy(maxCapturePerMessage = it)) }
                                VaultLimit("Recall token budget · estimate", settings.recallTokens, 128..4096, !busy) { viewModel.updateSettings(settings.copy(recallTokens = it)) }
                                VaultLimit("Memories per response", settings.maxRecall, 1..20, !busy) { viewModel.updateSettings(settings.copy(maxRecall = it)) }
                                VaultLimit("Memory capacity", settings.maxFacts, 16..2048, !busy) { viewModel.updateSettings(settings.copy(maxFacts = it)) }
                                VaultLimit("Retention days · 0 keeps memories", settings.retentionDays, 0..365, !busy) { viewModel.updateSettings(settings.copy(retentionDays = it)) }
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Connected memory", style = MaterialTheme.typography.titleMedium)
                                VaultToggle("Automatically recall from selected MCPs", settings.externalRecallEnabled, !busy) { viewModel.updateSettings(settings.copy(externalRecallEnabled = it)) }
                                Text("Add Mem0, Supermemory or your Graphiti server from the marketplace and enable its memory search tool in the AI profile. Selected servers receive up to 500 characters from the current question. Local saved memories and chat history are not uploaded. Remote saving uses the provider's ordinary tools and permissions.", style = MaterialTheme.typography.bodySmall)
                                if (!settings.allowCloudRecall || settings.sameChatOnly || settings.reviewBeforeRecall) Text("Automatic connected recall is paused by cloud recall, original-chat-only or review-before-use controls.", style = MaterialTheme.typography.bodySmall)
                                if (connections.isEmpty()) Text("No MCP connections configured yet.")
                                connections.forEach { connection ->
                                    val selected = connection.connectionUid in settings.externalMemoryConnections
                                    VaultToggle(connection.name, selected, !busy) { enabled ->
                                        val ids = if (enabled) settings.externalMemoryConnections + connection.connectionUid else settings.externalMemoryConnections - connection.connectionUid
                                        viewModel.updateSettings(settings.copy(externalMemoryConnections = ids))
                                    }
                                    if (selected) {
                                        var memoryScope by remember(connection.connectionUid, settings.externalMemoryScopes) { mutableStateOf(settings.externalMemoryScopes[connection.connectionUid].orEmpty()) }
                                        OutlinedTextField(memoryScope, { memoryScope = it.take(200) }, label = { Text("User ID / group ID / space key") }, supportingText = { Text("Blank uses the server's authorized default scope.") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                                        TextButton(onClick = { viewModel.updateSettings(settings.copy(externalMemoryScopes = settings.externalMemoryScopes + (connection.connectionUid to memoryScope.trim()))) }, enabled = !busy) { Text("Save scope") }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("Local memory tools", style = MaterialTheme.typography.titleMedium)
                        Text("Capture & recall · Search & open nodes · Read graph · Add observations · Forget · Search & read documents", style = MaterialTheme.typography.bodyMedium)
                        Text("Runs inside the app. No account or remote memory server. Cloud recall sends selected reference text only when enabled. Document indexes use the app's private storage.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { clearing = true }, enabled = !busy) { Text("Clear saved memories") }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        AlertDialog(
            onDismissRequest = {
                adding = false
                editing = null
            },
            title = { Text("Remember something") },
            text = { OutlinedTextField(draft, { draft = it.take(1000) }, label = { Text("Fact, preference or note") }, supportingText = { Text("${draft.length}/1000") }) },
            confirmButton = {
                TextButton(enabled = draft.isNotBlank(), onClick = {
                    viewModel.saveFact(draft, editing?.id)
                    adding = false
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    adding = false
                    editing = null
                }) { Text("Cancel") }
            }
        )
    }
    if (deleting != null || clearing) {
        AlertDialog(
            onDismissRequest = {
                deleting = null
                clearing = false
            },
            title = { Text(if (clearing) "Clear saved memories?" else "Forget this memory?") },
            text = { Text(if (clearing) "Removes saved facts and turns memory off. Conversation attachments stay in their chats." else "Stops future recall. Retrying the source message will not restore it.") },
            confirmButton = {
                TextButton(onClick = {
                    if (clearing) viewModel.clear() else deleting?.let(viewModel::delete)
                    deleting = null
                    clearing = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleting = null
                    clearing = false
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun VaultToggle(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun VaultLimit(title: String, value: Int, range: IntRange, enabled: Boolean, onChange: (Int) -> Unit) {
    var draft by remember(value) { mutableStateOf(value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat())) }
    Text("$title: ${draft.toInt()}", style = MaterialTheme.typography.labelLarge)
    Slider(value = draft, onValueChange = { draft = it }, onValueChangeFinished = { onChange(draft.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(), enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
}
