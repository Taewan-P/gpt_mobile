package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ClientType

data class PlatformTypeInfo(
    val clientType: ClientType,
    val titleResId: Int,
    val descriptionResId: Int
)

private val platformTypes = listOf(
    PlatformTypeInfo(
        clientType = ClientType.OPENAI,
        titleResId = R.string.openai,
        descriptionResId = R.string.openai_description
    ),
    PlatformTypeInfo(
        clientType = ClientType.ANTHROPIC,
        titleResId = R.string.anthropic,
        descriptionResId = R.string.anthropic_description
    ),
    PlatformTypeInfo(
        clientType = ClientType.GOOGLE,
        titleResId = R.string.google,
        descriptionResId = R.string.google_description
    ),
    PlatformTypeInfo(
        clientType = ClientType.GROQ,
        titleResId = R.string.groq,
        descriptionResId = R.string.groq_description
    ),
    PlatformTypeInfo(
        clientType = ClientType.OLLAMA,
        titleResId = R.string.ollama,
        descriptionResId = R.string.ollama_description
    ),
    PlatformTypeInfo(
        clientType = ClientType.OPENROUTER,
        titleResId = R.string.openrouter,
        descriptionResId = R.string.openrouter_description
    ),
    PlatformTypeInfo(
        clientType = ClientType.CUSTOM,
        titleResId = R.string.custom_provider,
        descriptionResId = R.string.custom_provider_description
    )
)

@Composable
fun SetupPlatformTypeScreen(
    modifier: Modifier = Modifier,
    setupViewModel: SetupViewModelV2 = hiltViewModel(),
    onPlatformTypeSelected: () -> Unit,
    onBackAction: () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SetupAppBar(onBackAction) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Header
            PlatformTypeHeader()

            // Platform type list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(platformTypes) { platformTypeInfo ->
                    PlatformTypeCard(
                        platformTypeInfo = platformTypeInfo,
                        onClick = {
                            setupViewModel.selectClientType(platformTypeInfo.clientType)
                            onPlatformTypeSelected()
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PlatformTypeHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = stringResource(R.string.choose_platform_type),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = stringResource(R.string.choose_platform_type_description),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun PlatformTypeCard(
    modifier: Modifier = Modifier,
    platformTypeInfo: PlatformTypeInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Platform info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(platformTypeInfo.titleResId),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(platformTypeInfo.descriptionResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Arrow icon
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
