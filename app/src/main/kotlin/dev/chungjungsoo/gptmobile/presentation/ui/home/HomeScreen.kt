package dev.chungjungsoo.gptmobile.presentation.ui.home

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.ConversationMode
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.collectReusableProfileLabels
import dev.chungjungsoo.gptmobile.presentation.common.BeveledProfileLabel
import dev.chungjungsoo.gptmobile.presentation.common.PlatformCheckBoxItem
import dev.chungjungsoo.gptmobile.presentation.ui.archive.ArchivedConversationsBar
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatMarkdown
import dev.chungjungsoo.gptmobile.util.getPlatformName
import kotlinx.coroutines.launch

enum class PlatformSortOrder {
    DEFAULT,
    NAME,
    PROVIDER,
    ENABLED_FIRST
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = hiltViewModel(),
    settingOnClick: () -> Unit = {},
    onExistingChatClick: (chatRoom: ChatRoomV2, targetMessageId: Int?) -> Unit = { _, _ -> },
    navigateToNewChat: (enabledPlatforms: List<String>, conversationMode: String) -> Unit = { _, _ -> }
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val currentTab by homeViewModel.currentTab.collectAsStateWithLifecycle()
    val chatListState by homeViewModel.chatListState.collectAsStateWithLifecycle()
    val showSelectModelDialog by homeViewModel.showSelectModelDialog.collectAsStateWithLifecycle()
    val showDeleteWarningDialog by homeViewModel.showDeleteWarningDialog.collectAsStateWithLifecycle()
    val platformState by homeViewModel.platformState.collectAsStateWithLifecycle()
    val activeChatIds by homeViewModel.activeChatIds.collectAsStateWithLifecycle()
    val unreadChatIds by homeViewModel.unreadChatIds.collectAsStateWithLifecycle()
    val archivedChats by homeViewModel.archivedChats.collectAsStateWithLifecycle()
    val searchQuery by homeViewModel.searchQuery.collectAsStateWithLifecycle()
    val favoriteMessages by homeViewModel.favoriteMessages.collectAsStateWithLifecycle()
    val favoriteGroups by homeViewModel.favoriteGroups.collectAsStateWithLifecycle()
    val selectedFavoriteGroup by homeViewModel.selectedFavoriteGroup.collectAsStateWithLifecycle()
    val messageGroups by homeViewModel.messageGroups.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val selectedChatCount = chatListState.selectedChats.count { it }
    val selectedChat = chatListState.chats.filterIndexed { index, _ -> chatListState.selectedChats.getOrElse(index) { false } }.singleOrNull()
    val duplicatedChatMessage = stringResource(R.string.duplicated_chat)
    val deletedChatsMessage = stringResource(R.string.deleted_chats, selectedChatCount)

