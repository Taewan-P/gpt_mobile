package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.catalog.CatalogCapabilities
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.localruntime.FakeLocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.localruntime.LocalEngineHolder
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryMessage
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryRole
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntimeEvent
import dev.chungjungsoo.gptmobile.data.localruntime.ScriptedToolInvocation
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.FakeLocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.FakeModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmAdapterTest {
    @Test
    fun `streams text deltas in order and maps thinking channel`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(
                    LocalRuntimeEvent.ThinkingDelta("plan"),
                    LocalRuntimeEvent.TextDelta("hel"),
                    LocalRuntimeEvent.TextDelta("lo"),
                    LocalRuntimeEvent.Done
                )
            )
        }
        val adapter = adapter(runtime)

        val events = adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(
                ProviderEvent.ThinkingDelta("plan"),
                ProviderEvent.TextDelta("hel"),
                ProviderEvent.TextDelta("lo"),
                ProviderEvent.Completed
            ),
            events
        )
        assertEquals(listOf("hello"), runtime.sendMessageCalls)
        assertEquals(1, runtime.createConversationCalls.size)
        assertEquals(0, runtime.closeConversationCalls)
    }

    @Test
    fun `seeds conversation from prior history on first turn`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime)
        val historyTurns = listOf(
            completedTurn("first", "answer"),
            pendingTurn("second")
        )

        adapter.openSession(historyTurns, localPlatform()).streamRound(emptyList(), emptyList()).toList()

        val config = runtime.createConversationCalls.single()
        assertEquals("Be concise", config.systemPrompt)
        assertEquals(2, config.initialMessages.size)
        assertEquals(LocalHistoryRole.USER, config.initialMessages[0].role)
        assertEquals("first", config.initialMessages[0].text)
        assertEquals(LocalHistoryRole.MODEL, config.initialMessages[1].role)
        assertEquals("answer", config.initialMessages[1].text)
        assertEquals(listOf("second"), runtime.sendMessageCalls)
    }

    @Test
    fun `happy path follow-up sends only the new message`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("answer"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()

        adapter.openSession(turns("first"), platform).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(completedTurn("first", "answer"), pendingTurn("second")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(1, runtime.createConversationCalls.size)
        assertTrue(runtime.createConversationCalls.single().initialMessages.isEmpty())
        assertEquals(listOf("first", "second"), runtime.sendMessageCalls)
        assertEquals(0, runtime.closeConversationCalls)
    }

    @Test
    fun `mixed-platform history with an unconsumed user turn rebuilds the conversation`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("answer"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()

        adapter.openSession(turns("first"), platform).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(
                completedTurn("first", "answer"),
                ConversationTurn(
                    userMessage = MessageV2(content = "other-platform-turn", platformType = null),
                    assistantMessage = null,
                    isCurrentTurn = false
                ),
                pendingTurn("newest")
            ),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.USER, "first"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "answer"),
                LocalHistoryMessage(LocalHistoryRole.USER, "other-platform-turn")
            ),
            runtime.createConversationCalls[1].initialMessages
        )
        assertEquals(listOf("first", "newest"), runtime.sendMessageCalls)
    }

    @Test
    fun `twelve sequential turns reuse one conversation and only send the new message`() = runBlocking {
        val turnCount = 12
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = List(turnCount) { index ->
                listOf(LocalRuntimeEvent.TextDelta("reply-$index"), LocalRuntimeEvent.Done)
            }
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()

        repeat(turnCount) { index ->
            val history = (0 until index).map { prior ->
                completedTurn("user-$prior", "reply-$prior")
            }
            adapter.openSession(history + pendingTurn("user-$index"), platform)
                .streamRound(emptyList(), emptyList())
                .toList()
        }

        assertEquals(1, runtime.createConversationCalls.size)
        assertEquals(0, runtime.closeConversationCalls)
        assertEquals((0 until turnCount).map { index -> "user-$index" }, runtime.sendMessageCalls)
    }

    @Test
    fun `edited early history closes and rebuilds the conversation`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("answer"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()

        adapter.openSession(turns("first"), platform).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(completedTurn("edited", "answer"), pendingTurn("second")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.USER, "edited"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "answer")
            ),
            runtime.createConversationCalls[1].initialMessages
        )
        assertEquals(listOf("first", "second"), runtime.sendMessageCalls)
    }

    @Test
    fun `retry of the last turn rebuilds from remaining history`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("answer"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("retry"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()

        adapter.openSession(turns("first"), platform).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(completedTurn("first", "answer"), pendingTurn("second")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(completedTurn("first", "answer"), pendingTurn("second")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.USER, "first"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "answer")
            ),
            runtime.createConversationCalls[1].initialMessages
        )
        assertEquals(listOf("first", "second", "second"), runtime.sendMessageCalls)
    }

    @Test
    fun `revision switch rebuilds with the selected assistant content`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("answer"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()

        adapter.openSession(turns("first"), platform).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(completedTurn("first", "revision"), pendingTurn("second")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals("revision", runtime.createConversationCalls[1].initialMessages[1].text)
    }

    @Test
    fun `cancellation calls cancelActive`() = runBlocking {
        val pause = CompletableDeferred<Unit>()
        val runtime = FakeLocalRuntime().apply {
            pauseAfterFirst = pause
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("hel"), LocalRuntimeEvent.TextDelta("lo"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val events = mutableListOf<ProviderEvent>()

        val job = launch {
            adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).collect {
                events += it
            }
        }
        while (events.none { it is ProviderEvent.TextDelta }) {
            yield()
        }
        job.cancelAndJoin()
        pause.complete(Unit)

        assertEquals(1, runtime.cancelActiveCalls)
        assertTrue(events.any { it is ProviderEvent.TextDelta })
    }

    @Test
    fun `cancel mid-stream forces the next turn to rebuild`() = runBlocking {
        val pause = CompletableDeferred<Unit>()
        val runtime = FakeLocalRuntime().apply {
            pauseAfterFirst = pause
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("hel"), LocalRuntimeEvent.TextDelta("lo"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("rebuilt"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()
        val events = mutableListOf<ProviderEvent>()

        val job = launch {
            adapter.openSession(turns("hello"), platform).streamRound(emptyList(), emptyList()).collect {
                events += it
            }
        }
        while (events.none { it is ProviderEvent.TextDelta }) {
            yield()
        }
        job.cancelAndJoin()
        pause.complete(Unit)

        adapter.openSession(turns("hello"), platform).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals(listOf("hello", "hello"), runtime.sendMessageCalls)
    }

    @Test
    fun `different profile rebuilds even when history matches`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)

        adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(turns("hello"), localPlatform(uid = "other")).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals(1, runtime.loadEngineCalls.distinct().size)
    }

    @Test
    fun `different model rebuilds and swaps the engine`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
        }
        val holder = LocalEngineHolder(runtime)
        val adapter = adapter(
            holder,
            FakeLocalModelRepository(
                downloadedPaths = mapOf(
                    "gemma3-1b-it" to "/models/gemma.litertlm",
                    "other-model" to "/models/other.litertlm"
                )
            )
        )

        adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(turns("hello"), localPlatform(model = "other-model")).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(2, runtime.loadEngineCalls.size)
        assertEquals("/models/gemma.litertlm", runtime.loadEngineCalls[0].modelPath)
        assertEquals("/models/other.litertlm", runtime.loadEngineCalls[1].modelPath)
        assertEquals(1, runtime.unloadEngineCalls)
    }

    @Test
    fun `attachment turns produce the notice event and continue with text`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime)
        val attachmentTurns = listOf(
            ConversationTurn(
                userMessage = MessageV2(
                    content = "describe this",
                    platformType = null,
                    attachments = listOf(
                        ChatAttachment(
                            localFilePath = "/tmp/photo.png",
                            preparedFilePath = "/tmp/photo.png",
                            displayName = "photo.png",
                            mimeType = "image/png",
                            sizeBytes = 12
                        )
                    )
                ),
                assistantMessage = null,
                isCurrentTurn = true
            )
        )

        val events = adapter.openSession(attachmentTurns, localPlatform()).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(
                ProviderEvent.Notice("The local platform ignored attachments", persistent = true),
                ProviderEvent.TextDelta("ok"),
                ProviderEvent.Completed
            ),
            events
        )
        assertEquals(listOf("describe this"), runtime.sendMessageCalls)
        assertTrue(runtime.sendMessageImages.single().isEmpty())
        assertEquals(false, runtime.loadEngineCalls.single().isVisionEnabled)
    }

    @Test
    fun `vision model forwards image bytes without a notice`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("a cat"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime, catalog = visionCatalog())
        val attachment = imageAttachment()

        val events = adapter.openSession(
            listOf(pendingTurn("describe this", listOf(attachment))),
            visionPlatform()
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(ProviderEvent.TextDelta("a cat"), ProviderEvent.Completed),
            events
        )
        assertEquals(listOf("describe this"), runtime.sendMessageCalls)
        assertImageBytes(listOf("/tmp/photo.png".toByteArray()), runtime.sendMessageImages.single())
        assertEquals(true, runtime.loadEngineCalls.single().isVisionEnabled)
    }

    @Test
    fun `vision model declines a pdf with the notice and still sends text`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime, catalog = visionCatalog())

        val events = adapter.openSession(
            listOf(pendingTurn("summarize", listOf(pdfAttachment()))),
            visionPlatform()
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(
                ProviderEvent.Notice(LiteRtLmAdapter.DEFAULT_IGNORED_ATTACHMENTS, persistent = true),
                ProviderEvent.TextDelta("ok"),
                ProviderEvent.Completed
            ),
            events
        )
        assertEquals(listOf("summarize"), runtime.sendMessageCalls)
        assertTrue(runtime.sendMessageImages.single().isEmpty())
    }

    @Test
    fun `vision model forwards an image and declines a pdf in the same turn`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime, catalog = visionCatalog())

        val events = adapter.openSession(
            listOf(pendingTurn("look", listOf(imageAttachment(), pdfAttachment()))),
            visionPlatform()
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(
                ProviderEvent.Notice(LiteRtLmAdapter.DEFAULT_IGNORED_ATTACHMENTS, persistent = true),
                ProviderEvent.TextDelta("ok"),
                ProviderEvent.Completed
            ),
            events
        )
        assertImageBytes(listOf("/tmp/photo.png".toByteArray()), runtime.sendMessageImages.single())
    }

    @Test
    fun `non vision model declines an image with the notice`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(
            runtime,
            catalog = FakeModelCatalogRepository(
                listOf(CatalogEntry(id = "gemma3-1b-it", capabilities = CatalogCapabilities(vision = false)))
            )
        )

        val events = adapter.openSession(
            listOf(pendingTurn("describe this", listOf(imageAttachment()))),
            localPlatform()
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(
                ProviderEvent.Notice(LiteRtLmAdapter.DEFAULT_IGNORED_ATTACHMENTS, persistent = true),
                ProviderEvent.TextDelta("ok"),
                ProviderEvent.Completed
            ),
            events
        )
        assertTrue(runtime.sendMessageImages.single().isEmpty())
        assertEquals(false, runtime.loadEngineCalls.single().isVisionEnabled)
    }

    @Test
    fun `vision model forwards ten images and notices the rest`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime, catalog = visionCatalog())
        val attachments = (1..11).map { index ->
            imageAttachment(path = "/tmp/photo-$index.png", name = "photo-$index.png")
        }

        val events = adapter.openSession(
            listOf(pendingTurn("what are these", attachments)),
            visionPlatform()
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(
                ProviderEvent.Notice(LiteRtLmAdapter.DEFAULT_TOO_MANY_IMAGES, persistent = true),
                ProviderEvent.TextDelta("ok"),
                ProviderEvent.Completed
            ),
            events
        )
        assertImageBytes(
            (1..10).map { index -> "/tmp/photo-$index.png".toByteArray() },
            runtime.sendMessageImages.single()
        )
    }

    @Test
    fun `follow-up after an image turn reuses the warm conversation`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("a cat"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime, catalog = visionCatalog())
        val platform = visionPlatform()
        val photo = imageAttachment()

        adapter.openSession(
            listOf(pendingTurn("describe this", listOf(photo))),
            platform
        ).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(
                completedTurn("describe this", "a cat", listOf(photo)),
                pendingTurn("follow up")
            ),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(1, runtime.createConversationCalls.size)
        assertEquals(0, runtime.closeConversationCalls)
        assertEquals(listOf("describe this", "follow up"), runtime.sendMessageCalls)
        assertTrue(runtime.sendMessageImages[1].isEmpty())
    }

    @Test
    fun `removing a prior image turn rebuilds the warm conversation`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("a cat"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("rebuilt"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime, catalog = visionCatalog())
        val platform = visionPlatform()
        val photo = imageAttachment()

        adapter.openSession(
            listOf(pendingTurn("describe this", listOf(photo))),
            platform
        ).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(
                completedTurn("describe this", "a cat"),
                pendingTurn("follow up")
            ),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals(listOf("describe this", "follow up"), runtime.sendMessageCalls)
        assertTrue(runtime.createConversationCalls[1].initialMessages[0].imageIds.isEmpty())
    }

    @Test
    fun `rebuild of a prior image turn seeds conversation with image bytes`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("a cat"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("retry"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime, catalog = visionCatalog())
        val platform = visionPlatform()
        val photo = imageAttachment()

        adapter.openSession(
            listOf(pendingTurn("describe this", listOf(photo))),
            platform
        ).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(
                completedTurn("describe this", "a cat", listOf(photo)),
                pendingTurn("follow up")
            ),
            platform
        ).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(
                completedTurn("describe this", "a cat", listOf(photo)),
                pendingTurn("follow up")
            ),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        val rebuiltUser = runtime.createConversationCalls[1].initialMessages[0]
        assertEquals("describe this", rebuiltUser.text)
        assertEquals(listOf("/tmp/photo.png|image/png|12"), rebuiltUser.imageIds)
        assertImageBytes(listOf("/tmp/photo.png".toByteArray()), rebuiltUser.images)
    }

    @Test
    fun `missing model produces error event`() = runBlocking {
        val runtime = FakeLocalRuntime()
        val adapter = LiteRtLmAdapter(
            localRuntime = runtime,
            localModelRepository = FakeLocalModelRepository(),
            ignoredAttachmentsNotice = LiteRtLmAdapter.DEFAULT_IGNORED_ATTACHMENTS,
            modelNotDownloadedError = LiteRtLmAdapter.DEFAULT_MODEL_NOT_DOWNLOADED
        )

        val events = adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(ProviderEvent.Failed("This Local Model is not downloaded. Download it from Settings → Local Models.")),
            events
        )
        assertTrue(runtime.loadEngineCalls.isEmpty())
        assertTrue(runtime.sendMessageCalls.isEmpty())
    }

    @Test
    fun `contended mutex emits waiting notice and runs complete in order`() = runBlocking {
        val pause = CompletableDeferred<Unit>()
        val runtime = FakeLocalRuntime().apply {
            pauseAfterFirst = pause
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
        }
        val holder = LocalEngineHolder(runtime)
        val adapter = adapter(holder)
        val platform = localPlatform()
        val firstEvents = mutableListOf<ProviderEvent>()
        val secondEvents = mutableListOf<ProviderEvent>()

        val first = launch {
            adapter.openSession(turns("a"), platform).streamRound(emptyList(), emptyList()).collect {
                firstEvents += it
            }
        }
        while (firstEvents.none { it is ProviderEvent.TextDelta }) {
            yield()
        }

        val second = launch {
            adapter.openSession(turns("b"), localPlatform(uid = "other")).streamRound(emptyList(), emptyList()).collect {
                secondEvents += it
            }
        }
        while (secondEvents.none { it is ProviderEvent.Notice }) {
            yield()
        }

        pause.complete(Unit)
        first.join()
        second.join()

        assertEquals(
            listOf(ProviderEvent.TextDelta("one"), ProviderEvent.Completed),
            firstEvents
        )
        assertEquals(
            listOf(
                ProviderEvent.Notice(LiteRtLmAdapter.DEFAULT_WAITING_FOR_ENGINE),
                ProviderEvent.TextDelta("two"),
                ProviderEvent.Completed
            ),
            secondEvents
        )
        assertEquals(listOf("a", "b"), runtime.sendMessageCalls)
    }

    @Test
    fun `tool capable model registers bound tools with constrained decoding`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime, catalog = toolsCatalog())
        val session = adapter.openSession(turns("hello"), localPlatform(), listOf(lookupTool()))

        assertTrue(session.handlesToolsInternally)
        session.streamRound(emptyList(), emptyList()).toList()

        val config = runtime.createConversationCalls.single()
        assertEquals(listOf("lookup"), config.tools.map { it.name })
        assertEquals("Look things up", config.tools.single().description)
        assertTrue(config.tools.single().inputSchemaJson.contains("query"))
        assertTrue(config.isConstrainedDecodingEnabled)
        assertTrue(config.toolExecutor != null)
    }

    @Test
    fun `scripted engine tool invocation executes the tool emits timeline events and continues`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(
                    LocalRuntimeEvent.TextDelta("before"),
                    LocalRuntimeEvent.TextDelta("after"),
                    LocalRuntimeEvent.Done
                )
            )
            scriptedToolInvocations = listOf(
                listOf(ScriptedToolInvocation("lookup", """{"query":"weather"}""", afterEventIndex = 0))
            )
        }
        val executedArgs = mutableListOf<JsonObject>()
        val adapter = adapter(runtime, catalog = toolsCatalog())

        val events = adapter.openSession(
            turns("hello"),
            localPlatform(),
            listOf(
                lookupTool { callId, arguments ->
                    executedArgs += arguments
                    AgentToolResult(callId, ToolResultContent.Text("result-ok"), isError = false)
                }
            )
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(listOf("lookup" to """{"query":"weather"}"""), runtime.toolExecutorCalls)
        assertEquals(listOf("result-ok"), runtime.toolExecutorResults)
        assertEquals(1, executedArgs.size)
        assertEquals("weather", executedArgs.single()["query"]?.toString()?.trim('"'))
        val toolCall = events.filterIsInstance<ProviderEvent.ToolCall>().single()
        val toolResult = events.filterIsInstance<ProviderEvent.ToolResult>().single()
        assertEquals("lookup", toolCall.name)
        assertEquals("weather", toolCall.arguments["query"]?.toString()?.trim('"'))
        assertEquals(toolCall, toolResult.call)
        assertEquals(false, toolResult.result.isError)
        assertEquals(ToolResultContent.Text("result-ok"), toolResult.result.content)
        assertEquals(
            listOf("before", "after"),
            events.filterIsInstance<ProviderEvent.TextDelta>().map { it.text }
        )
        assertTrue(events.last() is ProviderEvent.Completed)
        assertTrue(events.indexOf(toolCall) > events.indexOfFirst { it is ProviderEvent.TextDelta })
        assertTrue(events.indexOf(toolResult) > events.indexOf(toolCall))
        assertTrue(events.indexOfLast { it is ProviderEvent.TextDelta } > events.indexOf(toolResult))
    }

    @Test
    fun `non capable model does not register tools`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime)

        adapter.openSession(turns("hello"), localPlatform(), listOf(lookupTool()))
            .streamRound(emptyList(), emptyList())
            .toList()

        val config = runtime.createConversationCalls.single()
        assertTrue(config.tools.isEmpty())
        assertFalse(config.isConstrainedDecodingEnabled)
        assertEquals(null, config.toolExecutor)
    }

    @Test
    fun `tool capable model without bound tools does not register tools`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime, catalog = toolsCatalog())

        adapter.openSession(turns("hello"), localPlatform())
            .streamRound(emptyList(), emptyList())
            .toList()

        val config = runtime.createConversationCalls.single()
        assertTrue(config.tools.isEmpty())
        assertFalse(config.isConstrainedDecodingEnabled)
        assertEquals(null, config.toolExecutor)
    }

    @Test
    fun `same tools reuse the warm conversation without re-registering`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime, catalog = toolsCatalog())
        val platform = localPlatform()
        val tools = listOf(lookupTool())

        adapter.openSession(turns("first"), platform, tools).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(completedTurn("first", "one"), pendingTurn("second")),
            platform,
            tools
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(1, runtime.createConversationCalls.size)
        assertEquals(0, runtime.closeConversationCalls)
        assertEquals(listOf("lookup"), runtime.createConversationCalls.single().tools.map { it.name })
    }

    @Test
    fun `adapter resolves capabilities through the cache-first catalog path`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        val catalog = FakeModelCatalogRepository(
            listOf(CatalogEntry(id = "gemma-3n-e2b-it", capabilities = CatalogCapabilities(vision = true)))
        )
        val adapter = adapter(runtime, catalog = catalog)

        adapter.openSession(
            listOf(pendingTurn("describe this", listOf(imageAttachment()))),
            visionPlatform()
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(0, catalog.visibleEntriesCalls)
        assertEquals(1, catalog.cachedVisibleEntriesCalls)
        assertEquals(true, runtime.loadEngineCalls.single().isVisionEnabled)
        assertImageBytes(listOf("/tmp/photo.png".toByteArray()), runtime.sendMessageImages.single())
    }

    @Test
    fun `queued runs keep their own tools and event streams`() = runBlocking {
        val pause = CompletableDeferred<Unit>()
        val runtime = FakeLocalRuntime().apply {
            pauseAfterFirst = pause
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
            scriptedToolInvocations = listOf(
                listOf(ScriptedToolInvocation("lookup", """{"query":"a"}""", afterEventIndex = 0)),
                listOf(ScriptedToolInvocation("echo", """{"query":"b"}""", afterEventIndex = 0))
            )
        }
        val adapter = adapter(runtime, catalog = toolsCatalog())
        val firstEvents = mutableListOf<ProviderEvent>()
        val secondEvents = mutableListOf<ProviderEvent>()
        val firstToolCalls = mutableListOf<String>()
        val secondToolCalls = mutableListOf<String>()

        val first = launch {
            adapter.openSession(
                turns("a"),
                localPlatform(),
                listOf(
                    lookupTool { callId, _ ->
                        firstToolCalls += "lookup"
                        AgentToolResult(callId, ToolResultContent.Text("first-ok"), isError = false)
                    }
                )
            ).streamRound(emptyList(), emptyList()).collect { firstEvents += it }
        }
        while (firstEvents.none { it is ProviderEvent.TextDelta }) {
            yield()
        }

        val second = launch {
            adapter.openSession(
                turns("b"),
                localPlatform(uid = "other"),
                listOf(
                    lookupTool(name = "echo") { callId, _ ->
                        secondToolCalls += "echo"
                        AgentToolResult(callId, ToolResultContent.Text("second-ok"), isError = false)
                    }
                )
            ).streamRound(emptyList(), emptyList()).collect { secondEvents += it }
        }
        while (secondEvents.none { it is ProviderEvent.Notice }) {
            yield()
        }

        pause.complete(Unit)
        first.join()
        second.join()

        assertEquals(listOf("lookup"), firstToolCalls)
        assertEquals(listOf("echo"), secondToolCalls)
        assertEquals(listOf("lookup"), firstEvents.filterIsInstance<ProviderEvent.ToolCall>().map { it.name })
        assertEquals(listOf("echo"), secondEvents.filterIsInstance<ProviderEvent.ToolCall>().map { it.name })
        assertTrue(firstEvents.none { it is ProviderEvent.ToolCall && it.name == "echo" })
        assertTrue(secondEvents.none { it is ProviderEvent.ToolCall && it.name == "lookup" })
    }

    @Test
    fun `cold engine emits a loading notice and a warm engine does not`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime, loadingModelNotice = LiteRtLmAdapter.DEFAULT_LOADING_MODEL)
        val platform = localPlatform()

        val coldEvents = adapter.openSession(turns("first"), platform).streamRound(emptyList(), emptyList()).toList()
        val warmEvents = adapter.openSession(
            listOf(completedTurn("first", "one"), pendingTurn("second")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(
                ProviderEvent.Notice(LiteRtLmAdapter.DEFAULT_LOADING_MODEL),
                ProviderEvent.TextDelta("one"),
                ProviderEvent.Completed
            ),
            coldEvents
        )
        assertEquals(
            listOf(ProviderEvent.TextDelta("two"), ProviderEvent.Completed),
            warmEvents
        )
    }

    @Test
    fun `trim during generation cancels unloads and the next turn reloads and rebuilds`() = runBlocking {
        val pause = CompletableDeferred<Unit>()
        val runtime = FakeLocalRuntime().apply {
            pauseAfterFirst = pause
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("hel"), LocalRuntimeEvent.TextDelta("lo"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("rebuilt"), LocalRuntimeEvent.Done)
            )
        }
        val holder = LocalEngineHolder(runtime)
        val adapter = adapter(holder)
        val platform = localPlatform()
        val firstEvents = mutableListOf<ProviderEvent>()

        val first = launch {
            try {
                adapter.openSession(turns("hello"), platform).streamRound(emptyList(), emptyList()).collect {
                    firstEvents += it
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            }
        }
        while (firstEvents.none { it is ProviderEvent.TextDelta }) {
            yield()
        }

        holder.unloadEngine()
        first.join()

        adapter.openSession(turns("hello"), platform).streamRound(emptyList(), emptyList()).toList()

        assertTrue(runtime.cancelActiveCalls >= 1)
        assertEquals(1, runtime.unloadEngineCalls)
        assertEquals(2, runtime.loadEngineCalls.size)
        assertEquals(2, runtime.createConversationCalls.size)
    }

    @Test
    fun `changed tool set rebuilds the conversation and re-registers`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime, catalog = toolsCatalog())
        val platform = localPlatform()

        adapter.openSession(turns("first"), platform, listOf(lookupTool()))
            .streamRound(emptyList(), emptyList())
            .toList()
        adapter.openSession(
            listOf(completedTurn("first", "one"), pendingTurn("second")),
            platform,
            listOf(lookupTool(name = "echo"))
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals(listOf("lookup"), runtime.createConversationCalls[0].tools.map { it.name })
        assertEquals(listOf("echo"), runtime.createConversationCalls[1].tools.map { it.name })
        assertTrue(runtime.createConversationCalls[1].isConstrainedDecodingEnabled)
    }

    @Test
    fun `throwing tool is recorded as a timeline failure and generation continues`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(
                    LocalRuntimeEvent.TextDelta("before"),
                    LocalRuntimeEvent.TextDelta("after"),
                    LocalRuntimeEvent.Done
                )
            )
            scriptedToolInvocations = listOf(
                listOf(ScriptedToolInvocation("lookup", """{"query":"x"}""", afterEventIndex = 0))
            )
        }
        val adapter = adapter(runtime, catalog = toolsCatalog())
        val failingTool = lookupTool { _, _ -> error("boom") }

        val events = adapter.openSession(turns("hello"), localPlatform(), listOf(failingTool))
            .streamRound(emptyList(), emptyList())
            .toList()

        val toolResult = events.filterIsInstance<ProviderEvent.ToolResult>().single()
        assertEquals(true, toolResult.result.isError)
        assertEquals(ToolResultContent.Text("boom"), toolResult.result.content)
        assertFalse(events.any { it is ProviderEvent.Failed })
        assertEquals(
            listOf("before", "after"),
            events.filterIsInstance<ProviderEvent.TextDelta>().map { it.text }
        )
        assertTrue(events.last() is ProviderEvent.Completed)
    }

    @Test
    fun `gpu engine load failure falls back to cpu emits notice and later turns use cpu`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            failLoadEngineIf = { spec ->
                if (spec.accelerator == LocalAccelerators.GPU) {
                    IllegalStateException("Failed to create engine … CreateSharedMemoryManager unimplemented")
                } else {
                    null
                }
            }
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()

        val first = adapter.openSession(turns("hello"), platform).streamRound(emptyList(), emptyList()).toList()
        val second = adapter.openSession(
            listOf(completedTurn("hello", "ok"), pendingTurn("again")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertTrue(first.any { it is ProviderEvent.Notice && it.message == LiteRtLmAdapter.DEFAULT_GPU_UNAVAILABLE })
        assertTrue(first.any { it is ProviderEvent.TextDelta && it.text == "ok" })
        assertTrue(first.last() is ProviderEvent.Completed)
        assertFalse(first.any { it is ProviderEvent.Failed })
        assertEquals(
            listOf(LocalAccelerators.GPU, LocalAccelerators.CPU, LocalAccelerators.CPU),
            runtime.loadEngineCalls.map { it.accelerator }
        )
        assertEquals(listOf(LocalAccelerators.CPU), runtime.loadEngineCalls.drop(2).map { it.accelerator })
        assertFalse(second.any { it is ProviderEvent.Notice && it.message == LiteRtLmAdapter.DEFAULT_GPU_UNAVAILABLE })
    }

    @Test
    fun `gpu fallback does not force a later npu selection onto cpu`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            failLoadEngineIf = { spec ->
                if (spec.accelerator == LocalAccelerators.GPU || spec.accelerator == LocalAccelerators.NPU) {
                    IllegalStateException("accelerator unavailable")
                } else {
                    null
                }
            }
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("npu"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)

        val gpuEvents = adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).toList()
        val npuEvents = adapter.openSession(
            listOf(completedTurn("hello", "ok"), pendingTurn("again")),
            localPlatform(uid = "local-npu").copy(accelerator = LocalAccelerators.NPU)
        ).streamRound(emptyList(), emptyList()).toList()

        assertTrue(gpuEvents.any { it is ProviderEvent.Notice && it.message == LiteRtLmAdapter.DEFAULT_GPU_UNAVAILABLE })
        assertTrue(npuEvents.any { it is ProviderEvent.Notice && it.message == LiteRtLmAdapter.DEFAULT_NPU_UNAVAILABLE })
        assertFalse(npuEvents.any { it is ProviderEvent.Notice && it.message == LiteRtLmAdapter.DEFAULT_GPU_UNAVAILABLE })
        assertEquals(
            listOf(LocalAccelerators.GPU, LocalAccelerators.CPU, LocalAccelerators.NPU, LocalAccelerators.CPU),
            runtime.loadEngineCalls.map { it.accelerator }
        )
    }

    @Test
    fun `NPU engine spec clamps max tokens to the matching SOC variant context`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(
            runtime,
            catalog = FakeModelCatalogRepository(
                listOf(
                    CatalogEntry(
                        id = "gemma3-1b-it",
                        supportedAccelerators = listOf("gpu", "cpu", "npu"),
                        socToModelFiles = mapOf(
                            "SM8750" to SocVariant(modelFile = "npu.litertlm", contextSize = 1280)
                        )
                    )
                )
            ),
            deviceSocModel = "SM8750"
        )

        adapter.openSession(
            turns("hello"),
            localPlatform().copy(accelerator = LocalAccelerators.NPU, maxTokens = 4096)
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(1280, runtime.loadEngineCalls.single().maxTokens)
        assertEquals(LocalAccelerators.NPU, runtime.loadEngineCalls.single().accelerator)
    }

    @Test
    fun `cpu fallback failure surfaces a clean engine error instead of the native dump`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            failLoadEngineIf = {
                IllegalStateException("Failed to create engine … CreateSharedMemoryManager unimplemented")
            }
        }
        val adapter = adapter(runtime)

        val events = adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).toList()

        val failed = events.filterIsInstance<ProviderEvent.Failed>().single()
        assertEquals(LiteRtLmAdapter.DEFAULT_ENGINE_LOAD_FAILED, failed.message)
        assertFalse(failed.message.contains("CreateSharedMemoryManager"))
        assertFalse(failed.message.contains("Failed to create engine"))
    }

    @Test
    fun `holding the fake mutex emits waiting notice before the run`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        runtime.generationMutex.lock()
        val adapter = adapter(runtime)
        val events = mutableListOf<ProviderEvent>()

        val job = launch {
            adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).collect {
                events += it
            }
        }
        while (events.none { it is ProviderEvent.Notice }) {
            yield()
        }

        runtime.generationMutex.unlock()
        job.join()

        assertEquals(
            listOf(
                ProviderEvent.Notice(LiteRtLmAdapter.DEFAULT_WAITING_FOR_ENGINE),
                ProviderEvent.TextDelta("ok"),
                ProviderEvent.Completed
            ),
            events
        )
    }

    private fun adapter(
        runtime: LocalRuntime,
        models: FakeLocalModelRepository = FakeLocalModelRepository(
            downloadedPaths = mapOf(
                "gemma3-1b-it" to "/models/gemma.litertlm",
                "gemma-3n-e2b-it" to "/models/gemma3n.litertlm"
            )
        ),
        catalog: ModelCatalogRepository = FakeModelCatalogRepository(),
        loadingModelNotice: String = "",
        deviceSocModel: String = "",
        loadImageBytes: suspend (ChatAttachment) -> ByteArray? = { attachment ->
            attachment.preparedFilePath.ifBlank { attachment.localFilePath }.toByteArray()
        }
    ) = LiteRtLmAdapter(
        localRuntime = runtime,
        localModelRepository = models,
        ignoredAttachmentsNotice = LiteRtLmAdapter.DEFAULT_IGNORED_ATTACHMENTS,
        modelNotDownloadedError = LiteRtLmAdapter.DEFAULT_MODEL_NOT_DOWNLOADED,
        tooManyImagesNotice = LiteRtLmAdapter.DEFAULT_TOO_MANY_IMAGES,
        loadingModelNotice = loadingModelNotice,
        modelCatalogRepository = catalog,
        deviceSocModel = deviceSocModel,
        loadImageBytes = loadImageBytes
    )

    private fun turns(text: String) = listOf(pendingTurn(text))

    private fun pendingTurn(text: String, attachments: List<ChatAttachment> = emptyList()) = ConversationTurn(
        userMessage = MessageV2(content = text, platformType = null, attachments = attachments),
        assistantMessage = null,
        isCurrentTurn = true
    )

    private fun completedTurn(
        user: String,
        assistant: String,
        attachments: List<ChatAttachment> = emptyList()
    ) = ConversationTurn(
        userMessage = MessageV2(content = user, platformType = null, attachments = attachments),
        assistantMessage = MessageV2(content = assistant, platformType = "local"),
        isCurrentTurn = false
    )

    private fun imageAttachment(
        path: String = "/tmp/photo.png",
        name: String = "photo.png"
    ) = ChatAttachment(
        localFilePath = path,
        preparedFilePath = path,
        displayName = name,
        mimeType = "image/png",
        sizeBytes = 12
    )

    private fun pdfAttachment() = ChatAttachment(
        localFilePath = "/tmp/doc.pdf",
        preparedFilePath = "/tmp/doc.pdf",
        displayName = "doc.pdf",
        mimeType = "application/pdf",
        sizeBytes = 100
    )

    private fun visionCatalog() = FakeModelCatalogRepository(
        listOf(CatalogEntry(id = "gemma-3n-e2b-it", capabilities = CatalogCapabilities(vision = true)))
    )

    private fun toolsCatalog() = FakeModelCatalogRepository(
        listOf(CatalogEntry(id = "gemma3-1b-it", capabilities = CatalogCapabilities(tools = true)))
    )

    private fun lookupTool(
        name: String = "lookup",
        execute: suspend (String, JsonObject) -> AgentToolResult = { callId, _ ->
            AgentToolResult(callId, ToolResultContent.Text("result-ok"), isError = false)
        }
    ): AgentTool = object : AgentTool {
        override val definition = AgentToolDefinition(
            name = name,
            description = "Look things up",
            inputSchema = buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put("query", buildJsonObject { put("type", "string") })
                    }
                )
            }
        )

        override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult = execute(callId, arguments)
    }

    private fun visionPlatform() = localPlatform(model = "gemma-3n-e2b-it")

    private fun assertImageBytes(expected: List<ByteArray>, actual: List<ByteArray>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedBytes, actualBytes) ->
            assertTrue(expectedBytes.contentEquals(actualBytes))
        }
    }

    private fun localPlatform(
        uid: String = "local",
        model: String = "gemma3-1b-it"
    ) = PlatformV2(
        uid = uid,
        name = "Local",
        compatibleType = ClientType.LITERT_LM,
        apiUrl = "",
        model = model,
        temperature = 0.8f,
        topP = 0.9f,
        topK = 32,
        maxTokens = 2048,
        accelerator = "gpu",
        systemPrompt = "Be concise"
    )
}
