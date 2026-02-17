package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
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
    var installJsonField by remember { mutableStateOf(TextFieldValue(uiState.installJson)) }
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.installJson) {
        if (uiState.installJson != installJsonField.text) {
            installJsonField = TextFieldValue(uiState.installJson)
        }
    }

    LaunchedEffect(uiState.importSucceeded) {
        if (uiState.importSucceeded) {
            scrollState.animateScrollTo(scrollState.maxValue)
            viewModel.clearImportSucceeded()
        }
    }

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
                .verticalScroll(scrollState)
                .imePadding()
        ) {
            OutlinedTextField(
                value = installJsonField,
                onValueChange = { value ->
                    installJsonField = value
                    viewModel.updateInstallJson(value.text)
                },
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

                ArgsSection(
                    args = uiState.args,
                    pendingArg = uiState.pendingArg,
                    onPendingArgChange = viewModel::updatePendingArg,
                    onAddArg = {
                        viewModel.commitPendingArg()
                    },
                    onRemoveArg = viewModel::removeArg,
                    onUpdateArg = viewModel::updateArg
                )
            }

            if (uiState.type != McpTransportType.STDIO) {
                if (uiState.headers.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.mcp_headers_imported, uiState.headers.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                HeadersSection(
                    headers = uiState.headers,
                    onAddHeader = viewModel::addHeader,
                    onRemoveHeader = viewModel::removeHeader,
                    onUpdateHeader = viewModel::updateHeader
                )
            }

            Button(
                onClick = viewModel::testConnection,
                enabled = uiState.canTest && !uiState.isTesting && !uiState.isSaving,
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

            MaxToolCallIterationsSection(
                maxIterations = uiState.maxToolCallIterations,
                onMaxIterationsChange = viewModel::updateMaxToolCallIterations
            )

            Spacer(modifier = Modifier.height(8.dp))

            PrimaryLongButton(
                text = if (uiState.isSaving) stringResource(R.string.saving) else stringResource(R.string.save),
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

@Composable
internal fun HeadersSection(
    headers: Map<String, String>,
    pendingHeaderKey: String? = null,
    pendingHeaderValue: String? = null,
    onPendingHeaderKeyChange: ((String) -> Unit)? = null,
    onPendingHeaderValueChange: ((String) -> Unit)? = null,
    onAddHeader: (String, String) -> Unit,
    onRemoveHeader: (String) -> Unit,
    onUpdateHeader: (String, String, String) -> Unit
) {
    val useLocalState = pendingHeaderKey == null
    var localPendingKey by remember { mutableStateOf("") }
    var localPendingValue by remember { mutableStateOf("") }

    val currentKey = if (useLocalState) localPendingKey else pendingHeaderKey ?: ""
    val currentValue = if (useLocalState) localPendingValue else pendingHeaderValue ?: ""

    Text(
        text = stringResource(R.string.mcp_headers),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    if (headers.isEmpty()) {
        Text(
            text = stringResource(R.string.no_headers),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    } else {
        headers.forEach { (key, value) ->
            HeaderRow(
                headerKey = key,
                headerValue = value,
                onUpdate = { newKey, newValue -> onUpdateHeader(key, newKey, newValue) },
                onRemove = { onRemoveHeader(key) }
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = currentKey,
            onValueChange = {
                if (useLocalState) {
                    localPendingKey = it
                } else {
                    onPendingHeaderKeyChange?.invoke(it)
                }
            },
            label = { Text(stringResource(R.string.mcp_header_name)) },
            placeholder = { Text(stringResource(R.string.mcp_header_name_hint)) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = currentValue,
            onValueChange = {
                if (useLocalState) {
                    localPendingValue = it
                } else {
                    onPendingHeaderValueChange?.invoke(it)
                }
            },
            label = { Text(stringResource(R.string.mcp_header_value)) },
            placeholder = { Text(stringResource(R.string.mcp_header_value_hint)) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(
            onClick = {
                if (currentKey.isNotBlank()) {
                    onAddHeader(currentKey, currentValue)
                    if (useLocalState) {
                        localPendingKey = ""
                        localPendingValue = ""
                    } else {
                        onPendingHeaderKeyChange?.invoke("")
                        onPendingHeaderValueChange?.invoke("")
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.add_header)
            )
        }
    }
}

@Composable
internal fun HeaderRow(
    headerKey: String,
    headerValue: String,
    onUpdate: (String, String) -> Unit,
    onRemove: () -> Unit
) {
    var localKey by remember { mutableStateOf(headerKey) }
    var localValue by remember { mutableStateOf(headerValue) }

    LaunchedEffect(localKey, localValue) {
        if (localKey.isNotBlank() && (localKey != headerKey || localValue != headerValue)) {
            onUpdate(localKey, localValue)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = localKey,
            onValueChange = { localKey = it },
            label = { Text(stringResource(R.string.mcp_header_name)) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = localValue,
            onValueChange = { localValue = it },
            label = { Text(stringResource(R.string.mcp_header_value)) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.remove_header)
            )
        }
    }
}

@Composable
internal fun ArgsSection(
    args: List<String>,
    pendingArg: String? = null,
    onPendingArgChange: ((String) -> Unit)? = null,
    onAddArg: (String) -> Unit,
    onRemoveArg: (Int) -> Unit,
    onUpdateArg: (Int, String) -> Unit
) {
    val useLocalState = pendingArg == null
    var localPendingArg by remember { mutableStateOf("") }

    val currentArg = if (useLocalState) localPendingArg else pendingArg ?: ""
    Text(
        text = stringResource(R.string.mcp_args),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    val isEmpty = args.isEmpty() && currentArg.isBlank()
    if (isEmpty) {
        Text(
            text = stringResource(R.string.no_args),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    } else {
        args.forEachIndexed { index, arg ->
            ArgRow(
                arg = arg,
                onUpdate = { newValue -> onUpdateArg(index, newValue) },
                onRemove = { onRemoveArg(index) }
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = currentArg,
            onValueChange = {
                if (useLocalState) {
                    localPendingArg = it
                } else {
                    onPendingArgChange?.invoke(it)
                }
            },
            label = { Text(stringResource(R.string.mcp_arg)) },
            placeholder = { Text(stringResource(R.string.mcp_arg_hint)) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(
            onClick = {
                if (currentArg.isNotBlank()) {
                    onAddArg(currentArg)
                    if (useLocalState) {
                        localPendingArg = ""
                    } else {
                        onPendingArgChange?.invoke("")
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.add_arg)
            )
        }
    }
}

@Composable
internal fun ArgRow(
    arg: String,
    onUpdate: (String) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = arg,
            onValueChange = onUpdate,
            label = { Text(stringResource(R.string.mcp_arg)) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.remove_arg)
            )
        }
    }
}

