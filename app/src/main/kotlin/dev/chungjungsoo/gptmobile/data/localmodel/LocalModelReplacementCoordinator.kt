package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LocalModelReplacementRequest(
    val entry: CatalogEntry,
    val target: ResolvedModelDownload,
    val accelerator: String
)

@Singleton
class LocalModelReplacementCoordinator @Inject constructor() {
    private val _pending = MutableStateFlow<List<LocalModelReplacementRequest>>(emptyList())
    val pending: StateFlow<List<LocalModelReplacementRequest>> = _pending.asStateFlow()

    fun request(entry: CatalogEntry, target: ResolvedModelDownload, accelerator: String) {
        val next = LocalModelReplacementRequest(entry, target, accelerator)
        _pending.update { queue ->
            if (queue.any { it.sameTarget(next) }) queue else queue + next
        }
    }

    fun dismiss(request: LocalModelReplacementRequest) {
        _pending.update { queue -> queue.filterNot { it.sameTarget(request) } }
    }
}

internal fun LocalModelReplacementRequest.sameTarget(other: LocalModelReplacementRequest): Boolean = entry.id == other.entry.id &&
    target.downloadUrl == other.target.downloadUrl &&
    target.fileName == other.target.fileName &&
    target.commitHash == other.target.commitHash &&
    target.sizeInBytes == other.target.sizeInBytes
