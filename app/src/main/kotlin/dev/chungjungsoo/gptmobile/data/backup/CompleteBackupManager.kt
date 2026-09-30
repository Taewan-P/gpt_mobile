package dev.chungjungsoo.gptmobile.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2Migrations
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class CompleteBackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: ChatDatabaseV2,
    dataStore: DataStore<Preferences>,
    private val secretVault: SecretVault,
    private val settings: SettingRepository,
    private val legacy: AppBackupManager
) {
    private val mutex = Mutex()
    private val preferences = CompleteBackupPreferences(context, dataStore)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private fun files() = CompleteBackupFiles(mapOf("internal" to context.filesDir, "external" to (context.getExternalFilesDir(null) ?: context.filesDir)))
    fun getBackupStatus() = legacy.getBackupStatus()

    fun savedSelection(): CompleteBackupSelection {
        val raw = context.getSharedPreferences("complete_backup_ui_v1", Context.MODE_PRIVATE)
            .getStringSet("sections", null)
            ?: return CompleteBackupSelection()
        val sections = raw.mapNotNull { runCatching { CompleteBackupSection.valueOf(it) }.getOrNull() }.toSet()
        return CompleteBackupSelection(sections).normalized()
    }

    fun saveSelection(selection: CompleteBackupSelection) {
        val names = selection.normalized().sections.mapTo(mutableSetOf()) { it.name }
        check(
            context.getSharedPreferences("complete_backup_ui_v1", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("sections", names)
                .commit()
        ) { "Could not save backup selection." }
    }


    suspend fun backup(
        uri: Uri,
        selection: CompleteBackupSelection = CompleteBackupSelection(),
        password: String? = null
    ): BackupRestoreResult = operation { work ->
        val selected = selection.normalized()
        require(selected.sections.isNotEmpty()) { "Select at least one backup section." }

        require(!selected.requiresEncryption || (password?.length ?: 0) >= 8) {
            "Choose a password of at least 8 characters to export credentials or memory."
        }

        val storage = files()
        val sources = linkedMapOf<String, File>()
        val databaseSelected = selected.sections.any(::isDatabaseSection)

        if (databaseSelected) {
            val dbFile = File(work, "database.sqlite")
            database.withTransaction {
                ensureIdle(restoring = false)
                dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository(database.toolConnectionDao(), secretVault).listConnections()
                CompleteBackupDatabase.snapshot(database.openHelper.writableDatabase, dbFile)
            }

            val snapshot = openSnapshot(dbFile)
            try {
                val snapshotDb = snapshot.openHelper.writableDatabase
                CompleteBackupDatabase.retainSections(snapshotDb, selected)

                if (CompleteBackupSection.CONVERSATIONS in selected.sections) {
                    if (CompleteBackupSection.ATTACHMENTS in selected.sections) {
                        rewriteAttachments(snapshotDb) { path ->
                            storage.archivePath(path, sources)
                        }
                    } else {
                        snapshotDb.execSQL("UPDATE messages_v2 SET attachments = '[]'")
                        snapshotDb.query("SELECT id, payload FROM pending_prompts").use { rows ->
                            while (rows.moveToNext()) {
                                val payload = json.decodeFromString<dev.chungjungsoo.gptmobile.data.queue.PendingPromptPayload>(rows.getString(1))
                                snapshotDb.execSQL("UPDATE pending_prompts SET payload = ? WHERE id = ?", arrayOf<Any>(json.encodeToString(payload.copy(attachments = emptyList())), rows.getString(0)))
                            }
                        }
                    }
                }

                if (CompleteBackupSection.LOCAL_MODELS in selected.sections) {
                    storage.collect()
                        .filterKeys(::isModelArchivePath)
                        .forEach { (archivePath, source) -> sources.putIfAbsent(archivePath, source) }
                    validateModels(snapshotDb, sources.keys)
                }
            } finally {
                snapshot.close()
            }
            sources["database.sqlite"] = dbFile
        }

        val manifest = CompleteBackupManifest(
            preferences = if (CompleteBackupSection.SETTINGS in selected.sections) preferences.read() else emptyMap(),
            sharedPreferences = if (CompleteBackupSection.SETTINGS in selected.sections) preferences.readShared() else emptyMap(),
            secrets = readSecrets(selected),
            files = sources.mapValues { it.value.length() },
            sections = selected.sections.mapTo(linkedSetOf()) { it.name }
        )

        val archive = File(work, "archive.zip")
        CompleteBackupArchive.write(archive, manifest, sources)

        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            val protectionPassword = password?.takeIf(String::isNotBlank)
            if (protectionPassword != null) {
                CompleteBackupCrypto.encrypt(archive, output, protectionPassword)
            } else {
                val backupKey = getOrCreateBackupKey()
                try {
                    CompleteBackupCrypto.encryptWithKey(archive, output, backupKey)
                } finally {
                    backupKey.fill(0)
                }
            }
        } ?: error("Could not open the backup destination.")

        legacy.recordBackupMetadata()
        BackupRestoreResult(
            true,
            if (password.isNullOrBlank()) {
                "Encrypted backup saved."
            } else {
                "Password-encrypted backup saved."
            }
        )
    }

    suspend fun restore(
        uri: Uri,
        legacyPassword: String? = null,
        selection: CompleteBackupSelection = CompleteBackupSelection.ALL
    ): BackupRestoreResult = operation { work ->
        val requested = selection.normalized()
        require(requested.sections.isNotEmpty()) { "Select at least one restore section." }
        ensureIdle(restoring = true)

        val archive = File(work, "archive.zip")
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            input.mark(64)
            val header = ByteArray(9)
            val headerBytes = input.read(header)
            input.reset()

            if (headerBytes >= 9 && header.copyOfRange(0, 7).decodeToString() == "GPTBKUP") {
                val result = when (header[8].toInt()) {
                    1 -> legacy.restoreConfiguration(uri, legacyPassword?.takeIf(String::isNotEmpty))
                    2 -> legacy.restoreDatabase(uri, legacyPassword?.takeIf(String::isNotEmpty))
                    4 -> legacy.importFavorites(uri, legacyPassword?.takeIf(String::isNotEmpty))
                    else -> error("Unsupported legacy backup type.")
                }
                settings.invalidatePlatformCache()
                return@operation result.copy(message = "Legacy backup: ${result.message}")
            }

            val isLegacyComplete = headerBytes >= 8 &&
                header.copyOfRange(0, 8).decodeToString() == "GPTFULL1"
            val isPasswordlessComplete = headerBytes >= 8 &&
                header.copyOfRange(0, 8).decodeToString() == "GPTFULL2"
            val isZip = headerBytes >= 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()

            when {
                isPasswordlessComplete -> {
                    val backupKey = requireExistingBackupKey()
                    try {
                        CompleteBackupCrypto.decryptWithKey(input, archive, backupKey, work.usableSpace - RESERVE)
                    } finally {
                        backupKey.fill(0)
                    }
                }
                isLegacyComplete -> {
                    val password = legacyPassword?.takeIf(String::isNotBlank)
                        ?: error("This backup is password protected. Enter its password.")
                    CompleteBackupCrypto.decrypt(input, archive, password, work.usableSpace - RESERVE)
                }
                isZip -> copyArchiveWithLimit(input, archive, work.usableSpace - RESERVE)
                else -> error("Select a GPT Mobile backup file.")
            }
        } ?: error("Could not read the backup file.")

        val staging = File(work, "files").apply { mkdirs() }
        val manifest = CompleteBackupArchive.read(archive, staging, work.usableSpace - RESERVE)
        val available = manifestSelection(manifest)
        val effective = CompleteBackupSelection(
            requested.sections.intersect(available.sections)
        ).normalized()

        if (effective.sections.isEmpty()) {
            return@operation BackupRestoreResult(true, "None of the selected sections exist in this backup.")
        }

        val restoreSettings = CompleteBackupSection.SETTINGS in effective.sections
        val restoreSecrets = effective.requiresEncryption
        val selectedSecrets = manifest.secrets.filterKeys { secretBelongsTo(it, effective) }
        val restoreAttachments = CompleteBackupSection.ATTACHMENTS in effective.sections
        val restoreModels = CompleteBackupSection.LOCAL_MODELS in effective.sections
        val restoreDatabase = effective.sections.any(::isDatabaseSection) &&
            "database.sqlite" in manifest.files

        if (restoreSettings) {
            preferences.validate(manifest.preferences, manifest.sharedPreferences)
        }
        if (restoreSecrets) {
            validateSecrets(selectedSecrets)
        }

        val storage = files()
        val selectedPaths = manifest.files.keys
            .asSequence()
            .filterNot { it == "database.sqlite" }
            .filter { path ->
                (restoreModels && isModelArchivePath(path)) ||
                    (restoreAttachments && !isModelArchivePath(path))
            }
            .toSet()
        require(selectedPaths.map(storage::target).toSet().size == selectedPaths.size) {
            "Conflicting backup file locations."
        }

        val snapshot = if (restoreDatabase) {
            prepareSnapshot(File(staging, "database.sqlite"))
        } else {
            null
        }

        try {
            val source = snapshot?.openHelper?.writableDatabase
            if (source != null) {
                CompleteBackupDatabase.validate(source, database.openHelper.writableDatabase)

                if (CompleteBackupSection.CONVERSATIONS in effective.sections) {
                    if (restoreAttachments) {
                        rewriteAttachments(source) { path ->
                            if (path.isBlank()) {
                                path
                            } else {
                                require(path in selectedPaths) { "The backup is missing a selected attachment." }
                                storage.target(path).absolutePath
                            }
                        }
                    } else {
                        source.execSQL("UPDATE messages_v2 SET attachments = '[]'")
                    }
                }

                if (restoreModels) {
                    validateModels(source, selectedPaths)
                }
            }

            val oldPreferences = if (restoreSettings) preferences.read() else emptyMap()
            val oldShared = if (restoreSettings) preferences.readShared() else emptyMap()
            val oldSecrets = if (restoreSecrets) readSecrets(effective) else emptyMap()
            val replacement = storage.replacement(staging, selectedPaths)

            try {
                database.withTransaction {
                    ensureIdle(restoring = true)
                    if (source != null) {
                        CompleteBackupDatabase.restoreSections(
                            source,
                            database.openHelper.writableDatabase,
                            effective
                        )
                    }
                    if (selectedPaths.isNotEmpty()) replacement.apply()
                    if (restoreSecrets) replaceSecrets(selectedSecrets, effective)
                    if (restoreSettings) {
                        preferences.replace(manifest.preferences, manifest.sharedPreferences)
                    }
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    if (selectedPaths.isNotEmpty()) runCatching { replacement.rollback() }
                    if (restoreSecrets) runCatching { replaceSecrets(oldSecrets, effective) }
                    if (restoreSettings) runCatching { preferences.replace(oldPreferences, oldShared) }
                }
                throw error
            }

            if (selectedPaths.isNotEmpty()) replacement.cleanup()
            settings.invalidatePlatformCache()
        } finally {
            snapshot?.close()
        }

        BackupRestoreResult(
            true,
            "Restored: " + effective.sections
                .sortedBy { it.ordinal }
                .joinToString { section -> section.displayName() } + "."
        )
    }

    suspend fun requiresPassword(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            val header = ByteArray(9)
            val count = input.read(header)
            when {
                count >= 8 && header.copyOfRange(0, 8).decodeToString() == "GPTFULL1" -> true
                count >= 9 &&
                    header.copyOfRange(0, 7).decodeToString() == "GPTBKUP" &&
                    header[7].toInt() == 2 -> true
                else -> false
            }
        } ?: false
    }

    private fun copyArchiveWithLimit(input: java.io.InputStream, target: File, maxBytes: Long) {
        require(maxBytes > 0) { "Insufficient free space to restore the backup." }
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "Backup is too large for the available storage." }
                output.write(buffer, 0, count)
            }
        }
    }

    /** Rebuild in a fresh Room schema instead of trusting the exporting installation's schema identity. */
    private fun prepareSnapshot(file: File): ChatDatabaseV2 {
        val prepared = openSnapshot(File(file.parentFile, "restore-prepared.sqlite"))
        try {
            val target = prepared.openHelper.writableDatabase
            val originalVersion = android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            ).use { it.version }
            require(originalVersion in 10..target.version) {
                "This backup uses database version $originalVersion. Update the app to a version that supports it; your current data is unchanged."
            }
            val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(file.absolutePath)
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(originalVersion) {
                        override fun onCreate(db: SupportSQLiteDatabase) = error("The backup database is missing.")
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    }).build()
            )
            helper.use {
                val source = it.writableDatabase
                source.beginTransaction()
                try {
                    while (source.version < target.version) {
                        val migration = ChatDatabaseV2Migrations.ALL_MIGRATIONS.firstOrNull { step -> step.startVersion == source.version }
                            ?: error("No upgrade path exists for this backup. Your current data is unchanged.")
                        migration.migrate(source)
                        source.version = migration.endVersion
                    }
                    CompleteBackupDatabase.validate(source, target)
                    target.beginTransaction()
                    try {
                        CompleteBackupDatabase.restore(source, target)
                        target.query("PRAGMA foreign_key_check").use { rows ->
                            require(!rows.moveToFirst()) { "The backup contains incomplete relationships. Your current data is unchanged." }
                        }
                        target.setTransactionSuccessful()
                    } finally {
                        target.endTransaction()
                    }
                    source.setTransactionSuccessful()
                } finally {
                    source.endTransaction()
                }
            }
            return prepared
        } catch (error: Throwable) {
            prepared.close()
            throw error
        }
    }

    private fun openSnapshot(file: File): ChatDatabaseV2 = Room.databaseBuilder(context, ChatDatabaseV2::class.java, file.absolutePath)
        .addMigrations(*ChatDatabaseV2Migrations.ALL_MIGRATIONS)
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
        .build()

    private fun rewriteAttachments(db: SupportSQLiteDatabase, transform: (String) -> String) {
        db.query("SELECT message_id, attachments FROM messages_v2").use { rows ->
            while (rows.moveToNext()) {
                val encoded = rows.getString(1).orEmpty().ifBlank { "[]" }
                val attachments = json.decodeFromString<List<ChatAttachment>>(encoded).map {
                    it.copy(localFilePath = transform(it.localFilePath), preparedFilePath = transform(it.preparedFilePath))
                }
                db.execSQL("UPDATE messages_v2 SET attachments = ? WHERE message_id = ?", arrayOf<Any>(json.encodeToString(attachments), rows.getInt(0)))
            }
        }
        db.query("SELECT id, payload FROM pending_prompts").use { rows ->
            while (rows.moveToNext()) {
                val payload = json.decodeFromString<dev.chungjungsoo.gptmobile.data.queue.PendingPromptPayload>(rows.getString(1))
                val portable = payload.copy(
                    attachments = payload.attachments.map {
                        it.copy(localFilePath = transform(it.localFilePath), preparedFilePath = transform(it.preparedFilePath))
                    }
                )
                db.execSQL("UPDATE pending_prompts SET payload = ? WHERE id = ?", arrayOf<Any>(json.encodeToString(portable), rows.getString(0)))
            }
        }
    }

    private fun validateModels(db: SupportSQLiteDatabase, paths: Set<String>) {
        db.query("SELECT relative_directory, file_name, status FROM local_models").use { rows ->
            while (rows.moveToNext()) {
                val path = "${rows.getString(0)}/${rows.getString(1)}"
                require(path.startsWith("models/")) { "Unsupported model location in backup." }
                CompleteBackupArchive.validatePath("external/$path")
                if (rows.getString(2) == "READY") {
                    require("internal/$path" in paths || "external/$path" in paths) { "A downloaded model is missing. Remove it or download it again before backing up." }
                }
            }
        }
    }

    private suspend fun readSecrets(selection: CompleteBackupSelection): Map<String, String> {
        val refs = (
            secretVault.references() +
                database.platformDao().getPlatforms().mapNotNull { it.secretRef } +
                database.toolConnectionDao().getAllConnections().mapNotNull { it.secretRef }
            ).filter { secretBelongsTo(it, selection) }
        return buildMap {
            refs.forEach { reference ->
                secretVault.read(reference)?.let { bytes ->
                    try {
                        put(reference, Base64.getEncoder().encodeToString(bytes))
                    } finally {
                        bytes.fill(0)
                    }
                }
            }
        }
    }

    private fun secretBelongsTo(reference: String, selection: CompleteBackupSelection): Boolean = when {
        reference == BACKUP_KEY_REF -> false
        reference == FactVaultRepository.VAULT_REFERENCE || reference.startsWith("memory-part-") -> selection.includes(CompleteBackupSection.MEMORY)
        else -> selection.includes(CompleteBackupSection.CREDENTIALS)
    }

    private fun validateSecrets(values: Map<String, String>) {
        values.forEach { (reference, encoded) ->
            require(Regex("[A-Za-z0-9_-]{1,128}").matches(reference)) { "Invalid credential reference." }
            val bytes = Base64.getDecoder().decode(encoded)
            try {
                require(bytes.size <= 64 * 1024) { "Invalid credential size." }
            } finally {
                bytes.fill(0)
            }
        }
    }

    private suspend fun replaceSecrets(values: Map<String, String>, selection: CompleteBackupSelection) {
        values.forEach { (reference, encoded) ->
            val bytes = Base64.getDecoder().decode(encoded)
            try {
                secretVault.put(reference, bytes)
            } finally {
                bytes.fill(0)
            }
        }
        (secretVault.references().filter { secretBelongsTo(it, selection) }.toSet() - values.keys).forEach { secretVault.delete(it) }
    }

    private suspend fun getOrCreateBackupKey(): ByteArray {
        secretVault.read(BACKUP_KEY_REF)?.let { existing ->
            if (existing.size == BACKUP_KEY_BYTES) return existing
            existing.fill(0)
            secretVault.delete(BACKUP_KEY_REF)
        }

        val generated = ByteArray(BACKUP_KEY_BYTES).also(SecureRandom()::nextBytes)
        try {
            secretVault.put(BACKUP_KEY_REF, generated)
            return generated.copyOf()
        } finally {
            generated.fill(0)
        }
    }

    private suspend fun requireExistingBackupKey(): ByteArray {
        val key = secretVault.read(BACKUP_KEY_REF)
            ?: error(
                "This passwordless encrypted backup is protected by another app installation. " +
                    "Restore it from the installation that created it, or use an older password-based backup."
            )
        if (key.size != BACKUP_KEY_BYTES) {
            key.fill(0)
            error("The passwordless backup encryption key is invalid.")
        }
        return key
    }

    private fun isDatabaseSection(section: CompleteBackupSection): Boolean = when (section) {
        CompleteBackupSection.SETTINGS,
        CompleteBackupSection.CONVERSATIONS,
        CompleteBackupSection.PLATFORMS,
        CompleteBackupSection.TOOLS,
        CompleteBackupSection.LOCAL_MODELS,
        CompleteBackupSection.AGENT_HISTORY -> true

        CompleteBackupSection.CREDENTIALS,
        CompleteBackupSection.ATTACHMENTS,
        CompleteBackupSection.MEMORY -> false
    }

    private fun manifestSelection(manifest: CompleteBackupManifest): CompleteBackupSelection {
        if (manifest.version <= 1 || manifest.sections.isEmpty()) {
            return CompleteBackupSelection.ALL
        }

        val modern = manifest.sections.mapNotNull { raw ->
            runCatching { CompleteBackupSection.valueOf(raw) }.getOrNull()
        }.toSet()
        if (FactVaultRepository.VAULT_REFERENCE in manifest.secrets && CompleteBackupSection.CREDENTIALS in modern) {
            return CompleteBackupSelection(modern + CompleteBackupSection.MEMORY).normalized()
        }
        if (modern.isNotEmpty()) return CompleteBackupSelection(modern).normalized()

        // Compatibility with brief v2 development builds that used four broad section names.
        val legacySections = buildSet {
            if ("database" in manifest.sections) {
                add(CompleteBackupSection.CONVERSATIONS)
                add(CompleteBackupSection.PLATFORMS)
                add(CompleteBackupSection.TOOLS)
                add(CompleteBackupSection.LOCAL_MODELS)
                add(CompleteBackupSection.AGENT_HISTORY)
            }
            if ("settings" in manifest.sections) add(CompleteBackupSection.SETTINGS)
            if ("credentials" in manifest.sections) add(CompleteBackupSection.CREDENTIALS)
            if ("app_files" in manifest.sections) {
                add(CompleteBackupSection.ATTACHMENTS)
                add(CompleteBackupSection.LOCAL_MODELS)
            }
        }
        return CompleteBackupSelection(legacySections).normalized()
    }

    private fun isModelArchivePath(path: String): Boolean =
        path.substringAfter('/', missingDelimiterValue = path).startsWith("models/")

    private fun CompleteBackupSection.displayName(): String = when (this) {
        CompleteBackupSection.SETTINGS -> "settings"
        CompleteBackupSection.CONVERSATIONS -> "conversations"
        CompleteBackupSection.PLATFORMS -> "AI platforms"
        CompleteBackupSection.TOOLS -> "tool connections"
        CompleteBackupSection.CREDENTIALS -> "credentials"
        CompleteBackupSection.MEMORY -> "memory"
        CompleteBackupSection.LOCAL_MODELS -> "local models"
        CompleteBackupSection.ATTACHMENTS -> "attachments"
        CompleteBackupSection.AGENT_HISTORY -> "agent history"
    }

    private fun ensureIdle(restoring: Boolean) {
        val db = database.openHelper.writableDatabase
        db.query("SELECT COUNT(*) FROM local_models WHERE status = 'DOWNLOADING'").use {
            it.moveToFirst()
            require(it.getInt(0) == 0) { "Finish or cancel model downloads before backing up or restoring." }
        }
        if (restoring) {
            db.query("SELECT COUNT(*) FROM agent_runs WHERE status IN ('QUEUED', 'RUNNING')").use {
                it.moveToFirst()
                require(it.getInt(0) == 0) { "Stop active chats before restoring." }
            }
        }
    }

    private suspend fun operation(block: suspend (File) -> BackupRestoreResult): BackupRestoreResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val work = File(context.cacheDir, "complete-backup-${UUID.randomUUID()}")
            try {
                check(work.mkdirs()) { "Could not create temporary backup storage." }
                block(work)
            } catch (error: CancellationException) {
                throw error
            } catch (_: AEADBadTagException) {
                BackupRestoreResult(
                    false,
                    "The encrypted backup could not be authenticated. It may be damaged, from another installation, or use a different legacy password."
                )
            } catch (error: Exception) {
                BackupRestoreResult(false, error.localizedMessage?.takeIf(String::isNotBlank) ?: "The backup could not be read or written.")
            } finally {
                work.deleteRecursively()
            }
        }
    }

    private companion object {
        const val RESERVE = 16L * 1024 * 1024
        const val BACKUP_KEY_BYTES = 32
        const val BACKUP_KEY_REF = "complete_backup_master_v2"
    }
}
