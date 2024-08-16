package dev.chungjungsoo.gptmobile.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingDataSource {
    private val apiStatusMap = mapOf(
        ApiType.OPENAI to booleanPreferencesKey("openai_status"),
        ApiType.ANTHROPIC to booleanPreferencesKey("anthropic_status"),
        ApiType.GOOGLE to booleanPreferencesKey("google_status")
    )
    private val apiUrlMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_url"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_url"),
        ApiType.GOOGLE to stringPreferencesKey("google_url")
    )
    private val apiTokenMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_token"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_token"),
        ApiType.GOOGLE to stringPreferencesKey("google_token")
    )
    private val apiModelMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_model"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_model"),
        ApiType.GOOGLE to stringPreferencesKey("google_model")
    )
    private val apiTemperatureMap = mapOf(
        ApiType.OPENAI to floatPreferencesKey("openai_temperature"),
        ApiType.ANTHROPIC to floatPreferencesKey("anthropic_temperature"),
        ApiType.GOOGLE to floatPreferencesKey("google_temperature")
    )
    private val apiTopPMap = mapOf(
        ApiType.OPENAI to floatPreferencesKey("openai_top_p"),
        ApiType.ANTHROPIC to floatPreferencesKey("anthropic_top_p"),
        ApiType.GOOGLE to floatPreferencesKey("google_top_p")
    )
    private val apiSystemPromptMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_system_prompt"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_system_prompt"),
        ApiType.GOOGLE to stringPreferencesKey("google_system_prompt")
    )
    private val dynamicThemeKey = intPreferencesKey("dynamic_mode")
    private val themeModeKey = intPreferencesKey("theme_mode")

    override suspend fun updateDynamicTheme(theme: DynamicTheme) {
        dataStore.edit { pref ->
            pref[dynamicThemeKey] = theme.ordinal
        }
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { pref ->
            pref[themeModeKey] = themeMode.ordinal
        }
    }

    override suspend fun updateStatus(apiType: ApiType, status: Boolean) {
        dataStore.edit { pref ->
            pref[apiStatusMap[apiType]!!] = status
        }
    }

    override suspend fun updateAPIUrl(apiType: ApiType, url: String) {
        dataStore.edit { pref ->
            pref[apiUrlMap[apiType]!!] = url
        }
    }

    override suspend fun updateToken(apiType: ApiType, token: String) {
        dataStore.edit { pref ->
            pref[apiTokenMap[apiType]!!] = token
        }
    }

    override suspend fun updateModel(apiType: ApiType, model: String) {
        dataStore.edit { pref ->
            pref[apiModelMap[apiType]!!] = model
        }
    }

    override suspend fun updateTemperature(apiType: ApiType, temperature: Float) {
        dataStore.edit { pref ->
            pref[apiTemperatureMap[apiType]!!] = temperature
        }
    }

    override suspend fun updateTopP(apiType: ApiType, topP: Float) {
        dataStore.edit { pref ->
            pref[apiTopPMap[apiType]!!] = topP
        }
    }

    override suspend fun updateSystemPrompt(apiType: ApiType, prompt: String) {
        dataStore.edit { pref ->
            pref[apiSystemPromptMap[apiType]!!] = prompt
        }
    }

    override suspend fun getDynamicTheme(): DynamicTheme? {
        val mode = dataStore.data.map { pref ->
            pref[dynamicThemeKey]
        }.first() ?: return null

        return DynamicTheme.getByValue(mode)
    }

    override suspend fun getThemeMode(): ThemeMode? {
        val mode = dataStore.data.map { pref ->
            pref[themeModeKey]
        }.first() ?: return null

        return ThemeMode.getByValue(mode)
    }

    override suspend fun getStatus(apiType: ApiType): Boolean? = dataStore.data.map { pref ->
        pref[apiStatusMap[apiType]!!]
    }.first()

    override suspend fun getAPIUrl(apiType: ApiType): String? = dataStore.data.map { pref ->
        pref[apiUrlMap[apiType]!!]
    }.first()

    override suspend fun getToken(apiType: ApiType): String? = dataStore.data.map { pref ->
        pref[apiTokenMap[apiType]!!]
    }.first()

    override suspend fun getModel(apiType: ApiType): String? = dataStore.data.map { pref ->
        pref[apiModelMap[apiType]!!]
    }.first()

    override suspend fun getTemperature(apiType: ApiType): Float? = dataStore.data.map { pref ->
        pref[apiTemperatureMap[apiType]!!]
    }.first()

    override suspend fun getTopP(apiType: ApiType): Float? = dataStore.data.map { pref ->
        pref[apiTopPMap[apiType]!!]
    }.first()

    override suspend fun getSystemPrompt(apiType: ApiType): String? = dataStore.data.map { pref ->
        pref[apiSystemPromptMap[apiType]!!]
    }.first()
}
