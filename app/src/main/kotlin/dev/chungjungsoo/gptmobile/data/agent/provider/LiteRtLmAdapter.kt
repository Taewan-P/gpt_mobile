package dev.chungjungsoo.gptmobile.data.agent.provider

import android.util.Log
import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.localmodel.ResolvedModelDownload
import dev.chungjungsoo.gptmobile.data.localruntime.ConversationFingerprint
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.localruntime.LocalConversationConfig
import dev.chungjungsoo.gptmobile.data.localruntime.LocalEngineSpec
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryMessage
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryRole
import dev.chungjungsoo.gptmobile.data.localruntime.LocalModelExecutionTarget
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntimeEvent
import dev.chungjungsoo.gptmobile.data.localruntime.LocalSamplerConfig
import dev.chungjungsoo.gptmobile.data.localruntime.LocalToolDescriptor
import dev.chungjungsoo.gptmobile.data.localruntime.LocalToolExecutor
import dev.chungjungsoo.gptmobile.data.localruntime.conversationFingerprint
import dev.chungjungsoo.gptmobile.data.localruntime.localModelExecutionTargets
import dev.chungjungsoo.gptmobile.data.localruntime.matches
import dev.chungjungsoo.gptmobile.data.localruntime.resolvedEngineMaxTokens
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
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
    private val loadImageBytes: suspend (ChatAttachment) -> ByteArray? = { null },
    private val requestModelReplacement: (CatalogEntry, ResolvedModelDownload, String) -> Unit = { _, _, _ -> },
    private val modelReplacementRequiredError: String = DEFAULT_MODEL_REPLACEMENT_REQUIRED,
    private val npuUnavailableGpuNotice: String = DEFAULT_NPU_UNAVAILABLE_GPU
) {
    private data class OpenConversation(
        val profileUid: String,
        val engineSpec: LocalEngineSpec,
        val sampler: LocalSamplerConfig,
        val systemPrompt: String?,
        val toolsKey: String,
        val consumed: ConversationFingerprint
    )

    private var openConversation: OpenConversation? = null
    private var isConversationDirty = false
    private val failedTargets = ConcurrentHashMap.newKeySet<Pair<String, LocalModelExecutionTarget>>()
    private var exclusiveToolsByName: Map<String, AgentTool> = emptyMap()
    private var exclusiveToolEventSink: (suspend (ProviderEvent) -> Unit)? = null

    suspend fun resolvedAccelerator(platform: PlatformV2): String {
        val entry = modelCatalogRepository?.getCachedVisibleEntries()?.firstOrNull { it.id == platform.model }
            ?: return if (platform.accelerator == "auto") LocalAccelerators.GPU else LocalAccelerators.normalize(platform.accelerator)
        return localModelExecutionTargets(entry, platform.accelerator, deviceSocModel, localRuntime.isNpuAvailable())
            .firstOrNull { (platform.model to it) !in failedTargets }
            ?.accelerator ?: LocalAccelerators.CPU
    }

    suspend fun openSession(
        turns: List<ConversationTurn>,
        platform: PlatformV2,
        tools: List<AgentTool> = emptyList()
    ): AgentProviderSession {
        val boundTools = tools
        return object : AgentProviderSession {
            override val handlesToolsInternally: Boolean = true

            override fun streamRound(
                tools: List<AgentToolDefinition>,
                exchanges: List<AgentToolExchange>
            ): Flow<ProviderEvent> = channelFlow {
                val catalogEntry = modelCatalogRepository
                    ?.getCachedVisibleEntries()
                    ?.firstOrNull { entry -> entry.id == platform.model }
                val visionCapable = catalogEntry?.capabilities?.vision == true
                val toolsCapable = catalogEntry?.capabilities?.tools == true
                val registeredTools = if (toolsCapable) boundTools else emptyList()
                val descriptors = registeredTools.map { tool -> tool.definition.toLocalDescriptor() }
                val toolsKey = toolsFingerprint(descriptors)
                val runToolsByName = registeredTools.associateBy { it.definition.name }
                val runToolEventSink: suspend (ProviderEvent) -> Unit = { event -> send(event) }
                val latestAttachments = turns.lastOrNull()?.userMessage?.attachments.orEmpty()
                attachmentNotices(visionCapable, turns, latestAttachments).forEach { notice ->
                    send(notice)
                }

                val modelPath = localModelRepository.resolveDownloadedPath(platform.model)
                if (modelPath == null) {
                    send(ProviderEvent.Failed(modelNotDownloadedError))
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
                val history = historyMessages(
                    priorTurns = turns.dropLast(1),
                    visionCapable = visionCapable,
                    includeImageBytes = false
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
                    localRuntime.runExclusiveFlow(
                        onContended = { send(ProviderEvent.Notice(waitingForEngineNotice)) }
                    ) {
                        exclusiveToolsByName = runToolsByName
                        exclusiveToolEventSink = runToolEventSink
                        val loadedSpec = loadCompatibleEngine(platform, catalogEntry, visionCapable) { event -> send(event) }
                        val snapshot = openConversation
                        val canReuse = !isConversationDirty &&
                            hasOpenConversation() &&
                            snapshot != null &&
                            snapshot.profileUid == platform.uid &&
                            snapshot.engineSpec == loadedSpec &&
                            snapshot.sampler == sampler &&
                            snapshot.systemPrompt == platform.systemPrompt &&
                            snapshot.toolsKey == toolsKey &&
                            snapshot.consumed == incomingPrior
                        if (!canReuse) {
                            if (hasOpenConversation()) {
                                closeConversation()
                            }
                            val seedHistory = if (visionCapable) {
                                historyMessages(
                                    priorTurns = turns.dropLast(1),
                                    visionCapable = true,
                                    includeImageBytes = true
                                )
                            } else {
                                history
                            }
                            createConversation(
                                LocalConversationConfig(
                                    sampler = sampler,
                                    systemPrompt = platform.systemPrompt,
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
                                sampler = sampler,
                                systemPrompt = platform.systemPrompt,
                                toolsKey = toolsKey,
                                consumed = incomingPrior
                            )
                        }
                        isConversationDirty = true
                        sendMessage(latestUserText, latestImages)
                    }.collect { event ->
                        when (event) {
                            is LocalRuntimeEvent.TextDelta -> {
                                assistantReply.append(event.text)
                                send(ProviderEvent.TextDelta(event.text))
                            }

                            is LocalRuntimeEvent.ThinkingDelta -> send(ProviderEvent.ThinkingDelta(event.text))

                            is LocalRuntimeEvent.Error -> {
                                failed = true
                                isConversationDirty = true
                                send(ProviderEvent.Failed(event.message))
                            }

                            LocalRuntimeEvent.Done -> Unit
                        }
                    }
                    if (!failed) {
                        send(ProviderEvent.Completed)
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
                            isConversationDirty = false
                        }
                    }
                } catch (error: CancellationException) {
                    localRuntime.cancelActive()
                    isConversationDirty = true
                    throw error
                } catch (error: LocalEngineLoadException) {
                    isConversationDirty = true
                    send(ProviderEvent.Failed(error.message ?: engineLoadFailedError))
                } catch (error: Exception) {
                    isConversationDirty = true
                    send(ProviderEvent.Failed(error.message ?: "Local inference failed"))
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

    private suspend fun LocalRuntime.loadCompatibleEngine(
        platform: PlatformV2,
        entry: CatalogEntry?,
        visionCapable: Boolean,
        send: suspend (ProviderEvent) -> Unit
    ): LocalEngineSpec {
        // Re-read under the generation lock: a confirmed replacement may have completed while queued.
        val path = localModelRepository.resolveDownloadedPath(platform.model)
            ?: throw LocalEngineLoadException(modelNotDownloadedError)
        val retained = localModelRepository.getById(platform.model)
        val hasArtifactMetadata = entry != null && entry.downloadUrl.isNotBlank()
        val targets = if (hasArtifactMetadata) {
            localModelExecutionTargets(checkNotNull(entry), platform.accelerator, deviceSocModel, isNpuAvailable())
        } else {
            // Older cached catalogs may lack artifact metadata. CPU/GPU remain usable; NPU requires a known variant.
            val order = when (platform.accelerator?.lowercase()) {
                "auto", LocalAccelerators.GPU -> listOf(LocalAccelerators.GPU, LocalAccelerators.CPU)
                LocalAccelerators.NPU -> emptyList()
                else -> listOf(LocalAccelerators.CPU)
            }
            order.map { LocalModelExecutionTarget(it, ResolvedModelDownload("", "", "", 0)) }
        }
        var replacement: LocalModelExecutionTarget? = null
        var failedAccelerator: String? = null
        for (target in targets) {
            val spec = LocalEngineSpec(
                modelPath = path,
                accelerator = target.accelerator,
                maxTokens = resolvedEngineMaxTokens(
                    platform.maxTokens ?: DEFAULT_MAX_TOKENS,
                    target.accelerator,
                    entry,
                    deviceSocModel
                ),
                isVisionEnabled = visionCapable
            )
            val failureKey = (if (hasArtifactMetadata) platform.model else path) to target
            if (failureKey in failedTargets) continue
            if (hasArtifactMetadata && (retained == null || !target.download.matches(retained))) {
                if (replacement == null) replacement = target
                if (failedAccelerator == null) break
                continue
            }
            try {
                if (!isEngineLoaded(spec) && loadingModelNotice.isNotBlank()) {
                    send(ProviderEvent.Notice(loadingModelNotice))
                }
                loadEngine(spec)
                if (failedAccelerator != null) {
                    val notice = when {
                        target.accelerator == LocalAccelerators.CPU && failedAccelerator == LocalAccelerators.GPU -> gpuUnavailableNotice
                        target.accelerator == LocalAccelerators.CPU -> npuUnavailableNotice
                        else -> npuUnavailableGpuNotice
                    }
                    if (notice.isNotBlank()) send(ProviderEvent.Notice(notice, persistent = true))
                }
                return spec
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logEngineFailure(spec, error)
                if (target.accelerator != LocalAccelerators.CPU) failedTargets += failureKey
                failedAccelerator = target.accelerator
            } catch (error: LinkageError) {
                logEngineFailure(spec, error)
                if (target.accelerator != LocalAccelerators.CPU) failedTargets += failureKey
                failedAccelerator = target.accelerator
            }
        }
        if (entry != null && replacement != null) {
            requestModelReplacement(entry, replacement.download, replacement.accelerator)
            throw LocalEngineLoadException(modelReplacementRequiredError)
        }
        throw LocalEngineLoadException(engineLoadFailedError)
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

    companion object {
        const val DEFAULT_IGNORED_ATTACHMENTS = "The local platform ignored attachments"
        const val DEFAULT_MODEL_NOT_DOWNLOADED =
            "This Local Model is not downloaded. Download it from Settings → Local Models."
        const val DEFAULT_WAITING_FOR_ENGINE = "Waiting for the local engine"
        const val DEFAULT_TOO_MANY_IMAGES = "The local platform accepted only the first 10 images"
        const val DEFAULT_LOADING_MODEL = "Loading local model…"
        const val DEFAULT_GPU_UNAVAILABLE = "GPU unavailable on this device — running on CPU"
        const val DEFAULT_NPU_UNAVAILABLE_GPU = "NPU unavailable on this device — running on GPU"
        const val DEFAULT_NPU_UNAVAILABLE = "NPU unavailable on this device — running on CPU"
        const val DEFAULT_MODEL_REPLACEMENT_REQUIRED = "A compatible model download is needed. Confirm the replacement, then retry your message after downloading."
        const val DEFAULT_ENGINE_LOAD_FAILED = "Couldn't load the local model on this device"
        const val MAX_IMAGES_PER_MESSAGE = 10
        private const val TAG = "LiteRtLmAdapter"
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 0.95f
        const val DEFAULT_TEMPERATURE = 1.0f
        const val DEFAULT_MAX_TOKENS = 1024
    }
}

private class LocalEngineLoadException(message: String) : Exception(message)
