package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider.getUriForFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = hiltViewModel(),
    onBackAction: () -> Unit
) {
    val screenWidth = LocalWindowInfo.current.containerSize.width.dp
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboard.current
    val systemChatMargin = 32.dp
    val maximumUserChatBubbleWidth = (screenWidth - systemChatMargin) * 0.8F
    val maximumOpponentChatBubbleWidth = screenWidth - systemChatMargin
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val chatRoom by chatViewModel.chatRoom.collectAsStateWithLifecycle()
    val groupedMessages by chatViewModel.groupedMessages.collectAsStateWithLifecycle()
    val indexStates by chatViewModel.indexStates.collectAsStateWithLifecycle()
    val loadingStates by chatViewModel.loadingStates.collectAsStateWithLifecycle()
    val isChatTitleDialogOpen by chatViewModel.isChatTitleDialogOpen.collectAsStateWithLifecycle()
    val isEditQuestionDialogOpen by chatViewModel.isEditQuestionDialogOpen.collectAsStateWithLifecycle()
    val isSelectTextSheetOpen by chatViewModel.isSelectTextSheetOpen.collectAsStateWithLifecycle()
    val isLoaded by chatViewModel.isLoaded.collectAsStateWithLifecycle()
    val question by chatViewModel.question.collectAsStateWithLifecycle()
    val selectedFiles by chatViewModel.selectedFiles.collectAsStateWithLifecycle()
    val appEnabledPlatforms by chatViewModel.enabledPlatformsInApp.collectAsStateWithLifecycle()
    val canUseChat = (chatViewModel.enabledPlatformsInChat.toSet() - appEnabledPlatforms.map { it.uid }.toSet()).isEmpty()
    val isIdle = loadingStates.all { it == ChatViewModel.LoadingState.Idle }
    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    LaunchedEffect(isIdle) {
        listState.animateScrollToItem(groupedMessages.userMessages.size * 2)
    }

    LaunchedEffect(isLoaded) {
        listState.animateScrollToItem(groupedMessages.userMessages.size * 2)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { focusManager.clearFocus() },
        topBar = {
            ChatTopBar(
                chatRoom.title,
                chatRoom.id > 0,
                onBackAction,
                scrollBehavior,
                chatViewModel::openChatTitleDialog,
                onExportChatItemClick = { exportChat(context, chatViewModel) }
            )
        },
        bottomBar = {
            ChatInputBox(
                value = question,
                onValueChange = { s -> chatViewModel.updateQuestion(s) },
                chatEnabled = canUseChat,
                sendButtonEnabled = question.trim().isNotBlank() && isIdle,
                selectedFiles = selectedFiles,
                onFileSelected = { filePath -> chatViewModel.addSelectedFile(filePath) },
                onFileRemoved = { filePath -> chatViewModel.removeSelectedFile(filePath) }
            ) {
                chatViewModel.askQuestion()
                focusManager.clearFocus()
            }
        },
        floatingActionButton = {
            if (listState.canScrollForward) {
                ScrollToBottomButton {
                    scope.launch {
                        listState.animateScrollToItem(groupedMessages.userMessages.size * 2)
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            state = listState
        ) {
            Log.d("ChatScreen", "GroupMessage: $groupedMessages")
            groupedMessages.userMessages.forEachIndexed { i, message ->
                // i: index of nth message
                val platformIndexState = indexStates[i]
                val assistantContent = groupedMessages.assistantMessages[i][platformIndexState].content
                val isCurrentPlatformLoading = loadingStates[platformIndexState] == ChatViewModel.LoadingState.Loading
                item {
                    var isDropDownMenuExpanded by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Box {
                            UserChatBubble(
                                modifier = Modifier.widthIn(max = maximumUserChatBubbleWidth),
                                text = message.content,
                                files = message.files,
                                onLongPress = { isDropDownMenuExpanded = true }
                            )
                            ChatBubbleDropdownMenu(
                                isChatBubbleDropdownMenuExpanded = isDropDownMenuExpanded,
                                canEdit = canUseChat && isIdle,
                                onDismissRequest = { isDropDownMenuExpanded = false },
                                onEditItemClick = { chatViewModel.openEditQuestionDialog(message) },
                                onCopyItemClick = { scope.launch { clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText(message.content, message.content))) } }
                            )
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GPTMobileIcon(if (i == groupedMessages.assistantMessages.size - 1) !isIdle else false)
                            if (chatViewModel.enabledPlatformsInChat.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    chatViewModel.enabledPlatformsInChat.forEachIndexed { j, uid ->
                                        val platform = appEnabledPlatforms.find { it.uid == uid }
                                        val isLoading = loadingStates[j] == ChatViewModel.LoadingState.Loading
                                        PlatformButton(
                                            isLoading = if (i == groupedMessages.assistantMessages.size - 1) isLoading else false,
                                            name = platform?.name ?: stringResource(R.string.unknown),
                                            selected = platformIndexState == j,
                                            onPlatformClick = { chatViewModel.updateChatPlatformIndex(i, j) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                }
                            }
                        }
                        OpponentChatBubble(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .widthIn(max = maximumOpponentChatBubbleWidth),
                            canRetry = canUseChat && i == groupedMessages.assistantMessages.size - 1 && !isCurrentPlatformLoading,
                            isLoading = if (i == groupedMessages.assistantMessages.size - 1) isCurrentPlatformLoading else false,
                            text = assistantContent,
                            onCopyClick = { scope.launch { clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText(assistantContent, assistantContent))) } },
                            onSelectClick = { chatViewModel.openSelectTextSheet(assistantContent) },
                            onRetryClick = { chatViewModel.retryChat(platformIndexState) }
                        )
                    }
                }
            }
        }

        if (isChatTitleDialogOpen) {
            ChatTitleDialog(
                initialTitle = chatRoom.title,
                onDefaultTitleMode = chatViewModel::generateDefaultChatTitle,
                onConfirmRequest = { title -> chatViewModel.updateChatTitle(title) },
                onDismissRequest = chatViewModel::closeChatTitleDialog
            )
        }

        if (isEditQuestionDialogOpen) {
            val editedQuestion by chatViewModel.editedQuestion.collectAsStateWithLifecycle()
            ChatQuestionEditDialog(
                initialQuestion = editedQuestion,
                onDismissRequest = chatViewModel::closeEditQuestionDialog,
                onConfirmRequest = { question ->
                    chatViewModel.editQuestion(question)
                    chatViewModel.closeEditQuestionDialog()
                }
            )
        }

        if (isSelectTextSheetOpen) {
            val selectedText by chatViewModel.selectedText.collectAsStateWithLifecycle()
            ModalBottomSheet(onDismissRequest = chatViewModel::closeSelectTextSheet) {
                SelectionContainer(
                    modifier = Modifier
                        .padding(24.dp)
                        .heightIn(min = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(selectedText)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatTopBar(
    title: String,
    isMenuItemEnabled: Boolean,
    onBackAction: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onChatTitleItemClick: () -> Unit,
    onExportChatItemClick: () -> Unit
) {
    var isDropDownMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(
                onClick = onBackAction
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        actions = {
            IconButton(
                onClick = { isDropDownMenuExpanded = isDropDownMenuExpanded.not() }
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.options))
            }

            ChatDropdownMenu(
                isDropdownMenuExpanded = isDropDownMenuExpanded,
                isMenuItemEnabled = isMenuItemEnabled,
                onDismissRequest = { isDropDownMenuExpanded = false },
                onChatTitleItemClick = {
                    onChatTitleItemClick.invoke()
                    isDropDownMenuExpanded = false
                },
                onExportChatItemClick = onExportChatItemClick
            )
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun ChatDropdownMenu(
    isDropdownMenuExpanded: Boolean,
    isMenuItemEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onChatTitleItemClick: () -> Unit,
    onExportChatItemClick: () -> Unit
) {
    DropdownMenu(
        modifier = Modifier.wrapContentSize(),
        expanded = isDropdownMenuExpanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            enabled = isMenuItemEnabled,
            text = { Text(text = stringResource(R.string.update_chat_title)) },
            onClick = onChatTitleItemClick
        )
        /* Export Chat */
        DropdownMenuItem(
            enabled = isMenuItemEnabled,
            text = { Text(text = stringResource(R.string.export_chat)) },
            onClick = {
                onExportChatItemClick()
                onDismissRequest()
            }
        )
    }
}

@Composable
fun ChatBubbleDropdownMenu(
    isChatBubbleDropdownMenuExpanded: Boolean,
    canEdit: Boolean,
    onDismissRequest: () -> Unit,
    onEditItemClick: () -> Unit,
    onCopyItemClick: () -> Unit
) {
    DropdownMenu(
        modifier = Modifier.wrapContentSize(),
        expanded = isChatBubbleDropdownMenuExpanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            enabled = canEdit,
            leadingIcon = {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.edit)
                )
            },
            text = { Text(text = stringResource(R.string.edit)) },
            onClick = {
                onEditItemClick.invoke()
                onDismissRequest.invoke()
            }
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_copy),
                    contentDescription = stringResource(R.string.copy_text)
                )
            },
            text = { Text(text = stringResource(R.string.copy_text)) },
            onClick = {
                onCopyItemClick.invoke()
                onDismissRequest.invoke()
            }
        )
    }
}

