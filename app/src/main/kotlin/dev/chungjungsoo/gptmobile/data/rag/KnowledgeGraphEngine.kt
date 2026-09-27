package dev.chungjungsoo.gptmobile.data.rag

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

/**
 * Domain representations for the on-device Knowledge Graph Memory engine.
 */
@Serializable
data class KnowledgeEntity(
    val id: String,
    val name: String,
    val type: String, // e.g., "PERSON", "CONCEPT", "TECHNOLOGY", "LOCATION", "PREFERENCE", "PROJECT"
    val attributes: Map<String, String> = emptyMap()
)

@Serializable
data class KnowledgeRelation(
    val sourceId: String,
    val relationType: String, // e.g., "USES", "PREFERS", "LOCATED_IN", "CONTRIBUTES_TO", "DEVELOPED_WITH"
    val targetId: String,
    val weight: Float = 1.0f,
    val context: String = ""
)

@Serializable
data class KnowledgeFact(
    val entity: KnowledgeEntity,
    val relation: KnowledgeRelation,
    val target: KnowledgeEntity
) {
    fun toContextString(): String =
        "${entity.name} (${entity.type}) -[${relation.relationType}]-> ${target.name} (${target.type})${if (relation.context.isNotBlank()) " [${relation.context}]" else ""}"
}

/**
 * Knowledge Graph Memory Engine for local entity-relationship extraction,
 * semantic associative recall, and graph traversal.
 */
@Singleton
class KnowledgeGraphEngine @Inject constructor() {

    private val entities = mutableMapOf<String, KnowledgeEntity>()
    private val outgoingEdges = mutableMapOf<String, MutableList<KnowledgeRelation>>()
    private val incomingEdges = mutableMapOf<String, MutableList<KnowledgeRelation>>()

    @Synchronized
    fun addEntity(entity: KnowledgeEntity) {
        val normalizedId = entity.id.lowercase(Locale.ROOT).trim()
        val normalizedEntity = entity.copy(id = normalizedId)
        entities[normalizedId] = normalizedEntity
        outgoingEdges.putIfAbsent(normalizedId, mutableListOf())
        incomingEdges.putIfAbsent(normalizedId, mutableListOf())
    }

    @Synchronized
    fun addRelation(relation: KnowledgeRelation) {
        val src = relation.sourceId.lowercase(Locale.ROOT).trim()
        val dst = relation.targetId.lowercase(Locale.ROOT).trim()
        val normalizedRelation = relation.copy(sourceId = src, targetId = dst)

        val outgoing = outgoingEdges.getOrPut(src) { mutableListOf() }
        val incoming = incomingEdges.getOrPut(dst) { mutableListOf() }
        outgoing.removeAll { it.relationType == relation.relationType && it.targetId == dst }
        incoming.removeAll { it.relationType == relation.relationType && it.sourceId == src }
        outgoing.add(normalizedRelation)
        incoming.add(normalizedRelation)
    }

    @Synchronized
    fun getEntity(id: String): KnowledgeEntity? =
        entities[id.lowercase(Locale.ROOT).trim()]

    @Synchronized
    fun getAllEntities(): List<KnowledgeEntity> = entities.values.toList()

    @Synchronized
    fun getRelationsFor(entityId: String): List<KnowledgeRelation> {
        val id = entityId.lowercase(Locale.ROOT).trim()
        val outRels = outgoingEdges[id] ?: emptyList()
        val inRels = incomingEdges[id] ?: emptyList()
        return (outRels + inRels).distinct()
    }

    /**
     * Traverses graph from [seedEntityId] up to [maxDepth] steps.
     */
    @Synchronized
    fun querySubgraph(seedEntityId: String, maxDepth: Int = 2): List<KnowledgeFact> {
        val rootId = seedEntityId.lowercase(Locale.ROOT).trim()
        if (!entities.containsKey(rootId)) return emptyList()

        val facts = mutableListOf<KnowledgeFact>()
        val visitedEntities = mutableSetOf(rootId)
        var frontier = setOf(rootId)

        for (depth in 0 until maxDepth) {
            val nextFrontier = mutableSetOf<String>()
            for (currentId in frontier) {
                val out = outgoingEdges[currentId] ?: emptyList()
                for (rel in out) {
                    val src = entities[rel.sourceId]
                    val dst = entities[rel.targetId]
                    if (src != null && dst != null) {
                        facts.add(KnowledgeFact(src, rel, dst))
                        if (visitedEntities.add(rel.targetId)) {
                            nextFrontier.add(rel.targetId)
                        }
                    }
                }
            }
            frontier = nextFrontier
            if (frontier.isEmpty()) break
        }

        return facts.distinctBy { "${it.relation.sourceId}->${it.relation.relationType}->${it.relation.targetId}" }
    }

