package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ConversationDelegationSettings
import dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
import dev.chungjungsoo.gptmobile.data.model.isPrivateDestination

@Composable
internal fun ConversationDelegationCard(
    settings: ModelDelegationSettings,
    profiles: List<PlatformV2>,
    usesDefaults: Boolean,
    onChange: (ConversationDelegationSettings?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Enable delegation", style = MaterialTheme.typography.titleSmall)
                    Text("A helper for this conversation", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { enabled ->
                        onChange(ConversationDelegationSettings(enabled, settings.targetProfileUid, settings.allowRemoteWorkers))
                    },
                    modifier = Modifier.testTag("conversation_delegation_toggle")
                )
            }
            if (settings.enabled) {
                Text("Your main model answers. Your delegate helps with research and longer tasks.", style = MaterialTheme.typography.bodySmall)
                DelegateModelDropdown(
                    profiles = profiles,
                    selectedProfileUid = settings.targetProfileUid,
                    enabled = true,
                    automaticLabel = "Settings default",
                    onSelected = { profile ->
                        onChange(ConversationDelegationSettings(true, profile?.uid.orEmpty(), profile?.isPrivateDestination() == false))
                    }
                )
            }
            if (!usesDefaults) {
                TextButton(onClick = { onChange(null) }) { Text("Use settings defaults") }
            }
        }
    }
}