private fun exportChat(context: Context, chatViewModel: ChatViewModel) {
    try {
        val (fileName, fileContent) = chatViewModel.exportChat()
        val file = File(context.getExternalFilesDir(null), fileName)
        file.writeText(fileContent)
        val uri = getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Chat Export").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resInfo = context.packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
        resInfo.forEach { res ->
            context.grantUriPermission(res.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Log.e("ChatExport", "Failed to export chat", e)
        Toast.makeText(context, "Failed to export chat", Toast.LENGTH_SHORT).show()
    }
}

@Preview
@Composable
fun ChatInputBox(
    value: String = "",
    onValueChange: (String) -> Unit = {},
    chatEnabled: Boolean = true,
    sendButtonEnabled: Boolean = true,
    selectedFiles: List<String> = emptyList(),
    onFileSelected: (String) -> Unit = {},
    onFileRemoved: (String) -> Unit = {},
    onSendButtonClick: (String) -> Unit = {}
) {
    val localStyle = LocalTextStyle.current
    val mergedStyle = localStyle.merge(TextStyle(color = LocalContentColor.current))
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val filePath = copyFileToAppDirectory(context, it)
            filePath?.let { path -> onFileSelected(path) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(BottomAppBarDefaults.windowInsets)
            .padding(BottomAppBarDefaults.ContentPadding)
            .background(color = MaterialTheme.colorScheme.surface)
    ) {
        if (selectedFiles.isNotEmpty()) {
            FileThumbnailRow(
                selectedFiles = selectedFiles,
                onFileRemoved = onFileRemoved
            )
        }
        BasicTextField(
            modifier = Modifier
                .heightIn(max = 120.dp),
            value = value,
            enabled = chatEnabled,
            textStyle = mergedStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            onValueChange = { if (chatEnabled) onValueChange(it) },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(size = 24.dp))
                        .padding(all = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        enabled = chatEnabled,
                        onClick = { filePickerLauncher.launch("*/*") }
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_attach_file),
                            contentDescription = stringResource(R.string.attach_file)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                            .padding(start = 8.dp)
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                modifier = Modifier.alpha(0.38f),
                                text = if (chatEnabled) stringResource(R.string.ask_a_question) else stringResource(R.string.some_platforms_disabled)
                            )
                        }
                        innerTextField()
                    }
                    IconButton(
                        enabled = chatEnabled && sendButtonEnabled,
                        onClick = { onSendButtonClick(value) }
                    ) {
                        Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_send), contentDescription = stringResource(R.string.send))
                    }
                }
            }
        )
    }
}

