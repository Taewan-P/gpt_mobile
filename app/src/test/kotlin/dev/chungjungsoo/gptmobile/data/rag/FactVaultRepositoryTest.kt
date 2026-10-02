package dev.chungjungsoo.gptmobile.data.rag

import dev.chungjungsoo.gptmobile.data.security.SecretVault
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FactVaultRepositoryTest {
    @Test
    fun `new capture recall and connected memory controls persist with normalized budgets`() = runBlocking {
        val storage = MemoryVault()
        val repository = FactVaultRepository(storage, KnowledgeGraphEngine())
        repository.updateSettings(FactVaultSettings(localModelLearning = false, rotateAutomaticFacts = false, maxCapturePerMessage = 100, recallTokens = 1, maxRecall = 30, externalRecallEnabled = true, externalMemoryConnections = setOf("memory-server"), externalMemoryScopes = mapOf("memory-server" to "personal-space")))
        val restored = FactVaultRepository(storage, KnowledgeGraphEngine())
        restored.load()
        val settings = restored.state.value.settings
        assertFalse(settings.localModelLearning)
        assertFalse(settings.rotateAutomaticFacts)
        assertEquals(16, settings.maxCapturePerMessage)
        assertEquals(128, settings.recallTokens)
        assertEquals(20, settings.maxRecall)
        assertTrue(settings.externalRecallEnabled)
        assertEquals(setOf("memory-server"), settings.externalMemoryConnections)
        assertEquals("personal-space", settings.externalMemoryScopes["memory-server"])
    }

    @Test
    fun `sensitivity persists and changes capture without removing saved facts`() = runBlocking {
        val storage = MemoryVault()
        val repository = FactVaultRepository(storage, KnowledgeGraphEngine())
        repository.load()
        repository.updateSettings(repository.state.value.settings.copy(captureSensitivity = 0))
        repository.prepareTurn("I prefer Kotlin. I am learning Blender. I want a new workstation.", 1, 1)
        assertTrue(repository.state.value.facts.isEmpty())
        repository.updateSettings(repository.state.value.settings.copy(captureSensitivity = 50))
        repository.prepareTurn("I prefer Kotlin. I am learning Blender. I want a new workstation.", 1, 2)
        assertEquals(setOf("PREFERS", "WORKING_ON"), repository.state.value.facts.map { it.fact.relation.relationType }.toSet())
        repository.updateSettings(repository.state.value.settings.copy(captureSensitivity = 100, reviewBeforeRecall = true))
        repository.prepareTurn("I am learning Blender. I want a new workstation. Do I need a car? I do not want a boat.", 1, 3)
        assertEquals(3, repository.state.value.facts.size)
        assertEquals(1, repository.state.value.facts.count { !it.enabled })
        val restored = FactVaultRepository(storage, KnowledgeGraphEngine())
        restored.load()
        assertEquals(100, restored.state.value.settings.captureSensitivity)
        restored.updateSettings(restored.state.value.settings.copy(captureSensitivity = 0))
        assertEquals(3, restored.state.value.facts.size)
    }

    @Test
    fun `large memory shards survive reload and failed manifest publish keeps the old snapshot`() = runBlocking {
        val storage = MemoryVault()
        val repository = FactVaultRepository(storage, KnowledgeGraphEngine())
        repository.load()
        repeat(70) { repository.saveManual("Memory $it " + "a".repeat(890)) }
        assertTrue(storage.values.keys.any { it.startsWith("memory-part-") })
        assertTrue(storage.values.values.all { it.size <= 64 * 1024 })
        val restored = FactVaultRepository(storage, KnowledgeGraphEngine())
        restored.load()
        assertEquals(70, restored.state.value.facts.size)
        val before = storage.values.keys.toSet()
        storage.failManifest = true
        assertTrue(runCatching { restored.saveManual("New memory") }.isFailure)
        assertEquals(before, storage.values.keys)
        storage.failManifest = false
        restored.load()
        assertEquals(70, restored.state.value.facts.size)
    }

    @Test
    fun `memory tools only store user quotes and honor review cloud and tombstone controls`() = runBlocking {
        val storage = MemoryVault()
        val repository = FactVaultRepository(storage, KnowledgeGraphEngine())
        repository.load()
        repository.updateSettings(repository.state.value.settings.copy(reviewBeforeRecall = true, allowCloudRecall = false))
        val message = dev.chungjungsoo.gptmobile.data.database.entity.MessageV2(id = 11, chatId = 7, content = "Remember that I am allergic to peanuts", platformType = null)
        val id = repository.rememberUserText("I am allergic to peanuts", message)
        assertTrue(repository.visibleFacts(7, true).isEmpty())
        assertTrue(runCatching { repository.rememberUserText("I am allergic to shellfish", message) }.isFailure)
        repository.setFactEnabled(id, true)
        repository.pin(id, true)
        val restored = FactVaultRepository(storage, KnowledgeGraphEngine())
        assertEquals(1, restored.visibleFacts(7, true).size)
        assertTrue(restored.state.value.facts.single().pinned)
        assertTrue(restored.visibleFacts(7, false).isEmpty())
        restored.deleteFact(id)
        assertTrue(runCatching { restored.rememberUserText("I am allergic to peanuts", message) }.isFailure)
        assertTrue(restored.state.value.facts.isEmpty())
    }

    @Test
    fun `same fact has independent identity and forgetting in each project`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.prepareTurn("I prefer Kotlin", 1, 1, scope = "project:one")
        repository.prepareTurn("I prefer Kotlin", 2, 2, scope = "project:two")
        assertEquals(2, repository.state.value.facts.size)
        repository.deleteFact(repository.state.value.facts.first { it.scope == "project:one" }.id)
        repository.prepareTurn("I prefer Kotlin", 1, 3, scope = "project:one")
        assertEquals(1, repository.state.value.facts.size)
        assertTrue(repository.prepareTurn("Kotlin", 1, 4, capture = false, scope = "project:one").facts.isEmpty())
        assertEquals(1, repository.prepareTurn("Kotlin", 2, 4, capture = false, scope = "project:two").facts.size)
    }

    @Test
    fun `cloud and chat scope are enforced after settings reload`() = runBlocking {
        val storage = MemoryVault()
        val repository = FactVaultRepository(storage, KnowledgeGraphEngine())
        repository.setEnabled(true)
        repository.prepareTurn("I prefer Kotlin", 1, 1)
        repository.updateSettings(FactVaultSettings(allowCloudRecall = false, sameChatOnly = true))
        val restored = FactVaultRepository(storage, KnowledgeGraphEngine())
        assertTrue(restored.prepareTurn("Kotlin", 1, 2).facts.isEmpty())
        assertTrue(restored.prepareTurn("Kotlin", 2, 2, isLocal = true).facts.isEmpty())
        assertEquals(1, restored.prepareTurn("Kotlin", 1, 2, isLocal = true).facts.size)
    }

    @Test
    fun `learning and recall switches work independently`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.setEnabled(true)
        repository.updateSettings(FactVaultSettings(recallEnabled = false))
        repository.prepareTurn("I prefer Kotlin", 1, 1)
        assertTrue(repository.prepareTurn("Kotlin", 1, 2).facts.isEmpty())
        repository.updateSettings(FactVaultSettings(learningEnabled = false))
        repository.prepareTurn("I prefer Java", 1, 3)
        assertEquals(1, repository.state.value.facts.size)
        assertEquals(1, repository.prepareTurn("Kotlin", 1, 4).facts.size)
    }

    @Test
    fun `review requirement prevents new facts being recalled before approval`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.setEnabled(true)
        repository.updateSettings(FactVaultSettings(reviewBeforeRecall = true, maxRecall = 1))
        repository.prepareTurn("I prefer Kotlin", 1, 1)
        assertTrue(repository.prepareTurn("Kotlin", 1, 2).facts.isEmpty())
        val fact = repository.state.value.facts.single()
        assertTrue(fact.savedAtMillis > 0)
        assertFalse(fact.enabled)
        repository.setFactEnabled(fact.id, true)
        assertEquals(1, repository.prepareTurn("Kotlin", 1, 3).facts.size)
    }

    @Test
    fun `expired dated facts are removed without expiring legacy facts`() = runBlocking {
        val storage = MemoryVault()
        val repository = FactVaultRepository(storage, KnowledgeGraphEngine())
        repository.setEnabled(true)
        repository.prepareTurn("I prefer Kotlin\nI prefer Java", 1, 1)
        val old = repository.state.value.copy(settings = FactVaultSettings(retentionDays = 30), facts = repository.state.value.facts.mapIndexed { i, fact -> fact.copy(savedAtMillis = if (i == 0) 1L else 0L) })
        storage.values[FactVaultRepository.VAULT_REFERENCE] = kotlinx.serialization.json.Json.encodeToString(FactVaultSnapshot.serializer(), old).encodeToByteArray()
        repository.prepareTurn("What do I prefer?", 1, 2)
        assertEquals(1, repository.state.value.facts.size)
        assertEquals(0L, repository.state.value.facts.single().savedAtMillis)
    }

    @Test
    fun `same turn exclusions do not consume the recall limit`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.setEnabled(true)
        repository.prepareTurn((1..5).joinToString("\n") { "I prefer Item$it" }, 1, 1)
        repository.prepareTurn("I prefer Kotlin", 1, 2)
        val recall = repository.prepareTurn("What are my preferences?", 1, 1)
        assertEquals(listOf("Kotlin"), recall.facts.map { it.fact.target.name })
    }

    @Test
    fun `clear recovers an unreadable vault`() = runBlocking {
        val storage = MemoryVault()
        storage.values[FactVaultRepository.VAULT_REFERENCE] = "broken json".encodeToByteArray()
        val repository = FactVaultRepository(storage, KnowledgeGraphEngine())
        assertTrue(runCatching { repository.load() }.isFailure)
        repository.clear()
        repository.load()
        assertTrue(repository.state.value.facts.isEmpty())
        assertFalse(repository.state.value.enabled)
    }

    @Test
    fun `specific recall is not crowded out by unrelated personal facts`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.setEnabled(true)
        repository.prepareTurn((1..6).joinToString("\n") { "I prefer Item$it" }, 1, 1)
        repository.prepareTurn("I prefer Kotlin", 1, 2)
        val recall = repository.prepareTurn("Help me with Kotlin", 1, 3)
        assertEquals(listOf("Kotlin"), recall.facts.map { it.fact.target.name })
        assertTrue(repository.prepareTurn("Tell me the weather", 1, 4).facts.isEmpty())
    }

    @Test
    fun `questions negation and quoted statements are not learned as facts`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.setEnabled(true)
        for (text in listOf("Do I prefer Kotlin?", "I do not like Kotlin", "I don't like Kotlin", "Someone said I prefer Kotlin", "My friend claims I prefer Kotlin", "\"I prefer Kotlin\"")) {
            repository.prepareTurn(text, 1, 1)
        }
        assertTrue(repository.state.value.facts.isEmpty())
        repository.prepareTurn("I prefer Kotlin", 1, 2)
        assertEquals(1, repository.state.value.facts.size)
    }

    @Test
    fun `multiword location correction deactivates the previous location`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.setEnabled(true)
        repository.prepareTurn("I live in New York", 1, 1)
        assertTrue(repository.state.value.facts.any { it.fact.target.name == "New York" })
        repository.prepareTurn("I live in San Francisco", 1, 2)
        assertTrue(repository.state.value.facts.any { it.fact.target.name == "San Francisco" && it.enabled })
        assertFalse(repository.state.value.facts.any { it.fact.target.name == "New York" && it.enabled })
    }

    @Test
    fun `native graph writes stay grounded in the current user message`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.setEnabled(true)
        val message = dev.chungjungsoo.gptmobile.data.database.entity.MessageV2(
            id = 77,
            chatId = 12,
            content = "I own a Pixel 8 and prefer concise answers",
            platformType = null
        )

        val relationId = repository.rememberGraphFact(
            entityName = "User",
            entityType = "PERSON",
            relationType = "OWNS",
            targetName = "Pixel 8",
            targetType = "DEVICE",
            message = message
        )
        val observationId = repository.rememberGraphFact(
            entityName = "User",
            entityType = "PERSON",
            relationType = "OBSERVATION",
            targetName = "prefer concise answers",
            targetType = "OBSERVATION",
            message = message
        )

        assertTrue(repository.state.value.facts.any { it.id == relationId && it.fact.relation.relationType == "OWNS" })
        assertTrue(repository.state.value.facts.any { it.id == observationId && it.fact.target.name == "prefer concise answers" })
        assertTrue(
            runCatching {
                repository.rememberGraphFact("User", "PERSON", "OWNS", "MacBook", "DEVICE", message)
            }.isFailure
        )
    }

    private class MemoryVault : SecretVault {
        val values = mutableMapOf<String, ByteArray>()
        var failWrites = false
        var failManifest = false
        override suspend fun put(secretRef: String, secret: ByteArray) {
            check(!failWrites && !(failManifest && secretRef == FactVaultRepository.VAULT_REFERENCE)) { "Storage unavailable" }
            check(secret.size <= 64 * 1024) { "Oversize secret" }
            values[secretRef] = secret.copyOf()
        }
        override suspend fun read(secretRef: String) = values[secretRef]?.copyOf()
        override suspend fun delete(secretRef: String) {
            values.remove(secretRef)
        }
    }

    @Test
    fun `default learning persists through vault reload`() = runBlocking {
        val storage = MemoryVault()
        val repository = FactVaultRepository(storage, KnowledgeGraphEngine())
        assertTrue(repository.prepareTurn("I prefer Kotlin", 1, 1).facts.isEmpty())
        assertEquals(1, repository.state.value.facts.size)
        repository.setEnabled(true)
        assertTrue(repository.prepareTurn("I prefer Kotlin", 1, 1).facts.isEmpty())
        val restored = FactVaultRepository(storage, KnowledgeGraphEngine())
        val recalled = restored.prepareTurn("Help me with Kotlin", 2, 2)
        assertEquals(1, recalled.facts.size)
        assertEquals("User", recalled.facts.single().fact.entity.name)
        assertTrue(recalled.prefix().contains("Kotlin"))
        assertEquals("User preference", recalled.references.single().label)
        assertFalse(recalled.references.toString().contains("Kotlin"))
    }

    @Test
    fun `disabled and deleted facts stay excluded across retries`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.setEnabled(true)
        repository.prepareTurn("I prefer Kotlin", 1, 1)
        val id = repository.state.value.facts.single().id
        repository.setFactEnabled(id, false)
        repository.prepareTurn("I prefer Kotlin", 1, 1)
        assertTrue(repository.prepareTurn("Kotlin", 1, 2).facts.isEmpty())
        assertFalse(repository.state.value.facts.single().enabled)
        repository.setFactEnabled(id, true)
        assertEquals(1, repository.prepareTurn("What are my preferences?", 1, 3).facts.size)
        repository.deleteFact(id)
        repository.prepareTurn("I prefer Kotlin", 1, 1)
        assertTrue(repository.state.value.facts.isEmpty())
        assertTrue(repository.prepareTurn("Kotlin", 1, 4).facts.isEmpty())
    }

    @Test
    fun `parallel profiles do not recall facts learned in the same turn`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.setEnabled(true)
        val recalls = (1..8).map { async { repository.prepareTurn("I prefer Kotlin", 5, 8) } }.awaitAll()
        assertTrue(recalls.all { it.facts.isEmpty() })
        assertEquals(1, repository.state.value.facts.size)
        assertEquals(1, repository.prepareTurn("Kotlin", 5, 9).facts.size)
        assertTrue(repository.prepareTurn("Weather in Paris", 5, 10).facts.isEmpty())
    }

    @Test
    fun `turn reload observes restored vault and clear disables learning`() = runBlocking {
        val storage = MemoryVault()
        val repository = FactVaultRepository(storage, KnowledgeGraphEngine())
        repository.setEnabled(true)
        repository.prepareTurn("I prefer Kotlin", 1, 1)
        storage.values.clear()
        assertTrue(repository.prepareTurn("Kotlin", 1, 2).facts.isEmpty())
        assertFalse(repository.state.value.enabled)
        repository.setEnabled(true)
        repository.prepareTurn("I use Android", 1, 3)
        repository.clear()
        repository.prepareTurn("I use Android", 1, 3)
        assertTrue(repository.state.value.facts.isEmpty())
        assertFalse(repository.state.value.enabled)
    }

    @Test
    fun `failed storage mutation preserves the last saved state`() = runBlocking {
        val storage = MemoryVault()
        val repository = FactVaultRepository(storage, KnowledgeGraphEngine())
        repository.setEnabled(true)
        repository.prepareTurn("I prefer Kotlin", 1, 1)
        storage.failWrites = true
        var failed = false
        try {
            repository.deleteFact(repository.state.value.facts.single().id)
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(1, repository.state.value.facts.size)
        assertEquals(1, repository.prepareTurn("Kotlin", 1, 2).facts.size)
    }

    @Test
    fun `recall and storage remain bounded`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.setEnabled(true)
        repository.updateSettings(repository.state.value.settings.copy(maxFacts = 64))
        (1..80).chunked(8).forEachIndexed { index, items ->
            repository.prepareTurn(items.joinToString("\n") { "I prefer Item$it" }, 1, index + 1)
        }
        assertEquals(64, repository.state.value.facts.size)
        assertEquals(5, repository.prepareTurn("What do I prefer?", 1, 2).facts.size)
        repository.setEnabled(false)
        assertTrue(repository.prepareTurn("What do I prefer?", 1, 3).facts.isEmpty())
        assertEquals(64, repository.state.value.facts.size)
    }

    @Test
    fun `new vault defaults on but saved legacy off and explicit off survive reload`() = runBlocking {
        val storage = MemoryVault()
        val fresh = FactVaultRepository(storage, KnowledgeGraphEngine())
        fresh.load()
        assertTrue(fresh.state.value.enabled)
        fresh.load()
        assertTrue(fresh.state.value.enabled)
        fresh.setEnabled(false)
        val restored = FactVaultRepository(storage, KnowledgeGraphEngine())
        restored.load()
        assertFalse(restored.state.value.enabled)
        storage.values[FactVaultRepository.VAULT_REFERENCE] = "{}".encodeToByteArray()
        restored.load()
        assertFalse(restored.state.value.enabled)
    }

    @Test
    fun `recall tool never learns model provided queries`() = runBlocking {
        val repository = FactVaultRepository(MemoryVault(), KnowledgeGraphEngine())
        repository.prepareTurn("I prefer Kotlin", 1, 1)
        repository.prepareTurn("I prefer Java", 1, 2, capture = false)
        assertEquals(listOf("Kotlin"), repository.state.value.facts.map { it.fact.target.name })
    }
}
