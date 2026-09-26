package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ProviderConnection
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
class LocalRuntimeRouterTest {

    private lateinit var qnnRuntime: FakeLocalRuntime
    private lateinit var liteRtRuntime: FakeLocalRuntime
    private lateinit var fakeSettingRepository: FakeRouterSettingRepository
    private lateinit var router: LocalRuntimeRouter

    @Before
    fun setUp() {
        qnnRuntime = FakeLocalRuntime()
        liteRtRuntime = FakeLocalRuntime()
        fakeSettingRepository = FakeRouterSettingRepository()
        router = LocalRuntimeRouter(
            settingRepository = fakeSettingRepository,
            qnnRuntime = qnnRuntime,
            liteRtRuntime = liteRtRuntime
        )
    }

    @Test
    fun gpuSelectionUsesLiteRtEvenWhenQnnIsPreferredAndFallbackDisabled() = runTest {
        fakeSettingRepository.features = dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings(qnnAutomaticFallback = false)
        val spec = testEngineSpec().copy(accelerator = LocalAccelerators.GPU)
        router.loadEngine(spec)
        router.loadEngine(spec)
        assertTrue(qnnRuntime.loadEngineCalls.isEmpty())
        assertEquals(listOf(spec), liteRtRuntime.loadEngineCalls)
        assertEquals(LocalRuntimeBackend.LITERT_LM, router.state.value.backend)
        assertEquals(LocalRuntimeBackend.QUALCOMM_QNN, fakeSettingRepository.backend)
    }

    @Test
    fun npuOnlyPackageIsNeverRetriedWithGpuOrCpu() = runTest {
        qnnRuntime.failLoadEngineIf = { IllegalStateException("NPU unavailable") }
        val failure = runCatching { router.loadEngine(testEngineSpec().copy(modelPath = "/models/gemma_SM8750.litertlm")) }.exceptionOrNull()
        assertTrue(failure!!.message!!.contains("GPU / CPU edition"))
        assertTrue(liteRtRuntime.loadEngineCalls.isEmpty())
    }

    @Test
    fun tuningChangesInvalidateWarmEngineAndReachCpuBackend() = runTest {
        fakeSettingRepository.backend = LocalRuntimeBackend.LITERT_LM
        fakeSettingRepository.features = dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings(localCpuThreads = 1, localModelCache = false)
        val spec = testEngineSpec().copy(accelerator = LocalAccelerators.CPU)
        val holder = LocalEngineHolder(router) { 1000L }
        holder.loadEngine(spec)
        assertEquals(1, liteRtRuntime.loadEngineCalls.last().cpuThreads)
        assertFalse(liteRtRuntime.loadEngineCalls.last().cacheEnabled)
        assertTrue(holder.isEngineLoaded(spec))
        fakeSettingRepository.features = fakeSettingRepository.features.copy(localCpuThreads = 0, localModelCache = true)
        assertFalse(holder.isEngineLoaded(spec))
        holder.loadEngine(spec)
        assertEquals(null, liteRtRuntime.loadEngineCalls.last().cpuThreads)
        assertTrue(liteRtRuntime.loadEngineCalls.last().cacheEnabled)
    }

    @Test
    fun threadCountIsBoundedToAvailableCoresAndZeroIsAutomatic() {
        val features = dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings(localCpuThreads = 99)
        assertEquals(4, features.localEngineTuning(4).first)
        assertEquals(null, features.copy(localCpuThreads = 0).localEngineTuning(4).first)
    }

    @Test
    fun defaultPreferenceLoadsQnnAndPublishesActualNpu() = runTest {
        router.loadEngine(testEngineSpec())
        assertEquals(listOf(testEngineSpec()), qnnRuntime.loadEngineCalls)
        assertTrue(liteRtRuntime.loadEngineCalls.isEmpty())
        assertEquals(LocalRuntimeBackend.QUALCOMM_QNN, router.state.value.backend)
        assertEquals(LocalAccelerators.NPU, router.state.value.engineSpec?.accelerator)
    }

    @Test
    fun liteRtSelectionDoesNotTryQnn() = runTest {
        fakeSettingRepository.backend = LocalRuntimeBackend.LITERT_LM
        val spec = testEngineSpec().copy(accelerator = LocalAccelerators.GPU)
        router.loadEngine(spec)
        assertEquals(listOf(spec), liteRtRuntime.loadEngineCalls)
        assertTrue(qnnRuntime.loadEngineCalls.isEmpty())
    }

