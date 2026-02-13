package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.McpServerConfig
import dev.chungjungsoo.gptmobile.data.database.entity.McpTransportType
import dev.chungjungsoo.gptmobile.data.dto.tool.Tool
import dev.chungjungsoo.gptmobile.data.mcp.McpManager
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: McpSettingsViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onAddServer: () -> Unit,
    onServerClick: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val builtInTools by viewModel.builtInTools.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.mcp_servers)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            McpConnectionStatus(connectionState)

            SettingItem(
                title = stringResource(R.string.add_mcp_server),
                description = stringResource(R.string.add_mcp_server_description),
                onItemClick = onAddServer,
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            servers.forEach { server ->
                McpServerItem(
                    server = server,
                    onItemClick = { onServerClick(server.id) }
                )
            }

            Text(
                text = stringResource(R.string.builtin_tools),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            BuiltInToolsList(builtInTools = builtInTools)
        }
    }
}

@Composable
private fun McpConnectionStatus(connectionState: McpManager.ConnectionState) {
    val description = if (connectionState.isConnecting) {
        stringResource(
            R.string.mcp_connecting_status,
            connectionState.attemptedServers,
            connectionState.totalServers
        )
    } else {
        stringResource(
            R.string.mcp_connected_status,
            connectionState.connectedServers,
            connectionState.totalServers
        )
    }

    SettingItem(
        title = stringResource(R.string.mcp_connection_status),
        description = description,
        enabled = false,
        onItemClick = {},
        showTrailingIcon = false,
        showLeadingIcon = false
    )

    if (connectionState.isConnecting && connectionState.totalServers > 0) {
        LinearProgressIndicator(
            progress = { connectionState.attemptedServers.toFloat() / connectionState.totalServers.toFloat() },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    } else if (!connectionState.lastError.isNullOrBlank()) {
        Text(
            text = connectionState.lastError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun McpServerItem(server: McpServerConfig, onItemClick: () -> Unit) {
    SettingItem(
        title = server.name,
        description = "${server.type.name} - ${if (server.enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled)}",
        onItemClick = onItemClick,
        showTrailingIcon = true,
        showLeadingIcon = true,
        leadingIcon = {
            Icon(
                imageVector = when (server.type) {
                    McpTransportType.WEBSOCKET -> Icons.Filled.Cloud
                    McpTransportType.STREAMABLE_HTTP -> Icons.Filled.Link
                    McpTransportType.SSE -> Icons.Filled.Public
                    McpTransportType.STDIO -> Icons.Filled.Terminal
                },
                contentDescription = null
            )
        }
    )
}

@Composable
private fun BuiltInToolsList(builtInTools: List<Tool>) {
    if (builtInTools.isEmpty()) {
        Text(
            text = stringResource(R.string.no_builtin_tools_available),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }

    builtInTools.forEach { tool ->
        SettingItem(
            title = tool.name,
            description = tool.description,
            enabled = false,
            onItemClick = {},
            showTrailingIcon = false,
            showLeadingIcon = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Code,
                    contentDescription = null
                )
            }
        )
    }
}
