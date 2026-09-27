package dev.chungjungsoo.gptmobile.data.rag

/** Importance selection only; source validation, privacy and review controls still apply. */
object MemoryCapturePolicy {
    fun accepts(fact: KnowledgeFact, sensitivity: Int): Boolean {
        val level = sensitivity.coerceIn(0, 100)
        val context = fact.relation.context
        val explicitlyRequested = MemoryLearning.hasExplicitRequest(context) || Regex("(?i)\\b(?:remember|always|important|favorite)\\b").containsMatchIn(context)
        val importance = when {
            explicitlyRequested -> 100
            fact.relation.relationType in setOf("PREFERS", "AVOIDS", "LOCATED_IN", "CONTRIBUTES_TO", "NAMED", "TIMEZONE", "PRONOUNS", "OCCUPATION", "RESPONSE_LANGUAGE", "PROFILE") -> 85
            fact.relation.relationType in setOf("USES", "OWNS") -> 65
            fact.relation.relationType == "WORKING_ON" -> 60
            fact.relation.relationType == "GOAL" -> 20
            else -> 10
        }
        return importance >= 100 - level
    }
}