    @Test
    fun qnnFailureUsesGpuAndPersistsTheWorkingBackend() = runTest {
        qnnRuntime.failLoadEngineIf = { IllegalStateException("HTP unavailable") }
        router.loadEngine(testEngineSpec())
        assertEquals(listOf(LocalAccelerators.GPU), liteRtRuntime.loadEngineCalls.map { it.accelerator })
        assertEquals(LocalRuntimeBackend.LITERT_LM, fakeSettingRepository.backend)
        assertEquals(LocalAccelerators.GPU, router.state.value.engineSpec?.accelerator)
        assertTrue(router.isEngineLoaded(testEngineSpec()))
        assertTrue(qnnRuntime.unloadEngineCalls > 0)
    }

    @Test
    fun gpuDriverFailureUsesCpuAndKeepsTheRequestedSpecWarm() = runTest {
        fakeSettingRepository.backend = LocalRuntimeBackend.LITERT_LM
        liteRtRuntime.failLoadEngineIf = { if (it.accelerator == LocalAccelerators.GPU) UnsatisfiedLinkError("driver") else null }
        val holder = LocalEngineHolder(router) { 1000L }
        val spec = testEngineSpec().copy(accelerator = LocalAccelerators.GPU)
        holder.loadEngine(spec)
        holder.loadEngine(spec)
        assertEquals(listOf(LocalAccelerators.GPU, LocalAccelerators.CPU), liteRtRuntime.loadEngineCalls.map { it.accelerator })
        assertEquals(LocalAccelerators.CPU, holder.loadedEngineSpec()?.accelerator)
    }

    @Test
    fun fallbackDisabledNeverAttemptsLiteRtOrCpu() = runTest {
        fakeSettingRepository.features = dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings(qnnAutomaticFallback = false)
        qnnRuntime.failLoadEngineIf = { IllegalStateException("QNN failed") }
        val error = runCatching { router.loadEngine(testEngineSpec()) }.exceptionOrNull()
        assertTrue(error is LocalRuntimeFallbackDisabledException)
        assertTrue(liteRtRuntime.loadEngineCalls.isEmpty())
        assertEquals(LocalRuntimeBackend.QUALCOMM_QNN, fakeSettingRepository.backend)
        assertEquals(null, router.state.value.engineSpec)
    }

