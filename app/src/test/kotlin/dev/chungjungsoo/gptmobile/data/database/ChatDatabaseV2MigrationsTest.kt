package dev.chungjungsoo.gptmobile.data.database

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevisionListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.CombinedModelResponse
import dev.chungjungsoo.gptmobile.data.database.entity.CombinedModelResponseListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.ConversationMode
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.GeminiSafetySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDatabaseV2MigrationsTest {
    @Test
    fun `new platform defaults Gemini safety thresholds to block none`() {
        val platform = PlatformV2(
            name = "Google",
            compatibleType = ClientType.GOOGLE,
            apiUrl = "https://generativelanguage.googleapis.com",
            model = "gemini-3-pro-preview"
        )

        assertEquals(GeminiSafetySettings.BLOCK_NONE, platform.harassmentSafetyThreshold)
        assertEquals(GeminiSafetySettings.BLOCK_NONE, platform.hateSpeechSafetyThreshold)
        assertEquals(GeminiSafetySettings.BLOCK_NONE, platform.sexuallyExplicitSafetyThreshold)
        assertEquals(GeminiSafetySettings.BLOCK_NONE, platform.dangerousContentSafetyThreshold)
    }

    @Test
    fun `new platform defaults max tool calls to fifty`() {
        val platform = PlatformV2(
            name = "OpenAI",
            compatibleType = ClientType.OPENAI,
            apiUrl = "https://api.openai.com/v1/",
            model = "gpt-5.6"
        )

        assertEquals(50, platform.maxToolCalls)
    }

    @Test
    fun `new platform defaults open router routing to null`() {
        val platform = PlatformV2(
            name = "OpenRouter",
            compatibleType = ClientType.OPENROUTER,
            apiUrl = "https://openrouter.ai/api/v1/",
            model = "openai/gpt-5.6-sol"
        )

        assertNull(platform.openRouterRouting)
    }

    @Test
    fun `new platform defaults ollama options to null`() {
        val platform = PlatformV2(
            name = "Ollama",
            compatibleType = ClientType.OLLAMA,
            apiUrl = "http://localhost:11434/v1/",
            model = "gpt-oss"
        )

        assertNull(platform.ollamaOptions)
    }

    @Test
    fun `new platform defaults disable all tools to false`() {
        val platform = PlatformV2(
            name = "OpenAI",
            compatibleType = ClientType.OPENAI,
            apiUrl = "https://api.openai.com/v1/",
            model = "gpt-5.6"
        )

        assertFalse(platform.disableAllTools)
        assertFalse(platform.disableRemoteTools)
        assertFalse(platform.disableLocalTools)
    }

    @Test
    fun `migration instances have correct versions`() {
        ChatDatabaseV2Migrations.ALL_MIGRATIONS.forEach { migration ->
            assertEquals(migration.startVersion + 1, migration.endVersion)
        }

        assertEquals(10, ChatDatabaseV2Migrations.ALL_MIGRATIONS.first().startVersion)
        assertEquals(32, ChatDatabaseV2Migrations.ALL_MIGRATIONS.last().endVersion)

        assertEquals(10, ChatDatabaseV2Migrations.MIGRATION_10_11.startVersion)
        assertEquals(11, ChatDatabaseV2Migrations.MIGRATION_10_11.endVersion)

        assertEquals(11, ChatDatabaseV2Migrations.MIGRATION_11_12.startVersion)
        assertEquals(12, ChatDatabaseV2Migrations.MIGRATION_11_12.endVersion)

        assertEquals(12, ChatDatabaseV2Migrations.MIGRATION_12_13.startVersion)
        assertEquals(13, ChatDatabaseV2Migrations.MIGRATION_12_13.endVersion)

        assertEquals(13, ChatDatabaseV2Migrations.MIGRATION_13_14.startVersion)
        assertEquals(14, ChatDatabaseV2Migrations.MIGRATION_13_14.endVersion)

        assertEquals(14, ChatDatabaseV2Migrations.MIGRATION_14_15.startVersion)
        assertEquals(15, ChatDatabaseV2Migrations.MIGRATION_14_15.endVersion)

        assertEquals(15, ChatDatabaseV2Migrations.MIGRATION_15_16.startVersion)
        assertEquals(16, ChatDatabaseV2Migrations.MIGRATION_15_16.endVersion)

        assertEquals(16, ChatDatabaseV2Migrations.MIGRATION_16_17.startVersion)
        assertEquals(17, ChatDatabaseV2Migrations.MIGRATION_16_17.endVersion)

        assertEquals(17, ChatDatabaseV2Migrations.MIGRATION_17_18.startVersion)
        assertEquals(18, ChatDatabaseV2Migrations.MIGRATION_17_18.endVersion)

        assertEquals(18, ChatDatabaseV2Migrations.MIGRATION_18_19.startVersion)
        assertEquals(19, ChatDatabaseV2Migrations.MIGRATION_18_19.endVersion)

        assertEquals(19, ChatDatabaseV2Migrations.MIGRATION_19_20.startVersion)
        assertEquals(20, ChatDatabaseV2Migrations.MIGRATION_19_20.endVersion)

        assertEquals(20, ChatDatabaseV2Migrations.MIGRATION_20_21.startVersion)
        assertEquals(21, ChatDatabaseV2Migrations.MIGRATION_20_21.endVersion)

        assertEquals(21, ChatDatabaseV2Migrations.MIGRATION_21_22.startVersion)
        assertEquals(22, ChatDatabaseV2Migrations.MIGRATION_21_22.endVersion)

        assertEquals(22, ChatDatabaseV2Migrations.MIGRATION_22_23.startVersion)
        assertEquals(23, ChatDatabaseV2Migrations.MIGRATION_22_23.endVersion)

        assertEquals(23, ChatDatabaseV2Migrations.MIGRATION_23_24.startVersion)
        assertEquals(24, ChatDatabaseV2Migrations.MIGRATION_23_24.endVersion)

        assertEquals(24, ChatDatabaseV2Migrations.MIGRATION_24_25.startVersion)
        assertEquals(25, ChatDatabaseV2Migrations.MIGRATION_24_25.endVersion)

        assertEquals(25, ChatDatabaseV2Migrations.MIGRATION_25_26.startVersion)
        assertEquals(26, ChatDatabaseV2Migrations.MIGRATION_25_26.endVersion)

        assertEquals(26, ChatDatabaseV2Migrations.MIGRATION_26_27.startVersion)
        assertEquals(27, ChatDatabaseV2Migrations.MIGRATION_26_27.endVersion)

        assertEquals(31, ChatDatabaseV2Migrations.MIGRATION_31_32.startVersion)
        assertEquals(32, ChatDatabaseV2Migrations.MIGRATION_31_32.endVersion)
    }

    @Test
    fun `corrupt assistant revision json decodes to empty list`() {
        val revisions = AssistantRevisionListConverter().fromString("[")

        assertTrue(revisions.isEmpty())
    }

    @Test
    fun `assistant revision serialization preserves run linkage`() {
        val converter = AssistantRevisionListConverter()
        val encoded = converter.fromList(
            listOf(
                AssistantRevision(
                    content = "Answer",
                    thoughts = "Reasoning",
                    createdAt = 1234L,
                    runId = "run-123"
                )
            )
        )

        val decoded = converter.fromString(encoded)

        assertEquals("run-123", decoded.single().runId)
    }

    @Test
    fun `legacy assistant revision json decodes without run linkage`() {
        val decoded = AssistantRevisionListConverter().fromString(
            """[{"content":"Old answer","thoughts":"","createdAt":1234}]"""
        )

        assertNull(decoded.single().runId)
    }

    @Test
    fun `legacy provider api urls normalize to current defaults`() {
        assertEquals(ModelConstants.OPENAI_API_URL, ModelConstants.normalizeLegacyAPIUrl("https://api.openai.com/"))
        assertEquals(ModelConstants.ANTHROPIC_API_URL, ModelConstants.normalizeLegacyAPIUrl("https://api.anthropic.com/"))
        assertEquals(ModelConstants.GOOGLE_API_URL, ModelConstants.normalizeLegacyAPIUrl("https://generativelanguage.googleapis.com"))
        assertEquals(ModelConstants.GROQ_API_URL, ModelConstants.normalizeLegacyAPIUrl("https://api.groq.com/openai/"))
        assertEquals(ModelConstants.OPENROUTER_API_URL, ModelConstants.normalizeLegacyAPIUrl("https://openrouter.ai/api/"))
        assertEquals(ModelConstants.OLLAMA_API_URL, ModelConstants.normalizeLegacyAPIUrl("http://localhost:11434/"))
        assertEquals("https://proxy.example/api/", ModelConstants.normalizeLegacyAPIUrl("https://proxy.example/api/"))
    }

    @Test
    fun `new platform defaults local inference columns to null`() {
        val platform = PlatformV2(
            name = "Local",
            compatibleType = ClientType.LITERT_LM,
            apiUrl = "",
            model = "gemma3-1b-it"
        )

        assertNull(platform.topK)
        assertNull(platform.maxTokens)
        assertNull(platform.accelerator)
    }

    @Test
    fun `combined conversation defaults to standard and normalizes values`() {
        val chatRoom = ChatRoomV2(title = "Test Room", enabledPlatform = listOf("profile-1"))
        assertEquals(listOf("profile-1"), chatRoom.activePlatform)
        assertEquals(ConversationMode.STANDARD, chatRoom.conversationMode)
        assertEquals(ConversationMode.COMBINED, ConversationMode.normalize("combined"))
        assertEquals(ConversationMode.STANDARD, ConversationMode.normalize("anything-else"))
    }

    @Test
    fun `combined response converter preserves model identity and content`() {
        val converter = CombinedModelResponseListConverter()
        val encoded = converter.fromList(
            listOf(
                CombinedModelResponse(
                    platformUid = "openai",
                    platformName = "OpenAI",
                    modelName = "gpt",
                    content = "Candidate answer"
                )
            )
        )
        val decoded = converter.fromString(encoded).single()
        assertEquals("openai", decoded.platformUid)
        assertEquals("OpenAI", decoded.platformName)
        assertEquals("gpt", decoded.modelName)
        assertEquals("Candidate answer", decoded.content)
    }

    @Test
    fun `default favorite state is false`() {
        val chatRoom = ChatRoomV2(title = "Test Room")
        assertFalse(chatRoom.isFavorite)
    }
}
