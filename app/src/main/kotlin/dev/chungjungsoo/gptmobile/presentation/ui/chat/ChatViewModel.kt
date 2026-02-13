package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.tool.ToolResult
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.tool.ToolManager
import dev.chungjungsoo.gptmobile.util.getPlatformName
import dev.chungjungsoo.gptmobile.util.handleStates
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val toolManager: ToolManager
) : ViewModel() {
    sealed class LoadingState {
        data object Idle : LoadingState()
        data object Loading : LoadingState()
    }

    data class GroupedMessages(
        val userMessages: List<MessageV2> = listOf(),
        val assistantMessages: List<List<MessageV2>> = listOf()
    )

    data class McpToolEvent(
        val callId: String,
        val toolName: String,
        val request: String,
        val output: String = "",
        val status: ToolCallStatus = ToolCallStatus.REQUESTED,
        val isError: Boolean = false
    )

    enum class ToolCallStatus {
        REQUESTED,
        EXECUTING,
        COMPLETED,
        FAILED
    }

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

    // Enabled platforms list in app
    private val _enabledPlatformsInApp = MutableStateFlow(listOf<PlatformV2>())
    val enabledPlatformsInApp = _enabledPlatformsInApp.asStateFlow()

    // User input used for TextField
    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    // Selected files for current message
    private val _selectedFiles = MutableStateFlow(listOf<String>())
    val selectedFiles = _selectedFiles.asStateFlow()

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

    // MCP tool call events keyed by "<messageIndex>:<platformIndex>"
    private val _mcpToolEvents = MutableStateFlow<Map<String, List<McpToolEvent>>>(emptyMap())
    val mcpToolEvents = _mcpToolEvents.asStateFlow()

    // Used for passing user question to Edit User Message Dialog
    private val _editedQuestion = MutableStateFlow(MessageV2(chatId = chatRoomId, content = "", platformType = null))
    val editedQuestion = _editedQuestion.asStateFlow()

    // Used for text data to show in SelectText Bottom Sheet
    private val _selectedText = MutableStateFlow("")
    val selectedText = _selectedText.asStateFlow()

    // State for the message loading state (From the database)
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    init {
        Log.d("ViewModel", "$chatRoomId")
        Log.d("ViewModel", "$enabledPlatformsInChat")
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
        Log.d("Question: ", _question.value)
        MessageV2(
            chatId = chatRoomId,
            content = _question.value,
            files = _selectedFiles.value,
            platformType = null,
            createdAt = currentTimeStamp
        ).let { addMessage(it) }
        _question.update { "" }
        clearSelectedFiles()
        completeChat()
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

    fun openChatTitleDialog() = _isChatTitleDialogOpen.update { true }

    fun openEditQuestionDialog(question: MessageV2) {
        _editedQuestion.update { question }
        _isEditQuestionDialogOpen.update { true }
    }

    fun openSelectTextSheet(content: String) {
        _selectedText.update { content }
        _isSelectTextSheetOpen.update { true }
    }

    fun generateDefaultChatTitle(): String? = chatRepository.generateDefaultChatTitle(_groupedMessages.value.userMessages)

    fun retryChat(platformIndex: Int) {
        if (platformIndex >= enabledPlatformsInChat.size || platformIndex < 0) return
        val platform = _enabledPlatformsInApp.value.firstOrNull { it.uid == enabledPlatformsInChat[platformIndex] } ?: return
        _loadingStates.update { it.toMutableList().apply { this[platformIndex] = LoadingState.Loading } }
        _groupedMessages.update {
            val updatedAssistantMessages = it.assistantMessages.toMutableList()
            updatedAssistantMessages[it.assistantMessages.lastIndex] = updatedAssistantMessages[it.assistantMessages.lastIndex].toMutableList().apply {
                this[platformIndex] = MessageV2(chatId = chatRoomId, content = "", platformType = platform.uid)
            }
            it.copy(assistantMessages = updatedAssistantMessages)
        }

        val messageIndex = _groupedMessages.value.userMessages.lastIndex
        clearMcpToolEvents(messageIndex, platformIndex)

        viewModelScope.launch {
            chatRepository.completeChat(
                _groupedMessages.value.userMessages,
                _groupedMessages.value.assistantMessages,
                platform,
                tools = toolManager.getAllTools()
            ).handleStates(
                messageFlow = _groupedMessages,
                platformIdx = platformIndex,
                onLoadingComplete = {
                    _loadingStates.update { it.toMutableList().apply { this[platformIndex] = LoadingState.Idle } }
                },
                onToolState = { state ->
                    updateMcpToolEvents(messageIndex, platformIndex, state)
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

    fun updateQuestion(q: String) = _question.update { q }

    fun addSelectedFile(filePath: String) {
        _selectedFiles.update { currentFiles ->
            if (filePath !in currentFiles) {
                currentFiles + filePath
            } else {
                currentFiles
            }
        }
    }

    fun removeSelectedFile(filePath: String) {
        _selectedFiles.update { currentFiles ->
            currentFiles.filter { it != filePath }
        }
    }

    fun clearSelectedFiles() {
        _selectedFiles.update { emptyList() }
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
                    appendLine("**Assistant (${_enabledPlatformsInApp.value.getPlatformName(message.platformType!!)}):**")
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
        val allTools = toolManager.getAllTools()
        val mcpTools = toolManager.getMcpTools()
        val latestQuestion = _groupedMessages.value.userMessages.lastOrNull()?.content.orEmpty()
        val requestedMcp = requestsMcp(latestQuestion)

        // Send chat completion requests
        val messageIndex = _groupedMessages.value.userMessages.lastIndex
        enabledPlatformsInChat.forEachIndexed { idx, platformUid ->
            val platform = _enabledPlatformsInApp.value.firstOrNull { it.uid == platformUid } ?: return@forEachIndexed
            clearMcpToolEvents(messageIndex, idx)
            viewModelScope.launch {
                val responseFlow = if (requestedMcp && mcpTools.isEmpty()) {
                    Log.w(TAG, "MCP requested but no MCP tools available. question=$latestQuestion")
                    flowOf(ApiState.Error("No MCP tools are connected. Open Settings > MCP Servers and verify connection."))
                } else {
                    chatRepository.completeChat(
                        _groupedMessages.value.userMessages,
                        _groupedMessages.value.assistantMessages,
                        platform,
                        tools = allTools
                    )
                }

                responseFlow.handleStates(
                    messageFlow = _groupedMessages,
                    platformIdx = idx,
                    onLoadingComplete = {
                        _loadingStates.update { it.toMutableList().apply { this[idx] = LoadingState.Idle } }
                    },
                    onToolState = { state ->
                        updateMcpToolEvents(messageIndex, idx, state)
                    }
                )
            }
        }
    }

    fun getMcpToolEvents(messageIndex: Int, platformIndex: Int): List<McpToolEvent> =
        _mcpToolEvents.value[messagePlatformKey(messageIndex, platformIndex)].orEmpty()

    private fun clearMcpToolEvents(messageIndex: Int, platformIndex: Int) {
        val key = messagePlatformKey(messageIndex, platformIndex)
        _mcpToolEvents.update { current -> current - key }
    }

    private fun updateMcpToolEvents(messageIndex: Int, platformIndex: Int, apiState: ApiState) {
        val key = messagePlatformKey(messageIndex, platformIndex)
        val existing = _mcpToolEvents.value[key].orEmpty().toMutableList()

        when (apiState) {
            is ApiState.ToolCallRequested -> {
                apiState.toolCalls.forEach { toolCall ->
                    if (!toolManager.isMcpTool(toolCall.name)) {
                        return@forEach
                    }
                    val existingIndex = existing.indexOfFirst { it.callId == toolCall.id }
                    val event = McpToolEvent(
                        callId = toolCall.id,
                        toolName = toolCall.name,
                        request = toolCall.arguments.toString(),
                        status = ToolCallStatus.REQUESTED
                    )
                    if (existingIndex >= 0) {
                        existing[existingIndex] = event
                    } else {
                        existing.add(event)
                    }
                }
            }

            is ApiState.ToolExecuting -> {
                if (!toolManager.isMcpTool(apiState.toolName)) {
                    return
                }
                val targetIndex = existing.indexOfLast {
                    it.toolName == apiState.toolName &&
                        it.status != ToolCallStatus.COMPLETED &&
                        it.status != ToolCallStatus.FAILED
                }
                if (targetIndex >= 0) {
                    existing[targetIndex] = existing[targetIndex].copy(status = ToolCallStatus.EXECUTING)
                }
            }

            is ApiState.ToolResultReceived -> {
                applyToolResults(existing, apiState.results)
            }

            else -> Unit
        }

        _mcpToolEvents.update { current ->
            if (existing.isEmpty()) {
                current - key
            } else {
                current + (key to existing)
            }
        }
    }

    private fun applyToolResults(existing: MutableList<McpToolEvent>, results: List<ToolResult>) {
        results.forEach { result ->
            if (!toolManager.isMcpTool(result.name)) {
                return@forEach
            }
            val index = existing.indexOfFirst { it.callId == result.callId }
            val status = if (result.isError) ToolCallStatus.FAILED else ToolCallStatus.COMPLETED
            if (index >= 0) {
                existing[index] = existing[index].copy(
                    output = result.output,
                    status = status,
                    isError = result.isError
                )
            } else {
                existing.add(
                    McpToolEvent(
                        callId = result.callId,
                        toolName = result.name,
                        request = "{}",
                        output = result.output,
                        status = status,
                        isError = result.isError
                    )
                )
            }
        }
    }

    private fun messagePlatformKey(messageIndex: Int, platformIndex: Int): String =
        "$messageIndex:$platformIndex"

    private fun requestsMcp(content: String): Boolean {
        val normalized = content.lowercase()
        return normalized.contains("mcp") || normalized.contains("context7")
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
        val platformOrderMap = enabledPlatformsInChat.withIndex().associate { (idx, uuid) -> uuid to idx }

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

        val sortedAssistantMessages = assistantMessages.map { assistantMessage ->
            assistantMessage.sortedWith(
                compareBy(
                    { platformOrderMap[it.platformType] ?: Int.MAX_VALUE },
                    { it.platformType }
                )
            )
        }

        return GroupedMessages(userMessages, sortedAssistantMessages)
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
            Log.d("ViewModel", "chatroom: ${chatRoom.value}")
        }
    }

    private fun fetchEnabledPlatformsInApp() {
        viewModelScope.launch {
            val filtered = settingRepository.fetchPlatformV2s().filter { it.enabled }
            _enabledPlatformsInApp.update { filtered }
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
                    Log.d("ChatViewModel", "GroupMessage: ${_groupedMessages.value}")

                    // Save the chat & chat room
                    _chatRoom.update { chatRepository.saveChat(_chatRoom.value, ungroupedMessages()) }

                    // Sync message ids
                    fetchMessages()
                }
            }
        }
    }

    private fun ungroupedMessages(): List<MessageV2> {
        // Flatten the grouped messages into a single list
        val merged = _groupedMessages.value.userMessages + _groupedMessages.value.assistantMessages.flatten()
        return merged.filter { it.content.isNotBlank() }.sortedBy { it.createdAt }
    }

    private companion object {
        private const val TAG = "ChatViewModel"
    }
}







