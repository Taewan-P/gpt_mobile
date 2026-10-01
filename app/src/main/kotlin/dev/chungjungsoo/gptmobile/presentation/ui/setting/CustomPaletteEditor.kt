package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.dto.CustomThemePalette
import dev.chungjungsoo.gptmobile.presentation.common.LocalCustomPalette
import dev.chungjungsoo.gptmobile.presentation.common.LocalThemeViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomPaletteEditor() {
    val current = LocalCustomPalette.current
    val theme = LocalThemeViewModel.current
    val saved by theme.themeSetting.collectAsStateWithLifecycle()
    var profileName by rememberSaveable { mutableStateOf("") }
    var customizing by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val labels = listOf("Accent", "Secondary", "Background", "Cards")
    val initial = listOf(current?.primary ?: scheme.primary.toArgb().toLong(), current?.secondary ?: scheme.secondary.toArgb().toLong(), current?.background ?: scheme.background.toArgb().toLong(), current?.surface ?: scheme.surface.toArgb().toLong())
    var values by rememberSaveable(current) { mutableStateOf(initial.map { "#%06X".format(it and 0xFFFFFF) }) }
    var selected by rememberSaveable { mutableStateOf(0) }
    val colors = values.map { text -> text.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { it or 0xFF000000 } }
    fun setColor(color: Color) {
        values = values.toMutableList().apply { set(selected, "#%06X".format(color.toArgb() and 0xFFFFFF)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var menuOpen by remember { mutableStateOf(false) }
        val selectedProfile = saved.savedProfiles.firstOrNull { it.palette == current }
            ?: dev.chungjungsoo.gptmobile.data.dto.ThemePresets.profiles.firstOrNull { it.palette == current }
        Text("Choose your theme", style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedProfile?.name ?: if (current == null) "Default" else "Custom palette", modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Default") }, onClick = { theme.updateCustomPalette(null); menuOpen = false })
                Text("Presets", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                dev.chungjungsoo.gptmobile.data.dto.ThemePresets.profiles.forEach { preset ->
                    DropdownMenuItem(text = { Text(preset.name) }, leadingIcon = {
                        Surface(color = Color(preset.palette.primary), shape = MaterialTheme.shapes.small, modifier = Modifier.size(18.dp)) {}
                    }, onClick = { theme.applyProfile(preset); menuOpen = false })
                }
                if (saved.savedProfiles.isNotEmpty()) {
                    Text("Saved themes", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                    saved.savedProfiles.forEach { profile ->
                        DropdownMenuItem(text = { Text(profile.name) }, leadingIcon = {
                            Surface(color = Color(profile.palette.primary), shape = MaterialTheme.shapes.small, modifier = Modifier.size(18.dp)) {}
                        }, onClick = { theme.applyProfile(profile); menuOpen = false })
                    }
                }
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A theme that feels like you", style = MaterialTheme.typography.titleMedium)
                Text("Preview your chat colours, cards and accents.", color = scheme.onSurfaceVariant)
                Surface(color = scheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text("Your next idea starts here", Modifier.padding(12.dp), color = scheme.onPrimaryContainer)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(scheme.primary, scheme.secondary, scheme.background, scheme.surface).forEach { color ->
                        Surface(color = color, shape = MaterialTheme.shapes.small, modifier = Modifier.size(28.dp)) {}
                    }
                }
            }
        }
        selectedProfile?.takeIf { it in saved.savedProfiles }?.let { profile ->
            TextButton(onClick = { theme.deleteProfile(profile.name) }) { Text("Delete saved theme ${profile.name}") }
        }
        Text("Saved themes are included in Backup and restored with your settings.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        TextButton(onClick = { customizing = !customizing }) { Text(if (customizing) "Hide custom colours" else "Create a theme") }
        if (customizing) {
            Text("Your palette", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { index, label ->
                    FilterChip(selected = selected == index, onClick = { selected = index }, label = { Text(label) }, leadingIcon = {
                        Surface(color = colors[index]?.let(::Color) ?: scheme.surface, shape = MaterialTheme.shapes.small, modifier = Modifier.size(16.dp)) {}
                    })
                }
            }
            ColorWheelPicker(color = colors[selected]?.let(::Color) ?: scheme.primary, onColorChange = ::setColor)
            OutlinedTextField(value = values[selected], onValueChange = { value -> values = values.toMutableList().apply { set(selected, value.take(7)) } }, label = { Text("${labels[selected]} HEX") }, singleLine = true, isError = colors[selected] == null, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Cyan" to 0xFF00BCD4, "Violet" to 0xFFB39DDB, "Coral" to 0xFFFF8A80, "Mint" to 0xFF80CBC4).forEach { (label, value) ->
                    TextButton(onClick = { setColor(Color(value)) }) { Text(label) }
                }
            }
            if (colors.all { it != null }) {
                fun foreground(value: Long) = if (androidx.core.graphics.ColorUtils.calculateLuminance(value.toInt()) > 0.179) Color.Black else Color.White
                Surface(color = Color(colors[2]!!), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Live preview", color = foreground(colors[2]!!), style = MaterialTheme.typography.labelMedium)
                        Surface(color = Color(colors[3]!!), shape = MaterialTheme.shapes.medium) {
                            Text("Your next conversation", Modifier.padding(12.dp), color = foreground(colors[3]!!))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            colors.take(2).forEachIndexed { i, value ->
                                Surface(color = Color(value!!), shape = MaterialTheme.shapes.small) { Text(labels[i], Modifier.padding(8.dp), color = foreground(value)) }
                            }
                        }
                    }
                }
            }
            Button(onClick = { theme.updateCustomPalette(CustomThemePalette(colors[0]!!, colors[1]!!, colors[2]!!, colors[3]!!)) }, enabled = colors.all { it != null }, modifier = Modifier.fillMaxWidth()) { Text("Apply palette") }
            OutlinedTextField(value = profileName, onValueChange = { profileName = it.take(40) }, label = { Text("Theme name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                theme.saveProfile(profileName, CustomThemePalette(colors[0]!!, colors[1]!!, colors[2]!!, colors[3]!!))
                profileName = ""
            }, enabled = profileName.isNotBlank() && colors.all { it != null }, modifier = Modifier.fillMaxWidth()) { Text("Save and apply theme") }
        }
        TextButton(onClick = { theme.updateCustomPalette(null) }, modifier = Modifier.fillMaxWidth()) { Text("Restore default palette") }
    }
}

@Composable
private fun ColorWheelPicker(color: Color, onColorChange: (Color) -> Unit) {
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    val currentHsv by rememberUpdatedState(hsv)
    val change by rememberUpdatedState(onColorChange)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Canvas(
            Modifier.size(220.dp).semantics { contentDescription = "Colour wheel. Hue, saturation and brightness can also be adjusted with the sliders below." }
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val delta = position - center
                        val hue = ((Math.toDegrees(atan2(delta.y.toDouble(), delta.x.toDouble())) + 360) % 360).toFloat()
                        change(Color.hsv(hue, (delta.getDistance() / (size.width / 2f)).coerceIn(0f, 1f), currentHsv[2]))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { event, _ ->
                        event.consume()
                        val delta = event.position - Offset(size.width / 2f, size.height / 2f)
                        val hue = ((Math.toDegrees(atan2(delta.y.toDouble(), delta.x.toDouble())) + 360) % 360).toFloat()
                        change(Color.hsv(hue, (delta.getDistance() / (size.width / 2f)).coerceIn(0f, 1f), currentHsv[2]))
                    }
                }
        ) {
            drawCircle(Brush.sweepGradient((0..6).map { Color.hsv((it * 60f).coerceAtMost(360f), 1f, 1f) }))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), radius = size.minDimension / 2))
            drawCircle(Color.Black.copy(alpha = 1 - hsv[2]))
            val angle = Math.toRadians(hsv[0].toDouble())
            val radius = hsv[1] * (size.minDimension / 2 - 5.dp.toPx())
            val point = center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
            drawCircle(Color.Black, 7.dp.toPx(), point, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, 7.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
        }
        listOf("Hue", "Saturation", "Brightness").forEachIndexed { index, label ->
            Text(label, modifier = Modifier.align(Alignment.Start), style = MaterialTheme.typography.labelMedium)
            Slider(value = hsv[index], onValueChange = { value ->
                val updated = hsv.copyOf().apply { set(index, value) }
                onColorChange(Color.hsv(updated[0], updated[1], updated[2]))
            }, valueRange = 0f..(if (index == 0) 360f else 1f), modifier = Modifier.semantics { contentDescription = label })
        }
    }
}