@Composable
private fun FileThumbnailRow(
    selectedFiles: List<String>,
    onFileRemoved: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        selectedFiles.forEach { filePath ->
            FileThumbnail(
                filePath = filePath,
                onRemove = { onFileRemoved(filePath) }
            )
        }
    }
}

@Composable
private fun FileThumbnail(
    filePath: String,
    onRemove: () -> Unit
) {
    val file = File(filePath)
    val isImage = isImageFile(file.extension)

    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (isImage) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_image),
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_file),
                    contentDescription = file.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .background(
                        MaterialTheme.colorScheme.error,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        Text(
            text = file.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .width(72.dp)
        )
    }
}

private fun copyFileToAppDirectory(context: Context, uri: android.net.Uri): String? = try {
    val inputStream = context.contentResolver.openInputStream(uri)
    val fileName = getFileName(context, uri)
    val file = File(context.filesDir, "attachments/$fileName")
    file.parentFile?.mkdirs()

    inputStream?.use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    file.absolutePath
} catch (e: Exception) {
    null
}

private fun getFileName(context: Context, uri: android.net.Uri): String {
    var fileName = "attachment_${System.currentTimeMillis()}"

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex != -1) {
            fileName = cursor.getString(nameIndex) ?: fileName
        }
    }

    return fileName
}

private fun isImageFile(extension: String?): Boolean {
    val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    return extension?.lowercase() in imageExtensions
}

@Composable
fun ScrollToBottomButton(onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.scroll_to_bottom_icon))
    }
}
