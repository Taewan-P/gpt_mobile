package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryResult
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnResult
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.model.ChatMcpToolConfig
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    /** Isolated benchmark requests use only supplied fixtures, without chat memory or connected tools. */
    suspend fun openBenchmarkSession(
        platform: PlatformV2,
        turns: List<dev.chungjungsoo.gptmobile.data.context.ConversationTurn>,
        tools: List<dev.chungjungsoo.gptmobile.data.agent.AgentTool>,
        runId: String
    ): dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession = error("Benchmark sessions are unavailable")

    suspend fun runDelegationBenchmark(
        platform: PlatformV2,
        test: dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkCase,
        runId: String,
        settings: dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
    ): dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkSample = error("Delegation benchmarks are unavailable")

    suspend fun supportsBenchmarkTools(platform: PlatformV2): Boolean = false

    /** Validate setup before recording or sending any benchmark requests. */
    suspend fun validateBenchmarkProfile(platform: PlatformV2) = Unit

    suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        runId: String,
        chatToolConfig: ChatMcpToolConfig? = null
    ): Flow<ApiState>
    fun observeMessagesV2(chatId: Int): Flow<List<MessageV2>>
    fun observeMessageWindow(chatId: Int, turns: Int): Flow<List<MessageV2>> = observeMessagesV2(chatId)
    fun observeTurnCount(chatId: Int): Flow<Int> = kotlinx.coroutines.flow.flowOf(0)
    fun observeFavoriteAssistantMessages(): Flow<List<MessageV2>>
    fun searchFavoriteAssistantMessages(query: String): Flow<List<MessageV2>>
    suspend fun setMessageFavorite(messageId: Int, isFavorite: Boolean)
    fun observeAgentRuns(chatId: Int): Flow<List<AgentRun>>
    fun observeToolEvents(chatId: Int): Flow<List<ToolEvent>>
    suspend fun fetchChatListV2(): List<ChatRoomV2>
    suspend fun fetchArchivedChatListV2(): List<ChatRoomV2>
    suspend fun setChatArchived(chatId: Int, isArchived: Boolean)
    suspend fun setChatFavorite(chatId: Int, isFavorite: Boolean)
    suspend fun searchChatsV2(query: String): List<ChatRoomV2>
    suspend fun updateDraft(chatId: Int, draftText: String?, timestamp: Long?)
    suspend fun fetchMessagesV2(chatId: Int): List<MessageV2>
    suspend fun fetchChatPlatformModels(chatId: Int): Map<String, String>
    suspend fun saveChatPlatformModels(chatId: Int, models: Map<String, String>)
    suspend fun persistAgentTurn(request: PersistAgentTurnRequest): PersistAgentTurnResult
    suspend fun persistAgentRetry(request: PersistAgentRetryRequest): PersistAgentRetryResult
    suspend fun markAgentRunRunning(runId: String, startedAt: Long): Boolean
    suspend fun finishAgentRun(runId: String, status: String, completedAt: Long, terminalError: String?): Boolean
    suspend fun finishQueuedAgentRun(runId: String, status: String, completedAt: Long, terminalError: String?): Boolean
    suspend fun finishActiveAgentRun(runId: String, status: String, completedAt: Long, terminalError: String?): Boolean
    suspend fun updateAgentMessage(message: MessageV2)
    suspend fun interruptActiveAgentRuns(completedAt: Long): Int
    suspend fun bindGatewayJob(runId: String, jobId: String, baseUrl: String): Boolean
    suspend fun advanceGatewaySequence(runId: String, sequence: Int): Boolean
    suspend fun getRecoverableGatewayRuns(): List<AgentRun>
    suspend fun restoreGatewayAnswer(runId: String, jobId: String, content: String, completedAt: Long): Boolean = false
    fun generateDefaultChatTitle(messages: List<MessageV2>): String?
    suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String, isCustomized: Boolean = false)
    suspend fun updateChatPlatforms(chatRoom: ChatRoomV2, platformUids: List<String>): ChatRoomV2
    suspend fun generateAiTitle(userMessage: String, assistantMessage: String, platform: PlatformV2): String?
    suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>, chatPlatformModels: Map<String, String>): ChatRoomV2
    suspend fun duplicateChatV2(chatRoom: ChatRoomV2): ChatRoomV2
    suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>)
}
