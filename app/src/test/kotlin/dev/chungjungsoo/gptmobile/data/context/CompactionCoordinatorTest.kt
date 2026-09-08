package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.FakeModelCatalogRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactionCoordinatorTest {
    @Test
    fun `compacted context retains a fact from beyond ten turns`() = runBlocking {
        val store = InMemoryCompactionStore()
        val coordinator = coordinator(store, contextLimit = 800)
        val users = (0 until 12).map { index ->
            MessageV2(
                chatId = 7,
                content = if (index == 0) "Secret code is ORCHID-77" else "filler user $index ".repeat(if (index == 11) 0 else 8),
                platformType = null
            )
        }
        val assistants = (0 until 12).map { index ->
            listOf(
                MessageV2(
                    chatId = 7,
                    content = if (index == 0) "Noted the secret code." else "filler reply $index ".repeat(if (index == 11) 0 else 8),
                    platformType = "openai"
                )
            )
        }

        val outcome = coordinator.prepare(
            chatId = 7,
            userMessages = users,
            assistantMessages = assistants,
            platform = openaiPlatform(),
            force = true,
            outputReserve = 128
        )
        assertTrue((outcome as? CompactionOutcome.Failed)?.message ?: outcome.toString(), outcome is CompactionOutcome.Compacted)
        val compacted = outcome as CompactionOutcome.Compacted
        val blob = compacted.prepared.summaryText.orEmpty() + compacted.prepared.turns.joinToString { it.userMessage.content }
        assertTrue(blob.contains("ORCHID-77"))
        assertEquals(store.getCheckpoint(7, "openai")?.sourcePrefixFingerprint, compacted.checkpoint.sourcePrefixFingerprint)
    }

    @Test
    fun `summary plus tail keeps recent turns and the compacted prefix`() = runBlocking {
        val coordinator = coordinator(InMemoryCompactionStore(), contextLimit = 700)
        val users = (0 until 6).map { index ->
            MessageV2(chatId = 1, content = "user-$index " + "x".repeat(if (index == 5) 0 else 40), platformType = null)
        }
        val assistants = users.indices.map { index ->
            listOf(MessageV2(chatId = 1, content = "reply-$index", platformType = "openai"))
        }

        val outcome = coordinator.prepare(1, users, assistants, openaiPlatform(), force = true, outputReserve = 128) as CompactionOutcome.Compacted

        assertTrue(outcome.prepared.summaryText!!.contains("user-0"))
        assertTrue(outcome.prepared.turns.any { it.userMessage.content.contains("user-5") })
        assertEquals(CompactionRepresentation.TEXT_SUMMARY, outcome.prepared.representation)
    }

    @Test
    fun `editing a covered prefix invalidates the checkpoint`() = runBlocking {
        val store = InMemoryCompactionStore()
        val coordinator = coordinator(store, contextLimit = 800)
        val users = (0 until 8).map { index ->
            MessageV2(chatId = 3, content = "user-$index " + "y".repeat(if (index == 7) 0 else 30), platformType = null)
        }
        val assistants = users.indices.map { index ->
            listOf(MessageV2(chatId = 3, content = "reply-$index", platformType = "openai"))
        }
        val first = coordinator.prepare(3, users, assistants, openaiPlatform(), force = true, outputReserve = 128) as CompactionOutcome.Compacted
        val editedUsers = users.toMutableList().also {
            it[0] = it[0].copy(content = "edited first turn")
        }

        val second = coordinator.prepare(3, editedUsers, assistants, openaiPlatform(), force = true, outputReserve = 128) as CompactionOutcome.Compacted

        assertFalse(second.checkpoint.sourcePrefixFingerprint == first.checkpoint.sourcePrefixFingerprint)
        assertTrue(second.prepared.summaryText!!.contains("edited first turn"))
    }

    @Test
    fun `checkpoint write failure keeps the previous checkpoint`() = runBlocking {
        val store = InMemoryCompactionStore()
        val coordinator = coordinator(store, contextLimit = 800)
        val users = (0 until 8).map { index ->
            MessageV2(chatId = 4, content = "user-$index " + "z".repeat(if (index == 7) 0 else 30), platformType = null)
        }
        val assistants = users.indices.map { index ->
            listOf(MessageV2(chatId = 4, content = "reply-$index", platformType = "openai"))
        }
        val first = coordinator.prepare(4, users, assistants, openaiPlatform(), force = true, outputReserve = 128) as CompactionOutcome.Compacted
        store.failNextSave = true
        val longerUsers = users + MessageV2(chatId = 4, content = "extra " + "z".repeat(40), platformType = null)
        val longerAssistants = assistants + listOf(listOf(MessageV2(chatId = 4, content = "more", platformType = "openai")))

        val failed = coordinator.prepare(4, longerUsers, longerAssistants, openaiPlatform(), force = true, outputReserve = 128)

        assertTrue(failed is CompactionOutcome.Failed)
        assertEquals(first.checkpoint.sourcePrefixFingerprint, store.getCheckpoint(4, "openai")?.sourcePrefixFingerprint)
    }

    @Test
    fun `unknown model capacity does not guess a limit`() = runBlocking {
        val store = InMemoryCompactionStore()
        val resolver = ModelCapacityResolver(store, FakeModelCatalogRepository(), "")
        val coordinator = CompactionCoordinator(
            store = store,
            contextBuilder = ContextBuilder(),
            capacityResolver = resolver,
            textCompactor = boundedTestCompactor(),
            nativeCompactors = emptyList()
        )

        val outcome = coordinator.prepare(
            chatId = 5,
            userMessages = listOf(MessageV2(chatId = 5, content = "hello", platformType = null)),
            assistantMessages = listOf(emptyList()),
            platform = openaiPlatform()
        )

        assertTrue(outcome is CompactionOutcome.NeedsCapacity)
        assertEquals(null, store.getCheckpoint(5, "openai"))
    }

    @Test
    fun `local catalog context size is used when documented`() = runBlocking {
        val store = InMemoryCompactionStore()
        val catalog = FakeModelCatalogRepository(
            listOf(
                CatalogEntry(
                    id = "gemma3-1b-it",
                    socToModelFiles = mapOf("SM8750" to SocVariant(contextSize = 1280))
                )
            )
        )
        val resolver = ModelCapacityResolver(store, catalog, "SM8750")
        val resolution = resolver.resolve(
            PlatformV2(
                uid = "local",
                name = "Local",
                compatibleType = ClientType.LITERT_LM,
                apiUrl = "",
                model = "gemma3-1b-it",
                accelerator = "NPU",
                maxTokens = 4096
            )
        ) as ModelCapacityResolution.Known
        assertEquals(1280, resolution.capacity.detectedContextWindowTokens)
        assertEquals(1280, resolution.capacity.effectiveContextWindowTokens)
    }

    @Test
    fun `native openai window is stored and replayed as opaque items`() = runBlocking {
        val store = InMemoryCompactionStore()
        val native = object : ContextCompactor {
            override fun supports(platform: PlatformV2, estimatedTokens: Int) = true
            override suspend fun compact(input: CompactInput) = CompactOutput(
                representation = CompactionRepresentation.NATIVE_OPENAI,
                nativeItemsJson = "[{\"type\":\"compaction\",\"encrypted_content\":\"opaque-state\"}]",
                coveredTurnCount = input.turns.size
            )
        }
        val coordinator = CompactionCoordinator(
            store = store,
            contextBuilder = ContextBuilder(),
            capacityResolver = FixedCapacityResolver(4000),
            textCompactor = TextContextCompactor { _, _, _ -> error("text fallback should not run") },
            nativeCompactors = listOf(native)
        )
        val users = (0 until 6).map { index ->
            MessageV2(chatId = 9, content = "user-$index " + "n".repeat(if (index == 5) 0 else 40), platformType = null)
        }
        val assistants = users.indices.map { index ->
            listOf(MessageV2(chatId = 9, content = "reply-$index", platformType = "openai"))
        }

        val outcome = coordinator.prepare(9, users, assistants, openaiPlatform(), force = true, outputReserve = 128) as CompactionOutcome.Compacted
        assertEquals(CompactionRepresentation.NATIVE_OPENAI, outcome.prepared.representation)
        assertTrue(outcome.prepared.nativeItemsJson!!.contains("opaque-state"))
    }

    @Test
    fun `local engine max tokens is context capacity not output reserve`() {
        val platform = PlatformV2(
            uid = "local",
            name = "Local",
            compatibleType = ClientType.LITERT_LM,
            apiUrl = "",
            model = "gemma3-1b-it",
            maxTokens = 1280
        )
        assertEquals(null, resolvedOutputTokenCap(platform))
        assertEquals(OutputTokenBudget.LOCAL_OUTPUT_RESERVE, outputReserveTokens(platform))
    }

    @Test
    fun `thinking enabled small context uses full thinking output reserve`() = runBlocking {
        val platform = PlatformV2(
            uid = "anthropic",
            name = "Anthropic",
            compatibleType = ClientType.ANTHROPIC,
            apiUrl = "https://api.anthropic.com/",
            model = "claude-sonnet-4-6",
            reasoning = true
        )
        assertEquals(16_000, outputReserveTokens(platform))
        assertEquals(16_000, resolvedOutputTokenCap(platform))
        assertEquals("adaptive", anthropicThinkingPolicy(platform.model, true, false).config?.type)

        val coordinator = coordinator(InMemoryCompactionStore(), contextLimit = 8_000)
        val outcome = coordinator.validateDraft(
            platform,
            MessageV2(content = "Hi", platformType = null),
            outputReserve = outputReserveTokens(platform)
        )
        assertTrue(outcome is CompactionOutcome.InputTooLarge)
    }

    @Test
    fun `batched text compaction retains facts from first and last prefix batches`() = runBlocking {
        val coordinator = coordinator(InMemoryCompactionStore(), contextLimit = 900)
        val users = (0 until 10).map { index ->
            MessageV2(
                chatId = 11,
                content = if (index == 0) {
                    "ALPHA-SECRET"
                } else if (index == 7) {
                    "OMEGA-SECRET"
                } else {
                    "filler-$index " + "x".repeat(48)
                },
                platformType = null
            )
        }
        val assistants = users.indices.map { index ->
            listOf(MessageV2(chatId = 11, content = "reply-$index", platformType = "openai"))
        }
        val outcome = coordinator.prepare(11, users, assistants, openaiPlatform(), force = true, outputReserve = 128) as CompactionOutcome.Compacted
        val blob = outcome.prepared.summaryText.orEmpty() + outcome.prepared.turns.joinToString { it.userMessage.content }
        assertTrue(blob.contains("ALPHA-SECRET"))
        assertTrue(blob.contains("OMEGA-SECRET"))
    }

    @Test
    fun `oversized historical turn is split into bounded summary batches`() = runBlocking {
        val coordinator = coordinator(InMemoryCompactionStore(), contextLimit = 800)
        val users = listOf(
            MessageV2(chatId = 12, content = "NEEDLE-42 " + "w".repeat(4000), platformType = null),
            MessageV2(chatId = 12, content = "current", platformType = null)
        )
        val assistants = listOf(
            listOf(MessageV2(chatId = 12, content = "ack", platformType = "openai")),
            emptyList()
        )
        val outcome = coordinator.prepare(12, users, assistants, openaiPlatform(), force = true, outputReserve = 128)
        assertTrue((outcome as? CompactionOutcome.Failed)?.message ?: outcome.toString(), outcome is CompactionOutcome.Compacted)
        val compacted = outcome as CompactionOutcome.Compacted
        assertTrue(compacted.prepared.summaryText!!.contains("NEEDLE-42"))
    }

    @Test
    fun `selected revision changes the source fingerprint`() {
        val first = ConversationTurn(
            userMessage = MessageV2(content = "q", platformType = null),
            assistantMessage = MessageV2(
                content = "latest",
                platformType = "openai",
                revisions = listOf(AssistantRevision(content = "older", createdAt = 1)),
                activeRevisionIndex = -1
            ),
            isCurrentTurn = false
        )
        val selected = first.copy(
            assistantMessage = first.assistantMessage!!.copy(
                content = "older",
                activeRevisionIndex = 0
            )
        )
        val platform = openaiPlatform()
        assertTrue(
            CompactionKeys.sourceFingerprint(listOf(first), platform) !=
                CompactionKeys.sourceFingerprint(listOf(selected), platform)
        )
    }

    @Test
    fun `attributed compaction turns include tool evidence`() {
        val turns = listOf(
            ConversationTurn(
                userMessage = MessageV2(content = "search please", platformType = null),
                assistantMessage = MessageV2(content = "calling tool", platformType = "openai"),
                isCurrentTurn = false
            ),
            ConversationTurn(
                userMessage = MessageV2(content = "thanks", platformType = null),
                assistantMessage = null,
                isCurrentTurn = true
            )
        )
        val attributed = attributedTurnsForCompaction(
            turns,
            listOf(ToolEvidence("run-1", "web_search", "source-77", false))
        )
        assertTrue(attributed.any { it.userMessage.content.contains("source-77") })
        assertTrue(attributed.last().userMessage.content.contains("thanks"))
    }

    @Test
    fun `compaction state is emitted before and after the operation`() = runBlocking {
        val states = mutableListOf<Boolean>()
        val coordinator = coordinator(InMemoryCompactionStore(), contextLimit = 800)
        val users = (0 until 8).map { index ->
            MessageV2(chatId = 13, content = "user-$index " + "y".repeat(if (index == 7) 0 else 30), platformType = null)
        }
        val assistants = users.indices.map { index ->
            listOf(MessageV2(chatId = 13, content = "reply-$index", platformType = "openai"))
        }
        coordinator.prepare(
            chatId = 13,
            userMessages = users,
            assistantMessages = assistants,
            platform = openaiPlatform(),
            force = true,
            outputReserve = 128,
            onCompactionState = { active -> states += active }
        )
        assertEquals(listOf(true, false), states)
    }

    @Test
    fun `draft capacity check does not compact history`() = runBlocking {
        val coordinator = coordinator(InMemoryCompactionStore(), contextLimit = 800)
        val ok = coordinator.validateDraft(
            openaiPlatform(),
            MessageV2(content = "short", platformType = null)
        )
        assertEquals(null, ok)
        val tooBig = coordinator.validateDraft(
            openaiPlatform(),
            MessageV2(content = "huge ".repeat(2000), platformType = null)
        )
        assertTrue(tooBig is CompactionOutcome.InputTooLarge)
    }

    @Test
    fun `corrupt checkpoint rebuilds from the full transcript`() = runBlocking {
        val store = InMemoryCompactionStore()
        val coordinator = coordinator(store, 800)
        val users = listOf(MessageV2(content = "ORCHID-77", platformType = null), MessageV2(content = "current", platformType = null))
        val assistants = listOf(listOf(MessageV2(content = "ack", platformType = "openai")), emptyList())
        val first = coordinator.prepare(40, users, assistants, openaiPlatform(), force = true) as CompactionOutcome.Compacted
        store.saveCheckpoint(first.checkpoint.copy(serializedWorkingContext = "{broken"))
        val restored = coordinator.prepare(40, users, assistants, openaiPlatform()) as CompactionOutcome.Ready
        assertEquals("ORCHID-77", restored.prepared.turns.first().userMessage.content)
        assertEquals(0, restored.prepared.coveredTurnCount)
    }

    @Test
    fun `uncompacted context sends full attributed tool outcome once`() = runBlocking {
        val outcomeText = "x".repeat(1500) + "SOURCE-END"
        val result = coordinator(InMemoryCompactionStore(), 4000).prepare(
            41,
            listOf(MessageV2(content = "current", platformType = null)),
            listOf(emptyList()),
            openaiPlatform(),
            toolEvidence = listOf(ToolEvidence("run", "web_search", outcomeText, false))
        ) as CompactionOutcome.Ready
        val text = result.prepared.turns.joinToString { it.userMessage.content }
        assertTrue(text.contains(outcomeText))
        assertEquals(1, Regex("SOURCE-END").findAll(text).count())
        assertEquals("current", result.prepared.turns.last().userMessage.content)
    }

    @Test
    fun `nonshrinking summary fails without replacing checkpoint`() = runBlocking {
        val store = InMemoryCompactionStore()
        val users = listOf(MessageV2(content = "long ".repeat(3000), platformType = null), MessageV2(content = "current", platformType = null))
        var calls = 0
        val coordinator = CompactionCoordinator(
            store,
            ContextBuilder(),
            FixedCapacityResolver(800),
            TextContextCompactor { _, _, turns ->
                calls++
                turns.joinToString { it.userMessage.content }.repeat(2)
            },
            emptyList()
        )
        val result = coordinator.prepare(42, users, listOf(emptyList(), emptyList()), openaiPlatform())
        assertTrue(result is CompactionOutcome.Failed)
        assertTrue(calls in 1..2)
        assertEquals(null, store.getCheckpoint(42, "openai"))
    }

    @Test
    fun `text summarization keeps images and disables background generation`() = runBlocking {
        val image = dev.chungjungsoo.gptmobile.data.model.ChatAttachment("/tmp/image", "", "image.png", "image/png", 20)
        val turn = ConversationTurn(MessageV2(content = "remember image", attachments = listOf(image), platformType = null), null, false)
        var seen = false
        val compactor = TextContextCompactor { platform, _, turns ->
            seen = turns.any { image in it.userMessage.attachments }
            assertFalse(platform.resumableReplies)
            assertEquals(128, platform.maxTokens)
            "image description"
        }
        compactor.compact(CompactInput(openaiPlatform().copy(resumableReplies = true), null, listOf(turn), emptyList(), null, 128))
        assertTrue(seen)
    }

    @Test
    fun `provider usage calibrates newer context and ignores edited prefix`() = runBlocking {
        val coordinator = coordinator(InMemoryCompactionStore(), 800)
        val users = listOf(MessageV2(content = "first", platformType = null))
        val replies = listOf(listOf(MessageV2(content = "ack", platformType = "openai")))
        val platform = openaiPlatform()
        val prepared = (coordinator.prepare(43, users, replies, platform) as CompactionOutcome.Ready).prepared
        coordinator.recordUsage(43, platform, ContextBuilder().build(users, replies, platform), emptyList(), emptyList(), prepared, 650)
        val nextUsers = users + MessageV2(content = "next ".repeat(20), platformType = null)
        val nextReplies = replies + listOf(emptyList())
        assertTrue(coordinator.prepare(43, nextUsers, nextReplies, platform) is CompactionOutcome.Compacted)
        val edited = nextUsers.toMutableList().also { it[0] = it[0].copy(content = "edited") }
        val rebuilt = coordinator.prepare(43, edited, nextReplies, platform)
        assertTrue(rebuilt is CompactionOutcome.Ready)
        assertTrue((rebuilt as CompactionOutcome.Ready).prepared.tokenBudget.estimatedInputTokens < 650)
    }

    @Test
    fun `tool evidence from a later turn does not invalidate a checkpoint`() = runBlocking {
        val store = InMemoryCompactionStore()
        var summaries = 0
        val coordinator = CompactionCoordinator(
            store,
            ContextBuilder(),
            FixedCapacityResolver(800),
            TextContextCompactor { _, _, _ ->
                summaries++
                "Earlier work and SOURCE-OLD are complete."
            },
            emptyList()
        )
        val users = (0 until 6).map { index ->
            MessageV2(content = "user-$index " + "x".repeat(80), platformType = null)
        }
        val replies = users.indices.map { index ->
            listOf(
                MessageV2(
                    content = "reply-$index",
                    platformType = "openai",
                    currentRunId = "run-$index"
                )
            )
        }
        val oldEvidence = ToolEvidence("run-0", "search", "SOURCE-OLD", false)
        val first = coordinator.prepare(
            44,
            users,
            replies,
            openaiPlatform(),
            toolEvidence = listOf(oldEvidence),
            force = true
        ) as CompactionOutcome.Compacted

        val nextUsers = users + MessageV2(content = "next", platformType = null)
        val nextReplies = replies + listOf(
            listOf(MessageV2(content = "done", platformType = "openai", currentRunId = "run-new"))
        )
        val restored = coordinator.prepare(
            44,
            nextUsers,
            nextReplies,
            openaiPlatform(),
            toolEvidence = listOf(oldEvidence, ToolEvidence("run-new", "date", "SOURCE-NEW", false))
        ) as CompactionOutcome.Ready

        assertEquals(first.checkpoint.coveredTurnCount, restored.prepared.coveredTurnCount)
        assertTrue(restored.prepared.turns.any { it.userMessage.content.contains("SOURCE-NEW") })
        assertEquals(1, summaries)
    }

    @Test(expected = IllegalStateException::class)
    fun `text summary that exceeds target is rejected`() = runBlocking {
        TextContextCompactor { _, _, _ -> "x".repeat(2_000) }.compact(
            CompactInput(openaiPlatform(), null, emptyList(), emptyList(), null, 32)
        )
        Unit
    }

    private fun boundedTestCompactor(): TextContextCompactor {
        var calls = 0
        return TextContextCompactor { _, _, transcript ->
            check(++calls <= 64) { "Compaction did not make bounded source progress" }
            shortSummary(transcript.joinToString("\n") { it.userMessage.content + "\n" + it.assistantMessage?.content.orEmpty() })
        }
    }

    private fun shortSummary(transcript: String): String {
        val kept = Regex("ORCHID-77|edited first turn|user-[0-9]+|ALPHA-SECRET|OMEGA-SECRET|NEEDLE-42").findAll(transcript).map { it.value }.distinct().joinToString(" ")
        return "SUMMARY " + kept.ifBlank { "checkpoint" }
    }

    private fun coordinator(store: InMemoryCompactionStore, contextLimit: Int) = CompactionCoordinator(
        store = store,
        contextBuilder = ContextBuilder(),
        capacityResolver = FixedCapacityResolver(contextLimit),
        textCompactor = boundedTestCompactor(),
        nativeCompactors = emptyList()
    )

    private fun openaiPlatform() = PlatformV2(
        uid = "openai",
        name = "OpenAI",
        compatibleType = ClientType.OPENAI,
        apiUrl = "https://api.openai.com/v1/",
        model = "gpt-5.6",
        maxTokens = 128
    )
}

private class FixedCapacityResolver(
    private val tokens: Int
) : ModelCapacityResolver(InMemoryCompactionStore(), FakeModelCatalogRepository(), "") {
    override suspend fun resolve(platform: PlatformV2): ModelCapacityResolution = ModelCapacityResolution.Known(
        ModelCapacity(
            platformUid = platform.uid,
            endpoint = platform.apiUrl,
            model = platform.model,
            detectedContextWindowTokens = tokens
        )
    )
}
