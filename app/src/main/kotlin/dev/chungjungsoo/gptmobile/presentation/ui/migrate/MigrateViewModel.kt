package dev.chungjungsoo.gptmobile.presentation.ui.migrate

import androidx.lifecycle.ViewModel

class MigrateViewModel : ViewModel() {
    enum class MigrationState {
        READY,
        MIGRATED,
        ERROR,
        BLOCKED
    }
}
