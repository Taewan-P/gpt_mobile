package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.dto.APIModel
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem

@Composable
fun SelectModelScreen(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    availableModels: List<APIModel>,
    model: String,
    onChangeEvent: (String) -> Unit,
    onNextButtonClicked: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SelectModelText(title = title, description = description)
        ModelRadioGroup(
            availableModels = availableModels,
            model = model,
            onChangeEvent = onChangeEvent
        )
        Spacer(modifier = Modifier.weight(1f))
        PrimaryLongButton(
            enabled = availableModels.any { it.aliasValue == model },
            onClick = onNextButtonClicked,
            text = stringResource(R.string.next)
        )
    }
}

@Composable
fun SelectModelText(
    modifier: Modifier = Modifier,
    title: String,
    description: String
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = description,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun ModelRadioGroup(
    modifier: Modifier = Modifier,
    availableModels: List<APIModel>,
    model: String,
    onChangeEvent: (String) -> Unit
) {
    Column(modifier = modifier) {
        availableModels.forEach { m ->
            RadioItem(
                value = m.aliasValue,
                selected = model == m.aliasValue,
                title = m.name,
                description = m.description,
                onSelected = onChangeEvent
            )
        }
    }
}
