package dev.chungjungsoo.gptmobile.data.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.context.CompactionResult
import dev.chungjungsoo.gptmobile.data.context.CompactionStatus
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunTerminalError
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.appendChronologicalText
import dev.chungjungsoo.gptmobile.data.database.entity.resetActiveRevision
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.presentation.service.AgentRunForegroundService
import dev.chungjungsoo.gptmobile.util.ApiStateFlowOutcome
import dev.chungjungsoo.gptmobile.util.assistantErrorAppendedText
import dev.chungjungsoo.gptmobile.util.buildAssistantErrorContent
import dev.chungjungsoo.gptmobile.util.collectApiStateUpdates
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class AgentRunRequest(
    val runId: String,
    val chatId: Int,
    val assistantMessage: MessageV2,
    val platform: PlatformV2,
    val userMessages: List<MessageV2>,
    val assistantMessages: List<List<MessageV2>>
)

data class ActiveAgentRun(
    val runId: String,
    val chatId: Int,
    val profileUid: String
)

data class AgentRunNotice(
    val chatId: Int,
    val runId: String,
    val message: String,
    val persistent: Boolean = false,
    val key: String? = null
)

@Singleton
class AgentRunCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val chatLocks = ConcurrentHashMap<Int, Mutex>()
    private val interruptingRunIds = ConcurrentHashMap.newKeySet<String>()
    private val _activeRuns = MutableStateFlow<Map<String, ActiveAgentRun>>(emptyMap())
    private val _notices = MutableSharedFlow<AgentRunNotice>(extraBufferCapacity = 8)

    private val streamPublisher = LiveAgentStreamPublisher(
        persistScope = scope,
        persist = { chatRepository.updateAgentMessage(it) }
    )

    val activeRuns = _activeRuns.asStateFlow()
    val notices = _notices.asSharedFlow()

    fun compactContext(chatId: Int, platform: PlatformV2): Deferred<CompactionResult> {
        val runId = UUID.randomUUID().toString()
        val job = scope.async(start = CoroutineStart.LAZY) {
            withChatGate(chatId) {
                check(_activeRuns.value.values.none { it.chatId == chatId && it.runId != runId }) {
                    "Wait for this conversation's active replies before compacting."
                }
                collectCompactionResult(
                    timeoutMillis = AgentRunLimits().runTimeoutMillis,
                    timeoutMessage = context.getString(R.string.chat_context_timeout)
                ) { chatRepository.compactNow(chatId, platform) }
            }
        }
        jobs[runId] = job
        job.invokeOnCompletionCleanup {
            jobs.remove(runId)
            interruptingRunIds.remove(runId)
            _activeRuns.update { it - runId }
        }
        _activeRuns.update { it + (runId to ActiveAgentRun(runId, chatId, platform.uid)) }
        try {
            AgentRunForegroundService.start(context)
        } catch (error: RuntimeException) {
            job.cancel()
            return CompletableDeferred<CompactionResult>().apply { completeExceptionally(error) }
        }
        job.start()
        return job
    }
    val liveSnapshots = streamPublisher.snapshots

    fun start(requests: List<AgentRunRequest>) {
        scope.launch {
            withChatGate(requests.map { it.chatId }) {
                startUnlocked(requests)
            }
        }
    }

    private fun startUnlocked(requests: List<AgentRunRequest>) {
        val pending = requests.distinctBy(AgentRunRequest::runId).mapNotNull { request ->
            val job = scope.launch(start = CoroutineStart.LAZY) {
                execute(request)
            }
            if (jobs.putIfAbsent(request.runId, job) == null) {
                job.invokeOnCompletionCleanup {
                    runCatching {
                        jobs.remove(request.runId)
                        interruptingRunIds.remove(request.runId)
                        _activeRuns.update { it - request.runId }
                    }
                }
                request to job
            } else {
                job.cancel()
                null
            }
        }
        if (pending.isEmpty()) return

        _activeRuns.update { active ->
            active + pending.associate { (request, _) ->
                request.runId to ActiveAgentRun(request.runId, request.chatId, request.platform.uid)
            }
        }
        try {
            AgentRunForegroundService.start(context)
        } catch (error: RuntimeException) {
            pending.forEach { (request, job) ->
                jobs.remove(request.runId)
                job.cancel()
            }
            _activeRuns.update { active -> active - pending.map { it.first.runId }.toSet() }
            failQueuedStarts(pending.map { it.first }, error)
            return
        }
        pending.forEach { (_, job) -> job.start() }
    }

    fun cancelChat(chatId: Int) {
        val runIds = _activeRuns.value.values
            .filter { it.chatId == chatId }
            .mapTo(mutableSetOf(), ActiveAgentRun::runId)
        requestCancellation(runIds, isInterrupted = false)
    }

    fun cancelAll() {
        requestCancellation(jobs.keys.toSet(), isInterrupted = false)
    }

    fun interruptAll() {
        requestCancellation(jobs.keys.toSet(), isInterrupted = true)
    }

    suspend fun cancelChatAndJoin(chatId: Int) {
        val runIds = _activeRuns.value.values
            .filter { it.chatId == chatId }
            .mapTo(mutableSetOf(), ActiveAgentRun::runId)
        withContext(NonCancellable) {
            terminalizeAndCancel(runIds, isInterrupted = false)
        }
    }

    fun hasActiveRuns(chatId: Int): Boolean = _activeRuns.value.values.any { it.chatId == chatId }

    suspend fun <T> withChatGate(chatId: Int, block: suspend () -> T): T = withChatGate(listOf(chatId), block)

    suspend fun <T> withChatGate(chatIds: List<Int>, block: suspend () -> T): T {
        val locks = locksFor(chatIds)
        locks.forEach { it.lock() }
        return try {
            block()
        } finally {
            locks.asReversed().forEach { it.unlock() }
        }
    }

    private fun locksFor(chatIds: List<Int>): List<Mutex> = chatIds.distinct().sorted().map { chatLocks.computeIfAbsent(it) { Mutex() } }

    private fun requestCancellation(runIds: Set<String>, isInterrupted: Boolean) {
        if (runIds.isEmpty()) return
        scope.launch {
            terminalizeAndCancel(runIds, isInterrupted)
        }
    }

    private suspend fun terminalizeAndCancel(runIds: Set<String>, isInterrupted: Boolean) {
        if (runIds.isEmpty()) return
        if (isInterrupted) interruptingRunIds += runIds
        val completedAt = currentEpochSeconds()
        runIds.forEach { runId ->
            runCatching {
                cancelAndJoinAgentRun(jobs[runId]) {
                    chatRepository.finishActiveAgentRun(
                        runId = runId,
                        status = if (isInterrupted) AgentRunStatus.INTERRUPTED else AgentRunStatus.CANCELED,
                        completedAt = completedAt,
                        terminalError = if (isInterrupted) AgentRunTerminalError.SERVICE_STOPPED else null
                    )
                }
            }
        }
    }

    private fun failQueuedStarts(requests: List<AgentRunRequest>, error: RuntimeException) {
        scope.launch {
            val completedAt = currentEpochSeconds()
            val message = error.message?.takeIf { it.isNotBlank() }
                ?: "Android did not allow the foreground agent service to start."
            requests.forEach { request ->
                runCatching {
                    chatRepository.updateAgentMessage(
                        terminalAgentMessage(request.assistantMessage, message, completedAt)
                    )
                }
                runCatching {
                    chatRepository.finishQueuedAgentRun(
                        request.runId,
                        AgentRunStatus.FAILED,
                        completedAt,
                        message
                    )
                }
            }
        }
    }

    private suspend fun execute(request: AgentRunRequest) {
        val startedAt = currentEpochSeconds()
        var assistantMessage = request.assistantMessage
        try {
            if (!withContext(NonCancellable) { chatRepository.markAgentRunRunning(request.runId, startedAt) }) return
            val outcome = collectGenerationOutcome {
                chatRepository.completeChat(
                    request.userMessages,
                    request.assistantMessages,
                    request.platform,
                    request.runId
                ).collectApiStateUpdates(
                    onUpdate = { content, thoughts, timeline ->
                        assistantMessage = assistantMessage.copy(
                            content = content,
                            thoughts = thoughts,
                            timeline = timeline
                        )
                        streamPublisher.publish(request.runId, assistantMessage)
                    },
                    onNotice = { notice, persistent ->
                        _notices.tryEmit(AgentRunNotice(request.chatId, request.runId, notice, persistent))
                    },
                    publishIntervalMillis = 0L,
                    onCompactionState = { active ->
                        _notices.tryEmit(
                            AgentRunNotice(
                                request.chatId,
                                request.runId,
                                if (active) context.getString(R.string.chat_context_compacting) else "",
                                key = "compaction"
                            )
                        )
                    }
                )
            }
            val terminal = outcome.toTerminalUpdate()
            val completedAt = currentEpochSeconds()
            commitStreamTerminal(
                chatId = request.chatId,
                runId = request.runId,
                terminalMessage = terminalAgentMessage(assistantMessage, terminal.error, completedAt),
                finishRun = {
                    chatRepository.finishAgentRun(
                        request.runId,
                        terminal.status,
                        completedAt,
                        terminal.error
                    )
                }
            )
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                val completedAt = currentEpochSeconds()
                val isInterrupted = interruptingRunIds.remove(request.runId)
                commitStreamTerminal(
                    chatId = request.chatId,
                    runId = request.runId,
                    terminalMessage = assistantMessage.copy(createdAt = completedAt).resetActiveRevision(),
                    finishRun = {
                        chatRepository.finishAgentRun(
                            request.runId,
                            if (isInterrupted) AgentRunStatus.INTERRUPTED else AgentRunStatus.CANCELED,
                            completedAt,
                            if (isInterrupted) AgentRunTerminalError.SERVICE_STOPPED else null
                        )
                    }
                )
            }
            throw error
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                val completedAt = currentEpochSeconds()
                val message = error.message ?: "Unknown provider error."
                commitStreamTerminal(
                    chatId = request.chatId,
                    runId = request.runId,
                    terminalMessage = terminalAgentMessage(assistantMessage, message, completedAt),
                    finishRun = {
                        chatRepository.finishAgentRun(
                            request.runId,
                            AgentRunStatus.FAILED,
                            completedAt,
                            message
                        )
                    }
                )
            }
        }
    }

    private suspend fun commitStreamTerminal(
        chatId: Int,
        runId: String,
        terminalMessage: MessageV2,
        finishRun: suspend () -> Boolean
    ) {
        val persistError = persistLiveTerminal(runId, terminalMessage)
        val committed = runCatching {
            commitTerminalAgentRun(
                finishRun = finishRun,
                persistMessage = {
                    if (persistError != null) {
                        chatRepository.updateAgentMessage(terminalMessage)
                    }
                }
            )
        }
        if (shouldClearLiveSnapshot(persistError, committed.getOrNull())) {
            withContext(NonCancellable) { streamPublisher.clear(runId) }
        }
        if (committed.getOrNull() != true) {
            reportSaveFailure(chatId, runId, persistError ?: committed.exceptionOrNull())
        }
    }

    private suspend fun persistLiveTerminal(runId: String, message: MessageV2): Throwable? {
        streamPublisher.publish(runId, message)
        return streamPublisher.flush(runId)
    }

    private fun reportSaveFailure(chatId: Int, runId: String, error: Throwable?) {
        if (error == null) return
        _notices.tryEmit(
            AgentRunNotice(
                chatId = chatId,
                runId = runId,
                message = saveFailureNotice(error),
                persistent = true
            )
        )
    }
}

