package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.presentation.common.DestinationCard
import dev.chungjungsoo.gptmobile.presentation.common.EmptyErrorState
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.presentation.common.SettingsSection
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior
import dev.chungjungsoo.gptmobile.util.requiresLocalNetworkAccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolConnectionsScreen(
    modifier: Modifier = Modifier,
    viewModel: ToolConnectionsViewModel = hiltViewModel(),
    onLaunchOAuth: (String) -> Unit = {},
    onAddConnectionClick: () -> Unit,
    onEditConnectionClick: (String) -> Unit,
    onNavigationClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var deletingConnection by remember { mutableStateOf<ToolConnection?>(null) }
    var pendingOAuthConnection by remember { mutableStateOf<ToolConnection?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingOAuthConnection
        pendingOAuthConnection = null
        if (granted && pending != null) {
            viewModel.startOAuth(pending.connectionUid)
        } else if (!granted) {
            Toast.makeText(context, R.string.local_network_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.oauthLaunches.collect(onLaunchOAuth)
    }
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
        modifier = modifier,
        topBar = {
            ToolConnectionsTopBar(
                scrollBehavior = scrollBehavior,
                onNavigationClick = onNavigationClick,
                onAddClick = onAddConnectionClick,
                showAddAction = uiState.connections.isNotEmpty()
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.connections.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.loading_tool_connections),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            uiState.errorMessage != null && uiState.connections.isEmpty() -> {
                EmptyErrorState(
                    title = stringResource(R.string.tool_connections_load_error),
                    description = uiState.errorMessage.orEmpty(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    primaryActionLabel = stringResource(R.string.retry),
                    onPrimaryAction = viewModel::refresh,
                    isError = true
                )
            }

            uiState.connections.isEmpty() -> {
                EmptyErrorState(
                    title = stringResource(R.string.no_tool_connections_title),
                    description = stringResource(R.string.no_tool_connections),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    primaryActionLabel = stringResource(R.string.add_tool_connection),
                    onPrimaryAction = onAddConnectionClick
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                        .padding(bottom = 24.dp)
                ) {
                    val webSearchConnections = uiState.connections.filter { it.type != ToolConnectionType.MCP }
                    val mcpConnections = uiState.connections.filter { it.type == ToolConnectionType.MCP }
                    if (webSearchConnections.isNotEmpty()) {
                        ConnectionSection(
                            title = stringResource(R.string.web_search),
                            connections = webSearchConnections,
                            uiState = uiState,
                            onEditConnectionClick = onEditConnectionClick,
                            onOAuthClick = {},
                            onDeleteClick = { deletingConnection = it },
                            onClearRowError = viewModel::clearRowError
                        )
                    }
                    if (mcpConnections.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        ConnectionSection(
                            title = stringResource(R.string.mcp_servers),
                            connections = mcpConnections,
                            uiState = uiState,
                            onEditConnectionClick = onEditConnectionClick,
                            onOAuthClick = { connection ->
                                val needsPermission = connection.endpointUrl?.let(::requiresLocalNetworkAccess) == true
                                if (needsPermission &&
                                    Build.VERSION.SDK_INT >= 37 &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_LOCAL_NETWORK
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    pendingOAuthConnection = connection
                                    localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                                } else {
                                    viewModel.startOAuth(connection.connectionUid)
                                }
                            },
                            onDeleteClick = { deletingConnection = it },
                            onClearRowError = viewModel::clearRowError
                        )
                    }
                }
            }
        }
    }

    deletingConnection?.let { connection ->
        val deleteDescription = stringResource(R.string.delete_named_connection, connection.name)
        AlertDialog(
            title = { Text(stringResource(R.string.delete_tool_connection)) },
            text = { Text(stringResource(R.string.delete_tool_connection_confirmation, connection.name)) },
            onDismissRequest = { deletingConnection = null },
            confirmButton = {
                TextButton(
                    modifier = Modifier.semantics { contentDescription = deleteDescription },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteConnection(connection.connectionUid)
                        deletingConnection = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingConnection = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    uiState.errorMessage?.takeIf { uiState.connections.isNotEmpty() }?.let { message ->
        AlertDialog(
            title = { Text(stringResource(R.string.error)) },
            text = { Text(message) },
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::refresh) {
                    Text(stringResource(R.string.retry))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolConnectionEditorScreen(
    modifier: Modifier = Modifier,
    connectionUid: String? = null,
    viewModel: ToolConnectionsViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onSaveComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connection = connectionUid?.let { uid -> uiState.connections.firstOrNull { it.connectionUid == uid } }
    val isEditing = connectionUid != null
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )

    val editingFlow = connection?.let(ToolConnectionSetupFlow::editing)

    if (isEditing && uiState.isLoading) {
        ToolConnectionEditorStatusScreen(
            modifier = modifier,
            title = stringResource(R.string.edit_tool_connection),
            description = stringResource(R.string.loading_tool_connection),
            scrollBehavior = scrollBehavior,
            onNavigationClick = onNavigationClick,
            isLoading = true
        )
        return
    }
    if (isEditing && uiState.errorMessage != null && editingFlow == null) {
        ToolConnectionEditorStatusScreen(
            modifier = modifier,
            title = stringResource(R.string.tool_connections_load_error),
            description = uiState.errorMessage.orEmpty(),
            scrollBehavior = scrollBehavior,
            onNavigationClick = onNavigationClick,
            onRetry = viewModel::refresh,
            isError = true
        )
        return
    }
    if (isEditing && editingFlow == null) {
        ToolConnectionEditorStatusScreen(
            modifier = modifier,
            title = stringResource(R.string.tool_connection_not_found),
            description = stringResource(R.string.tool_connection_not_found_description),
            scrollBehavior = scrollBehavior,
            onNavigationClick = onNavigationClick,
            onRetry = viewModel::refresh
        )
        return
    }

    var setupFlow by remember(connection?.connectionUid) {
        mutableStateOf(editingFlow ?: ToolConnectionSetupFlow())
    }
    val title = stringResource(
        when {
            isEditing -> R.string.edit_tool_connection
            setupFlow.step == ToolConnectionSetupStep.CONNECTION_TYPE -> R.string.choose_connection_type
            setupFlow.step == ToolConnectionSetupStep.WEB_SEARCH_PROVIDER -> R.string.choose_search_provider
            setupFlow.step == ToolConnectionSetupStep.AUTHENTICATION -> R.string.authentication
            else -> R.string.connection_details
        }
    )
    var name by remember(connection?.connectionUid) { mutableStateOf(connection?.name.orEmpty()) }
    var alias by remember(connection?.connectionUid) { mutableStateOf(connection?.alias.orEmpty()) }
    var endpoint by remember(connection?.connectionUid) { mutableStateOf(connection?.endpointUrl.orEmpty()) }
    var authType by remember(connection?.connectionUid) { mutableStateOf(connection?.authType ?: ToolConnectionAuthType.NONE) }
    var credential by remember(connection?.connectionUid) { mutableStateOf("") }
    var oauthClientId by remember(connection?.connectionUid) { mutableStateOf(connection?.oauthClientId.orEmpty()) }
    var allowCleartext by remember(connection?.connectionUid) { mutableStateOf(connection?.allowCleartext == true) }
    var clearCredential by remember(connection?.connectionUid) { mutableStateOf(false) }
    val provider = setupFlow.provider
    val normalizedAlias = ToolConnectionsViewModel.normalizeAlias(alias)
    val isMcp = setupFlow.path == ToolConnectionSetupPath.MCP_SERVER
    val actualEndpoint = if (isMcp) endpoint else provider?.endpointUrl.orEmpty()
    val isEndpointValid = !isMcp || ToolConnectionsViewModel.isValidMcpEndpoint(actualEndpoint, allowCleartext)
    val detailsValid =
        name.isNotBlank() &&
            ToolConnectionsViewModel.isValidAlias(normalizedAlias) &&
            isEndpointValid
    val hasExistingCredential = connection?.secretRef != null
    val canPreserveCredential = connection?.let {
        hasExistingCredential &&
            it.type == provider?.type &&
            it.endpointUrl == actualEndpoint &&
            it.authType == authType
    } == true
    val credentialState = credentialEditState(
        hasExistingCredential = hasExistingCredential,
        canPreserveCredential = canPreserveCredential,
        credential = credential,
        clearCredential = clearCredential
    )
    val credentialValid = when {
        provider == null -> false
        !isMcp -> credentialState != CredentialEditState.MISSING
        authType == ToolConnectionAuthType.BEARER -> credentialState != CredentialEditState.MISSING
        else -> true
    }
    val isActionEnabled = !uiState.isSaving &&
        when (setupFlow.step) {
            ToolConnectionSetupStep.CONNECTION_TYPE -> setupFlow.canContinue
            ToolConnectionSetupStep.WEB_SEARCH_PROVIDER -> setupFlow.canContinue
            ToolConnectionSetupStep.DETAILS -> detailsValid && (!setupFlow.isSaveStep || credentialValid)
            ToolConnectionSetupStep.AUTHENTICATION -> detailsValid && credentialValid
        }
    val actionLabel = when {
        setupFlow.isSaveStep -> stringResource(R.string.save)
        else -> stringResource(R.string.next)
    }
    val hasPreviousStep = setupFlow.step == ToolConnectionSetupStep.AUTHENTICATION ||
        (!isEditing && setupFlow.step != ToolConnectionSetupStep.CONNECTION_TYPE)
    val navigateBack = {
        if (!uiState.isSaving) {
            if (hasPreviousStep) {
                setupFlow = setupFlow.back()
            } else {
                onNavigationClick()
            }
        }
    }
    BackHandler(enabled = hasPreviousStep || uiState.isSaving) {
        if (!uiState.isSaving) setupFlow = setupFlow.back()
    }
    val save = {
        provider?.let { selectedProvider ->
            viewModel.saveConnection(
                connection,
                selectedProvider,
                name,
                alias,
                actualEndpoint,
                authType,
                credential,
                oauthClientId,
                allowCleartext,
                clearCredential,
                onSaveComplete
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ToolConnectionEditorTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                onNavigationClick = navigateBack,
                navigationEnabled = !uiState.isSaving
            )
        },
        bottomBar = {
            ToolConnectionEditorBottomBar(
                label = actionLabel,
                isEnabled = isActionEnabled,
                isSaving = uiState.isSaving,
                errorMessage = uiState.errorMessage,
                onClick = {
                    if (setupFlow.isSaveStep) {
                        save()
                    } else {
                        setupFlow = setupFlow.next()
                    }
                }
            )
        }
    ) { innerPadding ->
        ToolConnectionStepContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
            setupFlow = setupFlow,
            connection = connection,
            provider = provider,
            name = name,
            alias = alias,
            endpoint = actualEndpoint,
            authType = authType,
            credential = credential,
            oauthClientId = oauthClientId,
            allowCleartext = allowCleartext,
            clearCredential = clearCredential,
            isMcp = isMcp,
            isEndpointValid = isEndpointValid,
            onPathSelected = { path ->
                val previousPath = setupFlow.path
                setupFlow = setupFlow.selectPath(path)
                if (previousPath != null && previousPath != path) {
                    name = ""
                    alias = ""
                    endpoint = ""
                    credential = ""
                    oauthClientId = ""
                    allowCleartext = false
                    clearCredential = false
                }
                if (path == ToolConnectionSetupPath.MCP_SERVER) {
                    if (name.isBlank()) name = "MCP Server"
                    if (alias.isBlank()) alias = "mcp_server"
                    if (previousPath != path) {
                        authType = connection?.authType ?: ToolConnectionAuthType.NONE
                    }
                }
            },
            onProviderSelected = { option ->
                val previousProvider = setupFlow.provider
                setupFlow = setupFlow.selectWebProvider(option)
                if (name.isBlank() || name == previousProvider?.label) name = option.label
                if (alias.isBlank() ||
                    alias == previousProvider?.label?.let(ToolConnectionsViewModel::normalizeAlias)
                ) {
                    alias = ToolConnectionsViewModel.normalizeAlias(option.label)
                }
                if (previousProvider != null && previousProvider.type != option.type) {
                    credential = ""
                    clearCredential = false
                }
                endpoint = option.endpointUrl
                authType = option.authType
            },
            onNameChange = { name = it },
            onAliasChange = { alias = it },
            onEndpointChange = { endpoint = it },
            onAuthTypeChange = { authType = it },
            onCredentialChange = {
                credential = it
                if (it.isNotBlank()) clearCredential = false
            },
            onOAuthClientIdChange = { oauthClientId = it },
            onAllowCleartextChange = { allowCleartext = it },
            onClearCredentialChange = {
                clearCredential = it
                if (it) credential = ""
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolConnectionEditorStatusScreen(
    modifier: Modifier,
    title: String,
    description: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationClick: () -> Unit,
    onRetry: (() -> Unit)? = null,
    isLoading: Boolean = false,
    isError: Boolean = false
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            ToolConnectionEditorTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                onNavigationClick = onNavigationClick,
                navigationEnabled = true
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                EmptyErrorState(
                    title = title,
                    description = description,
                    primaryActionLabel = onRetry?.let { stringResource(R.string.retry) },
                    onPrimaryAction = onRetry,
                    secondaryActionLabel = stringResource(R.string.go_back),
                    onSecondaryAction = onNavigationClick,
                    isError = isError
                )
            }
        }
    }
}

@Composable
private fun ToolConnectionEditorBottomBar(
    label: String,
    isEnabled: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onClick: () -> Unit
) {
    Surface(shadowElevation = 3.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                error(message)
                                liveRegion = LiveRegionMode.Assertive
                            }
                            .padding(bottom = 8.dp)
                    )
                }
                PrimaryLongButton(
                    enabled = isEnabled,
                    onClick = onClick,
                    text = if (isSaving) stringResource(R.string.saving) else label
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolConnectionsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationClick: () -> Unit,
    onAddClick: () -> Unit,
    showAddAction: Boolean
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.tool_connections),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = onNavigationClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        actions = {
            if (showAddAction) {
                IconButton(onClick = onAddClick) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.add_tool_connection))
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

internal enum class CredentialEditState {
    KEEP,
    REPLACE,
    CLEAR,
    MISSING
}

internal fun credentialEditState(
    hasExistingCredential: Boolean,
    canPreserveCredential: Boolean,
    credential: String,
    clearCredential: Boolean
): CredentialEditState = when {
    clearCredential && hasExistingCredential -> CredentialEditState.CLEAR
    credential.isNotBlank() -> CredentialEditState.REPLACE
    hasExistingCredential && canPreserveCredential -> CredentialEditState.KEEP
    else -> CredentialEditState.MISSING
}

@Composable
private fun ConnectionSection(
    title: String,
    connections: List<ToolConnection>,
    uiState: ToolConnectionsViewModel.ToolConnectionsUiState,
    onEditConnectionClick: (String) -> Unit,
    onOAuthClick: (ToolConnection) -> Unit,
    onDeleteClick: (ToolConnection) -> Unit,
    onClearRowError: (String) -> Unit
) {
    SettingsSection(title = title) {
        connections.forEach { connection ->
            ToolConnectionItem(
                connection = connection,
                isBusy = uiState.busyConnectionUid == connection.connectionUid,
                actionsEnabled = uiState.busyConnectionUid == null,
                errorMessage = uiState.rowErrorMessage.takeIf {
                    uiState.rowErrorConnectionUid == connection.connectionUid
                },
                onEditClick = { onEditConnectionClick(connection.connectionUid) },
                onOAuthClick = { onOAuthClick(connection) },
                onDeleteClick = { onDeleteClick(connection) },
                onClearError = { onClearRowError(connection.connectionUid) }
            )
        }
    }
}

@Composable
private fun ToolConnectionItem(
    connection: ToolConnection,
    isBusy: Boolean,
    actionsEnabled: Boolean,
    errorMessage: String?,
    onEditClick: () -> Unit,
    onOAuthClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClearError: () -> Unit
) {
    val editDescription = stringResource(R.string.edit_named_connection, connection.name)
    val deleteDescription = stringResource(R.string.delete_named_connection, connection.name)
    val connectDescription = stringResource(R.string.connect_with_oauth, connection.name)
    val progressDescription = stringResource(R.string.connection_action_in_progress)
    val credentialStatus = when {
        connection.authType == ToolConnectionAuthType.NONE -> stringResource(R.string.public_access)
        connection.authType == ToolConnectionAuthType.OAUTH && connection.secretRef == null -> stringResource(R.string.oauth_not_connected)
        connection.authType == ToolConnectionAuthType.OAUTH -> stringResource(R.string.oauth_connected)
        connection.secretRef == null -> stringResource(R.string.credential_not_set)
        else -> stringResource(R.string.credential_set)
    }
    Column {
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            headlineContent = { Text(connection.name, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    text = "${providerLabel(connection.type)} • ${connection.alias} • ${connection.endpointUrl.orEmpty()} • $credentialStatus",
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2
                )
            },
            trailingContent = {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = progressDescription },
                        strokeWidth = 2.dp
                    )
                } else {
                    Row {
                        if (connection.type == ToolConnectionType.MCP && connection.authType == ToolConnectionAuthType.OAUTH) {
                            TextButton(
                                modifier = Modifier.semantics { contentDescription = connectDescription },
                                enabled = actionsEnabled,
                                onClick = onOAuthClick
                            ) {
                                Text(stringResource(if (connection.secretRef == null) R.string.connect else R.string.reconnect))
                            }
                        }
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = editDescription },
                            enabled = actionsEnabled,
                            onClick = onEditClick
                        ) {
                            Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                        }
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = deleteDescription },
                            enabled = actionsEnabled,
                            onClick = onDeleteClick
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        errorMessage?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        error(message)
                        liveRegion = LiveRegionMode.Assertive
                    }
                    .padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearError) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
    }
}

@Composable
private fun ToolConnectionStepContent(
    modifier: Modifier = Modifier,
    setupFlow: ToolConnectionSetupFlow,
    connection: ToolConnection?,
    provider: ToolConnectionProvider?,
    name: String,
    alias: String,
    endpoint: String,
    authType: String,
    credential: String,
    oauthClientId: String,
    allowCleartext: Boolean,
    clearCredential: Boolean,
    isMcp: Boolean,
    isEndpointValid: Boolean,
    onPathSelected: (ToolConnectionSetupPath) -> Unit,
    onProviderSelected: (ToolConnectionProvider) -> Unit,
    onNameChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onAuthTypeChange: (String) -> Unit,
    onCredentialChange: (String) -> Unit,
    onOAuthClientIdChange: (String) -> Unit,
    onAllowCleartextChange: (Boolean) -> Unit,
    onClearCredentialChange: (Boolean) -> Unit
) {
    Column(modifier) {
        Spacer(modifier = Modifier.height(16.dp))
        when (setupFlow.step) {
            ToolConnectionSetupStep.CONNECTION_TYPE -> {
                CenteredEditorStep {
                    Text(
                        text = stringResource(R.string.choose_connection_type_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    DestinationCard(
                        title = stringResource(R.string.web_search),
                        description = stringResource(R.string.web_search_connection_description),
                        selected = setupFlow.path == ToolConnectionSetupPath.WEB_SEARCH,
                        onClick = { onPathSelected(ToolConnectionSetupPath.WEB_SEARCH) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DestinationCard(
                        title = stringResource(R.string.mcp_server),
                        description = stringResource(R.string.mcp_server_connection_description),
                        selected = setupFlow.path == ToolConnectionSetupPath.MCP_SERVER,
                        onClick = { onPathSelected(ToolConnectionSetupPath.MCP_SERVER) }
                    )
                }
            }

            ToolConnectionSetupStep.WEB_SEARCH_PROVIDER -> {
                CenteredEditorStep {
                    Text(
                        text = stringResource(R.string.choose_search_provider_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    ToolConnectionsViewModel.providers
                        .filterNot { it.type == ToolConnectionType.MCP }
                        .forEach { option ->
                            RadioItem(
                                title = option.label,
                                description = option.endpointUrl,
                                value = option.type,
                                selected = provider?.type == option.type
                            ) {
                                onProviderSelected(option)
                            }
                        }
                }
            }

            ToolConnectionSetupStep.DETAILS -> ConnectionDetailsStep(
                connection = connection,
                provider = provider,
                name = name,
                alias = alias,
                endpoint = endpoint,
                credential = credential,
                allowCleartext = allowCleartext,
                clearCredential = clearCredential,
                isMcp = isMcp,
                isEndpointValid = isEndpointValid,
                onNameChange = onNameChange,
                onAliasChange = onAliasChange,
                onEndpointChange = onEndpointChange,
                onCredentialChange = onCredentialChange,
                onAllowCleartextChange = onAllowCleartextChange,
                onClearCredentialChange = onClearCredentialChange
            )

            ToolConnectionSetupStep.AUTHENTICATION -> AuthenticationStep(
                connection = connection,
                authType = authType,
                credential = credential,
                oauthClientId = oauthClientId,
                clearCredential = clearCredential,
                onAuthTypeChange = onAuthTypeChange,
                onCredentialChange = onCredentialChange,
                onOAuthClientIdChange = onOAuthClientIdChange,
                onClearCredentialChange = onClearCredentialChange
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CenteredEditorStep(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = content
        )
    }
}

@Composable
private fun ConnectionDetailsStep(
    connection: ToolConnection?,
    provider: ToolConnectionProvider?,
    name: String,
    alias: String,
    endpoint: String,
    credential: String,
    allowCleartext: Boolean,
    clearCredential: Boolean,
    isMcp: Boolean,
    isEndpointValid: Boolean,
    onNameChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onCredentialChange: (String) -> Unit,
    onAllowCleartextChange: (Boolean) -> Unit,
    onClearCredentialChange: (Boolean) -> Unit
) {
    val normalizedAlias = ToolConnectionsViewModel.normalizeAlias(alias)
    val isAliasInvalid = !ToolConnectionsViewModel.isValidAlias(normalizedAlias)
    val aliasError = stringResource(if (alias.isBlank()) R.string.field_required else R.string.stable_alias_error)
    SettingsSection(title = stringResource(R.string.identity)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    if (isMcp) R.string.mcp_details_description else R.string.web_search_details_description,
                    provider?.label.orEmpty()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.name)) },
                isError = name.isBlank(),
                supportingText = if (name.isBlank()) {
                    { Text(stringResource(R.string.field_required)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .semantics {
                        if (isAliasInvalid) error(aliasError)
                    },
                value = alias,
                onValueChange = onAliasChange,
                label = { Text(stringResource(R.string.stable_alias)) },
                isError = isAliasInvalid,
                supportingText = {
                    Text(if (isAliasInvalid) aliasError else stringResource(R.string.stable_alias_description))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
    SettingsSection(title = stringResource(R.string.connection)) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isMcp) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = endpoint,
                    onValueChange = onEndpointChange,
                    label = { Text(stringResource(R.string.api_url)) },
                    isError = !isEndpointValid,
                    supportingText = if (!isEndpointValid) {
                        {
                            Text(
                                stringResource(
                                    if (endpoint.isBlank()) {
                                        R.string.field_required
                                    } else {
                                        R.string.mcp_endpoint_error
                                    }
                                )
                            )
                        }
                    } else {
                        null
                    },
                    singleLine = true
                )
                if (endpoint.startsWith("http://", ignoreCase = true)) {
                    LabeledCheckbox(
                        checked = allowCleartext,
                        label = stringResource(R.string.cleartext_mcp_warning),
                        contentDescription = stringResource(R.string.allow_cleartext_mcp_endpoint),
                        onCheckedChange = onAllowCleartextChange
                    )
                }
            } else {
                Text(
                    text = provider?.endpointUrl.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
    if (!isMcp) {
        Spacer(modifier = Modifier.height(24.dp))
        SettingsSection(title = stringResource(R.string.authentication)) {
            Column(modifier = Modifier.padding(16.dp)) {
                CredentialField(
                    connection = connection,
                    credential = credential,
                    label = stringResource(R.string.api_key),
                    clearCredential = clearCredential,
                    onCredentialChange = onCredentialChange,
                    onClearCredentialChange = onClearCredentialChange
                )
            }
        }
    }
}

@Composable
private fun AuthenticationStep(
    connection: ToolConnection?,
    authType: String,
    credential: String,
    oauthClientId: String,
    clearCredential: Boolean,
    onAuthTypeChange: (String) -> Unit,
    onCredentialChange: (String) -> Unit,
    onOAuthClientIdChange: (String) -> Unit,
    onClearCredentialChange: (Boolean) -> Unit
) {
    val bearerToken = stringResource(R.string.bearer_token)
    SettingsSection(title = stringResource(R.string.authentication)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.mcp_authentication_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            listOf(
                ToolConnectionAuthType.NONE to stringResource(R.string.public_access),
                ToolConnectionAuthType.BEARER to bearerToken,
                ToolConnectionAuthType.OAUTH to stringResource(R.string.oauth_pkce)
            ).forEach { (value, label) ->
                RadioItem(
                    title = label,
                    description = null,
                    value = value,
                    selected = authType == value
                ) {
                    onAuthTypeChange(value)
                }
            }
            if (authType == ToolConnectionAuthType.BEARER) {
                CredentialField(
                    connection = connection,
                    credential = credential,
                    label = bearerToken,
                    clearCredential = clearCredential,
                    onCredentialChange = onCredentialChange,
                    onClearCredentialChange = onClearCredentialChange
                )
            }
            if (authType == ToolConnectionAuthType.OAUTH) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    value = oauthClientId,
                    onValueChange = onOAuthClientIdChange,
                    label = { Text(stringResource(R.string.preregistered_client_id_optional)) },
                    supportingText = { Text(stringResource(R.string.dynamic_client_registration_hint)) },
                    singleLine = true
                )
                if (connection?.secretRef != null) {
                    LabeledCheckbox(
                        checked = clearCredential,
                        label = stringResource(R.string.clear_saved_credential),
                        contentDescription = stringResource(R.string.clear_saved_credential),
                        onCheckedChange = onClearCredentialChange
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialField(
    connection: ToolConnection?,
    credential: String,
    label: String,
    clearCredential: Boolean,
    onCredentialChange: (String) -> Unit,
    onClearCredentialChange: (Boolean) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        value = credential,
        onValueChange = onCredentialChange,
        label = { Text(label) },
        supportingText = {
            Text(
                if (connection?.secretRef == null) {
                    stringResource(R.string.credential_not_set)
                } else {
                    stringResource(R.string.blank_key_preserves_credential)
                }
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        visualTransformation = PasswordVisualTransformation()
    )
    if (connection?.secretRef != null) {
        LabeledCheckbox(
            checked = clearCredential,
            label = stringResource(R.string.clear_saved_credential),
            contentDescription = stringResource(R.string.clear_saved_credential),
            onCheckedChange = onClearCredentialChange
        )
    }
}

@Composable
private fun LabeledCheckbox(
    checked: Boolean,
    label: String,
    contentDescription: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription }
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(top = 8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            modifier = Modifier.padding(top = 12.dp),
            text = label
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolConnectionEditorTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationClick: () -> Unit,
    navigationEnabled: Boolean
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                enabled = navigationEnabled,
                onClick = onNavigationClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        scrollBehavior = scrollBehavior
    )
}

private fun providerLabel(type: String): String = ToolConnectionsViewModel.providers.firstOrNull { it.type == type }?.label ?: type
