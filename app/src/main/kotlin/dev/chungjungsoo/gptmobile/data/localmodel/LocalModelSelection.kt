package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository

internal data class LocalModelSelection(val modelId: String, val record: LocalModel?, val path: String)

/** Keep benchmark preflight and inference on the same installed package, including legacy IDs. */
internal suspend fun LocalModelRepository.resolveLocalModelSelection(modelId: String, accelerator: String?): LocalModelSelection {
    val wantsNpu = LocalAccelerators.normalize(accelerator) == LocalAccelerators.NPU
    var id = modelId
    var record = getById(id)
    var path = resolveDownloadedPath(id)
    val installedNpu = (record?.fileName ?: path)?.let(LocalModelPackages::isNpuFile) == true
    if (!wantsNpu && (path == null || installedNpu)) {
        id = "$modelId-litert"
        path = resolveDownloadedPath(id)
        record = getById(id)
    }
    check(path != null) {
        if (!wantsNpu && installedNpu) {
            "This download is an NPU package. Download its GPU / CPU edition in Local models, or select NPU in this profile."
        } else {
            "This Local Model is not downloaded. Download it from Settings → Local Models."
        }
    }
    check(wantsNpu || !LocalModelPackages.isNpuFile(path)) {
        "The selected download is an NPU package. Download its GPU / CPU edition to use this accelerator."
    }
    return LocalModelSelection(id, record, path)
}
