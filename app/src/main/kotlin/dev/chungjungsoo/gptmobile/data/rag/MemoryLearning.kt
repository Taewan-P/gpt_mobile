package dev.chungjungsoo.gptmobile.data.rag

import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Source-bound extraction: external text, code, questions and secrets are not personal observations. */
internal object MemoryLearning {
    private val optOut = Regex("(?i)\\b(?:do not|don't|don’t|never)\\s+(?:remember|save|store|memorize)\\b")
    private val credentials = Regex("(?i)\\b(?:password|api[_ -]?key|access[_ -]?token|secret[_ -]?key|bearer)\\b|\\b(?:sk|ghp|github_pat)-?[A-Za-z0-9_]{16,}|-----BEGIN .*PRIVATE KEY")

    // Android's ICU treats [:] as an unfinished POSIX property, unlike desktop Java.
    private val explicit = Regex("(?i)^(?:please\\s+)?(?:remember(?:\\s+that)?|save\\s+this(?:\\s+fact)?:?)[ :]+(.+)$")

    fun statements(text: String): List<String> {
        if (optOut.containsMatchIn(text)) return emptyList()
        var code = false
        return text.lineSequence().mapNotNull { line ->
            val value = line.trim()
            if (value.startsWith("```") || value.startsWith("~~~")) {
                code = !code
                null
            } else if (code || value.startsWith('>')) {
                null
            } else {
                value
            }
        }.flatMap { it.split(Regex("(?<=[.!?])\\s+")).asSequence() }
            .map { it.trim().trimEnd('.', '!') }
            .filterNot { Regex("(?i)^(?:if|suppose|imagine|for example|example:|user:|assistant:)\\b|\\b(?:said|says|claims|claimed|quoted|reported)\\b").containsMatchIn(it) }
            .filter { it.length in 4..1000 && '?' !in it && !it.startsWith('"') && !it.startsWith('“') && !credentials.containsMatchIn(it) }
            .take(32).toList()
    }

    fun hasExplicitRequest(text: String): Boolean = explicit.containsMatchIn(text)

    fun observation(text: String): KnowledgeFact = KnowledgeFact(
        KnowledgeEntity("user", "User", "PERSON"),
        KnowledgeRelation("user", "REMEMBERS", text.lowercase(Locale.ROOT), context = text),
        KnowledgeEntity(text.lowercase(Locale.ROOT), text, "OBSERVATION")
    )

    fun extract(text: String, sensitivity: Int): List<KnowledgeFact> = statements(text).flatMap { statement ->
        val request = explicit.matchEntire(statement)
        val value = request?.groupValues?.get(1) ?: statement
        val graph = KnowledgeGraphEngine()
        graph.extractAndStoreFromText(value, sensitivity)
        val facts = graph.getAllEntities().flatMap { graph.querySubgraph(it.id, 1) }.toMutableList()
        val target = "([^.!?;\\n]{1,250})"
        val patterns = listOf(
            Regex("(?iu)^(?:my name is|call me) $target$") to "NAMED",
            Regex("(?iu)^(?:I work as|I am|I'm|I’m) (?:a|an) $target$") to "OCCUPATION",
            Regex("(?iu)^my (?:time ?zone) is $target$") to "TIMEZONE",
            Regex("(?iu)^my pronouns are $target$") to "PRONOUNS",
            Regex("(?iu)^(?:I (?:avoid|dislike)|I cannot eat|I can't eat) $target$") to "AVOIDS",
            Regex("(?iu)^(?:I own|my (?:phone|computer|laptop) is) $target$") to "OWNS",
            Regex("(?iu)^(?:I (?:prefer replies|prefer responses|prefer answers) in|please (?:reply|respond|answer) in) $target$") to "RESPONSE_LANGUAGE"
        ) + if (sensitivity >= 40) listOf(Regex("(?iu)^(?:I am|I'm|I’m) (?:working on|building|developing|learning|studying) $target$") to "WORKING_ON") else emptyList()
        patterns.forEach { (pattern, relation) ->
            pattern.matchEntire(value)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && !Regex("(?i)\\b(?:not|never|maybe|if)\\b").containsMatchIn(it) }?.let { name ->
                facts += KnowledgeFact(KnowledgeEntity("user", "User", "PERSON"), KnowledgeRelation("user", relation, name.lowercase(Locale.ROOT), context = statement), KnowledgeEntity(name.lowercase(Locale.ROOT), name, "FACT"))
            }
        }
        if (request != null && facts.isEmpty()) facts += observation(value)
        facts.map { it.copy(relation = it.relation.copy(context = statement)) }
    }.filter { MemoryCapturePolicy.accepts(it, sensitivity) }

    /** The model may select whole statements, but cannot rewrite or fabricate what the user said. */
    fun modelObservations(text: String, response: JsonObject, sensitivity: Int): List<KnowledgeFact> {
        val originals = statements(text)
        return (response["observations"] as? JsonArray).orEmpty().mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            val quote = (row["quote"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            val original = originals.firstOrNull { it.equals(quote.trim().trimEnd('.', '!'), ignoreCase = true) } ?: return@mapNotNull null
            val kind = (row["kind"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val relation = when (kind) {
                "preference" -> "PREFERS"
                "profile" -> "PROFILE"
                "project" -> "WORKING_ON"
                "goal" -> "GOAL"
                else -> return@mapNotNull null
            }
            val fact = observation(original).let { it.copy(relation = it.relation.copy(relationType = relation)) }
            fact.takeIf { MemoryCapturePolicy.accepts(it, sensitivity) }
        }.distinctBy { it.target.id }
    }
}
