package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.localmodel.ResolvedModelDownload
import dev.chungjungsoo.gptmobile.data.localmodel.SocVariantResolver

data class LocalModelExecutionTarget(
    val accelerator: String,
    val download: ResolvedModelDownload
)

fun localModelExecutionTargets(
    entry: CatalogEntry,
    preference: String?,
    deviceSocModel: String,
    isNpuAvailable: Boolean
): List<LocalModelExecutionTarget> {
    val order = when (preference?.lowercase()) {
        "auto", LocalAccelerators.NPU -> listOf(LocalAccelerators.NPU, LocalAccelerators.GPU, LocalAccelerators.CPU)
        LocalAccelerators.GPU -> listOf(LocalAccelerators.GPU, LocalAccelerators.CPU)
        else -> listOf(LocalAccelerators.CPU)
    }
    return order.mapNotNull { accelerator ->
        if (entry.supportedAccelerators.none { it.equals(accelerator, ignoreCase = true) }) return@mapNotNull null
        if (accelerator == LocalAccelerators.NPU && !isNpuAvailable) return@mapNotNull null
        SocVariantResolver.resolve(entry, deviceSocModel, accelerator)?.let { LocalModelExecutionTarget(accelerator, it) }
    }
}

fun ResolvedModelDownload.matches(model: LocalModel): Boolean = fileName == model.fileName && commitHash == model.commitHash