internal fun saveFailureNotice(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: "Couldn't save this reply."

internal fun shouldClearLiveSnapshot(persistError: Throwable?, terminalTransitionCommitted: Boolean?): Boolean = persistError == null || terminalTransitionCommitted == true

internal data class AgentRunTerminalUpdate(val status: String, val error: String?)

internal fun terminalAgentMessage(message: MessageV2, error: String?, completedAt: Long): MessageV2 {
    if (error == null) return message.copy(createdAt = completedAt).resetActiveRevision()

    val updatedContent = buildAssistantErrorContent(message.content, error)
    val appendedError = assistantErrorAppendedText(message.content, updatedContent)
    return message.copy(
        content = updatedContent,
        timeline = message.timeline.appendChronologicalText(appendedError),
        createdAt = completedAt
    ).resetActiveRevision()
}

internal fun ApiStateFlowOutcome.toTerminalUpdate(): AgentRunTerminalUpdate = when (this) {
    ApiStateFlowOutcome.Completed -> AgentRunTerminalUpdate(AgentRunStatus.COMPLETED, null)

    is ApiStateFlowOutcome.Failed -> AgentRunTerminalUpdate(AgentRunStatus.FAILED, message)

    ApiStateFlowOutcome.Incomplete -> AgentRunTerminalUpdate(
        AgentRunStatus.FAILED,
        "Provider stream ended without completion."
    )
}

internal suspend fun collectGenerationOutcome(
    timeoutMillis: Long = AgentRunLimits().runTimeoutMillis,
    collect: suspend () -> ApiStateFlowOutcome
): ApiStateFlowOutcome = withTimeoutOrNull(timeoutMillis) { collect() }
    ?: ApiStateFlowOutcome.Failed("Agent run timed out after $timeoutMillis ms.")

internal suspend fun collectCompactionResult(
    timeoutMillis: Long,
    timeoutMessage: String,
    compact: suspend () -> CompactionResult
): CompactionResult = withTimeoutOrNull(timeoutMillis) { compact() }
    ?: CompactionResult(CompactionStatus.FAILED, timeoutMessage)

internal suspend fun commitTerminalAgentRun(
    finishRun: suspend () -> Boolean,
    persistMessage: suspend () -> Unit
): Boolean = withContext(NonCancellable) {
    if (!finishRun()) return@withContext false
    persistMessage()
    true
}

internal suspend fun cancelAndJoinAgentRun(job: Job?, finishActiveRun: suspend () -> Unit) {
    job?.cancelAndJoin()
    finishActiveRun()
}

internal fun Job.invokeOnCompletionCleanup(cleanup: () -> Unit) {
    invokeOnCompletion { cleanup() }
}

private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000
