package dev.chungjungsoo.gptmobile.presentation.ui.migrate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.icons.Block
import dev.chungjungsoo.gptmobile.presentation.icons.Complete
import dev.chungjungsoo.gptmobile.presentation.icons.Error
import dev.chungjungsoo.gptmobile.presentation.icons.Ready

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrateScreen(
    modifier: Modifier = Modifier,
    migrateViewModel: MigrateViewModel = hiltViewModel(),
    onFinish: () -> Unit
) {
    val uiState by migrateViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = {}) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            MigrationTitle()
            PlatformMigrationCard(
                status = uiState.platformState,
                numberOfPlatforms = uiState.numberOfPlatforms,
                errorMessage = uiState.platformErrorMessage,
                onMigrationClick = migrateViewModel::migratePlatform
            )
            ChatRoomMessageMigrationCard(
                status = uiState.chatState,
                numberOfChats = uiState.numberOfChats,
                errorMessage = uiState.chatErrorMessage,
                onMigrationClick = migrateViewModel::migrateChats
            )
            Spacer(modifier = Modifier.weight(1f))
            PrimaryLongButton(
                modifier = Modifier.padding(20.dp),
                enabled = uiState.platformState == MigrateViewModel.MigrationState.MIGRATED &&
                    uiState.chatState == MigrateViewModel.MigrationState.MIGRATED,
                onClick = onFinish,
                text = stringResource(R.string.done)
            )
        }
    }
}

@Composable
fun MigrationTitle(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = stringResource(R.string.migration_assistant),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = stringResource(R.string.migration_description),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun MigrationCard(
    status: MigrateViewModel.MigrationState,
    title: String,
    description: String,
    errorMessage: String? = null,
    onMigrationClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status == MigrateViewModel.MigrationState.MIGRATING) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                } else {
                    Icon(
                        imageVector = when (status) {
                            MigrateViewModel.MigrationState.READY -> Ready
                            MigrateViewModel.MigrationState.MIGRATED -> Complete
                            MigrateViewModel.MigrationState.ERROR -> Error
                            MigrateViewModel.MigrationState.BLOCKED -> Block
                            MigrateViewModel.MigrationState.MIGRATING -> Ready
                        },
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .semantics {
                                    error(message)
                                    liveRegion = LiveRegionMode.Assertive
                                }
                                .padding(top = 8.dp)
                        )
                    }
                }
                when (status) {
                    MigrateViewModel.MigrationState.READY -> TextButton(onClick = onMigrationClick) {
                        Text(stringResource(R.string.migrate))
                    }

                    MigrateViewModel.MigrationState.ERROR -> TextButton(onClick = onMigrationClick) {
                        Text(stringResource(R.string.retry))
                    }

                    MigrateViewModel.MigrationState.MIGRATING -> Unit

                    MigrateViewModel.MigrationState.MIGRATED -> Text(
                        text = stringResource(R.string.migrated),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    MigrateViewModel.MigrationState.BLOCKED -> Text(
                        text = stringResource(R.string.blocked),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PlatformMigrationCard(
    status: MigrateViewModel.MigrationState,
    numberOfPlatforms: Int,
    errorMessage: String? = null,
    onMigrationClick: () -> Unit
) {
    MigrationCard(
        status = status,
        title = stringResource(R.string.migrate_platform),
        description = stringResource(R.string.enabled_platform_numbers, numberOfPlatforms),
        errorMessage = if (status == MigrateViewModel.MigrationState.ERROR) {
            errorMessage?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.platform_migration_failed)
        } else {
            null
        },
        onMigrationClick = onMigrationClick
    )
}

@Composable
fun ChatRoomMessageMigrationCard(
    status: MigrateViewModel.MigrationState,
    numberOfChats: Int,
    errorMessage: String? = null,
    onMigrationClick: () -> Unit
) {
    MigrationCard(
        status = status,
        title = stringResource(R.string.migrate_chat),
        description = stringResource(R.string.existing_chats, numberOfChats),
        errorMessage = if (status == MigrateViewModel.MigrationState.ERROR) {
            errorMessage?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.chat_migration_failed)
        } else {
            null
        },
        onMigrationClick = onMigrationClick
    )
}
