package dev.chungjungsoo.gptmobile.data.localruntime

import android.content.Context
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
import com.google.ai.edge.litertlm.tool
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Production [LocalRuntime] implementation wrapping LiteRT-LM. */
class LocalRuntimeImpl(
    private val context: Context
) : LocalRuntime {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedAccelerator: String = LocalAccelerators.CPU
    private var loadedSpec: LocalEngineSpec? = null

    override suspend fun loadEngine(spec: LocalEngineSpec) {
        withContext(Dispatchers.IO) {
            loadedAccelerator = spec.accelerator
            val engineConfig = EngineConfig(
                modelPath = spec.modelPath,
                backend = backendFor(spec.accelerator),
                visionBackend = visionBackendFor(spec),
                audioBackend = null,
                maxNumTokens = spec.maxTokens,
                maxNumImages = if (spec.isVisionEnabled) MAX_IMAGES_PER_MESSAGE else null
            )
            val nextEngine = Engine(engineConfig)
            nextEngine.initialize()
            engine = nextEngine
            loadedSpec = spec
        }
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun createConversation(config: LocalConversationConfig) {
        withContext(Dispatchers.IO) {
            val currentEngine = engine ?: error("LiteRT-LM engine is not loaded")
            conversation?.close()
            val toolProviders = config.tools.map { descriptor ->
                tool(BridgedOpenApiTool(descriptor, config.toolExecutor))
            }
            val previousConstrainedDecoding = ExperimentalFlags.enableConversationConstrainedDecoding
            ExperimentalFlags.enableConversationConstrainedDecoding = config.isConstrainedDecodingEnabled
            try {
                conversation = currentEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = config.systemPrompt?.takeIf { it.isNotBlank() }?.let { Contents.of(it) },
                        // LiteRT-LM 0.11.0 Message.user/model(Contents) accept Content.ImageBytes,
                        // so rebuilds re-seed prior image turns instead of dropping them to text-only.
                        initialMessages = config.initialMessages.map { message ->
                            when (message.role) {
                                LocalHistoryRole.USER -> Message.user(contentsOf(message.text, message.images))
                                LocalHistoryRole.MODEL -> Message.model(contentsOf(message.text, message.images))
                            }
                        },
                        tools = toolProviders,
                        samplerConfig = if (LocalAccelerators.shouldApplySampler(loadedAccelerator)) {
                            SamplerConfig(
                                topK = config.sampler.topK,
                                topP = config.sampler.topP.toDouble(),
                                temperature = config.sampler.temperature.toDouble()
                            )
                        } else {
                            null
                        }
                    )
                )
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

        activeConversation.sendMessageAsync(
            contentsOf(text, images),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    message.channels[THOUGHT_CHANNEL]?.takeIf { it.isNotEmpty() }?.let { thought ->
                        trySend(LocalRuntimeEvent.ThinkingDelta(thought))
                    }
                    val visibleText = message.visibleText()
                    if (visibleText.isNotEmpty()) {
                        trySend(LocalRuntimeEvent.TextDelta(visibleText))
                    }
                }

                override fun onDone() {
                    trySend(LocalRuntimeEvent.Done)
                    close()
                }

                override fun onError(throwable: Throwable) {
                    if (throwable is CancellationException || throwable is kotlinx.coroutines.CancellationException) {
                        trySend(LocalRuntimeEvent.Done)
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

        awaitClose {
            runCatching { activeConversation.cancelProcess() }
        }
    }.buffer(Channel.UNLIMITED)

    override fun cancelActive() {
        runCatching { conversation?.cancelProcess() }
    }

    override fun hasOpenConversation(): Boolean = conversation != null

    override fun isEngineLoaded(spec: LocalEngineSpec): Boolean = engine != null && loadedSpec == spec

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

    private fun backendFor(accelerator: String): Backend = when (LocalAccelerators.normalize(accelerator)) {
        LocalAccelerators.GPU -> Backend.GPU()
        LocalAccelerators.NPU -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        else -> Backend.CPU()
    }

    private fun visionBackendFor(spec: LocalEngineSpec): Backend? {
        if (!spec.isVisionEnabled) return null
        return when (LocalAccelerators.normalize(spec.accelerator)) {
            LocalAccelerators.CPU -> Backend.CPU()
            LocalAccelerators.NPU -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
            else -> Backend.GPU()
        }
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

    private fun Message.visibleText(): String = contents.contents
        .filterIsInstance<Content.Text>()
        .joinToString("") { it.text }

    private companion object {
        const val THOUGHT_CHANNEL = "thought"
        const val MAX_IMAGES_PER_MESSAGE = 10
    }
}

private class BridgedOpenApiTool(
    private val descriptor: LocalToolDescriptor,
    private val executor: LocalToolExecutor?
) : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = buildJsonObject {
        put("name", descriptor.name)
        put("description", descriptor.description)
        put("parameters", Json.parseToJsonElement(descriptor.inputSchemaJson))
    }.toString()

    override fun execute(paramsJsonString: String): String = runBlocking {
        val current = executor ?: error("LiteRT-LM tool executor is not registered")
        try {
            withTimeout(TOOL_EXECUTE_TIMEOUT_MS) {
                current.execute(descriptor.name, paramsJsonString)
            }
        } catch (error: TimeoutCancellationException) {
            "Tool '${descriptor.name}' failed: ${error.message ?: "timed out"}"
        } catch (error: Exception) {
            "Tool '${descriptor.name}' failed: ${error.message ?: "unknown error"}"
        }
    }

    private companion object {
        const val TOOL_EXECUTE_TIMEOUT_MS = 60_000L
    }
}