    /**
     * Finds related contextual facts matching entities detected in [queryText].
     */
    @Synchronized
    fun queryContextualFacts(queryText: String, maxResults: Int = 5): List<KnowledgeFact> {
        val words = queryText.lowercase(Locale.ROOT).split(Regex("\\W+"))
        val tokens = words.filter { it.length > 2 }.toMutableSet()
        if (tokens.isEmpty()) return emptyList()

        // Match seed entities that overlap with tokens
        val matchedEntities = entities.values.filter { entity ->
            val entityTokens = entity.name.lowercase(Locale.ROOT).split(Regex("\\W+"))
            entityTokens.any { tokens.contains(it) } || tokens.contains(entity.id)
        }

        // Resolve first-person memory questions only when no named entity matched.
        // A generic “help me” must not pull in every unrelated user fact.
        val personalQuestion = words.any { it in setOf("i", "me", "my", "mine") } &&
            words.any { it in setOf("prefer", "preferences", "preference", "like", "use", "work", "live", "remember", "facts") }
        val seeds = matchedEntities.ifEmpty {
            if (personalQuestion) listOfNotNull(entities["user"]) else emptyList()
        }
        val collectedFacts = mutableListOf<KnowledgeFact>()
        for (seed in seeds) {
            collectedFacts.addAll(querySubgraph(seed.id, maxDepth = 2))
            // A query can name the target (e.g. Kotlin) rather than the subject (User).
            getRelationsFor(seed.id).forEach { relation ->
                val source = entities[relation.sourceId]
                val target = entities[relation.targetId]
                if (source != null && target != null) collectedFacts.add(KnowledgeFact(source, relation, target))
            }
        }

        return collectedFacts.distinctBy { "${it.relation.sourceId}:${it.relation.relationType}:${it.relation.targetId}" }
            .take(maxResults)
    }

    /**
     * Extracts entities and relations from conversational or structured text using pattern and keyword heuristics.
     */
    @Synchronized
    fun extractAndStoreFromText(text: String, sensitivity: Int = 50) {
        val lines = text.split(Regex("[\\r\\n]+|(?<=[.!])\\s+"))
        val subject = "([\\p{L}\\p{N}_-]+(?:[ ]+[\\p{L}\\p{N}_-]+){0,5})"
        val target = "([^.!?;\\n]{1,120})"
        val relationKeywords = listOf(
            Regex("(?iu)$subject\\s+(?:is using|uses|use|built with|built on|runs on|uso|utilizo)\\s+$target") to "USES",
            Regex("(?iu)$subject\\s+(?:prefers|prefer|likes|like|favorite is|prefiro|prefiero|préfère|gosto de|aime)\\s+$target") to "PREFERS",
            Regex("(?iu)$subject\\s+(?:works at|work at|contributes to|part of|trabalho na|trabalho no|trabajo en|travaille chez)\\s+$target") to "CONTRIBUTES_TO",
            Regex("(?iu)$subject\\s+(?:located in|lives in|live in|based in|moro em|vivo en|habite à)\\s+$target") to "LOCATED_IN"
        )

        val expandedKeywords = if (sensitivity > 50) {
            listOf(
                Regex("(?iu)^$subject\\s+(?:am working on|am building|am developing|am learning|am studying)\\s+$target") to "WORKING_ON",
                Regex("(?iu)^$subject\\s+(?:need|needs|want|wants|plan to|plans to)\\s+$target") to "GOAL"
            )
        } else {
            emptyList()
        }
        for (line in lines) {
            val trimmed = line.trim()
            // Only affirmative statements are candidates. Questions and negation can
            // otherwise become false facts such as “not PREFERS Kotlin”.
            if (trimmed.isEmpty() ||
                '?' in trimmed ||
                Regex("(?i)\\b(?:not|never|no longer|não|nunca|jamais|pas)\\b|n['’]t\\b").containsMatchIn(trimmed)
            ) {
                continue
            }

            for ((pattern, relType) in relationKeywords + expandedKeywords) {
                val match = pattern.matchAt(trimmed, 0)
                if (match != null && match.groupValues.size >= 3) {
                    val subject = match.groupValues[1].trim()
                    val target = match.groupValues[2].trim()
                    // A multiword subject must not turn reported speech into a fact.
                    if (Regex("(?iu)\\b(?:said|says|told|wrote|quoted|asked|reported|claims|claimed|disse|dijo|dit)\\b").containsMatchIn(subject)) continue
                    if (subject.isNotBlank() && target.isNotBlank()) {
                        val srcEntity = KnowledgeEntity(id = subject.lowercase(Locale.ROOT), name = subject, type = "ENTITY")
                        val dstEntity = KnowledgeEntity(id = target.lowercase(Locale.ROOT), name = target, type = "ENTITY")
                        addEntity(srcEntity)
                        addEntity(dstEntity)
                        addRelation(KnowledgeRelation(srcEntity.id, relType, dstEntity.id, 1.0f, trimmed))
                    }
                }
            }
        }
    }

    @Synchronized
    fun clear() {
        entities.clear()
        outgoingEdges.clear()
        incomingEdges.clear()
    }
}
