package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.repository.AttachmentUploadCoordinator
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.util.AttachmentPayloadCache
import dev.chungjungsoo.gptmobile.util.FileUtils
import dev.chungjungsoo.gptmobile.util.getPlatformName
import dev.chungjungsoo.gptmobile.util.handleStates
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val attachmentUploadCoordinator: AttachmentUploadCoordinator
) : ViewModel() {
    sealed class LoadingState {
        data object Idle : LoadingState()
        data object Loading : LoadingState()
    }

    data class GroupedMessages(
        val userMessages: List<MessageV2> = listOf(),
        val assistantMessages: List<List<MessageV2>> = listOf()
    )

    private val chatRoomId: Int = checkNotNull(savedStateHandle["chatRoomId"])
    private val enabledPlatformString: String = checkNotNull(savedStateHandle["enabledPlatforms"])
    val enabledPlatformsInChat = enabledPlatformString.split(',')

    private val currentTimeStamp: Long
        get() = System.currentTimeMillis() / 1000

    private val _chatRoom = MutableStateFlow(ChatRoomV2(id = -1, title = "", enabledPlatform = enabledPlatformsInChat))
    val chatRoom = _chatRoom.asStateFlow()

    private val _isChatTitleDialogOpen = MutableStateFlow(false)
    val isChatTitleDialogOpen = _isChatTitleDialogOpen.asStateFlow()

    private val _isEditQuestionDialogOpen = MutableStateFlow(false)
    val isEditQuestionDialogOpen = _isEditQuestionDialogOpen.asStateFlow()

    private val _isSelectTextSheetOpen = MutableStateFlow(false)
    val isSelectTextSheetOpen = _isSelectTextSheetOpen.asStateFlow()

    private val _isChatModelDialogOpen = MutableStateFlow(false)
    val isChatModelDialogOpen = _isChatModelDialogOpen.asStateFlow()

    private val _chatPlatformModels = MutableStateFlow<Map<String, String>>(emptyMap())
    val chatPlatformModels = _chatPlatformModels.asStateFlow()

    // All platforms configured in app (including disabled)
    private val _platformsInApp = MutableStateFlow(listOf<PlatformV2>())
    val platformsInApp = _platformsInApp.asStateFlow()

    // Enabled platforms list in app
    private val _enabledPlatformsInApp = MutableStateFlow(listOf<PlatformV2>())
    val enabledPlatformsInApp = _enabledPlatformsInApp.asStateFlow()

    // User input used for the chat composer
    val question = TextFieldState()

    // Selected attachment drafts for current message
    private val _selectedAttachments = MutableStateFlow(listOf<ChatAttachmentDraft>())
    val selectedAttachments = _selectedAttachments.asStateFlow()

    private val _attachmentNotice = MutableStateFlow<String?>(null)
    val attachmentNotice = _attachmentNotice.asStateFlow()

    // Chat messages currently in the chat room
    private val _groupedMessages = MutableStateFlow(GroupedMessages())
    val groupedMessages = _groupedMessages.asStateFlow()

    // Each chat states for assistant chat messages
    // Index of the currently shown message's platform - default is 0 (first platform)
    private val _indexStates = MutableStateFlow(listOf<Int>())
    val indexStates = _indexStates.asStateFlow()

    // Loading states for each platform
    private val _loadingStates = MutableStateFlow(List<LoadingState>(enabledPlatformsInChat.size) { LoadingState.Idle })
    val loadingStates = _loadingStates.asStateFlow()

    // Used for passing user question to Edit User Message Dialog
    private val _editedQuestion = MutableStateFlow(MessageV2(chatId = chatRoomId, content = "", platformType = null))
    val editedQuestion = _editedQuestion.asStateFlow()

    // Used for text data to show in SelectText Bottom Sheet
    private val _selectedText = MutableStateFlow("")
    val selectedText = _selectedText.asStateFlow()

    // State for the message loading state (From the database)
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    private var pendingQuestionText: String? = null

    init {
        fetchChatRoom()
        viewModelScope.launch { fetchMessages() }
        fetchEnabledPlatformsInApp()
        observeStateChanges()
    }

    fun addMessage(userMessage: MessageV2) {
        _groupedMessages.update {
            it.copy(
                userMessages = it.userMessages + listOf(userMessage),
                assistantMessages = it.assistantMessages + listOf(
                    enabledPlatformsInChat.map { p -> MessageV2(chatId = chatRoomId, content = "", platformType = p) }
                )
            )
        }
        _indexStates.update { it + listOf(0) }
    }

    fun askQuestion() {
        val questionText = question.text.toString()
        val hasReadyAttachments = _selectedAttachments.value.any { it.status == ChatAttachmentDraft.Status.Ready }
        val hasPreparingAttachments = _selectedAttachments.value.any { it.status == ChatAttachmentDraft.Status.Preparing }
        if (questionText.isBlank() && !hasReadyAttachments && !hasPreparingAttachments) return
        if (_selectedAttachments.value.any { it.status == ChatAttachmentDraft.Status.Failed }) {
            _attachmentNotice.update { "Remove failed attachments before sending." }
            return
        }

        if (hasPreparingAttachments) {
            pendingQuestionText = questionText
            question.clearText()
            _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Loading } }
            trySendPendingQuestionIfReady()
            return
        }

        sendQuestion(questionText, _selectedAttachments.value)
    }

    override fun onCleared() {
        AttachmentPayloadCache.clear()
        super.onCleared()
    }

    fun closeChatTitleDialog() = _isChatTitleDialogOpen.update { false }

    fun closeEditQuestionDialog() {
        _editedQuestion.update { MessageV2(chatId = chatRoomId, content = "", platformType = null) }
        _isEditQuestionDialogOpen.update { false }
    }

    fun closeSelectTextSheet() {
        _isSelectTextSheetOpen.update { false }
        _selectedText.update { "" }
    }

    fun closeChatModelDialog() = _isChatModelDialogOpen.update { false }

    fun openChatTitleDialog() = _isChatTitleDialogOpen.update { true }
    fun openChatModelDialog() = _isChatModelDialogOpen.update { true }

    fun openEditQuestionDialog(question: MessageV2) {
        _editedQuestion.update { question }
        _isEditQuestionDialogOpen.update { true }
    }

    fun openSelectTextSheet(content: String) {
        _selectedText.update { content }
        _isSelectTextSheetOpen.update { true }
    }

    fun generateDefaultChatTitle(): String? = chatRepository.generateDefaultChatTitle(_groupedMessages.value.userMessages)

    fun updateChatPlatformModels(models: Map<String, String>) {
        val sanitizedModels = models
            .filterKeys { it in enabledPlatformsInChat }
            .mapValues { (_, model) -> model.trim() }

        _chatPlatformModels.update { it + sanitizedModels }

        if (_chatRoom.value.id > 0) {
            viewModelScope.launch {
                chatRepository.saveChatPlatformModels(_chatRoom.value.id, _chatPlatformModels.value)
            }
        }
    }

    fun retryChat(turnIndex: Int, platformIndex: Int) {
        if (turnIndex !in _groupedMessages.value.assistantMessages.indices) return
        if (platformIndex >= enabledPlatformsInChat.size || platformIndex < 0) return
        val platform = _enabledPlatformsInApp.value.firstOrNull { it.uid == enabledPlatformsInChat[platformIndex] } ?: return
        val platformWithChatModel = resolvePlatformModel(platform)
        _loadingStates.update { it.toMutableList().apply { this[platformIndex] = LoadingState.Loading } }
        _groupedMessages.update {
            updateAssistantSlot(
                groupedMessages = it,
                turnIndex = turnIndex,
                platformIndex = platformIndex
            ) {
                createEmptyAssistantMessage(chatRoomId, platformWithChatModel.uid)
            }
        }

        viewModelScope.launch {
            chatRepository.completeChat(
                _groupedMessages.value.userMessages,
                _groupedMessages.value.assistantMessages,
                platformWithChatModel
            ).handleStates(
                messageFlow = _groupedMessages,
                turnIndex = turnIndex,
                platformIdx = platformIndex,
                onLoadingComplete = {
                    _loadingStates.update { it.toMutableList().apply { this[platformIndex] = LoadingState.Idle } }
                }
            )
        }
    }

    fun updateChatTitle(title: String) {
        // Should be only used for changing chat title after the chatroom is created.
        if (_chatRoom.value.id > 0) {
            _chatRoom.update { it.copy(title = title) }
            viewModelScope.launch {
                chatRepository.updateChatTitle(_chatRoom.value, title)
            }
        }
    }

    fun updateChatPlatformIndex(assistantIndex: Int, platformIndex: Int) {
        // Change the message shown in the screen to another platform
        if (assistantIndex >= _indexStates.value.size || assistantIndex < 0) return
        if (platformIndex >= enabledPlatformsInChat.size || platformIndex < 0) return

        _indexStates.update {
            val updatedIndex = it.toMutableList()
            updatedIndex[assistantIndex] = platformIndex
            updatedIndex
        }
    }

    fun addSelectedFile(filePath: String) {
        if (_selectedAttachments.value.any { it.sourceFilePath == filePath }) {
            return
        }

        _selectedAttachments.update { currentFiles ->
            currentFiles + ChatAttachmentDraft(sourceFilePath = filePath)
        }
        preprocessAttachment(filePath)
    }

    fun removeSelectedFile(filePath: String) {
        val removedAttachment = _selectedAttachments.value.firstOrNull { it.sourceFilePath == filePath }
        removedAttachment?.preparedFilePath?.let { AttachmentPayloadCache.remove(it) }
        removedAttachment?.let(::deleteDraftFiles)
        _selectedAttachments.update { currentFiles ->
            currentFiles.filter { it.sourceFilePath != filePath }
        }
        trySendPendingQuestionIfReady()
    }

    fun clearSelectedFiles() {
        _selectedAttachments.value.forEach { attachment ->
            attachment.preparedFilePath?.let { AttachmentPayloadCache.remove(it) }
        }
        _selectedAttachments.update { emptyList() }
    }

    fun consumeAttachmentNotice() {
        _attachmentNotice.update { null }
    }

    fun editQuestion(editedMessage: MessageV2) {
        val userMessages = _groupedMessages.value.userMessages
        val assistantMessages = _groupedMessages.value.assistantMessages

        // Find the index of the message being edited
        val messageIndex = userMessages.indexOfFirst { it.id == editedMessage.id }
        if (messageIndex == -1) return

        // Update the message content
        val updatedUserMessages = userMessages.toMutableList()
        updatedUserMessages[messageIndex] = editedMessage.copy(createdAt = currentTimeStamp)

        // Remove all messages after the edited question (both user and assistant messages)
        val remainingUserMessages = updatedUserMessages.take(messageIndex + 1)
        val remainingAssistantMessages = assistantMessages.take(messageIndex)

        // Update the grouped messages
        _groupedMessages.update {
            GroupedMessages(
                userMessages = remainingUserMessages,
                assistantMessages = remainingAssistantMessages
            )
        }

        // Add empty assistant message slots for the edited question
        _groupedMessages.update {
            it.copy(
                assistantMessages = it.assistantMessages + listOf(
                    enabledPlatformsInChat.map { p -> MessageV2(chatId = chatRoomId, content = "", platformType = p) }
                )
            )
        }

        // Update index states to match the new message count - trim the end part
        val removedMessagesCount = userMessages.size - remainingUserMessages.size
        _indexStates.update {
            val currentStates = it.toMutableList()
            repeat(removedMessagesCount) { currentStates.removeLastOrNull() }
            currentStates
        }

        // Start new conversation from the edited question
        completeChat()
    }

    fun exportChat(): Pair<String, String> {
        // Build the chat history in Markdown format
        val chatHistoryMarkdown = buildString {
            appendLine("# Chat Export: \"${chatRoom.value.title}\"")
            appendLine()
            appendLine("**Exported on:** ${formatCurrentDateTime()}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Chat History")
            appendLine()
            _groupedMessages.value.userMessages.forEachIndexed { i, message ->
                appendLine("**User:**")
                appendLine(message.content)
                appendLine()

                _groupedMessages.value.assistantMessages[i].forEach { message ->
                    val platformName = message.platformType
                        ?.let { _platformsInApp.value.getPlatformName(it) }
                        ?: "Unknown"
                    appendLine("**Assistant ($platformName):**")
                    appendLine(message.content)
                    appendLine()
                }
            }
        }

        // Save the Markdown file
        val fileName = "export_${chatRoom.value.title}_${System.currentTimeMillis()}.md"
        return Pair(fileName, chatHistoryMarkdown)
    }

    private fun completeChat() {
        // Update all the platform loading states to Loading
        _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Loading } }
        val turnIndex = _groupedMessages.value.assistantMessages.lastIndex

        // Send chat completion requests
        enabledPlatformsInChat.forEachIndexed { idx, platformUid ->
            val platform = _enabledPlatformsInApp.value.firstOrNull { it.uid == platformUid } ?: return@forEachIndexed
            val platformWithChatModel = resolvePlatformModel(platform)
            viewModelScope.launch {
                chatRepository.completeChat(
                    _groupedMessages.value.userMessages,
                    _groupedMessages.value.assistantMessages,
                    platformWithChatModel
                ).handleStates(
                    messageFlow = _groupedMessages,
                    turnIndex = turnIndex,
                    platformIdx = idx,
                    onLoadingComplete = {
                        _loadingStates.update { it.toMutableList().apply { this[idx] = LoadingState.Idle } }
                    }
                )
            }
        }
    }

    private fun preprocessAttachment(filePath: String) {
        viewModelScope.launch {
            val mimeType = withContext(Dispatchers.IO) {
                FileUtils.getMimeType(context, filePath)
            }

            if (!FileUtils.isSupportedUploadMimeType(mimeType)) {
                rejectAttachment(filePath, "Only image attachments are currently supported.")
                trySendPendingQuestionIfReady()
                return@launch
            }

            val fileSize = withContext(Dispatchers.IO) {
                FileUtils.getFileSize(context, filePath)
            }

            if (fileSize > FileUtils.MAX_UPLOAD_SIZE_BYTES) {
                rejectAttachment(filePath, "Files larger than 50 MB cannot be attached.")
                trySendPendingQuestionIfReady()
                return@launch
            }

            val currentDraftBytes = withContext(Dispatchers.IO) {
                _selectedAttachments.value
                    .filter { it.sourceFilePath != filePath }
                    .sumOf { FileUtils.getFileSize(context, it.sourceFilePath).coerceAtLeast(0L) }
            }

            if (FileUtils.wouldExceedTotalUploadLimit(currentDraftBytes, fileSize)) {
                rejectAttachment(filePath, "Total attachments cannot exceed 50 MB.")
                trySendPendingQuestionIfReady()
                return@launch
            }

            val preparationResult = withContext(Dispatchers.IO) {
                attachmentUploadCoordinator.prepareLocalAttachment(context, filePath)
            }

            if (_selectedAttachments.value.none { it.sourceFilePath == filePath }) {
                if (preparationResult != null && preparationResult.preparedFilePath != filePath) {
                    java.io.File(preparationResult.preparedFilePath).delete()
                }
                return@launch
            }

            _selectedAttachments.update { attachments ->
                attachments.map { attachment ->
                    if (attachment.sourceFilePath != filePath) {
                        attachment
                    } else if (preparationResult == null) {
                        attachment.copy(
                            status = ChatAttachmentDraft.Status.Failed,
                            errorMessage = "Failed to prepare attachment."
                        )
                    } else {
                        attachment.copy(
                            attachment = preparationResult,
                            preparedFilePath = preparationResult.preparedFilePath,
                            mimeType = preparationResult.mimeType,
                            status = ChatAttachmentDraft.Status.Ready,
                            notice = if (preparationResult.wasResized) {
                                "Large images are resized before upload."
                            } else {
                                null
                            },
                            errorMessage = null
                        )
                    }
                }
            }

            if (preparationResult?.wasResized == true) {
                _attachmentNotice.update { "Large images are resized before upload." }
            } else if (preparationResult == null) {
                _attachmentNotice.update { "Failed to prepare attachment." }
            }

            trySendPendingQuestionIfReady()
        }
    }

    private fun trySendPendingQuestionIfReady() {
        val queuedQuestion = pendingQuestionText ?: return
        val attachments = _selectedAttachments.value

        if (attachments.any { it.status == ChatAttachmentDraft.Status.Failed }) {
            restoreQueuedQuestion(queuedQuestion)
            pendingQuestionText = null
            _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Idle } }
            return
        }

        if (attachments.any { it.status == ChatAttachmentDraft.Status.Preparing }) {
            return
        }

        if (queuedQuestion.isBlank() && attachments.none { it.status == ChatAttachmentDraft.Status.Ready }) {
            pendingQuestionText = null
            _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Idle } }
            return
        }

        pendingQuestionText = null
        sendQuestion(queuedQuestion, attachments)
    }

    private fun sendQuestion(questionText: String, attachments: List<ChatAttachmentDraft>) {
        MessageV2(
            chatId = chatRoomId,
            content = questionText,
            attachments = attachments.mapNotNull { it.attachment },
            platformType = null,
            createdAt = currentTimeStamp
        ).let { addMessage(it) }
        question.clearText()
        clearSelectedFiles()
        completeChat()
    }

    private fun rejectAttachment(filePath: String, notice: String) {
        val rejectedAttachment = _selectedAttachments.value.firstOrNull { it.sourceFilePath == filePath }
        rejectedAttachment?.preparedFilePath?.let { AttachmentPayloadCache.remove(it) }
        rejectedAttachment?.let(::deleteDraftFiles)
        _selectedAttachments.update { attachments ->
            attachments.filter { it.sourceFilePath != filePath }
        }
        _attachmentNotice.update { notice }
    }

    private fun restoreQueuedQuestion(questionText: String) {
        if (questionText.isBlank()) return
        question.setTextAndPlaceCursorAtEnd(questionText)
    }

    private fun deleteDraftFiles(attachment: ChatAttachmentDraft) {
        java.io.File(attachment.sourceFilePath).delete()
        attachment.preparedFilePath
            ?.takeIf { it != attachment.sourceFilePath }
            ?.let { java.io.File(it).delete() }
    }

    private fun formatCurrentDateTime(): String {
        val currentDate = java.util.Date()
        val format = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm a", java.util.Locale.getDefault())
        return format.format(currentDate)
    }

    private suspend fun fetchMessages() {
        // If the room isn't new
        if (chatRoomId != 0) {
            _groupedMessages.update { fetchGroupedMessages(chatRoomId) }
            if (_groupedMessages.value.assistantMessages.size != _indexStates.value.size) {
                _indexStates.update { List(_groupedMessages.value.assistantMessages.size) { 0 } }
            }
            _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Idle } }
            _isLoaded.update { true } // Finish fetching
            return
        }

        // When message id should sync after saving chats
        if (_chatRoom.value.id != 0) {
            _groupedMessages.update { fetchGroupedMessages(_chatRoom.value.id) }
            return
        }
    }

    private suspend fun fetchGroupedMessages(chatId: Int): GroupedMessages {
        val messages = chatRepository.fetchMessagesV2(chatId).sortedBy { it.createdAt }

        val userMessages = mutableListOf<MessageV2>()
        val assistantMessages = mutableListOf<MutableList<MessageV2>>()

        messages.forEach { message ->
            if (message.platformType == null) {
                userMessages.add(message)
                assistantMessages.add(mutableListOf())
            } else {
                assistantMessages.last().add(message)
            }
        }

        val normalizedAssistantMessages = assistantMessages.map { assistantMessage ->
            normalizeAssistantRow(
                assistantMessages = assistantMessage,
                enabledPlatformsInChat = enabledPlatformsInChat,
                chatId = chatId
            )
        }

        return GroupedMessages(userMessages, normalizedAssistantMessages)
    }

    private fun fetchChatRoom() {
        viewModelScope.launch {
            _chatRoom.update {
                if (chatRoomId == 0) {
                    ChatRoomV2(id = 0, title = "Untitled Chat", enabledPlatform = enabledPlatformsInChat)
                } else {
                    chatRepository.fetchChatListV2().first { it.id == chatRoomId }
                }
            }
        }
    }

    private fun fetchEnabledPlatformsInApp() {
        viewModelScope.launch {
            val allPlatforms = settingRepository.fetchPlatformV2s()
            _platformsInApp.update { allPlatforms }
            _enabledPlatformsInApp.update { allPlatforms.filter { it.enabled } }
            initializeChatPlatformModels(allPlatforms)
        }
    }

    private suspend fun initializeChatPlatformModels(platforms: List<PlatformV2>) {
        val defaultModels = enabledPlatformsInChat.associateWith { uid ->
            platforms.firstOrNull { it.uid == uid }?.model ?: ""
        }
        val persistedModels = if (chatRoomId != 0) {
            chatRepository.fetchChatPlatformModels(chatRoomId)
        } else {
            emptyMap()
        }

        val mergedModels = defaultModels.mapValues { (uid, defaultModel) ->
            persistedModels[uid]?.takeIf { it.isNotBlank() } ?: defaultModel
        }

        _chatPlatformModels.update { mergedModels }

        if (chatRoomId != 0 && mergedModels != persistedModels) {
            chatRepository.saveChatPlatformModels(chatRoomId, mergedModels)
        }
    }

    private fun observeStateChanges() {
        viewModelScope.launch {
            _loadingStates.collect { states ->
                if (_chatRoom.value.id != -1 &&
                    states.all { it == LoadingState.Idle } &&
                    (_groupedMessages.value.userMessages.isNotEmpty() && _groupedMessages.value.assistantMessages.isNotEmpty()) &&
                    (_groupedMessages.value.userMessages.size == _groupedMessages.value.assistantMessages.size)
                ) {
                    // Save the chat & chat room
                    _chatRoom.update {
                        chatRepository.saveChat(
                            chatRoom = _chatRoom.value,
                            messages = ungroupedMessages(),
                            chatPlatformModels = _chatPlatformModels.value
                        )
                    }

                    // Sync message ids
                    fetchMessages()
                }
            }
        }
    }

    private fun resolvePlatformModel(platform: PlatformV2): PlatformV2 {
        val chatModel = _chatPlatformModels.value[platform.uid]?.trim().orEmpty()
        if (chatModel.isBlank() || chatModel == platform.model) return platform

        return platform.copy(model = chatModel)
    }

    private fun ungroupedMessages(): List<MessageV2> {
        // Flatten the grouped messages into a single list
        val merged = _groupedMessages.value.userMessages + _groupedMessages.value.assistantMessages.flatten()
        return merged.filter { it.content.isNotBlank() }.sortedBy { it.createdAt }
    }
}

