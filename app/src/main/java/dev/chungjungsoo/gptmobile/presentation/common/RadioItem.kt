package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

@Preview
@Composable
fun RadioItem(
    modifier: Modifier = Modifier,
    value: String = stringResource(R.string.sample_item_title),
    selected: Boolean = false,
    title: String = stringResource(R.string.sample_item_title),
    description: String? = stringResource(R.string.sample_item_description),
    onSelected: (String) -> Unit = { }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = { onSelected(value) },
                role = Role.RadioButton
            )
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(
            modifier = Modifier.padding(start = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
