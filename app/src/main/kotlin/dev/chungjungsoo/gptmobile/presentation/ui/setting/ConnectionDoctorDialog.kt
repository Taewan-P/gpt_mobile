package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R

@Composable
fun ConnectionDoctorDialog(onDismiss: () -> Unit, model: ConnectionDoctorViewModel = hiltViewModel()) {
    val detectedContext by model.detectedContext.collectAsStateWithLifecycle()
    val profiles by model.profiles.collectAsStateWithLifecycle()
    val report by model.report.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = {
        model.cancel()
        onDismiss()
    }, title = { Text(stringResource(R.string.connection_doctor_dialog_label_1)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.connection_doctor_dialog_label_2))
            profiles.filter { it.enabled }.forEach { profile ->
                Text(profile.name, Modifier.padding(top = 12.dp))
                TextButton(enabled = !busy, onClick = { model.inspect(profile) }) { Text(stringResource(R.string.connection_doctor_dialog_label_3)) }
            }
            if (detectedContext != null) TextButton(enabled = !busy, onClick = model::applyDetectedContext) { Text(stringResource(R.string.context_apply_detected)) }
            if (busy) TextButton(onClick = model::cancel) { Text(stringResource(R.string.connection_doctor_dialog_label_5)) }
            androidx.compose.foundation.text.selection.SelectionContainer { Text(report) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            model.cancel()
            onDismiss()
        }) { Text(stringResource(R.string.connection_doctor_dialog_label_7)) }
    })
}