    @Test
    fun cancelledQnnLoadDoesNotFallbackOrChangePreference() = runTest {
        qnnRuntime.failLoadEngineIf = { kotlinx.coroutines.CancellationException("cancel") }
        val error = runCatching { router.loadEngine(testEngineSpec()) }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.CancellationException)
        assertTrue(liteRtRuntime.loadEngineCalls.isEmpty())
        assertEquals(LocalRuntimeBackend.QUALCOMM_QNN, fakeSettingRepository.backend)
        assertFalse(router.isEngineLoaded(testEngineSpec()))
    }

    @Test
    fun failedFallbackDoesNotPersistSuccessOrLeaveAnActiveEngine() = runTest {
        qnnRuntime.failLoadEngineIf = { IllegalStateException("QNN failed") }
        liteRtRuntime.failLoadEngineIf = { IllegalStateException("unsupported model") }
        assertTrue(runCatching { router.loadEngine(testEngineSpec()) }.isFailure)
        assertEquals(LocalRuntimeBackend.QUALCOMM_QNN, fakeSettingRepository.backend)
        assertEquals(null, router.loadedEngineSpec())
        assertFalse(router.hasOpenConversation())
    }

    @Test
    fun settingsSwitchInvalidatesWarmEngineAndRoutesTheNextConversation() = runTest {
        val holder = LocalEngineHolder(router) { 1000L }
        val spec = testEngineSpec()
        holder.loadEngine(spec)
        holder.createConversation(testConversationConfig())
        fakeSettingRepository.backend = LocalRuntimeBackend.LITERT_LM
        assertFalse(holder.isEngineLoaded(spec))
        holder.loadEngine(spec)
        holder.createConversation(testConversationConfig())
        holder.sendMessage("new backend").toList()
        assertTrue(qnnRuntime.sendMessageCalls.isEmpty())
        assertEquals(listOf("new backend"), liteRtRuntime.sendMessageCalls)
        assertFalse(qnnRuntime.hasOpenConversation())
    }

    @Test
    fun sendAndCancelUseOnlyTheRuntimeThatOwnsTheConversation() = runTest {
        router.loadEngine(testEngineSpec())
        router.createConversation(testConversationConfig())
        router.sendMessage("hello").toList()
        router.cancelActive()
        assertEquals(listOf("hello"), qnnRuntime.sendMessageCalls)
        assertTrue(liteRtRuntime.sendMessageCalls.isEmpty())
        assertEquals(1, qnnRuntime.cancelActiveCalls)
        assertEquals(0, liteRtRuntime.cancelActiveCalls)
        router.closeConversation()
        assertFalse(router.hasOpenConversation())
    }

    @Test
    fun unloadedRouterReportsFailureInsteadOfGuessingARuntime() = runTest {
        assertTrue(router.sendMessage("hello").toList().single() is LocalRuntimeEvent.Error)
        router.loadEngine(testEngineSpec())
        router.unloadEngine()
        assertFalse(router.isEngineLoaded(testEngineSpec()))
        assertEquals(null, router.state.value.backend)
    }

    private fun testEngineSpec() = LocalEngineSpec(
        modelPath = "/path/to/model.bin",
        accelerator = LocalAccelerators.NPU,
        maxTokens = 512
    )

    private fun testConversationConfig() = LocalConversationConfig(
        sampler = LocalSamplerConfig(topK = 40, topP = 0.95f, temperature = 0.8f),
        systemPrompt = null,
        initialMessages = emptyList()
    )

    private class FakeRouterSettingRepository : SettingRepository {
        var backend: LocalRuntimeBackend = LocalRuntimeBackend.DEFAULT
        var features = dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings()
        override suspend fun getFeatureSettings() = features

        override suspend fun fetchProviderConnections(): List<ProviderConnection> = emptyList()
        override fun observeProviderConnections(): Flow<List<ProviderConnection>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getProviderConnection(uid: String): ProviderConnection? = null
        override suspend fun addProviderConnection(connection: ProviderConnection, credential: String?): ProviderConnection = error("Provider writes are not used by this fixture")
        override suspend fun updateProviderConnection(connection: ProviderConnection, credential: String?): ProviderConnection = error("Provider writes are not used by this fixture")
        override suspend fun deleteProviderConnection(connection: ProviderConnection): Boolean = error("Provider writes are not used by this fixture")

        override suspend fun getLocalRuntimeBackend(): LocalRuntimeBackend = backend

        override suspend fun updateLocalRuntimeBackend(backend: LocalRuntimeBackend) {
            this.backend = backend
        }

        override suspend fun fetchPlatforms(): List<Platform> = emptyList()
        override suspend fun fetchPlatformV2s(): List<PlatformV2> = emptyList()
        override fun observePlatformV2s(): Flow<List<PlatformV2>> = emptyFlow()
        override fun observePlatformV2ByUid(uid: String): Flow<PlatformV2?> = emptyFlow()
        override suspend fun fetchThemes(): ThemeSetting = ThemeSetting()
        override suspend fun getDebugMode(): Boolean = false
        override suspend fun updateDebugMode(enabled: Boolean) = Unit
        override fun observeDebugMode(): Flow<Boolean> = emptyFlow()
        override suspend fun getFavoriteGroups(): List<String> = emptyList()
        override suspend fun saveFavoriteGroups(groups: List<String>) = Unit
        override fun observeFavoriteGroups(): Flow<List<String>> = emptyFlow()
        override suspend fun getFavoriteMessageGroups(): Map<Int, String> = emptyMap()
        override suspend fun saveFavoriteMessageGroups(messageGroups: Map<Int, String>) = Unit
        override fun observeFavoriteMessageGroups(): Flow<Map<Int, String>> = emptyFlow()
        override suspend fun migrateToPlatformV2() = Unit
        override suspend fun migrateSecrets(): List<SecretMigrationError> = emptyList()
        override suspend fun updatePlatforms(platforms: List<Platform>) = Unit
        override suspend fun updateThemes(themeSetting: ThemeSetting) = Unit
        override suspend fun addPlatformV2(platform: PlatformV2) = Unit
        override suspend fun updatePlatformV2(platform: PlatformV2) = Unit
        override suspend fun deletePlatformV2(platform: PlatformV2) = Unit
        override suspend fun getPlatformV2ById(id: Int): PlatformV2? = null
        override suspend fun exportConfigurationJson(): String = "{}"
        override suspend fun importConfigurationJson(json: String): Result<Int> = Result.success(0)
    }
}
