package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry

data class LocalSamplingDefaults(
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val maxTokens: Int,
    val accelerator: String
)

fun localSamplingDefaults(
    entry: CatalogEntry,
    deviceSocModel: String = ""
): LocalSamplingDefaults = LocalSamplingDefaults(
    temperature = entry.defaultConfig.temperature,
    topP = entry.defaultConfig.topP,
    topK = entry.defaultConfig.topK,
    maxTokens = entry.defaultConfig.maxTokens,
    accelerator = LocalAccelerators.defaultFrom(
        supported = entry.supportedAccelerators,
        socToModelFiles = entry.socToModelFiles,
        deviceSocModel = deviceSocModel
    )
)
