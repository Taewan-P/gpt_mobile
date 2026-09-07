package dev.chungjungsoo.gptmobile.data.agent.tool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentDateToolTest {
    @Test
    fun `no argument tool exposes an explicit object schema accepted by strict providers`() {
        val schema = CurrentDateTool().definition.inputSchema

        assertEquals("object", schema.getValue("type").jsonPrimitive.content)
        assertEquals(JsonObject(emptyMap()), schema.getValue("properties").jsonObject)
        assertEquals("false", schema.getValue("additionalProperties").toString())
    }
}
