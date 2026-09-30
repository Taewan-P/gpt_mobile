package dev.chungjungsoo.gptmobile.data.model

import kotlinx.serialization.Serializable

/** A conversation override; detailed budgets continue to use the global settings. */
@Serializable
data class ConversationDelegationSettings(
    val enabled: Boolean,
    val targetProfileUid: String = "",
    val allowRemoteWorker: Boolean = false
) {
    fun applyTo(defaults: ModelDelegationSettings): ModelDelegationSettings = defaults.copy(
        enabled = enabled,
        targetProfileUid = targetProfileUid.ifBlank { defaults.targetProfileUid },
        allowRemoteWorkers = defaults.allowRemoteWorkers || allowRemoteWorker,
        fallbackToAnotherProfile = false
    ).normalized()
}
