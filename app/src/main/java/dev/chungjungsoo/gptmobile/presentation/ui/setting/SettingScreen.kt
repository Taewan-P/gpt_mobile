package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.presentation.theme.ThemeViewModel
import dev.chungjungsoo.gptmobile.util.collectManagedState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    modifier: Modifier = Modifier,
    onThemeSettingClick: () -> Unit,
    navigationOnClick: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SettingTopBar(
                scrollBehavior = scrollBehavior,
                navigationOnClick = navigationOnClick
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingTitle()
            ThemeSetting(onThemeSettingClick)
            SettingItem(title = "OpenAI Settings", "API Key, Model", {}, true)
            SettingItem(title = "Anthropic Settings", "API Key, Model", {}, true)
            SettingItem(title = "Google Settings", "API Key, Model", {}, true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    navigationOnClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.settings),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = scrollBehavior.state.overlappedFraction),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = navigationOnClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Preview
@Composable
private fun SettingTitle() {
    Text(
        modifier = Modifier
            .padding(top = 32.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        text = stringResource(R.string.settings),
        style = MaterialTheme.typography.headlineLarge
    )
}

@Composable
fun ThemeSetting(
    onItemClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        headlineContent = { Text(stringResource(R.string.theme_settings)) },
        supportingContent = {
            Text(text = stringResource(R.string.supported_soon))
        }
    )
}

@Composable
private fun SettingItem(
    title: String,
    description: String? = null,
    onItemClick: () -> Unit,
    showTrailingIcon: Boolean
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .padding(horizontal = 8.dp),
        headlineContent = { Text(title) },
        supportingContent = {
            description?.let { Text(it) }
        },
        trailingContent = {
            if (showTrailingIcon) {
                Icon(
                    ImageVector.vectorResource(id = R.drawable.ic_round_arrow_right),
                    contentDescription = stringResource(R.string.arrow_icon)
                )
            }
        }
    )
}

@Composable
fun ThemeSettingDialog(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val dynamicTheme by themeViewModel.dynamicTheme.collectManagedState()
    val themeMode by themeViewModel.themeMode.collectManagedState()

    AlertDialog(
        text = {
            Column {
                Text(text = "Dynamic Theme", style = MaterialTheme.typography.titleMedium)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                )
                RadioItem(
                    title = stringResource(R.string.on),
                    description = null,
                    value = dynamicTheme.name,
                    selected = dynamicTheme == DynamicTheme.ON
                ) {
                    themeViewModel.updateDynamicTheme(DynamicTheme.ON)
                }
                RadioItem(
                    title = stringResource(R.string.off),
                    description = null,
                    value = dynamicTheme.name,
                    selected = dynamicTheme == DynamicTheme.OFF
                ) {
                    themeViewModel.updateDynamicTheme(DynamicTheme.OFF)
                }
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
                Text(text = "Dark Mode", style = MaterialTheme.typography.titleMedium)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                )
                RadioItem(
                    title = stringResource(R.string.system_default),
                    description = null,
                    value = themeMode.name,
                    selected = themeMode == ThemeMode.SYSTEM
                ) {
                    themeViewModel.updateThemeMode(ThemeMode.SYSTEM)
                }
                RadioItem(
                    title = stringResource(R.string.on),
                    description = null,
                    value = themeMode.name,
                    selected = themeMode == ThemeMode.DARK
                ) {
                    themeViewModel.updateThemeMode(ThemeMode.DARK)
                }
                RadioItem(
                    title = stringResource(R.string.off),
                    description = null,
                    value = themeMode.name,
                    selected = themeMode == ThemeMode.LIGHT
                ) {
                    themeViewModel.updateThemeMode(ThemeMode.LIGHT)
                }
            }
        },
        onDismissRequest = { onDismiss() },
        confirmButton = {
            TextButton(
                onClick = { onDismiss() }
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}
