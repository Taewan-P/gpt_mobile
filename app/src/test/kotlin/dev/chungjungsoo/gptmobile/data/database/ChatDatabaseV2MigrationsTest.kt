package dev.chungjungsoo.gptmobile.data.database

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevisionListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.ChatAttachmentListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.GeminiSafetySettings
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun `assistant revision serialization preserves run linkage`() {
        val converter = AssistantRevisionListConverter()
        val encoded = converter.fromList(
            listOf(
                dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision(
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
    fun `legacy OpenAI compatible custom api urls gain version segment`() {
        assertEquals("https://proxy.example/api/v1/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("CUSTOM", "https://proxy.example/api/"))
        assertEquals("https://openrouter.example/api/v1/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("OPENROUTER", "https://openrouter.example/api/"))
        assertEquals("http://localhost:11434/v1/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("OLLAMA", "http://localhost:11434"))
        assertEquals("https://groq-proxy.example/openai/v1/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("GROQ", "https://groq-proxy.example/openai/"))
        assertEquals("https://proxy.example/api/v1/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("CUSTOM", "https://proxy.example/api/v1/"))
        assertEquals("https://generativelanguage.googleapis.com/custom/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("GOOGLE", "https://generativelanguage.googleapis.com/custom/"))
        assertEquals("https://anthropic-proxy.example/api/", ChatDatabaseV2Migrations.normalizeLegacyProviderApiUrl("ANTHROPIC", "https://anthropic-proxy.example/api/"))
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
    fun `version ten migration adds local inference columns`() {
        assertEquals(
            listOf(
                "ALTER TABLE `platform_v2` ADD COLUMN `top_k` INTEGER",
                "ALTER TABLE `platform_v2` ADD COLUMN `max_tokens` INTEGER",
                "ALTER TABLE `platform_v2` ADD COLUMN `accelerator` TEXT"
            ),
            ChatDatabaseV2Migrations.PLATFORM_LOCAL_INFERENCE_COLUMN_MIGRATIONS
        )
    }

    @Test
    fun `version nine migration creates local models table`() {
        assertEquals(
            listOf(
                """
                CREATE TABLE IF NOT EXISTS `local_models` (
                    `catalog_entry_id` TEXT NOT NULL,
                    `commit_hash` TEXT NOT NULL,
                    `file_name` TEXT NOT NULL,
                    `relative_directory` TEXT NOT NULL,
                    `total_bytes` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`catalog_entry_id`)
                )
                """.trimIndent()
            ),
            ChatDatabaseV2Migrations.LOCAL_MODEL_TABLE_MIGRATIONS
        )
    }

    @Test
    fun `new platform defaults resumable replies to false`() {
        val platform = PlatformV2(
            name = "OpenAI",
            compatibleType = ClientType.OPENAI,
            apiUrl = ModelConstants.OPENAI_API_URL,
            model = "gpt-5.4"
        )

        assertEquals(false, platform.resumableReplies)
    }

    @Test
    fun `version eleven migration adds checkpoint and capacity tables`() {
        val statements = ChatDatabaseV2Migrations.COMPACTION_TABLE_MIGRATIONS
        assertEquals(3, statements.size)
        assertTrue(statements[0].contains("CREATE TABLE IF NOT EXISTS `context_checkpoints`"))
        assertTrue(
            statements[0].contains(
                "FOREIGN KEY(`chat_id`) REFERENCES `chats_v2`(`chat_id`) ON UPDATE NO ACTION ON DELETE CASCADE"
            )
        )
        assertTrue(statements[1].contains("CREATE TABLE IF NOT EXISTS `model_capacities`"))
        assertTrue(statements[1].contains("`detected_context_tokens` INTEGER"))
        assertTrue(statements[1].contains("`override_context_tokens` INTEGER"))
        assertFalse(statements[1].contains("`detected_context_tokens` INTEGER NOT NULL"))
        assertFalse(statements[1].contains("`override_context_tokens` INTEGER NOT NULL"))
        assertEquals(
            "ALTER TABLE `platform_v2` ADD COLUMN `resumable_replies` INTEGER NOT NULL DEFAULT 0",
            statements[2]
        )
    }

    @Test
    fun `schema ten export stays pre compaction`() {
        val root = schemaDatabase(10)
        assertEquals(10, root["version"]!!.jsonPrimitive.int)
        assertEquals("285174f72c16361eab586029e7e9a8ae", root["identityHash"]!!.jsonPrimitive.content)
        val tables = entityNames(root)
        assertFalse(tables.contains("context_checkpoints"))
        assertFalse(tables.contains("model_capacities"))
        assertTrue(tables.contains("messages_v2"))
        assertTrue(tables.contains("chats_v2"))
        assertFalse(entityFields(root, "platform_v2").contains("resumable_replies"))
    }

    @Test
    fun `schema eleven export has compaction tables and resumable default`() {
        val root = schemaDatabase(11)
        assertEquals(11, root["version"]!!.jsonPrimitive.int)
        val tables = entityNames(root)
        assertTrue(tables.contains("context_checkpoints"))
        assertTrue(tables.contains("model_capacities"))
        assertTrue(tables.contains("messages_v2"))
        val platform = entity(root, "platform_v2")
        val resumable = platform["fields"]!!.jsonArray.first {
            it.jsonObject["columnName"]!!.jsonPrimitive.content == "resumable_replies"
        }.jsonObject
        assertEquals("INTEGER", resumable["affinity"]!!.jsonPrimitive.content)
        assertEquals(true, resumable["notNull"]!!.jsonPrimitive.content.toBooleanStrict())
        assertEquals("0", resumable["defaultValue"]!!.jsonPrimitive.content)
        val capacities = entity(root, "model_capacities")
        val capacityFields = capacities["fields"]!!.jsonArray.associate { field ->
            val obj = field.jsonObject
            obj["columnName"]!!.jsonPrimitive.content to obj
        }
        assertEquals(null, capacityFields.getValue("detected_context_tokens")["notNull"])
        assertEquals(null, capacityFields.getValue("override_context_tokens")["notNull"])
    }

    private fun schemaDatabase(version: Int) = Json.parseToJsonElement(schemaFile(version).readText())
        .jsonObject["database"]!!
        .jsonObject

    private fun entityNames(root: kotlinx.serialization.json.JsonObject) = root["entities"]!!
        .jsonArray
        .map { it.jsonObject["tableName"]!!.jsonPrimitive.content }

    private fun entity(root: kotlinx.serialization.json.JsonObject, table: String) = root["entities"]!!
        .jsonArray
        .map { it.jsonObject }
        .first { it["tableName"]!!.jsonPrimitive.content == table }

    private fun entityFields(root: kotlinx.serialization.json.JsonObject, table: String) = entity(root, table)["fields"]!!
        .jsonArray
        .map { it.jsonObject["columnName"]!!.jsonPrimitive.content }

    private fun schemaFile(version: Int): File {
        val relative = "schemas/dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2/$version.json"
        val candidates = listOf(File(relative), File("app/$relative"), File("../$relative"))
        return candidates.firstOrNull { it.isFile } ?: error("missing schema $version.json")
    }
}
