package dev.chungjungsoo.gptmobile.data.catalog

import kotlinx.serialization.Serializable

@Serializable
data class ModelCatalog(
    val schemaVersion: Int = 0,
    val models: List<CatalogEntry> = emptyList()
)

@Serializable
data class CatalogEntry(
    val id: String = "",
    val displayName: String = "",
    val downloadUrl: String = "",
    val sizeInBytes: Long = 0L,
    val minRamGb: Int = 0,
    val isGated: Boolean = false,
    val capabilities: CatalogCapabilities = CatalogCapabilities(),
    val supportedAccelerators: List<String> = emptyList(),
    val defaultConfig: CatalogDefaultConfig = CatalogDefaultConfig(),
    val minAppVersion: String = "0.0.0",
    val socToModelFiles: Map<String, SocVariant> = emptyMap()
)

@Serializable
data class CatalogCapabilities(
    val vision: Boolean = false,
    val tools: Boolean = false,
    val thinking: Boolean = false
)

@Serializable
data class CatalogDefaultConfig(
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val temperature: Float = 1.0f,
    val maxTokens: Int = 1024
)

@Serializable
data class SocVariant(
    val modelFile: String = "",
    val downloadUrl: String = "",
    val commitHash: String = "",
    val sizeInBytes: Long = 0L,
    val contextSize: Int = 0,
    val quantization: String = ""
)
