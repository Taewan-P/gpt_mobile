package dev.chungjungsoo.gptmobile.data.huggingface

import dev.chungjungsoo.gptmobile.data.security.SecretVault
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuggingFaceTokenStoreTest {

    @Test
    fun `save and read stores the token under a stable vault ref`() = runTest {
        val vault = MapSecretVault()
        val store = HuggingFaceTokenStore(vault)

        store.saveAccessToken("hf_test_token")

        assertEquals("hf_test_token", store.readAccessToken())
        assertEquals(
            "hf_test_token",
            vault.values.getValue(HuggingFaceTokenStore.SECRET_REF).decodeToString()
        )
    }

    @Test
    fun `missing or blank vault values are treated as no token`() = runTest {
        val vault = MapSecretVault()
        val store = HuggingFaceTokenStore(vault)

        assertNull(store.readAccessToken())

        vault.values[HuggingFaceTokenStore.SECRET_REF] = "   ".encodeToByteArray()
        assertNull(store.readAccessToken())
    }

    @Test
    fun `clear deletes the stored token`() = runTest {
        val vault = MapSecretVault()
        val store = HuggingFaceTokenStore(vault)
        store.saveAccessToken("hf_test_token")

        store.clear()

        assertNull(store.readAccessToken())
        assertNull(vault.values[HuggingFaceTokenStore.SECRET_REF])
    }

    @Test
    fun `bearer header prefixes the token without exposing extra whitespace`() {
        assertEquals("Bearer hf_test_token", HuggingFaceTokenStore.bearerHeader("hf_test_token"))
    }
}

private class MapSecretVault : SecretVault {
    val values = mutableMapOf<String, ByteArray>()

    override suspend fun put(secretRef: String, secret: ByteArray) {
        values[secretRef] = secret.copyOf()
    }

    override suspend fun read(secretRef: String): ByteArray? = values[secretRef]?.copyOf()

    override suspend fun delete(secretRef: String) {
        values.remove(secretRef)?.fill(0)
    }
}
