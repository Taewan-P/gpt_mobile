package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

internal fun toolActivityIcon(name: String): ImageVector {
    val value = name.lowercase()
    return when {
        value.contains("delegate_to_model") || value.contains("delegate") -> Icons.Outlined.Dns
        value.contains("location") || value.contains("map") -> Icons.Outlined.LocationOn
        value.contains("memory") || value.contains("recall") || value.contains("nodes") || value.contains("observations") -> Icons.Outlined.Memory
        value.contains("search") || value.contains("exa") || value.contains("brave") -> Icons.Outlined.Search
        value.contains("github") || value.contains("commit") || value.contains("repository") -> Icons.Outlined.Code
        value.contains("shell") || value.contains("terminal") -> Icons.Outlined.Terminal
        value.contains("image") -> Icons.Outlined.Image
        value.contains("calculate") -> Icons.Outlined.Calculate
        value.contains("directory") || value.contains("folder") -> Icons.Outlined.Folder
        value.contains("document") || value.contains("file") -> Icons.Outlined.Description
        value.contains("web") || value.contains("url") || value.contains("fetch") -> Icons.Outlined.Language
        else -> Icons.Outlined.Build
    }
}
