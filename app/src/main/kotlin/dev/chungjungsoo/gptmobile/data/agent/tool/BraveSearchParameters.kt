package dev.chungjungsoo.gptmobile.data.agent.tool

import java.net.URI
import java.time.Clock
import java.time.LocalDate

/** Shared by the direct API and the official brave_web_search MCP tool. */
internal fun braveSearchQuery(query: String, includeDomains: List<String>, excludeDomains: List<String>): String {
    require(query.isNotBlank()) { "A search query is required." }
    require(includeDomains.isEmpty() || excludeDomains.isEmpty()) { "Use either included or excluded domains." }
    require((includeDomains + excludeDomains).all(::isSearchDomain)) { "Search domains must be host names, without paths or search operators." }
    val filtered = buildString {
        append(query.trim())
        if (includeDomains.isNotEmpty()) append(" " + includeDomains.joinToString(" OR ") { "site:$it" })
        excludeDomains.forEach { append(" NOT site:$it") }
    }
    require(filtered.length <= 600 && filtered.split(Regex("\\s+")).size <= 75) {
        "Brave Search queries must fit within 600 characters and 75 words, including domain filters."
    }
    return filtered
}

internal fun isSearchDomain(domain: String): Boolean = domain.length <= 253 &&
    domain.split('.').all { label ->
        label.matches(Regex("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"))
    }

internal fun braveSearchFreshness(days: Int, clock: Clock): String {
    require(days >= 0) { "Search recency must be nonnegative." }
    val today = LocalDate.now(clock)
    val start = today.minusDays(days.toLong())
    require(start.year in 1..9999) { "Search recency is outside the supported date range." }
    return "${start}to$today"
}

internal fun matchesSearchDomains(url: String, includeDomains: List<String>, excludeDomains: List<String>): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
    val host = uri.host?.trimEnd('.') ?: return false
    fun matches(domain: String) = host.equals(domain, ignoreCase = true) || host.endsWith(".$domain", ignoreCase = true)
    return (includeDomains.isEmpty() || includeDomains.any(::matches)) && excludeDomains.none(::matches)
}
