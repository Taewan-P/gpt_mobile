package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.common.Route
import dev.chungjungsoo.gptmobile.presentation.icons.Done

@Composable
fun SetupCompleteScreen(
    modifier: Modifier = Modifier,
    platforms: List<PlatformV2>,
    onNavigate: (route: String) -> Unit,
    onBackAction: () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SetupAppBar(onBackAction) },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                PrimaryLongButton(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    onClick = { onNavigate(Route.CHAT_LIST) },
                    text = stringResource(R.string.start_chatting)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SetupCompleteText(platforms = platforms)
                SetupCompleteLogo()
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview
@Composable
private fun SetupCompleteText(
    modifier: Modifier = Modifier,
    platforms: List<PlatformV2> = emptyList()
) {
    val configuredNames = platforms.filter(PlatformV2::enabled).joinToString(", ", transform = PlatformV2::name)
    val pendingNames = platforms.filter { !it.enabled && it.compatibleType == ClientType.LITERT_LM }.joinToString(", ", transform = PlatformV2::name)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.setup_complete),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(R.string.setup_complete_description),
            style = MaterialTheme.typography.bodyLarge
        )
        if (configuredNames.isNotBlank()) {
            Text(
                text = stringResource(R.string.configured_providers, configuredNames),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        if (pendingNames.isNotBlank()) {
            Text(
                text = stringResource(R.string.pending_providers, pendingNames),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Preview
@Composable
private fun SetupCompleteLogo(modifier: Modifier = Modifier) {
    Image(
        imageVector = Done,
        contentDescription = stringResource(R.string.setup_complete_logo),
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(32.dp),
        contentScale = ContentScale.Fit
    )
}
