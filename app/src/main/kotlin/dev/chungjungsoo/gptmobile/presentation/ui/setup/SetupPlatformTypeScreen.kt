package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.presentation.common.DestinationCard

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
    ),
    PlatformTypeInfo(
        clientType = ClientType.LITERT_LM,
        titleResId = R.string.litert_lm,
        descriptionResId = R.string.litert_lm_description
    )
)

@Composable
fun SetupPlatformTypeScreen(
    modifier: Modifier = Modifier,
    setupViewModel: SetupViewModelV2 = hiltViewModel(),
    onPlatformTypeSelected: () -> Unit,
    onBackAction: () -> Unit
) {
    val selectedClientType by setupViewModel.selectedClientType.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SetupAppBar(onBackAction) }
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
            ) {
                PlatformTypeHeader()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(platformTypes, key = { it.clientType }) { platformTypeInfo ->
                        DestinationCard(
                            title = stringResource(platformTypeInfo.titleResId),
                            description = stringResource(platformTypeInfo.descriptionResId),
                            selected = selectedClientType == platformTypeInfo.clientType,
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
}

@Composable
private fun PlatformTypeHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.choose_platform_type),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(R.string.choose_platform_type_description),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
