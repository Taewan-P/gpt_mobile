package dev.chungjungsoo.gptmobile.data.database

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevisionListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.ChatAttachmentListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.GeminiSafetySettings
import org.junit.Assert.assertEquals
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
    fun `version five migration adds Gemini safety columns with block none defaults`() {
        assertEquals(
            listOf(
                "ALTER TABLE `platform_v2` ADD COLUMN `harassment_safety_threshold` TEXT NOT NULL DEFAULT 'BLOCK_NONE'",
                "ALTER TABLE `platform_v2` ADD COLUMN `hate_speech_safety_threshold` TEXT NOT NULL DEFAULT 'BLOCK_NONE'",
                "ALTER TABLE `platform_v2` ADD COLUMN `sexually_explicit_safety_threshold` TEXT NOT NULL DEFAULT 'BLOCK_NONE'",
                "ALTER TABLE `platform_v2` ADD COLUMN `dangerous_content_safety_threshold` TEXT NOT NULL DEFAULT 'BLOCK_NONE'"
            ),
            ChatDatabaseV2Migrations.GEMINI_SAFETY_COLUMN_MIGRATIONS
        )
    }

    @Test
    fun `legacy file list migrates to attachment json`() {
        val json = ChatDatabaseV2Migrations.legacyFilesToAttachmentsJson("/tmp/first.png,/tmp/second.webp")

        val attachments = ChatAttachmentListConverter().fromString(json)

        assertEquals(2, attachments.size)
        assertEquals("/tmp/first.png", attachments[0].localFilePath)
        assertEquals("/tmp/first.png", attachments[0].preparedFilePath)
        assertEquals("first.png", attachments[0].resolvedDisplayName)
        assertTrue(attachments[0].providerRefs.isEmpty())
        assertEquals("/tmp/second.webp", attachments[1].localFilePath)
    }

    @Test
    fun `legacy revision list migrates to structured revisions`() {
        val json = ChatDatabaseV2Migrations.legacyRevisionsToStructuredJson(
            revisionsValue = "first revision,second revision",
            createdAt = 1234L
        )

        val revisions = AssistantRevisionListConverter().fromString(json)

        assertEquals(2, revisions.size)
        assertEquals("first revision", revisions[0].content)
        assertEquals("", revisions[0].thoughts)
        assertEquals(1234L, revisions[0].createdAt)
        assertEquals("second revision", revisions[1].content)
        assertEquals(1234L, revisions[1].createdAt)
    }

    @Test
    fun `blank legacy revision list migrates to empty structured revisions`() {
        val json = ChatDatabaseV2Migrations.legacyRevisionsToStructuredJson(
            revisionsValue = "",
            createdAt = 1234L
        )

        val revisions = AssistantRevisionListConverter().fromString(json)

        assertTrue(revisions.isEmpty())
    }

    @Test
    fun `legacy revision migration filters blank segments and applies timestamp`() {
        val json = ChatDatabaseV2Migrations.legacyRevisionsToStructuredJson(
            revisionsValue = "a, ,b",
            createdAt = 1234L
        )

        val revisions = AssistantRevisionListConverter().fromString(json)

        assertEquals(2, revisions.size)
        assertEquals("a", revisions[0].content)
        assertEquals(1234L, revisions[0].createdAt)
        assertEquals("b", revisions[1].content)
        assertEquals(1234L, revisions[1].createdAt)
    }

    @Test
    fun `corrupt assistant revision json decodes to empty list`() {
        val revisions = AssistantRevisionListConverter().fromString("[")

        assertTrue(revisions.isEmpty())
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
    fun `legacy OpenAI compatible custom api urls gain version segment`() {
        assertEquals("https://proxy.example/api/v1/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("CUSTOM", "https://proxy.example/api/"))
        assertEquals("https://openrouter.example/api/v1/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("OPENROUTER", "https://openrouter.example/api/"))
        assertEquals("http://localhost:11434/v1/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("OLLAMA", "http://localhost:11434"))
        assertEquals("https://groq-proxy.example/openai/v1/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("GROQ", "https://groq-proxy.example/openai/"))
        assertEquals("https://proxy.example/api/v1/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("CUSTOM", "https://proxy.example/api/v1/"))
        assertEquals("https://generativelanguage.googleapis.com/custom/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("GOOGLE", "https://generativelanguage.googleapis.com/custom/"))
        assertEquals("https://anthropic-proxy.example/api/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("ANTHROPIC", "https://anthropic-proxy.example/api/"))
    }
}
