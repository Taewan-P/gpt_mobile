package dev.chungjungsoo.gptmobile.data.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.entity.ACTIVE_REVISION_LATEST
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevisionListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.ChatAttachmentListConverter
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.io.File

object ChatDatabaseV2Migrations {

    val AGENT_TOOL_BINDING_CALLBACK = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            installAgentToolBindingConstraints(db)
        }
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureLegacyMessageColumns(db)
            ensureLegacyPlatformColumns(db)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_platform_model_v2` (
                    `chat_id` INTEGER NOT NULL,
                    `platform_uid` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`chat_id`, `platform_uid`),
                    FOREIGN KEY(`chat_id`) REFERENCES `chats_v2`(`chat_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            val platformModelMap = mutableMapOf<String, String>()
            db.query("SELECT uid, model FROM platform_v2").use { platformCursor ->
                val uidIndex = platformCursor.getColumnIndexOrThrow("uid")
                val modelIndex = platformCursor.getColumnIndexOrThrow("model")
                while (platformCursor.moveToNext()) {
                    val uid = platformCursor.getString(uidIndex)
                    val model = platformCursor.getString(modelIndex) ?: ""
                    platformModelMap[uid] = model
                }
            }

            val currentTimestamp = System.currentTimeMillis() / 1000
            db.query("SELECT chat_id, enabled_platform FROM chats_v2").use { chatCursor ->
                val chatIdIndex = chatCursor.getColumnIndexOrThrow("chat_id")
                val enabledPlatformIndex = chatCursor.getColumnIndexOrThrow("enabled_platform")
                while (chatCursor.moveToNext()) {
                    val chatId = chatCursor.getInt(chatIdIndex)
                    val enabledPlatform = chatCursor.getString(enabledPlatformIndex) ?: ""
                    if (enabledPlatform.isBlank()) continue

                    enabledPlatform
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .forEach { platformUid ->
                            val model = platformModelMap[platformUid] ?: ""
                            db.execSQL(
                                "INSERT OR REPLACE INTO chat_platform_model_v2 (chat_id, platform_uid, model, updated_at) VALUES (?, ?, ?, ?)",
                                arrayOf<Any>(chatId, platformUid, model, currentTimestamp)
                            )
                        }
                }
            }
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Early v2 builds advanced the DB version without adding these schema-v2 columns.
            ensureLegacyMessageColumns(db)
            ensureLegacyPlatformColumns(db)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `messages_v2_new` (
                    `message_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `chat_id` INTEGER NOT NULL,
                    `thoughts` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `attachments` TEXT NOT NULL,
                    `revisions` TEXT NOT NULL,
                    `linked_message_id` INTEGER NOT NULL,
                    `platform_type` TEXT,
                    `created_at` INTEGER NOT NULL,
                    FOREIGN KEY(`chat_id`) REFERENCES `chats_v2`(`chat_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO `messages_v2_new` (
                    `message_id`,
                    `chat_id`,
                    `thoughts`,
                    `content`,
                    `attachments`,
                    `revisions`,
                    `linked_message_id`,
                    `platform_type`,
                    `created_at`
                )
                SELECT
                    `message_id`,
                    `chat_id`,
                    `thoughts`,
                    `content`,
                    '' as `attachments`,
                    `revisions`,
                    `linked_message_id`,
                    `platform_type`,
                    `created_at`
                FROM `messages_v2`
                """.trimIndent()
            )

            db.query("SELECT message_id, files FROM messages_v2").use { messageCursor ->
                val messageIdIndex = messageCursor.getColumnIndexOrThrow("message_id")
                val filesIndex = messageCursor.getColumnIndexOrThrow("files")
                while (messageCursor.moveToNext()) {
                    val messageId = messageCursor.getInt(messageIdIndex)
                    val filesValue = messageCursor.getString(filesIndex).orEmpty()
                    db.execSQL(
                        "UPDATE messages_v2_new SET attachments = ? WHERE message_id = ?",
                        arrayOf<Any>(legacyFilesToAttachmentsJson(filesValue), messageId)
                    )
                }
            }

            db.execSQL("DROP TABLE `messages_v2`")
            db.execSQL("ALTER TABLE `messages_v2_new` RENAME TO `messages_v2`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_v2_chat_id` ON `messages_v2` (`chat_id`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `messages_v2_new` (
                    `message_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `chat_id` INTEGER NOT NULL,
                    `thoughts` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `attachments` TEXT NOT NULL,
                    `revisions` TEXT NOT NULL,
                    `active_revision_index` INTEGER NOT NULL,
                    `linked_message_id` INTEGER NOT NULL,
                    `platform_type` TEXT,
                    `created_at` INTEGER NOT NULL,
                    FOREIGN KEY(`chat_id`) REFERENCES `chats_v2`(`chat_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.query(
                """
                SELECT
                    `message_id`,
                    `chat_id`,
                    `thoughts`,
                    `content`,
                    `attachments`,
                    `revisions`,
                    `linked_message_id`,
                    `platform_type`,
                    `created_at`
                FROM `messages_v2`
                """.trimIndent()
            ).use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("message_id")
                val chatIdIndex = cursor.getColumnIndexOrThrow("chat_id")
                val thoughtsIndex = cursor.getColumnIndexOrThrow("thoughts")
                val contentIndex = cursor.getColumnIndexOrThrow("content")
                val attachmentsIndex = cursor.getColumnIndexOrThrow("attachments")
                val revisionsIndex = cursor.getColumnIndexOrThrow("revisions")
                val linkedMessageIdIndex = cursor.getColumnIndexOrThrow("linked_message_id")
                val platformTypeIndex = cursor.getColumnIndexOrThrow("platform_type")
                val createdAtIndex = cursor.getColumnIndexOrThrow("created_at")

                while (cursor.moveToNext()) {
                    db.execSQL(
                        """
                        INSERT INTO `messages_v2_new` (
                            `message_id`,
                            `chat_id`,
                            `thoughts`,
                            `content`,
                            `attachments`,
                            `revisions`,
                            `active_revision_index`,
                            `linked_message_id`,
                            `platform_type`,
                            `created_at`
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf(
                            cursor.getInt(idIndex),
                            cursor.getInt(chatIdIndex),
                            cursor.getString(thoughtsIndex) ?: "",
                            cursor.getString(contentIndex) ?: "",
                            cursor.getString(attachmentsIndex) ?: "",
                            legacyRevisionsToStructuredJson(
                                revisionsValue = cursor.getString(revisionsIndex).orEmpty(),
                                createdAt = cursor.getLong(createdAtIndex)
                            ),
                            ACTIVE_REVISION_LATEST,
                            cursor.getInt(linkedMessageIdIndex),
                            cursor.getString(platformTypeIndex),
                            cursor.getLong(createdAtIndex)
                        )
                    )
                }
            }

            db.execSQL("DROP TABLE `messages_v2`")
            db.execSQL("ALTER TABLE `messages_v2_new` RENAME TO `messages_v2`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_v2_chat_id` ON `messages_v2` (`chat_id`)")
        }
    }

    val GEMINI_SAFETY_COLUMN_MIGRATIONS = listOf(
        "ALTER TABLE `platform_v2` ADD COLUMN `harassment_safety_threshold` TEXT NOT NULL DEFAULT 'BLOCK_NONE'",
        "ALTER TABLE `platform_v2` ADD COLUMN `hate_speech_safety_threshold` TEXT NOT NULL DEFAULT 'BLOCK_NONE'",
        "ALTER TABLE `platform_v2` ADD COLUMN `sexually_explicit_safety_threshold` TEXT NOT NULL DEFAULT 'BLOCK_NONE'",
        "ALTER TABLE `platform_v2` ADD COLUMN `dangerous_content_safety_threshold` TEXT NOT NULL DEFAULT 'BLOCK_NONE'"
    )

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            GEMINI_SAFETY_COLUMN_MIGRATIONS.forEach(db::execSQL)
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateLegacyProviderApiUrls(db)
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `messages_v2` ADD COLUMN `current_run_id` TEXT")
            db.execSQL("ALTER TABLE `platform_v2` ADD COLUMN `secret_ref` TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tool_connections` (
                    `connection_uid` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `alias` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `endpoint_url` TEXT,
                    `auth_type` TEXT NOT NULL,
                    `secret_ref` TEXT,
                    `oauth_client_id` TEXT,
                    `allow_cleartext` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`connection_uid`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tool_connections_alias` ON `tool_connections` (`alias`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `agent_tool_bindings` (
                    `binding_uid` TEXT NOT NULL,
                    `profile_uid` TEXT NOT NULL,
                    `connection_uid` TEXT,
                    `tool_name` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`binding_uid`),
                    FOREIGN KEY(`connection_uid`) REFERENCES `tool_connections`(`connection_uid`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_tool_bindings_profile_uid` ON `agent_tool_bindings` (`profile_uid`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_tool_bindings_connection_uid` ON `agent_tool_bindings` (`connection_uid`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_agent_tool_bindings_profile_uid_connection_uid_tool_name` ON `agent_tool_bindings` (`profile_uid`, `connection_uid`, `tool_name`)")
            installAgentToolBindingConstraints(db)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `agent_runs` (
                    `run_id` TEXT NOT NULL,
                    `chat_id` INTEGER NOT NULL,
                    `user_message_id` INTEGER NOT NULL,
                    `assistant_message_id` INTEGER NOT NULL,
                    `profile_uid` TEXT NOT NULL,
                    `provider_snapshot` TEXT NOT NULL,
                    `model_snapshot` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `started_at` INTEGER,
                    `completed_at` INTEGER,
                    `terminal_error` TEXT,
                    PRIMARY KEY(`run_id`),
                    FOREIGN KEY(`chat_id`) REFERENCES `chats_v2`(`chat_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`user_message_id`) REFERENCES `messages_v2`(`message_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`assistant_message_id`) REFERENCES `messages_v2`(`message_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_runs_chat_id` ON `agent_runs` (`chat_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_runs_user_message_id` ON `agent_runs` (`user_message_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_runs_assistant_message_id` ON `agent_runs` (`assistant_message_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_runs_status` ON `agent_runs` (`status`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tool_events` (
                    `event_id` TEXT NOT NULL,
                    `run_id` TEXT NOT NULL,
                    `sequence` INTEGER NOT NULL,
                    `call_id` TEXT NOT NULL,
                    `connection_uid_snapshot` TEXT,
                    `connection_name_snapshot` TEXT,
                    `tool_name` TEXT NOT NULL,
                    `model_tool_name` TEXT NOT NULL,
                    `arguments` TEXT NOT NULL,
                    `result` TEXT,
                    `result_type` TEXT,
                    `status` TEXT NOT NULL,
                    `is_error` INTEGER NOT NULL,
                    `started_at` INTEGER,
                    `completed_at` INTEGER,
                    `error` TEXT,
                    PRIMARY KEY(`event_id`),
                    FOREIGN KEY(`run_id`) REFERENCES `agent_runs`(`run_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tool_events_run_id` ON `tool_events` (`run_id`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tool_events_run_id_sequence` ON `tool_events` (`run_id`, `sequence`)")
        }
    }

    val LOCAL_MODEL_TABLE_MIGRATIONS = listOf(
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
    )

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            LOCAL_MODEL_TABLE_MIGRATIONS.forEach(db::execSQL)
        }
    }

    val PLATFORM_LOCAL_INFERENCE_COLUMN_MIGRATIONS = listOf(
        "ALTER TABLE `platform_v2` ADD COLUMN `top_k` INTEGER",
        "ALTER TABLE `platform_v2` ADD COLUMN `max_tokens` INTEGER",
        "ALTER TABLE `platform_v2` ADD COLUMN `accelerator` TEXT"
    )

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            PLATFORM_LOCAL_INFERENCE_COLUMN_MIGRATIONS.forEach(db::execSQL)
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `messages_v2` ADD COLUMN `timeline` TEXT NOT NULL DEFAULT '[]'")
            // Mirrors hasUnavailableAssistantOrder: only rows that interleaved reasoning with text,
            // or ran tools, have an order that cannot be reconstructed. A plain reply keeps an empty
            // timeline so it renders without the chronology notice.
            db.execSQL(
                """
                UPDATE `messages_v2`
                SET `timeline` = '[{"type":"LEGACY_ORDER"}]'
                WHERE `platform_type` IS NOT NULL
                  AND (
                      (TRIM(`content`) != '' AND TRIM(`thoughts`) != '')
                      OR EXISTS (
                          SELECT 1 FROM `tool_events`
                          WHERE `tool_events`.`run_id` = `messages_v2`.`current_run_id`
                      )
                  )
                """.trimIndent()
            )
        }
    }

    private fun installAgentToolBindingConstraints(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `agent_tool_bindings_builtin_insert_unique`
            BEFORE INSERT ON `agent_tool_bindings`
            WHEN NEW.`connection_uid` IS NULL AND EXISTS (
                SELECT 1 FROM `agent_tool_bindings`
                WHERE `profile_uid` = NEW.`profile_uid`
                  AND `connection_uid` IS NULL
                  AND `tool_name` = NEW.`tool_name`
            )
            BEGIN
                SELECT RAISE(ABORT, 'duplicate built-in tool binding');
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `agent_tool_bindings_builtin_update_unique`
            BEFORE UPDATE ON `agent_tool_bindings`
            WHEN NEW.`connection_uid` IS NULL AND EXISTS (
                SELECT 1 FROM `agent_tool_bindings`
                WHERE `profile_uid` = NEW.`profile_uid`
                  AND `connection_uid` IS NULL
                  AND `tool_name` = NEW.`tool_name`
                  AND `binding_uid` != OLD.`binding_uid`
            )
            BEGIN
                SELECT RAISE(ABORT, 'duplicate built-in tool binding');
            END
            """.trimIndent()
        )
    }

    internal fun legacyFilesToAttachmentsJson(filesValue: String): String {
        val attachments = filesValue
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { filePath ->
                ChatAttachment(
                    localFilePath = filePath,
                    preparedFilePath = filePath,
                    displayName = File(filePath).name,
                    mimeType = "",
                    sizeBytes = 0L
                )
            }

        return ChatAttachmentListConverter().fromList(attachments)
    }

    internal fun ensureLegacyMessageColumns(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("messages_v2", "thoughts")) {
            db.execSQL("ALTER TABLE `messages_v2` ADD COLUMN `thoughts` TEXT NOT NULL DEFAULT ''")
        }
        if (!db.hasColumn("messages_v2", "revisions")) {
            db.execSQL("ALTER TABLE `messages_v2` ADD COLUMN `revisions` TEXT NOT NULL DEFAULT ''")
        }
    }

    internal fun ensureLegacyPlatformColumns(db: SupportSQLiteDatabase) {
        val hasCompatibleType = db.hasColumn("platform_v2", "compatible_type")
        val hasReasoning = db.hasColumn("platform_v2", "reasoning")
        if (hasCompatibleType && hasReasoning) return

        val compatibleType = if (hasCompatibleType) {
            "`compatible_type`"
        } else {
            """
            CASE
                WHEN LOWER(`name`) LIKE '%anthropic%' OR LOWER(`api_url`) LIKE '%anthropic%' THEN 'ANTHROPIC'
                WHEN LOWER(`name`) LIKE '%gemini%' OR LOWER(`name`) LIKE '%google%' OR LOWER(`api_url`) LIKE '%googleapis%' THEN 'GOOGLE'
                WHEN LOWER(`name`) LIKE '%groq%' OR LOWER(`api_url`) LIKE '%groq%' THEN 'GROQ'
                WHEN LOWER(`name`) LIKE '%ollama%' THEN 'OLLAMA'
                WHEN LOWER(`name`) LIKE '%openrouter%' OR LOWER(`api_url`) LIKE '%openrouter%' THEN 'OPENROUTER'
                WHEN LOWER(`name`) LIKE '%openai%' OR LOWER(`api_url`) LIKE '%openai%' THEN 'OPENAI'
                ELSE 'CUSTOM'
            END
            """.trimIndent()
        }
        val reasoning = if (hasReasoning) "`reasoning`" else "0"

        db.execSQL("ALTER TABLE `platform_v2` RENAME TO `platform_v2_legacy`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `platform_v2` (
                `platform_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uid` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `compatible_type` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL,
                `api_url` TEXT NOT NULL,
                `token` TEXT,
                `model` TEXT NOT NULL,
                `temperature` REAL,
                `top_p` REAL,
                `system_prompt` TEXT,
                `stream` INTEGER NOT NULL,
                `reasoning` INTEGER NOT NULL,
                `timeout` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `platform_v2` (
                `platform_id`, `uid`, `name`, `compatible_type`, `enabled`, `api_url`,
                `token`, `model`, `temperature`, `top_p`, `system_prompt`, `stream`,
                `reasoning`, `timeout`
            )
            SELECT
                `platform_id`, `uid`, `name`, $compatibleType, `enabled`, `api_url`,
                `token`, `model`, `temperature`, `top_p`, `system_prompt`, `stream`,
                $reasoning, `timeout`
            FROM `platform_v2_legacy`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `platform_v2_legacy`")
    }

    internal fun legacyRevisionsToStructuredJson(
        revisionsValue: String,
        createdAt: Long
    ): String {
        val revisions = revisionsValue
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { AssistantRevision(content = it, thoughts = "", createdAt = createdAt) }

        return AssistantRevisionListConverter().fromList(revisions)
    }

    internal fun migrateLegacyProviderApiUrls(db: SupportSQLiteDatabase) {
        val updates = mutableListOf<Pair<Int, String>>()
        db.query("SELECT platform_id, compatible_type, api_url FROM platform_v2").use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("platform_id")
            val compatibleTypeIndex = cursor.getColumnIndexOrThrow("compatible_type")
            val apiUrlIndex = cursor.getColumnIndexOrThrow("api_url")
            while (cursor.moveToNext()) {
                val id = cursor.getInt(idIndex)
                val compatibleType = cursor.getString(compatibleTypeIndex) ?: continue
                val apiUrl = cursor.getString(apiUrlIndex) ?: continue
                val normalizedApiUrl = normalizeLegacyProviderApiUrl(compatibleType, apiUrl)
                if (normalizedApiUrl != apiUrl) {
                    updates.add(id to normalizedApiUrl)
                }
            }
        }

        updates.forEach { (id, apiUrl) ->
            db.execSQL(
                "UPDATE platform_v2 SET api_url = ? WHERE platform_id = ?",
                arrayOf<Any>(apiUrl, id)
            )
        }
    }

    internal fun normalizeLegacyProviderApiUrl(
        compatibleType: String,
        apiUrl: String
    ): String {
        val normalizedApiUrl = ModelConstants.normalizeLegacyAPIUrl(apiUrl)
        if (normalizedApiUrl != apiUrl) return normalizedApiUrl

        val trimmedApiUrl = apiUrl.trim()
        if (trimmedApiUrl.isBlank() || compatibleType !in legacyOpenAICompatibleTypes || trimmedApiUrl.hasV1Segment()) {
            return apiUrl
        }

        return "${trimmedApiUrl.trimEnd('/')}/v1/"
    }

    private val legacyOpenAICompatibleTypes = setOf(
        ClientType.CUSTOM.name,
        ClientType.GROQ.name,
        ClientType.OLLAMA.name,
        ClientType.OPENROUTER.name
    )

    private fun String.hasV1Segment(): Boolean = trimEnd('/')
        .split("/")
        .any { segment -> segment.substringBefore("?").substringBefore("#") == "v1" }
}

private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean = query("PRAGMA table_info(`$table`)").use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
        .any { it == column }
}
