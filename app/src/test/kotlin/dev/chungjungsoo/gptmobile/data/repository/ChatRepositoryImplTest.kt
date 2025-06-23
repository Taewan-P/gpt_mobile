package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import com.aallam.openai.client.OpenAI
import com.google.ai.client.generativeai.GenerativeModel
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.spy
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

// For verifying OpenAI client constructor calls, we'd ideally need a way to inject a factory or use a more advanced mocking tool.
// For simplicity, this test will focus on the *instance identity* from the getOpenAIClient/getGoogleClient methods,
// by making them visible for testing or by verifying interactions with the *returned* client.
// A more robust test would involve PowerMockito to mock constructors or a DI framework to provide test doubles.

// Let's assume ChatRepositoryImpl can be modified slightly for testability if needed,
// or we use reflection/spy to check internal state (less ideal but possible).
// For this example, I'll assume we can spy on the repository or that the client instances
// are somehow verifiable.

@RunWith(MockitoJUnitRunner::class)
class ChatRepositoryImplTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockChatRoomDao: ChatRoomDao

    @Mock
    private lateinit var mockMessageDao: MessageDao

    @Mock
    private lateinit var mockSettingRepository: SettingRepository

    @Mock
    private lateinit var mockAnthropicAPI: AnthropicAPI

    // We will spy on the actual repository to check client instances if possible,
    // or we'd need to refactor getOpenAIClient and getGoogleClient to be injectable/mockable.
    // For now, let's assume we can verify the *behavior* that results from caching.
    // A simple way is to use reflection to access the private client maps, but that's not ideal for unit tests.

    // Let's try a slightly different approach: we can't directly mock the constructors of OpenAI/GenerativeModel easily without PowerMock.
    // Instead, we can verify that when we call the repository methods, the *same instance* of the client is used
    // if the configuration is the same. This requires the getOpenAIClient and getGoogleClient methods to be structured
    // such that they return the actual client instance, which they do.

    private lateinit var chatRepository: ChatRepositoryImpl

    // Dummy message for testing
    private val testMessage = dev.chungjungsoo.gptmobile.data.database.entity.Message(
        id = 1,
        chatId = 1,
        content = "Hello",
        platformType = null
    )
    private val testHistory = emptyList<dev.chungjungsoo.gptmobile.data.database.entity.Message>()


    @Before
    fun setUp() {
        // chatRepository = ChatRepositoryImpl(mockContext, mockChatRoomDao, mockMessageDao, mockSettingRepository, mockAnthropicAPI)
        // To test caching effectively, we need to observe the created clients.
        // One way is to make the client maps in ChatRepositoryImpl internal or package-private for testing,
        // or use a spy. Let's proceed as if we can inspect them via a helper or by making them visible for tests.

        // For this example, we'll assume we can't easily inspect the private maps without reflection
        // or changing visibility. So, the tests will be more behavioral, focusing on whether the
        // *intended effect* of caching (e.g., not re-fetching settings if client is cached) happens,
        // or by verifying the number of times certain underlying SDK methods are called if we could mock them.

        // Let's simplify and assume we can make the getOpenAIClient and getGoogleClient methods
        // *protected* or *internal* so they can be spied on or overridden in a test subclass.
        // Or, we can spy the whole ChatRepositoryImpl instance.
        chatRepository = spy(ChatRepositoryImpl(mockContext, mockChatRoomDao, mockMessageDao, mockSettingRepository, mockAnthropicAPI))
    }

    @Test
    fun `completeOpenAIChat reuses OpenAI client for same config`() = runTest {
        val platformConfig1 = Platform(ApiType.OPENAI, "token1", "http://url1.com/v1", "model1", true, null, null, null)
        `when`(mockSettingRepository.fetchPlatforms()).thenReturn(listOf(platformConfig1))

        // Mock the actual client's behavior to avoid real network calls
        val mockOpenAIClientInstance1 = org.mockito.kotlin.mock<OpenAI> {
            on { chatCompletions(any(), any()) } doReturn flowOf() // Return an empty flow
        }
        val spiedRepo = spy(chatRepository)
        // This is tricky without modifying the original class for testability or using PowerMock for constructors
        // Let's assume getOpenAIClient is accessible for verification or we check a side effect.

        // Alternative: We can't easily verify constructor calls without PowerMock.
        // We can, however, ensure that `SettingRepository.fetchPlatforms()` is called
        // and then if we had a way to get the client instance, check its identity.

        // For this test, let's assume we can use a helper to get the cached client or verify instance.
        // Since we can't directly, we'll focus on the *principle*.
        // If ChatRepositoryImpl's getOpenAIClient was public/internal, we could do:
        // val client1 = chatRepository.getOpenAIClient("token1", "http://url1.com/v1")
        // val client2 = chatRepository.getOpenAIClient("token1", "http://url1.com/v1")
        // assertEquals(client1, client2)

        // Given the current structure, we'll test by calling the public API
        // and verifying that `SettingRepository.fetchPlatforms()` is called,
        // which is a prerequisite for client creation/retrieval.
        // A more direct test of caching needs more testability in ChatRepositoryImpl.

        // Call 1
        spiedRepo.completeOpenAIChat(testMessage, testHistory).collect {}
        // Call 2 with same config
        spiedRepo.completeOpenAIChat(testMessage, testHistory).collect {}

        // This doesn't directly test caching of the OpenAI object itself without deeper changes/tools.
        // However, it sets up the structure.
        // To truly test caching, one would spy on the `openAIClients.getOrPut` call or check map size/contents.
        // For now, we verify that the settings are fetched, which is part of the client acquisition logic.
        verify(mockSettingRepository, times(2)).fetchPlatforms() // Settings are fetched each time currently. Caching settings is a separate improvement.
                                                                 // The client *inside* should be cached.

        // A better test would be:
        // 1. Make `getOpenAIClient` internal or use reflection to access `openAIClients` map.
        // 2. Call `completeOpenAIChat` twice.
        // 3. Assert that the size of `openAIClients` map is 1.
        // This is what I'm aiming for in principle.
        // Let's write it as if `getOpenAIClient` was testable:

        val repo = ChatRepositoryImpl(mockContext, mockChatRoomDao, mockMessageDao, mockSettingRepository, mockAnthropicAPI)
        // First call
        `when`(mockSettingRepository.fetchPlatforms()).thenReturn(listOf(platformConfig1))
        repo.completeOpenAIChat(testMessage, testHistory).collect {}
        val client1Ref = repo.javaClass.getDeclaredField("openAIClients").let {
            it.isAccessible = true
            (it.get(repo) as Map<*, *>).values.firstOrNull()
        }
        assertNotNull(client1Ref)

        // Second call with same config
        `when`(mockSettingRepository.fetchPlatforms()).thenReturn(listOf(platformConfig1))
        repo.completeOpenAIChat(testMessage, testHistory).collect {}
        val client2Ref = repo.javaClass.getDeclaredField("openAIClients").let {
            it.isAccessible = true
            (it.get(repo) as Map<*, *>).values.firstOrNull()
        }
        assertEquals(client1Ref, client2Ref, "OpenAI client should be reused for the same config")

        val clientMapSize = repo.javaClass.getDeclaredField("openAIClients").let {
            it.isAccessible = true
            (it.get(repo) as Map<*, *>).size
        }
        assertEquals(1, clientMapSize, "OpenAI client map should only contain one client for the same config")
    }

    @Test
    fun `completeOpenAIChat creates new OpenAI client for different config`() = runTest {
         val repo = ChatRepositoryImpl(mockContext, mockChatRoomDao, mockMessageDao, mockSettingRepository, mockAnthropicAPI)
        val platformConfig1 = Platform(ApiType.OPENAI, "token1", "http://url1.com/v1", "model1", true, null, null, null)
        val platformConfig2 = Platform(ApiType.OPENAI, "token2", "http://url2.com/v1", "model1", true, null, null, null) // Different token

        // Call 1
        `when`(mockSettingRepository.fetchPlatforms()).thenReturn(listOf(platformConfig1))
        repo.completeOpenAIChat(testMessage, testHistory).collect {}
        val client1Ref = repo.javaClass.getDeclaredField("openAIClients").let {
            it.isAccessible = true
            (it.get(repo) as Map<*, *>).values.firstOrNull()
        }
        assertNotNull(client1Ref)

        // Call 2 with different config
        `when`(mockSettingRepository.fetchPlatforms()).thenReturn(listOf(platformConfig2))
        repo.completeOpenAIChat(testMessage, testHistory).collect {}

        val clientMap = repo.javaClass.getDeclaredField("openAIClients").let {
            it.isAccessible = true
            it.get(repo) as Map<*, *>
        }
        assertEquals(2, clientMap.size, "OpenAI client map should contain two clients for different configs")
        val client2Ref = clientMap.values.first { it != client1Ref } // Get the other client
        assertNotNull(client2Ref)
        assertNotEquals(client1Ref, client2Ref, "OpenAI clients should be different for different configs")
    }


    @Test
    fun `completeGoogleChat reuses Google client for same config`() = runTest {
        val repo = ChatRepositoryImpl(mockContext, mockChatRoomDao, mockMessageDao, mockSettingRepository, mockAnthropicAPI)
        val platformConfig1 = Platform(ApiType.GOOGLE, "token1", "N/A", "gemini-pro", true, null, null, null)
         `when`(mockSettingRepository.fetchPlatforms()).thenReturn(listOf(platformConfig1))

        repo.completeGoogleChat(testMessage, testHistory).collect {}
        val client1Ref = repo.javaClass.getDeclaredField("googleClients").let {
            it.isAccessible = true
            (it.get(repo) as Map<*, *>).values.firstOrNull()
        }
        assertNotNull(client1Ref)

        `when`(mockSettingRepository.fetchPlatforms()).thenReturn(listOf(platformConfig1))
        repo.completeGoogleChat(testMessage, testHistory).collect {}
        val client2Ref = repo.javaClass.getDeclaredField("googleClients").let {
            it.isAccessible = true
            (it.get(repo) as Map<*, *>).values.firstOrNull()
        }
        assertEquals(client1Ref, client2Ref, "Google client should be reused for the same config")
        val clientMapSize = repo.javaClass.getDeclaredField("googleClients").let {
            it.isAccessible = true
            (it.get(repo) as Map<*, *>).size
        }
        assertEquals(1, clientMapSize, "Google client map should only contain one client for the same config")
    }

    @Test
    fun `completeGoogleChat creates new Google client for different config`() = runTest {
        val repo = ChatRepositoryImpl(mockContext, mockChatRoomDao, mockMessageDao, mockSettingRepository, mockAnthropicAPI)
        val platformConfig1 = Platform(ApiType.GOOGLE, "token1", "N/A", "gemini-pro", true, null, null, null)
        val platformConfig2 = Platform(ApiType.GOOGLE, "token2", "N/A", "gemini-pro", true, null, null, null) // Different token

        `when`(mockSettingRepository.fetchPlatforms()).thenReturn(listOf(platformConfig1))
        repo.completeGoogleChat(testMessage, testHistory).collect {}
        val client1Ref = repo.javaClass.getDeclaredField("googleClients").let {
            it.isAccessible = true
            (it.get(repo) as Map<*, *>).values.firstOrNull()
        }
        assertNotNull(client1Ref)


        `when`(mockSettingRepository.fetchPlatforms()).thenReturn(listOf(platformConfig2))
        repo.completeGoogleChat(testMessage, testHistory).collect {}
        val clientMap = repo.javaClass.getDeclaredField("googleClients").let {
            it.isAccessible = true
            it.get(repo) as Map<*, *>
        }
        assertEquals(2, clientMap.size, "Google client map should contain two clients for different configs")
        val client2Ref = clientMap.values.first { it != client1Ref }
        assertNotNull(client2Ref)
        assertNotEquals(client1Ref, client2Ref, "Google clients should be different for different configs")
    }

    // Similar tests should be written for Groq and Ollama, verifying they use the openAIClients cache correctly.
    // For example, an Ollama client with a different baseUrl should result in a new entry in openAIClients.
    @Test
    fun `completeOllamaChat uses openAIClients cache`() = runTest {
        val repo = ChatRepositoryImpl(mockContext, mockChatRoomDao, mockMessageDao, mockSettingRepository, mockAnthropicAPI)
        val platformConfigOllama = Platform(ApiType.OLLAMA, "ollama-token", "http://ollama.host/api/", "llama2", true, null, null, null)
        // Note: ChatRepositoryImpl appends "v1/" to Ollama URL if not present.
        val expectedOllamaBaseUrl = "http://ollama.host/api/v1/"


        `when`(mockSettingRepository.fetchPlatforms()).thenReturn(listOf(platformConfigOllama))
        repo.completeOllamaChat(testMessage, testHistory).collect {}

        val openAIClientMap = repo.javaClass.getDeclaredField("openAIClients").let {
            it.isAccessible = true
            it.get(repo) as Map<*, *>
        }
        assertEquals(1, openAIClientMap.size, "openAIClients map should contain one client for Ollama")
        val clientConfigKey = openAIClientMap.keys.first() as ChatRepositoryImpl.OpenAIClientConfig // Assuming data class is public/internal for test
        assertEquals(expectedOllamaBaseUrl, clientConfigKey.baseUrl)
    }
}
