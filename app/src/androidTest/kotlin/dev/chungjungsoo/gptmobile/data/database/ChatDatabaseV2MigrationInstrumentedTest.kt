package dev.chungjungsoo.gptmobile.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatDatabaseV2MigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChatDatabaseV2::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrateBrokenVersion2To7_repairsMissingColumnsAndPreservesLegacyRows() {
        helper.createDatabase(BROKEN_VERSION_2_DATABASE, 1).apply {
            execSQL(
                "INSERT INTO chats_v2 (chat_id, title, enabled_platform, created_at, updated_at) " +
                    "VALUES (1, 'Legacy chat', 'profile-1', 10, 11)"
            )
            execSQL(
                """
                INSERT INTO platform_v2 (
                    platform_id, uid, name, enabled, api_url, token, model,
                    temperature, top_p, system_prompt, stream, timeout
                ) VALUES (
                    1, 'profile-1', 'Anthropic', 1, 'https://api.anthropic.com/',
                    'legacy-secret', 'claude', NULL, NULL, 'Legacy prompt', 1, 30
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO messages_v2 (
                    message_id, chat_id, content, files, linked_message_id, platform_type, created_at
                ) VALUES (1, 1, 'Legacy question', '/tmp/legacy.png', 0, NULL, 12)
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_platform_model_v2 (
                    chat_id INTEGER NOT NULL,
                    platform_uid TEXT NOT NULL,
                    model TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(chat_id, platform_uid),
                    FOREIGN KEY(chat_id) REFERENCES chats_v2(chat_id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO chat_platform_model_v2 (chat_id, platform_uid, model, updated_at) " +
                    "VALUES (1, 'profile-1', 'claude', 11)"
            )
            version = 2
            close()
        }

        val database = helper.runMigrationsAndValidate(
            BROKEN_VERSION_2_DATABASE,
            7,
            true,
            ChatDatabaseV2Migrations.MIGRATION_2_3,
            ChatDatabaseV2Migrations.MIGRATION_3_4,
            ChatDatabaseV2Migrations.MIGRATION_4_5,
            ChatDatabaseV2Migrations.MIGRATION_5_6,
            ChatDatabaseV2Migrations.MIGRATION_6_7
        )

        database.query(
            "SELECT thoughts, content, attachments, revisions, active_revision_index, current_run_id " +
                "FROM messages_v2 WHERE message_id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
            assertEquals("Legacy question", cursor.getString(1))
            assertTrue(cursor.getString(2).contains("legacy.png"))
            assertEquals("[]", cursor.getString(3))
            assertEquals(-1, cursor.getInt(4))
            assertTrue(cursor.isNull(5))
        }
        database.query(
            "SELECT compatible_type, reasoning, token, secret_ref, system_prompt FROM platform_v2 WHERE platform_id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ANTHROPIC", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("legacy-secret", cursor.getString(2))
            assertTrue(cursor.isNull(3))
            assertEquals("Legacy prompt", cursor.getString(4))
        }
        database.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7_preservesExistingRowsAndStartsWithNoToolsOrRuns() {
        helper.createDatabase(TEST_DATABASE, 6).apply {
            execSQL(
                """
                INSERT INTO chats_v2 (chat_id, title, enabled_platform, created_at, updated_at)
                VALUES (7, 'Existing chat', 'profile-1', 100, 101)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO platform_v2 (
                    platform_id, uid, name, compatible_type, enabled, api_url, token, model,
                    temperature, top_p, system_prompt, stream, reasoning, timeout,
                    harassment_safety_threshold, hate_speech_safety_threshold,
                    sexually_explicit_safety_threshold, dangerous_content_safety_threshold
                ) VALUES (
                    3, 'profile-1', 'OpenAI', 'OPENAI', 1, 'https://api.openai.com/v1/',
                    'legacy-secret', 'gpt-5', NULL, NULL, 'Keep this prompt', 1, 0, 30,
                    'BLOCK_NONE', 'BLOCK_NONE', 'BLOCK_NONE', 'BLOCK_NONE'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO messages_v2 (
                    message_id, chat_id, thoughts, content, attachments, revisions,
                    active_revision_index, linked_message_id, platform_type, created_at
                ) VALUES (11, 7, '', 'Old question', '', '[]', -1, 0, NULL, 102)
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            7,
            true,
            ChatDatabaseV2Migrations.MIGRATION_6_7
        )

        database.query("SELECT title, enabled_platform FROM chats_v2 WHERE chat_id = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Existing chat", cursor.getString(0))
            assertEquals("profile-1", cursor.getString(1))
        }
        database.query("SELECT token, secret_ref, system_prompt FROM platform_v2 WHERE platform_id = 3").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy-secret", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertEquals("Keep this prompt", cursor.getString(2))
        }
        database.query("SELECT current_run_id FROM messages_v2 WHERE message_id = 11").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        listOf("tool_connections", "agent_tool_bindings", "agent_runs", "tool_events").forEach { table ->
            database.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
        database.execSQL(
            "INSERT INTO agent_tool_bindings (binding_uid, profile_uid, connection_uid, tool_name, created_at) " +
                "VALUES ('binding-1', 'profile-1', NULL, 'read_url', 1)"
        )
        var duplicateRejected = false
        try {
            database.execSQL(
                "INSERT INTO agent_tool_bindings (binding_uid, profile_uid, connection_uid, tool_name, created_at) " +
                    "VALUES ('binding-2', 'profile-1', NULL, 'read_url', 2)"
            )
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            duplicateRejected = true
        }
        assertTrue(duplicateRejected)
        database.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7_preservesDuplicateLegacyProfileUids() {
        helper.createDatabase(DUPLICATE_PROFILE_DATABASE, 6).apply {
            repeat(2) { index ->
                execSQL(
                    """
                    INSERT INTO platform_v2 (
                        platform_id, uid, name, compatible_type, enabled, api_url, token, model,
                        temperature, top_p, system_prompt, stream, reasoning, timeout,
                        harassment_safety_threshold, hate_speech_safety_threshold,
                        sexually_explicit_safety_threshold, dangerous_content_safety_threshold
                    ) VALUES (
                        ${index + 1}, 'duplicate-profile', 'Profile ${index + 1}', 'OPENAI', 1,
                        'https://api.openai.com/v1/', 'secret-$index', 'gpt-5', NULL, NULL, NULL,
                        1, 0, 30, 'BLOCK_NONE', 'BLOCK_NONE', 'BLOCK_NONE', 'BLOCK_NONE'
                    )
                    """.trimIndent()
                )
            }
            close()
        }

        val database = helper.runMigrationsAndValidate(
            DUPLICATE_PROFILE_DATABASE,
            7,
            true,
            ChatDatabaseV2Migrations.MIGRATION_6_7
        )

        database.query("SELECT COUNT(*) FROM platform_v2 WHERE uid = 'duplicate-profile'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        database.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8_marksOnlyAmbiguousLegacyAssistantOrderWithoutChangingMessages() {
        helper.createDatabase(TIMELINE_DATABASE, 7).apply {
            execSQL(
                "INSERT INTO chats_v2 (chat_id, title, enabled_platform, created_at, updated_at) " +
                    "VALUES (8, 'Timeline chat', '', 100, 101)"
            )
            execSQL(
                """
                INSERT INTO messages_v2 (
                    message_id, chat_id, thoughts, content, attachments, revisions,
                    active_revision_index, linked_message_id, platform_type, current_run_id, created_at
                ) VALUES (11, 8, '', 'Question', '[]', '[]', -1, 0, NULL, NULL, 101)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO messages_v2 (
                    message_id, chat_id, thoughts, content, attachments, revisions,
                    active_revision_index, linked_message_id, platform_type, current_run_id, created_at
                ) VALUES (12, 8, 'Checking', 'Existing answer', '[]', '[]', -1, 11, 'profile-1', 'run-1', 102)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO messages_v2 (
                    message_id, chat_id, thoughts, content, attachments, revisions,
                    active_revision_index, linked_message_id, platform_type, current_run_id, created_at
                ) VALUES (13, 8, '', 'Plain answer', '[]', '[]', -1, 11, 'profile-2', NULL, 103)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO agent_runs (
                    run_id, chat_id, user_message_id, assistant_message_id, profile_uid,
                    provider_snapshot, model_snapshot, status, created_at
                ) VALUES ('run-1', 8, 11, 12, 'profile-1', 'OPENAI', 'model', 'COMPLETED', 101)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO tool_events (
                    event_id, run_id, sequence, call_id, connection_uid_snapshot,
                    connection_name_snapshot, tool_name, model_tool_name, arguments,
                    result, result_type, status, is_error
                ) VALUES (
                    'event-1', 'run-1', 0, 'call-1', NULL, NULL, 'search', 'search', '{}',
                    'Tool result', 'TEXT', 'COMPLETED', 0
                )
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TIMELINE_DATABASE,
            8,
            true,
            ChatDatabaseV2Migrations.MIGRATION_7_8
        )

        database.query(
            "SELECT thoughts, content, current_run_id, timeline FROM messages_v2 WHERE message_id = 12"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Checking", cursor.getString(0))
            assertEquals("Existing answer", cursor.getString(1))
            assertEquals("run-1", cursor.getString(2))
            assertEquals("""[{"type":"LEGACY_ORDER"}]""", cursor.getString(3))
        }
        database.query("SELECT content, timeline FROM messages_v2 WHERE message_id = 13").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Plain answer", cursor.getString(0))
            assertEquals("[]", cursor.getString(1))
        }
        database.query("SELECT sequence, result FROM tool_events WHERE run_id = 'run-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals("Tool result", cursor.getString(1))
        }
        database.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7_cascadesConnectionBindingsAndRunHistoryAtTheirOwners() {
        helper.createDatabase(CASCADE_DATABASE, 6).close()
        val database = helper.runMigrationsAndValidate(
            CASCADE_DATABASE,
            7,
            true,
            ChatDatabaseV2Migrations.MIGRATION_6_7
        )
        database.execSQL("PRAGMA foreign_keys = ON")
        database.execSQL(
            "INSERT INTO chats_v2 (chat_id, title, enabled_platform, created_at, updated_at) VALUES (1, 'Chat', 'profile-1', 1, 1)"
        )
        database.execSQL(
            """
            INSERT INTO platform_v2 (
                platform_id, uid, name, compatible_type, enabled, api_url, token, secret_ref,
                model, temperature, top_p, system_prompt, stream, reasoning, timeout,
                harassment_safety_threshold, hate_speech_safety_threshold,
                sexually_explicit_safety_threshold, dangerous_content_safety_threshold
            ) VALUES (
                1, 'profile-1', 'OpenAI', 'OPENAI', 1, 'https://api.openai.com/v1/', NULL, NULL,
                'gpt-5', NULL, NULL, NULL, 1, 0, 30,
                'BLOCK_NONE', 'BLOCK_NONE', 'BLOCK_NONE', 'BLOCK_NONE'
            )
            """.trimIndent()
        )
        database.execSQL(
            "INSERT INTO messages_v2 (message_id, chat_id, thoughts, content, attachments, revisions, active_revision_index, linked_message_id, platform_type, created_at, current_run_id) VALUES (1, 1, '', 'Q', '', '[]', -1, 0, NULL, 1, NULL)"
        )
        database.execSQL(
            "INSERT INTO messages_v2 (message_id, chat_id, thoughts, content, attachments, revisions, active_revision_index, linked_message_id, platform_type, created_at, current_run_id) VALUES (2, 1, '', '', '', '[]', -1, 0, 'profile-1', 1, 'run-1')"
        )
        database.execSQL(
            "INSERT INTO tool_connections (connection_uid, name, alias, type, endpoint_url, auth_type, secret_ref, oauth_client_id, allow_cleartext, created_at, updated_at) VALUES ('connection-1', 'Fixture', 'fixture', 'MCP', 'https://example.test/mcp', 'NONE', NULL, NULL, 0, 1, 1)"
        )
        database.execSQL(
            "INSERT INTO agent_tool_bindings (binding_uid, profile_uid, connection_uid, tool_name, created_at) VALUES ('binding-1', 'profile-1', 'connection-1', 'lookup', 1)"
        )
        database.execSQL(
            "INSERT INTO agent_tool_bindings (binding_uid, profile_uid, connection_uid, tool_name, created_at) VALUES ('binding-2', 'profile-1', NULL, 'read_url', 1)"
        )
        database.execSQL(
            "INSERT INTO agent_runs (run_id, chat_id, user_message_id, assistant_message_id, profile_uid, provider_snapshot, model_snapshot, status, created_at, started_at, completed_at, terminal_error) VALUES ('run-1', 1, 1, 2, 'profile-1', 'OPENAI', 'gpt-5', 'COMPLETED', 1, 1, 2, NULL)"
        )
        database.execSQL(
            "INSERT INTO tool_events (event_id, run_id, sequence, call_id, connection_uid_snapshot, connection_name_snapshot, tool_name, model_tool_name, arguments, result, result_type, status, is_error, started_at, completed_at, error) VALUES ('event-1', 'run-1', 0, 'call-1', 'connection-1', 'Fixture', 'lookup', 'fixture__lookup', '{}', 'ok', 'TEXT', 'COMPLETED', 0, 1, 2, NULL)"
        )

        database.execSQL("DELETE FROM tool_connections WHERE connection_uid = 'connection-1'")
        assertCount(database, "agent_tool_bindings", 1)
        assertCount(database, "tool_events", 1)

        database.execSQL("DELETE FROM chats_v2 WHERE chat_id = 1")
        assertCount(database, "agent_runs", 0)
        assertCount(database, "tool_events", 0)
        database.close()
    }

    private fun assertCount(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        expected: Int
    ) {
        database.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DATABASE = "agent-migration-test"
        const val DUPLICATE_PROFILE_DATABASE = "agent-migration-duplicate-profile-test"
        const val CASCADE_DATABASE = "agent-migration-cascade-test"
        const val TIMELINE_DATABASE = "agent-migration-timeline-test"
        const val BROKEN_VERSION_2_DATABASE = "agent-migration-broken-v2-test"
    }
}
