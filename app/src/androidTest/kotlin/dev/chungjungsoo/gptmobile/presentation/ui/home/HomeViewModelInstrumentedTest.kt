package dev.chungjungsoo.gptmobile.presentation.ui.home

import androidx.test.platform.app.InstrumentationRegistry
import dev.chungjungsoo.gptmobile.data.agent.AgentRunCoordinator
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryResult
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnResult
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HomeViewModelInstrumentedTest {
    @Test
    fun failedChatLoadCanRetryWithoutLosingTheScreen() = runBlocking {
        val repository = QueueChatRepository(
            ArrayDeque(
                listOf(
                    Result.failure(IOException("offline")),
                    Result.success(emptyList())
                )
            )
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = HomeViewModel(
            chatRepository = repository,
            settingRepository = EmptySettingRepository(),
            agentRunCoordinator = AgentRunCoordinator(context, repository)
        )

        viewModel.fetchChats()
        val failed = withTimeout(5_000) {
            viewModel.chatListState.first { it.loadError == "offline" }
        }
        assertEquals("offline", failed.loadError)
        assertFalse(failed.isLoading)

        viewModel.retryFetchChats()
        val ready = withTimeout(5_000) {
            viewModel.chatListState.first { !it.isLoading && it.loadError == null }
        }
        assertNull(ready.loadError)
        assertFalse(ready.isLoading)
    }

    @Test
    fun successfulSearchClearsStaleLoadError() = runBlocking {
        val chats = listOf(ChatRoomV2(id = 1, title = "Saved chat", enabledPlatform = listOf("platform-1")))
        val repository = QueueChatRepository(
            responses = ArrayDeque(listOf(Result.failure(IOException("offline")))),
            searchResults = ArrayDeque(listOf(chats))
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = HomeViewModel(
            chatRepository = repository,
            settingRepository = EmptySettingRepository(),
            agentRunCoordinator = AgentRunCoordinator(context, repository)
        )

        viewModel.fetchChats()
        withTimeout(5_000) {
            viewModel.chatListState.first { it.loadError == "offline" }
        }

        viewModel.enableSearchMode()
        viewModel.updateSearchQuery("Saved")
        val searched = withTimeout(5_000) {
            viewModel.chatListState.first { it.loadError == null && it.chats == chats }
        }
        assertNull(searched.loadError)
        assertEquals(chats, searched.chats)
    }

    @Test
    fun populatedRefreshRetainsExistingChats() = runBlocking {
        val chats = listOf(ChatRoomV2(id = 1, title = "Saved chat", enabledPlatform = listOf("platform-1")))
        val secondFetch = CompletableDeferred<Unit>()
        val repository = QueueChatRepository(
            responses = ArrayDeque(
                listOf(
                    Result.success(chats),
                    Result.success(chats)
                )
            ),
            fetchGate = secondFetch
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = HomeViewModel(
            chatRepository = repository,
            settingRepository = EmptySettingRepository(),
            agentRunCoordinator = AgentRunCoordinator(context, repository)
        )

        viewModel.fetchChats()
        withTimeout(5_000) {
            viewModel.chatListState.first { !it.isLoading && it.chats == chats }
        }

        viewModel.fetchChats()
        withTimeout(5_000) { repository.fetchStarted.await() }
        val refreshing = viewModel.chatListState.value
        assertFalse(refreshing.isLoading)
        assertEquals(chats, refreshing.chats)
        assertNull(refreshing.loadError)
        secondFetch.complete(Unit)
        Unit
    }
}

private class QueueChatRepository(
    private val responses: ArrayDeque<Result<List<ChatRoomV2>>>,
    private val searchResults: ArrayDeque<List<ChatRoomV2>> = ArrayDeque(),
    private val fetchGate: CompletableDeferred<Unit>? = null,
    val fetchStarted: CompletableDeferred<Unit> = CompletableDeferred()
) : ChatRepository {
    override suspend fun fetchChatListV2(): List<ChatRoomV2> {
        val remainingBefore = responses.size
        val result = responses.removeFirst()
        if (fetchGate != null && remainingBefore == 1) {
            fetchStarted.complete(Unit)
            fetchGate.await()
        }
        return result.getOrThrow()
    }
    override suspend fun completeChat(userMessages: List<MessageV2>, assistantMessages: List<List<MessageV2>>, platform: PlatformV2, runId: String): Flow<ApiState> = error("unused")
    override fun observeMessagesV2(chatId: Int): Flow<List<MessageV2>> = error("unused")
    override fun observeAgentRuns(chatId: Int): Flow<List<AgentRun>> = error("unused")
    override fun observeToolEvents(chatId: Int): Flow<List<ToolEvent>> = error("unused")
    override suspend fun fetchChatList(): List<ChatRoom> = error("unused")
    override suspend fun searchChatsV2(query: String): List<ChatRoomV2> = searchResults.removeFirst()
    override suspend fun fetchMessages(chatId: Int): List<Message> = error("unused")
    override suspend fun fetchMessagesV2(chatId: Int): List<MessageV2> = error("unused")
    override suspend fun fetchChatPlatformModels(chatId: Int): Map<String, String> = error("unused")
    override suspend fun saveChatPlatformModels(chatId: Int, models: Map<String, String>) = error("unused")
    override suspend fun persistAgentTurn(request: PersistAgentTurnRequest): PersistAgentTurnResult = error("unused")
    override suspend fun persistAgentRetry(request: PersistAgentRetryRequest): PersistAgentRetryResult = error("unused")
    override suspend fun markAgentRunRunning(runId: String, startedAt: Long): Boolean = error("unused")
    override suspend fun finishAgentRun(runId: String, status: String, completedAt: Long, terminalError: String?): Boolean = error("unused")
    override suspend fun finishQueuedAgentRun(runId: String, status: String, completedAt: Long, terminalError: String?): Boolean = error("unused")
    override suspend fun finishActiveAgentRun(runId: String, status: String, completedAt: Long, terminalError: String?): Boolean = error("unused")
    override suspend fun updateAgentMessage(message: MessageV2) = error("unused")
    override suspend fun interruptActiveAgentRuns(completedAt: Long): Int = error("unused")
    override suspend fun migrateToChatRoomV2MessageV2() = error("unused")
    override fun generateDefaultChatTitle(messages: List<MessageV2>): String? = error("unused")
    override suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String) = error("unused")
    override suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>, chatPlatformModels: Map<String, String>): ChatRoomV2 = error("unused")
    override suspend fun duplicateChatV2(chatRoom: ChatRoomV2): ChatRoomV2 = error("unused")
    override suspend fun deleteChats(chatRooms: List<ChatRoom>) = error("unused")
    override suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>) = error("unused")
}

private class EmptySettingRepository : SettingRepository {
    override suspend fun fetchPlatformV2s(): List<PlatformV2> = emptyList()
    override suspend fun fetchPlatforms(): List<Platform> = error("unused")
    override suspend fun fetchThemes(): ThemeSetting = error("unused")
    override suspend fun migrateToPlatformV2() = error("unused")
    override suspend fun migrateSecrets(): List<SecretMigrationError> = error("unused")
    override suspend fun updatePlatforms(platforms: List<Platform>) = error("unused")
    override suspend fun updateThemes(themeSetting: ThemeSetting) = error("unused")
    override suspend fun addPlatformV2(platform: PlatformV2) = error("unused")
    override suspend fun updatePlatformV2(platform: PlatformV2) = error("unused")
    override suspend fun deletePlatformV2(platform: PlatformV2) = error("unused")
    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = error("unused")
}
