package dev.chungjungsoo.gptmobile.presentation.ui.setting

import dev.chungjungsoo.gptmobile.data.database.entity.McpTransportType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddMcpServerViewModelTest {

    @Test
    fun uiState_isValidForHttpUrl_whenNameAndUrlPresent() {
        val state = AddMcpServerViewModel.UiState(
            name = "Example",
            type = McpTransportType.STREAMABLE_HTTP,
            url = "https://example.com/mcp"
        )

        assertTrue(state.isValid)
        assertTrue(state.canTest)
    }

    @Test
    fun uiState_websocketRequiresWsScheme() {
        val invalidState = AddMcpServerViewModel.UiState(
            name = "Example",
            type = McpTransportType.WEBSOCKET,
            url = "https://example.com/mcp"
        )
        val validState = AddMcpServerViewModel.UiState(
            name = "Example",
            type = McpTransportType.WEBSOCKET,
            url = "wss://example.com/mcp"
        )

        assertFalse(invalidState.isValid)
        assertFalse(invalidState.canTest)
        assertTrue(validState.isValid)
        assertTrue(validState.canTest)
    }

    @Test
    fun uiState_stdioRequiresCommandAndIsTestable() {
        val missingCommand = AddMcpServerViewModel.UiState(
            name = "Local",
            type = McpTransportType.STDIO,
            command = ""
        )
        val withCommand = AddMcpServerViewModel.UiState(
            name = "Local",
            type = McpTransportType.STDIO,
            command = "npx @modelcontextprotocol/server-filesystem"
        )

        assertFalse(missingCommand.isValid)
        assertFalse(missingCommand.canTest)
        assertTrue(withCommand.isValid)
        assertTrue(withCommand.canTest)
    }

    @Test
    fun inferType_defaultsToStreamableHttpForHttpUrls() {
        val state = AddMcpServerViewModel.UiState(
            name = "Context7",
            type = McpTransportType.STREAMABLE_HTTP,
            url = "https://mcp.context7.com/mcp"
        )

        assertTrue(state.isValid)
        assertTrue(state.canTest)
    }
}
