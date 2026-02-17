package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

private const val MIN_TOOL_CALL_ITERATIONS = 1
private const val MAX_TOOL_CALL_ITERATIONS = 100

@Composable
internal fun MaxToolCallIterationsSection(
    maxIterations: Int,
    onMaxIterationsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var editValue by remember(maxIterations) { mutableStateOf(maxIterations.toString()) }
    val parsed = editValue.toIntOrNull()
    val isValid = parsed == null || parsed in MIN_TOOL_CALL_ITERATIONS..MAX_TOOL_CALL_ITERATIONS

    OutlinedTextField(
        value = editValue,
        onValueChange = { newValue ->
            editValue = newValue
            val value = newValue.toIntOrNull() ?: return@OutlinedTextField
            if (value in MIN_TOOL_CALL_ITERATIONS..MAX_TOOL_CALL_ITERATIONS) {
                onMaxIterationsChange(value)
            }
        },
        label = { Text(stringResource(R.string.max_tool_call_iterations)) },
        supportingText = { Text(stringResource(R.string.max_tool_call_iterations_description)) },
        isError = !isValid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true
    )
}
