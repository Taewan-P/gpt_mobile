package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlatformScreen(
    modifier: Modifier = Modifier,
    onNavigationClick: () -> Unit,
    onSave: (PlatformV2) -> Unit
) {
    var platformName by remember { mutableStateOf("") }
    var selectedClientType by remember { mutableStateOf(ClientType.OPENAI) }
    var clientTypeExpanded by remember { mutableStateOf(false) }
    var apiUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_platform)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header text
            Text(
                text = stringResource(R.string.add_platform_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Platform Name
            OutlinedTextField(
                value = platformName,
                onValueChange = { platformName = it },
                label = { Text(stringResource(R.string.platform_name)) },
                placeholder = { Text(stringResource(R.string.platform_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text(stringResource(R.string.platform_name_supporting))
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Client Type Dropdown
            ExposedDropdownMenuBox(
                expanded = clientTypeExpanded,
                onExpandedChange = { clientTypeExpanded = it }
            ) {
                OutlinedTextField(
                    value = getClientTypeName(selectedClientType),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.api_type)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientTypeExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    supportingText = {
                        Text(getClientTypeDescription(selectedClientType))
                    }
                )

                ExposedDropdownMenu(
                    expanded = clientTypeExpanded,
                    onDismissRequest = { clientTypeExpanded = false }
                ) {
                    ClientType.entries.forEach { clientType ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = getClientTypeName(clientType),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = getClientTypeDescription(clientType),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedClientType = clientType
                                // Set default API URL based on client type
                                apiUrl = when (clientType) {
                                    ClientType.OPENAI -> ModelConstants.OPENAI_API_URL
                                    ClientType.ANTHROPIC -> ModelConstants.ANTHROPIC_API_URL
                                    ClientType.GOOGLE -> ModelConstants.GOOGLE_API_URL
                                    ClientType.GROQ -> ModelConstants.GROQ_API_URL
                                    ClientType.OLLAMA -> "http://localhost:11434"
                                    ClientType.OPENROUTER -> "https://openrouter.ai/api"
                                    ClientType.CUSTOM -> ""
                                }
                                clientTypeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // API URL
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text(stringResource(R.string.api_url)) },
                placeholder = { Text(stringResource(R.string.api_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = selectedClientType != ClientType.GOOGLE
            )

            Spacer(modifier = Modifier.height(24.dp))

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.api_key)) },
                placeholder = { Text(stringResource(R.string.api_key_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text(stringResource(R.string.api_key_supporting))
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Model
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text(stringResource(R.string.model)) },
                placeholder = { Text(getModelPlaceholder(selectedClientType)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text(stringResource(R.string.model_supporting))
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Action buttons
            Button(
                onClick = {
                    val platform = PlatformV2(
                        name = platformName.trim(),
                        compatibleType = selectedClientType,
                        enabled = true,
                        apiUrl = apiUrl.trim(),
                        token = apiKey.trim().takeIf { it.isNotEmpty() },
                        model = model.trim(),
                        temperature = 1.0f,
                        topP = 1.0f,
                        systemPrompt = ModelConstants.DEFAULT_PROMPT,
                        stream = true,
                        reasoning = false,
                        timeout = 30
                    )
                    onSave(platform)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = platformName.isNotBlank() && apiUrl.isNotBlank() && model.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNavigationClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cancel))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun getClientTypeName(clientType: ClientType): String = when (clientType) {
    ClientType.OPENAI -> "OpenAI"
    ClientType.ANTHROPIC -> "Anthropic"
    ClientType.GOOGLE -> "Google"
    ClientType.GROQ -> "Groq"
    ClientType.OLLAMA -> "Ollama"
    ClientType.OPENROUTER -> "OpenRouter"
    ClientType.CUSTOM -> stringResource(R.string.custom)
}

@Composable
private fun getClientTypeDescription(clientType: ClientType): String = when (clientType) {
    ClientType.OPENAI -> stringResource(R.string.client_type_openai_desc)
    ClientType.ANTHROPIC -> stringResource(R.string.client_type_anthropic_desc)
    ClientType.GOOGLE -> stringResource(R.string.client_type_google_desc)
    ClientType.GROQ -> stringResource(R.string.client_type_groq_desc)
    ClientType.OLLAMA -> stringResource(R.string.client_type_ollama_desc)
    ClientType.OPENROUTER -> stringResource(R.string.client_type_openrouter_desc)
    ClientType.CUSTOM -> stringResource(R.string.client_type_custom_desc)
}

@Composable
private fun getModelPlaceholder(clientType: ClientType): String = when (clientType) {
    ClientType.OPENAI -> "gpt-4o"
    ClientType.ANTHROPIC -> "claude-3-5-sonnet-20241022"
    ClientType.GOOGLE -> "gemini-1.5-pro"
    ClientType.GROQ -> "llama-3.1-70b-versatile"
    ClientType.OLLAMA -> "llama2"
    ClientType.OPENROUTER -> "openai/gpt-4o"
    ClientType.CUSTOM -> stringResource(R.string.model_name)
}
