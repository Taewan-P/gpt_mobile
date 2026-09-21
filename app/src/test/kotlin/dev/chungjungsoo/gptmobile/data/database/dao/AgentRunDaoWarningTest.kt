package dev.chungjungsoo.gptmobile.data.database.dao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentRunDaoWarningTest {
    @Test
    fun `combine keeps existing warning when finish supplies no error`() {
        assertEquals("unconfirmed cancel", combineTerminalErrors("unconfirmed cancel", null))
        assertEquals("unconfirmed cancel", combineTerminalErrors("unconfirmed cancel", "  "))
    }

    @Test
    fun `combine appends a new terminal error without dropping the warning`() {
        assertEquals(
            "unconfirmed cancel\nrecovery failed",
            combineTerminalErrors("unconfirmed cancel", "recovery failed")
        )
    }

    @Test
    fun `combine does not duplicate an already recorded warning`() {
        assertEquals("unconfirmed cancel", combineTerminalErrors("unconfirmed cancel", "unconfirmed cancel"))
        assertEquals(
            "unconfirmed cancel\nlater",
            combineTerminalErrors("unconfirmed cancel\nlater", "unconfirmed cancel")
        )
        assertNull(combineTerminalErrors(null, null))
        assertEquals("only new", combineTerminalErrors(null, "only new"))
    }
}
