package dev.chungjungsoo.gptmobile.presentation.ui.migrate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.icons.Block
import dev.chungjungsoo.gptmobile.presentation.icons.Complete
import dev.chungjungsoo.gptmobile.presentation.icons.Error
import dev.chungjungsoo.gptmobile.presentation.icons.Migrating
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
                onMigrationClick = migrateViewModel::migratePlatform
            )
            ChatRoomMessageMigrationCard(
                status = uiState.chatState,
                numberOfChats = uiState.numberOfChats,
                onMigrationClick = migrateViewModel::migrateChats
            )
            Spacer(modifier = Modifier.weight(1f))
            PrimaryLongButton(
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
    title: @Composable String,
    description: @Composable String,
    onMigrationClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (status) {
                    MigrateViewModel.MigrationState.READY -> Ready
                    MigrateViewModel.MigrationState.MIGRATING -> Migrating
                    MigrateViewModel.MigrationState.MIGRATED -> Complete
                    MigrateViewModel.MigrationState.ERROR -> Error
                    MigrateViewModel.MigrationState.BLOCKED -> Block
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Column {
                Text(
                    text = title,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onMigrationClick,
                enabled = status == MigrateViewModel.MigrationState.READY || status == MigrateViewModel.MigrationState.ERROR
            ) {
                when (status) {
                    MigrateViewModel.MigrationState.READY, MigrateViewModel.MigrationState.BLOCKED -> Text(stringResource(R.string.migrate))
                    MigrateViewModel.MigrationState.MIGRATING -> Text(stringResource(R.string.migrating))
                    MigrateViewModel.MigrationState.MIGRATED -> Text(stringResource(R.string.migrated))
                    MigrateViewModel.MigrationState.ERROR -> Text(stringResource(R.string.error))
                }
            }
        }
    }
}

@Composable
fun PlatformMigrationCard(
    status: MigrateViewModel.MigrationState,
    numberOfPlatforms: Int,
    onMigrationClick: () -> Unit
) {
    MigrationCard(
        status = status,
        title = stringResource(R.string.migrate_platform),
        description = stringResource(R.string.enabled_platform_numbers, numberOfPlatforms),
        onMigrationClick = onMigrationClick
    )
}

@Composable
fun ChatRoomMessageMigrationCard(
    status: MigrateViewModel.MigrationState,
    numberOfChats: Int,
    onMigrationClick: () -> Unit
) {
    MigrationCard(
        status = status,
        title = stringResource(R.string.migrate_chat),
        description = stringResource(R.string.existing_chats, numberOfChats),
        onMigrationClick = onMigrationClick
    )
}
