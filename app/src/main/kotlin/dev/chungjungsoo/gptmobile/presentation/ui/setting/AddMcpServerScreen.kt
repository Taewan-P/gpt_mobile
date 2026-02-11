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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.McpTransportType
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMcpServerScreen(
    modifier: Modifier = Modifier,
    viewModel: AddMcpServerViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onServerAdded: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.add_mcp_server)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
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
            OutlinedTextField(
                value = uiState.installJson,
                onValueChange = viewModel::updateInstallJson,
                label = { Text(stringResource(R.string.mcp_install_json)) },
                placeholder = { Text(stringResource(R.string.mcp_install_json_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                minLines = 5,
                maxLines = 9
            )

            Button(
                onClick = viewModel::importInstallJson,
                enabled = !uiState.isSaving,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.import_install_json))
            }

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.server_name)) },
                placeholder = { Text(stringResource(R.string.server_name_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

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
                    selected = uiState.type == type,
                    onSelected = { viewModel.updateType(type) }
                )
            }

            if (uiState.type != McpTransportType.STDIO) {
                OutlinedTextField(
                    value = uiState.url,
                    onValueChange = viewModel::updateUrl,
                    label = { Text(stringResource(R.string.server_url)) },
                    placeholder = { Text(stringResource(R.string.server_url_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )
            }

            if (uiState.type == McpTransportType.STDIO) {
                OutlinedTextField(
                    value = uiState.command,
                    onValueChange = viewModel::updateCommand,
                    label = { Text(stringResource(R.string.command)) },
                    placeholder = { Text(stringResource(R.string.command_hint)) },
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )

                Text(
                    text = stringResource(R.string.stdio_requires_native_support),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (uiState.headers.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.mcp_headers_imported, uiState.headers.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Button(
                onClick = viewModel::testConnection,
                enabled = uiState.canTest && !uiState.isTesting && !uiState.isSaving,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.test_connection))
            }

            uiState.connectionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.isConnectionError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            PrimaryLongButton(
                text = stringResource(R.string.save),
                enabled = uiState.isValid && !uiState.isSaving,
                onClick = { viewModel.save(onServerAdded) }
            )
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
