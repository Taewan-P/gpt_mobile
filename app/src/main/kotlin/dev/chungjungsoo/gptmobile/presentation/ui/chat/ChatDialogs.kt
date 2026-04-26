package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveThoughts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatModelDialog(
    platformOrder: List<String>,
    initialModels: Map<String, String>,
    platformNames: Map<String, String>,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Map<String, String>) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var models by rememberSaveable(platformOrder, initialModels) {
        mutableStateOf(platformOrder.associateWith { uid -> initialModels[uid].orEmpty() })
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.chat_models)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.chat_models_description),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                platformOrder.forEach { platformUid ->
                    val platformName = platformNames[platformUid] ?: stringResource(R.string.unknown)
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
                        supportingText = {
                            Text(stringResource(R.string.model_supporting))
                        }
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            val hasBlank = platformOrder.any { models[it].orEmpty().trim().isBlank() }
            TextButton(
                enabled = !hasBlank,
                onClick = {
                    onConfirmRequest(
                        models.mapValues { (_, model) -> model.trim() }
                    )
                }
            ) {
                Text(stringResource(R.string.update_chat_models))
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
