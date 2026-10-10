package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderContextPolicyTest {
    @Test
    fun `mistral policy applies 12 MiB inline ceiling`() {
        assertEquals(
            12L * 1024 * 1024,
            ProviderContextPolicy.forClientType(ClientType.MISTRAL).maxInlineAttachmentBytes
        )
    }
}
