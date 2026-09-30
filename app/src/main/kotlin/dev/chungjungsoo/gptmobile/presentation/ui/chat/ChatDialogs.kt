package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveThoughts
import dev.chungjungsoo.gptmobile.data.model.AvailableChatTool
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.ConversationDelegationSettings
import dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
import dev.chungjungsoo.gptmobile.data.repository.ProfileModelOption
import dev.chungjungsoo.gptmobile.presentation.ui.setting.ConversationDelegationCard
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LlamaModelPickerDialog
import dev.chungjungsoo.gptmobile.presentation.ui.setup.DownloadedLocalModelOption
import dev.chungjungsoo.gptmobile.presentation.ui.setup.LocalModelPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatModelDialog(
    platformOrder: List<String>,
    activePlatformUids: Set<String>,
    initialModels: Map<String, String>,
    platformNames: Map<String, String>,
    platformClientTypes: Map<String, ClientType> = emptyMap(),
    platformApiUrls: Map<String, String> = emptyMap(),
    downloadedLocalModels: List<DownloadedLocalModelOption> = emptyList(),
    ollamaModels: List<UnifiedModelOption.Ollama> = emptyList(),
    initialCreativity: Float = 0.5f,
    locationToolsEnabled: Boolean = false,
    webSearchToolsEnabled: Boolean = false,
    locationToolsAvailable: Boolean = true,
    webSearchToolsAvailable: Boolean = true,
    disabledPlatformUids: Set<String> = emptySet(),
    mcpTools: List<AvailableChatTool> = emptyList(),
    isToolEnabled: (String) -> Boolean = { false },
    onToolChanged: (String, Boolean) -> Unit = { _, _ -> },
    loadModels: suspend (String) -> List<ProfileModelOption> = { emptyList() },
    onPlatformActiveChanged: (String, Boolean) -> Unit = { _, _ -> },
    onLocationToolsChanged: (Boolean) -> Unit = {},
    onWebSearchToolsChanged: (Boolean) -> Unit = {},
    onNavigateToLocalModels: () -> Unit = {},
    delegationSettings: ModelDelegationSettings = ModelDelegationSettings(),
    delegationProfiles: List<PlatformV2> = emptyList(),
    usesDefaultDelegation: Boolean = true,
    onDelegationChanged: (ConversationDelegationSettings?) -> Unit = {},
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Map<String, String>, Float) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var models by rememberSaveable(platformOrder) {
        mutableStateOf(platformOrder.associateWith { uid -> initialModels[uid].orEmpty() })
    }

    var activeUnifiedPickerPlatformUid by remember { mutableStateOf<String?>(null) }
    var activeCloudPickerUid by remember { mutableStateOf<String?>(null) }
    var activeLlamaPickerPlatformUid by remember { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableStateOf("Options") }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    var selectedProfile by rememberSaveable(platformOrder) { mutableStateOf(platformOrder.firstOrNull().orEmpty()) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var creativity by rememberSaveable(initialCreativity) { mutableStateOf(initialCreativity.coerceIn(0f, 2f)) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text("Conversation settings") },
        text = {
            Column {
                val sections = listOf("Options", "Models")
                TabRow(selectedTabIndex = sections.indexOf(section).coerceAtLeast(0), containerColor = MaterialTheme.colorScheme.surface) {
                    sections.forEach { label ->
                        Tab(selected = section == label, onClick = { section = label }, text = { Text(label) })
                    }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (section == "Models") {
                        ConversationDelegationCard(
                            settings = delegationSettings,
                            profiles = delegationProfiles,
                            usesDefaults = usesDefaultDelegation,
                            onChange = onDelegationChanged
                        )
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        OutlinedTextField(
                            value = modelSearch,
                            onValueChange = { modelSearch = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            label = { Text("Filter profiles") },
                            singleLine = true
                        )
                        val filteredProfiles = platformOrder.filter { uid ->
                            modelSearch.isBlank() || platformNames[uid].orEmpty().contains(modelSearch, true) || models[uid].orEmpty().contains(modelSearch, true)
                        }
                        val displayedProfile = selectedProfile.takeIf { it in filteredProfiles } ?: filteredProfiles.firstOrNull()
                        Box(Modifier.fillMaxWidth()) {
                            TextButton(onClick = { profileMenuOpen = true }, enabled = filteredProfiles.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                Text(displayedProfile?.let { platformNames[it] }.orEmpty().ifBlank { "No matching profiles" }, Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, "Select AI profile")
                            }
                            DropdownMenu(expanded = profileMenuOpen, onDismissRequest = { profileMenuOpen = false }) {
                                filteredProfiles.forEach { uid ->
                                    DropdownMenuItem(
                                        text = { Text("${platformNames[uid].orEmpty()} · ${if (uid in activePlatformUids) "Added" else "Available"}") },
                                        onClick = {
                                            selectedProfile = uid
                                            profileMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        listOfNotNull(displayedProfile).forEach { platformUid ->
                            val platformName = platformNames[platformUid] ?: stringResource(R.string.unknown)
                            val clientType = platformClientTypes[platformUid]
                            val isMember = platformUid in activePlatformUids
                            val isTemporarilyEnabled = platformUid !in disabledPlatformUids

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(platformName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    if (!isTemporarilyEnabled) {
                                        "Paused"
                                    } else if (isMember) {
                                        "Added"
                                    } else {
                                        "Available"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(Modifier.width(8.dp))
                                Switch(
                                    checked = isMember,
                                    onCheckedChange = { active -> onPlatformActiveChanged(platformUid, active) }
                                )
                            }

                            if (clientType == ClientType.LITERT_LM) {
                                Text(
                                    text = stringResource(R.string.chat_model_for_platform, platformName),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                LocalModelPicker(
                                    models = downloadedLocalModels,
                                    selectedCatalogEntryId = models[platformUid].orEmpty(),
                                    onModelSelected = { value ->
                                        models = models.toMutableMap().apply { put(platformUid, value) }
                                    },
                                    onNavigateToLocalModels = onNavigateToLocalModels,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            } else if (clientType == ClientType.OLLAMA) {
                                // Ollama platform: support both direct text entry and unified picker selection
                                OutlinedTextField(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    value = models[platformUid].orEmpty(),
                                    onValueChange = { value ->
                                        models = models.toMutableMap().apply { put(platformUid, value) }
                                    },
                                    singleLine = true,
                                    label = { Text(text = stringResource(R.string.chat_model_for_platform, platformName)) },
                                    trailingIcon = {
                                        IconButton(onClick = { activeCloudPickerUid = platformUid }) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = stringResource(R.string.unified_model_picker)
                                            )
                                        }
                                    },
                                    supportingText = {
                                        Text(stringResource(R.string.model_supporting))
                                    }
                                )
                            } else if (clientType == ClientType.LLAMA) {
                                // Llama platform: support both direct text entry and Llama router model picker selection
                                OutlinedTextField(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    value = models[platformUid].orEmpty(),
                                    onValueChange = { value ->
                                        models = models.toMutableMap().apply { put(platformUid, value) }
                                    },
                                    singleLine = true,
                                    label = { Text(text = stringResource(R.string.chat_model_for_platform, platformName)) },
                                    trailingIcon = {
                                        IconButton(onClick = { activeLlamaPickerPlatformUid = platformUid }) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = stringResource(R.string.llama_select_router_model)
                                            )
                                        }
                                    },
                                    supportingText = {
                                        Text(stringResource(R.string.model_supporting))
                                    }
                                )
                            } else {
                                OutlinedTextField(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    value = models[platformUid].orEmpty(),
                                    onValueChange = { value ->
                                        models = models.toMutableMap().apply { put(platformUid, value) }
                                    },
                                    singleLine = true,
                                    label = { Text(text = stringResource(R.string.chat_model_for_platform, platformName)) },
                                    trailingIcon = {
                                        IconButton(onClick = { activeCloudPickerUid = platformUid }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Browse provider models")
                                        }
                                    },
                                    supportingText = {
                                        Text(stringResource(R.string.model_supporting))
                                    }
                                )
                            }
                        }
                    }
                    if (section == "Options") {
                        Text("Applies immediately to this conversation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Location tools", modifier = Modifier.weight(1f))
                            Switch(
                                checked = locationToolsEnabled,
                                enabled = locationToolsAvailable,
                                onCheckedChange = onLocationToolsChanged
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Web search tools", modifier = Modifier.weight(1f))
                            Switch(
                                checked = webSearchToolsEnabled,
                                enabled = webSearchToolsAvailable,
                                onCheckedChange = onWebSearchToolsChanged
                            )
                        }
                        var showConnectedTools by rememberSaveable { mutableStateOf(false) }
                        TextButton(onClick = { showConnectedTools = !showConnectedTools }) {
                            Text("Connected tools · ${mcpTools.size} ${if (showConnectedTools) "▴" else "▾"}")
                        }
                        if (showConnectedTools) {
                            mcpTools.forEach { tool ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(toolActivityIcon(tool.name), null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(tool.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = isToolEnabled(tool.id), enabled = tool.isEnabled, onCheckedChange = { onToolChanged(tool.id, it) })
                                }
                            }
                        }
                        Text("Web search queries all enabled search connections and combines their results. Individual switches control which engines participate.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (section == "Options") {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Creativity · %.2f".format(creativity), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        Slider(
                            value = creativity,
                            onValueChange = { creativity = it },
                            valueRange = 0f..2f,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                        Text("Focused answers at the left; more varied ideas at the right. Model and creativity changes apply when saved.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            val hasBlank = activePlatformUids.any { models[it].orEmpty().trim().isBlank() }
            TextButton(
                enabled = !hasBlank,
                onClick = {
                    onConfirmRequest(
                        models.mapValues { (_, model) -> model.trim() },
                        creativity
                    )
                }
            ) {
                Text("Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    activeCloudPickerUid?.let { uid ->
        CloudModelPickerDialog(
            profileUid = uid,
            loadModels = loadModels,
            onSelect = { model -> models = models + (uid to model) },
            onDismiss = { activeCloudPickerUid = null }
        )
    }

    // Show Unified Model Picker Dialog if active
    activeUnifiedPickerPlatformUid?.let { platformUid ->
        val localOptions = downloadedLocalModels.map {
            UnifiedModelOption.Local(
                id = it.catalogEntryId,
                displayName = it.displayName
            )
        }
        val currentModel = models[platformUid].orEmpty()
        val platformName = platformNames[platformUid] ?: ""

        UnifiedModelPickerDialog(
            title = stringResource(R.string.chat_model_for_platform, platformName),
            localModels = localOptions,
            ollamaModels = ollamaModels,
            selectedModelId = currentModel,
            onModelSelected = { selected ->
                models = models.toMutableMap().apply { put(platformUid, selected) }
            },
            onNavigateToLocalModels = onNavigateToLocalModels,
            onDismissRequest = { activeUnifiedPickerPlatformUid = null }
        )
    }

    // Show Llama Model Picker Dialog if active
    activeLlamaPickerPlatformUid?.let { platformUid ->
        val currentModel = models[platformUid].orEmpty()
        val baseUrl = platformApiUrls[platformUid].orEmpty()

        LlamaModelPickerDialog(
            baseUrl = baseUrl,
            currentModel = currentModel,
            onDismiss = { activeLlamaPickerPlatformUid = null },
            onModelSelected = { selected ->
                models = models.toMutableMap().apply { put(platformUid, selected) }
            }
        )
    }
}

@Composable
fun ChatTitleDialog(
    initialTitle: String,
    onDefaultTitleMode: () -> String?,
    onConfirmRequest: (title: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    val untitledChat = stringResource(R.string.untitled_chat)

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.chat_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    value = title,
                    singleLine = true,
                    isError = title.length > 50,
                    supportingText = {
                        if (title.length > 50) {
                            Text(stringResource(R.string.title_length_limit, title.length))
                        }
                    },
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.chat_title)) }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && title != initialTitle,
                onClick = {
                    onConfirmRequest(title)
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.update))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { title = onDefaultTitleMode.invoke() ?: untitledChat }
            ) {
                Text(text = stringResource(R.string.default_mode))
            }
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun UserMessageEditDialog(
    initialQuestion: MessageV2,
    attachments: List<ChatAttachmentDraft>,
    onFileSelected: (String) -> Unit,
    onCopyFailed: () -> Unit,
    onFileRemoved: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (MessageV2) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf(initialQuestion.content) }
    val questionFieldMaxLines = 8
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val filePath = withContext(Dispatchers.IO) {
                    copyFileToAppDirectory(context, it)
                }
                if (filePath != null) {
                    onFileSelected(filePath)
                } else {
                    onCopyFailed()
                }
            }
        }
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.edit_question)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = question,
                    onValueChange = { question = it },
                    minLines = 3,
                    maxLines = questionFieldMaxLines,
                    label = { Text(stringResource(R.string.user_message)) }
                )
                AttachmentEditorSection(
                    attachments = attachments,
                    onAttachFileClick = { filePickerLauncher.launch("*/*") },
                    onFileRemoved = onFileRemoved
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            val hasPendingOrFailedAttachments = attachments.any { it.status != ChatAttachmentDraft.Status.Ready }
            TextButton(
                enabled = !hasPendingOrFailedAttachments &&
                    (question.isNotBlank() || attachments.isNotEmpty()) &&
                    (question != initialQuestion.content || attachments.mapNotNull { it.attachment } != initialQuestion.attachments),
                onClick = { onConfirmRequest(initialQuestion.copy(content = question)) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun AssistantMessageEditDialog(
    initialMessage: MessageV2,
    attachments: List<ChatAttachmentDraft>,
    onFileSelected: (String) -> Unit,
    onCopyFailed: () -> Unit,
    onFileRemoved: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (MessageV2, String) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var responseText by remember { mutableStateOf(initialMessage.effectiveContent()) }
    var thoughtsText by remember { mutableStateOf(initialMessage.effectiveThoughts()) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val filePath = withContext(Dispatchers.IO) {
                    copyFileToAppDirectory(context, it)
                }
                if (filePath != null) {
                    onFileSelected(filePath)
                } else {
                    onCopyFailed()
                }
            }
        }
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.edit_assistant_message)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = responseText,
                    onValueChange = { responseText = it },
                    minLines = 3,
                    maxLines = 8,
                    label = { Text(stringResource(R.string.assistant_message)) }
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    value = thoughtsText,
                    onValueChange = { thoughtsText = it },
                    minLines = 2,
                    maxLines = 8,
                    label = { Text(stringResource(R.string.assistant_thoughts)) }
                )
                AttachmentEditorSection(
                    attachments = attachments,
                    onAttachFileClick = { filePickerLauncher.launch("image/*") },
                    onFileRemoved = onFileRemoved
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            val hasPendingOrFailedAttachments = attachments.any { it.status != ChatAttachmentDraft.Status.Ready }
            TextButton(
                enabled = !hasPendingOrFailedAttachments &&
                    (responseText.isNotBlank() || thoughtsText.isNotBlank() || attachments.isNotEmpty()) &&
                    (
                        responseText != initialMessage.effectiveContent() ||
                            thoughtsText != initialMessage.effectiveThoughts() ||
                            attachments.mapNotNull { it.attachment } != initialMessage.attachments
                        ),
                onClick = {
                    onConfirmRequest(
                        initialMessage.copy(content = responseText),
                        thoughtsText
                    )
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun AttachmentEditorSection(
    attachments: List<ChatAttachmentDraft>,
    onAttachFileClick: () -> Unit,
    onFileRemoved: (String) -> Unit
) {
    if (attachments.isNotEmpty()) {
        FileThumbnailRow(
            selectedAttachments = attachments,
            onFileRemoved = onFileRemoved
        )
    }
    TextButton(
        modifier = Modifier.padding(horizontal = 12.dp),
        onClick = onAttachFileClick
    ) {
        Text(text = stringResource(R.string.attach_file))
    }
}
