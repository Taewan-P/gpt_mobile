package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.McpServerConfig
import dev.chungjungsoo.gptmobile.data.database.entity.McpTransportType
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServerDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: McpServerDetailViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val server = uiState.server
    var isDeleteDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigationClick()
        }
    }

    val handleNavigation: () -> Unit = {
        viewModel.commitPending()
        onNavigationClick()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeTopAppBar(
                title = { Text(server?.name ?: stringResource(R.string.mcp_server_detail)) },
                navigationIcon = {
                    IconButton(onClick = handleNavigation) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            if (server == null) {
                Text(
                    text = stringResource(R.string.mcp_server_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
                return@Column
            }

            PreferenceSwitchWithContainer(
                title = stringResource(R.string.enable_server),
                isChecked = server.enabled,
                onClick = { viewModel.toggleEnabled(!server.enabled) }
            )

            ServerDetails(
                server = server,
                onNameChange = viewModel::updateName,
                onTypeChange = viewModel::updateType,
                onUrlChange = viewModel::updateUrl,
                onCommandChange = viewModel::updateCommand
            )

            if (server.type == McpTransportType.STDIO) {
                ArgsSection(
                    args = server.args,
                    pendingArg = uiState.pendingArg,
                    onPendingArgChange = viewModel::updatePendingArg,
                    onAddArg = { arg ->
                        viewModel.addArg(arg)
                        viewModel.updatePendingArg("")
                    },
                    onRemoveArg = viewModel::removeArg,
                    onUpdateArg = viewModel::updateArg
                )
            }

            if (server.type != McpTransportType.STDIO) {
                HeadersSection(
                    headers = server.headers,
                    pendingHeaderKey = uiState.pendingHeaderKey,
                    pendingHeaderValue = uiState.pendingHeaderValue,
                    onPendingHeaderKeyChange = viewModel::updatePendingHeaderKey,
                    onPendingHeaderValueChange = viewModel::updatePendingHeaderValue,
                    onAddHeader = { key, value ->
                        viewModel.addHeader(key, value)
                        viewModel.updatePendingHeaderKey("")
                        viewModel.updatePendingHeaderValue("")
                    },
                    onRemoveHeader = viewModel::removeHeader,
                    onUpdateHeader = viewModel::updateHeader
                )
            }

            Button(
                onClick = viewModel::testConnection,
                enabled = !uiState.isTesting,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    if (uiState.isTesting) {
                        stringResource(R.string.testing)
                    } else {
                        stringResource(R.string.test_connection)
                    }
                )
            }

            uiState.statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.isStatusError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { isDeleteDialogOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.delete_server))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (isDeleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { isDeleteDialogOpen = false },
            title = { Text(stringResource(R.string.delete_server)) },
            text = { Text(stringResource(R.string.delete_server_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    isDeleteDialogOpen = false
                    viewModel.delete()
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteDialogOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ServerDetails(
    server: McpServerConfig,
    onNameChange: (String) -> Unit,
    onTypeChange: (McpTransportType) -> Unit,
    onUrlChange: (String) -> Unit,
    onCommandChange: (String) -> Unit
) {
    var editName by remember(server.name) { mutableStateOf(server.name) }
    var editUrl by remember(server.url) { mutableStateOf(server.url ?: "") }
    var editCommand by remember(server.command) { mutableStateOf(server.command ?: "") }

    OutlinedTextField(
        value = editName,
        onValueChange = { editName = it },
        label = { Text(stringResource(R.string.server_name)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true
    )

    LaunchedEffect(editName) {
        if (editName != server.name && editName.isNotBlank()) {
            onNameChange(editName)
        }
    }

    Text(
        text = stringResource(R.string.transport_type),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    McpTransportType.entries.forEach { type ->
        RadioItem(
            title = transportTypeTitle(type),
            description = transportTypeDescription(type),
            value = type.name,
            selected = server.type == type,
            onSelected = { onTypeChange(type) }
        )
    }

    if (server.type != McpTransportType.STDIO) {
        OutlinedTextField(
            value = editUrl,
            onValueChange = { editUrl = it },
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = { Text(stringResource(R.string.server_url_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true
        )

        LaunchedEffect(editUrl) {
            if (editUrl != (server.url ?: "")) {
                onUrlChange(editUrl)
            }
        }
    }

    if (server.type == McpTransportType.STDIO) {
        OutlinedTextField(
            value = editCommand,
            onValueChange = { editCommand = it },
            label = { Text(stringResource(R.string.command)) },
            placeholder = { Text(stringResource(R.string.command_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true
        )

        Text(
            text = stringResource(R.string.stdio_termux_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LaunchedEffect(editCommand) {
            if (editCommand != (server.command ?: "")) {
                onCommandChange(editCommand)
            }
        }
    }
}

@Composable
private fun transportTypeTitle(type: McpTransportType): String = when (type) {
    McpTransportType.WEBSOCKET -> stringResource(R.string.transport_websocket)
    McpTransportType.STREAMABLE_HTTP -> stringResource(R.string.transport_http)
    McpTransportType.SSE -> stringResource(R.string.transport_sse)
    McpTransportType.STDIO -> stringResource(R.string.transport_stdio)
}

@Composable
private fun transportTypeDescription(type: McpTransportType): String = when (type) {
    McpTransportType.WEBSOCKET -> stringResource(R.string.transport_websocket_desc)
    McpTransportType.STREAMABLE_HTTP -> stringResource(R.string.transport_http_desc)
    McpTransportType.SSE -> stringResource(R.string.transport_sse_desc)
    McpTransportType.STDIO -> stringResource(R.string.transport_stdio_desc)
}
