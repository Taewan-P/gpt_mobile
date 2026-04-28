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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider.getUriForFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.ACTIVE_REVISION_LATEST
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveThoughts
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = hiltViewModel(),
    onBackAction: () -> Unit
) {
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidthDp = with(LocalDensity.current) { containerSize.width.toDp() }
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboard.current
    val systemChatMargin = 32.dp
    val maximumUserChatBubbleWidth = (screenWidthDp - systemChatMargin) * 0.8F
    val maximumOpponentChatBubbleWidth = screenWidthDp - systemChatMargin
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val chatRoom by chatViewModel.chatRoom.collectAsStateWithLifecycle()
    val groupedMessages by chatViewModel.groupedMessages.collectAsStateWithLifecycle()
    val indexStates by chatViewModel.indexStates.collectAsStateWithLifecycle()
    val loadingStates by chatViewModel.loadingStates.collectAsStateWithLifecycle()
    val isChatTitleDialogOpen by chatViewModel.isChatTitleDialogOpen.collectAsStateWithLifecycle()
    val isChatModelDialogOpen by chatViewModel.isChatModelDialogOpen.collectAsStateWithLifecycle()
    val messageEditSession by chatViewModel.messageEditSession.collectAsStateWithLifecycle()
    val isSelectTextSheetOpen by chatViewModel.isSelectTextSheetOpen.collectAsStateWithLifecycle()
    val isLoaded by chatViewModel.isLoaded.collectAsStateWithLifecycle()
    val selectedAttachments by chatViewModel.selectedAttachments.collectAsStateWithLifecycle()
    val attachmentNotice by chatViewModel.attachmentNotice.collectAsStateWithLifecycle()
    val appEnabledPlatforms by chatViewModel.enabledPlatformsInApp.collectAsStateWithLifecycle()
    val appAllPlatforms by chatViewModel.platformsInApp.collectAsStateWithLifecycle()
    val chatPlatformModels by chatViewModel.chatPlatformModels.collectAsStateWithLifecycle()
    val enabledPlatformLookup = remember(appEnabledPlatforms) { appEnabledPlatforms.associateBy { it.uid } }
    val canUseChat = (chatViewModel.enabledPlatformsInChat.toSet() - appEnabledPlatforms.map { it.uid }.toSet()).isEmpty()
    val isIdle = loadingStates.all { it == ChatViewModel.LoadingState.Idle }
    val context = LocalContext.current
    val lastMessageIndex = groupedMessages.userMessages.lastIndex

    val scope = rememberCoroutineScope()

    suspend fun animateScrollToLatestMessage() {
        if (lastMessageIndex >= 0) {
            listState.animateScrollToItem(lastMessageIndex)
        }
    }

    LaunchedEffect(isIdle) {
        animateScrollToLatestMessage()
    }

    LaunchedEffect(isLoaded) {
        animateScrollToLatestMessage()
    }

    LaunchedEffect(attachmentNotice) {
        attachmentNotice?.let { notice ->
            Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
            chatViewModel.consumeAttachmentNotice()
        }
    }

    // Auto-scroll to bottom when keyboard opens
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            delay(100) // Small delay to let keyboard animation start
            animateScrollToLatestMessage()
        }
    }

    Scaffold(
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { focusManager.clearFocus() },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatTopBar(
                chatRoom.title,
                chatRoom.id > 0,
                chatViewModel.enabledPlatformsInChat.isNotEmpty(),
                onBackAction,
                scrollBehavior,
                chatViewModel::openChatTitleDialog,
                chatViewModel::openChatModelDialog,
                onExportChatItemClick = { exportChat(context, chatViewModel) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState
                ) {
                    val historicalMessageCount = lastMessageIndex.coerceAtLeast(0)

                    items(
                        count = historicalMessageCount,
                        key = { index -> chatMessagePairKey(groupedMessages.userMessages[index], index) }
                    ) { index ->
                        ChatMessagePair(
                            messageIndex = index,
                            message = groupedMessages.userMessages[index],
                            assistantMessages = groupedMessages.assistantMessages.getOrNull(index) ?: emptyList(),
                            platformIndexState = indexStates.getOrElse(index) { 0 },
                            loadingStates = loadingStates,
                            enabledPlatformsInChat = chatViewModel.enabledPlatformsInChat,
                            enabledPlatformLookup = enabledPlatformLookup,
                            canUseChat = canUseChat,
                            isIdle = isIdle,
                            isActiveMessage = false,
                            maximumUserChatBubbleWidth = maximumUserChatBubbleWidth,
                            maximumOpponentChatBubbleWidth = maximumOpponentChatBubbleWidth,
                            onEditQuestion = chatViewModel::openUserMessageEditDialog,
                            onEditAssistant = chatViewModel::openAssistantMessageEditDialog,
                            onCopyText = { copiedText ->
                                scope.launch {
                                    clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText(copiedText, copiedText)))
                                }
                            },
                            onPlatformClick = chatViewModel::updateChatPlatformIndex,
                            onSelectText = chatViewModel::openSelectTextSheet,
                            onRetry = chatViewModel::retryChat,
                            onShowPreviousRevision = chatViewModel::showPreviousAssistantRevision,
                            onShowNextRevision = chatViewModel::showNextAssistantRevision
                        )
                    }

                    if (lastMessageIndex >= 0) {
                        item(key = chatMessagePairKey(groupedMessages.userMessages[lastMessageIndex], lastMessageIndex)) {
                            ChatMessagePair(
                                messageIndex = lastMessageIndex,
                                message = groupedMessages.userMessages[lastMessageIndex],
                                assistantMessages = groupedMessages.assistantMessages.getOrNull(lastMessageIndex) ?: emptyList(),
                                platformIndexState = indexStates.getOrElse(lastMessageIndex) { 0 },
                                loadingStates = loadingStates,
                                enabledPlatformsInChat = chatViewModel.enabledPlatformsInChat,
                                enabledPlatformLookup = enabledPlatformLookup,
                                canUseChat = canUseChat,
                                isIdle = isIdle,
                                isActiveMessage = true,
                                maximumUserChatBubbleWidth = maximumUserChatBubbleWidth,
                                maximumOpponentChatBubbleWidth = maximumOpponentChatBubbleWidth,
                                onEditQuestion = chatViewModel::openUserMessageEditDialog,
                                onEditAssistant = chatViewModel::openAssistantMessageEditDialog,
                                onCopyText = { copiedText ->
                                    scope.launch {
                                        clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText(copiedText, copiedText)))
                                    }
                                },
                                onPlatformClick = chatViewModel::updateChatPlatformIndex,
                                onSelectText = chatViewModel::openSelectTextSheet,
                                onRetry = chatViewModel::retryChat,
                                onShowPreviousRevision = chatViewModel::showPreviousAssistantRevision,
                                onShowNextRevision = chatViewModel::showNextAssistantRevision
                            )
                        }
                    }
                }

                if (listState.canScrollForward) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        ScrollToBottomButton {
                            scope.launch {
                                animateScrollToLatestMessage()
                            }
                        }
                    }
                }
            }

            ChatInputBox(
                inputState = chatViewModel.question,
                chatEnabled = canUseChat,
                sendButtonEnabled = isIdle,
                selectedAttachments = selectedAttachments,
                onFileSelected = { filePath -> chatViewModel.addSelectedFile(filePath) },
                onFileRemoved = { filePath -> chatViewModel.removeSelectedFile(filePath) }
            ) {
                chatViewModel.askQuestion()
                focusManager.clearFocus()
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

        if (isChatModelDialogOpen) {
            val platformNames = chatViewModel.enabledPlatformsInChat.associateWith { uid ->
                appAllPlatforms.find { it.uid == uid }?.name ?: stringResource(R.string.unknown)
            }
            ChatModelDialog(
                platformOrder = chatViewModel.enabledPlatformsInChat,
                initialModels = chatPlatformModels,
                platformNames = platformNames,
                onDismissRequest = chatViewModel::closeChatModelDialog,
                onConfirmRequest = { models ->
                    chatViewModel.updateChatPlatformModels(models)
                    chatViewModel.closeChatModelDialog()
                }
            )
        }

        messageEditSession?.let { session ->
            when (session.role) {
                ChatViewModel.MessageEditRole.USER -> {
                    UserMessageEditDialog(
                        initialQuestion = session.message,
                        attachments = session.attachments,
                        onFileSelected = chatViewModel::addMessageEditFile,
                        onCopyFailed = chatViewModel::notifyAttachmentCopyFailed,
                        onFileRemoved = chatViewModel::removeMessageEditFile,
                        onDismissRequest = chatViewModel::discardMessageEditDialog,
                        onConfirmRequest = { question ->
                            if (chatViewModel.saveUserMessageEdit(question, session.attachments)) {
                                chatViewModel.finishMessageEditDialog()
                            }
                        }
                    )
                }

                ChatViewModel.MessageEditRole.ASSISTANT -> {
                    AssistantMessageEditDialog(
                        initialMessage = session.message,
                        attachments = session.attachments,
                        onFileSelected = chatViewModel::addMessageEditFile,
                        onCopyFailed = chatViewModel::notifyAttachmentCopyFailed,
                        onFileRemoved = chatViewModel::removeMessageEditFile,
                        onDismissRequest = chatViewModel::discardMessageEditDialog,
                        onConfirmRequest = { message, thoughts ->
                            if (chatViewModel.saveAssistantMessageEdit(message, thoughts, session.attachments)) {
                                chatViewModel.finishMessageEditDialog()
                            }
                        }
                    )
                }
            }
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
private fun ChatMessagePair(
    messageIndex: Int,
    message: MessageV2,
    assistantMessages: List<MessageV2>,
    platformIndexState: Int,
    loadingStates: List<ChatViewModel.LoadingState>,
    enabledPlatformsInChat: List<String>,
    enabledPlatformLookup: Map<String, PlatformV2>,
    canUseChat: Boolean,
    isIdle: Boolean,
    isActiveMessage: Boolean,
    maximumUserChatBubbleWidth: Dp,
    maximumOpponentChatBubbleWidth: Dp,
    onEditQuestion: (MessageV2) -> Unit,
    onEditAssistant: (Int, Int) -> Unit,
    onCopyText: (String) -> Unit,
    onPlatformClick: (Int, Int) -> Unit,
    onSelectText: (String) -> Unit,
    onRetry: (Int, Int) -> Unit,
    onShowPreviousRevision: (Int, Int) -> Unit,
    onShowNextRevision: (Int, Int) -> Unit
) {
    val selectedAssistantMessage = assistantMessages.getOrNull(platformIndexState)
    val assistantContent = selectedAssistantMessage?.effectiveContent() ?: ""
    val assistantThoughts = selectedAssistantMessage?.effectiveThoughts() ?: ""
    val canShowPreviousRevision = selectedAssistantMessage?.let { assistantMessage ->
        assistantMessage.revisions.isNotEmpty() &&
            assistantMessage.activeRevisionIndex < assistantMessage.revisions.lastIndex
    } ?: false
    val canShowNextRevision = selectedAssistantMessage?.let { assistantMessage ->
        assistantMessage.revisions.isNotEmpty() &&
            assistantMessage.activeRevisionIndex != ACTIVE_REVISION_LATEST
    } ?: false
    val selectedPlatformUid = enabledPlatformsInChat.getOrElse(platformIndexState) { "" }
    val isCurrentPlatformLoading =
        loadingStates.getOrElse(platformIndexState) { ChatViewModel.LoadingState.Idle } == ChatViewModel.LoadingState.Loading
    var isDropDownMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                    files = message.attachments.map { it.filePathForDisplay },
                    onLongPress = { isDropDownMenuExpanded = true }
                )
                ChatBubbleDropdownMenu(
                    isChatBubbleDropdownMenuExpanded = isDropDownMenuExpanded,
                    canEdit = canUseChat && isIdle,
                    onDismissRequest = { isDropDownMenuExpanded = false },
                    onEditItemClick = { onEditQuestion(message) },
                    onCopyItemClick = { onCopyText(message.content) }
                )
            }
        }

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
                GPTMobileIcon(loading = isActiveMessage && !isIdle)
                if (enabledPlatformsInChat.size > 1) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        enabledPlatformsInChat.forEachIndexed { platformIndex, uid ->
                            PlatformButton(
                                isLoading = isActiveMessage && loadingStates[platformIndex] == ChatViewModel.LoadingState.Loading,
                                name = enabledPlatformLookup[uid]?.name ?: stringResource(R.string.unknown),
                                selected = platformIndexState == platformIndex,
                                onPlatformClick = { onPlatformClick(messageIndex, platformIndex) }
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
                canEdit = canUseChat && isIdle,
                canRetry = canUseChat && isActiveMessage && !isCurrentPlatformLoading,
                isLoading = isActiveMessage && isCurrentPlatformLoading,
                text = assistantContent,
                thoughts = assistantThoughts,
                attachments = selectedAssistantMessage?.attachments.orEmpty().map { it.filePathForDisplay },
                contentIdentity = "$messageIndex:$selectedPlatformUid",
                revisionIndexLabel = selectedAssistantMessage?.let { assistantMessage ->
                    val totalRevisions = assistantMessage.revisions.size + 1
                    if (assistantMessage.activeRevisionIndex == ACTIVE_REVISION_LATEST) {
                        stringResource(
                            R.string.revision_counter,
                            totalRevisions,
                            totalRevisions
                        )
                    } else {
                        stringResource(
                            R.string.revision_counter,
                            assistantMessage.revisions.size - assistantMessage.activeRevisionIndex,
                            totalRevisions
                        )
                    }
                },
                canShowPreviousRevision = canShowPreviousRevision,
                canShowNextRevision = canShowNextRevision,
                onCopyClick = { onCopyText(assistantContent) },
                onSelectClick = { onSelectText(assistantContent) },
                onRetryClick = { onRetry(messageIndex, platformIndexState) },
                onEditClick = { onEditAssistant(messageIndex, platformIndexState) },
                onShowPreviousRevision = { onShowPreviousRevision(messageIndex, platformIndexState) },
                onShowNextRevision = { onShowNextRevision(messageIndex, platformIndexState) }
            )
        }
    }
}

