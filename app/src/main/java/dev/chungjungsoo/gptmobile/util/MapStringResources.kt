package dev.chungjungsoo.gptmobile.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ApiType

@Composable
fun getPlatformTitleResources(): Map<ApiType, String> {
    return mapOf(
        ApiType.OPENAI to stringResource(R.string.openai),
        ApiType.ANTHROPIC to stringResource(R.string.anthropic),
        ApiType.GOOGLE to stringResource(R.string.google)
    )
}

@Composable
fun getPlatformDescriptionResources(): Map<ApiType, String> {
    return mapOf(
        ApiType.OPENAI to stringResource(R.string.openai_description),
        ApiType.ANTHROPIC to stringResource(R.string.anthropic_description),
        ApiType.GOOGLE to stringResource(R.string.google_description)
    )
}

@Composable
fun getPlatformAPILabelResources(): Map<ApiType, String> {
    return mapOf(
        ApiType.OPENAI to stringResource(R.string.openai_api_key),
        ApiType.ANTHROPIC to stringResource(R.string.anthropic_api_key),
        ApiType.GOOGLE to stringResource(R.string.google_api_key)
    )
}

@Composable
fun getPlatformHelpLinkResources(): Map<ApiType, String> {
    return mapOf(
        ApiType.OPENAI to stringResource(R.string.openai_api_help),
        ApiType.ANTHROPIC to stringResource(R.string.anthropic_api_help),
        ApiType.GOOGLE to stringResource(R.string.google_api_help)
    )
}
