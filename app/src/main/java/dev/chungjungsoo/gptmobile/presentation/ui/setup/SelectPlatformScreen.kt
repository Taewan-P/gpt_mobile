package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.dto.ApiType
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton

@Preview
@Composable
fun SelectPlatformScreen(
    modifier: Modifier = Modifier,
    platformState: List<Platform> = listOf(),
    onCheckedEvent: (Platform) -> Unit = {},
    onNextButtonClicked: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        GetStartedText()
        SelectPlatform(
            modifier = Modifier.weight(1f),
            platforms = platformState,
            onClickEvent = {
                onCheckedEvent(it)
            }
        )
        PrimaryLongButton(
            enabled = platformState.any { it.enabled },
            onClick = onNextButtonClicked,
            text = stringResource(R.string.next)
        )
    }
}

@Preview
@Composable
fun GetStartedText(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = stringResource(R.string.get_started),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = stringResource(R.string.platform_select_description),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun SelectPlatform(
    modifier: Modifier = Modifier,
    platforms: List<Platform>,
    onClickEvent: (Platform) -> Unit
) {
    val titles = mapOf(
        ApiType.OPENAI to stringResource(R.string.openai),
        ApiType.ANTHROPIC to stringResource(R.string.anthropic),
        ApiType.GOOGLE to stringResource(R.string.google)
    )
    val descriptions = mapOf(
        ApiType.OPENAI to stringResource(R.string.openai_description),
        ApiType.ANTHROPIC to stringResource(R.string.anthropic_description),
        ApiType.GOOGLE to stringResource(R.string.google_description)
    )

    LazyColumn(modifier = modifier) {
        items(platforms) { platform ->
            CheckBoxItem(
                platform = platform,
                title = titles[platform.name]!!,
                description = descriptions[platform.name]!!,
                onClickEvent = onClickEvent
            )
        }
    }
}

@Composable
fun CheckBoxItem(
    modifier: Modifier = Modifier,
    platform: Platform,
    title: String = stringResource(R.string.sample_item_title),
    description: String = stringResource(R.string.sample_item_description),
    onClickEvent: (Platform) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClickEvent.invoke(platform) }
            .padding(top = 12.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = platform.enabled, onCheckedChange = { onClickEvent.invoke(platform) })
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
