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
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.agent.AgentRunCoordinator
import dev.chungjungsoo.gptmobile.data.agent.AgentRunRequest
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.ACTIVE_REVISION_LATEST
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunDraft
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.LEGACY_ORDER_NOTICE
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveRunId
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveThoughts
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveTimeline
import dev.chungjungsoo.gptmobile.data.database.entity.hasUnavailableAssistantOrder
import dev.chungjungsoo.gptmobile.data.database.entity.rebuildAssistantTimelineForEdit
import dev.chungjungsoo.gptmobile.data.database.entity.resetActiveRevision
import dev.chungjungsoo.gptmobile.data.database.entity.selectRevision
import dev.chungjungsoo.gptmobile.data.database.entity.snapshotLatestAssistantRevision
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.repository.AttachmentUploadCoordinator
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.presentation.StartupRecoveryGate
import dev.chungjungsoo.gptmobile.presentation.ui.setup.DownloadedLocalModelOption
import dev.chungjungsoo.gptmobile.util.AttachmentPayloadCache
import dev.chungjungsoo.gptmobile.util.FileUtils
import dev.chungjungsoo.gptmobile.util.buildAssistantErrorContent
import dev.chungjungsoo.gptmobile.util.determineLocalNetworkAccessRequirement
import dev.chungjungsoo.gptmobile.util.getPlatformName
import dev.chungjungsoo.gptmobile.util.requiresLocalNetworkAccess
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val attachmentUploadCoordinator: AttachmentUploadCoordinator,
    private val agentRunCoordinator: AgentRunCoordinator,
    private val toolConnectionRepository: ToolConnectionRepository,
    private val localModelRepository: LocalModelRepository,
    private val modelCatalogRepository: ModelCatalogRepository
) : ViewModel() {
    sealed class LoadingState {
        data object Idle : LoadingState()
        data object Loading : LoadingState()
    }

    data class GroupedMessages(
        val userMessages: List<MessageV2> = listOf(),
        val assistantMessages: List<List<MessageV2>> = listOf()
    )

    enum class MessageEditRole {
        USER,
        ASSISTANT
    }

    data class MessageEditSession(
        val message: MessageV2,
        val role: MessageEditRole,
        val turnIndex: Int? = null,
        val platformIndex: Int? = null,
        val attachments: List<ChatAttachmentDraft> = emptyList()
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

    private val _messageEditSession = MutableStateFlow<MessageEditSession?>(null)
    val messageEditSession = _messageEditSession.asStateFlow()

    private val _isSelectTextSheetOpen = MutableStateFlow(false)
    val isSelectTextSheetOpen = _isSelectTextSheetOpen.asStateFlow()

    private val _isChatModelDialogOpen = MutableStateFlow(false)
    val isChatModelDialogOpen = _isChatModelDialogOpen.asStateFlow()

    private val _chatPlatformModels = MutableStateFlow<Map<String, String>>(emptyMap())
    val chatPlatformModels = _chatPlatformModels.asStateFlow()

    private val _catalogEntries = MutableStateFlow<List<CatalogEntry>>(emptyList())
    val catalogEntries = _catalogEntries.asStateFlow()
    val downloadedLocalModels: StateFlow<List<DownloadedLocalModelOption>> = combine(
        localModelRepository.observeAll(),
        _catalogEntries
    ) { models, catalog ->
        val names = catalog.associate { it.id to it.displayName }
        models.filter { it.status == LocalModelStatus.READY }.map { model ->
            DownloadedLocalModelOption(
                catalogEntryId = model.catalogEntryId,
                displayName = names[model.catalogEntryId]?.takeIf { it.isNotBlank() } ?: model.catalogEntryId
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private val _runNoticesById = MutableStateFlow<Map<String, List<ChatRunNotice>>>(emptyMap())
    val runNoticesById = _runNoticesById.asStateFlow()

    private val _needsLocalNetworkAccess = MutableStateFlow(false)
    val needsLocalNetworkAccess = _needsLocalNetworkAccess.asStateFlow()

    // Chat messages currently in the chat room
    private val _groupedMessages = MutableStateFlow(GroupedMessages())
    val groupedMessages = _groupedMessages.asStateFlow()

    private val _toolEventsByRun = MutableStateFlow<Map<String, List<ToolEvent>>>(emptyMap())
    val toolEventsByRun = _toolEventsByRun.asStateFlow()

    private val _agentRunsById = MutableStateFlow<Map<String, AgentRun>>(emptyMap())
    val agentRunsById = _agentRunsById.asStateFlow()

    // Each chat states for assistant chat messages
    // Index of the currently shown message's platform - default is 0 (first platform)
    private val _indexStates = MutableStateFlow(listOf<Int>())
    val indexStates = _indexStates.asStateFlow()

    // Loading states for each platform
    private val _loadingStates = MutableStateFlow(List<LoadingState>(enabledPlatformsInChat.size) { LoadingState.Idle })
    val loadingStates = _loadingStates.asStateFlow()

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
        observePersistedMessages()
        observeAgentRuns()
        observeToolEvents()
        observeAgentNotices()
        viewModelScope.launch {
            _catalogEntries.value = modelCatalogRepository.getCachedVisibleEntries()
        }
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

    fun cancelActiveRuns() {
        _chatRoom.value.id.takeIf { it > 0 }?.let(agentRunCoordinator::cancelChat)
    }

    fun refreshLocalNetworkRequirement() {
        fetchEnabledPlatformsInApp()
    }

    override fun onCleared() {
        AttachmentPayloadCache.clear()
        super.onCleared()
    }

    fun closeChatTitleDialog() = _isChatTitleDialogOpen.update { false }

    fun discardMessageEditDialog() {
        _messageEditSession.value?.attachments?.forEach { attachment ->
            if (attachment.cleanupOnDiscard) {
                attachment.preparedFilePath?.let { AttachmentPayloadCache.remove(it) }
                deleteDraftFiles(attachment)
            }
        }
        _messageEditSession.update { null }
    }

    fun finishMessageEditDialog() {
        _messageEditSession.update { null }
    }

    fun closeSelectTextSheet() {
        _isSelectTextSheetOpen.update { false }
        _selectedText.update { "" }
    }

    fun closeChatModelDialog() = _isChatModelDialogOpen.update { false }

    fun openChatTitleDialog() = _isChatTitleDialogOpen.update { true }
    fun openChatModelDialog() = _isChatModelDialogOpen.update { true }

    fun openUserMessageEditDialog(question: MessageV2) {
        _messageEditSession.update {
            MessageEditSession(
                message = question,
                role = MessageEditRole.USER,
                attachments = question.attachments.map(ChatAttachmentDraft::fromAttachment)
            )
        }
    }

    fun openAssistantMessageEditDialog(turnIndex: Int, platformIndex: Int) {
        val assistantMessage = _groupedMessages.value.assistantMessages
            .getOrNull(turnIndex)
            ?.getOrNull(platformIndex)
            ?: return
        _messageEditSession.update {
            MessageEditSession(
                message = assistantMessage,
                role = MessageEditRole.ASSISTANT,
                turnIndex = turnIndex,
                platformIndex = platformIndex,
                attachments = assistantMessage.attachments.map(ChatAttachmentDraft::fromAttachment)
            )
        }
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
        val platform = _platformsInApp.value.firstOrNull { it.uid == enabledPlatformsInChat[platformIndex] } ?: return
        val platformWithChatModel = resolvePlatformModel(platform)
        val currentAssistantMessage = _groupedMessages.value.assistantMessages
            .getOrNull(turnIndex)
            ?.getOrNull(platformIndex)
            ?: return
        val userMessage = _groupedMessages.value.userMessages.getOrNull(turnIndex) ?: return
        val runId = UUID.randomUUID().toString()
        _loadingStates.update { it.toMutableList().apply { this[platformIndex] = LoadingState.Loading } }

        viewModelScope.launch {
            persistBeforeProvider(
                persist = {
                    chatRepository.persistAgentRetry(
                        PersistAgentRetryRequest(
                            userMessage = userMessage,
                            assistantMessage = currentAssistantMessage,
                            run = AgentRunDraft(
                                runId = runId,
                                profileUid = platformWithChatModel.uid,
                                providerSnapshot = platformWithChatModel.compatibleType.name,
                                modelSnapshot = platformWithChatModel.model,
                                createdAt = currentTimeStamp
                            )
                        )
                    )
                },
                startProvider = { persisted ->
                    _groupedMessages.update { groupedMessages ->
                        updateAssistantSlot(groupedMessages, turnIndex, platformIndex) { persisted.assistantMessage }
                    }
                    val contextMessages = groupedMessagesThroughTurn(_groupedMessages.value, turnIndex)
                    agentRunCoordinator.start(
                        listOf(
                            AgentRunRequest(
                                runId = runId,
                                chatId = persisted.assistantMessage.chatId,
                                assistantMessage = persisted.assistantMessage,
                                platform = platformWithChatModel,
                                userMessages = contextMessages.userMessages,
                                assistantMessages = contextMessages.assistantMessages
                            )
                        )
                    )
                },
                onFailure = { error ->
                    showPersistenceFailure(turnIndex, listOf(platformIndex), error)
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
        addDraftFile(
            currentAttachments = { _selectedAttachments.value },
            updateAttachments = { attachments -> _selectedAttachments.update { attachments } },
            filePath = filePath,
            onNotice = { notice -> _attachmentNotice.update { notice } }
        )
    }

    fun removeSelectedFile(filePath: String) {
        removeDraftFile(
            currentAttachments = { _selectedAttachments.value },
            updateAttachments = { attachments -> _selectedAttachments.update { attachments } },
            filePath = filePath
        )
        trySendPendingQuestionIfReady()
    }

    fun addMessageEditFile(filePath: String) {
        addDraftFile(
            currentAttachments = { _messageEditSession.value?.attachments.orEmpty() },
            updateAttachments = ::updateMessageEditAttachments,
            filePath = filePath,
            onNotice = { notice -> _attachmentNotice.update { notice } }
        )
    }

    fun removeMessageEditFile(filePath: String) {
        removeDraftFile(
            currentAttachments = { _messageEditSession.value?.attachments.orEmpty() },
            updateAttachments = ::updateMessageEditAttachments,
            filePath = filePath
        )
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

    fun notifyAttachmentCopyFailed() {
        _attachmentNotice.update { "Failed to copy attachment." }
    }

    fun saveUserMessageEdit(
        editedMessage: MessageV2,
        attachments: List<ChatAttachmentDraft>
    ): Boolean {
        if (attachments.any { it.status != ChatAttachmentDraft.Status.Ready }) {
            _attachmentNotice.update { "Wait for attachments to finish processing before saving." }
            return false
        }

        val userMessages = _groupedMessages.value.userMessages
        val assistantMessages = _groupedMessages.value.assistantMessages

        // Find the index of the message being edited
        val messageIndex = userMessages.indexOfFirst { it.id == editedMessage.id }
        if (messageIndex == -1) return false

        // Update the message content
        val updatedUserMessages = userMessages.toMutableList()
        updatedUserMessages[messageIndex] = editedMessage.copy(
            attachments = attachments.mapNotNull { it.attachment },
            createdAt = currentTimeStamp
        )

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
        cancelActiveRuns()
        completeChat(persistSnapshotFirst = true)
        return true
    }

    fun saveAssistantMessageEdit(
        editedMessage: MessageV2,
        thoughts: String,
        attachments: List<ChatAttachmentDraft>
    ): Boolean {
        if (attachments.any { it.status != ChatAttachmentDraft.Status.Ready }) {
            _attachmentNotice.update { "Wait for attachments to finish processing before saving." }
            return false
        }

        val session = _messageEditSession.value ?: return false
        val turnIndex = session.turnIndex ?: return false
        val platformIndex = session.platformIndex ?: return false
        val currentMessage = _groupedMessages.value.assistantMessages
            .getOrNull(turnIndex)
            ?.getOrNull(platformIndex)
            ?: return false

        val updatedContent = editedMessage.content
        val updatedThoughts = thoughts
        val updatedAttachments = attachments.mapNotNull { it.attachment }

        val textChanged = currentMessage.content != updatedContent || currentMessage.thoughts != updatedThoughts
        val updatedTimeline = if (textChanged) {
            rebuildAssistantTimelineForEdit(
                currentTimeline = currentMessage.timeline,
                updatedContent = updatedContent,
                updatedThoughts = updatedThoughts,
                hasToolTrace = currentMessage.currentRunId
                    ?.let(_toolEventsByRun.value::get)
                    .orEmpty()
                    .isNotEmpty()
            )
        } else {
            currentMessage.timeline
        }
        val updatedRevisions = if (textChanged) {
            currentMessage.snapshotLatestAssistantRevision(currentTimeStamp)
                ?.let { listOf(it) + currentMessage.revisions }
                ?: currentMessage.revisions
        } else {
            currentMessage.revisions
        }

        _groupedMessages.update {
            updateAssistantSlot(
                groupedMessages = it,
                turnIndex = turnIndex,
                platformIndex = platformIndex
            ) { assistantMessage ->
                assistantMessage.copy(
                    content = updatedContent,
                    thoughts = updatedThoughts,
                    timeline = updatedTimeline,
                    attachments = updatedAttachments,
                    revisions = updatedRevisions,
                    createdAt = assistantMessage.createdAt
                ).resetActiveRevision()
            }
        }
        persistCurrentChatSnapshot()
        return true
    }

    fun showPreviousAssistantRevision(turnIndex: Int, platformIndex: Int) {
        updateAssistantRevisionSelection(turnIndex, platformIndex) { message ->
            when {
                message.revisions.isEmpty() -> message.activeRevisionIndex
                message.activeRevisionIndex == ACTIVE_REVISION_LATEST -> 0
                message.activeRevisionIndex < message.revisions.lastIndex -> message.activeRevisionIndex + 1
                else -> message.activeRevisionIndex
            }
        }
    }

    fun showNextAssistantRevision(turnIndex: Int, platformIndex: Int) {
        updateAssistantRevisionSelection(turnIndex, platformIndex) { message ->
            when {
                message.activeRevisionIndex == ACTIVE_REVISION_LATEST -> ACTIVE_REVISION_LATEST
                message.activeRevisionIndex == 0 -> ACTIVE_REVISION_LATEST
                else -> message.activeRevisionIndex - 1
            }
        }
    }

    fun exportChat(
        toolTraceLabels: ToolTraceLabels = ToolTraceLabels.Default,
        legacyOrderNotice: String = LEGACY_ORDER_NOTICE
    ): Pair<String, String> {
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
                    append(formatAssistantExport(platformName, message, _toolEventsByRun.value, toolTraceLabels, legacyOrderNotice))
                }
            }
        }

        // Save the Markdown file
        val fileName = "export_${chatRoom.value.title}_${System.currentTimeMillis()}.md"
        return Pair(fileName, chatHistoryMarkdown)
    }

    private fun completeChat(persistSnapshotFirst: Boolean = false) {
        // Update all the platform loading states to Loading
        _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Loading } }
        val turnIndex = _groupedMessages.value.assistantMessages.lastIndex

        viewModelScope.launch {
            val platforms = resolveSelectedPlatforms(enabledPlatformsInChat, _platformsInApp.value)
                .map { IndexedValue(it.index, resolvePlatformModel(it.value)) }
            val unavailableIndexes = enabledPlatformsInChat.indices - platforms.mapTo(mutableSetOf()) { it.index }
            _loadingStates.update { states ->
                states.toMutableList().apply {
                    unavailableIndexes.forEach { this[it] = LoadingState.Idle }
                }
            }
            if (platforms.isEmpty()) {
                _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Idle } }
                return@launch
            }
            val timestamp = currentTimeStamp
            val userMessage = _groupedMessages.value.userMessages.getOrNull(turnIndex)
            if (userMessage == null) {
                _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Idle } }
                return@launch
            }
            val runs = platforms.map { (_, platform) ->
                AgentRunDraft(
                    runId = UUID.randomUUID().toString(),
                    profileUid = platform.uid,
                    providerSnapshot = platform.compatibleType.name,
                    modelSnapshot = platform.model,
                    createdAt = timestamp
                )
            }
            val chatRoom = _chatRoom.value.copy(
                title = if (_chatRoom.value.id == 0) {
                    userMessage.content.replace('\n', ' ').take(50)
                } else {
                    _chatRoom.value.title
                },
                updatedAt = timestamp
            )
            persistBeforeProvider(
                persist = {
                    if (persistSnapshotFirst && _chatRoom.value.id > 0) {
                        chatRepository.saveChat(
                            chatRoom = _chatRoom.value,
                            messages = persistableMessages(_groupedMessages.value),
                            chatPlatformModels = _chatPlatformModels.value
                        )
                    }
                    chatRepository.persistAgentTurn(
                        PersistAgentTurnRequest(
                            chatRoom = chatRoom,
                            userMessage = userMessage,
                            runs = runs,
                            chatPlatformModels = _chatPlatformModels.value.filterKeys { it in chatRoom.enabledPlatform }
                        )
                    )
                },
                startProvider = { persisted ->
                    _chatRoom.update { persisted.chatRoom }
                    _groupedMessages.update { groupedMessages ->
                        groupedMessages.copy(
                            userMessages = groupedMessages.userMessages.toMutableList().apply {
                                this[turnIndex] = persisted.userMessage
                            },
                            assistantMessages = groupedMessages.assistantMessages.toMutableList().apply {
                                this[turnIndex] = mergePersistedAssistantRow(
                                    currentRow = this[turnIndex],
                                    selectedProfileUids = enabledPlatformsInChat,
                                    persistedMessages = persisted.assistantMessages,
                                    chatId = persisted.chatRoom.id
                                )
                            }
                        )
                    }
                    val contextMessages = _groupedMessages.value
                    agentRunCoordinator.start(
                        platforms.mapIndexed { runIndex, (_, platform) ->
                            AgentRunRequest(
                                runId = runs[runIndex].runId,
                                chatId = persisted.chatRoom.id,
                                assistantMessage = persisted.assistantMessages[runIndex],
                                platform = platform,
                                userMessages = contextMessages.userMessages,
                                assistantMessages = contextMessages.assistantMessages
                            )
                        }
                    )
                },
                onFailure = { error ->
                    showPersistenceFailure(turnIndex, platforms.map { it.index }, error)
                }
            )
        }
    }

    private fun showPersistenceFailure(turnIndex: Int, platformIndexes: List<Int>, error: Throwable) {
        val message = error.message ?: "Failed to save this turn."
        _groupedMessages.update { groupedMessages ->
            platformIndexes.fold(groupedMessages) { current, platformIndex ->
                updateAssistantSlot(current, turnIndex, platformIndex) { assistantMessage ->
                    assistantMessage.copy(
                        content = buildAssistantErrorContent(assistantMessage.content, message),
                        createdAt = currentTimeStamp
                    )
                }
            }
        }
        _loadingStates.update { states ->
            states.toMutableList().apply {
                platformIndexes.forEach { index ->
                    if (index in indices) this[index] = LoadingState.Idle
                }
            }
        }
    }

    private fun updateMessageEditAttachments(attachments: List<ChatAttachmentDraft>) {
        _messageEditSession.update { session ->
            session?.copy(attachments = attachments)
        }
    }

    private fun addDraftFile(
        currentAttachments: () -> List<ChatAttachmentDraft>,
        updateAttachments: (List<ChatAttachmentDraft>) -> Unit,
        filePath: String,
        onNotice: (String?) -> Unit = {}
    ) {
        if (currentAttachments().any { it.sourceFilePath == filePath }) return

        updateAttachments(currentAttachments() + ChatAttachmentDraft(sourceFilePath = filePath))
        preprocessDraftAttachment(
            currentAttachments = currentAttachments,
            updateAttachments = updateAttachments,
            filePath = filePath,
            onNotice = onNotice
        )
    }

    private fun removeDraftFile(
        currentAttachments: () -> List<ChatAttachmentDraft>,
        updateAttachments: (List<ChatAttachmentDraft>) -> Unit,
        filePath: String
    ) {
        val removedAttachment = currentAttachments().firstOrNull { it.sourceFilePath == filePath }
        removedAttachment?.preparedFilePath?.let { AttachmentPayloadCache.remove(it) }
        if (removedAttachment?.cleanupOnDiscard == true) {
            removedAttachment.let(::deleteDraftFiles)
        }
        updateAttachments(currentAttachments().filter { it.sourceFilePath != filePath })
    }

    private fun preprocessDraftAttachment(
        currentAttachments: () -> List<ChatAttachmentDraft>,
        updateAttachments: (List<ChatAttachmentDraft>) -> Unit,
        filePath: String,
        onNotice: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val mimeType = withContext(Dispatchers.IO) {
                FileUtils.getMimeType(context, filePath)
            }

            if (!FileUtils.isSupportedUploadMimeType(mimeType)) {
                rejectDraftAttachment(
                    currentAttachments = currentAttachments,
                    updateAttachments = updateAttachments,
                    filePath = filePath,
                    notice = "Only image attachments are currently supported."
                )
                trySendPendingQuestionIfReady()
                return@launch
            }

            val fileSize = withContext(Dispatchers.IO) {
                FileUtils.getFileSize(context, filePath)
            }

            if (fileSize > FileUtils.MAX_UPLOAD_SIZE_BYTES) {
                rejectDraftAttachment(
                    currentAttachments = currentAttachments,
                    updateAttachments = updateAttachments,
                    filePath = filePath,
                    notice = "Files larger than 50 MB cannot be attached."
                )
                trySendPendingQuestionIfReady()
                return@launch
            }

            val currentDraftBytes = withContext(Dispatchers.IO) {
                currentAttachments()
                    .filter { it.sourceFilePath != filePath }
                    .sumOf { FileUtils.getFileSize(context, it.sourceFilePath).coerceAtLeast(0L) }
            }

            if (FileUtils.wouldExceedTotalUploadLimit(currentDraftBytes, fileSize)) {
                rejectDraftAttachment(
                    currentAttachments = currentAttachments,
                    updateAttachments = updateAttachments,
                    filePath = filePath,
                    notice = "Total attachments cannot exceed 50 MB."
                )
                trySendPendingQuestionIfReady()
                return@launch
            }

            val preparationResult = withContext(Dispatchers.IO) {
                attachmentUploadCoordinator.prepareLocalAttachment(context, filePath)
            }

            if (currentAttachments().none { it.sourceFilePath == filePath }) {
                if (preparationResult != null && preparationResult.preparedFilePath != filePath) {
                    java.io.File(preparationResult.preparedFilePath).delete()
                }
                return@launch
            }

            updateAttachments(
                currentAttachments().map { attachment ->
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
                            cleanupOnDiscard = true,
                            notice = if (preparationResult.wasResized) {
                                "Large images are resized before upload."
                            } else {
                                null
                            },
                            errorMessage = null
                        )
                    }
                }
            )

            if (preparationResult?.wasResized == true) {
                onNotice("Large images are resized before upload.")
            } else if (preparationResult == null) {
                onNotice("Failed to prepare attachment.")
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

    private fun rejectDraftAttachment(
        currentAttachments: () -> List<ChatAttachmentDraft>,
        updateAttachments: (List<ChatAttachmentDraft>) -> Unit,
        filePath: String,
        notice: String
    ) {
        val rejectedAttachment = currentAttachments().firstOrNull { it.sourceFilePath == filePath }
        rejectedAttachment?.preparedFilePath?.let { AttachmentPayloadCache.remove(it) }
        if (rejectedAttachment?.cleanupOnDiscard == true) {
            rejectedAttachment.let(::deleteDraftFiles)
        }
        updateAttachments(currentAttachments().filter { it.sourceFilePath != filePath })
        _attachmentNotice.update { notice }
    }

    private fun restoreQueuedQuestion(questionText: String) {
        if (questionText.isBlank()) return
        question.setTextAndPlaceCursorAtEnd(questionText)
    }

    private fun deleteDraftFiles(attachment: ChatAttachmentDraft) {
        if (!attachment.cleanupOnDiscard) return
        java.io.File(attachment.sourceFilePath).delete()
        attachment.preparedFilePath
            ?.takeIf { it != attachment.sourceFilePath }
            ?.let { java.io.File(it).delete() }
    }

    /**
     * Assistant revisions are stored newest-first: revisions[0] is the newest
     * saved answer, and ACTIVE_REVISION_LATEST points at the live content.
     */
    private fun updateAssistantRevisionSelection(
        turnIndex: Int,
        platformIndex: Int,
        nextIndex: (MessageV2) -> Int
    ) {
        _groupedMessages.update {
            updateAssistantSlot(
                groupedMessages = it,
                turnIndex = turnIndex,
                platformIndex = platformIndex
            ) { message ->
                message.selectRevision(nextIndex(message))
            }
        }
        persistCurrentChatSnapshot()
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
        return groupPersistedMessages(messages, enabledPlatformsInChat, chatId)
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
            initializeChatPlatformModels(allPlatforms)
            updateLocalNetworkRequirement(allPlatforms)
            _enabledPlatformsInApp.update { allPlatforms.filter { it.enabled } }
        }
    }

    private suspend fun updateLocalNetworkRequirement(platforms: List<PlatformV2>) {
        val selectedProfiles = platforms.filter { it.uid in enabledPlatformsInChat }
        val providerNeedsAccess = selectedProfiles.any { requiresLocalNetworkAccess(it.apiUrl) }
        val requiresAccess = determineLocalNetworkAccessRequirement(
            providerNeedsAccess = providerNeedsAccess,
            toolNeedsAccess = {
                enabledPlatformsInChat.any { profileUid ->
                    toolConnectionRepository.listBindingsWithConnections(profileUid).any { binding ->
                        binding.connection?.endpointUrl?.let(::requiresLocalNetworkAccess) == true
                    }
                }
            },
            onLookupFailure = {
                _attachmentNotice.update { context.getString(R.string.local_network_check_failed) }
            }
        )
        _needsLocalNetworkAccess.update { requiresAccess }
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePersistedMessages() {
        viewModelScope.launch {
            StartupRecoveryGate.await()
            _chatRoom
                .map { it.id }
                .distinctUntilChanged()
                .flatMapLatest { chatId ->
                    if (chatId > 0) {
                        chatRepository.observeMessagesV2(chatId).map { messages -> chatId to messages }
                    } else {
                        flowOf(chatId to emptyList())
                    }
                }
                .collect { (chatId, messages) ->
                    if (chatId <= 0) return@collect
                    val groupedMessages = groupPersistedMessages(messages, enabledPlatformsInChat, chatId)
                    _groupedMessages.update { groupedMessages }
                    _indexStates.update { current ->
                        List(groupedMessages.assistantMessages.size) { index -> current.getOrElse(index) { 0 } }
                    }
                    syncLoadingStates(_agentRunsById.value.values.toList())
                    _isLoaded.update { true }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeAgentRuns() {
        viewModelScope.launch {
            StartupRecoveryGate.await()
            _chatRoom
                .map { it.id }
                .distinctUntilChanged()
                .flatMapLatest { chatId ->
                    if (chatId > 0) chatRepository.observeAgentRuns(chatId) else flowOf(emptyList())
                }
                .collect { runs ->
                    _agentRunsById.update { runs.associateBy(AgentRun::runId) }
                    _runNoticesById.update { current ->
                        pruneTransientChatRunNotices(
                            current,
                            runStatuses = runs.associate { it.runId to it.status },
                            activeRunIds = agentRunCoordinator.activeRuns.value.keys
                        )
                    }
                    syncLoadingStates(runs)
                }
        }
    }

    private fun observeAgentNotices() {
        viewModelScope.launch {
            agentRunCoordinator.notices.collect { notice ->
                if (notice.chatId == _chatRoom.value.id) {
                    _runNoticesById.update { current ->
                        applyChatRunNotice(current, notice.runId, notice.message, notice.persistent)
                    }
                }
            }
        }
        viewModelScope.launch {
            agentRunCoordinator.activeRuns.collect {
                syncLoadingStates(_agentRunsById.value.values.toList())
            }
        }
    }

    private fun syncLoadingStates(runs: List<AgentRun>) {
        val runsById = runs.associateBy(AgentRun::runId)
        val activeRunIds = agentRunCoordinator.activeRuns.value.keys
        val latestAssistantRow = _groupedMessages.value.assistantMessages.lastOrNull()
        _loadingStates.update {
            loadingStatesForLatestAssistant(
                platformCount = enabledPlatformsInChat.size,
                latestAssistantRow = latestAssistantRow,
                runsById = runsById,
                activeRunIds = activeRunIds
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeToolEvents() {
        viewModelScope.launch {
            StartupRecoveryGate.await()
            _chatRoom
                .map { it.id }
                .distinctUntilChanged()
                .flatMapLatest { chatId ->
                    if (chatId > 0) chatRepository.observeToolEvents(chatId) else flowOf(emptyList())
                }
                .collect { events ->
                    _toolEventsByRun.update { events.groupBy(ToolEvent::runId) }
                }
        }
    }

    private fun resolvePlatformModel(platform: PlatformV2): PlatformV2 = resolvePlatformModel(platform, _chatPlatformModels.value)

    private fun persistCurrentChatSnapshot() {
        viewModelScope.launch {
            val chatRoom = _chatRoom.value
            val groupedMessages = _groupedMessages.value
            if (chatRoom.id <= 0) return@launch
            if (groupedMessages.userMessages.isEmpty()) return@launch
            if (groupedMessages.userMessages.size != groupedMessages.assistantMessages.size) return@launch

            withContext(Dispatchers.IO) {
                chatRepository.saveChat(
                    chatRoom = chatRoom,
                    messages = persistableMessages(groupedMessages),
                    chatPlatformModels = _chatPlatformModels.value
                )
            }
        }
    }
}

data class ChatRunNotice(
    val message: String,
    val persistent: Boolean
)

internal fun applyChatRunNotice(
    noticesByRunId: Map<String, List<ChatRunNotice>>,
    runId: String,
    message: String,
    persistent: Boolean
): Map<String, List<ChatRunNotice>> {
    if (runId.isBlank() || message.isBlank()) return noticesByRunId
    val current = noticesByRunId[runId].orEmpty()
    if (current.any { it.message == message && it.persistent == persistent }) return noticesByRunId
    return noticesByRunId + (runId to (current + ChatRunNotice(message, persistent)))
}

internal fun pruneTransientChatRunNotices(
    noticesByRunId: Map<String, List<ChatRunNotice>>,
    runStatuses: Map<String, String>,
    activeRunIds: Set<String>
): Map<String, List<ChatRunNotice>> = noticesByRunId.mapValues { (runId, notices) ->
    val status = runStatuses[runId]
    val isActive = runId in activeRunIds || status == AgentRunStatus.QUEUED || status == AgentRunStatus.RUNNING
    if (isActive) notices else notices.filter { it.persistent }
}.filterValues { it.isNotEmpty() }

internal fun visibleChatRunNotices(
    stored: List<ChatRunNotice>,
    timelineNotices: List<String>,
    isRunActive: Boolean
): List<String> {
    val fromStore = stored.filter { it.persistent || isRunActive }.map { it.message }
    return (timelineNotices + fromStore).distinct()
}

internal fun timelineNoticeMessages(timeline: List<AssistantTimelineItem>): List<String> = timeline.filter { it.type == AssistantTimelineItemType.NOTICE }.map { it.content }.filter { it.isNotBlank() }

internal fun loadingStatesForLatestAssistant(
    platformCount: Int,
    latestAssistantRow: List<MessageV2>?,
    runsById: Map<String, AgentRun>,
    activeRunIds: Set<String>
): List<ChatViewModel.LoadingState> = List(platformCount) { platformIndex ->
    val runId = latestAssistantRow?.getOrNull(platformIndex)?.currentRunId
    val status = runId?.let(runsById::get)?.status
    if (runId in activeRunIds || status == AgentRunStatus.QUEUED || status == AgentRunStatus.RUNNING) {
        ChatViewModel.LoadingState.Loading
    } else {
        ChatViewModel.LoadingState.Idle
    }
}

internal fun groupPersistedMessages(
    messages: List<MessageV2>,
    enabledPlatformsInChat: List<String>,
    chatId: Int
): ChatViewModel.GroupedMessages {
    val userMessages = mutableListOf<MessageV2>()
    val assistantMessages = mutableListOf<MutableList<MessageV2>>()
    messages.forEach { message ->
        if (message.platformType == null) {
            userMessages += message
            assistantMessages += mutableListOf<MessageV2>()
        } else {
            assistantMessages.lastOrNull()?.add(message)
        }
    }
    return ChatViewModel.GroupedMessages(
        userMessages = userMessages,
        assistantMessages = assistantMessages.map { row ->
            normalizeAssistantRow(row, enabledPlatformsInChat, chatId)
        }
    )
}

internal fun groupedMessagesThroughTurn(
    groupedMessages: ChatViewModel.GroupedMessages,
    turnIndex: Int
): ChatViewModel.GroupedMessages = groupedMessages.copy(
    userMessages = groupedMessages.userMessages.take(turnIndex + 1),
    assistantMessages = groupedMessages.assistantMessages.take(turnIndex + 1)
)

internal fun resolvePlatformModel(
    platform: PlatformV2,
    chatPlatformModels: Map<String, String>
): PlatformV2 {
    val chatModel = chatPlatformModels[platform.uid]?.trim().orEmpty()
    if (chatModel.isBlank() || chatModel == platform.model) return platform

    return platform.copy(model = chatModel)
}

internal fun resolveSelectedPlatforms(
    selectedProfileUids: List<String>,
    configuredPlatforms: List<PlatformV2>
): List<IndexedValue<PlatformV2>> {
    val platformsByUid = configuredPlatforms.associateBy(PlatformV2::uid)
    return selectedProfileUids.mapIndexedNotNull { index, uid ->
        platformsByUid[uid]?.let { IndexedValue(index, it) }
    }
}

internal suspend fun <T> persistBeforeProvider(
    persist: suspend () -> T,
    startProvider: suspend (T) -> Unit,
    onFailure: suspend (Throwable) -> Unit
) {
    val persisted = try {
        persist()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        onFailure(error)
        return
    }
    startProvider(persisted)
}

internal fun mergePersistedAssistantRow(
    currentRow: List<MessageV2>,
    selectedProfileUids: List<String>,
    persistedMessages: List<MessageV2>,
    chatId: Int
): List<MessageV2> {
    val currentByProfile = currentRow.associateBy { it.platformType }
    val persistedByProfile = persistedMessages.associateBy { it.platformType }
    return selectedProfileUids.map { profileUid ->
        persistedByProfile[profileUid]
            ?: currentByProfile[profileUid]
            ?: createEmptyAssistantMessage(chatId, profileUid)
    }
}

internal fun formatAssistantExport(
    platformName: String,
    message: MessageV2,
    toolEventsByRun: Map<String, List<ToolEvent>>,
    toolTraceLabels: ToolTraceLabels = ToolTraceLabels.Default,
    legacyOrderNotice: String = LEGACY_ORDER_NOTICE
): String = buildString {
    appendLine("**Assistant ($platformName):**")
    val trace = message.effectiveRunId()
        ?.let(toolEventsByRun::get)
        .orEmpty()
    val timeline = message.effectiveTimeline()
    val content = message.effectiveContent()
    val thoughts = message.effectiveThoughts()
    if (hasUnavailableAssistantOrder(timeline, content, thoughts, trace.isNotEmpty())) {
        appendLine("> $legacyOrderNotice")
        appendLine()
        thoughts.takeIf(String::isNotBlank)?.let {
            appendLine("<details><summary>Thinking (order unavailable)</summary>")
            appendLine()
            appendLine(it)
            appendLine()
            appendLine("</details>")
            appendLine()
        }
        content.takeIf(String::isNotBlank)?.let {
            appendLine(it)
            appendLine()
        }
        formatToolTraceMarkdown(trace, toolTraceLabels).takeIf { it.isNotBlank() }?.let {
            appendLine(it)
            appendLine()
        }
    } else if (timeline.isEmpty()) {
        appendLine(content)
        appendLine()
        formatToolTraceMarkdown(trace, toolTraceLabels).takeIf { it.isNotBlank() }?.let {
            appendLine(it)
            appendLine()
        }
    } else {
        val traceBySequence = trace.associateBy(ToolEvent::sequence)
        val renderedSequences = timeline.mapNotNull { it.toolSequence }.toSet()
        timeline.forEach { item ->
            when (item.type) {
                AssistantTimelineItemType.TEXT -> appendLine(item.content)

                AssistantTimelineItemType.THINKING -> {
                    appendLine("<details><summary>Thinking</summary>")
                    appendLine()
                    appendLine(item.content)
                    appendLine()
                    appendLine("</details>")
                }

                AssistantTimelineItemType.TOOL ->
                    item.toolSequence
                        ?.let(traceBySequence::get)
                        ?.let { appendLine(formatToolTraceMarkdown(listOf(it), toolTraceLabels)) }

                AssistantTimelineItemType.NOTICE -> appendLine("> ${item.content}")

                AssistantTimelineItemType.LEGACY_ORDER -> Unit
            }
            appendLine()
        }
        trace.filterNot { it.sequence in renderedSequences }
            .takeIf { it.isNotEmpty() }
            ?.let {
                appendLine(formatToolTraceMarkdown(it, toolTraceLabels))
                appendLine()
            }
    }
}

internal fun persistableMessages(groupedMessages: ChatViewModel.GroupedMessages): List<MessageV2> {
    val merged = groupedMessages.userMessages + groupedMessages.assistantMessages.flatten()
    return merged
        .filter {
            it.effectiveContent().isNotBlank() ||
                it.effectiveThoughts().isNotBlank() ||
                it.effectiveTimeline().isNotEmpty() ||
                it.attachments.isNotEmpty() ||
                it.currentRunId != null
        }
        .sortedBy { it.createdAt }
}

internal fun createEmptyAssistantMessage(chatId: Int, platformUid: String): MessageV2 = MessageV2(
    chatId = chatId,
    content = "",
    platformType = platformUid
)

internal fun createRetryAssistantMessage(
    currentMessage: MessageV2,
    chatId: Int,
    platformUid: String
): MessageV2 = createEmptyAssistantMessage(chatId, platformUid).copy(
    revisions = currentMessage.revisions
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