private fun chatMessagePairKey(message: MessageV2, index: Int): String = if (message.id > 0) {
    "message-${message.id}"
} else {
    "message-${message.createdAt}-$index"
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatTopBar(
    title: String,
    isMenuItemEnabled: Boolean,
    isModelItemEnabled: Boolean,
    onBackAction: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onChatTitleItemClick: () -> Unit,
    onChatModelItemClick: () -> Unit,
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
                enabled = isModelItemEnabled,
                onClick = onChatModelItemClick
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_model),
                    contentDescription = stringResource(R.string.chat_models)
                )
            }
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
    inputState: TextFieldState = rememberTextFieldState(),
    chatEnabled: Boolean = true,
    sendButtonEnabled: Boolean = true,
    selectedAttachments: List<ChatAttachmentDraft> = emptyList(),
    onFileSelected: (String) -> Unit = {},
    onFileRemoved: (String) -> Unit = {},
    onSendButtonClick: () -> Unit = {}
) {
    val localStyle = LocalTextStyle.current
    val mergedStyle = localStyle.merge(TextStyle(color = LocalContentColor.current))
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatInputLineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5)
    val hasQuestionText = inputState.text.isNotEmpty()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val filePath = withContext(Dispatchers.IO) {
                    copyFileToAppDirectory(context, it)
                }
                filePath?.let { path -> onFileSelected(path) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surface)
    ) {
        if (selectedAttachments.isNotEmpty()) {
            FileThumbnailRow(
                selectedAttachments = selectedAttachments,
                onFileRemoved = onFileRemoved
            )
        }
        BasicTextField(
            state = inputState,
            modifier = Modifier.fillMaxWidth(),
            enabled = chatEnabled,
            textStyle = mergedStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            lineLimits = chatInputLineLimits,
            decorator = { innerTextField ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(size = 24.dp))
                        .padding(all = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        enabled = chatEnabled,
                        onClick = { filePickerLauncher.launch("image/*") }
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_attach_file),
                            contentDescription = stringResource(R.string.attach_file)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        if (inputState.text.isEmpty()) {
                            Text(
                                modifier = Modifier.alpha(0.38f),
                                text = if (chatEnabled) stringResource(R.string.ask_a_question) else stringResource(R.string.some_platforms_disabled)
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            innerTextField()
                        }
                    }
                    IconButton(
                        enabled = chatEnabled && sendButtonEnabled && hasQuestionText,
                        onClick = onSendButtonClick
                    ) {
                        Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_send), contentDescription = stringResource(R.string.send))
                    }
                }
            }
        )
    }
}

