package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.diagnostics.AppLogRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppLogPanel() {
    val enabled by AppLogRecorder.enabled.collectAsStateWithLifecycle()
    val entries by AppLogRecorder.entries.collectAsStateWithLifecycle()
    val error by AppLogRecorder.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var errorsOnly by rememberSaveable { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<dev.chungjungsoo.gptmobile.data.diagnostics.AppLogEntry>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Track app logs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Switch(enabled, AppLogRecorder::setEnabled)
                }
                Text("Capture app events, errors, model requests, tools, network headers and Android logs for this app.", style = MaterialTheme.typography.bodyMedium)
                Text("Logs stay on this device until you share them. Credentials are redacted and HTTP bodies are excluded. Other app logs may include content; review before sharing. The latest 2 MB is retained.", style = MaterialTheme.typography.bodySmall)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(query, { query = it }, label = { Text("Filter logs") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!errorsOnly, { errorsOnly = false }, label = { Text("All levels") })
            FilterChip(errorsOnly, { errorsOnly = true }, label = { Text("Warnings & errors") })
        }
        Row {
            TextButton(onClick = { frozen = if (frozen == null) entries else null }) { Text(if (frozen == null) "Pause view" else "Resume") }
            TextButton(onClick = {
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) { AppLogRecorder.export() } ?: return@launch
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                                "Share diagnostic logs"
                            )
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, "Unable to export logs", Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text("Export logs") }
            TextButton(onClick = {
                frozen = null
                AppLogRecorder.clear()
            }) { Text("Clear logs") }
        }
        val visible = dev.chungjungsoo.gptmobile.data.diagnostics.groupAppLogs(frozen ?: entries)
            .filter { (!errorsOnly || it.entry.level in setOf("W", "E", "F")) && (it.entry.tag + it.entry.message).contains(query, ignoreCase = true) }.takeLast(100)
        Text("${visible.sumOf { it.repetitions }} events · ${visible.size} rows · newest first", style = MaterialTheme.typography.labelMedium)
        Text("Consecutive repeats are grouped; exports keep every event.", style = MaterialTheme.typography.labelSmall)
        if (visible.isEmpty()) Text(if (enabled) "Waiting for matching app events…" else "Enable tracking to record a test session.")
        visible.asReversed().forEach { group ->
            val entry = group.entry
            Card(Modifier.fillMaxWidth()) {
                Text(
                    entry.line() + if (group.repetitions > 1) "\n× ${group.repetitions} · last ${java.time.Instant.ofEpochMilli(group.lastTime)}" else "",
                    modifier = Modifier.padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (entry.level) {
                        "E", "F" -> MaterialTheme.colorScheme.error
                        "W" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
