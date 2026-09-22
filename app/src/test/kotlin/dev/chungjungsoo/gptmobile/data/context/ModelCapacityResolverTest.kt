package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.agent.provider.LiteRtLmAdapter
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.FakeModelCatalogRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCapacityResolverTest {
    @Test
    fun auto_npuTarget_clampsCapacityBeforeCompaction() = runBlocking {
        val resolver = ModelCapacityResolver(
            InMemoryCompactionStore(),
            FakeModelCatalogRepository(listOf(npuEntry())),
            "SM8750",
            localAccelerator = { LocalAccelerators.NPU }
        )
        val result = resolver.resolve(localPlatform(maxTokens = 4096, accelerator = "auto"))
            as ModelCapacityResolution.Known
        assertEquals(1280, result.capacity.detectedContextWindowTokens)
    }

    @Test
    fun `cpu local capacity uses configured engine maxTokens not npu catalog context`() = runBlocking {
        val resolution = resolver().resolve(
            localPlatform(maxTokens = 4096, accelerator = LocalAccelerators.CPU)
        ) as ModelCapacityResolution.Known

        assertEquals(4096, resolution.capacity.detectedContextWindowTokens)
        assertEquals(4096, resolution.capacity.effectiveContextWindowTokens)
        assertNull(resolution.capacity.overrideContextWindowTokens)
    }

    @Test
    fun `npu local capacity clamps configured maxTokens to soc variant context`() = runBlocking {
        val resolution = resolver().resolve(
            localPlatform(maxTokens = 4096, accelerator = LocalAccelerators.NPU)
        ) as ModelCapacityResolution.Known

        assertEquals(1280, resolution.capacity.detectedContextWindowTokens)
    }

    @Test
    fun `gpu local capacity does not use npu soc context`() = runBlocking {
        val resolution = resolver().resolve(
            localPlatform(maxTokens = 4096, accelerator = LocalAccelerators.GPU)
        ) as ModelCapacityResolution.Known

        assertEquals(4096, resolution.capacity.detectedContextWindowTokens)
    }

    @Test
    fun `local default engine maxTokens is used when profile omits maxTokens`() = runBlocking {
        val resolution = resolver().resolve(
            localPlatform(maxTokens = null, accelerator = LocalAccelerators.GPU)
        ) as ModelCapacityResolution.Known

        assertEquals(LiteRtLmAdapter.DEFAULT_MAX_TOKENS, resolution.capacity.detectedContextWindowTokens)
    }

    @Test
    fun `local accelerator and maxTokens changes do not reuse stale stored detected capacity`() = runBlocking {
        val store = InMemoryCompactionStore()
        val resolver = resolver(store)
        val npu = localPlatform(maxTokens = 4096, accelerator = LocalAccelerators.NPU)
        resolver.resolve(npu)
        store.saveCapacity(
            ModelCapacity(
                platformUid = npu.uid,
                endpoint = npu.apiUrl,
                model = npu.model,
                detectedContextWindowTokens = 1280,
                overrideContextWindowTokens = null
            )
        )

        val cpu = resolver.resolve(
            localPlatform(maxTokens = 4096, accelerator = LocalAccelerators.CPU)
        ) as ModelCapacityResolution.Known
        assertEquals(4096, cpu.capacity.detectedContextWindowTokens)

        val lowered = resolver.resolve(
            localPlatform(maxTokens = 512, accelerator = LocalAccelerators.NPU)
        ) as ModelCapacityResolution.Known
        assertEquals(512, lowered.capacity.detectedContextWindowTokens)
    }

    @Test
    fun `local override is capped by the active engine maximum`() = runBlocking {
        val store = InMemoryCompactionStore()
        val resolver = resolver(store)
        val platform = localPlatform(maxTokens = 4096, accelerator = LocalAccelerators.NPU)

        resolver.saveOverride(platform, 99999)
        val stored = store.getCapacity(platform.uid, platform.apiUrl, platform.model)
        assertEquals(1280, stored?.overrideContextWindowTokens)
        assertEquals(1280, stored?.detectedContextWindowTokens)

        val resolution = resolver.resolve(platform) as ModelCapacityResolution.Known
        assertEquals(1280, resolution.capacity.overrideContextWindowTokens)
        assertEquals(1280, resolution.capacity.detectedContextWindowTokens)
    }

    @Test
    fun `hosted override-only remains detected null`() = runBlocking {
        val store = InMemoryCompactionStore()
        val resolver = resolver(store)
        val platform = openaiPlatform()

        resolver.saveOverride(platform, 32000)
        val stored = store.getCapacity(platform.uid, platform.apiUrl, platform.model)
        assertEquals(32000, stored?.overrideContextWindowTokens)
        assertNull(stored?.detectedContextWindowTokens)

        val resolution = resolver.resolve(platform) as ModelCapacityResolution.Known
        assertEquals(32000, resolution.capacity.overrideContextWindowTokens)
        assertNull(resolution.capacity.detectedContextWindowTokens)
        assertEquals(32000, resolution.capacity.effectiveContextWindowTokens)
    }

    @Test
    fun `unknown hosted model does not guess a limit`() = runBlocking {
        val resolution = resolver().resolve(openaiPlatform())
        assertTrue(resolution is ModelCapacityResolution.Unknown)
    }

    @Test
    fun `non-positive overrides are ignored`() = runBlocking {
        val store = InMemoryCompactionStore()
        val resolver = resolver(store)
        val platform = openaiPlatform()

        resolver.saveOverride(platform, 0)
        assertNull(store.getCapacity(platform.uid, platform.apiUrl, platform.model)?.overrideContextWindowTokens)
        resolver.saveOverride(platform, -8)
        assertNull(store.getCapacity(platform.uid, platform.apiUrl, platform.model)?.overrideContextWindowTokens)
        assertTrue(resolver.resolve(platform) is ModelCapacityResolution.Unknown)
    }

    @Test
    fun `reliable hosted metadata is cached and not treated as an output limit`() = runBlocking {
        val store = InMemoryCompactionStore()
        var lookups = 0
        val resolver = ModelCapacityResolver(
            store,
            FakeModelCatalogRepository(),
            "SM8750",
            RemoteContextWindowLookup {
                lookups += 1
                131072
            }
        )
        val platform = groqPlatform()

        val first = resolver.resolve(platform) as ModelCapacityResolution.Known
        val second = resolver.resolve(platform) as ModelCapacityResolution.Known

        assertEquals(131072, first.capacity.detectedContextWindowTokens)
        assertEquals(131072, second.capacity.detectedContextWindowTokens)
        assertEquals(1, lookups)
        assertEquals(131072, store.getCapacity(platform.uid, platform.apiUrl, platform.model)?.detectedContextWindowTokens)
    }

    private fun resolver(
        store: InMemoryCompactionStore = InMemoryCompactionStore()
    ): ModelCapacityResolver = ModelCapacityResolver(
        store,
        FakeModelCatalogRepository(listOf(npuEntry())),
        "SM8750"
    )

    private fun npuEntry() = CatalogEntry(
        id = "gemma3-1b-it",
        supportedAccelerators = listOf("gpu", "cpu", "npu"),
        socToModelFiles = mapOf(
            "SM8750" to SocVariant(modelFile = "npu-sm8750.litertlm", contextSize = 1280)
        )
    )

    private fun localPlatform(
        maxTokens: Int?,
        accelerator: String
    ) = PlatformV2(
        uid = "local",
        name = "Local",
        compatibleType = ClientType.LITERT_LM,
        apiUrl = "",
        model = "gemma3-1b-it",
        maxTokens = maxTokens,
        accelerator = accelerator
    )

    private fun openaiPlatform() = PlatformV2(
        uid = "openai",
        name = "OpenAI",
        compatibleType = ClientType.OPENAI,
        apiUrl = "https://api.openai.com/v1/",
        model = "gpt-5.4"
    )

    private fun groqPlatform() = PlatformV2(
        uid = "groq",
        name = "Groq",
        compatibleType = ClientType.GROQ,
        apiUrl = "https://api.groq.com/openai/v1/",
        model = "llama-3.1-8b-instant",
        token = "gsk-test"
    )
}
