package dev.chungjungsoo.gptmobile.data.datastore

import androidx.datastore.preferences.core.Preferences
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface SettingDataSource {
    suspend fun getPreferencesSnapshot(): Preferences
    suspend fun updateDynamicTheme(theme: DynamicTheme)
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun getSavedThemeProfiles(): List<dev.chungjungsoo.gptmobile.data.dto.SavedThemeProfile> = emptyList()
    suspend fun updateSavedThemeProfiles(profiles: List<dev.chungjungsoo.gptmobile.data.dto.SavedThemeProfile>) = Unit
    suspend fun getCustomPalette(): dev.chungjungsoo.gptmobile.data.dto.CustomThemePalette? = null
    suspend fun updateCustomPalette(palette: dev.chungjungsoo.gptmobile.data.dto.CustomThemePalette?) = Unit
    suspend fun updateCustomPrimaryArgb(argb: Long?) = Unit
    suspend fun updateLocalRuntimeBackend(backend: LocalRuntimeBackend)
    suspend fun updateDebugMode(enabled: Boolean)
    suspend fun updateFeatureSettings(settings: AppFeatureSettings) = Unit
    suspend fun getFeatureSettings(): AppFeatureSettings = AppFeatureSettings()
    fun observeFeatureSettings(): Flow<AppFeatureSettings> = flowOf(AppFeatureSettings())
    suspend fun getDebugMode(): Boolean
    fun observeDebugMode(): Flow<Boolean>
    suspend fun updateStatus(apiType: ApiType, status: Boolean)
    suspend fun updateAPIUrl(apiType: ApiType, url: String)
    suspend fun updateToken(apiType: ApiType, token: String)
    suspend fun clearToken(apiType: ApiType)
    suspend fun updateModel(apiType: ApiType, model: String)
    suspend fun updateTemperature(apiType: ApiType, temperature: Float)
    suspend fun updateTopP(apiType: ApiType, topP: Float)
    suspend fun updateSystemPrompt(apiType: ApiType, prompt: String)
    suspend fun getDynamicTheme(): DynamicTheme?
    suspend fun getThemeMode(): ThemeMode?
    suspend fun getCustomPrimaryArgb(): Long? = null
    suspend fun getLocalRuntimeBackend(): LocalRuntimeBackend
    fun observeLocalRuntimeBackend(): Flow<LocalRuntimeBackend> = flowOf(LocalRuntimeBackend.DEFAULT)
    suspend fun getStatus(apiType: ApiType): Boolean?
    suspend fun getAPIUrl(apiType: ApiType): String?
    suspend fun getToken(apiType: ApiType): String?
    suspend fun getModel(apiType: ApiType): String?
    suspend fun getTemperature(apiType: ApiType): Float?
    suspend fun getTopP(apiType: ApiType): Float?
    suspend fun getSystemPrompt(apiType: ApiType): String?
    suspend fun getFavoriteGroups(): List<String>
    suspend fun saveFavoriteGroups(groups: List<String>)
    fun observeFavoriteGroups(): Flow<List<String>>
    suspend fun getFavoriteMessageGroups(): Map<Int, String>
    suspend fun saveFavoriteMessageGroups(messageGroups: Map<Int, String>)
    fun observeFavoriteMessageGroups(): Flow<Map<Int, String>>
}
