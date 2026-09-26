package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import com.example.gptmobileai.debug.ToolMetricsCollector
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.agent.AgentRunEvent
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolExecutionBudget
import dev.chungjungsoo.gptmobile.data.agent.ToolPayloadMetrics
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.agent.agentRunnerForPlatform
import dev.chungjungsoo.gptmobile.data.agent.liveToolSystemPrompt
import dev.chungjungsoo.gptmobile.data.agent.provider.AnthropicMessagesAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.GeminiAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.LiteRtLmAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.OpenAICompatibleAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.OpenAIResponsesAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.ProviderAttachmentEncoder
import dev.chungjungsoo.gptmobile.data.agent.provider.RequestConstraints
import dev.chungjungsoo.gptmobile.data.agent.tool.AgentToolResolver
import dev.chungjungsoo.gptmobile.data.agent.tool.MeasuredAgentTool
import dev.chungjungsoo.gptmobile.data.agent.tool.ResolvedAgentTool
import dev.chungjungsoo.gptmobile.data.agent.tool.SharedToolCallBroker
import dev.chungjungsoo.gptmobile.data.agent.tool.isWebSearchEngine
import dev.chungjungsoo.gptmobile.data.agent.withDeviceLocation
import dev.chungjungsoo.gptmobile.data.context.ContextBuilder
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.context.ProviderContextPolicy
import dev.chungjungsoo.gptmobile.data.conversation.ConversationTitleSummarizer
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.dao.AgentRunDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryResult
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnResult
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.openai.response.GatewayProgress
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.model.ChatMcpToolConfig
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import dev.chungjungsoo.gptmobile.data.model.excludesMemory
import dev.chungjungsoo.gptmobile.data.model.isPrivateDestination
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.GroqAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.network.error.ErrorClassification
import dev.chungjungsoo.gptmobile.data.rag.FactRecall
import dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository
import dev.chungjungsoo.gptmobile.util.DocumentTextExtractor
import dev.chungjungsoo.gptmobile.util.FileUtils
import dev.chungjungsoo.gptmobile.util.stripAssistantErrorNote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class ChatRepositoryImpl(
    private val context: Context,
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
    private val deviceSocModel: String,
    private val titleSummarizer: ConversationTitleSummarizer? = null,
    private val factVault: FactVaultRepository? = null,
    private val toolMetricsCollector: ToolMetricsCollector? = null,
    private val knowledge: dev.chungjungsoo.gptmobile.data.knowledge.MemoryDocumentRepository? = null,
    private val toolApprovals: dev.chungjungsoo.gptmobile.data.permissions.ToolApprovalManager? = null,
    private val invocationLedger: dev.chungjungsoo.gptmobile.data.accounting.InvocationLedger? = null
) : ChatRepository {
    private val providerAttachmentEncoder = ProviderAttachmentEncoder(context)
    private val openAIResponsesAdapter = OpenAIResponsesAdapter(openAIAPI, providerAttachmentEncoder)
    private val openAICompatibleAdapter = OpenAICompatibleAdapter(openAIAPI, groqAPI, providerAttachmentEncoder)
    private val anthropicMessagesAdapter = AnthropicMessagesAdapter(anthropicAPI, providerAttachmentEncoder)
    private val geminiAdapter = GeminiAdapter(googleAPI, providerAttachmentEncoder)
    private val sharedToolCallBroker = SharedToolCallBroker()
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

    /** A single isolated text round. No resolver, history, memory injection or nested tool execution. */
    private suspend fun delegateToProfile(target: PlatformV2, task: String, maxTokens: Int, parentRunId: String, turnKey: String): String {
        val constraints = RequestConstraints(maxOutputTokens = maxTokens, allowTools = false, allowReasoning = false)
        val bounded = target.copy(
            reasoning = false,
            disableAllTools = true,
            systemPrompt = "Complete the supplied bounded task. Return a concise text answer. Do not call tools or delegate work."
        )
        val turns = listOf(ConversationTurn(MessageV2(content = task, platformType = null), null, true))
        val session = when (bounded.compatibleType) {
            ClientType.OPENAI -> openAIResponsesAdapter.openSession(turns, bounded, constraints)
            ClientType.GROQ, ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM, ClientType.LLAMA, ClientType.FREE -> openAICompatibleAdapter.openSession(turns, bounded, constraints)
            ClientType.ANTHROPIC -> anthropicMessagesAdapter.openSession(turns, bounded, constraints)
            ClientType.GOOGLE -> geminiAdapter.openSession(turns, bounded, constraints)
            ClientType.LITERT_LM -> liteRtLmAdapter.openSession(turns, bounded, emptyList(), constraints)
        }
        val text = StringBuilder()
        val accounted = invocationLedger?.wrap(
            session, parentRunId, turnKey, target.compatibleType.name, target.model, "delegate",
            dev.chungjungsoo.gptmobile.data.context.ContextBudgetService.estimate(task + bounded.systemPrompt), maxTokens,
            settingRepository.getFeatureSettings().tokenBudget.normalized().totalRunTokens
        ) ?: session
        accounted.streamRound(emptyList(), emptyList()).collect { event ->
            when (event) {
                is ProviderEvent.TextDelta -> {
                    check(text.length + event.text.length <= 16000) { "Delegated output exceeded the character limit." }
                    text.append(event.text)
                }
                is ProviderEvent.Failed -> error("The delegated provider failed.")
                is ProviderEvent.ToolCall -> error("Delegated tool calls are not permitted.")
                else -> Unit
            }
        }
        return text.toString()
    }

    override suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        runId: String,
        chatToolConfig: ChatMcpToolConfig?
    ): Flow<ApiState> = flow {
        emit(ApiState.Loading)
        emit(ApiState.ProgressCheckpoint("Preparing the response and checking the available context."))
        try {
            if (platform.compatibleType == ClientType.FREE) {
                check(FreeAiProvider.requireFor(platform).isAvailable) { "LLM7 is awaiting provider approval for app integration. Choose another Free provider." }
                require(userMessages.all { it.attachments.isEmpty() } && assistantMessages.flatten().all { it.attachments.isEmpty() }) {
                    "Free profiles support public text only. Start a chat without attachments, or select another platform."
                }
                emit(ApiState.Notice("Free provider · Memory off. Use public prompts only.", persistent = true))
            }
            if (!platform.excludesMemory()) {
                try {
                    factVault?.load()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emit(ApiState.Notice("Memory is unavailable for this response.", persistent = true))
                }
            }
            val contextTurns = withContext(Dispatchers.Default) {
                buildContextTurns(userMessages, assistantMessages, platform).also { turns ->
                    validateInlineBudgetIfNeeded(turns, platform)
                }
            }
            val diagnosticsEnabled = runCatching {
                settingRepository.getFeatureSettings().diagnosticsCollection
            }.getOrDefault(false)
            val customRunner = agentRunnerForPlatform(platform, chatToolConfig?.maxToolCalls)
            val budgetSettings = settingRepository.getFeatureSettings().tokenBudget.normalized()
            val profileBudget = budgetSettings.copy(contextTokens = minOf(budgetSettings.contextTokens, budgetSettings.profileContextCeilings[platform.uid] ?: Int.MAX_VALUE))
            val limits = if (platform.compatibleType == ClientType.FREE && FreeAiProvider.requireFor(platform) == FreeAiProvider.POLLINATIONS) {
                // The legacy GET endpoint accepts a small prompt in its URL.
                profileBudget.copy(contextTokens = minOf(profileBudget.contextTokens, 1536), outputTokens = minOf(profileBudget.outputTokens, 256))
            } else if (platform.compatibleType == ClientType.LITERT_LM) {
                profileBudget.copy(contextTokens = minOf(profileBudget.contextTokens, platform.maxTokens ?: 4096))
            } else {
                profileBudget
            }
            val turnKey = userMessages.lastOrNull()?.takeIf { it.id > 0 }?.let { "${it.chatId}:${it.id}" } ?: runId
            val unavailableConnections = mutableListOf<String>()
            val supportsTools = when (platform.compatibleType) {
                ClientType.FREE -> FreeAiProvider.requireFor(platform).supportsTools
                ClientType.LITERT_LM -> modelCatalogRepository.getCachedVisibleEntries().firstOrNull { it.id == platform.model }?.capabilities?.tools == true
                else -> true
            }
            val resolvedTools = if (platform.disableAllTools || !supportsTools) {
                emptyList()
            } else {
                val sharingEnabled = runCatching {
                    settingRepository.getFeatureSettings().sharedReadOnlyToolCalls
                }.getOrDefault(true)
                val shareScope = buildSharedToolScope(contextTurns).takeIf { sharingEnabled }
                agentToolResolver.resolve(platform.uid, chatToolConfig, userMessages.lastOrNull(), { target, task, cap -> delegateToProfile(target, task, cap, runId, turnKey) }, onConnectionError = { unavailableConnections += it }).map { resolved ->
                    resolved.copy(
                        tool = MeasuredAgentTool(
                            sharedToolCallBroker.wrap(
                                scopeId = shareScope,
                                toolIdentity = buildSharedToolIdentity(resolved),
                                shareableReadOnly = sharingEnabled && resolved.shareableReadOnly,
                                tool = resolved.tool
                            ),
                            onMeasured = { result ->
                                val metrics = result.measurement
                                if (diagnosticsEnabled && metrics != null && !result.sharedResult) {
                                    toolMetricsCollector?.onToolExecuted(
                                        toolId = resolved.modelToolName,
                                        tokensUsed = metrics.estimatedResultTokens ?: 0,
                                        executionTimeMs = metrics.durationMs ?: 0,
                                        success = !result.isError,
                                        errorType = if (result.isError) "tool_error" else null
                                    )
                                }
                            }
                        )
                    )
                }
            }
            unavailableConnections.forEach { emit(ApiState.Notice(it, persistent = true)) }
            val latestUser = userMessages.lastOrNull()
            val recalled = try {
                if (latestUser == null || platform.excludesMemory() || platform.disableAllTools || platform.disableLocalTools) {
                    FactRecall()
                } else {
                    factVault?.prepareTurn(latestUser.content, latestUser.chatId, latestUser.id, isLocal = platform.isPrivateDestination()) ?: FactRecall()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                emit(ApiState.Notice("Local memory is unavailable. Continuing without saved facts.", persistent = true))
                FactRecall()
            }
            if (recalled.facts.isNotEmpty()) emit(ApiState.MemoryRecalled(recalled.references))
            val exposedTools = dev.chungjungsoo.gptmobile.data.agent.tool.aggregateWebSearch(resolvedTools)
            val baseSystemPrompt = liveToolSystemPrompt(platform.systemPrompt, exposedTools.map { it.modelToolName }, compact = limits.contextTokens < 4096) +
                if (resolvedTools.isNotEmpty()) "\nBefore the first tool call and after every 10 completed tool calls, " + dev.chungjungsoo.gptmobile.data.agent.ToolProgressTracker.SUMMARY_INSTRUCTION else ""
            val memorySettings = factVault?.state?.value
            val canRecallDocuments = memorySettings?.enabled == true &&
                memorySettings.settings.recallEnabled &&
                (platform.isPrivateDestination() || memorySettings.settings.allowCloudRecall) &&
                !platform.disableAllTools &&
                !platform.disableLocalTools
            val documentContext = if (platform.excludesMemory() || !canRecallDocuments) "" else latestUser?.let { knowledge?.context(it.chatId, it.content) }.orEmpty()
            val requestPlatform = platform.copy(
                systemPrompt = recalled.prefix() + documentContext + baseSystemPrompt
            )
            val contextPlan = dev.chungjungsoo.gptmobile.data.context.ContextBudgetService.plan(contextTurns, requestPlatform.systemPrompt.orEmpty(), exposedTools.map { it.tool.definition }, limits)
            if (settingRepository.getDebugMode()) emit(ApiState.Notice(contextPlan.notice, persistent = true))
            val toolBudget = ToolExecutionBudget(customRunner.limits.copy(maxToolOutputBytes = contextPlan.toolResultBytes))
            val boundedTools = resolvedTools.filter { resolved -> contextPlan.tools.any { it.name == resolved.modelToolName || (it.name == "web_search" && resolved.isWebSearchEngine()) } }.map { resolved ->
                resolved.copy(
                    tool = toolBudget.bind(resolved.tool, onFinished = { callId, success ->
                        toolApprovals?.finish(runId, callId, success)
                    }) { callId, arguments ->
                        resolved.connectionUid?.let { uid ->
                            toolApprovals?.authorize(uid, runId, callId, resolved.realToolName, arguments) ?: true
                        } ?: true
                    }
                )
            }
            val effectiveTools = dev.chungjungsoo.gptmobile.data.agent.tool.aggregateWebSearch(boundedTools)
            val requestConstraints = RequestConstraints(maxOutputTokens = contextPlan.outputTokens)
            val session = when (platform.compatibleType) {
                ClientType.OPENAI -> openAIResponsesAdapter.openSession(contextPlan.turns, requestPlatform, requestConstraints)

                ClientType.GROQ, ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM, ClientType.LLAMA, ClientType.FREE ->
                    openAICompatibleAdapter.openSession(contextPlan.turns, requestPlatform, requestConstraints)

                ClientType.ANTHROPIC -> anthropicMessagesAdapter.openSession(contextPlan.turns, requestPlatform, requestConstraints)

                ClientType.GOOGLE -> geminiAdapter.openSession(contextPlan.turns, requestPlatform, requestConstraints)

                ClientType.LITERT_LM -> liteRtLmAdapter.openSession(
                    contextPlan.turns,
                    requestPlatform,
                    effectiveTools.map { it.tool },
                    requestConstraints
                )
            }
            val accountedSession = invocationLedger?.wrap(
                session, runId, turnKey, platform.compatibleType.name, platform.model,
                if (runId.startsWith("combined-synthesis:")) "synthesis" else "primary",
                dev.chungjungsoo.gptmobile.data.context.ContextBudgetService.estimate(requestPlatform.systemPrompt.orEmpty() + contextPlan.turns.joinToString { it.userMessage.content + it.assistantMessage?.content.orEmpty() }) + contextPlan.tools.sumOf { dev.chungjungsoo.gptmobile.data.context.ContextBudgetService.estimate(it.inputSchema.toString()) },
                contextPlan.outputTokens, budgetSettings.totalRunTokens
            ) ?: session
            val groundedSession = accountedSession.withDeviceLocation(
                clientType = platform.compatibleType,
                userPrompt = latestUser?.content,
                nativeLocationToolName = resolvedTools.firstOrNull {
                    it.connectionUid == null && it.realToolName == BuiltInAgentTool.DEVICE_LOCATION
                }?.modelToolName
            )
            val runnerTools = if (groundedSession.handlesToolsInternally) {
                emptyList()
            } else {
                effectiveTools.map { it.tool }
            }
            val trace = ToolTraceSession(runId, effectiveTools, toolEventRecorder)

            val progressTracker = dev.chungjungsoo.gptmobile.data.agent.ToolProgressTracker()
            val progressParser = dev.chungjungsoo.gptmobile.data.agent.PublicProgressParser()
            var gatewayTelemetrySeen = false
            var providerToolCalls = 0
            var providerUsefulToolCalls = 0
            var providerToolFailures = 0
            var providerThinkingSeen = false
            var providerTextSeen = false
            var accumulatedInputTokens = 0L
            var accumulatedOutputTokens = 0L
            var accumulatedTotalTokens = 0L
            var hasInputTokenUsage = false
            var hasOutputTokenUsage = false
            var hasTotalTokenUsage = false
            val providerRoute = platform.compatibleType.name.lowercase()

            fun providerProgress(
                event: String,
                stage: String,
                message: String,
                toolName: String? = null,
                status: String? = null,
                resultQuality: String? = null
            ): GatewayProgress = GatewayProgress(
                origin = "provider",
                event = event,
                stage = stage,
                message = message,
                timestamp = System.currentTimeMillis() / 1000.0,
                status = status,
                toolName = toolName,
                toolSource = "client",
                server = platform.name,
                route = providerRoute,
                resultQuality = resultQuality,
                totalToolCalls = providerToolCalls,
                usefulToolCalls = providerUsefulToolCalls,
                noProgress = providerToolFailures,
                selectedToolCount = resolvedTools.size
            )

            if (platform.compatibleType != ClientType.LITERT_LM) {
                emit(
                    ApiState.GatewayProgressChanged(
                        providerProgress(
                            event = "provider_started",
                            stage = "requesting",
                            message = "Connecting to ${platform.name.ifBlank { platform.compatibleType.name }}…"
                        )
                    )
                )
            }

            dev.chungjungsoo.gptmobile.data.agent.AgentRunner(customRunner.limits.copy(contextTokens = limits.contextTokens, initialContextTokens = contextPlan.promptTokens, finalResponseReserveTokens = contextPlan.outputTokens)).run(groundedSession, runnerTools).collect { runEvent ->
                when (runEvent) {
                    is AgentRunEvent.Provider -> when (val providerEvent = runEvent.event) {
                        is ProviderEvent.ThinkingDelta -> {
                            if (!gatewayTelemetrySeen && !providerThinkingSeen) {
                                providerThinkingSeen = true
                                emit(
                                    ApiState.GatewayProgressChanged(
                                        providerProgress(
                                            event = "reasoning_started",
                                            stage = "reasoning",
                                            message = "${platform.name.ifBlank { platform.compatibleType.name }} is reasoning…"
                                        )
                                    )
                                )
                            }
                            emit(ApiState.Thinking(providerEvent.text))
                        }

                        is ProviderEvent.TextDelta -> {
                            if (!gatewayTelemetrySeen && !providerTextSeen) {
                                providerTextSeen = true
                                emit(
                                    ApiState.GatewayProgressChanged(
                                        providerProgress(
                                            event = "response_started",
                                            stage = "generating",
                                            message = "Writing the response…"
                                        )
                                    )
                                )
                            }
                            progressParser.accept(providerEvent.text).forEach { (progress, text) ->
                                if (progress) emit(ApiState.ProgressCheckpoint(text, modelAuthored = true)) else emit(ApiState.Success(text))
                            }
                        }

                        is ProviderEvent.Failed -> emit(ApiState.Error(providerEvent.message))

                        is ProviderEvent.Notice -> emit(ApiState.Notice(providerEvent.message, providerEvent.persistent))

                        is ProviderEvent.PhaseChanged -> emit(ApiState.PhaseChanged(providerEvent.phase))

                        is ProviderEvent.Usage -> {
                            emit(ApiState.TokenUsage(providerEvent.inputTokens, providerEvent.outputTokens, providerEvent.totalTokens))
                            providerEvent.inputTokens?.let {
                                accumulatedInputTokens = if (providerEvent.cumulative) {
                                    maxOf(accumulatedInputTokens, it.toLong())
                                } else {
                                    accumulatedInputTokens + it
                                }
                                hasInputTokenUsage = true
                            }
                            providerEvent.outputTokens?.let {
                                accumulatedOutputTokens = if (providerEvent.cumulative) {
                                    maxOf(accumulatedOutputTokens, it.toLong())
                                } else {
                                    accumulatedOutputTokens + it
                                }
                                hasOutputTokenUsage = true
                            }
                            providerEvent.totalTokens?.let {
                                accumulatedTotalTokens = if (providerEvent.cumulative) {
                                    maxOf(accumulatedTotalTokens, it.toLong())
                                } else {
                                    accumulatedTotalTokens + it
                                }
                                hasTotalTokenUsage = true
                            }
                            agentRunDao.updateUsage(
                                runId = runId,
                                inputTokens = if (hasInputTokenUsage) accumulatedInputTokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else null,
                                outputTokens = if (hasOutputTokenUsage) accumulatedOutputTokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else null,
                                totalTokens = if (hasTotalTokenUsage) accumulatedTotalTokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else null
                            )
                        }

                        is ProviderEvent.GatewayMetadataCaptured -> {
                            providerEvent.metadata.jobId?.let { jobId ->
                                agentRunDao.bindGatewayJob(runId, jobId, platform.apiUrl)
                            }
                        }

                        is ProviderEvent.GatewayProgressUpdate -> {
                            gatewayTelemetrySeen = true
                            providerEvent.progress.sequence?.let { seq ->
                                agentRunDao.advanceGatewaySequence(runId, seq)
                            }
                            emit(ApiState.GatewayProgressChanged(providerEvent.progress))
                            val gatewayToolEvent = trace.gateway(providerEvent.progress)
                            if (gatewayToolEvent != null) {
                                emit(gatewayToolEvent)
                                val progress = providerEvent.progress
                                if (progress.event in setOf("tool_completed", "tool_failed", "tool_finished")) {
                                    progressTracker.complete(
                                        progress.toolCallId ?: "gateway-${gatewayToolEvent.toolSequence}",
                                        progress.toolName ?: "tool",
                                        progress.event == "tool_failed"
                                    )?.let {
                                        emit(ApiState.ProgressCheckpoint(it))
                                    }
                                }
                            }
                        }

                        is ProviderEvent.ToolCall -> {
                            val toolEvent = trace.start(providerEvent)
                            emit(
                                ApiState.ToolCall(
                                    toolEvent.sequence,
                                    ToolPayloadMetrics(
                                        argumentsCharacters = providerEvent.arguments.toString().length,
                                        argumentsBytes = providerEvent.arguments.toString().toByteArray(Charsets.UTF_8).size
                                    )
                                )
                            )
                            if (!gatewayTelemetrySeen) {
                                emit(
                                    ApiState.GatewayProgressChanged(
                                        providerProgress(
                                            event = "tool_formulating",
                                            stage = "tools",
                                            message = "Preparing ${providerEvent.name}…",
                                            toolName = providerEvent.name,
                                            status = "preparing"
                                        )
                                    )
                                )
                            }
                        }

                        is ProviderEvent.ToolResult -> Unit

                        ProviderEvent.Completed -> progressParser.accept("", flush = true).forEach { (progress, text) ->
                            if (progress) emit(ApiState.ProgressCheckpoint(text, modelAuthored = true)) else emit(ApiState.Success(text))
                        }
                    }

                    is AgentRunEvent.ToolStarted -> {
                        providerToolCalls += 1
                        if (!gatewayTelemetrySeen) {
                            emit(
                                ApiState.GatewayProgressChanged(
                                    providerProgress(
                                        event = "tool_started",
                                        stage = "executing_tools",
                                        message = "Running ${runEvent.call.name}…",
                                        toolName = runEvent.call.name,
                                        status = "running"
                                    )
                                )
                            )
                        }
                    }

                    is AgentRunEvent.ToolFinished -> {
                        trace.finish(runEvent.call, runEvent.result)?.let { emit(it) }
                        progressTracker.complete(runEvent.call.callId, runEvent.call.name, runEvent.result.isError)?.let {
                            emit(ApiState.ProgressCheckpoint(it))
                        }
                        if (!gatewayTelemetrySeen) {
                            if (runEvent.result.isError) {
                                providerToolFailures += 1
                            } else {
                                providerUsefulToolCalls += 1
                            }
                            emit(
                                ApiState.GatewayProgressChanged(
                                    providerProgress(
                                        event = if (runEvent.result.isError) "tool_failed" else "tool_completed",
                                        stage = "tools",
                                        message = if (runEvent.result.isError) {
                                            "${runEvent.call.name} failed"
                                        } else {
                                            "Completed ${runEvent.call.name}"
                                        },
                                        toolName = runEvent.call.name,
                                        status = if (runEvent.result.isError) "failed" else "completed",
                                        resultQuality = if (runEvent.result.isError) "error" else "useful"
                                    )
                                )
                            )
                        }
                    }

                    is AgentRunEvent.Notice -> emit(ApiState.Notice(runEvent.message, runEvent.persistent))
                }
            }
        } finally {
            withContext(NonCancellable) {
                toolEventRecorder.cancelRun(runId, currentEpochSeconds())
            }
        }
    }.catch { error ->
        val classified = ErrorClassification.classify(error)
        emit(ApiState.Error(classified.userMessage))
    }.onCompletion {
        emit(ApiState.Done)
    }

    private fun buildSharedToolScope(contextTurns: List<ConversationTurn>): String? {
        val latestUserMessage = contextTurns.lastOrNull()?.userMessage ?: return null
        if (latestUserMessage.chatId <= 0 || latestUserMessage.id <= 0) return null
        return "chat:${latestUserMessage.chatId}:turn:${latestUserMessage.id}"
    }

    private fun buildSharedToolIdentity(tool: ResolvedAgentTool): String = buildString {
        append(tool.connectionUid ?: "builtin")
        append(':')
        append(tool.realToolName)
    }

    private suspend fun buildContextTurns(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): List<ConversationTurn> {
        val policy = ProviderContextPolicy.forClientType(platform.compatibleType).copy(
            recentTurnWindow = Int.MAX_VALUE,
            maxHistoryCharBudget = Int.MAX_VALUE
        )
        val preparedUsers = userMessages.map { withDocumentContext(it, platform) }
        val preparedAssistants = assistantMessages.map { row -> row.map { withDocumentContext(it, platform) } }
        val contextTurns = contextBuilder.build(preparedUsers, preparedAssistants, platform, policy)
        if (!policy.preferProviderFileRefs || contextTurns.isEmpty()) {
            return contextTurns
        }

        return ensureProviderReferencesForTurns(contextTurns, platform, userMessages.associateBy { it.id })
    }

    private suspend fun withDocumentContext(message: MessageV2, platform: PlatformV2): MessageV2 {
        if (platform.compatibleType == ClientType.FREE) return message
        val nativePdf = platform.compatibleType in setOf(ClientType.OPENAI, ClientType.ANTHROPIC, ClientType.GOOGLE)
        val documents = message.attachments.filter { !FileUtils.isImage(it.mimeType) }
        if (documents.isEmpty()) return message
        val textOnlyDocuments = documents.filterNot { nativePdf && it.mimeType == "application/pdf" }
        val excerpts = withContext(Dispatchers.IO) {
            documents.mapNotNull { document ->
                val extracted = document.extractedText?.let { DocumentTextExtractor.Result(it, document.extractionNote) }
                    ?: DocumentTextExtractor.extract(context, java.io.File(document.filePathForDisplay), document.mimeType)
                if (!platform.excludesMemory() && !platform.disableAllTools && !platform.disableLocalTools && factVault?.state?.value?.enabled == true && factVault.state.value.settings.learningEnabled && knowledge != null && message.chatId > 0 && extracted.text.isNotBlank()) {
                    try {
                        knowledge.index(document.resolvedDisplayName, extracted.text.take(1_000_000), chatId = message.chatId, sourceKey = java.io.File(document.filePathForDisplay).name)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        dev.chungjungsoo.gptmobile.data.diagnostics.AppLogRecorder.record("Memory", "Attachment indexing failed for chat ${message.chatId}", "E")
                    }
                }
                if (document in textOnlyDocuments) "Attachment: ${document.resolvedDisplayName}\n${extracted.note.orEmpty()}\n${extracted.text.take(12000)}" else null
            }
        }
        return message.copy(
            content = message.content + if (excerpts.isEmpty()) "" else "\n\n" + excerpts.joinToString("\n\n"),
            attachments = message.attachments - textOnlyDocuments.toSet()
        )
    }

    private suspend fun ensureProviderReferencesForTurns(
        turns: List<ConversationTurn>,
        platform: PlatformV2,
        originals: Map<Int, MessageV2>
    ): List<ConversationTurn> {
        val preparedUserMessages = prepareMessagesForPlatform(turns.map { it.userMessage }, platform, originals)
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
        platform: PlatformV2,
        originals: Map<Int, MessageV2>
    ): List<MessageV2> {
        if (messages.none { it.attachments.isNotEmpty() }) {
            return messages
        }

        val updatedMessages = coroutineScope {
            messages.map { message ->
                async { attachmentUploadCoordinator.ensureMessageAttachmentsForPlatform(message, platform) }
            }.awaitAll()
        }

        val changedMessages = updatedMessages
            .zip(messages)
            .mapNotNull { (updated, original) -> updated.takeIf { it != original } }

        if (changedMessages.isNotEmpty()) {
            // Provider input may contain extracted document text or a compacted context.
            // Persist only attachment references onto the original conversation message.
            val persisted = changedMessages.map { updated ->
                val source = originals[updated.id] ?: updated
                source.copy(
                    attachments = source.attachments.map { attachment ->
                        updated.attachments.firstOrNull { it.localFilePath == attachment.localFilePath } ?: attachment
                    }
                )
            }
            messageV2Dao.editMessages(*persisted.toTypedArray())
        }

        return updatedMessages
    }

    override suspend fun fetchChatListV2(): List<ChatRoomV2> = chatRoomV2Dao.getChatRooms()

    override suspend fun fetchArchivedChatListV2(): List<ChatRoomV2> = chatRoomV2Dao.getArchivedChatRooms()

    override suspend fun setChatArchived(chatId: Int, isArchived: Boolean) {
        chatRoomV2Dao.updateArchived(chatId, isArchived)
    }

    override suspend fun setChatFavorite(chatId: Int, isFavorite: Boolean) {
        chatRoomV2Dao.updateFavorite(chatId, isFavorite)
    }

    override suspend fun updateDraft(chatId: Int, draftText: String?, timestamp: Long?) {
        chatRoomV2Dao.updateDraft(chatId, draftText, timestamp)
    }

    override suspend fun searchChatsV2(query: String): List<ChatRoomV2> {
        if (query.isBlank()) {
            return chatRoomV2Dao.getChatRooms()
        }

        val (titleMatches, messageMatchChatIds) = withContext(Dispatchers.IO) {
            coroutineScope {
                val titleJob = async { chatRoomV2Dao.searchChatRoomsByTitle(query) }
                val contentJob = async { messageV2Dao.searchMessagesByContent(query) }
                Pair(titleJob.await(), contentJob.await())
            }
        }

        val messageMatches = if (messageMatchChatIds.isEmpty()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                chatRoomV2Dao.getChatRoomsByIds(messageMatchChatIds)
            }
        }

        val titleMatchIds = HashSet<Int>(titleMatches.size)
        val combined = ArrayList<ChatRoomV2>(titleMatches.size + messageMatches.size)
        for (room in titleMatches) {
            titleMatchIds.add(room.id)
        }
        for (room in messageMatches) {
            if (titleMatchIds.add(room.id)) {
                combined.add(room)
            }
        }
        combined.sortByDescending { it.updatedAt }
        return combined
    }

    override suspend fun fetchMessagesV2(chatId: Int): List<MessageV2> = messageV2Dao.loadMessages(chatId)

    override fun observeMessageWindow(chatId: Int, turns: Int): Flow<List<MessageV2>> = messageV2Dao.observeWindow(chatId, (turns - 1).coerceAtLeast(0))
    override fun observeTurnCount(chatId: Int): Flow<Int> = messageV2Dao.observeTurnCount(chatId)

    override fun observeMessagesV2(chatId: Int): Flow<List<MessageV2>> = messageV2Dao.observeMessages(chatId)

    override fun observeFavoriteAssistantMessages(): Flow<List<MessageV2>> = messageV2Dao.observeFavoriteAssistantMessages()

    override fun searchFavoriteAssistantMessages(query: String): Flow<List<MessageV2>> =
        if (query.isBlank()) {
            messageV2Dao.observeFavoriteAssistantMessages()
        } else {
            messageV2Dao.searchFavoriteAssistantMessages(query)
        }

    override suspend fun setMessageFavorite(messageId: Int, isFavorite: Boolean) {
        messageV2Dao.updateFavorite(messageId, isFavorite)
    }

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

    override suspend fun bindGatewayJob(runId: String, jobId: String, baseUrl: String): Boolean =
        agentRunDao.bindGatewayJob(runId, jobId, baseUrl) == 1

    override suspend fun advanceGatewaySequence(runId: String, sequence: Int): Boolean =
        agentRunDao.advanceGatewaySequence(runId, sequence) == 1

    override suspend fun restoreGatewayAnswer(runId: String, jobId: String, content: String, completedAt: Long): Boolean =
        agentPersistenceDao.restoreGatewayAnswer(runId, jobId, content, completedAt)

    override suspend fun getRecoverableGatewayRuns(): List<AgentRun> =
        agentRunDao.getRecoverableGatewayRuns()

    override fun generateDefaultChatTitle(messages: List<MessageV2>): String? = messages.sortedBy { it.createdAt }.firstOrNull { it.platformType == null }?.content?.replace('\n', ' ')?.take(50)

    override suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String, isCustomized: Boolean) {
        val cleanedTitle = title.replace('\n', ' ').take(50)
        chatRoomV2Dao.updateTitle(
            chatId = chatRoom.id,
            title = cleanedTitle,
            isCustomized = isCustomized
        )
    }

    override suspend fun updateChatPlatforms(chatRoom: ChatRoomV2, platformUids: List<String>): ChatRoomV2 {
        val activeProfiles = platformUids.filter(String::isNotBlank).distinct()
        require(activeProfiles.isNotEmpty()) { "A conversation must keep at least one AI profile." }
        val stableProfileSlots = (chatRoom.enabledPlatform + activeProfiles).filter(String::isNotBlank).distinct()
        val updated = chatRoom.copy(
            enabledPlatform = stableProfileSlots,
            activePlatform = activeProfiles,
            updatedAt = System.currentTimeMillis() / 1000
        )
        if (chatRoom.id > 0) {
            chatRoomV2Dao.editChatRoom(updated)
        }
        return updated
    }

    override suspend fun generateAiTitle(
        userMessage: String,
        assistantMessage: String,
        platform: PlatformV2
    ): String? = titleSummarizer?.summarize(userMessage, assistantMessage, platform)

    override suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>, chatPlatformModels: Map<String, String>): ChatRoomV2 {
        if (chatRoom.id == 0) {
            val chatId = chatRoomV2Dao.addChatRoom(chatRoom)
            val updatedMessages = messages.map { it.copy(chatId = chatId.toInt()) }
            messageV2Dao.addMessages(*updatedMessages.toTypedArray())
            saveChatPlatformModels(
                chatId = chatId.toInt(),
                models = chatPlatformModels.filterKeys { it in chatRoom.enabledPlatform }
            )

            val savedChatRoom = chatRoom.copy(id = chatId.toInt())
            updateChatTitle(savedChatRoom, updatedMessages[0].content, isCustomized = false)

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
        return agentPersistenceDao.duplicateChatWithHistory(
            sourceChatId = chatRoom.id,
            title = duplicatedTitle,
            timestamp = System.currentTimeMillis() / 1000
        )
    }

    override suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>) {
        chatRoomV2Dao.deleteChatRooms(*chatRooms.toTypedArray())
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

private class ToolTraceSession(
    private val runId: String,
    tools: List<ResolvedAgentTool>,
    private val recorder: ToolEventRecorder
) {
    private val toolsByName = tools.associateBy { it.modelToolName }
    private val pendingEventIds = mutableMapOf<String, ArrayDeque<String>>()

    private val gatewayEventIds = mutableMapOf<String, ToolEvent>()
    private val sequences = mutableMapOf<String, Int>()

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
        sequences[event.eventId] = event.sequence
        return event
    }

    suspend fun gateway(progress: GatewayProgress): ApiState.ToolCall? {
        val isGatewaySource = progress.toolSource.equals("gateway", ignoreCase = true) ||
            progress.origin.equals("gateway", ignoreCase = true)
        if (!isGatewaySource) return null

        val callId = progress.toolCallId?.takeIf { it.isNotBlank() } ?: return null
        val eventName = progress.event?.lowercase().orEmpty()
        val toolName = progress.ui?.title?.takeIf { it.isNotBlank() }
            ?: progress.displayTitle?.takeIf { it.isNotBlank() }
            ?: progress.toolName?.takeIf { it.isNotBlank() }
            ?: "gateway_tool"
        val server = progress.server?.takeIf { it.isNotBlank() } ?: "gateway"

        return when (eventName) {
            "tool_started" -> {
                if (gatewayEventIds.containsKey(callId)) return null

                val event = recorder.startTool(
                    runId = runId,
                    sequence = sequence++,
                    callId = callId,
                    toolName = toolName,
                    modelToolName = toolName,
                    arguments = progress.toolArgs ?: JsonObject(emptyMap()),
                    connectionUid = "gateway:$server",
                    connectionName = "GATEWAY • $server",
                    startedAt = progress.timestampEpochSeconds()
                )

                gatewayEventIds[callId] = event
                ApiState.ToolCall(
                    event.sequence,
                    ToolPayloadMetrics(
                        argumentsCharacters = progress.toolArgs?.toString()?.length ?: 0,
                        argumentsBytes = progress.toolArgs?.toString()?.toByteArray(Charsets.UTF_8)?.size ?: 0,
                        timingSource = "gateway"
                    )
                )
            }

            "tool_completed", "tool_failed", "tool_blocked" -> {
                val startedEvent = gatewayEventIds.remove(callId) ?: return null
                val eventId = startedEvent.eventId
                val isError =
                    eventName == "tool_failed" ||
                        eventName == "tool_blocked" ||
                        progress.status.equals("failed", ignoreCase = true) ||
                        progress.status.equals("blocked", ignoreCase = true)

                val isEmptyResult = !isError &&
                    (
                        progress.resultQuality.equals("empty", ignoreCase = true) ||
                            progress.status.equals("no_useful_result", ignoreCase = true)
                        )

                val resultText = buildString {
                    if (isEmptyResult) {
                        append(progress.message ?: "Completed — No results")
                    } else {
                        append(progress.message ?: progress.status ?: "Gateway tool finished")
                    }
                    progress.resultQuality?.takeIf { it.isNotBlank() }?.let {
                        append("\nResult quality: ")
                        append(it)
                    }
                    progress.durationMs?.let {
                        append("\nGateway duration: ")
                        append(it)
                        append(" ms")
                    }
                }

                recorder.finishTool(
                    eventId = eventId,
                    result = AgentToolResult(
                        callId = callId,
                        content = ToolResultContent.Text(resultText),
                        isError = isError,
                        traceContent = if (isEmptyResult) ToolResultContent.Text("") else null
                    ),
                    completedAt = currentEpochSeconds(),
                    error = if (isError) resultText else null
                )

                // Gateway progress contains a summary, not the actual response payload.
                ApiState.ToolCall(
                    startedEvent.sequence,
                    ToolPayloadMetrics(
                        argumentsCharacters = startedEvent.arguments.length,
                        argumentsBytes = startedEvent.arguments.toByteArray(Charsets.UTF_8).size,
                        durationMs = progress.durationMs?.toLong()?.coerceAtLeast(0),
                        timingSource = "gateway"
                    )
                )
            }

            else -> null
        }
    }

    suspend fun finish(call: ProviderEvent.ToolCall, result: AgentToolResult): ApiState.ToolCall? {
        val eventId = pendingEventIds[call.callId]?.removeFirstOrNull() ?: return null
        recorder.finishTool(
            eventId = eventId,
            result = result,
            completedAt = currentEpochSeconds(),
            error = result.errorMessage()
        )
        val sequence = sequences.remove(eventId) ?: return null
        return ApiState.ToolCall(sequence, result.measurement ?: ToolPayloadMetrics.measure(call.arguments.toString(), result.content))
    }
}

private fun GatewayProgress.timestampEpochSeconds(): Long = (timestamp ?: (System.currentTimeMillis() / 1000.0)).toLong()

private fun AgentToolResult.errorMessage(): String? {
    if (!isError) return null
    return when (val value = content) {
        is ToolResultContent.Text -> value.text
        is ToolResultContent.Json -> value.value.toString()
        is ToolResultContent.ResourceLinks -> "Tool call failed."
    }
}

private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000
