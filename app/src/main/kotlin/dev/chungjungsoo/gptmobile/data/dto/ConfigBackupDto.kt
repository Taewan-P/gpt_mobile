package dev.chungjungsoo.gptmobile.data.dto

import dev.chungjungsoo.gptmobile.data.model.GeminiSafetySettings
import kotlinx.serialization.Serializable

@Serializable
data class ConfigBackupDto(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val theme: ThemeBackupDto? = null,
    val platforms: List<PlatformBackupDto> = emptyList(),
    val favoriteGroups: List<String> = emptyList(),
    val favoriteMessageGroups: Map<Int, String> = emptyMap()
)

@Serializable
data class ThemeBackupDto(
    val dynamicTheme: Boolean = false,
    val themeMode: Int = 0,
    val customPrimaryArgb: Long? = null,
    val customPalette: CustomThemePalette? = null,
    val savedProfiles: List<SavedThemeProfile> = emptyList()
)

@Serializable
data class PlatformBackupDto(
    val name: String,
    val compatibleType: Int,
    val enabled: Boolean,
    val apiUrl: String,
    val token: String,
    val model: String,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxTokens: Int? = null,
    val accelerator: String? = null,
    val systemPrompt: String? = null,
    val stream: Boolean = true,
    val reasoning: Boolean = false,
    val timeout: Int = 30,
    val harassmentSafetyThreshold: String = GeminiSafetySettings.BLOCK_NONE,
    val hateSpeechSafetyThreshold: String = GeminiSafetySettings.BLOCK_NONE,
    val sexuallyExplicitSafetyThreshold: String = GeminiSafetySettings.BLOCK_NONE,
    val dangerousContentSafetyThreshold: String = GeminiSafetySettings.BLOCK_NONE,
    val openRouterRouting: String? = null,
    val ollamaOptions: String? = null
)
