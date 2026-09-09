package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentRunEvent
import dev.chungjungsoo.gptmobile.data.agent.AgentRunner
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.agent.provider.AnthropicMessagesAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.GeminiAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.LiteRtLmAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.OpenAICompatibleAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.OpenAIResponsesAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.ProviderAttachmentEncoder
import dev.chungjungsoo.gptmobile.data.agent.tool.AgentToolResolver
import dev.chungjungsoo.gptmobile.data.agent.tool.ResolvedAgentTool
import dev.chungjungsoo.gptmobile.data.context.AnthropicNativeCompactor
import dev.chungjungsoo.gptmobile.data.context.ApiErrorKind
import dev.chungjungsoo.gptmobile.data.context.CompactionCoordinator
import dev.chungjungsoo.gptmobile.data.context.CompactionOutcome
import dev.chungjungsoo.gptmobile.data.context.CompactionResult
import dev.chungjungsoo.gptmobile.data.context.CompactionStatus
import dev.chungjungsoo.gptmobile.data.context.CompactionStore
import dev.chungjungsoo.gptmobile.data.context.ContextBuilder
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.context.ModelCapacityResolver
import dev.chungjungsoo.gptmobile.data.context.ModelContextSettings
import dev.chungjungsoo.gptmobile.data.context.OpenAINativeCompactor
import dev.chungjungsoo.gptmobile.data.context.PreparedContext
import dev.chungjungsoo.gptmobile.data.context.ProviderContextPolicy
import dev.chungjungsoo.gptmobile.data.context.RemoteContextWindowLookup
import dev.chungjungsoo.gptmobile.data.context.TextContextCompactor
import dev.chungjungsoo.gptmobile.data.context.TokenEstimator
import dev.chungjungsoo.gptmobile.data.context.ToolEvidence
import dev.chungjungsoo.gptmobile.data.context.attributedTurnsForCompaction
import dev.chungjungsoo.gptmobile.data.context.groupPersistedConversation
import dev.chungjungsoo.gptmobile.data.context.outputReserveTokens
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.dao.AgentRunDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryResult
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnResult
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveRunId
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.GroqAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.di.DeviceSocModel
import dev.chungjungsoo.gptmobile.util.FileUtils
import dev.chungjungsoo.gptmobile.util.stripAssistantErrorNote
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class ChatRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val chatRoomV2Dao: ChatRoomV2Dao,
    private val messageV2Dao: MessageV2Dao,
    private val chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
    private val agentPersistenceDao: AgentPersistenceDao,
    private val agentRunDao: AgentRunDao,
    private val settingRepository: SettingRepository,
    private val openAIAPI: OpenAIAPI,
    private val groqAPI: GroqAPI,
    private val anthropicAPI: AnthropicAPI,
    private val googleAPI: GoogleAPI,
    private val attachmentUploadCoordinator: AttachmentUploadCoordinator,
    private val contextBuilder: ContextBuilder,
    private val agentToolResolver: AgentToolResolver,
    private val toolEventRecorder: ToolEventRecorder,
    private val localRuntime: LocalRuntime,
    private val localModelRepository: LocalModelRepository,
    private val modelCatalogRepository: ModelCatalogRepository,
    @param:DeviceSocModel private val deviceSocModel: String,
    private val compactionStore: CompactionStore,
    private val remoteContextWindowLookup: RemoteContextWindowLookup
) : ChatRepository {
    private val providerAttachmentEncoder = ProviderAttachmentEncoder(context)
    private val openAIResponsesAdapter = OpenAIResponsesAdapter(openAIAPI, providerAttachmentEncoder)
    private val openAICompatibleAdapter = OpenAICompatibleAdapter(openAIAPI, groqAPI, providerAttachmentEncoder)
    private val anthropicMessagesAdapter = AnthropicMessagesAdapter(anthropicAPI, providerAttachmentEncoder)
    private val geminiAdapter = GeminiAdapter(googleAPI, providerAttachmentEncoder)
    private val liteRtLmAdapter = LiteRtLmAdapter(
        localRuntime = localRuntime,
        localModelRepository = localModelRepository,
        ignoredAttachmentsNotice = contextString(
            R.string.local_platform_ignored_attachments,
            LiteRtLmAdapter.DEFAULT_IGNORED_ATTACHMENTS
        ),
        modelNotDownloadedError = contextString(
            R.string.local_platform_model_not_downloaded,
            LiteRtLmAdapter.DEFAULT_MODEL_NOT_DOWNLOADED
        ),
        waitingForEngineNotice = contextString(
            R.string.local_platform_waiting_for_engine,
            LiteRtLmAdapter.DEFAULT_WAITING_FOR_ENGINE
        ),
        tooManyImagesNotice = contextString(
            R.string.local_platform_too_many_images,
            LiteRtLmAdapter.DEFAULT_TOO_MANY_IMAGES
        ),
        loadingModelNotice = contextString(
            R.string.local_platform_loading_model,
            LiteRtLmAdapter.DEFAULT_LOADING_MODEL
        ),
        gpuUnavailableNotice = contextString(
            R.string.local_platform_gpu_unavailable_cpu,
            LiteRtLmAdapter.DEFAULT_GPU_UNAVAILABLE
        ),
        npuUnavailableNotice = contextString(
            R.string.local_platform_npu_unavailable_cpu,
            LiteRtLmAdapter.DEFAULT_NPU_UNAVAILABLE
        ),
        engineLoadFailedError = contextString(
            R.string.local_platform_engine_load_failed,
            LiteRtLmAdapter.DEFAULT_ENGINE_LOAD_FAILED
        ),
        modelCatalogRepository = modelCatalogRepository,
        deviceSocModel = deviceSocModel,
        loadImageBytes = { attachment ->
            val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
            FileUtils.readImageBytesForLocalInference(context, filePath)
        }
    )
    private val capacityResolver = ModelCapacityResolver(
        compactionStore,
        modelCatalogRepository,
        deviceSocModel,
        remoteContextWindowLookup
    )
    private val compactionCoordinator = CompactionCoordinator(
        store = compactionStore,
        contextBuilder = contextBuilder,
        capacityResolver = capacityResolver,
        textCompactor = TextContextCompactor(::generateSamePlatformText),
        nativeCompactors = listOf(
            OpenAINativeCompactor(openAIAPI) { input -> encodeOpenAiCompactInput(input) },
            AnthropicNativeCompactor(anthropicAPI) { input -> encodeAnthropicCompactInput(input) }
        )
    )

    override suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        runId: String
    ): Flow<ApiState> = flow {
        emit(ApiState.Loading)
        try {
            val resolvedTools = agentToolResolver.resolve(platform.uid)
            val chatId = userMessages.firstOrNull()?.chatId ?: 0
            val toolEvidence = loadToolEvidence(assistantMessages, platform)
            val toolDefinitionTokens = estimateAssignedToolDefinitionTokens(resolvedTools)
            val outputReserve = outputReserveTokens(platform, resolvedTools.isNotEmpty())
            val outcome = compactionCoordinator.prepare(
                chatId = chatId,
                userMessages = userMessages,
                assistantMessages = assistantMessages,
                platform = platform,
                toolEvidence = toolEvidence,
                toolDefinitionTokens = toolDefinitionTokens,
                toolDefinitionNames = resolvedTools.map { it.tool.definition.name },
                outputReserve = outputReserve,
                onCompactionState = { active -> emit(ApiState.Compaction(active)) }
            )
            val prepared = when (outcome) {
                is CompactionOutcome.NeedsCapacity -> {
                    emit(
                        ApiState.Error(
                            message = "Enter this model's context window before continuing.",
                            kind = ApiErrorKind.MODEL_CAPACITY_UNKNOWN,
                            platformUid = platform.uid,
                            model = platform.model,
                            endpoint = platform.apiUrl
                        )
                    )
                    return@flow
                }

                is CompactionOutcome.Failed -> {
                    emit(ApiState.Error(outcome.message, ApiErrorKind.COMPACTION_FAILED, platform.uid, platform.model, platform.apiUrl))
                    return@flow
                }

                is CompactionOutcome.InputTooLarge -> {
                    emit(ApiState.Error(outcome.message, ApiErrorKind.INPUT_TOO_LARGE, platform.uid, platform.model, platform.apiUrl))
                    return@flow
                }

                is CompactionOutcome.Compacted -> outcome.prepared

                is CompactionOutcome.Ready -> outcome.prepared
            }
            val onUnconfirmedCancellation: suspend (String) -> Unit = {
                agentRunDao.recordCancellationWarning(
                    runId,
                    contextString(R.string.chat_remote_cancellation_unconfirmed, "Remote cancellation could not be confirmed. The provider may still be generating this reply.")
                )
            }
            val session = openPreparedSession(
                prepared.turns,
                platform,
                resolvedTools.map { it.tool },
                prepared,
                onUnconfirmedCancellation
            )
            val runnerTools = if (session.handlesToolsInternally) {
                emptyList()
            } else {
                resolvedTools.map { it.tool }
            }
            val trace = ToolTraceSession(runId, resolvedTools, toolEventRecorder)

            val mutableSession = object : AgentProviderSession {
                var inner = session
                override val handlesToolsInternally: Boolean
                    get() = inner.handlesToolsInternally
                override fun streamRound(
                    tools: List<AgentToolDefinition>,
                    exchanges: List<AgentToolExchange>
                ) = inner.streamRound(tools, exchanges)
            }
            var activePrepared = prepared
            val completedExchanges = mutableListOf<AgentToolExchange>()
            val assistantProgress = StringBuilder()
            var usageAdjustment = 0
            var latestUsage: ProviderEvent.Usage? = null
            var lastRequestEstimate = prepared.tokenBudget.estimatedInputTokens
            var didCompactDuringRun = false
            var compactedProgressLength = 0
            var runCompleted = false
            val runner = AgentRunner(
                afterCompleteExchange = { exchanges ->
                    // The callback follows exactly one completed exchange. Keep the durable source
                    // separately when the provider's working exchange list is replaced.
                    completedExchanges += exchanges.last()
                    val pendingEvidence = exchanges.toToolEvidence(runId)
                    val exchangeTokens = TokenEstimator.estimateEvidence(pendingEvidence) +
                        exchanges.sumOf { exchange -> exchange.calls.sumOf { TokenEstimator.estimateText(it.arguments.toString()) + 16 } }
                    val nextEstimate = activePrepared.tokenBudget.estimatedInputTokens + exchangeTokens +
                        TokenEstimator.estimateText(assistantProgress.substring(compactedProgressLength))
                    val needsCompaction = nextEstimate + usageAdjustment.coerceAtLeast(0) + outputReserve >= activePrepared.tokenBudget.contextLimit
                    if (!needsCompaction) {
                        lastRequestEstimate = nextEstimate
                        exchanges
                    } else {
                        val progressMessages = assistantMessages.toMutableList()
                        while (progressMessages.size < userMessages.size) progressMessages += emptyList<MessageV2>()
                        if (progressMessages.isNotEmpty()) {
                            progressMessages[progressMessages.lastIndex] = listOf(
                                MessageV2(
                                    content = assistantProgress.toString().ifBlank {
                                        "Completed tool actions are included below; continue the current request."
                                    },
                                    platformType = platform.uid,
                                    currentRunId = runId
                                )
                            )
                        }
                        // Mid-run state is transient until the transcript is committed. Never save
                        // a checkpoint covering actions that are not yet durable.
                        when (
                            val mid = compactionCoordinator.prepare(
                                chatId = 0,
                                userMessages = userMessages,
                                assistantMessages = progressMessages,
                                platform = platform,
                                toolEvidence = toolEvidence + completedExchanges.toToolEvidence(runId),
                                toolDefinitionTokens = toolDefinitionTokens,
                                toolDefinitionNames = resolvedTools.map { it.tool.definition.name },
                                force = true,
                                protectCurrent = false,
                                outputReserve = outputReserve,
                                onCompactionState = { active -> emit(ApiState.Compaction(active)) }
                            )
                        ) {
                            is CompactionOutcome.Compacted -> {
                                activePrepared = mid.prepared
                                mutableSession.inner = openPreparedSession(
                                    activePrepared.turns,
                                    platform,
                                    resolvedTools.map { it.tool },
                                    activePrepared,
                                    onUnconfirmedCancellation
                                )
                                didCompactDuringRun = true
                                usageAdjustment = 0
                                latestUsage = null
                                lastRequestEstimate = activePrepared.tokenBudget.estimatedInputTokens
                                compactedProgressLength = assistantProgress.length
                                completedExchanges.toList()
                            }

                            is CompactionOutcome.Failed -> throw ContextPreparationException(mid.message, ApiErrorKind.COMPACTION_FAILED)

                            is CompactionOutcome.InputTooLarge -> throw ContextPreparationException(mid.message, ApiErrorKind.INPUT_TOO_LARGE)

                            is CompactionOutcome.NeedsCapacity -> throw ContextPreparationException("Enter this model's context window before continuing.", ApiErrorKind.MODEL_CAPACITY_UNKNOWN)

                            is CompactionOutcome.Ready -> throw ContextPreparationException("Context could not be reduced. Original transcript was kept.", ApiErrorKind.COMPACTION_FAILED)
                        }
                    }
                }
            )
            runner.run(mutableSession, runnerTools).collect { runEvent ->
                when (runEvent) {
                    is AgentRunEvent.Provider -> when (val providerEvent = runEvent.event) {
                        is ProviderEvent.ThinkingDelta -> emit(ApiState.Thinking(providerEvent.text))

                        is ProviderEvent.TextDelta -> {
                            assistantProgress.append(providerEvent.text)
                            emit(ApiState.Success(providerEvent.text))
                        }

                        is ProviderEvent.Usage -> {
                            latestUsage = providerEvent
                            usageAdjustment = providerEvent.inputTokens - lastRequestEstimate + providerEvent.outputTokens
                        }

                        is ProviderEvent.Failed -> emit(ApiState.Error(providerEvent.message))

                        is ProviderEvent.Notice -> emit(ApiState.Notice(providerEvent.message, providerEvent.persistent))

                        is ProviderEvent.ToolCall -> {
                            val toolEvent = trace.start(providerEvent)
                            emit(ApiState.ToolCall(toolEvent.sequence))
                        }

                        is ProviderEvent.ToolResult -> Unit

                        ProviderEvent.Completed -> runCompleted = true
                    }

                    is AgentRunEvent.ToolStarted -> Unit

                    is AgentRunEvent.ToolFinished -> trace.finish(runEvent.call, runEvent.result)

                    is AgentRunEvent.Notice -> emit(ApiState.Notice(runEvent.message, runEvent.persistent))
                }
            }
            val usage = latestUsage
            if (runCompleted && !didCompactDuringRun && usage != null && userMessages.isNotEmpty()) {
                val completedMessages = assistantMessages.toMutableList()
                while (completedMessages.size < userMessages.size) completedMessages += emptyList<MessageV2>()
                completedMessages[completedMessages.lastIndex] = listOf(
                    MessageV2(content = assistantProgress.toString(), platformType = platform.uid, currentRunId = runId)
                )
                compactionCoordinator.recordUsage(
                    chatId,
                    platform,
                    contextBuilder.build(userMessages, completedMessages, platform),
                    loadToolEvidence(completedMessages, platform),
                    resolvedTools.map { it.tool.definition.name },
                    prepared,
                    usage.inputTokens + usage.outputTokens
                )
            }
        } finally {
            withContext(NonCancellable) {
                toolEventRecorder.cancelRun(runId, currentEpochSeconds())
            }
        }
    }.catch { error ->
        emit(ApiState.Error(error.message ?: "Failed to complete chat", (error as? ContextPreparationException)?.kind ?: ApiErrorKind.GENERIC, platform.uid, platform.model, platform.apiUrl))
    }.onCompletion {
        emit(ApiState.Done)
    }

    private suspend fun ensureProviderReferencesForTurns(
        turns: List<ConversationTurn>,
        platform: PlatformV2
    ): List<ConversationTurn> {
        val preparedUserMessages = prepareMessagesForPlatform(turns.map { it.userMessage }, platform)
        return turns.mapIndexed { index, turn ->
            turn.copy(userMessage = preparedUserMessages[index])
        }
    }

    private suspend fun validateInlineBudgetIfNeeded(
        contextTurns: List<ConversationTurn>,
        platform: PlatformV2
    ) {
        val maxInlineBytes = ProviderContextPolicy.forClientType(platform.compatibleType).maxInlineAttachmentBytes ?: return
        attachmentUploadCoordinator.validateInlineAttachmentBudget(contextTurns, maxInlineBytes)
    }

    private suspend fun prepareMessagesForPlatform(
        messages: List<MessageV2>,
        platform: PlatformV2
    ): List<MessageV2> {
        val updatedMessages = messages.map { attachmentUploadCoordinator.ensureMessageAttachmentsForPlatform(it, platform) }
        val changedMessages = updatedMessages
            .zip(messages)
            .mapNotNull { (updated, original) -> updated.takeIf { it.id != 0 && it != original } }

        if (changedMessages.isNotEmpty()) {
            messageV2Dao.editMessages(*changedMessages.toTypedArray())
        }

        return updatedMessages
    }

    override suspend fun fetchChatList(): List<ChatRoom> = chatRoomDao.getChatRooms()

    override suspend fun fetchChatListV2(): List<ChatRoomV2> = chatRoomV2Dao.getChatRooms()

    override suspend fun searchChatsV2(query: String): List<ChatRoomV2> {
        if (query.isBlank()) {
            return chatRoomV2Dao.getChatRooms()
        }

        // Search by title
        val titleMatches = chatRoomV2Dao.searchChatRoomsByTitle(query)

        // Search by message content and get chat IDs
        val messageMatchChatIds = messageV2Dao.searchMessagesByContent(query)

        // Get all chat rooms and filter by message match IDs
        val allChatRooms = chatRoomV2Dao.getChatRooms()
        val messageMatches = allChatRooms.filter { it.id in messageMatchChatIds }

        // Combine results and remove duplicates, maintaining order by updatedAt
        return (titleMatches + messageMatches)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun fetchMessages(chatId: Int): List<Message> = messageDao.loadMessages(chatId)

    override suspend fun fetchMessagesV2(chatId: Int): List<MessageV2> = messageV2Dao.loadMessages(chatId)

    override fun observeMessagesV2(chatId: Int): Flow<List<MessageV2>> = messageV2Dao.observeMessages(chatId)

    override fun observeAgentRuns(chatId: Int) = agentRunDao.observeByChatId(chatId)

    override fun observeToolEvents(chatId: Int): Flow<List<ToolEvent>> = toolEventRecorder.observeChat(chatId)

    override suspend fun fetchChatPlatformModels(chatId: Int): Map<String, String> = chatPlatformModelV2Dao.getByChatId(chatId).associate {
        it.platformUid to it.model
    }

    override suspend fun saveChatPlatformModels(chatId: Int, models: Map<String, String>) {
        val rows = models
            .filterKeys { it.isNotBlank() }
            .map { (platformUid, model) ->
                ChatPlatformModelV2(
                    chatId = chatId,
                    platformUid = platformUid,
                    model = model.trim()
                )
            }

        if (rows.isNotEmpty()) {
            chatPlatformModelV2Dao.upsertAll(*rows.toTypedArray())
        }
    }

    override suspend fun persistAgentTurn(request: PersistAgentTurnRequest): PersistAgentTurnResult = agentPersistenceDao.persistAgentTurn(request)

    override suspend fun persistAgentRetry(request: PersistAgentRetryRequest): PersistAgentRetryResult = agentPersistenceDao.persistAgentRetry(request)

    override suspend fun markAgentRunRunning(runId: String, startedAt: Long): Boolean = agentRunDao.markRunning(runId, startedAt) == 1

    override suspend fun finishAgentRun(
        runId: String,
        status: String,
        completedAt: Long,
        terminalError: String?
    ): Boolean = agentRunDao.finishRunning(runId, status, completedAt, terminalError) == 1

    override suspend fun finishQueuedAgentRun(
        runId: String,
        status: String,
        completedAt: Long,
        terminalError: String?
    ): Boolean = agentRunDao.finishQueued(runId, status, completedAt, terminalError) == 1

    override suspend fun finishActiveAgentRun(
        runId: String,
        status: String,
        completedAt: Long,
        terminalError: String?
    ): Boolean = agentRunDao.finishActive(runId, status, completedAt, terminalError) == 1

    override suspend fun updateAgentMessage(message: MessageV2) {
        messageV2Dao.editMessages(message)
    }

    override suspend fun interruptActiveAgentRuns(completedAt: Long): Int = agentRunDao.interruptActiveRuns(completedAt)

    override suspend fun migrateToChatRoomV2MessageV2() {
        val leftOverChatRoomV2s = chatRoomV2Dao.getChatRooms()
        leftOverChatRoomV2s.forEach { chatPlatformModelV2Dao.deleteByChatId(it.id) }
        chatRoomV2Dao.deleteChatRooms(*leftOverChatRoomV2s.toTypedArray())

        val chatList = fetchChatList()
        val platforms = settingRepository.fetchPlatformV2s()
        val apiTypeMap = mutableMapOf<ApiType, String>()
        val modelByPlatformUid = mutableMapOf<String, String>()

        platforms.forEach { platform ->
            modelByPlatformUid[platform.uid] = platform.model
            when (platform.name) {
                "OpenAI" -> apiTypeMap[ApiType.OPENAI] = platform.uid
                "Anthropic" -> apiTypeMap[ApiType.ANTHROPIC] = platform.uid
                "Google" -> apiTypeMap[ApiType.GOOGLE] = platform.uid
                "Groq" -> apiTypeMap[ApiType.GROQ] = platform.uid
                "Ollama" -> apiTypeMap[ApiType.OLLAMA] = platform.uid
            }
        }

        chatList.forEach { chatRoom ->
            val messages = messageDao.loadMessages(chatRoom.id).map { m ->
                MessageV2(
                    id = m.id,
                    chatId = m.chatId,
                    content = m.content,
                    attachments = listOf(),
                    revisions = listOf(),
                    linkedMessageId = m.linkedMessageId,
                    platformType = m.platformType?.let { apiTypeMap[it] },
                    createdAt = m.createdAt
                )
            }

            val enabledPlatformUids = chatRoom.enabledPlatform.mapNotNull { apiTypeMap[it] }.filter { it.isNotBlank() }
            chatRoomV2Dao.addChatRoom(
                ChatRoomV2(
                    id = chatRoom.id,
                    title = chatRoom.title,
                    enabledPlatform = enabledPlatformUids,
                    createdAt = chatRoom.createdAt,
                    updatedAt = chatRoom.createdAt
                )
            )

            val modelRows = enabledPlatformUids.map { platformUid ->
                ChatPlatformModelV2(
                    chatId = chatRoom.id,
                    platformUid = platformUid,
                    model = modelByPlatformUid[platformUid] ?: ""
                )
            }

            if (modelRows.isNotEmpty()) {
                chatPlatformModelV2Dao.upsertAll(*modelRows.toTypedArray())
            }

            messageV2Dao.addMessages(*messages.toTypedArray())
        }
    }

    override fun generateDefaultChatTitle(messages: List<MessageV2>): String? = messages.sortedBy { it.createdAt }.firstOrNull { it.platformType == null }?.content?.replace('\n', ' ')?.take(50)

    override suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String) {
        chatRoomV2Dao.editChatRoom(chatRoom.copy(title = title.replace('\n', ' ').take(50)))
    }

    override suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>, chatPlatformModels: Map<String, String>): ChatRoomV2 {
        if (chatRoom.id == 0) {
            // New Chat
            val chatId = chatRoomV2Dao.addChatRoom(chatRoom)
            val updatedMessages = messages.map { it.copy(chatId = chatId.toInt()) }
            messageV2Dao.addMessages(*updatedMessages.toTypedArray())
            saveChatPlatformModels(
                chatId = chatId.toInt(),
                models = chatPlatformModels.filterKeys { it in chatRoom.enabledPlatform }
            )

            val savedChatRoom = chatRoom.copy(id = chatId.toInt())
            updateChatTitle(savedChatRoom, updatedMessages[0].content)

            return savedChatRoom.copy(title = updatedMessages[0].content.replace('\n', ' ').take(50))
        }

        agentPersistenceDao.saveChatSnapshot(
            chatRoom = chatRoom,
            messages = messages,
            chatPlatformModels = chatPlatformModels.filterKeys { it in chatRoom.enabledPlatform }
        )

        return chatRoom
    }

    override suspend fun duplicateChatV2(chatRoom: ChatRoomV2): ChatRoomV2 {
        val duplicatedTitle = "${chatRoom.title} (copy)".take(50)
        val timestamp = System.currentTimeMillis() / 1000
        val duplicate = agentPersistenceDao.duplicateChatWithHistory(
            sourceChatId = chatRoom.id,
            title = duplicatedTitle,
            timestamp = timestamp
        )
        return duplicate
    }

    override suspend fun deleteChats(chatRooms: List<ChatRoom>) {
        chatRoomDao.deleteChatRooms(*chatRooms.toTypedArray())
    }

    override suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>) {
        chatRoomV2Dao.deleteChatRooms(*chatRooms.toTypedArray())
    }

    override suspend fun getModelContextSettings(platform: PlatformV2): ModelContextSettings {
        val resolution = capacityResolver.resolve(platform)
        val detected = when (resolution) {
            is dev.chungjungsoo.gptmobile.data.context.ModelCapacityResolution.Known ->
                resolution.capacity.detectedContextWindowTokens

            is dev.chungjungsoo.gptmobile.data.context.ModelCapacityResolution.Unknown -> null
        }
        val overrideTokens = compactionStore.getCapacity(platform.uid, platform.apiUrl, platform.model)?.overrideContextWindowTokens
        return ModelContextSettings(
            platformUid = platform.uid,
            model = platform.model,
            endpoint = platform.apiUrl,
            overrideContextWindowTokens = overrideTokens,
            detectedContextWindowTokens = detected,
            resumableReplies = platform.resumableReplies,
            supportsResumableReplies = ModelCapacityResolver.supportsResumableReplies(platform),
            canCompact = true
        )
    }

    override suspend fun saveModelContextSettings(
        platform: PlatformV2,
        contextWindowTokens: Int?,
        resumableReplies: Boolean
    ) {
        capacityResolver.saveOverride(platform, contextWindowTokens)
        val current = settingRepository.getPlatformV2ById(platform.id) ?: platform
        if (current.resumableReplies != resumableReplies) {
            settingRepository.updatePlatformV2(current.copy(resumableReplies = resumableReplies))
        }
    }

    override suspend fun compactNow(chatId: Int, platform: PlatformV2): CompactionResult {
        val grouped = groupPersistedConversation(messageV2Dao.loadMessages(chatId))
        val resolvedTools = agentToolResolver.resolve(platform.uid)
        val outcome = compactionCoordinator.prepare(
            chatId = chatId,
            userMessages = grouped.first,
            assistantMessages = grouped.second,
            platform = platform,
            toolEvidence = loadToolEvidence(grouped.second, platform),
            toolDefinitionTokens = estimateAssignedToolDefinitionTokens(resolvedTools),
            toolDefinitionNames = resolvedTools.map { it.tool.definition.name },
            force = true,
            outputReserve = outputReserveTokens(platform, resolvedTools.isNotEmpty())
        )
        return outcome.toResult()
    }

    override suspend fun validateDraftCapacity(platform: PlatformV2, message: MessageV2): CompactionResult? {
        val resolvedTools = agentToolResolver.resolve(platform.uid)
        val toolDefinitionTokens = estimateAssignedToolDefinitionTokens(resolvedTools)
        return compactionCoordinator.validateDraft(platform, message, toolDefinitionTokens, outputReserveTokens(platform, resolvedTools.isNotEmpty()))?.toResult()
    }

    private suspend fun generateSamePlatformText(platform: PlatformV2, systemPrompt: String, turns: List<ConversationTurn>): String {
        val session = openPreparedSession(turns, platform.copy(systemPrompt = systemPrompt, resumableReplies = false), emptyList())
        val text = StringBuilder()
        session.streamRound(emptyList(), emptyList()).collect { event ->
            when (event) {
                is ProviderEvent.TextDelta -> text.append(event.text)
                is ProviderEvent.Failed -> throw IllegalStateException(event.message)
                else -> Unit
            }
        }
        return text.toString()
    }

    private fun estimateAssignedToolDefinitionTokens(resolvedTools: List<ResolvedAgentTool>): Int = resolvedTools.sumOf { tool ->
        val definition = tool.tool.definition
        TokenEstimator.estimateToolDefinition(
            name = definition.name,
            description = definition.description,
            schemaJson = definition.inputSchema.toString()
        )
    }

    private fun compactTurnsWithEvidence(input: dev.chungjungsoo.gptmobile.data.context.CompactInput): List<ConversationTurn> = attributedTurnsForCompaction(input.turns, input.toolEvidence)

    private suspend fun encodeOpenAiCompactInput(input: dev.chungjungsoo.gptmobile.data.context.CompactInput): kotlinx.serialization.json.JsonArray {
        val messages = providerAttachmentEncoder.responsesInput(ensureProviderReferencesForTurns(compactTurnsWithEvidence(input), input.platform), input.platform.uid)
        return kotlinx.serialization.json.JsonArray(
            messages.map { message ->
                kotlinx.serialization.json.Json.parseToJsonElement(
                    dev.chungjungsoo.gptmobile.data.network.NetworkClient.openAIJson.encodeToString(message)
                )
            }
        )
    }

    private suspend fun encodeAnthropicCompactInput(input: dev.chungjungsoo.gptmobile.data.context.CompactInput) = providerAttachmentEncoder.anthropicMessages(ensureProviderReferencesForTurns(compactTurnsWithEvidence(input), input.platform), input.platform.uid)

    private suspend fun openPreparedSession(
        turns: List<ConversationTurn>,
        platform: PlatformV2,
        tools: List<dev.chungjungsoo.gptmobile.data.agent.AgentTool>,
        prepared: PreparedContext? = null,
        onUnconfirmedRemoteCancellation: (suspend (String) -> Unit)? = null
    ): AgentProviderSession {
        val preparedTurns = ensureProviderReferencesForTurns(turns, platform)
        validateInlineBudgetIfNeeded(preparedTurns, platform)
        return when (platform.compatibleType) {
            ClientType.OPENAI -> openAIResponsesAdapter.openSession(preparedTurns, platform, prepared, onUnconfirmedRemoteCancellation)

            ClientType.GROQ, ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM ->
                openAICompatibleAdapter.openSession(preparedTurns, platform)

            ClientType.ANTHROPIC -> anthropicMessagesAdapter.openSession(preparedTurns, platform, prepared)

            ClientType.GOOGLE -> geminiAdapter.openSession(preparedTurns, platform)

            ClientType.LITERT_LM -> liteRtLmAdapter.openSession(preparedTurns, platform, tools)
        }
    }

    private fun List<AgentToolExchange>.toToolEvidence(runId: String): List<ToolEvidence> = flatMap { exchange ->
        exchange.results.map { result ->
            val call = exchange.calls.firstOrNull { it.callId == result.callId }
            ToolEvidence(
                runId = runId,
                toolName = call?.name ?: result.callId,
                result = "Arguments: ${call?.arguments}\nOutcome: " + when (val content = result.content) {
                    is ToolResultContent.Text -> content.text
                    is ToolResultContent.Json -> content.value.toString()
                    is ToolResultContent.ResourceLinks -> content.links.joinToString { link -> "${link.name.orEmpty()}: ${link.uri}" }
                },
                isError = result.isError
            )
        }
    }

    private suspend fun loadToolEvidence(assistantMessages: List<List<MessageV2>>, platform: PlatformV2): List<ToolEvidence> {
        val runIds = assistantMessages.flatten()
            .filter { it.platformType == platform.uid }
            .mapNotNull { it.effectiveRunId() }
            .distinct()
        if (runIds.isEmpty()) return emptyList()
        return agentPersistenceDao.getToolEvents(runIds).map { event ->
            ToolEvidence(
                runId = event.runId,
                toolName = event.toolName,
                result = "Arguments: ${event.arguments}\nOutcome: ${event.result.orEmpty()}",
                isError = event.isError
            )
        }
    }

    private fun CompactionOutcome.toResult(): CompactionResult = when (this) {
        is CompactionOutcome.Ready -> CompactionResult(CompactionStatus.UNCHANGED, "Context already within budget.", prepared)
        is CompactionOutcome.Compacted -> CompactionResult(CompactionStatus.SUCCESS, "Conversation compacted.", prepared, checkpoint)
        is CompactionOutcome.NeedsCapacity -> CompactionResult(CompactionStatus.NEEDS_CAPACITY, "Enter this model's context window before continuing.")
        is CompactionOutcome.Failed -> CompactionResult(CompactionStatus.FAILED, message, checkpoint = preservedCheckpoint)
        is CompactionOutcome.InputTooLarge -> CompactionResult(CompactionStatus.INPUT_TOO_LARGE, message)
    }
    private fun contextString(resId: Int, fallback: String): String = runCatching { context.getString(resId) }.getOrDefault(fallback)
}

