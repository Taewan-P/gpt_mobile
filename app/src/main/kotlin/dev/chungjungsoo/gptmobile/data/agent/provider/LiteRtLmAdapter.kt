package dev.chungjungsoo.gptmobile.data.agent.provider

import android.util.Log
import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.context.LocalContextPlanner
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelCompatibility
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelPackages
import dev.chungjungsoo.gptmobile.data.localmodel.resolveLocalModelSelection
import dev.chungjungsoo.gptmobile.data.localruntime.ConversationFingerprint
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.localruntime.LocalConversationConfig
import dev.chungjungsoo.gptmobile.data.localruntime.LocalEngineSpec
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryMessage
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryRole
import dev.chungjungsoo.gptmobile.data.localruntime.LocalInferenceMetrics
import dev.chungjungsoo.gptmobile.data.localruntime.LocalNpuPackageException
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntimeEvent
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntimeFallbackDisabledException
import dev.chungjungsoo.gptmobile.data.localruntime.LocalSamplerConfig
import dev.chungjungsoo.gptmobile.data.localruntime.LocalToolDescriptor
import dev.chungjungsoo.gptmobile.data.localruntime.LocalToolExecutor
import dev.chungjungsoo.gptmobile.data.localruntime.conversationFingerprint
import dev.chungjungsoo.gptmobile.data.localruntime.resolvedEngineMaxTokens
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class LiteRtLmAdapter(
    private val localRuntime: LocalRuntime,
    private val localModelRepository: LocalModelRepository,
    private val ignoredAttachmentsNotice: String,
    private val modelNotDownloadedError: String,
    private val waitingForEngineNotice: String = DEFAULT_WAITING_FOR_ENGINE,
    private val tooManyImagesNotice: String = DEFAULT_TOO_MANY_IMAGES,
    private val loadingModelNotice: String = DEFAULT_LOADING_MODEL,
    private val gpuUnavailableNotice: String = DEFAULT_GPU_UNAVAILABLE,
    private val npuUnavailableNotice: String = DEFAULT_NPU_UNAVAILABLE,
    private val engineLoadFailedError: String = DEFAULT_ENGINE_LOAD_FAILED,
    private val modelCatalogRepository: ModelCatalogRepository? = null,
    private val deviceSocModel: String = "",
    private val loadImageBytes: suspend (ChatAttachment) -> ByteArray? = { null }
) {
    private data class OpenConversation(
        val profileUid: String,
        val engineSpec: LocalEngineSpec,
        val sampler: LocalSamplerConfig,
        val systemPrompt: String?,
        val toolsKey: String,
        val maxOutputTokens: Int?,
        val thinkingEnabled: Boolean,
        val consumed: ConversationFingerprint
    )

    private var openConversation: OpenConversation? = null
    private var isConversationDirty = false
    private val cpuFallbackByModelAccelerator = mutableSetOf<Pair<String, String>>()
    private var exclusiveToolsByName: Map<String, AgentTool> = emptyMap()
    private var exclusiveToolEventSink: (suspend (ProviderEvent) -> Unit)? = null

    suspend fun openSession(
        turns: List<ConversationTurn>,
        platform: PlatformV2,
        tools: List<AgentTool> = emptyList(),
        constraints: RequestConstraints = RequestConstraints(),
        fallbackSystemPrompt: String? = null
    ): AgentProviderSession {
        require(constraints.allowTools || tools.isEmpty()) { "Tools are disabled for this request." }
        val boundTools = tools
        return object : AgentProviderSession {
            override val handlesToolsInternally: Boolean = true

            override fun streamRound(
                tools: List<AgentToolDefinition>,
                exchanges: List<AgentToolExchange>
            ): Flow<ProviderEvent> = channelFlow {
                val entries = modelCatalogRepository?.getCachedVisibleEntries().orEmpty()
                val wantsNpu = LocalAccelerators.normalize(platform.accelerator) == LocalAccelerators.NPU
                val selection = try {
                    localModelRepository.resolveLocalModelSelection(platform.model, platform.accelerator)
                } catch (error: IllegalStateException) {
                    send(ProviderEvent.Failed(error.message ?: modelNotDownloadedError))
                    return@channelFlow
                }
                val resolvedModelId = selection.modelId
                val installedRecord = selection.record
                val modelPath = selection.path
                val catalogEntry = entries.firstOrNull { it.id == resolvedModelId }
                    ?.let { entry -> installedRecord?.let { LocalModelPackages.forInstalledFile(entry, it.fileName) } ?: entry }
                LocalModelCompatibility.installedPackageIssue(catalogEntry?.downloadUrl.orEmpty(), modelPath)?.let { reason ->
                    send(ProviderEvent.Failed(reason))
                    return@channelFlow
                }
                val visionCapable = catalogEntry?.capabilities?.vision == true
                // Tool execution is provided by LiteRT-LM's OpenAPI bridge. The
                // catalogue flag is a recommendation, not a runtime capability;
                // using it as a gate hid all MCP tools from imported, legacy and
                // most bundled local models.
                val toolsCapable = true
                val latestAttachments = turns.lastOrNull()?.userMessage?.attachments.orEmpty()
                attachmentNotices(visionCapable, turns, latestAttachments).forEach { notice ->
                    send(notice)
                }

                if (wantsNpu &&
                    (catalogEntry == null || !LocalAccelerators.isNpuEligible(catalogEntry.supportedAccelerators, catalogEntry.socToModelFiles, deviceSocModel))
                ) {
                    send(ProviderEvent.Failed("This package has no verified QNN build for this phone. Select a matching NPU package from the marketplace, or use its GPU edition."))
                    return@channelFlow
                }

                val latestUserText = turns.lastOrNull()?.userMessage?.effectiveContent().orEmpty()
                val latestImageIds = visionImageIds(latestAttachments, visionCapable)
                val latestImages = if (visionCapable) {
                    latestAttachments
                        .filter { attachment -> attachment.isImageAttachment() }
                        .take(MAX_IMAGES_PER_MESSAGE)
                        .mapNotNull { attachment -> loadImageBytes(attachment) }
                } else {
                    emptyList()
                }

                val resolvedMaxTokens = resolvedEngineMaxTokens(
                    requestedMaxTokens = Int.MAX_VALUE,
                    accelerator = platform.accelerator.orEmpty(),
                    entry = catalogEntry,
                    deviceSocModel = deviceSocModel,
                    deviceRamGb = localRuntime.deviceRamGb
                )

                val outputLimit = constraints.outputLimit(platform.maxTokens)
                val thinkingEnabled = platform.reasoning && constraints.allowReasoning
                val throttlingPolicy = localRuntime.getAdaptiveThrottlingPolicy()
                val effectiveContextTokens = if (throttlingPolicy.maxTokensClamp != null) {
                    minOf(resolvedMaxTokens, throttlingPolicy.maxTokensClamp)
                } else {
                    resolvedMaxTokens
                }

                var availableTools = if (toolsCapable) boundTools else emptyList()
                var conversationSystemPrompt = platform.systemPrompt
                var usedCompactFallback = false
                val plan = try {
                    LocalContextPlanner.plan(
                        priorTurns = turns.dropLast(1),
                        currentUserPrompt = latestUserText,
                        systemPrompt = platform.systemPrompt,
                        tools = availableTools.map { it.definition },
                        contextTokens = effectiveContextTokens,
                        outputLimit = outputLimit,
                        imageCount = latestImages.size,
                        historyImageCount = { turn -> visionImageIds(turn.userMessage.attachments, visionCapable).size }
                    )
                } catch (error: IllegalArgumentException) {
                    val compactPrompt = fallbackSystemPrompt?.takeIf { it != platform.systemPrompt }
                    if (compactPrompt == null) {
                        send(ProviderEvent.Failed(error.message ?: "Local context is too small for this request."))
                        return@channelFlow
                    }
                    availableTools = emptyList()
                    conversationSystemPrompt = compactPrompt
                    try {
                        LocalContextPlanner.plan(
                            priorTurns = turns.dropLast(1),
                            currentUserPrompt = latestUserText,
                            systemPrompt = compactPrompt,
                            tools = emptyList(),
                            contextTokens = effectiveContextTokens,
                            outputLimit = outputLimit,
                            imageCount = latestImages.size,
                            historyImageCount = { turn -> visionImageIds(turn.userMessage.attachments, visionCapable).size }
                        ).also { usedCompactFallback = true }
                    } catch (_: IllegalArgumentException) {
                        send(ProviderEvent.Failed(error.message ?: "Local context is too small for this request."))
                        return@channelFlow
                    }
                }
                if (usedCompactFallback) {
                    send(
                        ProviderEvent.Notice(
                            "Local context: optional memory, document context, and tools were omitted so this request fits the model's $effectiveContextTokens-token limit.",
                            persistent = true
                        )
                    )
                }
                if (plan.omittedTurns > 0 || plan.omittedTools > 0) {
                    send(
                        ProviderEvent.Notice(
                            "Local context: ${plan.omittedTurns} earlier turns and ${plan.omittedTools} tools omitted to fit this model. " +
                                "Select fewer tools or a larger-context model if needed.",
                            persistent = true
                        )
                    )
                }
                val compactedPriorTurns = plan.priorTurns
                val selectedNames = plan.tools.map { it.name }.toSet()
                val toolBudget = dev.chungjungsoo.gptmobile.data.agent.ToolExecutionBudget(
                    dev.chungjungsoo.gptmobile.data.agent.AgentRunLimits(
                        maxToolCalls = Int.MAX_VALUE,
                        maxToolOutputBytes = plan.toolResultBytes,
                        toolTimeoutMillis = Long.MAX_VALUE
                    ),
                    failureMessage = { error -> error.message ?: "Local tool execution failed." }
                )
                val registeredTools = availableTools.filter { it.definition.name in selectedNames }.map { toolBudget.bind(it) }
                val descriptors = registeredTools.map { it.definition.toLocalDescriptor() }
                val toolsKey = toolsFingerprint(descriptors)
                val runToolsByName = registeredTools.associateBy { it.definition.name }
                var nativeToolsUsed = false
                val runToolEventSink: suspend (ProviderEvent) -> Unit = { event ->
                    if (event is ProviderEvent.ToolCall) nativeToolsUsed = true
                    send(event)
                }

                val history = historyMessages(
                    priorTurns = compactedPriorTurns,
                    visionCapable = visionCapable,
                    includeImageBytes = false
                )
                val spec = rememberedEngineSpec(
                    modelPath = modelPath,
                    accelerator = LocalAccelerators.normalize(platform.accelerator),
                    maxTokens = effectiveContextTokens,
                    isVisionEnabled = visionCapable,
                    visionAccelerator = if (
                        LocalAccelerators.normalize(platform.accelerator) == LocalAccelerators.NPU &&
                        catalogEntry?.capabilities?.npuVision == true
                    ) {
                        LocalAccelerators.NPU
                    } else {
                        LocalAccelerators.GPU
                    }
                )
                val sampler = LocalSamplerConfig(
                    topK = platform.topK ?: DEFAULT_TOP_K,
                    topP = platform.topP ?: DEFAULT_TOP_P,
                    temperature = platform.temperature ?: DEFAULT_TEMPERATURE
                )
                val incomingPrior = conversationFingerprint(history)

                try {
                    var failed = false
                    val assistantReply = StringBuilder()
                    var latestMetrics: LocalInferenceMetrics? = null
                    localRuntime.runExclusiveFlow(
                        onContended = { send(ProviderEvent.Notice(waitingForEngineNotice)) }
                    ) {
                        flow<Unit> {
                            try {
                                exclusiveToolsByName = runToolsByName
                                exclusiveToolEventSink = runToolEventSink
                                if (!isEngineLoaded(spec) && loadingModelNotice.isNotBlank()) {
                                    send(ProviderEvent.Notice(loadingModelNotice))
                                }
                                val loadedSpec = loadEngineOrFallback(spec) { event -> send(event) }
                                val snapshot = openConversation
                                val canReuse = !isConversationDirty &&
                                    hasOpenConversation() &&
                                    snapshot != null &&
                                    snapshot.profileUid == platform.uid &&
                                    snapshot.engineSpec == loadedSpec &&
                                    snapshot.maxOutputTokens == outputLimit &&
                                    snapshot.thinkingEnabled == thinkingEnabled &&
                                    snapshot.sampler == sampler &&
                                    snapshot.systemPrompt == conversationSystemPrompt &&
                                    snapshot.toolsKey == toolsKey &&
                                    snapshot.consumed == incomingPrior
                                suspend fun rebuildConversation() {
                                    if (hasOpenConversation()) {
                                        closeConversation()
                                    }
                                    yield() // Cooperative yield before starting heavy conversation allocation
                                    val seedHistory = if (visionCapable) {
                                        historyMessages(
                                            priorTurns = compactedPriorTurns,
                                            visionCapable = true,
                                            includeImageBytes = true
                                        )
                                    } else {
                                        history
                                    }
                                    createConversation(
                                        LocalConversationConfig(
                                            sampler = sampler,
                                            maxOutputTokens = outputLimit,
                                            thinkingEnabled = thinkingEnabled,
                                            systemPrompt = conversationSystemPrompt,
                                            initialMessages = seedHistory,
                                            tools = descriptors,
                                            isConstrainedDecodingEnabled = descriptors.isNotEmpty(),
                                            toolExecutor = if (descriptors.isNotEmpty()) {
                                                LocalToolExecutor { name, argumentsJson ->
                                                    executeBoundTool(
                                                        name,
                                                        argumentsJson,
                                                        exclusiveToolsByName,
                                                        exclusiveToolEventSink
                                                    )
                                                }
                                            } else {
                                                null
                                            }
                                        )
                                    )
                                    openConversation = OpenConversation(
                                        profileUid = platform.uid,
                                        engineSpec = loadedSpec,
                                        maxOutputTokens = outputLimit,
                                        thinkingEnabled = thinkingEnabled,
                                        sampler = sampler,
                                        systemPrompt = conversationSystemPrompt,
                                        toolsKey = toolsKey,
                                        consumed = incomingPrior
                                    )
                                }
                                if (!canReuse) rebuildConversation()
                                isConversationDirty = true
                                var templateRetried = false
                                var generatedOutput = false
                                while (true) {
                                    var templateFailure: String? = null
                                    yield()
                                    sendMessage(latestUserText, latestImages)
                                        .catch { error ->
                                            if (error is CancellationException) throw error
                                            emit(LocalRuntimeEvent.Error(error.message ?: "Local inference failed", error))
                                        }
                                        .collect { event ->
                                            when (event) {
                                                is LocalRuntimeEvent.PhaseChanged -> {
                                                    send(ProviderEvent.PhaseChanged(event.phase))
                                                }

                                                is LocalRuntimeEvent.TextDelta -> {
                                                    if (event.text.isNotEmpty()) generatedOutput = true
                                                    assistantReply.append(event.text)
                                                    send(ProviderEvent.TextDelta(event.text))
                                                }

                                                is LocalRuntimeEvent.ThinkingDelta -> {
                                                    if (event.text.isNotEmpty()) generatedOutput = true
                                                    send(ProviderEvent.ThinkingDelta(event.text))
                                                }

                                                is LocalRuntimeEvent.Metrics -> {
                                                    latestMetrics = event.metrics
                                                }

                                                is LocalRuntimeEvent.Error -> {
                                                    if (isLocalTemplateMismatch(event.message)) {
                                                        templateFailure = event.message
                                                    } else {
                                                        failed = true
                                                        send(ProviderEvent.Failed(event.message))
                                                    }
                                                }

                                                LocalRuntimeEvent.Done -> Unit
                                            }
                                        }
                                    if (templateFailure == null) break
                                    if (!canReuse || templateRetried || generatedOutput || nativeToolsUsed || failed) {
                                        failed = true
                                        send(ProviderEvent.Failed(LOCAL_TEMPLATE_ERROR))
                                        break
                                    }
                                    // Only retry before output/tool execution. Keep the warm engine,
                                    // rebuild from canonical visible history, and try the new turn once.
                                    templateRetried = true
                                    latestMetrics = null
                                    send(ProviderEvent.Notice("Rebuilding local conversation state…"))
                                    rebuildConversation()
                                }
                                if (!failed) {
                                    latestMetrics?.let { metrics ->
                                        send(ProviderEvent.LocalMetrics(metrics))
                                        val telemetryNotice = formatTelemetryNotice(metrics, localRuntime)
                                        if (telemetryNotice.isNotBlank()) {
                                            send(ProviderEvent.Notice(telemetryNotice))
                                        }
                                    }
                                    val snapshot = openConversation
                                    if (snapshot != null) {
                                        openConversation = snapshot.copy(
                                            consumed = snapshot.consumed.extend(
                                                listOfNotNull(
                                                    LocalHistoryMessage(
                                                        role = LocalHistoryRole.USER,
                                                        text = latestUserText,
                                                        imageIds = latestImageIds
                                                    ),
                                                    assistantReply.toString().takeIf { it.isNotBlank() }?.let { content ->
                                                        LocalHistoryMessage(LocalHistoryRole.MODEL, content)
                                                    }
                                                )
                                            )
                                        )
                                        isConversationDirty = nativeToolsUsed
                                    }
                                }
                                if (!failed) send(ProviderEvent.Completed)
                            } catch (error: CancellationException) {
                                cancelActive()
                                isConversationDirty = true
                                throw error
                            } catch (error: Throwable) {
                                isConversationDirty = true
                                throw error
                            } finally {
                                // Cancellation signals native generation asynchronously.
                                // Close the dirty session while still holding the engine
                                // lock, before a queued request can create its replacement.
                                if (isConversationDirty) {
                                    openConversation = null
                                    withContext(NonCancellable) {
                                        runCatching { closeConversation() }
                                    }
                                }
                                exclusiveToolsByName = emptyMap()
                                exclusiveToolEventSink = null
                            }
                        }
                    }.collect { }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: LocalEngineLoadException) {
                    // Native diagnostics are retained in Logcat; keep the conversation error readable.
                    send(ProviderEvent.Failed(engineLoadFailedError))
                } catch (error: Exception) {
                    val message = error.message ?: "Local inference failed"
                    send(ProviderEvent.Failed(if (isLocalTemplateMismatch(message)) LOCAL_TEMPLATE_ERROR else message))
                }
            }
        }
    }

    private suspend fun executeBoundTool(
        toolName: String,
        argumentsJson: String,
        toolsByName: Map<String, AgentTool>,
        eventSink: (suspend (ProviderEvent) -> Unit)?
    ): String {
        val arguments = parseArguments(argumentsJson)
        val call = ProviderEvent.ToolCall(UUID.randomUUID().toString(), toolName, arguments)
        eventSink?.invoke(call)
        val result = try {
            val tool = toolsByName[toolName]
            if (tool == null) {
                AgentToolResult(
                    callId = call.callId,
                    content = ToolResultContent.Text("Tool '$toolName' is not assigned to this profile."),
                    isError = true
                )
            } else {
                tool.execute(call.callId, arguments)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AgentToolResult(
                callId = call.callId,
                content = ToolResultContent.Text(error.message ?: "Tool '$toolName' failed."),
                isError = true
            )
        }
        eventSink?.invoke(ProviderEvent.ToolResult(call, result))
        return result.engineText()
    }

    private fun attachmentNotices(
        visionCapable: Boolean,
        turns: List<ConversationTurn>,
        latestAttachments: List<ChatAttachment>
    ): List<ProviderEvent> = buildList {
        if (visionCapable) {
            val images = latestAttachments.filter { attachment -> attachment.isImageAttachment() }
            val nonImages = latestAttachments.filter { attachment -> !attachment.isImageAttachment() }
            if (images.size > MAX_IMAGES_PER_MESSAGE) {
                add(ProviderEvent.Notice(tooManyImagesNotice, persistent = true))
            }
            if (nonImages.isNotEmpty()) {
                add(ProviderEvent.Notice(ignoredAttachmentsNotice, persistent = true))
            }
            return@buildList
        }
        val hasAttachments = turns.any { turn ->
            turn.userMessage.attachments.isNotEmpty() ||
                turn.assistantMessage?.attachments?.isNotEmpty() == true
        }
        if (hasAttachments) {
            add(ProviderEvent.Notice(ignoredAttachmentsNotice, persistent = true))
        }
    }

    private fun rememberedEngineSpec(
        modelPath: String,
        accelerator: String,
        maxTokens: Int,
        isVisionEnabled: Boolean,
        visionAccelerator: String
    ): LocalEngineSpec {
        val requested = LocalEngineSpec(
            modelPath = modelPath,
            accelerator = accelerator,
            maxTokens = maxTokens,
            isVisionEnabled = isVisionEnabled,
            visionAccelerator = visionAccelerator
        )
        return if (cpuFallbackKey(modelPath, accelerator) in cpuFallbackByModelAccelerator) {
            requested.copy(accelerator = LocalAccelerators.CPU)
        } else {
            requested
        }
    }

    private suspend fun LocalRuntime.loadEngineOrFallback(
        requested: LocalEngineSpec,
        send: suspend (ProviderEvent) -> Unit
    ): LocalEngineSpec {
        if (isEngineLoaded(requested)) return loadedEngineSpec() ?: requested
        try {
            loadEngine(requested)
            val active = loadedEngineSpec() ?: requested
            if (active.accelerator != requested.accelerator) {
                send(ProviderEvent.Notice("${requested.accelerator.uppercase()} unavailable — running on ${active.accelerator.uppercase()}", persistent = true))
            }
            return active
        } catch (error: CancellationException) {
            throw error
        } catch (error: LocalRuntimeFallbackDisabledException) {
            throw error
        } catch (error: LocalNpuPackageException) {
            logEngineFailure(requested, error)
            throw error
        } catch (error: Throwable) {
            if (error !is Exception && error !is LinkageError) throw error
            logEngineFailure(requested, error)
            if (LocalModelPackages.isNpuFile(requested.modelPath)) throw LocalNpuPackageException(error)
            if (handlesEngineFallback) throw LocalEngineLoadException(engineLoadFailedError, error)
            if (LocalAccelerators.normalize(requested.accelerator) == LocalAccelerators.CPU) {
                throw LocalEngineLoadException(engineLoadFailedError, error)
            }
            val cpuSpec = requested.copy(accelerator = LocalAccelerators.CPU)
            try {
                loadEngine(cpuSpec)
            } catch (cpuCancelled: CancellationException) {
                throw cpuCancelled
            } catch (error: LocalRuntimeFallbackDisabledException) {
                throw error
            } catch (cpuError: Throwable) {
                if (cpuError !is Exception && cpuError !is LinkageError) throw cpuError
                logEngineFailure(cpuSpec, cpuError)
                throw LocalEngineLoadException(engineLoadFailedError, cpuError)
            }
            cpuFallbackByModelAccelerator += cpuFallbackKey(requested.modelPath, requested.accelerator)
            val notice = acceleratorUnavailableNotice(requested.accelerator)
            if (notice.isNotBlank()) {
                send(ProviderEvent.Notice(notice, persistent = true))
            }
            return loadedEngineSpec() ?: cpuSpec
        }
    }

    private fun cpuFallbackKey(modelPath: String, accelerator: String): Pair<String, String> = modelPath to LocalAccelerators.normalize(accelerator)

    private fun acceleratorUnavailableNotice(accelerator: String): String = if (LocalAccelerators.normalize(accelerator) == LocalAccelerators.NPU) {
        npuUnavailableNotice
    } else {
        gpuUnavailableNotice
    }

    private fun logEngineFailure(spec: LocalEngineSpec, error: Throwable) {
        runCatching {
            Log.e(
                TAG,
                "Failed to load local engine path=${spec.modelPath} accelerator=${spec.accelerator}",
                error
            )
        }
    }

    private suspend fun historyMessages(
        priorTurns: List<ConversationTurn>,
        visionCapable: Boolean,
        includeImageBytes: Boolean
    ): List<LocalHistoryMessage> = priorTurns.flatMap { turn ->
        val attachments = turn.userMessage.attachments
        val imageIds = visionImageIds(attachments, visionCapable)
        val images = if (includeImageBytes && visionCapable) {
            attachments
                .filter { attachment -> attachment.isImageAttachment() }
                .take(MAX_IMAGES_PER_MESSAGE)
                .mapNotNull { attachment -> loadImageBytes(attachment) }
        } else {
            emptyList()
        }
        buildList {
            add(
                LocalHistoryMessage(
                    role = LocalHistoryRole.USER,
                    text = turn.userMessage.effectiveContent(),
                    imageIds = imageIds,
                    images = images
                )
            )
            turn.assistantMessage?.effectiveContent()?.takeIf { it.isNotBlank() }?.let { content ->
                add(LocalHistoryMessage(LocalHistoryRole.MODEL, content))
            }
        }
    }

    private fun visionImageIds(
        attachments: List<ChatAttachment>,
        visionCapable: Boolean
    ): List<String> {
        if (!visionCapable) return emptyList()
        return attachments
            .filter { attachment -> attachment.isImageAttachment() }
            .take(MAX_IMAGES_PER_MESSAGE)
            .map { attachment -> attachment.identity() }
    }

    private fun ChatAttachment.isImageAttachment(): Boolean = mimeType.startsWith("image/")

    private fun ChatAttachment.identity(): String = "${preparedFilePath.ifBlank { localFilePath }}|$mimeType|$sizeBytes"

    private fun AgentToolDefinition.toLocalDescriptor() = LocalToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = inputSchema.toString()
    )

    private fun toolsFingerprint(descriptors: List<LocalToolDescriptor>): String = descriptors.sortedBy { it.name }.joinToString("\u001e") { descriptor ->
        "${descriptor.name}\u001f${descriptor.description}\u001f${descriptor.inputSchemaJson}"
    }

    private fun parseArguments(argumentsJson: String): JsonObject {
        val element = runCatching { Json.parseToJsonElement(argumentsJson) }.getOrNull()
        return element as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun AgentToolResult.engineText(): String = when (val value = content) {
        is ToolResultContent.Text -> value.text
        is ToolResultContent.Json -> value.value.toString()
        is ToolResultContent.ResourceLinks -> value.links.joinToString("\n") { link -> link.uri }
    }

    internal fun formatTelemetryNotice(
        metrics: LocalInferenceMetrics,
        runtime: LocalRuntime
    ): String {
        if (metrics.totalDurationMs <= 0L && metrics.totalChunks <= 0) return ""
        val native = metrics.native?.takeIf { it.isValid }
        val baseNotice = if (native != null) {
            val speed = String.format(Locale.US, "%.1f", native.decodeTokensPerSecond)
            val prefill = String.format(Locale.US, "%.1f", native.prefillTokensPerSecond)
            "Local: $speed decode tok/s · $prefill prefill tok/s · ${native.decodeTokens} tokens (last segment) · First callback ${metrics.timeToFirstTokenMs}ms"
        } else {
            val speed = String.format(Locale.US, "%.1f", metrics.tokensPerSecond)
            "Local: ~$speed tok/s end-to-end · First callback ${metrics.timeToFirstTokenMs}ms · ~${metrics.estimatedTokens} tokens"
        }

        val hwState = runtime.getHardwareState()
        val throttleSuffix = when {
            hwState.isThrottlingRequired -> " · ⚡ Throttled"
            hwState.isModeratePressure -> " · 🌡️ Warm"
            else -> ""
        }
        return baseNotice + throttleSuffix
    }

    companion object {
        const val DEFAULT_IGNORED_ATTACHMENTS = "The local platform ignored attachments"
        const val DEFAULT_MODEL_NOT_DOWNLOADED =
            "This Local Model is not downloaded. Download it from Settings → Local Models."
        const val DEFAULT_WAITING_FOR_ENGINE = "Waiting for the local engine"
        const val DEFAULT_TOO_MANY_IMAGES = "The local platform accepted only the first 10 images"
        const val DEFAULT_LOADING_MODEL = "Loading local model…"
        const val DEFAULT_GPU_UNAVAILABLE = "GPU unavailable on this device — running on CPU"
        const val DEFAULT_NPU_UNAVAILABLE = "NPU unavailable on this device — running on CPU"
        const val DEFAULT_ENGINE_LOAD_FAILED = "Couldn't load the local model on this device"
        const val MAX_IMAGES_PER_MESSAGE = 10
        private const val TAG = "LiteRtLmAdapter"
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 0.95f
        const val DEFAULT_TEMPERATURE = 1.0f
    }
}

private class LocalEngineLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

// Native mismatch errors include old/new rendered prompts; never expose those in diagnostics.
internal fun isLocalTemplateMismatch(message: String): Boolean =
    message.contains("does not start with the previous rendered", ignoreCase = true) ||
        message.contains("new rendered string is shorter than the previous", ignoreCase = true)

private const val LOCAL_TEMPLATE_ERROR = "The local model could not reuse its conversation state. Start a new conversation or select another model."
