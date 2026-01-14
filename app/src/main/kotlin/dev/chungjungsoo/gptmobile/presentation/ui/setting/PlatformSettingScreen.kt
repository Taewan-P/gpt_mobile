package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: PlatformSettingViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val platform by settingViewModel.platformState.collectAsStateWithLifecycle()
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()
    val isDeleted by settingViewModel.isDeleted.collectAsStateWithLifecycle()

    LaunchedEffect(isDeleted) {
        if (isDeleted) {
            onNavigationClick()
        }
    }

    platform?.let { platformData ->
        Scaffold(
            modifier = modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                PlatformTopAppBar(
                    title = platformData.name,
                    onNavigationClick = onNavigationClick,
                    onDeleteClick = settingViewModel::openDeleteDialog,
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            Column(
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
            ) {
                PreferenceSwitchWithContainer(
                    title = stringResource(R.string.enable_api),
                    isChecked = platformData.enabled
                ) { settingViewModel.toggleEnabled() }
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.platform_name),
                    description = platformData.name,
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openPlatformNameDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Label,
                            contentDescription = stringResource(R.string.platform_name_icon)
                        )
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.api_url),
                    description = platformData.apiUrl,
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openApiUrlDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_link),
                            contentDescription = stringResource(R.string.url_icon)
                        )
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.api_key),
                    description = if (platformData.token.isNullOrEmpty()) {
                        stringResource(R.string.token_not_set)
                    } else {
                        stringResource(R.string.token_set, platformData.token!![0])
                    },
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openApiTokenDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_key),
                            contentDescription = stringResource(R.string.key_icon)
                        )
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.api_model),
                    description = platformData.model,
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openApiModelDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_model),
                            contentDescription = stringResource(R.string.model_icon)
                        )
                    }
                )
                // Disable temperature and top_p when reasoning is enabled for OpenAI
                val isReasoningDisabled = platformData.compatibleType == ClientType.OPENAI && platformData.reasoning
                val notSetText = stringResource(R.string.not_set)
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.temperature),
                    description = platformData.temperature?.toString() ?: notSetText,
                    enabled = platformData.enabled && !isReasoningDisabled,
                    onItemClick = settingViewModel::openTemperatureDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_temperature),
                            contentDescription = stringResource(R.string.temperature_icon)
                        )
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.top_p),
                    description = platformData.topP?.toString() ?: notSetText,
                    enabled = platformData.enabled && !isReasoningDisabled,
                    onItemClick = settingViewModel::openTopPDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_chart),
                            contentDescription = stringResource(R.string.top_p_icon)
                        )
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.system_prompt),
                    description = platformData.systemPrompt,
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openSystemPromptDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_instructions),
                            contentDescription = stringResource(R.string.system_prompt_icon)
                        )
                    }
                )
                ExtendedThinkingSwitch(
                    modifier = Modifier.height(64.dp),
                    enabled = platformData.enabled,
                    isChecked = platformData.reasoning,
                    onCheckedChange = { settingViewModel.toggleReasoning() }
                )

                PlatformNameDialog(dialogState, platformData.name, settingViewModel)
                APIUrlDialog(dialogState, platformData.apiUrl, settingViewModel)
                APIKeyDialog(dialogState, settingViewModel)
                ModelDialog(dialogState, platformData.model, settingViewModel)
                TemperatureDialog(dialogState, platformData.temperature, settingViewModel)
                TopPDialog(dialogState, platformData.topP, settingViewModel)
                SystemPromptDialog(dialogState, platformData.systemPrompt ?: "", settingViewModel)
                DeletePlatformDialog(dialogState, settingViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformTopAppBar(
    title: String,
    onNavigationClick: () -> Unit,
    onDeleteClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    var showMenu by remember { mutableStateOf(false) }

    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = onNavigationClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.more_options)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_platform)) },
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    }
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun ExtendedThinkingSwitch(
    modifier: Modifier,
    enabled: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val clickableModifier = if (enabled) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = { onCheckedChange(!isChecked) })
            .padding(horizontal = 8.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    }
    val colors = ListItemDefaults.colors()

    ListItem(
        modifier = clickableModifier,
        headlineContent = {
            Text(
                text = stringResource(R.string.extended_thinking),
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.extended_thinking_description),
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_model),
                contentDescription = stringResource(R.string.extended_thinking)
            )
        },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = null,
                enabled = enabled
            )
        },
        colors = ListItemDefaults.colors(
            headlineColor = if (enabled) colors.headlineColor else colors.disabledHeadlineColor,
            supportingColor = if (enabled) colors.supportingTextColor else colors.disabledHeadlineColor,
            leadingIconColor = if (enabled) colors.leadingIconColor else colors.disabledLeadingIconColor,
            trailingIconColor = if (enabled) colors.trailingIconColor else colors.disabledTrailingIconColor
        )
    )
}

@Composable
fun PreferenceSwitchWithContainer(
    title: String,
    icon: ImageVector? = null,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    val thumbContent: (@Composable () -> Unit)? = remember(isChecked) {
        if (isChecked) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        } else {
            null
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                MaterialTheme.colorScheme.primaryContainer
            )
            .toggleable(
                value = isChecked,
                onValueChange = { onClick() },
                interactionSource = interactionSource,
                indication = LocalIndication.current
            )
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp, end = 16.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (icon == null) 12.dp else 0.dp, end = 12.dp)
        ) {
            Text(
                text = title,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Switch(
            checked = isChecked,
            interactionSource = interactionSource,
            onCheckedChange = null,
            modifier = Modifier.padding(start = 12.dp, end = 6.dp),
            thumbContent = thumbContent
        )
    }
}
