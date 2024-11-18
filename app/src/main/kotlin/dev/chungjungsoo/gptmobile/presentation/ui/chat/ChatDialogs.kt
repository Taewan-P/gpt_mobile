package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.Message

@Composable
fun ChatTitleDialog(
    initialTitle: String,
    aiCoreModeEnabled: Boolean,
    aiGeneratedResult: String,
    isAICoreLoading: Boolean,
    onDefaultTitleMode: () -> String?,
    onAICoreTitleMode: () -> Unit,
    onRetryRequest: () -> Unit,
    onConfirmRequest: (title: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val configuration = LocalConfiguration.current
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var useAICore by rememberSaveable { mutableStateOf(false) }
    val untitledChat = stringResource(R.string.untitled_chat)

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.chat_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = stringResource(R.string.chat_title_dialog_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    value = title,
                    singleLine = true,
                    isError = title.length > 50,
                    supportingText = {
                        if (title.length > 50) {
                            Text(stringResource(R.string.title_length_limit, title.length))
                        }
                    },
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.chat_title)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .height(48.dp)
                            .weight(1F),
                        enabled = !isAICoreLoading,
                        onClick = { title = onDefaultTitleMode.invoke() ?: untitledChat }
                    ) { Text(text = stringResource(R.string.default_mode)) }

                    FilledTonalButton(
                        enabled = aiCoreModeEnabled && !isAICoreLoading,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .height(48.dp)
                            .weight(1F),
                        onClick = {
                            onAICoreTitleMode.invoke()
                            useAICore = true
                        }
                    ) { Text(text = stringResource(R.string.ai_generated)) }
                }

                if (useAICore) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = aiGeneratedResult.trimIndent() + if (isAICoreLoading) "▊" else "",
                                fontWeight = FontWeight.Bold
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Spacer(Modifier.weight(1f))
                                if (!isAICoreLoading) {
                                    IconButton(
                                        onClick = {
                                            title = aiGeneratedResult.trimIndent().replace('\n', ' ')
                                            useAICore = false
                                        }
                                    ) { Icon(Icons.Default.Done, contentDescription = stringResource(R.string.apply_generated_title)) }
                                    IconButton(
                                        onClick = onRetryRequest
                                    ) { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.retry_ai_title)) }
                                }
                            }
                        }
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && title != initialTitle,
                onClick = {
                    onConfirmRequest(title)
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.update))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ChatQuestionEditDialog(
    initialQuestion: Message,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (q: Message) -> Unit
) {
    val configuration = LocalConfiguration.current
    var question by remember { mutableStateOf(initialQuestion.content) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.edit_question)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(stringResource(R.string.user_message)) }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = question.isNotBlank() && question != initialQuestion.content,
                onClick = { onConfirmRequest(initialQuestion.copy(content = question)) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
