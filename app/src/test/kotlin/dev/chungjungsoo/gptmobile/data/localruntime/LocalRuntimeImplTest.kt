package dev.chungjungsoo.gptmobile.data.localruntime

import android.app.Application
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.MessageCallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalRuntimeImplTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun spec(accelerator: String = "gpu", vision: Boolean = false): LocalEngineSpec {
        val file = temporaryFolder.newFile()
        val model = java.io.File(file.parent, "${file.name}.litertlm")
        RandomAccessFile(model, "rw").use {
            it.setLength(LocalModelValidator.DEFAULT_MIN_SIZE_BYTES)
            it.write("LITERTLM".toByteArray())
        }
        return LocalEngineSpec(model.path, accelerator, 2048, vision)
    }

    @Test
    fun sdkReceivesTheExactBudgetAndVisionBackend() = runTest {
        val engine = mockk<Engine>(relaxed = true)
        val configs = mutableListOf<EngineConfig>()
        val runtime = LocalRuntimeImpl(RuntimeEnvironment.getApplication()) {
            configs += it
            engine
        }
        val requested = spec(vision = true)
        runtime.loadEngine(requested)
        assertEquals(2048, configs.single().maxNumTokens)
        assertTrue(configs.single().backend is Backend.GPU)
        assertTrue(configs.single().visionBackend is Backend.GPU)
        assertEquals(10, configs.single().maxNumImages)
        assertTrue(runtime.isEngineLoaded(requested))
    }

    @Test
    fun cpuLanguageModelKeepsGpuVisionForMultimodalPackages() = runTest {
        val engine = mockk<Engine>(relaxed = true)
        val configs = mutableListOf<EngineConfig>()
        val runtime = LocalRuntimeImpl(RuntimeEnvironment.getApplication()) {
            configs += it
            engine
        }
        runtime.loadEngine(spec("cpu", vision = true))
        assertTrue(configs.single().backend is Backend.CPU)
        assertTrue(configs.single().visionBackend is Backend.GPU)
    }

    @Test
    fun cancelledRequestCannotExecuteAnotherTool() {
        val job = kotlinx.coroutines.Job().apply { cancel() }
        var executed = false
        val tool = BridgedOpenApiTool(
            LocalToolDescriptor("lookup", "Lookup", "{}"),
            LocalToolExecutor { _, _ ->
                executed = true
                "result"
            }
        ) { job }
        assertTrue(runCatching { tool.execute("{}") }.exceptionOrNull() is CancellationException)
        assertFalse(executed)
    }

    @Test
    fun gpuFailureDoesNotHideACpuRetry() = runTest {
        val engine = mockk<Engine>(relaxed = true)
        every { engine.initialize() } throws IllegalStateException("GPU unavailable")
        val configs = mutableListOf<EngineConfig>()
        val runtime = LocalRuntimeImpl(RuntimeEnvironment.getApplication()) {
            configs += it
            engine
        }
        val requested = spec()
        assertTrue(runCatching { runtime.loadEngine(requested) }.isFailure)
        assertEquals(1, configs.size)
        assertFalse(runtime.isEngineLoaded(requested))
    }

    @Test
    fun cancelledInitializationReleasesItsNativeEngine() = runTest {
        val engine = mockk<Engine>(relaxed = true)
        every { engine.initialize() } throws CancellationException("cancelled")
        every { engine.isInitialized() } returns true
        val runtime = LocalRuntimeImpl(RuntimeEnvironment.getApplication()) { engine }
        val requested = spec()
        assertTrue(runCatching { runtime.loadEngine(requested) }.exceptionOrNull() is CancellationException)
        verify(exactly = 1) { engine.close() }
        assertFalse(runtime.isEngineLoaded(requested))
    }

    @Test
    fun nativeCancellationIsNotReportedAsSuccessfulCompletion() = runTest {
        val engine = mockk<Engine>(relaxed = true)
        val conversation = mockk<Conversation>(relaxed = true)
        every { engine.createConversation(any()) } returns conversation
        every { conversation.sendMessageAsync(any<Contents>(), any<MessageCallback>()) } answers {
            secondArg<MessageCallback>().onError(CancellationException("cancelled by native runtime"))
        }
        val runtime = LocalRuntimeImpl(RuntimeEnvironment.getApplication()) { engine }
        runtime.loadEngine(spec("cpu"))
        runtime.createConversation(LocalConversationConfig(LocalSamplerConfig(40, .95f, .8f), null, emptyList()))
        val events = mutableListOf<LocalRuntimeEvent>()
        assertTrue(runCatching { runtime.sendMessage("hello").toList(events) }.exceptionOrNull() is CancellationException)
        assertFalse(events.any { it is LocalRuntimeEvent.Done })
    }

    @Test
    fun synchronousSendFailureCancelsNativeWorkAndNextRequestCanComplete() = runTest {
        val engine = mockk<Engine>(relaxed = true)
        val conversation = mockk<Conversation>(relaxed = true)
        every { engine.createConversation(any()) } returns conversation
        every { conversation.sendMessageAsync(any<Contents>(), any<MessageCallback>()) } throws IllegalStateException("send failed")
        val runtime = LocalRuntimeImpl(RuntimeEnvironment.getApplication()) { engine }
        runtime.loadEngine(spec("cpu"))
        runtime.createConversation(LocalConversationConfig(LocalSamplerConfig(40, .95f, .8f), null, emptyList()))
        assertTrue(runCatching { runtime.sendMessage("first").toList() }.isFailure)
        verify(exactly = 1) { conversation.cancelProcess() }
        every { conversation.sendMessageAsync(any<Contents>(), any<MessageCallback>()) } answers { secondArg<MessageCallback>().onDone() }
        assertTrue(runtime.sendMessage("second").toList().any { it is LocalRuntimeEvent.Done })
        verify(exactly = 1) { conversation.cancelProcess() }
    }

    @Test
    fun conversationCreationFailureClearsTheClosedConversation() = runTest {
        val engine = mockk<Engine>(relaxed = true)
        val conversation = mockk<Conversation>(relaxed = true)
        every { engine.createConversation(any()) } returns conversation andThenThrows IllegalStateException("KV cache")
        val runtime = LocalRuntimeImpl(RuntimeEnvironment.getApplication()) { engine }
        runtime.loadEngine(spec("cpu"))
        val config = LocalConversationConfig(LocalSamplerConfig(40, .95f, .8f), null, emptyList())
        runtime.createConversation(config)
        assertTrue(runtime.hasOpenConversation())
        assertTrue(runCatching { runtime.createConversation(config) }.isFailure)
        assertFalse(runtime.hasOpenConversation())
        verify(exactly = 1) { conversation.close() }
    }

    @OptIn(ExperimentalApi::class)
    @Test
    fun nativeCountersAreCollectedOnlyWhenEnabled() = runTest {
        val engine = mockk<Engine>(relaxed = true)
        val conversation = mockk<Conversation>(relaxed = true)
        every { engine.createConversation(any()) } returns conversation
        every { conversation.sendMessageAsync(any<Contents>(), any<MessageCallback>()) } answers { secondArg<MessageCallback>().onDone() }
        every { conversation.getBenchmarkInfo() } returns BenchmarkInfo(0.0, .1, 20, 10, 100.0, 25.0)
        val runtime = LocalRuntimeImpl(RuntimeEnvironment.getApplication()) { engine }
        val requested = spec("cpu")
        val config = LocalConversationConfig(LocalSamplerConfig(40, .95f, .8f), null, emptyList())
        runtime.loadEngine(requested)
        runtime.createConversation(config)
        assertNull(runtime.sendMessage("first").toList().filterIsInstance<LocalRuntimeEvent.Metrics>().single().metrics.native)
        verify(exactly = 0) { conversation.getBenchmarkInfo() }
        runtime.loadEngine(requested.copy(nativeMetricsEnabled = true))
        runtime.createConversation(config)
        val native = runtime.sendMessage("second").toList().filterIsInstance<LocalRuntimeEvent.Metrics>().single().metrics.native
        assertEquals(10, native?.decodeTokens)
        assertEquals(25.0, native?.decodeTokensPerSecond)
        verify(exactly = 1) { conversation.getBenchmarkInfo() }
    }

    @OptIn(ExperimentalApi::class)
    @Test
    fun counterFailureDoesNotDiscardCompletedResponse() = runTest {
        val engine = mockk<Engine>(relaxed = true)
        val conversation = mockk<Conversation>(relaxed = true)
        every { engine.createConversation(any()) } returns conversation
        every { conversation.sendMessageAsync(any<Contents>(), any<MessageCallback>()) } answers { secondArg<MessageCallback>().onDone() }
        every { conversation.getBenchmarkInfo() } throws IllegalStateException("Counters unavailable")
        val runtime = LocalRuntimeImpl(RuntimeEnvironment.getApplication()) { engine }
        runtime.loadEngine(spec("cpu").copy(nativeMetricsEnabled = true))
        runtime.createConversation(LocalConversationConfig(LocalSamplerConfig(40, .95f, .8f), null, emptyList()))
        val events = runtime.sendMessage("hello").toList()
        assertTrue(events.last() is LocalRuntimeEvent.Done)
        assertFalse(events.any { it is LocalRuntimeEvent.Error })
        assertNull(events.filterIsInstance<LocalRuntimeEvent.Metrics>().single().metrics.native)
    }
}
