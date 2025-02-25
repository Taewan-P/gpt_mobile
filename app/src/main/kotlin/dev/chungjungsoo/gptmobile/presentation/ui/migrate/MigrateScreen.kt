package dev.chungjungsoo.gptmobile.presentation.ui.migrate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.icons.Block
import dev.chungjungsoo.gptmobile.presentation.icons.Done
import dev.chungjungsoo.gptmobile.presentation.icons.Error

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrateScreen(
    modifier: Modifier = Modifier,
    onNavigate: (route: String) -> Unit
) {
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
                status = MigrateViewModel.MigrationState.READY,
                onMigrationClick = {}
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
fun PlatformMigrationCard(
    status: MigrateViewModel.MigrationState,
    onMigrationClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(16.dp)
    ) {
        Row {
            Icon(
                imageVector = when (status) {
                    MigrateViewModel.MigrationState.READY -> Done // TODO()
                    MigrateViewModel.MigrationState.MIGRATED -> Done
                    MigrateViewModel.MigrationState.ERROR -> Error
                    MigrateViewModel.MigrationState.BLOCKED -> Block
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.migrate_platform),
                modifier = Modifier
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = onMigrationClick
            ) {
                Text(stringResource(R.string.migrate))
            }
        }
    }
}

@Composable
fun ChatRoomMessageMigrationCard() {
}
