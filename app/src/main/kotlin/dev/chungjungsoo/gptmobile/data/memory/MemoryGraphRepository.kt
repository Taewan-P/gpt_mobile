package dev.chungjungsoo.gptmobile.data.memory

import androidx.sqlite.db.SimpleSQLiteQuery
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
import dev.chungjungsoo.gptmobile.data.rag.VaultFact
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class MemoryGraphRepository @Inject constructor(
    private val database: ChatDatabaseV2
) {
    private val dao get() = database.memoryGraphDao()
    private val mutex = Mutex()
    private var ftsAvailable: Boolean? = null

    suspend fun createEntities(
        entities: List<MemoryGraphEntityInput>,
        chatId: Int,
        messageId: Int,
        scope: String = "personal"
    ): List<MemoryGraphEntityRecord> = mutex.withLock {
        require(scope == "personal" || scope.startsWith("project:"))
        val now = System.currentTimeMillis()
        val rows = entities.filter { it.name.isNotBlank() }.take(32).map { input ->
            val normalized = normalize(input.name)
            require(normalized.length in 1..120) { "Entity names must be 1–120 characters." }
            val existing = dao.entityByName(scope, normalized)
            MemoryGraphEntityRecord(
                id = existing?.id ?: stableId("entity", scope, normalized),
                name = input.name.trim().take(120),
                normalizedName = normalized,
                entityType = normalizeType(input.entityType),
                scope = scope,
                standalone = true,
                sourceChatId = existing?.sourceChatId?.takeIf { it != 0 } ?: chatId,
                sourceMessageId = existing?.sourceMessageId?.takeIf { it != 0 } ?: messageId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        }.distinctBy { it.id }
        if (rows.isNotEmpty()) {
            dao.upsertEntities(rows)
            rebuildFtsLocked()
        }
        rows
    }

    suspend fun findEntity(name: String, scope: String = "personal"): MemoryGraphEntityRecord? =
        mutex.withLock { dao.entityByName(scope, normalize(name)) }

    suspend fun replaceFromVault(facts: List<VaultFact>, scope: String = "personal") = mutex.withLock {
        dao.deleteObservationsBySource(SOURCE_VAULT)
        dao.deleteRelationsBySource(SOURCE_VAULT)
        val now = System.currentTimeMillis()
        val entityRows = linkedMapOf<String, MemoryGraphEntityRecord>()
        val observations = mutableListOf<MemoryGraphObservationRecord>()
        val relations = mutableListOf<MemoryGraphRelationRecord>()

        facts.filter { it.scope == scope }.forEach { entry ->
            val source = entityRecord(entry.fact.entity.name, entry.fact.entity.type, scope, entry.sourceChatId, entry.sourceMessageId, now)
            val target = entityRecord(entry.fact.target.name, entry.fact.target.type, scope, entry.sourceChatId, entry.sourceMessageId, now)
            entityRows[source.id] = mergeEntity(entityRows[source.id], source)
            entityRows[target.id] = mergeEntity(entityRows[target.id], target)
            relations += MemoryGraphRelationRecord(
                id = stableId("relation", scope, entry.id),
                fromEntityId = source.id,
                toEntityId = target.id,
                relationType = normalizeType(entry.fact.relation.relationType),
                scope = scope,
                sourceKind = SOURCE_VAULT,
                sourceChatId = entry.sourceChatId,
                sourceMessageId = entry.sourceMessageId,
                updatedAt = now
            )
            if (
                entry.fact.target.type.equals("OBSERVATION", true) ||
                entry.fact.target.type.equals("PREFERENCE", true) ||
                entry.fact.relation.relationType in setOf("REMEMBERS", "OBSERVATION", "PREFERS", "AVOIDS", "RESPONSE_LANGUAGE")
            ) {
                observations += MemoryGraphObservationRecord(
                    id = stableId("observation", scope, entry.id),
                    entityId = source.id,
                    observation = entry.fact.target.name.take(MAX_OBSERVATION_CHARS),
                    normalizedObservation = normalize(entry.fact.target.name).take(MAX_OBSERVATION_CHARS),
                    scope = scope,
                    sourceKind = SOURCE_VAULT,
                    sourceChatId = entry.sourceChatId,
                    sourceMessageId = entry.sourceMessageId,
                    updatedAt = now
                )
            }
        }

        val persisted = entityRows.values.map { row ->
            val existing = dao.entityById(row.id)
            if (existing == null) row else row.copy(
                standalone = existing.standalone,
                createdAt = existing.createdAt,
                sourceChatId = existing.sourceChatId.takeIf { it != 0 } ?: row.sourceChatId,
                sourceMessageId = existing.sourceMessageId.takeIf { it != 0 } ?: row.sourceMessageId,
                entityType = existing.entityType.takeIf { existing.standalone && it != "ENTITY" } ?: row.entityType
            )
        }
        if (persisted.isNotEmpty()) dao.upsertEntities(persisted)
        if (observations.isNotEmpty()) dao.upsertObservations(observations)
        if (relations.isNotEmpty()) dao.upsertRelations(relations)
        dao.pruneUnreferencedEntities()
        rebuildFtsLocked()
    }

    suspend fun searchNodes(query: String, chatId: Int?, scope: String = "personal", limit: Int = 20): List<MemoryGraphNode> =
        mutex.withLock {
            val bounded = limit.coerceIn(1, 64)
            val ids = searchFtsLocked(query, scope, bounded)
            val rows = if (ids.isNotEmpty()) {
                val byId = dao.entitiesByIds(ids).associateBy { it.id }
                ids.mapNotNull(byId::get)
            } else {
                dao.searchLike(scope, "%${normalize(query)}%", bounded)
            }
            loadNodesLocked(rows.filter { visible(it, chatId) }.take(bounded), chatId)
        }

    suspend fun openNodes(names: List<String>, chatId: Int?, scope: String = "personal"): List<MemoryGraphNode> =
        mutex.withLock {
            val rows = names.take(32).mapNotNull { dao.entityByName(scope, normalize(it)) }
                .filter { visible(it, chatId) }
                .distinctBy { it.id }
            loadNodesLocked(rows, chatId)
        }

    suspend fun readGraph(
        chatId: Int?,
        scope: String = "personal",
        offset: Int = 0,
        limit: Int = 32
    ): Pair<Int, List<MemoryGraphNode>> = mutex.withLock {
        val visibleRows = dao.entities(scope).filter { visible(it, chatId) }
        val page = visibleRows.drop(offset.coerceAtLeast(0)).take(limit.coerceIn(1, 64))
        visibleRows.size to loadNodesLocked(page, chatId)
    }

    suspend fun clear() = mutex.withLock {
        dao.clearRelations()
        dao.clearObservations()
        dao.clearEntities()
        runCatching { database.openHelper.writableDatabase.execSQL("DELETE FROM memory_graph_fts") }
    }

    private suspend fun entityRecord(
        name: String,
        type: String,
        scope: String,
        chatId: Int,
        messageId: Int,
        now: Long
    ): MemoryGraphEntityRecord {
        val normalized = normalize(name).take(120)
        val id = stableId("entity", scope, normalized)
        val existing = dao.entityById(id)
        return MemoryGraphEntityRecord(
            id = id,
            name = name.trim().take(120),
            normalizedName = normalized,
            entityType = normalizeType(type),
            scope = scope,
            standalone = existing?.standalone ?: false,
            sourceChatId = existing?.sourceChatId?.takeIf { it != 0 } ?: chatId,
            sourceMessageId = existing?.sourceMessageId?.takeIf { it != 0 } ?: messageId,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
    }

    private fun mergeEntity(current: MemoryGraphEntityRecord?, incoming: MemoryGraphEntityRecord): MemoryGraphEntityRecord =
        if (current == null) incoming else incoming.copy(
            standalone = current.standalone || incoming.standalone,
            createdAt = minOf(current.createdAt, incoming.createdAt)
        )

    private suspend fun loadNodesLocked(rows: List<MemoryGraphEntityRecord>, chatId: Int?): List<MemoryGraphNode> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.id }
        val relations = dao.relationsFor(ids)
            .filter { chatId == null || it.sourceChatId == 0 || it.sourceChatId == chatId }
        val relatedIds = (ids + relations.flatMap { listOf(it.fromEntityId, it.toEntityId) }).distinct()
        val entityMap = dao.entitiesByIds(relatedIds).associateBy { it.id }
        val observations = dao.observationsFor(ids)
            .filter { chatId == null || it.sourceChatId == 0 || it.sourceChatId == chatId }
            .groupBy { it.entityId }

        return rows.map { row ->
            MemoryGraphNode(
                id = row.id,
                name = row.name,
                entityType = row.entityType,
                observations = observations[row.id].orEmpty().map { it.observation }.distinct().take(24),
                relations = relations.filter { it.fromEntityId == row.id || it.toEntityId == row.id }.mapNotNull { relation ->
                    val from = entityMap[relation.fromEntityId]?.name ?: return@mapNotNull null
                    val to = entityMap[relation.toEntityId]?.name ?: return@mapNotNull null
                    MemoryGraphRelationView(from, to, relation.relationType)
                }.distinct().take(48)
            )
        }
    }

    private fun visible(row: MemoryGraphEntityRecord, chatId: Int?): Boolean =
        chatId == null || row.sourceChatId == 0 || row.sourceChatId == chatId

    private fun ensureFtsLocked(): Boolean {
        ftsAvailable?.let { return it }
        val available = runCatching {
            database.openHelper.writableDatabase.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS memory_graph_fts USING fts5(entity_id UNINDEXED, scope UNINDEXED, text, tokenize='unicode61 remove_diacritics 2')"
            )
        }.isSuccess
        ftsAvailable = available
        return available
    }

    private suspend fun rebuildFtsLocked() {
        if (!ensureFtsLocked()) return
        val db = database.openHelper.writableDatabase
        runCatching {
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM memory_graph_fts")
                val entities = dao.entities("personal")
                entities.forEach { entity ->
                    db.execSQL(
                        "INSERT INTO memory_graph_fts(entity_id, scope, text) VALUES (?, ?, ?)",
                        arrayOf(entity.id, entity.scope, "${entity.name} ${entity.entityType}")
                    )
                }
                if (entities.isNotEmpty()) {
                    dao.observationsFor(entities.map { it.id }).forEach { observation ->
                        db.execSQL(
                            "INSERT INTO memory_graph_fts(entity_id, scope, text) VALUES (?, ?, ?)",
                            arrayOf(observation.entityId, observation.scope, observation.observation)
                        )
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }.onFailure { ftsAvailable = false }
    }

    private fun searchFtsLocked(query: String, scope: String, limit: Int): List<String> {
        if (!ensureFtsLocked()) return emptyList()
        val match = ftsQuery(query) ?: return emptyList()
        return runCatching {
            val cursor = database.openHelper.readableDatabase.query(
                SimpleSQLiteQuery(
                    "SELECT entity_id FROM memory_graph_fts WHERE scope = ? AND memory_graph_fts MATCH ? GROUP BY entity_id LIMIT ?",
                    arrayOf(scope, match, limit)
                )
            )
            cursor.use {
                buildList {
                    while (it.moveToNext()) add(it.getString(0))
                }
            }
        }.getOrElse {
            ftsAvailable = false
            emptyList()
        }
    }

    internal fun ftsQuery(query: String): String? {
        val tokens = normalize(query)
            .split(Regex("[^\\p{L}\\p{N}_-]+"))
            .filter { it.length >= 2 }
            .distinct()
            .take(12)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { token -> "\"${token.replace("\"", "\"\"")}\"*" }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun normalizeType(value: String): String =
        value.trim().uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9_]+"), "_").trim('_').take(64).ifBlank { "ENTITY" }

    private fun stableId(kind: String, scope: String, value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$kind|$scope|$value".encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$kind-${digest.take(32)}"
    }

    companion object {
        private const val SOURCE_VAULT = "vault"
        private const val MAX_OBSERVATION_CHARS = 1000
    }
}
