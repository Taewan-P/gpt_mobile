package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatedDownloadCoordinatorTest {

    @Test
    fun `non-gated entries proceed without probing`() = runTest {
        val prober = RecordingProber()
        val coordinator = coordinator(prober = prober)

        val step = coordinator.resolve(entry(isGated = false))

        assertEquals(GatedDownloadStep.Proceed, step)
        assertTrue(prober.calls.isEmpty())
    }

    @Test
    fun `gated probe 200 proceeds and keeps any stored token`() = runTest {
        val vault = MapSecretVault(mapOf(HuggingFaceTokenStore.SECRET_REF to "hf_ok".encodeToByteArray()))
        val coordinator = coordinator(
            tokenStore = HuggingFaceTokenStore(vault),
            prober = RecordingProber(statusCode = 200)
        )

        assertEquals(GatedDownloadStep.Proceed, coordinator.resolve(gatedEntry()))
        assertEquals("hf_ok", String(vault.values.getValue(HuggingFaceTokenStore.SECRET_REF)))
    }

    @Test
    fun `gated 401 without a token requests sign-in when oauth is configured`() = runTest {
        val coordinator = coordinator(prober = RecordingProber(statusCode = 401))

        assertEquals(
            GatedDownloadStep.NeedsSignIn(isSessionExpired = false),
            coordinator.resolve(gatedEntry())
        )
    }

    @Test
    fun `gated 401 with a token clears it and requests reauthentication`() = runTest {
        val vault = MapSecretVault(mapOf(HuggingFaceTokenStore.SECRET_REF to "hf_expired".encodeToByteArray()))
        val coordinator = coordinator(
            tokenStore = HuggingFaceTokenStore(vault),
            prober = RecordingProber(statusCode = 401)
        )

        assertEquals(
            GatedDownloadStep.NeedsSignIn(isSessionExpired = true),
            coordinator.resolve(gatedEntry())
        )
        assertNull(vault.values[HuggingFaceTokenStore.SECRET_REF])
    }

    @Test
    fun `sign-in needed without oauth credentials routes to token entry`() = runTest {
        val coordinator = coordinator(
            prober = RecordingProber(statusCode = 401),
            oauthConfigured = false
        )

        assertEquals(
            GatedDownloadStep.OAuthNotConfigured(isSessionExpired = false),
            coordinator.resolve(gatedEntry())
        )
    }

    @Test
    fun `session expired without oauth credentials routes to token entry`() = runTest {
        val vault = MapSecretVault(mapOf(HuggingFaceTokenStore.SECRET_REF to "hf_expired".encodeToByteArray()))
        val coordinator = coordinator(
            tokenStore = HuggingFaceTokenStore(vault),
            prober = RecordingProber(statusCode = 401),
            oauthConfigured = false
        )

        assertEquals(
            GatedDownloadStep.OAuthNotConfigured(isSessionExpired = true),
            coordinator.resolve(gatedEntry())
        )
        assertNull(vault.values[HuggingFaceTokenStore.SECRET_REF])
    }

    @Test
    fun `gated 403 with a token opens the huggingface license page`() = runTest {
        val vault = MapSecretVault(mapOf(HuggingFaceTokenStore.SECRET_REF to "hf_ok".encodeToByteArray()))
        val coordinator = coordinator(
            tokenStore = HuggingFaceTokenStore(vault),
            prober = RecordingProber(statusCode = 403)
        )

        assertEquals(
            GatedDownloadStep.NeedsLicense(
                modelPageUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT"
            ),
            coordinator.resolve(gatedEntry())
        )
    }

    @Test
    fun `gated 403 without a token requests sign-in`() = runTest {
        val coordinator = coordinator(prober = RecordingProber(statusCode = 403))

        assertEquals(
            GatedDownloadStep.NeedsSignIn(isSessionExpired = false),
            coordinator.resolve(gatedEntry())
        )
    }

    @Test
    fun `probe network failure is an error`() = runTest {
        val coordinator = coordinator(prober = RecordingProber(statusCode = -1))

        assertEquals(GatedDownloadStep.Error, coordinator.resolve(gatedEntry()))
    }

    @Test
    fun `probe uses the stored token when present`() = runTest {
        val vault = MapSecretVault(mapOf(HuggingFaceTokenStore.SECRET_REF to "hf_ok".encodeToByteArray()))
        val prober = RecordingProber(statusCode = 200)
        val coordinator = coordinator(
            tokenStore = HuggingFaceTokenStore(vault),
            prober = prober
        )

        coordinator.resolve(gatedEntry())

        assertEquals(listOf("hf_ok"), prober.tokens)
    }

    @Test
    fun `probe executes on the injected dispatcher`() = runTest {
        val onInjectedDispatcher = ThreadLocal<Boolean>()
        val recordingDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                onInjectedDispatcher.set(true)
                try {
                    block.run()
                } finally {
                    onInjectedDispatcher.remove()
                }
            }
        }
        var probedOnInjectedDispatcher = false
        val prober = LocalModelDownloadProber { _, _ ->
            probedOnInjectedDispatcher = onInjectedDispatcher.get() == true
            200
        }

        val step = coordinator(prober = prober, ioDispatcher = recordingDispatcher).resolve(gatedEntry())

        assertEquals(GatedDownloadStep.Proceed, step)
        assertTrue(probedOnInjectedDispatcher)
    }

    private fun coordinator(
        tokenStore: HuggingFaceTokenStore = HuggingFaceTokenStore(MapSecretVault()),
        prober: LocalModelDownloadProber = RecordingProber(),
        oauthConfigured: Boolean = true,
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    ): GatedDownloadCoordinator = GatedDownloadCoordinator(
        tokenStore = tokenStore,
        prober = prober,
        isOAuthConfigured = { oauthConfigured },
        ioDispatcher = ioDispatcher
    )

    private fun gatedEntry(): CatalogEntry = entry(isGated = true)

    private fun entry(isGated: Boolean): CatalogEntry = CatalogEntry(
        id = "gemma3-1b-it",
        displayName = "Gemma3-1B-IT",
        downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true",
        isGated = isGated
    )
}

private class RecordingProber(
    private val statusCode: Int = 200
) : LocalModelDownloadProber {
    val calls = mutableListOf<String>()
    val tokens = mutableListOf<String?>()

    override fun probe(downloadUrl: String, accessToken: String?): Int {
        calls += downloadUrl
        tokens += accessToken
        return statusCode
    }
}

private class MapSecretVault(
    initial: Map<String, ByteArray> = emptyMap()
) : SecretVault {
    val values = initial.mapValues { it.value.copyOf() }.toMutableMap()

    override suspend fun put(secretRef: String, secret: ByteArray) {
        values[secretRef] = secret.copyOf()
    }

    override suspend fun read(secretRef: String): ByteArray? = values[secretRef]?.copyOf()

    override suspend fun delete(secretRef: String) {
        values.remove(secretRef)?.fill(0)
    }
}
