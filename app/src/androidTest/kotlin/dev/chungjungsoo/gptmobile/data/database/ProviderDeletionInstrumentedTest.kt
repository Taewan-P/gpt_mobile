package dev.chungjungsoo.gptmobile.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ProviderConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDeletionInstrumentedTest {
    @Test
    fun deletingProviderRemovesProfilesAndSelectionsButKeepsHistory() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChatDatabaseV2::class.java).build()
        try {
            val connection = ProviderConnection(uid = "provider", name = "NVIDIA", compatibleType = dev.chungjungsoo.gptmobile.data.model.ClientType.NVIDIA)
            database.providerConnectionDao().upsert(connection)
            database.platformDao().addPlatform(PlatformV2(uid = "profile", name = "Assistant", providerConnectionUid = connection.uid))
            database.platformDao().addPlatform(PlatformV2(uid = "other", name = "Other"))
            val chatId = database.chatRoomDao().addChatRoom(ChatRoomV2(title = "History", enabledPlatform = listOf("profile"))).toInt()
            database.messageDao().addMessages(MessageV2(chatId = chatId, content = "Keep this response", platformType = "profile"))
            database.chatPlatformModelDao().upsertChatPlatformModel(ChatPlatformModelV2(chatId = chatId, platformUid = "profile", model = "example"))

            database.providerConnectionDao().deleteWithProfiles(connection)

            assertNull(database.providerConnectionDao().getConnection(connection.uid))
            assertEquals(listOf("other"), database.platformDao().getPlatforms().map { it.uid })
            assertTrue(database.chatPlatformModelDao().getByChatId(chatId).isEmpty())
            assertEquals("Keep this response", database.messageDao().loadMessages(chatId).single().content)
            assertEquals(listOf("profile"), database.chatRoomDao().getChatRooms().single().enabledPlatform)
        } finally {
            database.close()
        }
    }
}
