package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigationClick: () -> Unit,
    onNavigationToLicense: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val githubLink = stringResource(R.string.github_link)
    val fdroidLink = stringResource(R.string.f_droid_link)
    val googlePlayLink = stringResource(R.string.play_store_link)
    val bugReportLink = stringResource(R.string.bug_report_link)
    val feedbackLink = stringResource(R.string.feedback_link)

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AboutTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationOnClick = onNavigationClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            SettingItem(
                modifier = Modifier.height(64.dp),
                title = stringResource(R.string.version),
                description = "v$version",
                onItemClick = { clipboardManager.setText(AnnotatedString("v$version")) },
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_info),
                        contentDescription = stringResource(R.string.version_icon)
                    )
                }
            )
            SettingItem(
                modifier = Modifier.height(64.dp),
                title = stringResource(R.string.license),
                description = stringResource(R.string.license_description),
                onItemClick = onNavigationToLicense,
                showTrailingIcon = true,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_license),
                        contentDescription = stringResource(R.string.license_icon)
                    )
                }
            )
            SettingItem(
                modifier = Modifier.height(64.dp),
                title = stringResource(R.string.github),
                onItemClick = { uriHandler.openUri(githubLink) },
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_github),
                        contentDescription = stringResource(R.string.github_icon)
                    )
                }
            )
            SettingItem(
                modifier = Modifier.height(64.dp),
                title = stringResource(R.string.f_droid),
                onItemClick = { uriHandler.openUri(fdroidLink) },
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_f_droid),
                        contentDescription = stringResource(R.string.f_droid_icon)
                    )
                }
            )
            SettingItem(
                modifier = Modifier.height(64.dp),
                title = stringResource(R.string.play_store),
                onItemClick = { uriHandler.openUri(googlePlayLink) },
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_play_store),
                        contentDescription = stringResource(R.string.play_store_icon)
                    )
                }
            )
            SettingItem(
                modifier = Modifier.height(64.dp),
                title = stringResource(R.string.bug_report),
                description = stringResource(R.string.bug_report_description),
                onItemClick = { uriHandler.openUri(bugReportLink) },
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_bug_report),
                        contentDescription = stringResource(R.string.bug_report_icon)
                    )
                }
            )
            SettingItem(
                modifier = Modifier.height(64.dp),
                title = stringResource(R.string.feedback),
                description = stringResource(R.string.feedback_description),
                onItemClick = { uriHandler.openUri(feedbackLink) },
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_feedback),
                        contentDescription = stringResource(R.string.feedback_icon)
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    navigationOnClick: () -> Unit
) {
    LargeTopAppBar(
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.about),
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
