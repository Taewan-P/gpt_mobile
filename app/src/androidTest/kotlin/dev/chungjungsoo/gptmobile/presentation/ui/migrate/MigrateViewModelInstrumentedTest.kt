package dev.chungjungsoo.gptmobile.presentation.ui.migrate

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
import org.junit.Assert.assertNull
import org.junit.Test

class MigrateViewModelInstrumentedTest {
    @Test
    fun platformFailureRetriesWithoutUnblockingChatEarly() = runBlocking {
        val retryGate = CompletableDeferred<Unit>()
        val settings = MigrationSettingRepository(
            ArrayDeque(
                listOf(
                    suspend { throw IOException("platform offline") },
                    suspend { retryGate.await() }
                )
            )
        )
        val viewModel = MigrateViewModel(settings, MigrationChatRepository())

        viewModel.migratePlatform()
        val failed = state(viewModel) { it.platformState == MigrateViewModel.MigrationState.ERROR }
        assertEquals("platform offline", failed.platformErrorMessage)
        assertEquals(MigrateViewModel.MigrationState.BLOCKED, failed.chatState)

        viewModel.migratePlatform()
        val migrating = state(viewModel) { it.platformState == MigrateViewModel.MigrationState.MIGRATING }
        assertNull(migrating.platformErrorMessage)
        assertEquals(MigrateViewModel.MigrationState.BLOCKED, migrating.chatState)

        retryGate.complete(Unit)
        val migrated = state(viewModel) { it.platformState == MigrateViewModel.MigrationState.MIGRATED }
        assertEquals(MigrateViewModel.MigrationState.READY, migrated.chatState)
    }

    @Test
    fun chatFailureRetriesWithoutChangingMigratedPlatform() = runBlocking {
        val retryGate = CompletableDeferred<Unit>()
        val chats = MigrationChatRepository(
            ArrayDeque(
                listOf(
                    suspend { throw IOException("chat offline") },
                    suspend { retryGate.await() }
                )
            )
        )
        val viewModel = MigrateViewModel(MigrationSettingRepository(), chats)

        viewModel.migratePlatform()
        state(viewModel) { it.chatState == MigrateViewModel.MigrationState.READY }
        viewModel.migrateChats()
        val failed = state(viewModel) { it.chatState == MigrateViewModel.MigrationState.ERROR }
        assertEquals("chat offline", failed.chatErrorMessage)
        assertEquals(MigrateViewModel.MigrationState.MIGRATED, failed.platformState)

        viewModel.migrateChats()
        val migrating = state(viewModel) { it.chatState == MigrateViewModel.MigrationState.MIGRATING }
        assertNull(migrating.chatErrorMessage)
        assertEquals(MigrateViewModel.MigrationState.MIGRATED, migrating.platformState)

        retryGate.complete(Unit)
        state(viewModel) { it.chatState == MigrateViewModel.MigrationState.MIGRATED }
        Unit
    }

    private suspend fun state(
        viewModel: MigrateViewModel,
        predicate: (MigrateViewModel.MigrationUIState) -> Boolean
    ): MigrateViewModel.MigrationUIState = withTimeout(5_000) {
        viewModel.uiState.first(predicate)
    }
}

private class MigrationSettingRepository(
    private val migrations: ArrayDeque<suspend () -> Unit> = ArrayDeque(listOf(suspend { }))
) : SettingRepository {
    override suspend fun fetchPlatforms(): List<Platform> = emptyList()
    override suspend fun migrateToPlatformV2() = migrations.removeFirst().invoke()
    override suspend fun fetchPlatformV2s(): List<PlatformV2> = error("unused")
    override suspend fun fetchThemes(): ThemeSetting = error("unused")
    override suspend fun migrateSecrets(): List<SecretMigrationError> = error("unused")
    override suspend fun updatePlatforms(platforms: List<Platform>) = error("unused")
    override suspend fun updateThemes(themeSetting: ThemeSetting) = error("unused")
    override suspend fun addPlatformV2(platform: PlatformV2) = error("unused")
    override suspend fun updatePlatformV2(platform: PlatformV2) = error("unused")
    override suspend fun deletePlatformV2(platform: PlatformV2) = error("unused")
    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = error("unused")
}

private class MigrationChatRepository(
    private val migrations: ArrayDeque<suspend () -> Unit> = ArrayDeque(listOf(suspend { }))
) : ChatRepository {
    override suspend fun fetchChatList(): List<ChatRoom> = emptyList()
    override suspend fun migrateToChatRoomV2MessageV2() = migrations.removeFirst().invoke()
    override suspend fun completeChat(userMessages: List<MessageV2>, assistantMessages: List<List<MessageV2>>, platform: PlatformV2, runId: String): Flow<ApiState> = error("unused")
    override fun observeMessagesV2(chatId: Int): Flow<List<MessageV2>> = error("unused")
    override fun observeAgentRuns(chatId: Int): Flow<List<AgentRun>> = error("unused")
    override fun observeToolEvents(chatId: Int): Flow<List<ToolEvent>> = error("unused")
    override suspend fun fetchChatListV2(): List<ChatRoomV2> = error("unused")
    override suspend fun searchChatsV2(query: String): List<ChatRoomV2> = error("unused")
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
    override fun generateDefaultChatTitle(messages: List<MessageV2>): String? = error("unused")
    override suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String) = error("unused")
    override suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>, chatPlatformModels: Map<String, String>): ChatRoomV2 = error("unused")
    override suspend fun duplicateChatV2(chatRoom: ChatRoomV2): ChatRoomV2 = error("unused")
    override suspend fun deleteChats(chatRooms: List<ChatRoom>) = error("unused")
    override suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>) = error("unused")
}
