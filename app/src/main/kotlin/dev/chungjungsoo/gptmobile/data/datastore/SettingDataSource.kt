package dev.chungjungsoo.gptmobile.data.datastore

import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode

interface SettingDataSource {
    suspend fun updateDynamicTheme(theme: DynamicTheme)
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun updateStatus(apiType: ApiType, status: Boolean)
    suspend fun updateToken(apiType: ApiType, token: String)
    suspend fun updateModel(apiType: ApiType, model: String)
    suspend fun updateTemperature(apiType: ApiType, temperature: Float)
    suspend fun updateTopP(apiType: ApiType, topP: Float)
    suspend fun updateSystemPrompt(apiType: ApiType, prompt: String)
    suspend fun getDynamicTheme(): DynamicTheme?
    suspend fun getThemeMode(): ThemeMode?
    suspend fun getStatus(apiType: ApiType): Boolean?
    suspend fun getToken(apiType: ApiType): String?
    suspend fun getModel(apiType: ApiType): String?
    suspend fun getTemperature(apiType: ApiType): Float?
    suspend fun getTopP(apiType: ApiType): Float?
    suspend fun getSystemPrompt(apiType: ApiType): String?
}
