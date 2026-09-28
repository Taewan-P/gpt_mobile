package dev.chungjungsoo.gptmobile.data.localruntime

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRuntimeQnnImplTest {
    private val spec = LocalEngineSpec("model.litertlm", "npu", 1280)
    private val ready = QnnEnvironment.QnnProbeStatus(
        true, "SM8750", "/native", "/dispatch", "/dispatch", "/dispatch", emptyList(), true,
        "/dispatch/libQnnHtpV79Skel.so", true
    )

    @Test
    fun transformedDispatchSpecStillReusesTheWarmEngine() = runTest {
        val native = FakeLocalRuntime()
        val qnn = LocalRuntimeQnnImpl(mockk<Context>(), native) { ready }
        val holder = LocalEngineHolder(qnn) { 1_000L }
        holder.loadEngine(spec)
        holder.loadEngine(spec)
        assertEquals(1, native.loadEngineCalls.size)
        assertEquals("/dispatch", native.loadEngineCalls.single().litertDispatchLibDir)
        assertTrue(holder.isEngineLoaded(spec))
        holder.unloadEngine()
        assertFalse(holder.isEngineLoaded(spec))
    }

    @Test
    fun missingPrerequisitesDoNotAttemptNativeInitialization() = runTest {
        val native = FakeLocalRuntime()
        val qnn = LocalRuntimeQnnImpl(mockk<Context>(), native) { ready.copy(isReady = false, errorMessage = "missing HTP") }
        assertTrue(runCatching { qnn.loadEngine(spec) }.isFailure)
        assertTrue(native.loadEngineCalls.isEmpty())
    }

    @Test
    fun explicitGpuIsNeverSilentlyChangedToNpu() = runTest {
        val native = FakeLocalRuntime()
        val qnn = LocalRuntimeQnnImpl(mockk<Context>(), native) { ready }
        assertTrue(runCatching { qnn.loadEngine(spec.copy(accelerator = "gpu")) }.isFailure)
        assertTrue(native.loadEngineCalls.isEmpty())
    }

    @Test
    fun conversationAndVisionPayloadAreForwardedToTheInitializedEngine() = runTest {
        val native = FakeLocalRuntime()
        val qnn = LocalRuntimeQnnImpl(mockk<Context>(), native) { ready }
        qnn.loadEngine(spec)
        val config = LocalConversationConfig(LocalSamplerConfig(40, .95f, .8f), "system", emptyList())
        qnn.createConversation(config)
        val image = byteArrayOf(1, 2, 3)
        qnn.sendMessage("describe", listOf(image)).toList()
        assertEquals(config, native.createConversationCalls.single())
        assertTrue(image.contentEquals(native.sendMessageImages.single().single()))
        qnn.closeConversation()
        assertFalse(qnn.hasOpenConversation())
    }

    @Test
    fun libraryRequirementsFollowTheDeviceHtpVersion() {
        assertEquals(
            listOf(
                "libLiteRtDispatch_Qualcomm.so",
                "libQnnHtp.so",
                "libQnnHtpPrepare.so",
                "libQnnSystem.so",
                "libQnnHtpV79Stub.so",
                "libQnnHtpV79Skel.so"
            ),
            QualcommSocSupport.requiredLibraries("SM8750")
        )
        assertTrue("libQnnHtpV73Skel.so" in QualcommSocSupport.requiredLibraries("SM8550"))
        assertTrue("libQnnHtpV75Stub.so" in QualcommSocSupport.requiredLibraries("sm8650"))
        assertTrue("libQnnHtpV81Skel.so" in QualcommSocSupport.requiredLibraries("SM8850"))
        assertTrue(QualcommSocSupport.requiredLibraries("Tensor G5").isEmpty())
        assertTrue(QualcommSocSupport.requiredLibraries("MT6991").isEmpty())
    }
}
