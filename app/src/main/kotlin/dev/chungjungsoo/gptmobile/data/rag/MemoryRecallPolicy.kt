package dev.chungjungsoo.gptmobile.data.rag

import java.util.Locale
import kotlin.math.ln

/** Bounded lexical retrieval with related vocabulary, relation intent and conversation continuity. */
internal object MemoryRecallPolicy {
    private val stop = setOf("the", "and", "that", "this", "with", "have", "what", "how", "for", "are", "was", "can", "you", "your", "please", "help", "user", "about", "from", "would", "could", "tell")
    private val groups = listOf(
        setOf("concise", "brief", "short", "succinct", "brevity"),
        setOf("reply", "response", "answer", "replies"),
        setOf("code", "coding", "programming", "developer"),
        setOf("phone", "smartphone", "mobile", "android", "iphone"),
        setOf("computer", "laptop", "workstation", "desktop"),
        setOf("food", "diet", "eat", "meal", "vegetarian", "vegan", "allergic", "allergy"),
        setOf("location", "city", "live", "based", "where"),
        setOf("job", "occupation", "profession", "career", "work"),
        setOf("project", "building", "developing", "working"),
        setOf("preference", "prefer", "favorite", "like")
    )
    private fun tokens(text: String): Set<String> = Regex("[\\p{L}\\p{N}]+", RegexOption.IGNORE_CASE).findAll(text.lowercase(Locale.ROOT))
        .map { it.value }.filter { it.length > 2 && it !in stop }.map { if (it.endsWith("s") && it.length > 4) it.dropLast(1) else it }.toSet()
    private fun expanded(text: String, document: Boolean = false): Set<String> {
        val terms = tokens(text).toMutableSet()
        if (document && terms.any { it in setOf("kotlin", "python", "typescript", "javascript", "java", "rust") }) terms += "programming"
        return terms + groups.filter { group -> group.any { it in terms } }.flatMap { tokens(it.joinToString(" ")) }
    }
    fun isFollowUp(query: String): Boolean = tokens(query).size <= 10 && Regex("(?i)\\b(?:it|that|those|them|continue|earlier|previous|again)\\b").containsMatchIn(query)

    fun rank(query: String, facts: List<VaultFact>, previousContext: String = ""): List<VaultFact> {
        val queryTerms = tokens(query)
        val broad = Regex("(?i)\\b(?:remember|know|saved|facts|profile)\\b.*\\b(?:me|my|about me)\\b|\\b(?:my memories|my preferences)\\b").containsMatchIn(query)
        val personal = Regex("(?i)\\b(?:I|me|my|mine)\\b").containsMatchIn(query)
        val expandedQuery = expanded(query)
        val continuity = if (isFollowUp(query)) expanded(previousContext.takeLast(2000)) else emptySet()
        val corpus = facts.associateWith { tokens(it.fact.entity.name + " " + it.fact.target.name) }
        val documentFrequency = corpus.values.flatten().groupingBy { it }.eachCount()
        return facts.map { fact ->
            val terms = corpus.getValue(fact)
            val exact = queryTerms.intersect(terms).sumOf { term -> ln(1.0 + facts.size.toDouble() / (1 + documentFrequency.getOrDefault(term, 0))) * 4 }
            val related = expandedQuery.intersect(expanded(fact.fact.target.name, document = true)).size.coerceAtMost(3) * 0.35
            val relation = fact.fact.relation.relationType
            val intent = if (!personal) {
                0.0
            } else {
                when {
                    relation in setOf("PREFERS", "AVOIDS", "RESPONSE_LANGUAGE") && queryTerms.any { it in setOf("prefer", "preference", "like", "favorite") } -> 4.0
                    relation == "LOCATED_IN" && queryTerms.any { it in setOf("live", "where", "location", "city") } -> 5.0
                    relation == "NAMED" && "name" in queryTerms -> 5.0
                    relation == "OCCUPATION" && queryTerms.any { it in setOf("job", "work", "occupation", "profession") } -> 5.0
                    relation == "WORKING_ON" && queryTerms.any { it in setOf("project", "working", "building", "learning") } -> 4.0
                    else -> 0.0
                }
            }
            val followUp = if (continuity.isNotEmpty()) continuity.intersect(terms).size.coerceAtMost(3) * 0.6 else 0.0
            val score = exact + related + intent + followUp + if (broad && fact.fact.entity.id == "user") 0.5 else 0.0
            fact to score
        }.filter { it.second > 0.0 }
            .sortedWith(compareByDescending<Pair<VaultFact, Double>> { it.second }.thenByDescending { it.first.pinned }.thenByDescending { it.first.savedAtMillis })
            .map { it.first }
    }
}
