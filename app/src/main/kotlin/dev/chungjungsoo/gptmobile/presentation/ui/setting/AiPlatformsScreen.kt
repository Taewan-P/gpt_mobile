package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ProviderConnection
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import dev.chungjungsoo.gptmobile.data.model.parseProfileLabels
import dev.chungjungsoo.gptmobile.presentation.common.BeveledProfileLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPlatformsScreen(
    settingViewModel: SettingViewModelV2,
    onNavigationClick: () -> Unit,
    onNavigateToAddPlatform: () -> Unit,
    onNavigateToOpenRouterSettings: () -> Unit = {},
    onNavigateToProviderSettings: (String) -> Unit = {},
    onNavigateToPlatformSetting: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val platforms by settingViewModel.platformState.collectAsStateWithLifecycle()
    val providerConnections by settingViewModel.providerConnections.collectAsStateWithLifecycle()
    var freeExpanded by rememberSaveable { mutableStateOf(false) }
    var remoteExpanded by rememberSaveable { mutableStateOf(true) }
    var localExpanded by rememberSaveable { mutableStateOf(true) }
    var deletingProvider by remember { mutableStateOf<ProviderConnection?>(null) }
    deletingProvider?.let { connection ->
        val linked = platforms.filter { it.providerConnectionUid == connection.uid }
        AlertDialog(
            onDismissRequest = { deletingProvider = null },
            title = { Text("Delete ${connection.name}?") },
            text = { Text("This removes the provider, its saved API keys and ${linked.size} AI profiles${if (linked.isEmpty()) "" else ": " + linked.joinToString { it.name }}. Existing conversation history is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    deletingProvider = null
                    settingViewModel.deleteProviderConnection(connection)
                }) { Text("Delete provider", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingProvider = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.ai_platforms)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddPlatform,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_platform)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (platforms.isEmpty() && providerConnections.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.no_platforms_yet),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = onNavigateToAddPlatform) {
                                Text(stringResource(R.string.add_your_first_platform))
                            }
                        }
                    }
                }
            } else {
                val freeConnections = providerConnections.filter { it.compatibleType == ClientType.FREE }
                val freeStandalone = platforms.filter { it.compatibleType == ClientType.FREE && it.providerConnectionUid == null }
                if (freeConnections.isNotEmpty() || freeStandalone.isNotEmpty()) {
                    item {
                        Card(onClick = { freeExpanded = !freeExpanded }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.free_ai), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("No account required · ${freeConnections.size + freeStandalone.size} connections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(if (freeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (freeExpanded) "Collapse free AI" else "Expand free AI")
                            }
                        }
                    }
                    if (freeExpanded) {
                        items(freeConnections, key = { "connection:${it.uid}" }) { connection ->
                            ProviderConnectionGroupCard(
                                connection = connection,
                                onDelete = { deletingProvider = connection },
                                profiles = platforms.filter { it.providerConnectionUid == connection.uid },
                                onToggleFavorite = { settingViewModel.togglePlatformFavorite(it.id) },
                                onEdit = { onNavigateToPlatformSetting(it.uid) },
                                onProviderSettings = { onNavigateToProviderSettings(connection.uid) }
                            )
                        }
                        items(freeStandalone, key = { "profile:${it.id}" }) { platform ->
                            PlatformItemCard(
                                platform = platform,
                                onToggleFavorite = { settingViewModel.togglePlatformFavorite(platform.id) },
                                onEdit = { onNavigateToPlatformSetting(platform.uid) }
                            )
                        }
                    }
                }
                val localTypes = setOf(ClientType.LITERT_LM, ClientType.LLAMA, ClientType.OLLAMA)
                val localConnections = providerConnections.filter { it.compatibleType in localTypes }
                val remoteConnections = providerConnections.filter { it.compatibleType != ClientType.FREE && it.compatibleType !in localTypes }
                val localStandalone = platforms.filter { it.providerConnectionUid == null && it.compatibleType in localTypes }
                val remoteStandalone = platforms.filter { it.providerConnectionUid == null && it.compatibleType != ClientType.FREE && it.compatibleType !in localTypes }

                if (remoteConnections.isNotEmpty() || remoteStandalone.isNotEmpty()) {
                    item {
                        ProviderCategoryHeader(
                            title = "Remote",
                            count = remoteConnections.size + remoteStandalone.size,
                            expanded = remoteExpanded,
                            icon = Icons.Default.Cloud,
                            onClick = { remoteExpanded = !remoteExpanded }
                        )
                    }
                    if (remoteExpanded) {
                        items(remoteConnections, key = { "remote-connection:${it.uid}" }) { connection ->
                            ProviderConnectionGroupCard(
                                connection = connection,
                                onDelete = { deletingProvider = connection },
                                profiles = platforms.filter { it.providerConnectionUid == connection.uid },
                                onToggleFavorite = { settingViewModel.togglePlatformFavorite(it.id) },
                                onEdit = { onNavigateToPlatformSetting(it.uid) },
                                onProviderSettings = { onNavigateToProviderSettings(connection.uid) },
                                onSpecialSettings = if (connection.compatibleType == ClientType.OPENROUTER) onNavigateToOpenRouterSettings else null
                            )
                        }
                        items(remoteStandalone, key = { "remote-profile:${it.id}" }) { platform ->
                            PlatformItemCard(platform, { settingViewModel.togglePlatformFavorite(platform.id) }, { onNavigateToPlatformSetting(platform.uid) })
                        }
                    }
                }

                if (localConnections.isNotEmpty() || localStandalone.isNotEmpty()) {
                    item {
                        ProviderCategoryHeader(
                            title = "Local",
                            count = localConnections.size + localStandalone.size,
                            expanded = localExpanded,
                            icon = Icons.Default.Dns,
                            onClick = { localExpanded = !localExpanded }
                        )
                    }
                    if (localExpanded) {
                        items(localConnections, key = { "local-connection:${it.uid}" }) { connection ->
                            ProviderConnectionGroupCard(
                                connection = connection,
                                onDelete = { deletingProvider = connection },
                                profiles = platforms.filter { it.providerConnectionUid == connection.uid },
                                onToggleFavorite = { settingViewModel.togglePlatformFavorite(it.id) },
                                onEdit = { onNavigateToPlatformSetting(it.uid) },
                                onProviderSettings = { onNavigateToProviderSettings(connection.uid) }
                            )
                        }
                        items(localStandalone, key = { "local-profile:${it.id}" }) { platform ->
                            PlatformItemCard(platform, { settingViewModel.togglePlatformFavorite(platform.id) }, { onNavigateToPlatformSetting(platform.uid) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCategoryHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$count configured", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse $title providers" else "Expand $title providers")
        }
    }
}

@Composable
private fun ProviderConnectionGroupCard(
    connection: ProviderConnection,
    profiles: List<PlatformV2>,
    onToggleFavorite: (PlatformV2) -> Unit,
    onDelete: () -> Unit,
    onEdit: (PlatformV2) -> Unit,
    onProviderSettings: (() -> Unit)? = null,
    onSpecialSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(connection.uid) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).clickable { expanded = !expanded }) {
                    Text(
                        text = connection.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (connection.compatibleType == ClientType.FREE) {
                            "${FreeAiProvider.fromApiUrl(connection.apiUrl)?.displayName ?: "Free"} · No account · Memory off"
                        } else {
                            buildString {
                                append(connection.compatibleType.name)
                                connection.apiUrl.takeIf(String::isNotBlank)?.let {
                                    append(" • ")
                                    append(it)
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.provider_profiles_count, profiles.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (onProviderSettings != null) {
                        TextButton(onClick = onProviderSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(" Provider settings")
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    Text(if (expanded) "Hide profiles" else "Show profiles")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete provider ${connection.name}", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (expanded) onSpecialSettings?.let { action -> TextButton(onClick = action) { Text("OpenRouter options") } }
            if (expanded && connection.hasCredential) {
                Text(
                    text = stringResource(R.string.credential_saved),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                if (profiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_profiles_for_connection),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profiles.forEach { platform ->
                            PlatformItemCard(
                                platform = platform,
                                onToggleFavorite = { onToggleFavorite(platform) },
                                onEdit = { onEdit(platform) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlatformItemCard(
    platform: PlatformV2,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val labelsList = remember(platform.labels) { parseProfileLabels(platform.labels) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onEdit,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite()
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = platform.name.ifBlank { platform.compatibleType.name },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (platform.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Favorite",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${platform.compatibleType.name} • ${platform.model.ifBlank { "Default model" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (labelsList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        labelsList.forEach { label ->
                            BeveledProfileLabel(label = label)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(if (platform.enabled) "Active" else "Disabled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.ChevronRight, contentDescription = "Profile settings")
            }
        }
    }
}
