package dev.chungjungsoo.gptmobile.presentation.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.presentation.common.PlatformCheckBoxItem
import dev.chungjungsoo.gptmobile.util.collectManagedState
import dev.chungjungsoo.gptmobile.util.getPlatformTitleResources
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    settingOnClick: () -> Unit,
    onExistingChatClick: (ChatRoom) -> Unit,
    navigateToNewChat: (enabledPlatforms: List<ApiType>) -> Unit
) {
    val platformTitles = getPlatformTitleResources()
    val listState = rememberLazyListState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { listState.canScrollForward || listState.canScrollBackward }
    )
    val chatList by homeViewModel.chatList.collectManagedState()
    val showSelectModelDialog by homeViewModel.showSelectModelDialog.collectManagedState()
    val platformState by homeViewModel.platformState.collectManagedState()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectManagedState()

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            homeViewModel.fetchChats()
            homeViewModel.fetchPlatformStatus()
        }
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { HomeTopAppBar(scrollBehavior, settingOnClick) },
        floatingActionButton = { NewChatButton(expanded = listState.isScrollingUp(), onClick = homeViewModel::openSelectModelDialog) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            state = listState
        ) {
            items(chatList, key = { it.id }) { chatRoom ->
                val usingPlatform = chatRoom.enabledPlatform.joinToString(", ") { platformTitles[it] ?: "" }
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExistingChatClick(chatRoom) }
                        .padding(start = 8.dp, end = 8.dp)
                        .animateItemPlacement(),
                    headlineContent = { Text(text = chatRoom.title) },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_rounded_chat),
                            contentDescription = stringResource(R.string.chat_icon)
                        )
                    },
                    supportingContent = { Text(text = stringResource(R.string.using_certain_platform, usingPlatform)) }
                )
            }
        }
        if (showSelectModelDialog) {
            SelectPlatformDialog(
                platformState,
                onDismissRequest = { homeViewModel.closeSelectModelDialog() },
                onConfirmation = {
                    homeViewModel.closeSelectModelDialog()
                    navigateToNewChat(it)
                },
                onPlatformSelect = { homeViewModel.updateCheckedState(it) }
            )
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

@Composable
private fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}

@Preview
@Composable
fun NewChatButton(
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onClick: () -> Unit = { }
) {
    val orientation = LocalConfiguration.current.orientation
    val fabModifier = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        modifier.systemBarsPadding()
    } else {
        modifier
    }
    ExtendedFloatingActionButton(
        modifier = fabModifier,
        onClick = { onClick() },
        expanded = expanded,
        icon = { Icon(Icons.Filled.Add, stringResource(R.string.new_chat)) },
        text = { Text(text = stringResource(R.string.new_chat)) }
    )
}

@Composable
fun SelectPlatformDialog(
    platforms: List<Platform>,
    onDismissRequest: () -> Unit,
    onConfirmation: (enabledPlatforms: List<ApiType>) -> Unit,
    onPlatformSelect: (Platform) -> Unit
) {
    val titles = getPlatformTitleResources()
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
                onClick = { onConfirmation(platforms.filter { it.selected }.map { it.name }) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismissRequest() }
            ) {
                Text(stringResource(R.string.cancel))
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
