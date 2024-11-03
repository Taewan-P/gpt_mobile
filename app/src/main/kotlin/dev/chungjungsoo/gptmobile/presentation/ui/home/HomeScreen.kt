package dev.chungjungsoo.gptmobile.presentation.ui.home

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.presentation.common.PlatformCheckBoxItem
import dev.chungjungsoo.gptmobile.util.collectManagedState
import dev.chungjungsoo.gptmobile.util.getPlatformTitleResources

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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val chatListState by homeViewModel.chatListState.collectManagedState()
    val showSelectModelDialog by homeViewModel.showSelectModelDialog.collectManagedState()
    val showDeleteWarningDialog by homeViewModel.showDeleteWarningDialog.collectManagedState()
    val platformState by homeViewModel.platformState.collectManagedState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectManagedState()
    val context = LocalContext.current

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED && !chatListState.isSelectionMode) {
            homeViewModel.fetchChats()
            homeViewModel.fetchPlatformStatus()
        }
    }

    BackHandler(enabled = chatListState.isSelectionMode) {
        homeViewModel.disableSelectionMode()
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HomeTopAppBar(
                chatListState.isSelectionMode,
                selectedChats = chatListState.selected.count { it },
                scrollBehavior,
                actionOnClick = {
                    if (chatListState.isSelectionMode) {
                        homeViewModel.openDeleteWarningDialog()
                    } else {
                        settingOnClick()
                    }
                },
                navigationOnClick = {
                    homeViewModel.disableSelectionMode()
                }
            )
        },
        floatingActionButton = {
            NewChatButton(expanded = listState.isScrollingUp(), onClick = {
                val enabledApiTypes = platformState.filter { it.enabled }.map { it.name }
                if (enabledApiTypes.size == 1) {
                    // Navigate to new chat directly if only one platform is enabled
                    navigateToNewChat(enabledApiTypes)
                } else {
                    homeViewModel.openSelectModelDialog()
                }
            })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            state = listState
        ) {
            item { ChatsTitle(scrollBehavior) }
            itemsIndexed(chatListState.chats, key = { _, it -> it.id }) { idx, chatRoom ->
                val usingPlatform = chatRoom.enabledPlatform.joinToString(", ") { platformTitles[it] ?: "" }
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onLongClick = {
                                homeViewModel.enableSelectionMode()
                                homeViewModel.selectChat(idx)
                            },
                            onClick = {
                                if (chatListState.isSelectionMode) {
                                    homeViewModel.selectChat(idx)
                                } else {
                                    onExistingChatClick(chatRoom)
                                }
                            }
                        )
                        .padding(start = 8.dp, end = 8.dp)
                        .animateItem(),
                    headlineContent = { Text(text = chatRoom.title) },
                    leadingContent = {
                        if (chatListState.isSelectionMode) {
                            Checkbox(
                                checked = chatListState.selected[idx],
                                onCheckedChange = { homeViewModel.selectChat(idx) }
                            )
                        } else {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.ic_rounded_chat),
                                contentDescription = stringResource(R.string.chat_icon)
                            )
                        }
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

        if (showDeleteWarningDialog) {
            DeleteWarningDialog(
                onDismissRequest = homeViewModel::closeDeleteWarningDialog,
                onConfirm = {
                    val deletedChatRoomCount = chatListState.selected.count { it }
                    homeViewModel.deleteSelectedChats()
                    Toast.makeText(context, context.getString(R.string.deleted_chats, deletedChatRoomCount), Toast.LENGTH_SHORT).show()
                    homeViewModel.closeDeleteWarningDialog()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    isSelectionMode: Boolean,
    selectedChats: Int,
    scrollBehavior: TopAppBarScrollBehavior,
    actionOnClick: () -> Unit,
    navigationOnClick: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            scrolledContainerColor = if (isSelectionMode) MaterialTheme.colorScheme.primaryContainer else Color.Unspecified,
            containerColor = if (isSelectionMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
            titleContentColor = if (isSelectionMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground
        ),
        title = {
            if (isSelectionMode) {
                Text(
                    modifier = Modifier.padding(4.dp),
                    text = stringResource(R.string.chats_selected, selectedChats),
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    modifier = Modifier.padding(4.dp),
                    text = stringResource(R.string.chats),
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = scrollBehavior.state.overlappedFraction),
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            if (isSelectionMode) {
                IconButton(
                    modifier = Modifier.padding(4.dp),
                    onClick = navigationOnClick
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }
        },
        actions = {
            if (isSelectionMode) {
                IconButton(
                    modifier = Modifier.padding(4.dp),
                    onClick = actionOnClick
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentDescription = stringResource(R.string.delete)
                    )
                }
            } else {
                IconButton(
                    modifier = Modifier.padding(4.dp),
                    onClick = actionOnClick
                ) {
                    Icon(imageVector = Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsTitle(scrollBehavior: TopAppBarScrollBehavior) {
    Text(
        modifier = Modifier
            .padding(top = 32.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        text = stringResource(R.string.chats),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1.0F - scrollBehavior.state.overlappedFraction),
        style = MaterialTheme.typography.headlineLarge
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
        Platform(ApiType.GOOGLE, enabled = false),
        Platform(ApiType.GROQ, enabled = true),
        Platform(ApiType.OLLAMA, enabled = true)
    )
    SelectPlatformDialog(
        platforms = platforms,
        onDismissRequest = {},
        onConfirmation = {},
        onPlatformSelect = {}
    )
}

@Composable
fun DeleteWarningDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val configuration = LocalConfiguration.current
    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(configuration.screenWidthDp.dp - 40.dp),
        title = {
            Text(
                text = stringResource(R.string.delete_selected_chats),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(stringResource(R.string.this_operation_can_t_be_undone))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
