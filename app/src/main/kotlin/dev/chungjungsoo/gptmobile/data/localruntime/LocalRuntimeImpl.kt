package dev.chungjungsoo.gptmobile.data.localruntime

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.ai.edge.litertlm.tool
import java.io.FileNotFoundException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Production [LocalRuntime] implementation wrapping LiteRT-LM. */
class LocalRuntimeImpl(
    private val context: Context,
    private val createEngine: (EngineConfig) -> Engine = { Engine(it) }
) : LocalRuntime {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    @Volatile private var activeRequestJob: Job? = null
    private var loadedAccelerator: String = LocalAccelerators.CPU
    private var loadedSpec: LocalEngineSpec? = null

    private val activityManager: ActivityManager? by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }

    override val deviceRamGb: Long by lazy {
        val memoryInfo = ActivityManager.MemoryInfo()
        if (activityManager != null) {
            activityManager?.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem / (1024L * 1024L * 1024L)
        } else {
            4L
        }
    }

    private val isHighRamDevice: Boolean by lazy {
        deviceRamGb >= 10L
    }

    override fun getHardwareState(): DeviceHardwareState =
        DeviceHardwareGovernor.inspectHardwareState(context)

    override fun getAdaptiveThrottlingPolicy(): AdaptiveThrottlingPolicy {
        val policy = DeviceHardwareGovernor.computeThrottlingPolicy(getHardwareState(), isHighRamDevice)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val lowMemory = activityManager != null &&
            (memoryInfo.lowMemory || memoryInfo.availMem < 500L * 1024L * 1024L)
        // Report the same limit to history compaction and engine initialization.
        return if (lowMemory) policy.copy(maxTokensClamp = minOf(policy.maxTokensClamp ?: 1024, 1024)) else policy
    }

    override fun loadedEngineSpec(): LocalEngineSpec? = loadedSpec

    override suspend fun loadEngine(spec: LocalEngineSpec) {
        withContext(Dispatchers.IO) {
            require(spec.modelPath.endsWith(".litertlm", ignoreCase = true)) {
                "This runtime requires a LiteRT-LM (.litertlm) model package"
            }
            // Pre-flight model integrity verification to avoid native hard crashes (SIGSEGV)
            when (val validation = LocalModelValidator.validate(spec.modelPath)) {
                is ModelValidationResult.Invalid -> {
                    Log.e(TAG, "Model validation failed for path '${spec.modelPath}': ${validation.reason} (${validation.details})")
                    when (validation.reason) {
                        ModelValidationResult.Invalid.Reason.NOT_FOUND -> {
                            throw FileNotFoundException("Model file not found at path: ${spec.modelPath}")
                        }
                        ModelValidationResult.Invalid.Reason.FILE_TOO_SMALL -> {
                            throw IllegalStateException("Model file is truncated or incomplete: ${validation.details}")
                        }
                        else -> {
                            throw IllegalStateException("Model file validation failed (${validation.reason}): ${validation.details}")
                        }
                    }
                }
                is ModelValidationResult.Valid -> {
                    Log.i(TAG, "Model file verified: ${validation.file.name} (${validation.sizeBytes} bytes)")
                }
            }

            require(spec.maxTokens > 0) { "Local context size must be positive" }
            // Fallback belongs to the router/adapter so it can honor settings and report
            // the real accelerator. Never silently mutate the caller's context budget.
            unloadEngine()
            val nextEngine = createEngine(
                EngineConfig(
                    modelPath = spec.modelPath,
                    backend = backendFor(spec.accelerator, spec.litertDispatchLibDir, spec.cpuThreads),
                    visionBackend = visionBackendFor(spec, spec.litertDispatchLibDir),
                    audioBackend = null,
                    maxNumTokens = spec.maxTokens,
                    maxNumImages = if (spec.isVisionEnabled) MAX_IMAGES_PER_MESSAGE else null,
                    cacheDir = if (spec.cacheEnabled) context.cacheDir.resolve("litert-lm").apply { mkdirs() }.absolutePath else ":nocache"
                )
            )
            try {
                nextEngine.initialize()
                // JNI initialization is blocking. Release the result if its caller was
                // cancelled while native code was working.
                coroutineContext.ensureActive()
                engine = nextEngine
                loadedAccelerator = LocalAccelerators.normalize(spec.accelerator)
                loadedSpec = spec
            } catch (error: Throwable) {
                if (nextEngine.isInitialized()) runCatching { nextEngine.close() }
                throw error
            }
        }
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun createConversation(config: LocalConversationConfig) {
        withContext(Dispatchers.IO) {
            val currentEngine = engine ?: error("LiteRT-LM engine is not loaded")
            closeConversation()
            yield() // Cooperative yield checkpoint before creating conversation and allocating KV-cache
            val toolProviders = config.tools.map { descriptor ->
                tool(BridgedOpenApiTool(descriptor, config.toolExecutor) { activeRequestJob })
            }
            val previousConstrainedDecoding = ExperimentalFlags.enableConversationConstrainedDecoding
            ExperimentalFlags.enableConversationConstrainedDecoding = config.isConstrainedDecodingEnabled
            val throttling = getAdaptiveThrottlingPolicy()
            val effectiveTopK = if (throttling.topKReductionRatio < 1.0f) {
                (config.sampler.topK * throttling.topKReductionRatio).toInt().coerceAtLeast(1)
            } else {
                config.sampler.topK
            }
            try {
                conversation = currentEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = config.systemPrompt?.takeIf { it.isNotBlank() }?.let { Contents.of(it) },
                        initialMessages = config.initialMessages.map { message ->
                            when (message.role) {
                                LocalHistoryRole.USER -> Message.user(contentsOf(message.text, message.images))
                                LocalHistoryRole.MODEL -> Message.model(contentsOf(message.text, message.images))
                            }
                        },
                        tools = toolProviders,
                        maxOutputToken = config.maxOutputTokens,
                        thinkingConfig = config.thinkingEnabled?.let { ThinkingConfig(enableThinking = it) },
                        samplerConfig = if (LocalAccelerators.shouldApplySampler(loadedAccelerator)) {
                            SamplerConfig(
                                topK = effectiveTopK,
                                topP = config.sampler.topP.toDouble(),
                                temperature = config.sampler.temperature.toDouble()
                            )
                        } else {
                            null
                        }
                    )
                )
            } catch (error: Throwable) {
                conversation = null
                throw error
            } finally {
                ExperimentalFlags.enableConversationConstrainedDecoding = previousConstrainedDecoding
            }
        }
    }

    override fun sendMessage(text: String, images: List<ByteArray>): Flow<LocalRuntimeEvent> = callbackFlow {
        val activeConversation = conversation
        if (activeConversation == null) {
            trySend(LocalRuntimeEvent.Error("LiteRT-LM conversation is not ready"))
            close()
            return@callbackFlow
        }

        val requestJob = coroutineContext[Job]
        activeRequestJob = requestJob

        // Notify downstream consumers that prompt prefill is underway
        trySend(LocalRuntimeEvent.PhaseChanged(LocalInferencePhase.PREFILL))

        val startTimeMs = SystemClock.elapsedRealtime()
        val firstTokenTimeMs = AtomicLong(0L)
        val chunkCount = AtomicInteger(0)
        val totalCharacters = AtomicInteger(0)
        val finished = AtomicBoolean(false)

        try {
            activeConversation.sendMessageAsync(
                contentsOf(text, images),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val now = SystemClock.elapsedRealtime()
                        if (firstTokenTimeMs.compareAndSet(0L, now)) {
                            trySend(LocalRuntimeEvent.PhaseChanged(LocalInferencePhase.GENERATING))
                        }

                        message.channels[THOUGHT_CHANNEL]?.takeIf { it.isNotEmpty() }?.let { thought ->
                            trySend(LocalRuntimeEvent.ThinkingDelta(thought))
                        }
                        val visibleText = message.visibleText()
                        if (visibleText.isNotEmpty()) {
                            chunkCount.incrementAndGet()
                            totalCharacters.addAndGet(visibleText.length)
                            trySend(LocalRuntimeEvent.TextDelta(visibleText))
                        }
                    }

                    override fun onDone() {
                        finished.set(true)
                        val finishTimeMs = SystemClock.elapsedRealtime()
                        val totalDuration = finishTimeMs - startTimeMs
                        val ttft = if (firstTokenTimeMs.get() > 0L) firstTokenTimeMs.get() - startTimeMs else totalDuration
                        val chars = totalCharacters.get()
                        // Rough approximation: ~4 characters per token for English/general text
                        val estimatedTokens = (chars / 4).coerceAtLeast(chunkCount.get())
                        val tps = if (totalDuration > 0) (estimatedTokens.toDouble() / (totalDuration.toDouble() / 1000.0)) else 0.0

                        val metrics = LocalInferenceMetrics(
                            timeToFirstTokenMs = ttft,
                            totalDurationMs = totalDuration,
                            totalChunks = chunkCount.get(),
                            totalCharacters = chars,
                            estimatedTokens = estimatedTokens,
                            tokensPerSecond = tps
                        )
                        trySend(LocalRuntimeEvent.Metrics(metrics))
                        trySend(LocalRuntimeEvent.Done)
                        close()
                    }

                    override fun onError(throwable: Throwable) {
                        finished.set(true)
                        if (throwable is CancellationException) {
                            close(throwable)
                            return
                        } else {
                            trySend(
                                LocalRuntimeEvent.Error(
                                    message = throwable.message ?: "Local inference failed",
                                    cause = throwable
                                )
                            )
                        }
                        close()
                    }
                }
            )

            awaitClose { }
        } finally {
            if (!finished.get()) runCatching { activeConversation.cancelProcess() }
            if (activeRequestJob === requestJob) activeRequestJob = null
        }
    }.buffer(Channel.UNLIMITED)

    override fun cancelActive() {
        activeRequestJob?.cancel()
        runCatching { conversation?.cancelProcess() }
    }

    override fun hasOpenConversation(): Boolean = conversation != null

    override suspend fun isEngineLoaded(spec: LocalEngineSpec): Boolean = engine != null && loadedSpec == spec

    override suspend fun closeConversation() {
        withContext(Dispatchers.IO) {
            runCatching { conversation?.close() }
            conversation = null
        }
    }

    override suspend fun unloadEngine() {
        withContext(Dispatchers.IO) {
            runCatching { conversation?.close() }
            conversation = null
            runCatching { engine?.close() }
            engine = null
            loadedSpec = null
            loadedAccelerator = LocalAccelerators.CPU
        }
    }

    private fun backendFor(accelerator: String, dispatchLibDir: String? = null, cpuThreads: Int? = null): Backend = when (LocalAccelerators.normalize(accelerator)) {
        LocalAccelerators.GPU -> Backend.GPU()
        LocalAccelerators.NPU -> {
            val probe = QnnEnvironment.getProbeStatus(context)
            check(probe.isReady) { probe.errorMessage ?: "NPU prerequisites are unavailable" }
            Backend.NPU(nativeLibraryDir = dispatchLibDir ?: probe.dispatchDir)
        }
        else -> Backend.CPU(threadCount = cpuThreads)
    }

    private fun visionBackendFor(spec: LocalEngineSpec, dispatchLibDir: String? = null): Backend? {
        if (!spec.isVisionEnabled) return null
        // Vision is a separate executor. Gemma 3n requires GPU vision even when
        // its language model uses CPU; NPU language execution does not imply NPU vision.
        return backendFor(spec.visionAccelerator, dispatchLibDir)
    }

    private fun contentsOf(text: String, images: List<ByteArray>): Contents {
        if (images.isEmpty()) return Contents.of(text)
        return Contents.of(
            buildList {
                images.forEach { image -> add(Content.ImageBytes(image)) }
                if (text.isNotBlank()) add(Content.Text(text))
            }
        )
    }

    private fun Message.visibleText(): String {
        val textList = contents.contents.filterIsInstance<Content.Text>()
        if (textList.isEmpty()) return ""
        if (textList.size == 1) return textList[0].text
        val totalLength = textList.sumOf { it.text.length }
        val sb = java.lang.StringBuilder(totalLength)
        for (item in textList) {
            sb.append(item.text)
        }
        return sb.toString()
    }

    private companion object {
        const val TAG = "LocalRuntimeImpl"
        const val THOUGHT_CHANNEL = "thought"
        const val MAX_IMAGES_PER_MESSAGE = 10
    }
}

internal class BridgedOpenApiTool(
    private val descriptor: LocalToolDescriptor,
    private val executor: LocalToolExecutor?,
    private val requestJob: () -> Job?
) : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = buildJsonObject {
        put("name", descriptor.name)
        put("description", descriptor.description)
        put("parameters", Json.parseToJsonElement(descriptor.inputSchemaJson))
    }.toString()

    override fun execute(paramsJsonString: String): String = runBlocking(
        checkNotNull(requestJob()) { "No active local inference request" }
    ) {
        val current = executor ?: error("LiteRT-LM tool executor is not registered")
        try {
            withTimeout(TOOL_EXECUTE_TIMEOUT_MS) {
                current.execute(descriptor.name, paramsJsonString)
            }
        } catch (error: TimeoutCancellationException) {
            "Tool '${descriptor.name}' failed: ${error.message ?: "timed out"}"
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            "Tool '${descriptor.name}' failed: ${error.message ?: "unknown error"}"
        }
    }

    private companion object {
        const val TOOL_EXECUTE_TIMEOUT_MS = 60_000L
    }
}
