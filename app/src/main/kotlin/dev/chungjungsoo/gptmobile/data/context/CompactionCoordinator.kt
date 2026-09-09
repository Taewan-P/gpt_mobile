package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveRunId
import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

class CompactionCoordinator(
    private val store: CompactionStore,
    private val contextBuilder: ContextBuilder,
    private val capacityResolver: ModelCapacityResolver,
    private val textCompactor: TextContextCompactor,
    private val nativeCompactors: List<ContextCompactor>,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 }
) {
    // Provider usage calibrates subsequent requests in this process. Persisted checkpoints
    // remain independent; after restart the UI explicitly uses an estimate again.
    private val usageBaselines = ConcurrentHashMap<Pair<Int, String>, UsageBaseline>()

    fun recordUsage(
        chatId: Int,
        platform: PlatformV2,
        turns: List<ConversationTurn>,
        evidence: List<ToolEvidence>,
        toolNames: List<String>,
        prepared: PreparedContext,
        totalTokens: Int
    ) {
        if (chatId == 0 || totalTokens <= 0) return
        usageBaselines[chatId to platform.uid] = UsageBaseline(
            turns.size,
            CompactionKeys.sourceFingerprint(turns, platform, evidence, toolNames),
            prepared.coveredTurnCount,
            prepared.sourcePrefixFingerprint,
            totalTokens
        )
    }

    suspend fun prepare(
        chatId: Int,
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        toolEvidence: List<ToolEvidence> = emptyList(),
        toolDefinitionTokens: Int = 0,
        toolDefinitionNames: List<String> = emptyList(),
        force: Boolean = false,
        protectCurrent: Boolean = true,
        outputReserve: Int? = null,
        onCompactionState: (suspend (Boolean) -> Unit)? = null
    ): CompactionOutcome {
        val eligible = contextBuilder.build(userMessages, assistantMessages, platform)
        val resolution = capacityResolver.resolve(platform)
        if (resolution is ModelCapacityResolution.Unknown) {
            return CompactionOutcome.NeedsCapacity(platform)
        }
        val capacity = (resolution as ModelCapacityResolution.Known).capacity
        val limit = contextLimit(platform, capacity) ?: return CompactionOutcome.NeedsCapacity(platform)
        val outputReserve = outputReserve ?: outputReserveTokens(platform)
        val instructionTokens = TokenEstimator.estimateText(platform.systemPrompt)
        val evidenceTokens = TokenEstimator.estimateEvidence(toolEvidence)
        val endpointKey = CompactionKeys.endpointModelKey(platform)
        val existing = if (chatId != 0) store.getCheckpoint(chatId, platform.uid) else null
        val valid = existing?.takeIf { checkpoint ->
            checkpoint.endpointModelKey == endpointKey &&
                checkpoint.coveredTurnCount in 0..eligible.size &&
                decodeCheckpoint(checkpoint) != null &&
                CompactionKeys.sourceFingerprint(
                    eligible.take(checkpoint.coveredTurnCount),
                    platform,
                    evidenceForTurns(eligible.take(checkpoint.coveredTurnCount), toolEvidence),
                    toolDefinitionNames
                ) == checkpoint.sourcePrefixFingerprint
        }
        val tailFromCheckpoint = if (valid != null) eligible.drop(valid.coveredTurnCount) else eligible
        val restored = restoreWorkingTurns(valid, tailFromCheckpoint, toolEvidence)
        val rawEstimate = workingTokens(
            restored.turns,
            restored.nativeItemsJson,
            instructionTokens,
            toolDefinitionTokens
        )
        val baseline = usageBaselines[chatId to platform.uid]?.takeIf {
            it.turnCount <= eligible.size &&
                it.coveredTurnCount == (valid?.coveredTurnCount ?: 0) &&
                (valid == null || it.checkpointFingerprint == valid.sourcePrefixFingerprint) &&
                it.sourceFingerprint == CompactionKeys.sourceFingerprint(eligible.take(it.turnCount), platform, toolEvidence, toolDefinitionNames)
        }
        val estimated = maxOf(
            rawEstimate,
            baseline?.let {
                it.totalTokens + TokenEstimator.estimateTurns(eligible.drop(it.turnCount))
            } ?: 0
        )
        val budget = TokenBudget(
            contextLimit = limit,
            estimatedInputTokens = estimated,
            outputReserve = outputReserve,
            instructionTokens = instructionTokens,
            toolDefinitionTokens = toolDefinitionTokens,
            toolEvidenceTokens = evidenceTokens,
            remaining = limit - estimated - outputReserve
        )
        val currentTokens = if (protectCurrent && eligible.lastOrNull()?.assistantMessage == null) {
            eligible.lastOrNull()?.let(TokenEstimator::estimateTurn) ?: 0
        } else {
            0
        }
        if (currentTokens + instructionTokens + toolDefinitionTokens + outputReserve > limit) {
            return CompactionOutcome.InputTooLarge(
                "The current message cannot fit in this model's context window. Original draft was kept."
            )
        }
        val overBudget = estimated + outputReserve >= limit
        if (!force && !overBudget) {
            return CompactionOutcome.Ready(
                preparedContext(platform, eligible, restored, budget, valid?.coveredTurnCount ?: 0, valid?.representation ?: CompactionRepresentation.RAW, toolEvidence, toolDefinitionNames)
            )
        }
        val uncompacted = if (valid == null) eligible else tailFromCheckpoint
        var (prefix, tail) = if (protectCurrent) {
            splitPrefixAndTail(uncompacted, maxOf((limit - outputReserve - instructionTokens - toolDefinitionTokens) / 4, currentTokens))
        } else {
            uncompacted to emptyList()
        }
        if ((force || overBudget) && prefix.isEmpty() && uncompacted.size > 1) {
            prefix = uncompacted.dropLast(1)
            tail = listOf(uncompacted.last())
        }
        if (prefix.isEmpty() && (force || overBudget) && uncompacted.lastOrNull()?.assistantMessage != null) {
            prefix = uncompacted
            tail = emptyList()
        }
        if (prefix.isEmpty() && toolEvidence.isEmpty() && valid == null) {
            if (overBudget) return CompactionOutcome.Failed("Context cannot fit in this model window. Original transcript was kept.", valid)
            return CompactionOutcome.Ready(
                preparedContext(platform, eligible, restored, budget, valid?.coveredTurnCount ?: 0, valid?.representation ?: CompactionRepresentation.RAW, toolEvidence, toolDefinitionNames)
            )
        }
        return persistCompact(
            chatId = chatId,
            platform = platform,
            eligible = eligible,
            prefix = prefix,
            tail = tail,
            toolEvidence = toolEvidence,
            toolDefinitionTokens = toolDefinitionTokens,
            toolDefinitionNames = toolDefinitionNames,
            limit = limit,
            outputReserve = outputReserve,
            instructionTokens = instructionTokens,
            preserved = valid,
            onCompactionState = onCompactionState
        )
    }

    suspend fun validateDraft(
        platform: PlatformV2,
        message: MessageV2,
        toolDefinitionTokens: Int = 0,
        outputReserve: Int? = null
    ): CompactionOutcome? {
        val resolution = capacityResolver.resolve(platform)
        if (resolution is ModelCapacityResolution.Unknown) {
            return CompactionOutcome.NeedsCapacity(platform)
        }
        val capacity = (resolution as ModelCapacityResolution.Known).capacity
        val limit = contextLimit(platform, capacity) ?: return CompactionOutcome.NeedsCapacity(platform)
        val reserve = outputReserve ?: outputReserveTokens(platform)
        val instructionTokens = TokenEstimator.estimateText(platform.systemPrompt)
        val draftTokens = TokenEstimator.estimateTurn(
            ConversationTurn(userMessage = message, assistantMessage = null, isCurrentTurn = true)
        )
        if (draftTokens + instructionTokens + toolDefinitionTokens + reserve > limit) {
            return CompactionOutcome.InputTooLarge(
                "The current message cannot fit in this model's context window. Original draft was kept."
            )
        }
        return null
    }

    private suspend fun persistCompact(
        chatId: Int,
        platform: PlatformV2,
        eligible: List<ConversationTurn>,
        prefix: List<ConversationTurn>,
        tail: List<ConversationTurn>,
        toolEvidence: List<ToolEvidence>,
        toolDefinitionTokens: Int,
        toolDefinitionNames: List<String>,
        limit: Int,
        outputReserve: Int,
        instructionTokens: Int,
        preserved: ContextCheckpoint?,
        onCompactionState: (suspend (Boolean) -> Unit)?
    ): CompactionOutcome {
        val prefixEvidence = evidenceForTurns(prefix, toolEvidence)
        val previousCovered = preserved?.coveredTurnCount ?: 0
        val covered = previousCovered + prefix.size
        val coveredTurns = eligible.take(covered)
        val coveredEvidence = evidenceForTurns(coveredTurns, toolEvidence)
        val output = try {
            onCompactionState?.invoke(true)
            compactPrefix(
                platform,
                prefix,
                prefixEvidence,
                coveredTurns,
                coveredEvidence,
                preserved?.let(::decodeCheckpoint),
                limit,
                outputReserve,
                instructionTokens,
                toolDefinitionTokens
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return CompactionOutcome.Failed(error.message ?: "Compaction failed", preserved)
        } finally {
            onCompactionState?.invoke(false)
        }
        val tailWithEvidence = attributedTurnsForCompaction(tail, evidenceForTurns(tail, toolEvidence))
        val restoredTurns = workingTurns(output, tailWithEvidence)
        val estimated = workingTokens(
            restoredTurns,
            output.nativeItemsJson,
            instructionTokens,
            toolDefinitionTokens
        )
        if (estimated + outputReserve > limit) {
            return CompactionOutcome.Failed(
                "Compacted context still exceeds the model window. Original transcript and last valid checkpoint were kept.",
                preserved
            )
        }
        val fingerprint = CompactionKeys.sourceFingerprint(
            coveredTurns,
            platform,
            coveredEvidence,
            toolDefinitionNames
        )
        val working = SerializedWorkingContext(
            representation = output.representation,
            instructions = platform.systemPrompt,
            summaryText = output.summaryText,
            nativeItemsJson = output.nativeItemsJson,
            estimatedTokens = estimated
        )
        val checkpoint = ContextCheckpoint(
            chatId = chatId,
            platformUid = platform.uid,
            sourcePrefixFingerprint = fingerprint,
            endpointModelKey = CompactionKeys.endpointModelKey(platform),
            coveredTurnCount = covered,
            representation = output.representation,
            serializedWorkingContext = json.encodeToString(working),
            estimatedTokens = estimated,
            updatedAt = nowSeconds()
        )
        if (chatId != 0) {
            try {
                store.saveCheckpoint(checkpoint)
                usageBaselines.remove(chatId to platform.uid)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return CompactionOutcome.Failed(error.message ?: "Failed to save compaction checkpoint", preserved)
            }
        }
        return CompactionOutcome.Compacted(
            PreparedContext(
                instructions = platform.systemPrompt,
                turns = restoredTurns,
                toolEvidence = toolEvidence,
                nativeItemsJson = output.nativeItemsJson,
                summaryText = output.summaryText,
                representation = output.representation,
                tokenBudget = TokenBudget(
                    contextLimit = limit,
                    estimatedInputTokens = estimated,
                    outputReserve = outputReserve,
                    instructionTokens = instructionTokens,
                    toolDefinitionTokens = toolDefinitionTokens,
                    toolEvidenceTokens = TokenEstimator.estimateEvidence(toolEvidence),
                    remaining = limit - estimated - outputReserve
                ),
                coveredTurnCount = covered,
                sourcePrefixFingerprint = fingerprint
            ),
            checkpoint
        )
    }

    private suspend fun compactPrefix(
        platform: PlatformV2,
        prefix: List<ConversationTurn>,
        toolEvidence: List<ToolEvidence>,
        coveredTurns: List<ConversationTurn>,
        coveredEvidence: List<ToolEvidence>,
        previous: SerializedWorkingContext?,
        limit: Int,
        outputReserve: Int,
        instructionTokens: Int,
        toolDefinitionTokens: Int
    ): CompactOutput {
        val incrementalSource = attributedTurnsForCompaction(prefix, toolEvidence)
        val nativeSource = if (previous?.representation == CompactionRepresentation.TEXT_SUMMARY) {
            listOf(summaryTurn(checkNotNull(previous.summaryText))) + incrementalSource
        } else {
            incrementalSource
        }
        val nativeTokens = TokenEstimator.estimateTurns(nativeSource) + TokenEstimator.estimateText(previous?.nativeItemsJson)
        val nativeLimit = limit - outputReserve - instructionTokens - toolDefinitionTokens - 64
        val targetTokens = minOf(limit / 4, outputReserve).coerceAtLeast(1)
        val native = nativeCompactors.firstOrNull { it.supports(platform, nativeTokens) }
        if (native != null && nativeTokens <= nativeLimit) {
            repeat(MAX_NATIVE_ATTEMPTS) {
                try {
                    return native.compact(
                        CompactInput(
                            platform,
                            platform.systemPrompt,
                            nativeSource,
                            emptyList(),
                            previous?.nativeItemsJson,
                            targetTokens
                        )
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Fall back only after bounded attempts, using the same complete source.
                }
            }
        }
        val source = when (previous?.representation) {
            CompactionRepresentation.TEXT_SUMMARY -> nativeSource

            CompactionRepresentation.NATIVE_OPENAI, CompactionRepresentation.NATIVE_ANTHROPIC -> {
                attributedTurnsForCompaction(coveredTurns, coveredEvidence)
            }

            else -> incrementalSource
        }
        val summaryOverhead = TokenEstimator.estimateText(TextContextCompactor.SUMMARIZATION_PROMPT) + 96
        val batchLimit = limit - targetTokens - summaryOverhead
        check(batchLimit >= 64) { "This context window is too small for a compaction request." }
        val summaryLimit = batchLimit / 2
        val sourceChunks = explodeOversized(source, batchLimit - summaryLimit)
        var sourceIndex = 0
        var rollingSummary: String? = null
        while (sourceIndex < sourceChunks.size) {
            currentCoroutineContext().ensureActive()
            val previous = rollingSummary?.let { summaryTurn(it) }
            val previousCost = previous?.let(TokenEstimator::estimateTurn) ?: 0
            val batch = takeFittingBatch(sourceChunks.drop(sourceIndex), batchLimit - previousCost)
            val inputTurns = listOfNotNull(previous) + batch
            val output = compactOnce(platform, inputTurns, minOf(targetTokens, summaryLimit - 16))
            val summary = output.summaryText?.trim().orEmpty()
            check(summary.isNotEmpty()) { "Compaction summary was empty" }
            check(TokenEstimator.estimateTurn(summaryTurn(summary)) <= summaryLimit) {
                "The summary did not fit the compaction budget. Original transcript was kept."
            }
            rollingSummary = summary
            sourceIndex += batch.size
        }
        check(!rollingSummary.isNullOrBlank()) { "No history was available to compact." }
        return CompactOutput(CompactionRepresentation.TEXT_SUMMARY, rollingSummary, coveredTurnCount = coveredTurns.size)
    }

    private suspend fun compactOnce(
        platform: PlatformV2,
        turns: List<ConversationTurn>,
        targetTokens: Int
    ): CompactOutput {
        val input = CompactInput(platform, platform.systemPrompt, turns, emptyList(), null, targetTokens)
        var lastError: Exception? = null
        repeat(MAX_TEXT_ATTEMPTS) {
            try {
                return textCompactor.compact(input)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IllegalStateException(lastError?.message ?: "Compaction failed", lastError)
    }

    private fun decodeCheckpoint(checkpoint: ContextCheckpoint): SerializedWorkingContext? = runCatching {
        val working = json.decodeFromString(SerializedWorkingContext.serializer(), checkpoint.serializedWorkingContext)
        require(working.representation == checkpoint.representation)
        when (working.representation) {
            CompactionRepresentation.TEXT_SUMMARY -> require(!working.summaryText.isNullOrBlank())

            CompactionRepresentation.NATIVE_OPENAI, CompactionRepresentation.NATIVE_ANTHROPIC -> {
                val items = json.parseToJsonElement(requireNotNull(working.nativeItemsJson)) as JsonArray
                require(items.isNotEmpty())
            }

            CompactionRepresentation.RAW -> error("Raw history is not a compaction checkpoint")
        }
        working
    }.getOrNull()

    private fun restoreWorkingTurns(
        checkpoint: ContextCheckpoint?,
        tail: List<ConversationTurn>,
        evidence: List<ToolEvidence>
    ): RestoredWorking {
        if (checkpoint == null) {
            return RestoredWorking(turns = attributedTurnsForCompaction(tail, evidence), summaryText = null, nativeItemsJson = null)
        }
        val working = checkNotNull(decodeCheckpoint(checkpoint))
        val tailWithEvidence = attributedTurnsForCompaction(tail, evidenceForTurns(tail, evidence))
        return RestoredWorking(
            turns = workingTurns(
                CompactOutput(
                    working.representation,
                    working.summaryText,
                    working.nativeItemsJson,
                    checkpoint.coveredTurnCount
                ),
                tailWithEvidence
            ),
            summaryText = working.summaryText,
            nativeItemsJson = working.nativeItemsJson
        )
    }

    private fun summaryTurn(summary: String) = ConversationTurn(
        userMessage = checkpointUserMessage(summary, emptyList()),
        assistantMessage = null,
        isCurrentTurn = false
    )

    private fun workingTurns(output: CompactOutput, tail: List<ConversationTurn>): List<ConversationTurn> = if (output.representation == CompactionRepresentation.TEXT_SUMMARY && !output.summaryText.isNullOrBlank()) {
        listOf(summaryTurn(output.summaryText)) + tail
    } else {
        tail
    }

    private fun workingTokens(
        turns: List<ConversationTurn>,
        nativeItemsJson: String?,
        instructionTokens: Int,
        toolDefinitionTokens: Int
    ): Int = TokenEstimator.estimateTurns(turns) +
        TokenEstimator.estimateText(nativeItemsJson) +
        instructionTokens +
        toolDefinitionTokens

    private fun preparedContext(
        platform: PlatformV2,
        eligible: List<ConversationTurn>,
        restored: RestoredWorking,
        budget: TokenBudget,
        coveredTurnCount: Int,
        representation: CompactionRepresentation,
        toolEvidence: List<ToolEvidence>,
        toolDefinitionNames: List<String>
    ): PreparedContext = PreparedContext(
        instructions = platform.systemPrompt,
        turns = restored.turns,
        toolEvidence = toolEvidence,
        nativeItemsJson = restored.nativeItemsJson,
        summaryText = restored.summaryText,
        representation = representation,
        tokenBudget = budget,
        coveredTurnCount = coveredTurnCount,
        sourcePrefixFingerprint = eligible.take(coveredTurnCount.coerceAtLeast(0)).let { coveredTurns ->
            CompactionKeys.sourceFingerprint(
                coveredTurns,
                platform,
                evidenceForTurns(coveredTurns, toolEvidence),
                toolDefinitionNames
            )
        }
    )

    private fun evidenceForTurns(
        turns: List<ConversationTurn>,
        evidence: List<ToolEvidence>
    ): List<ToolEvidence> {
        if (evidence.isEmpty()) return emptyList()
        val coveredRunIds = turns.mapNotNull { it.assistantMessage?.effectiveRunId() }.toSet()
        return evidence.filter { it.runId in coveredRunIds }
    }

    private fun splitPrefixAndTail(
        turns: List<ConversationTurn>,
        tailBudget: Int
    ): Pair<List<ConversationTurn>, List<ConversationTurn>> {
        if (turns.isEmpty()) return emptyList<ConversationTurn>() to emptyList()
        val current = turns.last()
        val history = turns.dropLast(1)
        var used = TokenEstimator.estimateTurn(current)
        val tailReversed = mutableListOf<ConversationTurn>()
        for (turn in history.asReversed()) {
            val cost = TokenEstimator.estimateTurn(turn)
            if (used + cost > tailBudget) break
            tailReversed += turn
            used += cost
        }
        val tail = tailReversed.asReversed() + current
        val prefix = history.dropLast(tailReversed.size)
        return prefix to tail
    }

    private fun takeFittingBatch(turns: List<ConversationTurn>, batchLimit: Int): List<ConversationTurn> {
        var used = 0
        val batch = turns.takeWhile { turn ->
            val cost = TokenEstimator.estimateTurn(turn)
            (used + cost <= batchLimit).also { fits -> if (fits) used += cost }
        }
        check(batch.isNotEmpty()) { "A message cannot fit the compaction request budget." }
        return batch
    }

    private fun explodeOversized(turns: List<ConversationTurn>, batchLimit: Int): List<ConversationTurn> = turns.flatMap { turn ->
        if (TokenEstimator.estimateTurn(turn) <= batchLimit) {
            listOf(turn)
        } else {
            val attachments = turn.userMessage.attachments + turn.assistantMessage?.attachments.orEmpty()
            val attachmentTurn = turn.copy(
                userMessage = turn.userMessage.copy(id = 0, content = "Attachment from preceding conversation", attachments = attachments),
                assistantMessage = null,
                isCurrentTurn = false
            )
            check(attachments.isEmpty() || TokenEstimator.estimateTurn(attachmentTurn) <= batchLimit) {
                "Attachments exceed the compaction request budget. Original transcript was kept."
            }
            chunkText(turnText(turn), batchLimit - 16).map { chunk ->
                ConversationTurn(MessageV2(content = chunk, platformType = null), null, false)
            } + if (attachments.isEmpty()) emptyList() else listOf(attachmentTurn)
        }
    }

    private fun turnText(turn: ConversationTurn): String = buildString {
        append("User: ")
        append(turn.userMessage.content)
        turn.assistantMessage?.content?.takeIf { it.isNotBlank() }?.let { content ->
            append("\nAssistant: ")
            append(content)
        }
    }

    private fun chunkText(text: String, tokenLimit: Int): List<String> {
        if (text.isEmpty()) {
            throw IllegalStateException("A single message exceeds the model context window and cannot be batched. empty-text tokenLimit=" + tokenLimit)
        }
        if (TokenEstimator.estimateText(text) <= tokenLimit) return listOf(text)
        val chunks = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            var low = index + 1
            var high = text.length
            var best = -1
            while (low <= high) {
                val mid = (low + high) / 2
                val piece = text.substring(index, mid)
                if (TokenEstimator.estimateText(piece) <= tokenLimit) {
                    best = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            if (best < text.length && best > index && Character.isHighSurrogate(text[best - 1]) && Character.isLowSurrogate(text[best])) best -= 1
            if (best <= index) {
                throw IllegalStateException(
                    "A single message exceeds the model context window and cannot be batched. tokenLimit=" + tokenLimit
                )
            }
            chunks += text.substring(index, best)
            index = best
        }
        return chunks
    }

    private fun contextLimit(platform: PlatformV2, capacity: ModelCapacity): Int? {
        capacity.effectiveContextWindowTokens?.let { return it }
        return if (platform.compatibleType == ClientType.LITERT_LM) {
            platform.maxTokens?.takeIf { it > 0 }
        } else {
            null
        }
    }

    private data class UsageBaseline(
        val turnCount: Int,
        val sourceFingerprint: String,
        val coveredTurnCount: Int,
        val checkpointFingerprint: String,
        val totalTokens: Int
    )

    private data class RestoredWorking(
        val turns: List<ConversationTurn>,
        val summaryText: String?,
        val nativeItemsJson: String?
    )

    companion object {
        const val MAX_NATIVE_ATTEMPTS = 2
        const val MAX_TEXT_ATTEMPTS = 2
        private val json = Json { ignoreUnknownKeys = true }
    }
}
