package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import dev.chungjungsoo.gptmobile.data.network.ApiCredentialRotator
import dev.chungjungsoo.gptmobile.presentation.common.DestinationCard
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.util.PERMISSION_ACCESS_LOCAL_NETWORK
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior
import dev.chungjungsoo.gptmobile.util.requiresLocalNetworkAccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolConnectionsScreen(
    modifier: Modifier = Modifier,
    viewModel: ToolConnectionsViewModel = hiltViewModel(),
    onLaunchOAuth: (String) -> Unit = {},
    onMarketplaceClick: () -> Unit = {},
    onAddConnectionClick: () -> Unit,
    onEditConnectionClick: (String) -> Unit,
    onNavigationClick: () -> Unit
) {
    var settingsTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var pairingLink by remember { mutableStateOf<String?>(null) }
    val pairingContext = LocalContext.current
    pairingLink?.let { entered ->
        val valid = runCatching { dev.chungjungsoo.gptmobile.data.pairing.PairingLink.parse(entered) }.isSuccess
        AlertDialog(
            onDismissRequest = { pairingLink = null },
            title = { Text(stringResource(R.string.pair_server_title)) },
            text = { OutlinedTextField(entered, { pairingLink = it.take(4096) }, label = { Text(stringResource(R.string.pair_server_link)) }) },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    pairingContext.startActivity(Intent(pairingContext, ServerPairingActivity::class.java).setData(Uri.parse(entered)))
                    pairingLink = null
                }) { Text(stringResource(R.string.pair_server_review)) }
            },
            dismissButton = { TextButton(onClick = { pairingLink = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var browsingConnection by remember { mutableStateOf<ToolConnection?>(null) }
    browsingConnection?.let { connection -> McpBrowserDialog(connection, onDismiss = { browsingConnection = null }) }
    var permissionsConnection by remember { mutableStateOf<ToolConnection?>(null) }
    var deletingConnection by remember { mutableStateOf<ToolConnection?>(null) }
    var pendingOAuthConnection by remember { mutableStateOf<ToolConnection?>(null) }
    permissionsConnection?.let { selected ->
        ToolPolicyDialog(selected, onDismiss = { permissionsConnection = null }, onResetGrants = { viewModel.revokeToolGrants(selected.connectionUid) }) { policy, reads ->
            viewModel.savePolicy(selected, policy, reads)
            permissionsConnection = null
        }
    }
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
                onMarketplaceClick = onMarketplaceClick,
                onAddClick = onAddConnectionClick
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            androidx.compose.material3.TabRow(selectedTabIndex = if (settingsTab) 1 else 0) {
                androidx.compose.material3.Tab(selected = !settingsTab, onClick = { settingsTab = false }, text = { Text("Connections") })
                androidx.compose.material3.Tab(selected = settingsTab, onClick = { settingsTab = true }, text = { Text("Settings") })
            }
            if (settingsTab) {
                LocalToolsSettingsPanel(settingsOnly = true)
            } else {
                ToolInventorySummaryCard(
                    installedCount = uiState.connections.size,
                    remoteMcpCount = uiState.connections.count { it.type == ToolConnectionType.MCP },
                    onlineMcpCount = uiState.connectionHealth.values.count {
                        it.status == ToolConnectionHealthStatus.ONLINE
                    }
                )

                Text(
                    text = "Integrated tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                IntegratedToolsCard()
                TextButton(onClick = { pairingLink = "" }) { Text(stringResource(R.string.pair_server_title)) }

                Text(
                    text = "Installed connections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                // Marketplace Discover Banner
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    headlineContent = {
                        Text(
                            text = "Browse MCP Marketplace",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "Discover pre-configured MCP tools categorized into Free, Free with sign up, and Paid.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Storefront,
                            contentDescription = "MCP Marketplace",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = onMarketplaceClick) {
                            Text("Explore")
                        }
                    }
                )

                LocalToolsSettingsPanel()

                if (uiState.connections.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_tool_connections),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
                uiState.connections.forEach { connection ->
                    CollapsibleToolConnectionCard(
                        connection = connection,
                        onEditClick = { onEditConnectionClick(connection.connectionUid) },
                        onPermissionsClick = { permissionsConnection = connection },
                        onBrowseClick = { browsingConnection = connection },
                        onOAuthClick = {
                            val needsPermission = connection.endpointUrl?.let(::requiresLocalNetworkAccess) == true
                            if (needsPermission &&
                                Build.VERSION.SDK_INT >= 37 &&
                                ContextCompat.checkSelfPermission(context, PERMISSION_ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
                            ) {
                                pendingOAuthConnection = connection
                                localNetworkPermissionLauncher.launch(PERMISSION_ACCESS_LOCAL_NETWORK)
                            } else {
                                viewModel.startOAuth(connection.connectionUid)
                            }
                        },
                        onDeleteClick = { deletingConnection = connection },
                        health = uiState.connectionHealth[connection.connectionUid],
                        onRefreshHealth = { viewModel.probeConnections(listOf(connection)) }
                    )
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

    uiState.errorMessage?.let { message ->
        AlertDialog(
            title = { Text(stringResource(R.string.error)) },
            text = { Text(message) },
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun ToolProviderIcon(type: String, modifier: Modifier = Modifier) {
    val icon = when (type) {
        ToolConnectionType.MCP -> Icons.Filled.Hub
        ToolConnectionType.FIRECRAWL -> Icons.Filled.Language
        ToolConnectionType.PERPLEXITY -> Icons.Filled.Search
        ToolConnectionType.EXA -> Icons.Filled.Search
        else -> Icons.Filled.Cable
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = providerLabel(type),
            modifier = Modifier.padding(6.dp).size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CollapsibleToolConnectionCard(
    connection: ToolConnection,
    onEditClick: () -> Unit,
    onPermissionsClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onOAuthClick: () -> Unit,
    onDeleteClick: () -> Unit,
    health: ToolConnectionHealth?,
    onRefreshHealth: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "toolConnectionArrow"
    )

    val credentialStatus = when {
        connection.authType == ToolConnectionAuthType.NONE -> stringResource(R.string.public_access)
        connection.authType == ToolConnectionAuthType.OAUTH && connection.secretRef == null -> stringResource(R.string.oauth_not_connected)
        connection.authType == ToolConnectionAuthType.OAUTH -> stringResource(R.string.oauth_connected)
        connection.secretRef == null -> stringResource(R.string.credential_not_set)
        else -> stringResource(R.string.credential_set)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolProviderIcon(type = connection.type)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connection.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${providerLabel(connection.type)} • ${connection.alias}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (connection.type == ToolConnectionType.MCP) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ConnectionHealthLine(health)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        connection.authType == ToolConnectionAuthType.NONE -> MaterialTheme.colorScheme.surfaceContainerHighest
                        connection.authType == ToolConnectionAuthType.OAUTH && connection.secretRef != null -> MaterialTheme.colorScheme.primaryContainer
                        connection.secretRef != null -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    }
                ) {
                    Text(
                        text = credentialStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            connection.authType == ToolConnectionAuthType.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                            connection.authType == ToolConnectionAuthType.OAUTH && connection.secretRef != null -> MaterialTheme.colorScheme.onPrimaryContainer
                            connection.secretRef != null -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(arrowRotation)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    if (connection.type == ToolConnectionType.MCP) TextButton(onClick = onBrowseClick) { Text("Resources and prompts") }
                    if (connection.type == ToolConnectionType.MCP) TextButton(onClick = onPermissionsClick) { Text(stringResource(R.string.tool_policy)) }
                    connection.endpointUrl?.let { url ->
                        if (url.isNotBlank()) {
                            Text(
                                text = "Endpoint: $url",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Text(
                        text = "Authentication: ${connection.authType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (connection.type == ToolConnectionType.MCP) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = health?.message ?: "Health has not been checked yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (connection.type == ToolConnectionType.MCP) {
                            TextButton(onClick = onRefreshHealth) {
                                Text("Test")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (connection.type == ToolConnectionType.MCP && connection.authType == ToolConnectionAuthType.OAUTH) {
                            TextButton(onClick = onOAuthClick) {
                                Text(stringResource(if (connection.secretRef == null) R.string.connect else R.string.reconnect))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        IconButton(onClick = onEditClick) {
                            Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit Connection")
                        }
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete Connection",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolInventorySummaryCard(
    installedCount: Int,
    remoteMcpCount: Int,
    onlineMcpCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Tool workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Built-in tools are ready immediately; installed connections extend profiles with remote capabilities.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InventoryBadge("$installedCount installed", Modifier.weight(1f))
                InventoryBadge("$remoteMcpCount MCP", Modifier.weight(1f))
                InventoryBadge("$onlineMcpCount online", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InventoryBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun IntegratedToolsCard() {
    val tools = listOf(
        "Date & time",
        "Calculator",
        "Read files",
        "Read URL",
        "GitHub",
        "Device location"
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tools.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { name ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                Modifier.padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(
                                    Modifier.size(8.dp).background(Color(0xFF2E7D32), CircleShape)
                                )
                                Text(name, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ConnectionHealthLine(health: ToolConnectionHealth?) {
    val status = health?.status
    val dotColor = when (status) {
        ToolConnectionHealthStatus.ONLINE -> Color(0xFF2E7D32)
        ToolConnectionHealthStatus.LIMITED -> Color(0xFFF9A825)
        ToolConnectionHealthStatus.OFFLINE -> Color(0xFFC62828)
        ToolConnectionHealthStatus.CHECKING -> Color(0xFF1976D2)
        null -> MaterialTheme.colorScheme.outline
    }
    val label = when (status) {
        ToolConnectionHealthStatus.ONLINE -> "Online"
        ToolConnectionHealthStatus.LIMITED -> "Limited"
        ToolConnectionHealthStatus.OFFLINE -> "Disconnected"
        ToolConnectionHealthStatus.CHECKING -> "Checking"
        null -> "Not checked"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(9.dp).background(dotColor, CircleShape))
        Text(
            text = buildString {
                append(label)
                health?.toolCount?.let { append(" • $it tools") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

    if (isEditing && editingFlow == null) {
        Scaffold(
            modifier = modifier,
            topBar = {
                ToolConnectionEditorTopBar(
                    title = stringResource(R.string.edit_tool_connection),
                    scrollBehavior = scrollBehavior,
                    actionLabel = null,
                    isActionEnabled = false,
                    onNavigationClick = onNavigationClick,
                    onActionClick = {}
                )
            }
        ) { innerPadding ->
            Text(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(24.dp),
                text = stringResource(R.string.tool_connection_not_found)
            )
        }
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
    val isActionEnabled = when (setupFlow.step) {
        ToolConnectionSetupStep.CONNECTION_TYPE -> false
        ToolConnectionSetupStep.WEB_SEARCH_PROVIDER -> setupFlow.canContinue
        ToolConnectionSetupStep.DETAILS -> detailsValid && (!setupFlow.isSaveStep || credentialValid)
        ToolConnectionSetupStep.AUTHENTICATION -> detailsValid && credentialValid
    }
    val actionLabel = when {
        setupFlow.step == ToolConnectionSetupStep.CONNECTION_TYPE -> null
        setupFlow.isSaveStep -> stringResource(R.string.save)
        else -> stringResource(R.string.next)
    }
    val hasPreviousStep = setupFlow.step == ToolConnectionSetupStep.AUTHENTICATION ||
        (!isEditing && setupFlow.step != ToolConnectionSetupStep.CONNECTION_TYPE)
    val navigateBack = {
        if (hasPreviousStep) {
            setupFlow = setupFlow.back()
        } else {
            onNavigationClick()
        }
    }
    BackHandler(enabled = hasPreviousStep) {
        setupFlow = setupFlow.back()
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
                actionLabel = actionLabel,
                isActionEnabled = isActionEnabled,
                onNavigationClick = navigateBack,
                onActionClick = {
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
                .imePadding()
                .padding(horizontal = 24.dp),
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
                setupFlow = setupFlow.selectPath(path).next()
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

    uiState.errorMessage?.let { message ->
        AlertDialog(
            title = { Text(stringResource(R.string.error)) },
            text = { Text(message) },
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolConnectionsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationClick: () -> Unit,
    onMarketplaceClick: () -> Unit,
    onAddClick: () -> Unit
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
            IconButton(
                onClick = onMarketplaceClick
            ) {
                Icon(imageVector = Icons.Filled.Storefront, contentDescription = "MCP Marketplace")
            }
            IconButton(
                onClick = onAddClick
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.add_tool_connection))
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
                Text(
                    text = stringResource(R.string.choose_connection_type_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                DestinationCard(
                    title = stringResource(R.string.web_search),
                    description = stringResource(R.string.web_search_connection_description),
                    onClick = { onPathSelected(ToolConnectionSetupPath.WEB_SEARCH) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                DestinationCard(
                    title = stringResource(R.string.mcp_server),
                    description = stringResource(R.string.mcp_server_connection_description),
                    onClick = { onPathSelected(ToolConnectionSetupPath.MCP_SERVER) }
                )
            }

            ToolConnectionSetupStep.WEB_SEARCH_PROVIDER -> {
                Text(
                    text = stringResource(R.string.choose_search_provider_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
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
    val isAliasInvalid = alias.isNotBlank() && !ToolConnectionsViewModel.isValidAlias(normalizedAlias)
    val aliasError = stringResource(R.string.stable_alias_error)
    Text(
        text = stringResource(
            if (isMcp) R.string.mcp_details_description else R.string.web_search_details_description,
            provider?.label.orEmpty()
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.name)) },
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
    if (isMcp) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            value = endpoint,
            onValueChange = onEndpointChange,
            label = { Text(stringResource(R.string.api_url)) },
            isError = endpoint.isNotBlank() && !isEndpointValid,
            supportingText = if (endpoint.isNotBlank() && !isEndpointValid) {
                { Text(stringResource(R.string.mcp_endpoint_error)) }
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
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
    Text(
        text = stringResource(R.string.mcp_authentication_description),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp)
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

@Composable
private fun CredentialField(
    connection: ToolConnection?,
    credential: String,
    label: String,
    clearCredential: Boolean,
    onCredentialChange: (String) -> Unit,
    onClearCredentialChange: (Boolean) -> Unit
) {
    val initialKeys = remember(credential) {
        val parsed = ApiCredentialRotator.parseKeys(credential)
        if (parsed.isEmpty()) listOf("") else parsed
    }
    val credentialKeys = remember { mutableStateListOf<String>().apply { addAll(initialKeys) } }

    fun syncCredential() {
        onCredentialChange(ApiCredentialRotator.formatKeys(credentialKeys.toList()))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.multi_api_keys_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        credentialKeys.forEachIndexed { index, keyVal ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = keyVal,
                    onValueChange = { newVal ->
                        credentialKeys[index] = newVal
                        syncCredential()
                    },
                    label = {
                        Text(
                            if (credentialKeys.size > 1) {
                                stringResource(R.string.api_key_number, index + 1)
                            } else {
                                label
                            }
                        )
                    },
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
                if (credentialKeys.size > 1) {
                    IconButton(
                        onClick = {
                            credentialKeys.removeAt(index)
                            syncCredential()
                        },
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.remove_api_key),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    credentialKeys.add("")
                    syncCredential()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_api_key),
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(stringResource(R.string.add_api_key))
            }
        }
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
    actionLabel: String?,
    isActionEnabled: Boolean,
    onNavigationClick: () -> Unit,
    onActionClick: () -> Unit
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
                onClick = onNavigationClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        actions = {
            actionLabel?.let { label ->
                TextButton(
                    modifier = Modifier.semantics { contentDescription = label },
                    enabled = isActionEnabled,
                    onClick = onActionClick
                ) {
                    Text(label)
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

private fun providerLabel(type: String): String = ToolConnectionsViewModel.providers.firstOrNull { it.type == type }?.label ?: type