    var selectedDetailMessage by remember { mutableStateOf<MessageV2?>(null) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var chatPendingDelete by remember { mutableStateOf<ChatRoomV2?>(null) }

    BackHandler {
        when {
            chatListState.isSelectionMode -> homeViewModel.disableSelectionMode()
            chatListState.isSearchMode -> homeViewModel.disableSearchMode()
            currentTab != HomeTab.CHATS -> homeViewModel.selectTab(HomeTab.CHATS)
            else -> Unit
        }
    }

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED && !chatListState.isSelectionMode && !chatListState.isSearchMode) {
            homeViewModel.fetchChats()
            homeViewModel.fetchPlatformStatus()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HomeTopBar(
                chatListState = chatListState,
                scrollBehavior = scrollBehavior,
                selectedChatCount = selectedChatCount,
                canDuplicate = selectedChat != null && !activeChatIds.contains(selectedChat.id),
                onCloseSelectionMode = homeViewModel::disableSelectionMode,
                onDuplicateClick = {
                    homeViewModel.duplicateSelectedChat()
                    Toast.makeText(context, duplicatedChatMessage, Toast.LENGTH_SHORT).show()
                },
                onDeleteClick = homeViewModel::openDeleteWarningDialog,
                onSettingClick = settingOnClick,
                onSearchToggle = {
                    if (chatListState.isSearchMode) {
                        homeViewModel.disableSearchMode()
                    } else {
                        homeViewModel.enableSearchMode()
                    }
                },
                onSearchQueryChanged = homeViewModel::updateSearchQuery,
                searchQuery = searchQuery
            )
        },
        floatingActionButton = {
            if (currentTab == HomeTab.CHATS && !chatListState.isSelectionMode && !chatListState.isSearchMode) {
                NewChatButton(expanded = listState.isScrollingUp(), onClick = {
                    val enabledApiTypes = platformState.filter { it.enabled }.map { it.uid }
                    if (enabledApiTypes.size == 1) {
                        // Navigate to new chat directly if only one platform is enabled
                        navigateToNewChat(enabledApiTypes, ConversationMode.STANDARD)
                    } else {
                        homeViewModel.openSelectModelDialog()
                    }
                })
            }
        },
        bottomBar = {
            if (currentTab == HomeTab.CHATS && !chatListState.isSelectionMode && !chatListState.isSearchMode) {
                ArchivedConversationsBar(
                    archivedChats = archivedChats,
                    onUnarchiveChat = { room ->
                        homeViewModel.unarchiveChat(room)
                        Toast.makeText(context, R.string.chat_unarchived, Toast.LENGTH_SHORT).show()
                    },
                    onDeleteChat = { room ->
                        homeViewModel.deleteArchivedChat(room)
                    },
                    onChatClick = { room ->
                        onExistingChatClick(room, null)
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (!chatListState.isSelectionMode && !chatListState.isSearchMode) {
                PrimaryTabRow(
                    selectedTabIndex = currentTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.background,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)) }
                ) {
                    Tab(
                        selected = currentTab == HomeTab.CHATS,
                        onClick = { homeViewModel.selectTab(HomeTab.CHATS) },
                        text = { Text(stringResource(R.string.chats)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = stringResource(R.string.chats)
                            )
                        }
                    )
                    Tab(
                        selected = currentTab == HomeTab.FAVORITES,
                        onClick = { homeViewModel.selectTab(HomeTab.FAVORITES) },
                        text = { Text(stringResource(R.string.favorites)) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == HomeTab.FAVORITES) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = stringResource(R.string.favorites)
                            )
                        }
                    )
                }
            }

            when (currentTab) {
                HomeTab.CHATS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        state = listState
                    ) {
                        if (!chatListState.isSearchMode) {
                            item(key = "home-chats-title", contentType = "header") {
                                ChatsTitle(scrollBehavior)
                            }
                        }
                        if (chatListState.isSearchMode && chatListState.chats.isEmpty() && searchQuery.isNotEmpty()) {
                            item(key = "home-chats-empty", contentType = "empty-notice") {
                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    text = stringResource(R.string.no_search_results),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        itemsIndexed(
                            items = chatListState.chats,
                            key = { _, it -> it.id },
                            contentType = { _, _ -> "chat-room-item" }
                        ) { idx, chatRoom ->
                            val chatProfiles = chatRoom.enabledPlatform.mapNotNull { uid ->
                                platformState.firstOrNull { it.uid == uid }
                            }
                            val usingPlatform = chatProfiles.joinToString(", ") { it.name }
                                .ifBlank {
                                    chatRoom.enabledPlatform.joinToString(", ") { uid -> platformState.getPlatformName(uid) }
                                }
                            val chatProfileLabels = collectReusableProfileLabels(chatProfiles.map { it.labels })
                            val isGenerating = activeChatIds.contains(chatRoom.id)
                            val hasUnreadResponse = unreadChatIds.contains(chatRoom.id)
                            var hasTriggeredHaptic by remember { mutableStateOf(false) }
                            val dismissState = rememberSwipeToDismissBoxState(
                                positionalThreshold = { totalDistance -> totalDistance * 0.38f },
                                confirmValueChange = { dismissValue ->
                                    when (dismissValue) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            // Swipe Right -> Archive
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            homeViewModel.archiveChat(chatRoom)
                                            Toast.makeText(context, R.string.chat_archived, Toast.LENGTH_SHORT).show()
                                            false
                                        }
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            // Swipe Left -> Delete with confirmation
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            chatPendingDelete = chatRoom
                                            false
                                        }
                                        SwipeToDismissBoxValue.Settled -> false
                                    }
                                }
                            )

                            // Trigger haptic feedback when crossing the swipe threshold
                            val swipeProgress = dismissState.progress
                            LaunchedEffect(dismissState.targetValue, swipeProgress) {
                                if (dismissState.targetValue != SwipeToDismissBoxValue.Settled && swipeProgress >= 0.5f) {
                                    if (!hasTriggeredHaptic) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        hasTriggeredHaptic = true
                                    }
                                } else if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) {
                                    hasTriggeredHaptic = false
                                }
                            }

                            if (chatListState.isSelectionMode || chatListState.isSearchMode) {
                                ChatListItem(
                                    chatRoom = chatRoom,
                                    idx = idx,
                                    chatListState = chatListState,
                                    isGenerating = isGenerating,
                                    hasUnreadResponse = hasUnreadResponse,
                                    usingPlatform = usingPlatform,
                                    profileLabels = chatProfileLabels,
                                    isServerChat = chatProfiles.singleOrNull()?.compatibleType in setOf(ClientType.OLLAMA, ClientType.LLAMA, ClientType.LITERT_LM),
                                    onItemClick = {
                                        if (chatListState.isSelectionMode) {
                                            homeViewModel.selectChat(idx)
                                        } else {
                                            homeViewModel.markChatViewed(chatRoom.id)
                                            onExistingChatClick(chatRoom, null)
                                        }
                                    },
                                    onItemLongClick = {
                                        if (!chatListState.isSearchMode) {
                                            homeViewModel.enableSelectionMode()
                                            homeViewModel.selectChat(idx)
                                        }
                                    }
                                )
                            } else {
                                FancySwipeChatCard(
                                    dismissState = dismissState,
                                    chatRoom = chatRoom,
                                    idx = idx,
                                    chatListState = chatListState,
                                    isGenerating = isGenerating,
                                    hasUnreadResponse = hasUnreadResponse,
                                    usingPlatform = usingPlatform,
                                    profileLabels = chatProfileLabels,
                                    isServerChat = chatProfiles.singleOrNull()?.compatibleType in setOf(ClientType.OLLAMA, ClientType.LLAMA, ClientType.LITERT_LM),
                                    onItemClick = {
                                        homeViewModel.markChatViewed(chatRoom.id)
                                        onExistingChatClick(chatRoom, null)
                                    },
                                    onItemLongClick = {
                                        val newFavorite = !chatRoom.isFavorite
                                        homeViewModel.toggleChatFavorite(chatRoom.id, newFavorite)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val messageRes = if (newFavorite) R.string.chat_pinned else R.string.chat_unpinned
                                        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                HomeTab.FAVORITES -> {
                    FavoritesList(
                        favorites = favoriteMessages,
                        favoriteGroups = favoriteGroups,
                        selectedGroup = selectedFavoriteGroup,
                        messageGroups = messageGroups,
                        platformState = platformState,
                        onSelectGroup = homeViewModel::selectFavoriteGroup,
                        onAddGroupClick = { showAddGroupDialog = true },
                        onRenameGroup = homeViewModel::renameFavoriteGroup,
                        onDeleteGroup = homeViewModel::deleteFavoriteGroup,
                        onFavoriteClick = { message ->
                            selectedDetailMessage = message
                        },
                        onToggleFavorite = { message ->
                            homeViewModel.toggleFavorite(message.id, !message.isFavorite)
                        }
                    )
                }
            }
        }

        if (showAddGroupDialog) {
            AddFavoriteGroupDialog(
                onDismissRequest = { showAddGroupDialog = false },
                onAddGroup = { newGroupName ->
                    homeViewModel.addFavoriteGroup(newGroupName)
                    showAddGroupDialog = false
                }
            )
        }

        chatPendingDelete?.let { chatToDelete ->
            AlertDialog(
                onDismissRequest = { chatPendingDelete = null },
                title = { Text(stringResource(R.string.delete_chat_dialog_title)) },
                text = { Text(stringResource(R.string.delete_chat_dialog_message, chatToDelete.title)) },
                confirmButton = {
                    TextButton(onClick = {
                        homeViewModel.deleteChat(chatToDelete)
                        chatPendingDelete = null
                    }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chatPendingDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        selectedDetailMessage?.let { detailMessage ->
            val platformName = detailMessage.platformType?.let { platformState.getPlatformName(it) }
                ?: stringResource(R.string.unknown)
            val currentGroup = messageGroups[detailMessage.id]
            FavoriteDetailDialog(
                message = detailMessage,
                platformName = platformName,
                favoriteGroups = favoriteGroups,
                currentGroup = currentGroup,
                onDismiss = { selectedDetailMessage = null },
                onAssignGroup = { group ->
                    homeViewModel.assignFavoriteMessageGroup(detailMessage.id, group)
                },
                onViewInChat = {
                    homeViewModel.getChatRoom(detailMessage.chatId) { targetChat ->
                        selectedDetailMessage = null
                        if (targetChat != null) {
                            onExistingChatClick(targetChat, detailMessage.id)
                        }
                    }
                },
                onUnfavorite = {
                    homeViewModel.toggleFavorite(detailMessage.id, false)
                    selectedDetailMessage = null
                }
            )
        }

        if (showSelectModelDialog) {
            SelectPlatformDialog(
                platformState,
                selectedPlatforms = chatListState.selectedPlatforms,
                onDismissRequest = { homeViewModel.closeSelectModelDialog() },
                onConfirmation = { platforms, conversationMode ->
                    navigateToNewChat(platforms, conversationMode)
                    homeViewModel.closeSelectModelDialog()
                },
                onPlatformSelect = { homeViewModel.updatePlatformCheckedState(it) },
                onTogglePlatformFavorite = { platformId ->
                    val target = platformState.find { it.id == platformId }
                    if (target != null) {
                        homeViewModel.togglePlatformFavorite(platformId, !target.isFavorite)
                    }
                }
            )
        }

        if (showDeleteWarningDialog) {
            DeleteWarningDialog(
                onDismissRequest = homeViewModel::closeDeleteWarningDialog,
                onConfirm = {
                    homeViewModel.deleteSelectedChats()
                    Toast.makeText(context, deletedChatsMessage, Toast.LENGTH_SHORT).show()
                    homeViewModel.closeDeleteWarningDialog()
                }
            )
        }
    }
}

/**
 * Enhanced swipe-to-dismiss card with visual effects:
 * - Dynamic full-color transitions matching swipe direction (Green for Archive, Red for Delete)
 * - Continuous pulsating animation on revealed action icons (Archive / Delete)
 * - Clean card design without persistent action icons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FancySwipeChatCard(
    dismissState: SwipeToDismissBoxState,
    chatRoom: ChatRoomV2,
    idx: Int,
    chatListState: HomeViewModel.ChatListState,
    isGenerating: Boolean,
    hasUnreadResponse: Boolean,
    usingPlatform: String,
    profileLabels: List<dev.chungjungsoo.gptmobile.data.model.ProfileLabel>,
    isServerChat: Boolean = false,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = dismissState.progress
    val swipeDirection = dismissState.dismissDirection
    val isSwipingStartToEnd = swipeDirection == SwipeToDismissBoxValue.StartToEnd ||
        dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
    val isSwipingEndToStart = swipeDirection == SwipeToDismissBoxValue.EndToStart ||
        dismissState.targetValue == SwipeToDismissBoxValue.EndToStart

    // Pulse animation spec for revealed swipe icons
    val infiniteTransition = rememberInfiniteTransition(label = "icon_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Full-color swipe backgrounds
    val archiveColor = Color(0xFF00BCD4)
    val deleteColor = Color(0xFFF44336)

    // Card surface tint dynamically reacting to swipe progress
    val cardContainerColor = when {
        isSwipingStartToEnd && progress > 0.001f ->
            archiveColor.copy(alpha = (0.45f + progress * 0.45f).coerceIn(0.45f, 0.90f))
        isSwipingEndToStart && progress > 0.001f ->
            deleteColor.copy(alpha = (0.45f + progress * 0.45f).coerceIn(0.45f, 0.90f))
        else -> MaterialTheme.colorScheme.surface
    }

    val animatedCardColor = cardContainerColor

    val cardElevation = if (progress > 0.1f) 4.dp else 1.dp

    SwipeToDismissBox(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        state = dismissState,
        backgroundContent = {
            val isArchiveTarget = isSwipingStartToEnd
            val isDeleteTarget = isSwipingEndToStart

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isArchiveTarget -> archiveColor
                            isDeleteTarget -> deleteColor
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = if (isArchiveTarget) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (isArchiveTarget) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = stringResource(R.string.archive_chat),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.archive_chat),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else if (isDeleteTarget) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = animatedCardColor,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
        ) {
            ChatListItem(
                chatRoom = chatRoom,
                idx = idx,
                chatListState = chatListState,
                isGenerating = isGenerating,
                hasUnreadResponse = hasUnreadResponse,
                usingPlatform = usingPlatform,
                profileLabels = profileLabels,
                isServerChat = isServerChat,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    chatRoom: ChatRoomV2,
    idx: Int,
    chatListState: HomeViewModel.ChatListState,
    isGenerating: Boolean,
    hasUnreadResponse: Boolean,
    usingPlatform: String,
    profileLabels: List<dev.chungjungsoo.gptmobile.data.model.ProfileLabel>,
    isServerChat: Boolean = false,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = onItemLongClick,
                onClick = onItemClick
            )
            .padding(start = 8.dp, end = 8.dp),
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (chatRoom.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.pinned_chat),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = chatRoom.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (chatRoom.isTitleCustomized) Color(0xFF67E8F9) else Color.Unspecified,
                    fontWeight = if (hasUnreadResponse) FontWeight.Bold else FontWeight.Normal
                )
            }
        },
        leadingContent = {
            if (chatListState.isSelectionMode) {
                Checkbox(
                    checked = chatListState.selectedChats.getOrElse(idx) { false },
                    onCheckedChange = { onItemClick() }
                )
            } else if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                ConversationModeSymbol(
                    chatRoom = chatRoom,
                    isServerChat = isServerChat,
                    hasUnreadResponse = hasUnreadResponse
                )
            }
        },
        supportingContent = {
            if (!chatRoom.draftText.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0x33FFC107),
                        border = BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "DRAFT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color(0xFFFFB300),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = chatRoom.draftText,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = usingPlatform,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                    )
                    if (profileLabels.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            profileLabels.take(4).forEach { label ->
                                BeveledProfileLabel(label = label, compact = true)
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ConversationModeSymbol(
    chatRoom: ChatRoomV2,
    isServerChat: Boolean,
    hasUnreadResponse: Boolean
) {
    val (icon, description) = if (hasUnreadResponse) {
        Icons.Filled.ChatBubble to "Unread AI response"
    } else {
        when {
            chatRoom.conversationMode == ConversationMode.COMBINED -> Icons.Outlined.Hub to "Combined conversation"
            chatRoom.enabledPlatform.size > 1 -> Icons.Outlined.Forum to "Multiple AI conversation"
            isServerChat -> Icons.Outlined.Dns to "Local or server AI conversation"
            else -> Icons.Outlined.ChatBubbleOutline to "Conversation"
        }
    }
    Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun FavoritesList(
    favorites: List<MessageV2>,
    favoriteGroups: List<String>,
    selectedGroup: String,
    messageGroups: Map<Int, String>,
    platformState: List<PlatformV2>,
    onSelectGroup: (String) -> Unit,
    onAddGroupClick: () -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onFavoriteClick: (MessageV2) -> Unit,
    onToggleFavorite: (MessageV2) -> Unit
) {
    var groupMenu by remember { mutableStateOf<String?>(null) }
    var editingGroup by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            favoriteGroups.forEach { group ->
                Box {
                    val selected = selectedGroup == group
                    Surface(
                        modifier = Modifier.combinedClickable(
                            onClick = { onSelectGroup(group) },
                            onLongClick = { groupMenu = group }
                        ),
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        )
                    ) {
                        Text(
                            group,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            },
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = groupMenu == group,
                        onDismissRequest = { groupMenu = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit label") },
                            onClick = {
                                editingGroup = group
                                editingText = group
                                groupMenu = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete label", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                onDeleteGroup(group)
                                groupMenu = null
                            }
                        )
                    }
                }
            }
            IconButton(onClick = onAddGroupClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_group),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_favorites_yet),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favorites, key = { it.id }) { message ->
                    val platformName = message.platformType?.let { platformState.getPlatformName(it) }
                        ?: stringResource(R.string.unknown)
                    val assignedGroup = messageGroups[message.id]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFavoriteClick(message) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = platformName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (assignedGroup != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = assignedGroup,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        editingGroup?.let { original ->
            AlertDialog(
                onDismissRequest = { editingGroup = null },
                title = { Text("Edit favorite label") },
                text = {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        label = { Text("Label name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = editingText.isNotBlank(),
                        onClick = {
                            onRenameGroup(original, editingText)
                            editingGroup = null
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { editingGroup = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun AddFavoriteGroupDialog(
    onDismissRequest: () -> Unit,
    onAddGroup: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.add_group)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.group_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onAddGroup(text.trim()) }
            ) {
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

@Composable
fun FavoriteDetailDialog(
    message: MessageV2,
    platformName: String,
    favoriteGroups: List<String>,
    currentGroup: String?,
    onDismiss: () -> Unit,
    onAssignGroup: (String?) -> Unit,
    onViewInChat: () -> Unit,
    onUnfavorite: () -> Unit
) {
    var showGroupDropdown by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = platformName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Copy Button
                        IconButton(onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(message.content, message.content)))
                                Toast.makeText(context, R.string.copy_text, Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.copy_text),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Group Selector
                        Box {
                            IconButton(onClick = { showGroupDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = stringResource(R.string.group_name),
                                    tint = if (currentGroup != null) Color.Cyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showGroupDropdown,
                                onDismissRequest = { showGroupDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.none_group)) },
                                    leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                    onClick = {
                                        onAssignGroup(null)
                                        showGroupDropdown = false
                                    }
                                )
                                favoriteGroups.filter { it != HomeViewModel.GROUP_ALL }.forEach { group ->
                                    DropdownMenuItem(
                                        text = { Text(group) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Folder,
                                                contentDescription = null,
                                                tint = if (currentGroup == group) Color.Cyan else MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {
                                            onAssignGroup(group)
                                            showGroupDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // View in Chat
                        IconButton(onClick = onViewInChat) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = stringResource(R.string.view_in_chat),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Delete / Unfavorite
                        IconButton(onClick = onUnfavorite) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Content area with SelectionContainer for text selection and copying
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        ChatMarkdown(content = message.content)
                    }
                }
            }
        }
    }
}

@Composable
fun SelectPlatformDialog(
    platforms: List<PlatformV2>,
    selectedPlatforms: List<Boolean>,
    onDismissRequest: () -> Unit,
    onConfirmation: (enabledPlatforms: List<String>, conversationMode: String) -> Unit,
    onPlatformSelect: (idx: Int) -> Unit,
    onTogglePlatformFavorite: (platformId: Int) -> Unit = {}
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var combinedMode by rememberSaveable { mutableStateOf(false) }
    val selectedCount = platforms.indices.count { platforms[it].enabled && selectedPlatforms.getOrElse(it) { false } }
    val canCombine = selectedCount >= 2

    LaunchedEffect(canCombine) {
        if (!canCombine) combinedMode = false
    }
    val indexedPlatforms = remember(platforms) {
        orderedChatProfiles(platforms)
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
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
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !combinedMode,
                        onClick = { combinedMode = false },
                        label = { Text("Separate") }
                    )
                    FilterChip(
                        selected = combinedMode,
                        enabled = canCombine,
                        onClick = { combinedMode = true },
                        label = { Text(stringResource(R.string.chat_mode_combined)) }
                    )
                }
                Text(
                    text = stringResource(if (combinedMode) R.string.chat_mode_combined_description else R.string.chat_mode_standard_description),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            HorizontalDivider()
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (platforms.any { it.enabled }) {
                    indexedPlatforms.forEach { (originalIndex, platform) ->
                        PlatformCheckBoxItem(
                            title = platform.name,
                            enabled = platform.enabled,
                            selected = selectedPlatforms.getOrElse(originalIndex) { false },
                            isFavorite = platform.isFavorite,
                            labels = platform.labels,
                            description = null,
                            onLongClickEvent = { onTogglePlatformFavorite(platform.id) },
                            onClickEvent = { onPlatformSelect(originalIndex) }
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
                enabled = selectedCount > 0,
                onClick = {
                    val selected = platforms.filterIndexed { i, platform -> platform.enabled && selectedPlatforms.getOrElse(i) { false } }.map { it.uid }
                    onConfirmation(
                        selected,
                        if (combinedMode && selected.size >= 2) ConversationMode.COMBINED else ConversationMode.STANDARD
                    )
                }
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

@Composable
fun EnablePlatformWarningText() {
    Text(
        text = stringResource(R.string.enable_at_leat_one_platform),
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
fun DeleteWarningDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.delete_selected_chats)) },
        text = { Text(stringResource(R.string.this_operation_can_t_be_undone)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun LazyListState.isScrollingUp(): Boolean {
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

@Composable
fun NewChatButton(expanded: Boolean, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { Icon(Icons.Outlined.AddComment, stringResource(R.string.new_chat)) },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        text = { Text(text = stringResource(R.string.new_chat), fontWeight = FontWeight.SemiBold) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    chatListState: HomeViewModel.ChatListState,
    scrollBehavior: TopAppBarScrollBehavior,
    selectedChatCount: Int,
    canDuplicate: Boolean,
    onCloseSelectionMode: () -> Unit,
    onDuplicateClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSettingClick: () -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    searchQuery: String
) {
    if (chatListState.isSelectionMode) {
        TopAppBar(
            title = { Text(stringResource(R.string.chats_selected, selectedChatCount)) },
            navigationIcon = {
                IconButton(onClick = onCloseSelectionMode) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                }
            },
            actions = {
                if (canDuplicate) {
                    IconButton(onClick = onDuplicateClick) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.duplicate))
                    }
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        )
    } else if (chatListState.isSearchMode) {
        TopAppBar(
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text(stringResource(R.string.search_chats)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onSearchToggle) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                }
            }
        )
    } else {
        TopAppBar(
            title = { },
            actions = {
                FilledTonalIconButton(onClick = onSearchToggle, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), contentColor = MaterialTheme.colorScheme.primary)) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search))
                }
                FilledTonalIconButton(onClick = onSettingClick, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f), contentColor = MaterialTheme.colorScheme.primary)) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsTitle(scrollBehavior: TopAppBarScrollBehavior) {
    Text(
        text = stringResource(R.string.chats),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}
