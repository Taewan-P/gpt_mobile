package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.presentation.common.DestinationCard

private data class PlatformTypeInfo(
    val clientType: ClientType,
    val titleResId: Int,
    val descriptionResId: Int
)

private data class SetupProviderGroup(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val items: List<PlatformTypeInfo>
)

private val setupProviderGroups = listOf(
    SetupProviderGroup(
        title = "Free",
        description = "No account or API key. Memory stays off.",
        icon = Icons.Default.Cloud,
        items = listOf(PlatformTypeInfo(ClientType.FREE, R.string.free_ai, R.string.free_ai_description))
    ),
    SetupProviderGroup(
        title = "Hosted AI providers",
        description = "Connect directly to managed cloud APIs.",
        icon = Icons.Default.Cloud,
        items = listOf(
            PlatformTypeInfo(ClientType.OPENAI, R.string.openai, R.string.openai_description),
            PlatformTypeInfo(ClientType.ANTHROPIC, R.string.anthropic, R.string.anthropic_description),
            PlatformTypeInfo(ClientType.GOOGLE, R.string.google, R.string.google_description),
            PlatformTypeInfo(ClientType.NVIDIA, R.string.nvidia, R.string.nvidia_description),
            PlatformTypeInfo(ClientType.GROQ, R.string.groq, R.string.groq_description)
        )
    ),
    SetupProviderGroup(
        title = "AI routing platforms",
        description = "Use one connection to access and discover many models.",
        icon = Icons.Default.Route,
        items = listOf(
            PlatformTypeInfo(ClientType.OPENROUTER, R.string.openrouter, R.string.openrouter_description)
        )
    ),
    SetupProviderGroup(
        title = "Local & self-hosted",
        description = "Run models on your phone or connect to infrastructure you control.",
        icon = Icons.Default.Memory,
        items = listOf(
            PlatformTypeInfo(ClientType.LITERT_LM, R.string.litert_lm, R.string.litert_lm_description),
            PlatformTypeInfo(ClientType.OLLAMA, R.string.ollama, R.string.ollama_description),
            PlatformTypeInfo(ClientType.LLAMA, R.string.llama, R.string.client_type_llama_desc)
        )
    ),
    SetupProviderGroup(
        title = "Custom compatible API",
        description = "Connect another endpoint when you already know its compatibility mode.",
        icon = Icons.Default.Tune,
        items = listOf(
            PlatformTypeInfo(ClientType.CUSTOM, R.string.custom_provider, R.string.custom_provider_description)
        )
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ProviderChoiceHero()
            }

            item {
                DestinationCard(title = "Free AI", description = "No account or API key. Memory stays off.", onClick = {
                    setupViewModel.selectClientType(ClientType.FREE)
                    onPlatformTypeSelected()
                })
            }
            item {
                DestinationCard(title = "On this device", description = "Get a local model from the marketplace.", onClick = {
                    setupViewModel.selectClientType(ClientType.LITERT_LM)
                    onPlatformTypeSelected()
                })
            }
            item {
                dev.chungjungsoo.gptmobile.presentation.common.AdvancedOptions("Connect a cloud provider") {
                    setupProviderGroups.flatMap { it.items }.filter { it.clientType in setOf(ClientType.NVIDIA, ClientType.OPENAI, ClientType.ANTHROPIC, ClientType.GOOGLE, ClientType.GROQ, ClientType.OPENROUTER) }.forEach { item ->
                        DestinationCard(title = stringResource(item.titleResId), description = "Use your API key", onClick = {
                            setupViewModel.selectClientType(item.clientType)
                            onPlatformTypeSelected()
                        })
                    }
                }
            }
            item {
                dev.chungjungsoo.gptmobile.presentation.common.AdvancedOptions("Advanced · custom and self-hosted") {
                    setupProviderGroups.flatMap { it.items }.filter { it.clientType in setOf(ClientType.CUSTOM, ClientType.OLLAMA, ClientType.LLAMA) }.forEach { item ->
                        DestinationCard(title = stringResource(item.titleResId), description = "Custom endpoint", onClick = {
                            setupViewModel.selectClientType(item.clientType)
                            onPlatformTypeSelected()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderChoiceHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                ) {
                    Icon(
                        Icons.Default.Computer,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "Choose how to chat",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "Start with one option. You can add more later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