internal fun createEmptyAssistantMessage(chatId: Int, platformUid: String): MessageV2 = MessageV2(
    chatId = chatId,
    content = "",
    platformType = platformUid
)

internal fun normalizeAssistantRow(
    assistantMessages: List<MessageV2>,
    enabledPlatformsInChat: List<String>,
    chatId: Int
): List<MessageV2> {
    if (enabledPlatformsInChat.isEmpty()) return assistantMessages

    val consumedIndexes = mutableSetOf<Int>()
    val normalizedMessages = enabledPlatformsInChat.map { platformUid ->
        val matchedIndex = assistantMessages.indices.firstOrNull { index ->
            index !in consumedIndexes && assistantMessages[index].platformType == platformUid
        }

        if (matchedIndex == null) {
            createEmptyAssistantMessage(chatId, platformUid)
        } else {
            consumedIndexes += matchedIndex
            assistantMessages[matchedIndex]
        }
    }
    val overflowMessages = assistantMessages.filterIndexed { index, _ -> index !in consumedIndexes }

    return normalizedMessages + overflowMessages
}

internal fun updateAssistantSlot(
    groupedMessages: ChatViewModel.GroupedMessages,
    turnIndex: Int,
    platformIndex: Int,
    transform: (MessageV2) -> MessageV2
): ChatViewModel.GroupedMessages {
    if (turnIndex !in groupedMessages.assistantMessages.indices) return groupedMessages

    val currentTurnMessages = groupedMessages.assistantMessages[turnIndex]
    if (platformIndex !in currentTurnMessages.indices) return groupedMessages

    val updatedTurnMessages = currentTurnMessages.toMutableList()
    val updatedMessage = transform(updatedTurnMessages[platformIndex])
    if (updatedMessage == updatedTurnMessages[platformIndex]) return groupedMessages

    updatedTurnMessages[platformIndex] = updatedMessage
    val assistantMessages = groupedMessages.assistantMessages.toMutableList()
    assistantMessages[turnIndex] = updatedTurnMessages

    return groupedMessages.copy(assistantMessages = assistantMessages)
}
