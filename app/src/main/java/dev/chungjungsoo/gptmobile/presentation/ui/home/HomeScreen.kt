package dev.chungjungsoo.gptmobile.presentation.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.dto.ApiType
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.presentation.common.PlatformCheckBoxItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    actionOnClick: () -> Unit,
    newChatOnClick: () -> Unit
) {
    val scrollable = { false } // TODO: Change this condition
    val scrollBehavior = if (scrollable()) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    } else {
        TopAppBarDefaults.pinnedScrollBehavior()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { HomeTopAppBar(scrollBehavior, actionOnClick) },
        floatingActionButton = { NewChatButton(onClick = newChatOnClick) }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    actionOnClick: () -> Unit
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.chats),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = actionOnClick
            ) {
                Icon(imageVector = Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Preview
@Composable
fun NewChatButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { }
) {
    val orientation = LocalConfiguration.current.orientation
    val fabModifier = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        modifier.navigationBarsPadding()
    } else {
        modifier
    }

    ExtendedFloatingActionButton(
        modifier = fabModifier,
        onClick = { onClick() },
        icon = { Icon(Icons.Filled.Add, stringResource(R.string.new_chat)) },
        text = { Text(text = stringResource(R.string.new_chat)) }
    )
}

@Composable
fun SelectPlatformDialog(
    platforms: List<Platform>,
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    onPlatformSelect: (Platform) -> Unit
) {
    val titles = mapOf(
        ApiType.OPENAI to stringResource(R.string.openai),
        ApiType.ANTHROPIC to stringResource(R.string.anthropic),
        ApiType.GOOGLE to stringResource(R.string.google)
    )
    val configuration = LocalConfiguration.current

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = configuration.screenWidthDp.dp - 40.dp),
        onDismissRequest = onDismissRequest,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.select_platform),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.select_platform_description),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        text = {
            HorizontalDivider()
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (platforms.any { it.enabled }) {
                    platforms.forEach { platform ->
                        PlatformCheckBoxItem(
                            platform = platform,
                            title = titles[platform.name]!!,
                            enabled = platform.enabled,
                            description = null,
                            onClickEvent = { onPlatformSelect(platform) }
                        )
                    }
                } else {
                    EnablePlatformWarningText()
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = platforms.any { it.selected },
                onClick = { onConfirmation() }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismissRequest() }
            ) {
                Text("Dismiss")
            }
        }
    )
}

@Preview
@Composable
fun EnablePlatformWarningText() {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .wrapContentHeight(align = Alignment.CenterVertically)
            .padding(16.dp),
        textAlign = TextAlign.Center,
        text = stringResource(R.string.enable_at_leat_one_platform)
    )
}

@Preview
@Composable
private fun SelectPlatformDialogPreview() {
    val platforms = listOf(
        Platform(ApiType.OPENAI, enabled = true),
        Platform(ApiType.ANTHROPIC, enabled = false),
        Platform(ApiType.GOOGLE, enabled = false)
    )
    SelectPlatformDialog(
        platforms = platforms,
        onDismissRequest = {},
        onConfirmation = {},
        onPlatformSelect = {}
    )
}
