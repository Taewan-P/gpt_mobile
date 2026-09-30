package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
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
import dev.chungjungsoo.gptmobile.data.database.entity.CombinedModelResponse
import dev.chungjungsoo.gptmobile.data.database.entity.ConversationMode
import dev.chungjungsoo.gptmobile.data.database.entity.LEGACY_ORDER_NOTICE
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
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
import dev.chungjungsoo.gptmobile.data.model.AvailableChatTool
import dev.chungjungsoo.gptmobile.data.model.ChatMcpToolConfig
import dev.chungjungsoo.gptmobile.data.repository.AttachmentUploadCoordinator
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.presentation.StartupRecoveryGate
import dev.chungjungsoo.gptmobile.presentation.ui.setup.DownloadedLocalModelOption
import dev.chungjungsoo.gptmobile.presentation.ui.thinking.ThinkingParser
import dev.chungjungsoo.gptmobile.util.AttachmentPayloadCache
import dev.chungjungsoo.gptmobile.util.ChatToolUtils
import dev.chungjungsoo.gptmobile.util.FileUtils
import dev.chungjungsoo.gptmobile.util.buildAssistantErrorContent
import dev.chungjungsoo.gptmobile.util.determineLocalNetworkAccessRequirement
import dev.chungjungsoo.gptmobile.util.isAssistantErrorMessage
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val modelCatalogRepository: ModelCatalogRepository,
    private val freeModelToolConsentStore: dev.chungjungsoo.gptmobile.data.permissions.FreeModelToolConsentStore,
    private val conversationReadStateStore: dev.chungjungsoo.gptmobile.data.chat.ConversationReadStateStore,
    private val durablePromptQueue: dev.chungjungsoo.gptmobile.data.queue.DurablePromptQueue? = null,
    private val toolApprovals: dev.chungjungsoo.gptmobile.data.permissions.ToolApprovalManager? = null,
    private val mcpInteractions: dev.chungjungsoo.gptmobile.data.agent.tool.McpInteractions? = null,
    private val invocationLedger: dev.chungjungsoo.gptmobile.data.accounting.InvocationLedger? = null
) : ViewModel() {
    private val visibleHistoryTurns = MutableStateFlow(if (savedStateHandle.get<Int>("targetMessageId") != null) Int.MAX_VALUE else 40)
    private var windowStartId = 0
    val olderHistoryAvailable = MutableStateFlow(false)
    fun loadOlderMessages() {
        visibleHistoryTurns.update { (it.toLong() + 40).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    }
    private suspend fun completeWindowMessages(grouped: GroupedMessages): List<MessageV2> {
        val older = if (windowStartId > 0) chatRepository.fetchMessagesV2(_chatRoom.value.id).filter { it.id < windowStartId } else emptyList()
        return older + persistableMessages(grouped)
    }

    val pendingMcpInput get() = mcpInteractions?.pending ?: MutableStateFlow(emptyList())
    fun respondMcpInput(id: String, value: kotlinx.serialization.json.JsonObject?) {
        mcpInteractions?.respond(id, value)
    }
    val pendingToolApprovals get() = toolApprovals?.pending ?: flowOf(emptyList())
    data class FreeToolConsentRequest(
        val profileUid: String,
        val profileName: String,
        val toolId: String,
        val toolName: String
    )
    private val _pendingFreeToolConsent = MutableStateFlow<FreeToolConsentRequest?>(null)
    val pendingFreeToolConsent = _pendingFreeToolConsent.asStateFlow()
    fun decideToolApproval(id: String, allow: Boolean) {
        viewModelScope.launch { toolApprovals?.decide(id, allow) }
    }
    fun alwaysAllowTool(id: String) {
        viewModelScope.launch {
            try {
                toolApprovals?.alwaysAllow(id)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _attachmentNotice.value = "Could not save permission. You can still allow this action once."
            }
        }
    }

    fun alwaysAllowToolProvider(id: String) {
        viewModelScope.launch {
            try {
                toolApprovals?.alwaysAllowProvider(id)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _attachmentNotice.value = "Could not save provider permission. You can still allow this action once."
            }
        }
    }

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
    private val requestedConversationMode = ConversationMode.normalize(savedStateHandle["conversationMode"])
    private val initialPlatformUids = enabledPlatformString.split(',').filter(String::isNotBlank)
    private val platformSlotUids = MutableStateFlow(initialPlatformUids)
    val enabledPlatformsInChat: List<String>
        get() = platformSlotUids.value
    private val _activePlatformUids = MutableStateFlow(initialPlatformUids)
    val activePlatformUids = _activePlatformUids.asStateFlow()
    val targetMessageId: Int = savedStateHandle.get<Int>("targetMessageId") ?: -1

    private val currentTimeStamp: Long
        get() = System.currentTimeMillis() / 1000

    private val _chatRoom = MutableStateFlow(
        ChatRoomV2(
            id = -1,
            title = "",
            enabledPlatform = enabledPlatformsInChat,
            conversationMode = requestedConversationMode
        )
    )
    val chatRoom = _chatRoom.asStateFlow()

    private val _isChatTitleDialogOpen = MutableStateFlow(false)
    val isChatTitleDialogOpen = _isChatTitleDialogOpen.asStateFlow()

    private val _messageEditSession = MutableStateFlow<MessageEditSession?>(null)
    val messageEditSession = _messageEditSession.asStateFlow()

    private val _isSelectTextSheetOpen = MutableStateFlow(false)
    val isSelectTextSheetOpen = _isSelectTextSheetOpen.asStateFlow()

    private val _isChatModelDialogOpen = MutableStateFlow(false)
    val isChatModelDialogOpen = _isChatModelDialogOpen.asStateFlow()

    private val _isChatToolSheetOpen = MutableStateFlow(false)
    val isChatToolSheetOpen = _isChatToolSheetOpen.asStateFlow()

    private val _chatToolConfig = MutableStateFlow(ChatMcpToolConfig())
    val chatToolConfig = _chatToolConfig.asStateFlow()

    private val _availableChatTools = MutableStateFlow<List<AvailableChatTool>>(emptyList())
    val availableChatTools = _availableChatTools.asStateFlow()

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

    val invocationDiagnostics = (invocationLedger?.diagnostics ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debugMode: StateFlow<Boolean> = settingRepository.observeDebugMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
    val activeAgentRuns = agentRunCoordinator.activeRuns

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

    val featureSettings: StateFlow<dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings> =
        settingRepository.observeFeatureSettings()
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings()
            )

    private data class QueuedPrompt(val text: String, val attachments: List<ChatAttachmentDraft>)
    private val queuedPrompts = ArrayDeque<QueuedPrompt>()
    private val _pendingPrompts = MutableStateFlow<List<dev.chungjungsoo.gptmobile.data.queue.PendingPrompt>>(emptyList())
    val pendingPrompts = _pendingPrompts.asStateFlow()
    private var queueSubmissionPending = false
    private val _queuedPromptCount = MutableStateFlow(0)
    val queuedPromptCount = _queuedPromptCount.asStateFlow()
    private val _disabledPlatformUids = MutableStateFlow<Set<String>>(emptySet())
    val disabledPlatformUids = _disabledPlatformUids.asStateFlow()
    private var lastAutoTitleUserTurnCount = 0
    private val combinedSynthesisTurns = mutableSetOf<Int>()
    private var pendingRunDispatches = 0
    private val platformMembershipMutex = Mutex()

    init {
        fetchChatRoom()
        durablePromptQueue?.let { queue ->
            viewModelScope.launch {
                _chatRoom.collectLatest { room ->
                    if (room.id > 0) {
                        _disabledPlatformUids.value = queue.pausedProfiles(room.id)
                        queue.observe(room.id).collect { entries ->
                            _pendingPrompts.value = entries
                            _queuedPromptCount.value = entries.size
                        }
                    }
                }
            }
            queue.start()
        }
        viewModelScope.launch { fetchMessages() }
        fetchEnabledPlatformsInApp()
        observePersistedMessages()
        observeAgentRuns()
        observeToolEvents()
        observeAgentNotices()
        loadAvailableChatTools()
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
            // Do not accept a half-prepared submission or clear a newer composer draft.
            _attachmentNotice.value = "Preparing attachments. Your draft is kept until they are ready."
            return
        }

        submitOrQueueQuestion(questionText, _selectedAttachments.value)
    }

    private fun submitOrQueueQuestion(questionText: String, attachments: List<ChatAttachmentDraft>) {
        if (isGenerationBusy() || !hasUnpausedProfile() || _pendingPrompts.value.isNotEmpty()) {
            if (durablePromptQueue != null) {
                if (queueSubmissionPending) return
                val roomId = _chatRoom.value.id
                if (roomId == 0) {
                    _attachmentNotice.value = "The conversation is starting. Your draft is kept; send it again when ready."
                    return
                }
                queueSubmissionPending = true
                val snapshot = attachments.toList()
                viewModelScope.launch {
                    try {
                        durablePromptQueue.enqueue(
                            roomId,
                            questionText,
                            dev.chungjungsoo.gptmobile.data.queue.PendingPromptPayload(
                                snapshot.mapNotNull { it.attachment },
                                _activePlatformUids.value,
                                _chatPlatformModels.value,
                                _chatToolConfig.value
                            )
                        )
                        if (question.text.toString() == questionText) question.clearText()
                        _selectedAttachments.update { current -> current.filterNot { it in snapshot } }
                        _attachmentNotice.value = "Saved to queue. You can edit, reorder, pause or remove it."
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        _attachmentNotice.value = "Could not save the queued prompt. Your draft is kept."
                    } finally {
                        queueSubmissionPending = false
                    }
                }
                return
            }
            queuedPrompts.addLast(QueuedPrompt(questionText, attachments.toList()))
            _queuedPromptCount.value = queuedPrompts.size
            question.clearText()
            _selectedAttachments.value = emptyList()

            return
        }
        sendQuestion(questionText, attachments)
    }

    private fun isGenerationBusy(): Boolean =
        pendingRunDispatches > 0 ||
            _loadingStates.value.any { it != LoadingState.Idle } ||
            agentRunCoordinator.activeRuns.value.values.any { it.chatId == _chatRoom.value.id }

    private fun drainPromptQueueIfIdle() {
        if (durablePromptQueue != null) return
        if (isGenerationBusy() || queuedPrompts.isEmpty() || !hasUnpausedProfile()) return
        val next = queuedPrompts.removeFirst()
        _queuedPromptCount.value = queuedPrompts.size
        sendQuestion(next.text, next.attachments, clearComposer = false)
    }

    fun removeQueuedPrompt(id: String) {
        viewModelScope.launch { durablePromptQueue?.remove(id) }
    }
    fun editQueuedPrompt(id: String, text: String) {
        viewModelScope.launch { durablePromptQueue?.edit(id, text) }
    }
    fun pauseQueuedPrompt(id: String, paused: Boolean) {
        viewModelScope.launch { durablePromptQueue?.pause(id, paused) }
    }
    fun moveQueuedPrompt(id: String, otherId: String) {
        viewModelScope.launch { durablePromptQueue?.move(id, otherId) }
    }

    private fun hasUnpausedProfile(): Boolean = _activePlatformUids.value.any { it !in _disabledPlatformUids.value }

    fun togglePlatformDisabled(platformUid: String) {
        if (platformUid !in _activePlatformUids.value) return
        _disabledPlatformUids.update { disabled ->
            if (platformUid in disabled) {
                disabled - platformUid
            } else {
                disabled + platformUid
            }
        }
        durablePromptQueue?.setPausedProfiles(_chatRoom.value.id, _disabledPlatformUids.value)
        advancePendingGeneration()
    }

    fun setPlatformMembership(platformUid: String, active: Boolean) {
        // Reserve the change before launching: a queued turn must not dispatch with
        // membership that has not yet been persisted or given a stable response slot.
        pendingRunDispatches += 1
        viewModelScope.launch {
            try {
                platformMembershipMutex.withLock {
                    val current = _activePlatformUids.value
                    val platform = _platformsInApp.value.firstOrNull { it.uid == platformUid }
                    if (active && platform?.enabled != true) return@withLock
                    val updated = if (active) (current + platformUid).distinct() else current - platformUid
                    if (updated.isEmpty()) {
                        _attachmentNotice.value = "At least one AI profile must remain active."
                        return@withLock
                    }
                    if (updated == current) return@withLock

                    val updatedRoom = chatRepository.updateChatPlatforms(_chatRoom.value, updated)
                    _chatRoom.value = updatedRoom
                    applyChatPlatformState(updatedRoom)
                    if (active && platform != null && platformUid !in _chatPlatformModels.value) {
                        _chatPlatformModels.update { it + (platformUid to platform.model) }
                        if (updatedRoom.id > 0) {
                            chatRepository.saveChatPlatformModels(updatedRoom.id, _chatPlatformModels.value)
                        }
                    }
                    updateLocalNetworkRequirement(_platformsInApp.value)
                    loadAvailableChatTools()
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                _attachmentNotice.value = "Could not update AI profiles for this conversation."
            } finally {
                pendingRunDispatches -= 1
                advancePendingGeneration()
            }
        }
    }

    fun sendContinueResponse() {
        submitOrQueueQuestion("continue", emptyList())
    }

    fun sendPromptResponse(promptText: String) {
        if (promptText.isNotBlank()) {
            submitOrQueueQuestion(promptText.trim(), emptyList())
        }
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
    fun openChatModelDialog() {
        loadAvailableChatTools()
        _isChatModelDialogOpen.update { true }
    }

    suspend fun loadProfileModels(uid: String): List<dev.chungjungsoo.gptmobile.data.repository.ProfileModelOption> {
        val profile = settingRepository.fetchPlatformV2s().first { it.uid == uid }
        return dev.chungjungsoo.gptmobile.data.repository.ProfileModelCatalog().load(profile)
    }

    fun setDeviceLocationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val features = settingRepository.getFeatureSettings()
                val profiles = settingRepository.fetchPlatformV2s().filter { it.uid in _activePlatformUids.value }
                if (!features.deviceLocationTool || profiles.all { it.disableAllTools || it.disableLocalTools }) {
                    _attachmentNotice.value = "Enable device location in Advanced Settings and allow local tools for an AI profile."
                    return@launch
                }
                profiles.filterNot { it.disableAllTools || it.disableLocalTools }.forEach {
                    toolConnectionRepository.setBuiltInToolBinding(it.uid, dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool.DEVICE_LOCATION, true)
                }
            }
            setChatToolsEnabled(listOf(dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool.DEVICE_LOCATION), enabled)
            loadAvailableChatTools()
        }
    }

    fun openChatToolSheet() = _isChatToolSheetOpen.update { true }
    fun closeChatToolSheet() = _isChatToolSheetOpen.update { false }

    fun toggleChatTool(toolId: String) {
        val config = _chatToolConfig.value
        if (config.isToolEnabled(toolId)) {
            _chatToolConfig.update { it.withToolDisabled(toolId) }
            return
        }

        val available = _availableChatTools.value.firstOrNull { it.id == toolId }
        if (available?.source != "MCP") {
            _chatToolConfig.update { it.withToolEnabled(toolId) }
            return
        }

        viewModelScope.launch {
            val freeProfile = settingRepository.fetchPlatformV2s()
                .firstOrNull {
                    it.uid in _activePlatformUids.value &&
                        it.compatibleType == dev.chungjungsoo.gptmobile.data.model.ClientType.FREE &&
                        !it.disableAllTools &&
                        !freeModelToolConsentStore.isGranted(it.uid, toolId)
                }
            if (freeProfile == null) {
                _chatToolConfig.update { it.withToolEnabled(toolId) }
            } else {
                _pendingFreeToolConsent.value = FreeToolConsentRequest(
                    profileUid = freeProfile.uid,
                    profileName = freeProfile.name,
                    toolId = toolId,
                    toolName = available.name
                )
            }
        }
    }

    fun confirmFreeToolConsent() {
        val request = _pendingFreeToolConsent.value ?: return
        runCatching { freeModelToolConsentStore.grant(request.profileUid, request.toolId) }
            .onSuccess {
                _chatToolConfig.update { it.withToolEnabled(request.toolId) }
                _pendingFreeToolConsent.value = null
            }
            .onFailure {
                _attachmentNotice.value = "Could not save this tool permission."
            }
    }

    fun dismissFreeToolConsent() {
        _pendingFreeToolConsent.value = null
    }

    fun markCurrentChatViewed() {
        conversationReadStateStore.markViewed(chatRoom.value.id)
    }

    fun setChatToolsEnabled(toolIds: Collection<String>, enabled: Boolean) {
        if (toolIds.isEmpty()) return
        val ids = toolIds.toSet()
        _chatToolConfig.update { config ->
            if (enabled) {
                config.copy(
                    disabledToolIds = config.disabledToolIds - ids,
                    enabledToolIds = config.enabledToolIds + ids,
                    allToolsDisabled = false
                )
            } else {
                val disabled = config.disabledToolIds + ids
                config.copy(
                    disabledToolIds = disabled,
                    enabledToolIds = config.enabledToolIds - ids
                )
            }
        }
    }

    fun enableAllChatTools() {
        _chatToolConfig.update { config ->
            config.copy(
                disabledToolIds = emptySet(),
                enabledToolIds = _availableChatTools.value.map { it.id }.toSet(),
                allToolsDisabled = false
            )
        }
    }

    fun disableAllChatTools() {
        _chatToolConfig.update { config ->
            config.copy(
                disabledToolIds = _availableChatTools.value.map { it.id }.toSet(),
                enabledToolIds = emptySet(),
                allToolsDisabled = true
            )
        }
    }

    private fun loadAvailableChatTools() {
        viewModelScope.launch {
            val connections = toolConnectionRepository.getAllConnections()
            val profiles = settingRepository.fetchPlatformV2s().filter { it.uid in _activePlatformUids.value && !it.disableAllTools }
            val features = settingRepository.getFeatureSettings()
            val bindings = profiles.flatMap { toolConnectionRepository.listBindingsByProfile(it.uid) }
            val remoteProfiles = profiles.filterNot { it.disableRemoteTools }.mapTo(mutableSetOf()) { it.uid }
            val localProfiles = profiles.filterNot { it.disableLocalTools }.mapTo(mutableSetOf()) { it.uid }
            val boundConnectionIds = bindings.filter { it.profileUid in remoteProfiles }.mapNotNull { it.connectionUid }.toSet()
            val locationEnabled = features.deviceLocationTool &&
                bindings.any {
                    it.profileUid in localProfiles && it.toolName == dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool.DEVICE_LOCATION
                }
            val available = ChatToolUtils.buildAvailableChatTools(
                connections.filter {
                    it.connectionUid in boundConnectionIds && (it.type != ToolConnectionType.MCP || features.remoteMcpConnections)
                }
            ) + listOf(
                AvailableChatTool("web_search", "Web search", "All enabled web search engines", "Multi-engine", remoteProfiles.isNotEmpty()),
                AvailableChatTool("device_location", "Device location", "Phone GPS location", "Built-in", locationEnabled)
            )
            _availableChatTools.value = available

            // Free profiles may use every tool, but a new profile must explicitly
            // acknowledge external MCP data sharing once per tool before it is active.
            val freeProfiles = profiles.filter { it.compatibleType == dev.chungjungsoo.gptmobile.data.model.ClientType.FREE }
            if (freeProfiles.isNotEmpty()) {
                val lockedMcpIds = available
                    .filter { tool ->
                        tool.source == "MCP" &&
                            freeProfiles.any { profile ->
                                !freeModelToolConsentStore.isGranted(profile.uid, tool.id)
                            }
                    }
                    .mapTo(mutableSetOf()) { it.id }
                if (lockedMcpIds.isNotEmpty()) {
                    _chatToolConfig.update { config ->
                        config.copy(
                            disabledToolIds = config.disabledToolIds + lockedMcpIds,
                            enabledToolIds = config.enabledToolIds - lockedMcpIds
                        )
                    }
                }
            }
        }
    }

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

    fun updateChatCreativity(temperature: Float) {
        val clamped = temperature.coerceIn(0f, 2f)
        val targetUids = _activePlatformUids.value.toSet()
        _platformsInApp.update { platforms ->
            platforms.map { platform ->
                if (platform.uid in targetUids) platform.copy(temperature = clamped) else platform
            }
        }
        viewModelScope.launch {
            _platformsInApp.value
                .filter { it.uid in targetUids }
                .forEach { settingRepository.updatePlatformV2(it) }
        }
    }

    fun updateChatPlatformModels(models: Map<String, String>) {
        val sanitizedModels = models
            .filterKeys { it in _activePlatformUids.value }
            .mapValues { (_, model) -> model.trim() }

        _chatPlatformModels.update { it + sanitizedModels }

        if (_chatRoom.value.id > 0) {
            viewModelScope.launch {
                chatRepository.saveChatPlatformModels(_chatRoom.value.id, _chatPlatformModels.value)
            }
        }
    }

    fun retryChat(turnIndex: Int, platformIndex: Int) {
        if (enabledPlatformsInChat.getOrNull(platformIndex) in _disabledPlatformUids.value) return
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
                                assistantMessages = contextMessages.assistantMessages,
                                chatToolConfig = _chatToolConfig.value
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

    fun toggleMessageFavorite(turnIndex: Int, platformIndex: Int) {
        val message = _groupedMessages.value.assistantMessages
            .getOrNull(turnIndex)
            ?.getOrNull(platformIndex)
            ?: return

        val newFavorite = !message.isFavorite
        _groupedMessages.update {
            updateAssistantSlot(it, turnIndex, platformIndex) { assistantMessage ->
                assistantMessage.copy(isFavorite = newFavorite)
            }
        }
        if (message.id > 0) {
            viewModelScope.launch {
                chatRepository.setMessageFavorite(message.id, newFavorite)
            }
        }
    }

    fun updateChatTitle(title: String) {
        // Should be only used for changing chat title after the chatroom is created.
        if (_chatRoom.value.id > 0) {
            _chatRoom.update { it.copy(title = title, isTitleCustomized = true) }
            viewModelScope.launch {
                chatRepository.updateChatTitle(_chatRoom.value, title, isCustomized = true)
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

    suspend fun exportChat(
        toolTraceLabels: ToolTraceLabels = ToolTraceLabels.Default,
        legacyOrderNotice: String = LEGACY_ORDER_NOTICE
    ): Pair<String, String> {
        val exported = groupPersistedMessages(completeWindowMessages(_groupedMessages.value), enabledPlatformsInChat, _chatRoom.value.id)
        val platformNames = _platformsInApp.value.associate { it.uid to it.name }
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
            exported.userMessages.forEachIndexed { i, message ->
                appendLine("**User:**")
                appendLine(message.content)
                appendLine()

                exported.assistantMessages[i].forEach { message ->
                    val platformName = message.platformType?.let { platformNames[it] } ?: "Unknown"
                    append(formatAssistantExport(platformName, message, _toolEventsByRun.value, toolTraceLabels, legacyOrderNotice))
                }
            }
        }

        // Save the Markdown file
        val safeTitle = chatRoom.value.title.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80)
        val fileName = "export_${safeTitle}_${System.currentTimeMillis()}.md"
        return Pair(fileName, chatHistoryMarkdown)
    }

    private fun completeChat(persistSnapshotFirst: Boolean = false) {
        // Update all the platform loading states to Loading
        _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Loading } }
        val turnIndex = _groupedMessages.value.assistantMessages.lastIndex

        pendingRunDispatches += 1
        viewModelScope.launch {
            try {
                val disabled = _disabledPlatformUids.value
                val activeUids = _activePlatformUids.value.filterNot { it in disabled }.toSet()
                val platforms = resolveSelectedPlatforms(enabledPlatformsInChat, _platformsInApp.value)
                    .filter { it.value.uid in activeUids }
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
                                messages = completeWindowMessages(_groupedMessages.value),
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
                        _agentRunsById.update { it + persisted.runs.associateBy(AgentRun::runId) }
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
                                    assistantMessages = contextMessages.assistantMessages,
                                    chatToolConfig = _chatToolConfig.value
                                )
                            }
                        )
                    },
                    onFailure = { error ->
                        showPersistenceFailure(turnIndex, platforms.map { it.index }, error)
                    }
                )
            } finally {
                pendingRunDispatches -= 1
                syncLoadingStates(_agentRunsById.value)
                drainPromptQueueIfIdle()
            }
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
                    notice = "Unsupported attachment type. Use images, PDF, Word, Excel, PowerPoint, text, CSV, JSON, XML or Markdown files."
                )
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
                return@launch
            }

            val preparationResult = try {
                withContext(Dispatchers.IO) { attachmentUploadCoordinator.prepareLocalAttachment(context, filePath) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                rejectDraftAttachment(currentAttachments, updateAttachments, filePath, error.message ?: "Could not read this document.")
                return@launch
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
        }
    }

    private fun sendQuestion(
        questionText: String,
        attachments: List<ChatAttachmentDraft>,
        clearComposer: Boolean = true
    ) {
        MessageV2(
            chatId = chatRoomId,
            content = questionText,
            attachments = attachments.mapNotNull { it.attachment },
            platformType = null,
            createdAt = currentTimeStamp
        ).let { addMessage(it) }
        if (clearComposer) {
            question.clearText()
            clearSelectedFiles()
        }
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
            val room = if (chatRoomId == 0) {
                ChatRoomV2(
                    id = 0,
                    title = "Untitled Chat",
                    enabledPlatform = initialPlatformUids,
                    activePlatform = initialPlatformUids,
                    conversationMode = requestedConversationMode
                )
            } else {
                chatRepository.fetchChatListV2().first { it.id == chatRoomId }
            }
            _chatRoom.value = room
            applyChatPlatformState(room)
        }
    }

    private fun applyChatPlatformState(room: ChatRoomV2) {
        val slots = room.enabledPlatform.filter(String::isNotBlank).distinct()
        val active = room.activePlatform
            .filter(String::isNotBlank)
            .distinct()
            .ifEmpty { slots }
            .filter { it in slots }

        platformSlotUids.value = slots
        _activePlatformUids.value = active
        _disabledPlatformUids.update { disabled -> disabled.intersect(active.toSet()) }
        _loadingStates.update { current ->
            List(slots.size) { index -> current.getOrElse(index) { LoadingState.Idle } }
        }
        _groupedMessages.update { grouped ->
            grouped.copy(
                assistantMessages = grouped.assistantMessages.map { row ->
                    normalizeAssistantRow(row, slots, room.id.coerceAtLeast(chatRoomId))
                }
            )
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
        val selectedProfiles = platforms.filter { it.uid in _activePlatformUids.value }
        val providerNeedsAccess = selectedProfiles.any { requiresLocalNetworkAccess(it.apiUrl) }
        val requiresAccess = determineLocalNetworkAccessRequirement(
            providerNeedsAccess = providerNeedsAccess,
            toolNeedsAccess = {
                _activePlatformUids.value.any { profileUid ->
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
                        visibleHistoryTurns.flatMapLatest { turns ->
                            combine(chatRepository.observeMessageWindow(chatId, if (targetMessageId > 0) Int.MAX_VALUE else turns), agentRunCoordinator.streamMessages, chatRepository.observeTurnCount(chatId)) { messages, live, total ->
                                windowStartId = messages.minOfOrNull { it.id } ?: 0
                                olderHistoryAvailable.value = total > messages.count { it.platformType == null }
                                chatId to messages.map { message -> live[message.id]?.takeIf { it.currentRunId == message.currentRunId } ?: message }
                            }
                        }
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
                    advancePendingGeneration()
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
                    val runsById = runs.associateBy(AgentRun::runId)
                    _agentRunsById.update { runsById }
                    _runNoticesById.update { current ->
                        pruneTransientChatRunNotices(
                            current,
                            runsById = runsById,
                            activeRunIds = agentRunCoordinator.activeRuns.value.keys
                        )
                    }
                    advancePendingGeneration()
                }
        }
    }

    private fun maybeStartCombinedSynthesis(runsById: Map<String, AgentRun>) {
        val room = _chatRoom.value
        val activeCombinedUids = _activePlatformUids.value
            .filterNot { it in _disabledPlatformUids.value }
            .toSet()
        val slotUids = enabledPlatformsInChat
        val activeSlotIndexes = slotUids.mapIndexedNotNull { index, uid ->
            index.takeIf { uid in activeCombinedUids }
        }
        if (room.conversationMode != ConversationMode.COMBINED || activeSlotIndexes.size < 2) return

        val grouped = _groupedMessages.value
        grouped.userMessages.indices.forEach { turnIndex ->
            if (turnIndex in combinedSynthesisTurns) return@forEach
            val row = grouped.assistantMessages.getOrNull(turnIndex).orEmpty()
            val activeRow = activeSlotIndexes.mapNotNull { index ->
                row.getOrNull(index)?.let { message -> index to message }
            }
            if (activeRow.size < 2) return@forEach

            val runIds = activeRow.map { (_, message) -> message.currentRunId }
            if (runIds.any { it.isNullOrBlank() }) return@forEach
            if (runIds.any { it?.startsWith(COMBINED_RUN_PREFIX) == true }) {
                combinedSynthesisTurns += turnIndex
                return@forEach
            }

            val statuses = runIds.map { runId -> runsById[runId] ?: return@forEach }
            if (statuses.any { it.status == AgentRunStatus.QUEUED || it.status == AgentRunStatus.RUNNING }) {
                return@forEach
            }

            val sources = activeRow.mapNotNull { (index, message) ->
                val content = message.effectiveContent().trim()
                if (content.isBlank() || isAssistantErrorMessage(content)) return@mapNotNull null
                val uid = slotUids.getOrNull(index)
                    ?: message.platformType
                    ?: return@mapNotNull null
                val platform = _platformsInApp.value.firstOrNull { it.uid == uid }
                CombinedModelResponse(
                    platformUid = uid,
                    platformName = platform?.name ?: "AI " + (index + 1),
                    modelName = _chatPlatformModels.value[uid].orEmpty(),
                    content = content
                )
            }
            if (sources.isEmpty()) return@forEach

            combinedSynthesisTurns += turnIndex
            pendingRunDispatches += 1
            viewModelScope.launch {
                try {
                    startCombinedSynthesis(turnIndex, sources)
                } catch (error: CancellationException) {
                    combinedSynthesisTurns -= turnIndex
                    throw error
                } catch (error: Throwable) {
                    combinedSynthesisTurns -= turnIndex
                    _attachmentNotice.update {
                        error.message?.takeIf(String::isNotBlank)
                            ?: "Could not combine the model responses."
                    }
                } finally {
                    pendingRunDispatches -= 1
                    syncLoadingStates(_agentRunsById.value)
                    drainPromptQueueIfIdle()
                }
            }
        }
    }

    private suspend fun startCombinedSynthesis(
        turnIndex: Int,
        sources: List<CombinedModelResponse>
    ) {
        val grouped = _groupedMessages.value
        val userMessage = grouped.userMessages.getOrNull(turnIndex) ?: return
        val leadUid = sources.firstOrNull()?.platformUid ?: return
        val leadIndex = enabledPlatformsInChat.indexOf(leadUid).takeIf { it >= 0 } ?: return
        val leadMessage = grouped.assistantMessages.getOrNull(turnIndex)?.getOrNull(leadIndex) ?: return
        if (leadMessage.id <= 0) return

        val leadPlatform = _platformsInApp.value
            .firstOrNull { it.uid == leadUid && it.enabled }
            ?.let(::resolvePlatformModel)
            ?: return

        val synthesisRunId = COMBINED_RUN_PREFIX + UUID.randomUUID().toString()
        val run = AgentRunDraft(
            runId = synthesisRunId,
            profileUid = leadPlatform.uid,
            providerSnapshot = leadPlatform.compatibleType.name,
            modelSnapshot = leadPlatform.model,
            createdAt = currentTimeStamp
        )

        val persisted = chatRepository.persistAgentRetry(
            PersistAgentRetryRequest(
                userMessage = userMessage,
                assistantMessage = leadMessage.copy(combinedSources = sources),
                run = run
            )
        )

        _agentRunsById.update { it + (persisted.run.runId to persisted.run) }
        _groupedMessages.update { current ->
            updateAssistantSlot(current, turnIndex, leadIndex) { persisted.assistantMessage }
        }
        _loadingStates.update {
            List(enabledPlatformsInChat.size) { index ->
                if (index == leadIndex) LoadingState.Loading else LoadingState.Idle
            }
        }

        val prompt = buildCombinedSynthesisPrompt(userMessage.content, sources)
        val synthesisUserMessage = userMessage.copy(
            id = 0,
            content = prompt,
            attachments = emptyList(),
            createdAt = currentTimeStamp
        )

        agentRunCoordinator.start(
            listOf(
                AgentRunRequest(
                    runId = synthesisRunId,
                    chatId = userMessage.chatId,
                    assistantMessage = persisted.assistantMessage,
                    platform = leadPlatform,
                    userMessages = listOf(synthesisUserMessage),
                    assistantMessages = listOf(emptyList()),
                    chatToolConfig = _chatToolConfig.value.copy(allToolsDisabled = true)
                )
            )
        )
    }

    private fun buildCombinedSynthesisPrompt(
        originalQuestion: String,
        sources: List<CombinedModelResponse>
    ): String = buildString {
        appendLine("You are the lead AI in Combined Mode.")
        appendLine("Create the single final response to the user's original request by synthesizing the candidate model responses below.")
        appendLine("Use the strongest accurate details from all candidates, resolve disagreements carefully, remove duplication, and produce one coherent answer.")
        appendLine("Do not mention the multi-model process unless the user explicitly asks about it.")
        appendLine()
        appendLine("Original user request:")
        appendLine(originalQuestion)
        appendLine()
        sources.forEachIndexed { index, source ->
            append("Candidate ")
            append(index + 1)
            append(" — ")
            append(source.platformName)
            if (source.modelName.isNotBlank()) {
                append(" (")
                append(source.modelName)
                append(")")
            }
            appendLine(":")
            appendLine(source.content.take(MAX_COMBINED_SOURCE_CHARS))
            appendLine()
        }
    }

    private fun checkAndGenerateAiTitle(runsById: Map<String, AgentRun>) {
        val room = _chatRoom.value
        if (!featureSettings.value.automaticConversationTitles) return
        if (room.id <= 0 || room.isTitleCustomized) return

        val grouped = _groupedMessages.value
        val userTurnCount = grouped.userMessages.size
        if (userTurnCount <= 0) return

        val shouldGenerate = when {
            lastAutoTitleUserTurnCount == 0 -> userTurnCount >= 1
            else -> userTurnCount - lastAutoTitleUserTurnCount >= 3
        }
        if (!shouldGenerate) return

        val latestTurnIndex = userTurnCount - 1
        val latestAssistantMessages = grouped.assistantMessages.getOrNull(latestTurnIndex).orEmpty()
        if (latestAssistantMessages.isEmpty()) return

        val activeRunIds = agentRunCoordinator.activeRuns.value.keys
        val anyActive = latestAssistantMessages.any { msg ->
            val runId = msg.currentRunId
            if (runId == null) {
                false
            } else {
                runId in activeRunIds ||
                    runsById[runId]?.status == AgentRunStatus.RUNNING ||
                    runsById[runId]?.status == AgentRunStatus.QUEUED
            }
        }
        if (anyActive) return

        if (room.conversationMode == ConversationMode.COMBINED) {
            val leadRunId = latestAssistantMessages
                .firstOrNull { it.currentRunId?.startsWith(COMBINED_RUN_PREFIX) == true }
                ?.currentRunId
            if (leadRunId?.startsWith(COMBINED_RUN_PREFIX) != true ||
                runsById[leadRunId]?.status != AgentRunStatus.COMPLETED
            ) {
                return
            }
        }

        val contextStart = (userTurnCount - 3).coerceAtLeast(0)
        val userContext = grouped.userMessages
            .drop(contextStart)
            .joinToString("\n") { it.content.trim() }
            .takeIf(String::isNotBlank)
            ?: return
        val assistantContext = grouped.assistantMessages
            .drop(contextStart)
            .flatten()
            .map { it.effectiveContent().trim() }
            .filter(String::isNotBlank)
            .joinToString("\n")
            .takeIf(String::isNotBlank)
            ?: return

        lastAutoTitleUserTurnCount = userTurnCount
        viewModelScope.launch(Dispatchers.IO) {
            val activeUids = _activePlatformUids.value.toSet()
            val platform = _platformsInApp.value.firstOrNull { it.uid in activeUids && it.enabled }
                ?: _platformsInApp.value.firstOrNull()
                ?: return@launch

            val aiTitle = chatRepository.generateAiTitle(userContext, assistantContext, platform)
            if (!aiTitle.isNullOrBlank() && !_chatRoom.value.isTitleCustomized) {
                val cleaned = aiTitle
                    .replace('\n', ' ')
                    .trim()
                    .split(Regex("\\s+"))
                    .take(8)
                    .joinToString(" ")
                    .take(64)
                if (cleaned.isNotBlank()) {
                    _chatRoom.update { it.copy(title = cleaned) }
                    chatRepository.updateChatTitle(_chatRoom.value, cleaned, isCustomized = false)
                }
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
                advancePendingGeneration()
            }
        }
    }

    private fun advancePendingGeneration() {
        if (pendingRunDispatches > 0) return
        val runs = _agentRunsById.value
        syncLoadingStates(runs)
        if (isGenerationBusy()) return
        maybeStartCombinedSynthesis(runs)
        checkAndGenerateAiTitle(runs)
        drainPromptQueueIfIdle()
    }

    private fun syncLoadingStates(runs: List<AgentRun>) {
        syncLoadingStates(runs.associateBy(AgentRun::runId))
    }

    private fun syncLoadingStates(runsById: Map<String, AgentRun>) {
        if (pendingRunDispatches > 0) return
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
                    messages = completeWindowMessages(groupedMessages),
                    chatPlatformModels = _chatPlatformModels.value
                )
            }
        }
    }
    companion object {
        internal const val COMBINED_RUN_PREFIX = "combined-synthesis:"
        private const val MAX_COMBINED_SOURCE_CHARS = 24_000
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

@JvmName("pruneTransientChatRunNoticesByStatus")
internal fun pruneTransientChatRunNotices(
    noticesByRunId: Map<String, List<ChatRunNotice>>,
    runStatuses: Map<String, String>,
    activeRunIds: Set<String>
): Map<String, List<ChatRunNotice>> = pruneTransientChatRunNoticesWithStatusLookup(
    noticesByRunId = noticesByRunId,
    activeRunIds = activeRunIds,
    getStatus = { runStatuses[it] }
)

internal fun pruneTransientChatRunNotices(
    noticesByRunId: Map<String, List<ChatRunNotice>>,
    runsById: Map<String, AgentRun>,
    activeRunIds: Set<String>
): Map<String, List<ChatRunNotice>> = pruneTransientChatRunNoticesWithStatusLookup(
    noticesByRunId = noticesByRunId,
    activeRunIds = activeRunIds,
    getStatus = { runsById[it]?.status }
)

private inline fun pruneTransientChatRunNoticesWithStatusLookup(
    noticesByRunId: Map<String, List<ChatRunNotice>>,
    activeRunIds: Set<String>,
    getStatus: (String) -> String?
): Map<String, List<ChatRunNotice>> = noticesByRunId.mapValues { (runId, notices) ->
    val status = getStatus(runId)
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

private fun isPersistableMessage(message: MessageV2): Boolean =
    message.effectiveContent().isNotBlank() ||
        message.effectiveThoughts().isNotBlank() ||
        message.effectiveTimeline().isNotEmpty() ||
        message.attachments.isNotEmpty() ||
        message.currentRunId != null

internal fun persistableMessages(groupedMessages: ChatViewModel.GroupedMessages): List<MessageV2> {
    val estimatedSize = groupedMessages.userMessages.size + (groupedMessages.assistantMessages.size * 2)
    val result = ArrayList<MessageV2>(estimatedSize)
    for (msg in groupedMessages.userMessages) {
        if (isPersistableMessage(msg)) {
            result.add(msg)
        }
    }
    for (row in groupedMessages.assistantMessages) {
        for (msg in row) {
            if (isPersistableMessage(msg)) {
                result.add(msg)
            }
        }
    }
    result.sortBy { it.createdAt }
    return result
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

    val currentTurnMessages = groupedMessages.assistantMessages[turnIndex].toMutableList()
    if (platformIndex !in currentTurnMessages.indices) return groupedMessages

    currentTurnMessages[platformIndex] = transform(currentTurnMessages[platformIndex])

    val updatedAssistantMessages = groupedMessages.assistantMessages.toMutableList()
    updatedAssistantMessages[turnIndex] = currentTurnMessages

    return groupedMessages.copy(assistantMessages = updatedAssistantMessages)
}

internal fun shouldShowReplyLoadingIndicator(
    isActiveMessage: Boolean,
    loadingStates: List<ChatViewModel.LoadingState>
): Boolean = isActiveMessage && loadingStates.any { it == ChatViewModel.LoadingState.Loading }

internal fun hasAssistantProcessDetails(
    timeline: List<AssistantTimelineItem>,
    fallbackThoughts: String,
    hasToolEvents: Boolean
): Boolean {
    val hasTimelineDetails = timeline.any { item ->
        when (item.type) {
            AssistantTimelineItemType.THINKING -> !item.content.isNullOrBlank()
            AssistantTimelineItemType.TOOL -> true
            AssistantTimelineItemType.TEXT -> {
                val parsed = ThinkingParser.extractThinking(item.content.orEmpty())
                !parsed.thinking.isNullOrBlank()
            }
            AssistantTimelineItemType.NOTICE,
            AssistantTimelineItemType.LEGACY_ORDER -> false
        }
    }
    return hasTimelineDetails || fallbackThoughts.isNotBlank() || hasToolEvents
}
