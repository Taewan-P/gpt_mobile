package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem

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

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeTopAppBar(
                title = { Text(server?.name ?: stringResource(R.string.mcp_server_detail)) },
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

            ServerDetails(server)

            Button(
                onClick = viewModel::testConnection,
                enabled = !uiState.isTesting,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.test_connection))
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
                    viewModel.delete(onNavigationClick)
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
private fun ServerDetails(server: McpServerConfig) {
    SettingItem(
        title = stringResource(R.string.server_name),
        description = server.name,
        enabled = false,
        onItemClick = {},
        showTrailingIcon = false,
        showLeadingIcon = false
    )
    SettingItem(
        title = stringResource(R.string.transport_type),
        description = server.type.name,
        enabled = false,
        onItemClick = {},
        showTrailingIcon = false,
        showLeadingIcon = false
    )
    server.url?.let {
        SettingItem(
            title = stringResource(R.string.server_url),
            description = it,
            enabled = false,
            onItemClick = {},
            showTrailingIcon = false,
            showLeadingIcon = false
        )
    }
    server.command?.let {
        SettingItem(
            title = stringResource(R.string.command),
            description = it,
            enabled = false,
            onItemClick = {},
            showTrailingIcon = false,
            showLeadingIcon = false
        )
    }
}