internal fun MessageV2.sendableAssistantContent(): String {
    val strippedContent = stripAssistantErrorNote(effectiveContent()).trim()
    return if (strippedContent.startsWith("Error: ")) "" else strippedContent
}

internal fun MessageV2.hasSendableAssistantPayload(): Boolean = sendableAssistantContent().isNotBlank() || attachments.isNotEmpty()

internal fun validateResponseInputPartsOrThrow(messageContent: String, partCount: Int, messageId: Int) {
    if (messageContent.isBlank() && partCount == 0) {
        throw IllegalStateException("No encodable message content for messageId=$messageId")
    }
}

private class ContextPreparationException(message: String, val kind: ApiErrorKind) : IllegalStateException(message)

private class ToolTraceSession(
    private val runId: String,
    tools: List<ResolvedAgentTool>,
    private val recorder: ToolEventRecorder
) {
    private val toolsByName = tools.associateBy { it.modelToolName }
    private val pendingEventIds = mutableMapOf<String, ArrayDeque<String>>()
    private var sequence = 0

    suspend fun start(call: ProviderEvent.ToolCall): ToolEvent {
        val resolved = toolsByName[call.name]
        val event = recorder.startTool(
            runId = runId,
            sequence = sequence++,
            callId = call.callId,
            toolName = resolved?.realToolName ?: call.name,
            modelToolName = call.name,
            arguments = call.arguments,
            connectionUid = resolved?.connectionUid,
            connectionName = resolved?.connectionName,
            startedAt = currentEpochSeconds()
        )
        pendingEventIds.getOrPut(call.callId, ::ArrayDeque).addLast(event.eventId)
        return event
    }

    suspend fun finish(call: ProviderEvent.ToolCall, result: AgentToolResult) {
        val eventId = pendingEventIds[call.callId]?.removeFirstOrNull() ?: return
        recorder.finishTool(
            eventId = eventId,
            result = result,
            completedAt = currentEpochSeconds(),
            error = result.errorMessage()
        )
    }
}

private fun AgentToolResult.errorMessage(): String? {
    if (!isError) return null
    return when (val value = content) {
        is ToolResultContent.Text -> value.text
        is ToolResultContent.Json -> value.value.toString()
        is ToolResultContent.ResourceLinks -> "Tool call failed."
    }
}

private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000
