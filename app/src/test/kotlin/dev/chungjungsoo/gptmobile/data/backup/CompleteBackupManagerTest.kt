package dev.chungjungsoo.gptmobile.data.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.Room
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.AgentToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.OpenRouterBatchCacheEntity
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CompleteBackupManagerTest {
    private lateinit var context: Context
    private lateinit var database: ChatDatabaseV2
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vault = TestVault()
    private val settings = mockk<SettingRepository>(relaxed = true)
    private val legacy = mockk<AppBackupManager>(relaxed = true)
    private lateinit var preferences: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var manager: CompleteBackupManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        database = Room.databaseBuilder(context, ChatDatabaseV2::class.java, "test-${UUID.randomUUID()}.db").build()
        preferences = PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb") }
        every { legacy.getBackupStatus() } returns BackupStatus()
        manager = CompleteBackupManager(context, database, preferences, vault, settings, legacy)
    }

    @After
    fun cleanup() {
        database.close()
        scope.cancel()
    }

    @Test
    fun completeRoundTripRestoresEveryTablePreferencesCredentialsAndPortableFiles() = runBlocking {
        val attachment = File(context.cacheDir, "photo.txt").apply { writeText("attachment data") }
        seed(attachment)
        preferences.edit {
            it[booleanPreferencesKey("gateway_stable")] = false
            it[intPreferencesKey("soft_rounds")] = 0
            it[stringSetPreferencesKey("favorite_groups")] = setOf("work", "personal")
        }
        context.getSharedPreferences("llama_settings", 0).edit().putString("advanced_settings", "{\"stableToolSurface\":false}").commit()
        vault.put("provider", "provider-token".toByteArray())
        vault.put("tool", "tool-token".toByteArray())
        val archive = File(context.cacheDir, "complete.gptbackup")
        val saved = manager.backup(Uri.fromFile(archive), CompleteBackupSelection.ALL, password = "test-password")
        assertTrue(saved.message, saved.success)
        // Simulate changed data and a different attachment location/device.
        database.chatRoomDao().updateTitle(7, "changed", true)
        database.agentRunDao().updateStatus("run", "COMPLETED", null, null, null)
        preferences.edit {
            it.clear()
            it[intPreferencesKey("new_setting")] = 99
        }
        context.getSharedPreferences("llama_settings", 0).edit().clear().commit()
        vault.put("provider", "changed-token".toByteArray())
        vault.put("extra", "extra-token".toByteArray())
        attachment.delete()
        File(context.filesDir, "remove.txt").writeText("newer data")
        val restored = manager.restore(Uri.fromFile(archive), legacyPassword = "test-password")
        assertTrue(restored.message, restored.success)
        val chat = database.chatRoomDao().getChatRooms().single()
        assertEquals("saved", chat.title)
        assertTrue(chat.isFavorite)
        assertEquals("draft", chat.draftText)
        val message = database.messageDao().loadMessages(7).first { it.id == 11 }
        assertTrue(message.isFavorite)
        assertEquals("attachment data", File(message.attachments.single().localFilePath).readText())
        assertTrue(message.attachments.single().localFilePath.startsWith(context.filesDir.absolutePath))
        assertEquals("provider-token", vault.read("provider")!!.decodeToString())
        assertEquals("tool-token", vault.read("tool")!!.decodeToString())
        assertFalse("extra" in vault.references())
        assertEquals(false, preferences.data.first()[booleanPreferencesKey("gateway_stable")])
        assertEquals(0, preferences.data.first()[intPreferencesKey("soft_rounds")])
        assertEquals(setOf("work", "personal"), preferences.data.first()[stringSetPreferencesKey("favorite_groups")])
        assertEquals(null, preferences.data.first()[intPreferencesKey("new_setting")])
        assertEquals("{\"stableToolSurface\":false}", context.getSharedPreferences("llama_settings", 0).getString("advanced_settings", null))
        // Restoring selected portable files must not erase unrelated app data.
        assertEquals("newer data", File(context.filesDir, "remove.txt").readText())
        assertEquals("model", database.chatPlatformModelDao().getByChatId(7).single().model)
        assertEquals("READY", database.localModelDao().getAll().single().status)
        assertEquals("model bytes", File(context.getExternalFilesDir(null), "models/local/revision/model.bin").readText())
        assertEquals("cached", database.openRouterBatchCacheDao().getByCacheKey("key")!!.responseContent)
        assertEquals("tool", database.toolConnectionDao().getAllConnections().single().secretRef)
        assertEquals("write_file", database.toolConnectionDao().listBindingsByProfile("profile").single().toolName)
        assertEquals("provider", database.platformDao().getPlatforms().single().secretRef)
        assertEquals("INTERRUPTED", database.agentRunDao().getById("run")!!.status)
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.query("SELECT status FROM tool_events").use {
                assertTrue(it.moveToFirst())
                assertEquals("CANCELED", it.getString(0))
            }
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        verify { settings.invalidatePlatformCache() }
    }

    @Test
    fun modernBackupWithDifferentColumnOrderAndOldRoomIdentityRestoresIntoFreshSchema() = runBlocking {
        seed(File(context.cacheDir, "modern-file").apply { writeText("original") })
        val archive = File(context.cacheDir, "modern.gptbackup")
        assertTrue(backupFixture(archive).success)
        val stage = File(context.cacheDir, "rewrite-${UUID.randomUUID()}").apply { mkdirs() }
        val manifest = CompleteBackupArchive.read(archive, stage, Long.MAX_VALUE)
        android.database.sqlite.SQLiteDatabase.openDatabase(File(stage, "database.sqlite").absolutePath, null, 0).use { db ->
            db.execSQL("UPDATE room_master_table SET identity_hash = 'old-installation-identity'")
            db.execSQL("CREATE TABLE reordered_cache (timestamp INTEGER NOT NULL, response_content TEXT NOT NULL, cache_key TEXT NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, retired_option TEXT)")
            db.execSQL("INSERT INTO reordered_cache(timestamp, response_content, cache_key, id) SELECT timestamp, response_content, cache_key, id FROM openrouter_batch_cache")
            db.execSQL("DROP TABLE openrouter_batch_cache")
            db.execSQL("ALTER TABLE reordered_cache RENAME TO openrouter_batch_cache")
        }
        val sources = manifest.files.mapValues { (path, _) -> File(stage, path) }
        CompleteBackupArchive.write(archive, manifest.copy(files = sources.mapValues { it.value.length() }), sources)
        database.agentRunDao().updateStatus("run", "COMPLETED", null, null, null)
        database.chatRoomDao().updateTitle(7, "changed", true)
        val result = manager.restore(Uri.fromFile(archive))
        assertTrue(result.message, result.success)
        assertEquals("saved", database.chatRoomDao().getChatRooms().single().title)
        assertEquals("cached", database.openRouterBatchCacheDao().getByCacheKey("key")!!.responseContent)
        stage.deleteRecursively()
        Unit
    }

    @Test
    fun olderModernBackupMigratesNewTablesAndColumnsBeforeRestore() = runBlocking {
        seed(File(context.cacheDir, "older-file").apply { writeText("original") })
        val archive = File(context.cacheDir, "older.gptbackup")
        assertTrue(backupFixture(archive).success)
        val stage = File(context.cacheDir, "older-${UUID.randomUUID()}").apply { mkdirs() }
        val manifest = CompleteBackupArchive.read(archive, stage, Long.MAX_VALUE)
        android.database.sqlite.SQLiteDatabase.openDatabase(File(stage, "database.sqlite").absolutePath, null, 0).use { db ->
            fun withoutColumns(table: String, omitted: Set<String>) {
                val definitions = mutableListOf<String>()
                val names = mutableListOf<String>()
                db.rawQuery("PRAGMA table_info(`$table`)", null).use { rows ->
                    while (rows.moveToNext()) {
                        val name = rows.getString(1)
                        if (name in omitted) continue
                        names += "`$name`"
                        definitions += "`$name` ${rows.getString(2)}" +
                            (if (rows.getInt(3) != 0) " NOT NULL" else "") +
                            (if (rows.getInt(5) != 0) " PRIMARY KEY" else "") +
                            (rows.getString(4)?.let { " DEFAULT $it" } ?: "")
                    }
                }
                db.execSQL("CREATE TABLE older_table (${definitions.joinToString()})")
                db.execSQL("INSERT INTO older_table (${names.joinToString()}) SELECT ${names.joinToString()} FROM `$table`")
                db.execSQL("DROP TABLE `$table`")
                db.execSQL("ALTER TABLE older_table RENAME TO `$table`")
            }
            withoutColumns("tool_connections", setOf("tool_policy", "approved_read_tools"))
            withoutColumns("knowledge_documents", setOf("deleted"))
            val triggers = db.rawQuery("SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'room_fts_content_sync_messages_search_%'", null).use { rows ->
                buildList { while (rows.moveToNext()) add(rows.getString(0)) }
            }
            triggers.forEach { db.execSQL("DROP TRIGGER `$it`") }
            db.execSQL("DROP TABLE messages_search")
            db.execSQL("DROP TABLE tool_approvals")
            db.execSQL("DROP TABLE model_invocations")
            db.version = 29
        }
        val sources = manifest.files.mapValues { (path, _) -> File(stage, path) }
        CompleteBackupArchive.write(archive, manifest.copy(files = sources.mapValues { it.value.length() }), sources)
        database.agentRunDao().updateStatus("run", "COMPLETED", null, null, null)
        database.chatRoomDao().updateTitle(7, "changed", true)
        val result = manager.restore(Uri.fromFile(archive))
        assertTrue(result.message, result.success)
        assertEquals("saved", database.chatRoomDao().getChatRooms().single().title)
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        stage.deleteRecursively()
        Unit
    }

    @Test
    fun version30BackupAddsProfileAttributionWithoutLosingRequests() = runBlocking {
        seed(File(context.cacheDir, "v30-file").apply { writeText("original") })
        val archive = File(context.cacheDir, "v30.gptbackup")
        val selection = CompleteBackupSelection().toggled(CompleteBackupSection.AGENT_HISTORY, true)
        assertTrue(backupFixture(archive, selection).success)
        val stage = File(context.cacheDir, "v30-${UUID.randomUUID()}").apply { mkdirs() }
        val manifest = CompleteBackupArchive.read(archive, stage, Long.MAX_VALUE)
        android.database.sqlite.SQLiteDatabase.openDatabase(File(stage, "database.sqlite").absolutePath, null, 0).use { db ->
            db.execSQL("DROP TABLE model_invocations")
            db.execSQL("CREATE TABLE model_invocations (id TEXT NOT NULL PRIMARY KEY, parentRunId TEXT NOT NULL, turnKey TEXT NOT NULL, provider TEXT NOT NULL, model TEXT NOT NULL, kind TEXT NOT NULL, inputTokens INTEGER NOT NULL, outputTokens INTEGER NOT NULL, estimated INTEGER NOT NULL, status TEXT NOT NULL, startedAt INTEGER NOT NULL, durationMs INTEGER NOT NULL, firstTokenMs INTEGER)")
            db.execSQL("INSERT INTO model_invocations VALUES ('primary', 'run', 'turn', 'provider', 'model', 'primary', 10, 20, 0, 'COMPLETED', 1000, 1500, 200)")
            db.execSQL("INSERT INTO model_invocations VALUES ('delegate', 'run', 'turn', 'provider', 'model', 'delegate', 10, 20, 0, 'COMPLETED', 1000, 1500, 200)")
            db.version = 30
        }
        val sources = manifest.files.mapValues { (path, _) -> File(stage, path) }
        CompleteBackupArchive.write(archive, manifest.copy(files = sources.mapValues { it.value.length() }), sources)
        database.agentRunDao().updateStatus("run", "COMPLETED", null, null, null)
        val result = manager.restore(Uri.fromFile(archive))
        assertTrue(result.message, result.success)
        val records = database.invocationDao().statistics().first().associateBy { it.id }
        assertEquals(2, records.size)
        assertEquals("profile", records.getValue("primary").profileUid)
        assertEquals(null, records.getValue("delegate").profileUid)
        assertEquals(20, records.getValue("primary").outputTokens)
        stage.deleteRecursively()
        Unit
    }

    @Test
    fun corruptBackupAndCredentialWriteFailureLeaveExistingStateIntact() = runBlocking {
        seed(File(context.cacheDir, "file").apply { writeText("old attachment") })
        vault.put("provider", "saved-token".toByteArray())
        val archive = File(context.cacheDir, "complete.gptbackup")
        assertTrue(manager.backup(Uri.fromFile(archive), CompleteBackupSelection.ALL, password = "test-password").success)
        database.agentRunDao().updateStatus("run", "COMPLETED", null, null, null)
        database.chatRoomDao().updateTitle(7, "current", true)
        preferences.edit { it[intPreferencesKey("current")] = 7 }
        vault.put("provider", "current-token".toByteArray())
        File(context.filesDir, "current.txt").writeText("current file")
        val encryptedBytes = archive.readBytes()
        archive.writeBytes(
            encryptedBytes.copyOf().also { bytes ->
                bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            }
        )
        assertFalse(manager.restore(Uri.fromFile(archive), legacyPassword = "test-password").success)
        archive.writeBytes(encryptedBytes)
        vault.failNextPut = true
        assertFalse(manager.restore(Uri.fromFile(archive), legacyPassword = "test-password").success)
        assertEquals("current", database.chatRoomDao().getChatRooms().single().title)
        assertEquals(7, preferences.data.first()[intPreferencesKey("current")])
        assertEquals("current-token", vault.read("provider")!!.decodeToString())
        assertEquals("current file", File(context.filesDir, "current.txt").readText())
    }

    @Test
    fun passwordlessBackupsAreAlwaysPortableAndNeverCreateInstallationBoundKeys() = runBlocking {
        seed(File(context.cacheDir, "portable-file").apply { writeText("portable") })
        val archive = File(context.cacheDir, "portable.gptbackup")

        val keyFile = File(context.cacheDir, "portable.gptkey")
        val saved = manager.backup(Uri.fromFile(archive), recoveryKeyUri = Uri.fromFile(keyFile))
        assertTrue(saved.message, saved.success)

        val header = archive.inputStream().use { input -> ByteArray(8).also { input.read(it) } }
        assertEquals("GPTFULL3", header.decodeToString())
        assertTrue(keyFile.exists())
        assertFalse("Passwordless backups must never use GPTFULL2.", header.decodeToString().startsWith("GPTFULL2"))
        assertFalse("No installation-bound backup master key may be created.", "complete_backup_master_v2" in vault.references())
        assertFalse(manager.requiresPassword(Uri.fromFile(archive)))
        assertFalse(manager.requiresRecoveryKey(Uri.fromFile(archive)))
        vault.references().filter { it.startsWith("backup-recovery-") }.forEach { vault.delete(it) }
        assertTrue(manager.requiresRecoveryKey(Uri.fromFile(archive)))
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("UPDATE agent_runs SET status = 'INTERRUPTED'") }
        val recovered = manager.restore(Uri.fromFile(archive), recoveryKeyUri = Uri.fromFile(keyFile))
        assertTrue(recovered.message, recovered.success)

        val protected = File(context.cacheDir, "portable-protected.gptbackup")
        val protectedSaved = manager.backup(Uri.fromFile(protected), password = "portable-password")
        assertTrue(protectedSaved.message, protectedSaved.success)
        assertEquals("GPTFULL1", protected.inputStream().use { input -> ByteArray(8).also { input.read(it) } }.decodeToString())
        assertTrue(manager.requiresPassword(Uri.fromFile(protected)))
        assertFalse("Password encryption must also be independent of an installation key.", "complete_backup_master_v2" in vault.references())
    }

    @Test
    fun defaultsNeverExportCredentialsOrMemoryAndEncryptionRequiresRecoveryMaterial() = runBlocking {
        seed(File(context.cacheDir, "default-file").apply { writeText("file") })
        vault.put("provider", "sentinel-original-token".toByteArray())
        vault.put(dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository.VAULT_REFERENCE, "sentinel-memory".toByteArray())
        val archive = File(context.cacheDir, "default.gptbackup")
        assertTrue(backupFixture(archive).success)
        java.util.zip.ZipFile(archive).use { zip ->
            val manifest = zip.entries().asSequence().first { it.name.endsWith("manifest.json") }
            val contents = zip.getInputStream(manifest).bufferedReader().readText()
            assertFalse(contents.contains("sentinel-original-token"))
            assertFalse(contents.contains("sentinel-memory"))
            assertFalse(contents.contains(java.util.Base64.getEncoder().encodeToString("sentinel-original-token".toByteArray())))
        }
        vault.put("provider", "new-token".toByteArray())
        vault.put(dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository.VAULT_REFERENCE, "new-memory".toByteArray())
        assertFalse(manager.restore(Uri.fromFile(archive)).success)
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("UPDATE agent_runs SET status = 'INTERRUPTED'") }
        val restored = manager.restore(Uri.fromFile(archive))
        assertTrue(restored.message, restored.success)
        assertEquals("new-token", vault.read("provider")!!.decodeToString())
        assertEquals("new-memory", vault.read(dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository.VAULT_REFERENCE)!!.decodeToString())
        assertFalse(manager.backup(Uri.fromFile(archive), CompleteBackupSelection(setOf(CompleteBackupSection.MEMORY))).success)
        assertFalse(manager.backup(Uri.fromFile(archive), CompleteBackupSelection(setOf(CompleteBackupSection.CREDENTIALS))).success)
    }

    @Test
    fun memoryShardsFollowMemorySelectionAndNeverCredentialSelection() = runBlocking {
        val memory = dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository(vault, dev.chungjungsoo.gptmobile.data.rag.KnowledgeGraphEngine())
        memory.load()
        repeat(40) { memory.saveManual("Memory $it " + "x".repeat(890)) }
        assertTrue(vault.references().any { it.startsWith("memory-part-") })
        vault.put("provider", "first-token".toByteArray())
        val credentials = File(context.cacheDir, "credentials-only.gptbackup")
        val credentialsSaved = manager.backup(Uri.fromFile(credentials), CompleteBackupSelection(setOf(CompleteBackupSection.CREDENTIALS)), password = "passphrase")
        assertTrue(credentialsSaved.message, credentialsSaved.success)
        val memoryArchive = File(context.cacheDir, "memory-only.gptbackup")
        val memorySaved = manager.backup(Uri.fromFile(memoryArchive), CompleteBackupSelection(setOf(CompleteBackupSection.MEMORY)), password = "passphrase")
        assertTrue(memorySaved.message, memorySaved.success)
        memory.clear()
        val emptyKeys = vault.references().filter { it.startsWith("memory-part-") }.toSet()
        assertTrue(emptyKeys.isEmpty())
        assertTrue(manager.restore(Uri.fromFile(credentials), legacyPassword = "passphrase").success)
        assertEquals(emptyKeys, vault.references().filter { it.startsWith("memory-part-") }.toSet())
        memory.load()
        assertTrue(memory.state.value.facts.isEmpty())
        assertTrue(manager.restore(Uri.fromFile(memoryArchive), legacyPassword = "passphrase").success)
        memory.load()
        assertEquals(40, memory.state.value.facts.size)
        assertEquals("first-token", vault.read("provider")!!.decodeToString())
    }

    /** Structural migration tests inspect a decrypted archive, never a plaintext export. */
    private suspend fun backupFixture(archive: File, selection: CompleteBackupSelection = CompleteBackupSelection()): BackupRestoreResult {
        val keyFile = File(context.cacheDir, "${UUID.randomUUID()}.gptkey")
        val result = manager.backup(Uri.fromFile(archive), selection, recoveryKeyUri = Uri.fromFile(keyFile))
        if (result.success) {
            val key = keyFile.inputStream().use(CompleteBackupCrypto::readRecoveryKey)
            try {
                val decoded = File(context.cacheDir, "${UUID.randomUUID()}.zip")
                archive.inputStream().use { CompleteBackupCrypto.decryptPortable(it, decoded, key) }
                decoded.copyTo(archive, overwrite = true)
                decoded.delete()
            } finally {
                key.fill(0)
                keyFile.delete()
            }
        }
        return result
    }

    private suspend fun seed(attachment: File) {
        database.chatRoomDao().addChatRoom(ChatRoomV2(id = 7, title = "saved", enabledPlatform = listOf("profile"), isFavorite = true, draftText = "draft"))
        database.messageDao().addMessages(
            MessageV2(
                id = 11,
                chatId = 7,
                content = "question",
                platformType = null,
                isFavorite = true,
                attachments = listOf(ChatAttachment(attachment.absolutePath, "", "photo.txt", "text/plain", attachment.length()))
            ),
            MessageV2(id = 12, chatId = 7, content = "answer", platformType = "profile")
        )
        database.platformDao().addPlatform(PlatformV2(id = 9, uid = "profile", name = "saved", secretRef = "provider"))
        database.chatPlatformModelDao().upsertChatPlatformModel(ChatPlatformModelV2(7, "profile", "model"))
        database.toolConnectionDao().upsertConnection(
            dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection(
                "connection",
                "GitHub",
                "github",
                "MCP",
                "https://example.com/mcp",
                "BEARER",
                "tool",
                null
            )
        )
        database.toolConnectionDao().insertBinding(AgentToolBinding("binding", "profile", "connection", "write_file"))
        database.agentRunDao().upsert(AgentRun("run", 7, 11, 12, "profile", "{}", "model", status = "RUNNING"))
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL("INSERT INTO tool_events(event_id, run_id, sequence, call_id, tool_name, model_tool_name, arguments, status, is_error) VALUES ('event', 'run', 1, 'call', 'write_file', 'write_file', '{}', 'RUNNING', 0)")
        }
        File(context.getExternalFilesDir(null), "models/local/revision/model.bin").apply {
            parentFile!!.mkdirs()
            writeText("model bytes")
        }
        database.localModelDao().upsert(LocalModel("local", "revision", "model.bin", "models/local/revision", 11, "READY"))
        database.openRouterBatchCacheDao().insertOrUpdate(OpenRouterBatchCacheEntity(cacheKey = "key", responseContent = "cached"))
    }

    private class TestVault : SecretVault {
        private val values = mutableMapOf<String, ByteArray>()
        var failNextPut = false
        override suspend fun put(secretRef: String, secret: ByteArray) {
            if (failNextPut) {
                failNextPut = false
                error("Injected credential write failure")
            }
            values[secretRef] = secret.copyOf()
        }
        override suspend fun read(secretRef: String) = values[secretRef]?.copyOf()
        override suspend fun delete(secretRef: String) {
            values.remove(secretRef)
        }
        override suspend fun references() = values.keys.toSet()
    }
}
