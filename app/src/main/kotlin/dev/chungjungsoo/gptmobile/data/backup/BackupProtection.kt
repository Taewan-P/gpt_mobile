package dev.chungjungsoo.gptmobile.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupProtection(val enabled: Boolean = false, val password: String = "") {
    override fun toString(): String = "BackupProtection(enabled=$enabled, password=<redacted>)"
}
