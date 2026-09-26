package dev.chungjungsoo.gptmobile.data.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/** Copies logical rows under the caller's Room transaction, including data still in the WAL. */
internal object CompleteBackupDatabase {
    fun snapshot(source: SupportSQLiteDatabase, target: File) {
        SQLiteDatabase.openOrCreateDatabase(target, null).use { copy ->
            copy.execSQL("DROP TABLE IF EXISTS android_metadata")
            copy.beginTransaction()
            try {
                source.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'messages_search_%'").use { cursor ->
                    while (cursor.moveToNext()) copy.execSQL(cursor.getString(0))
                }
                tables(source, includeMetadata = true).forEach { table ->
                    source.query("SELECT * FROM ${quote(table)}").use { rows -> copyRows(table, rows) { sql, values -> copy.execSQL(sql, values) } }
                }
                source.query("SELECT sql FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL").use { cursor ->
                    while (cursor.moveToNext()) copy.execSQL(cursor.getString(0))
                }
                copy.execSQL("DELETE FROM sqlite_sequence")
                source.query("SELECT name, seq FROM sqlite_sequence").use { rows ->
                    copyRows("sqlite_sequence", rows) { sql, values -> copy.execSQL(sql, values) }
                }
                if (copy.rawQuery("SELECT name FROM sqlite_master WHERE name='messages_search'", null).use { it.moveToFirst() }) copy.execSQL("INSERT INTO messages_search(messages_search) VALUES('rebuild')")
                copy.version = source.version
                copy.setTransactionSuccessful()
            } finally {
                copy.endTransaction()
            }
        }
    }

    fun validate(source: SupportSQLiteDatabase, destination: SupportSQLiteDatabase) {
        require(source.version == destination.version) { "Unsupported backup database version." }
        val sourceTables = tables(source).toSet()
        val destinationTables = tables(destination)
        val missingTables = destinationTables.filterNot { it in sourceTables }
        require(missingTables.isEmpty()) {
            "The backup is missing required records (${missingTables.joinToString()}). Your current data has not been changed."
        }
        destinationTables.filter { it in sourceTables }.forEach { table ->
            val incoming = columns(source, table)
            val target = columns(destination, table)
            val hasRows = source.query("SELECT 1 FROM ${quote(table)} LIMIT 1").use { it.moveToFirst() }
            target.forEach { (name, column) ->
                val saved = incoming[name]
                require(saved == null || saved.affinity == column.affinity) {
                    "The backup contains an unsupported value type in $table.$name. Update the app before restoring."
                }
                require(!hasRows || saved != null || (!column.required && column.primaryKeyPosition == 0) || column.defaultValue != null) {
                    "The backup is missing required data in $table.$name. Your current data has not been changed."
                }
            }
        }
        source.query("PRAGMA integrity_check").use { require(it.moveToFirst() && it.getString(0) == "ok") { "Damaged backup database." } }
        source.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Backup contains broken data relationships." } }
    }

    fun restore(source: SupportSQLiteDatabase, destination: SupportSQLiteDatabase) {
        validate(source, destination)
        val order = dependencyOrder(destination)
        order.asReversed().forEach { destination.execSQL("DELETE FROM ${quote(it)}") }
        order.forEach { table ->
            copyCompatibleRows(source, destination, table)
        }
        destination.execSQL("DELETE FROM sqlite_sequence")
        source.query("SELECT name, seq FROM sqlite_sequence").use { rows ->
            copyRows("sqlite_sequence", rows) { sql, values -> destination.execSQL(sql, values) }
        }
        // History is portable; active jobs must never be replayed by service recovery.
        if ("agent_runs" in tables(destination)) destination.execSQL("UPDATE agent_runs SET status = 'INTERRUPTED', terminal_error = 'BACKUP_RESTORED' WHERE status IN ('QUEUED', 'RUNNING')")
        if ("tool_events" in tables(destination)) destination.execSQL("UPDATE tool_events SET status = 'CANCELED', error = 'BACKUP_RESTORED' WHERE status IN ('PENDING', 'RUNNING')")
    }

    fun retainSections(database: SupportSQLiteDatabase, selection: CompleteBackupSelection) {
        val selectedTables = tablesFor(selection.normalized())
        database.execSQL("UPDATE pending_prompts SET paused = 1 WHERE userMessageId IS NULL")
        database.execSQL("UPDATE tool_approvals SET state = 'INTERRUPTED' WHERE state IN ('PENDING', 'APPROVED', 'EXECUTING')")
        database.execSQL("UPDATE model_invocations SET status = 'INTERRUPTED' WHERE status = 'RUNNING'")
        val order = dependencyOrder(database)
        order.asReversed()
            .filterNot { it in selectedTables }
            .forEach { database.execSQL("DELETE FROM ${quote(it)}") }
        if (selectedTables.isEmpty()) {
            database.execSQL("DELETE FROM sqlite_sequence")
        } else {
            database.execSQL(
                "DELETE FROM sqlite_sequence WHERE name NOT IN (" +
                    selectedTables.joinToString(",") { "'${it.replace("'", "''")}'" } +
                    ")"
            )
        }
        rebuildSearch(database)
    }

    fun restoreSections(
        source: SupportSQLiteDatabase,
        destination: SupportSQLiteDatabase,
        selection: CompleteBackupSelection
    ) {
        validate(source, destination)
        val selectedTables = tablesFor(selection.normalized())
        if (selectedTables.isEmpty()) return

        val order = dependencyOrder(destination)
        order.asReversed()
            .filter { it in selectedTables }
            .forEach { destination.execSQL("DELETE FROM ${quote(it)}") }

        order.filter { it in selectedTables }.forEach { table ->
            copyCompatibleRows(source, destination, table)
        }

        selectedTables.forEach { table ->
            destination.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf<Any>(table))
        }
        source.query("SELECT name, seq FROM sqlite_sequence").use { rows ->
            while (rows.moveToNext()) {
                val name = rows.getString(0)
                if (name in selectedTables) {
                    destination.execSQL(
                        "INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)",
                        arrayOf<Any>(name, rows.getLong(1))
                    )
                }
            }
        }

        if (CompleteBackupSection.AGENT_HISTORY in selection.normalized().sections) {
            destination.execSQL(
                "UPDATE agent_runs SET status = 'INTERRUPTED', terminal_error = 'BACKUP_RESTORED' " +
                    "WHERE status IN ('QUEUED', 'RUNNING')"
            )
            destination.execSQL(
                "UPDATE tool_events SET status = 'CANCELED', error = 'BACKUP_RESTORED' " +
                    "WHERE status IN ('PENDING', 'RUNNING')"
            )
        }
        rebuildSearch(destination)
    }

    private fun rebuildSearch(database: SupportSQLiteDatabase) {
        if (database.query("SELECT name FROM sqlite_master WHERE name='messages_search'").use { it.moveToFirst() }) database.execSQL("INSERT INTO messages_search(messages_search) VALUES('rebuild')")
    }

    private fun tablesFor(selection: CompleteBackupSelection): Set<String> {
        val sections = selection.normalized().sections
        return buildSet {
            if (CompleteBackupSection.CONVERSATIONS in sections) {
                add("chats_v2")
                add("pending_prompts")
                addAll(listOf("knowledge_projects", "knowledge_project_chats", "knowledge_documents", "knowledge_chunks"))
                add("messages_v2")
                add("chat_platform_model_v2")
            }
            if (CompleteBackupSection.PLATFORMS in sections) {
                add("platform_v2")
                add("provider_connections")
            }
            if (CompleteBackupSection.TOOLS in sections) {
                add("tool_connections")
                add("agent_tool_bindings")
            }
            if (CompleteBackupSection.LOCAL_MODELS in sections) {
                add("local_models")
            }
            if (CompleteBackupSection.AGENT_HISTORY in sections) {
                add("agent_runs")
                add("tool_events")
                add("tool_approvals")
                add("model_invocations")
            }
            if (CompleteBackupSection.SETTINGS in sections) {
                // The queue cache is optional operational state, but keeping it with app
                // settings preserves existing complete-backup round trips.
                add("openrouter_batch_cache")
            }
        }
    }

    private fun tables(db: SupportSQLiteDatabase, includeMetadata: Boolean = false): List<String> = db.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'messages_search%' ORDER BY name"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (includeMetadata || name !in setOf("room_master_table", "android_metadata")) add(name)
            }
        }
    }

    private fun dependencyOrder(db: SupportSQLiteDatabase): List<String> {
        val tables = tables(db).toSet()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val result = mutableListOf<String>()
        fun visit(table: String) {
            if (table in visited) return
            require(visiting.add(table)) { "Unsupported circular database relationship." }
            db.query("PRAGMA foreign_key_list(${quote(table)})").use { rows ->
                while (rows.moveToNext()) rows.getString(2).takeIf { it in tables && it != table }?.let(::visit)
            }
            visiting.remove(table)
            visited.add(table)
            result.add(table)
        }
        tables.forEach(::visit)
        return result
    }

    private fun copyRows(table: String, rows: Cursor, insert: (String, Array<Any?>) -> Unit) {
        val sql = "INSERT INTO ${quote(table)} (${rows.columnNames.joinToString { quote(it) }}) VALUES (${rows.columnNames.joinToString { "?" }})"
        while (rows.moveToNext()) {
            insert(
                sql,
                Array(rows.columnCount) { column ->
                    when (rows.getType(column)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> rows.getLong(column)
                        Cursor.FIELD_TYPE_FLOAT -> rows.getDouble(column)
                        Cursor.FIELD_TYPE_BLOB -> rows.getBlob(column)
                        else -> rows.getString(column)
                    }
                }
            )
        }
    }

    private fun quote(name: String): String = "\"${name.replace("\"", "\"\"")}\""

    /** Import into the current schema, allowing new nullable/defaulted columns and ignoring retired fields. */
    private fun copyCompatibleRows(source: SupportSQLiteDatabase, destination: SupportSQLiteDatabase, table: String) {
        if (table !in tables(source)) return
        val incoming = columns(source, table)
        val target = columns(destination, table)
        val names = target.keys.filter { it in incoming }
        if (names.isEmpty()) return
        val projection = names.joinToString { name ->
            val column = target.getValue(name)
            if (column.required && column.defaultValue != null) "COALESCE(${quote(name)}, ${column.defaultValue}) AS ${quote(name)}" else quote(name)
        }
        source.query("SELECT $projection FROM ${quote(table)}").use { rows ->
            copyRows(table, rows) { sql, values -> destination.execSQL(sql, values) }
        }
    }

    private data class ColumnShape(val affinity: String, val required: Boolean, val primaryKeyPosition: Int, val defaultValue: String?)

    private fun columns(db: SupportSQLiteDatabase, table: String): Map<String, ColumnShape> =
        db.query("PRAGMA table_info(${quote(table)})").use { rows ->
            buildMap {
                while (rows.moveToNext()) {
                    val type = rows.getString(2).orEmpty().uppercase()
                    val affinity = when {
                        "INT" in type -> "INTEGER"
                        listOf("CHAR", "CLOB", "TEXT").any(type::contains) -> "TEXT"
                        type.isBlank() || "BLOB" in type -> "BLOB"
                        listOf("REAL", "FLOA", "DOUB").any(type::contains) -> "REAL"
                        else -> "NUMERIC"
                    }
                    put(rows.getString(1), ColumnShape(affinity, rows.getInt(3) != 0, rows.getInt(5), rows.getString(4)?.takeUnless { it.equals("NULL", true) }))
                }
            }
        }
}