@Composable
internal fun FileThumbnailRow(
    selectedAttachments: List<ChatAttachmentDraft>,
    onFileRemoved: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        selectedAttachments.forEach { attachment ->
            FileThumbnail(
                attachment = attachment,
                onRemove = { onFileRemoved(attachment.sourceFilePath) }
            )
        }
    }
}

@Composable
internal fun FileThumbnail(
    attachment: ChatAttachmentDraft,
    onRemove: () -> Unit
) {
    val file = File(attachment.preparedFilePath ?: attachment.sourceFilePath)
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
                    contentDescription = stringResource(R.string.remove),
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(10.dp)
                )
            }

            if (attachment.status == ChatAttachmentDraft.Status.Preparing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp
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

        attachment.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.width(72.dp)
            )
        }

        attachment.errorMessage?.let { errorMessage ->
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.width(72.dp)
            )
        }
    }
}

internal fun copyFileToAppDirectory(context: Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val rawFileName = getFileName(context, uri)
        val sanitizedFileName = sanitizeFileName(rawFileName)

        val attachmentsDir = File(context.filesDir, "attachments")
        attachmentsDir.mkdirs()

        var targetFile = File(attachmentsDir, sanitizedFileName)

        // If file exists, append timestamp to avoid overwrites
        if (targetFile.exists()) {
            val nameWithoutExt = sanitizedFileName.substringBeforeLast(".")
            val ext = sanitizedFileName.substringAfterLast(".", "")
            val uniqueName = if (ext.isNotEmpty()) {
                "${nameWithoutExt}_${System.currentTimeMillis()}.$ext"
            } else {
                "${sanitizedFileName}_${System.currentTimeMillis()}"
            }
            targetFile = File(attachmentsDir, uniqueName)
        }

        // Verify canonical path is within attachments directory to prevent path traversal
        val attachmentsDirCanonical = attachmentsDir.canonicalPath
        val targetFileCanonical = targetFile.canonicalPath
        if (!targetFileCanonical.startsWith(attachmentsDirCanonical + File.separator) &&
            targetFileCanonical != attachmentsDirCanonical
        ) {
            return null
        }

        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        targetFile.absolutePath
    } catch (e: Exception) {
        null
    }
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

private fun sanitizeFileName(fileName: String): String {
    val maxLength = 200

    // Remove path separators and ".." segments
    val withoutPathTraversal = fileName
        .replace("..", "")
        .replace("/", "")
        .replace("\\", "")

    // Keep only safe characters: alphanumerics, dash, underscore, dot
    val sanitized = withoutPathTraversal
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
        .take(maxLength)
        .trim('.')

    // If sanitized name is empty, generate a fallback
    return sanitized.ifEmpty { "attachment_${System.currentTimeMillis()}" }
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
