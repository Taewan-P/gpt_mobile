package dev.chungjungsoo.gptmobile.presentation.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

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
    ExtendedFloatingActionButton(
        modifier = modifier,
        onClick = { onClick() },
        icon = { Icon(Icons.Filled.Add, stringResource(R.string.new_chat)) },
        text = { Text(text = stringResource(R.string.new_chat)) }
    )
}
