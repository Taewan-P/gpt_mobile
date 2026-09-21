package dev.chungjungsoo.gptmobile.data.huggingface

import org.junit.Assert.assertEquals
import org.junit.Test

class HuggingFaceOAuthRequestsTest {

    @Test
    fun `missing activity result is a failed auth attempt`() {
        assertEquals(HuggingFaceAuthOutcome.Failed, HuggingFaceOAuthRequests.parseResult(null))
    }
}
