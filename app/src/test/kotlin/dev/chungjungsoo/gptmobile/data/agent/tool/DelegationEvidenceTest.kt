package dev.chungjungsoo.gptmobile.data.agent.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegationEvidenceTest {
    @Test fun `handoff enforces UTF8 budget and only retains observed citations`() {
        val sources = (1..20).map { DelegationSource("S$it", "https://example.org/$it?q=a%20b", "Source $it", pageRead = it == 1) }
        val summary = "Result 42 ms [S1]. Invalid [S99] https://invented.example/a. " + "東京 😀 ".repeat(2000)
        for (budget in listOf(128, 512, 1024, 4096)) {
            val handoff = delegationHandoff(summary, sources, listOf("Some pages were unavailable."), budget)
            val payload = Json.parseToJsonElement(handoff).jsonObject
            assertTrue(handoff.toByteArray().size <= budget * 3)
            assertFalse(handoff.contains("invented.example"))
            assertFalse(handoff.contains("S99"))
            assertTrue(payload.getValue("partial").jsonPrimitive.content == "true")
            val observed = sources.map { it.url }.toSet()
            assertTrue((payload["sources"] as JsonArray).all { it.jsonObject.getValue("url").jsonPrimitive.content in observed })
        }
    }

    @Test fun `warning only search degradation does not force partial evidence`() {
        val handoff = delegationHandoff(
            "Verified fact [S1].",
            listOf(DelegationSource("S1", "https://example.org/article", "Article", text = "Verified fact", pageRead = true)),
            listOf(
                "Some search engines were unavailable.",
                "Search 1 failed on one provider; another enabled search provider was attempted.",
                "Search 2 did not return usable evidence; remaining planned queries were still attempted."
            ),
            512
        )
        val payload = Json.parseToJsonElement(handoff).jsonObject
        assertEquals("false", payload.getValue("partial").jsonPrimitive.content)
        assertTrue((payload.getValue("limitations") as JsonArray).isEmpty())
        val warnings = payload.getValue("warnings") as JsonArray
        assertEquals(3, warnings.size)
        assertTrue(warnings.any { it.jsonPrimitive.content.contains("another enabled search provider") })
    }

    @Test fun `worker prompt is valid bounded JSON and preserves a relevant late passage`() {
        val page = "Unrelated introduction. ".repeat(200) + "The latency is exactly 42 ms after optimization. " + "Other material. ".repeat(200)
        val excerpt = relevantEvidence(page, "latency optimization", 1000)
        assertTrue(excerpt.contains("42 ms"))
        val prompt = delegationPrompt("Extract facts.", "Find latency", page + "\"😀".repeat(1000), 1500)
        assertTrue(prompt.toByteArray().size <= 1500)
        val payload = Json.parseToJsonElement(prompt.substringAfter('\n')).jsonObject
        assertEquals("Find latency", payload.getValue("task").jsonPrimitive.content)
        assertTrue(payload.getValue("untrusted_evidence").jsonPrimitive.content.contains("42 ms"))
    }

    @Test fun `links preserve percent encoding resolve relative paths and reject active schemes`() {
        assertEquals("https://example.org/a?q=a%20b", publicResearchUrl("https://example.org/a?q=a%20b#section"))
        assertNull(publicResearchUrl("javascript:alert(1)"))
        assertNull(publicResearchUrl("https://user:secret@example.org/"))
        val links = researchLinks("""<a href="/docs?q=a%20b&amp;lang=en">Docs</a><a href="javascript:bad()">Bad</a>""", "https://example.org/start")
        assertEquals(listOf("https://example.org/docs?q=a%20b&lang=en"), links)
    }
}
