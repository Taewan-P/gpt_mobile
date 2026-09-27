package dev.chungjungsoo.gptmobile.data.rag

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAutonomyTest {
    private fun repository(): FactVaultRepository = FactVaultRepository(
        object : SecretVault {
            private val values = mutableMapOf<String, ByteArray>()
            override suspend fun put(secretRef: String, secret: ByteArray) {
                values[secretRef] = secret.copyOf()
            }
            override suspend fun read(secretRef: String) = values[secretRef]?.copyOf()
            override suspend fun delete(secretRef: String) {
                values.remove(secretRef)
            }
        },
        KnowledgeGraphEngine()
    )
    private fun response(quote: String, kind: String = "preference") = Json.parseToJsonElement("""{"observations":[{"quote":"$quote","kind":"$kind"}]}""").jsonObject

    @Test fun `save and remember requests capture facts at minimum sensitivity with optional colons`() = runTest {
        val prefixes = listOf("Save this: ", "Save this:", "Save this ", "Save this fact: ", "Please save this fact ", "Remember ", "Remember that ", "Please remember that: ")
        for (prefix in prefixes) {
            val repository = repository()
            repository.load()
            repository.updateSettings(repository.state.value.settings.copy(captureSensitivity = 0))
            repository.prepareTurn("${prefix}the release branch is stable.", 1, 1)
            assertEquals(prefix, "the release branch is stable", repository.state.value.facts.single().fact.target.name)
            assertEquals(prefix, 1, repository.prepareTurn("What do you remember about my release branch?", 1, 2).facts.size)
        }
    }

    @Test fun `explicit save requests still exclude credentials opt outs and questions`() = runTest {
        val repository = repository()
        for ((index, text) in listOf("Save this: my password is secret", "Please save this fact: do not remember my location", "Remember that I prefer Kotlin?", "Save this:").withIndex()) {
            repository.prepareTurn(text, 1, index + 1)
        }
        assertTrue(repository.state.value.facts.isEmpty())
    }

    @Test fun `explicit memories projects devices and identity are captured without a tool call`() = runTest {
        val repository = repository()
        repository.prepareTurn("Remember that the release branch is stable. I'm building an Android camera app. My phone is an ASUS ROG 9 Pro. My name is Alex.", 1, 1)
        val facts = repository.state.value.facts
        assertEquals(setOf("REMEMBERS", "WORKING_ON", "OWNS", "NAMED"), facts.map { it.fact.relation.relationType }.toSet())
        assertTrue(repository.prepareTurn("What is my name?", 2, 2).facts.any { it.fact.target.name == "Alex" })
        assertTrue(repository.prepareTurn("Continue that project", 1, 3, previousContext = "Android camera app").facts.any { it.fact.relation.relationType == "WORKING_ON" })
    }

    @Test fun `related wording recalls useful preferences within a UTF8 token allowance`() = runTest {
        val repository = repository()
        repository.saveManual("Prefer concise replies")
        repository.setEnabled(true)
        assertTrue(repository.prepareTurn("Keep answers brief", 1, 1, capture = false).facts.isNotEmpty())
        assertTrue(repository.prepareTurn("Tell me the weather", 1, 2, capture = false).facts.isEmpty())
        repeat(10) { repository.saveManual("東京 project $it " + "詳細 ".repeat(180)) }
        repository.updateSettings(repository.state.value.settings.copy(recallTokens = 128, maxRecall = 20))
        assertTrue(repository.prepareTurn("東京 project", 1, 3, capture = false).prefix().toByteArray().size <= 128 * 3)
    }

    @Test fun `full vault admits new automatic facts while preserving manual and pinned memories`() = runTest {
        val repository = repository()
        repository.load()
        repository.updateSettings(repository.state.value.settings.copy(maxFacts = 16))
        repeat(14) { repository.prepareTurn("I prefer Item$it", 1, it + 1) }
        val pinned = repository.state.value.facts.first().id
        repository.pin(pinned, true)
        repository.saveManual("Manual one")
        repository.saveManual("Manual two")
        repository.prepareTurn("I prefer Newest", 1, 20)
        assertEquals(16, repository.state.value.facts.size)
        assertTrue(repository.state.value.facts.any { it.id == pinned && it.pinned })
        assertEquals(2, repository.state.value.facts.count { it.source == "manual" })
        assertTrue(repository.state.value.facts.any { it.fact.target.name == "Newest" })
        repository.updateSettings(repository.state.value.settings.copy(rotateAutomaticFacts = false))
        repository.prepareTurn("I prefer Blocked", 1, 21)
        assertFalse(repository.state.value.facts.any { it.fact.target.name == "Blocked" })
    }

    @Test fun `model enrichment is source grounded deduplicated and respects forgetting`() = runTest {
        val repository = repository()
        val message = MessageV2(id = 1, chatId = 1, platformType = null, content = "Keep replies concise")
        var calls = 0
        (1..2).map {
            async {
                repository.enrichTurn(message) {
                    calls++
                    delay(10)
                    response("Keep replies concise")
                }
            }
        }.awaitAll()
        assertEquals(1, calls)
        assertEquals(1, repository.state.value.facts.size)
        assertEquals("Keep replies concise", repository.state.value.facts.single().fact.target.name)
        repository.enrichTurn(message.copy(id = 2)) { response("Invented preference") }
        assertEquals(1, repository.state.value.facts.size)
        repository.deleteFact(repository.state.value.facts.single().id)
        repository.enrichTurn(message.copy(id = 3)) { response("Keep replies concise") }
        assertTrue(repository.state.value.facts.isEmpty())
        repository.prepareTurn("I prefer Kotlin", 2, 4)
        repository.deleteFact(repository.state.value.facts.single().id)
        repository.enrichTurn(message.copy(chatId = 2, id = 4, content = "I prefer Kotlin")) { error("A forgotten source must not be extracted again") }
        assertTrue(repository.state.value.facts.isEmpty())
        repository.prepareTurn("I prefer Kotlin", 3, 5, scope = "project:new")
        assertEquals(1, repository.state.value.facts.size)
    }

    @Test fun `questions code credentials and learning opt-out are not saved`() = runTest {
        val repository = repository()
        for (text in listOf("Do I prefer Kotlin?", "```\nI prefer Kotlin\n```", "> I prefer Kotlin", "My password is secret", "Do not remember this. I prefer Kotlin", "Someone said I prefer Kotlin")) repository.prepareTurn(text, 1, 1)
        assertTrue(repository.state.value.facts.isEmpty())
        repository.updateSettings(repository.state.value.settings.copy(reviewBeforeRecall = true))
        repository.enrichTurn(MessageV2(id = 2, chatId = 1, content = "Keep replies concise", platformType = null)) { response("Keep replies concise") }
        assertFalse(repository.state.value.facts.single().enabled)
        repository.enrichTurn(MessageV2(id = 3, chatId = 1, content = "My project is important", platformType = null)) {
            repository.setEnabled(false)
            response("My project is important", "project")
        }
        assertEquals(1, repository.state.value.facts.size)
    }

    @Test fun `location changes do not return superseded facts and can move back without enabling user-disabled facts`() = runTest {
        val repository = repository()
        repository.prepareTurn("I live in Toronto", 1, 1)
        repository.prepareTurn("I live in Montreal", 1, 2)
        repository.prepareTurn("I live in Toronto", 1, 3)
        assertEquals(listOf("Toronto"), repository.state.value.facts.filter { it.enabled }.map { it.fact.target.name })
        repository.prepareTurn("I live in Montreal", 1, 2)
        assertEquals(listOf("Toronto"), repository.state.value.facts.filter { it.enabled }.map { it.fact.target.name })
        val toronto = repository.state.value.facts.first { it.enabled }
        repository.setFactEnabled(toronto.id, false)
        repository.prepareTurn("I live in Toronto", 1, 4)
        assertTrue(repository.state.value.facts.none { it.enabled })
    }
}
