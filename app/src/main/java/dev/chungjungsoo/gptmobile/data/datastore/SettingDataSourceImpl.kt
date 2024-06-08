package dev.chungjungsoo.gptmobile.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    override suspend fun getToken(apiType: ApiType): String? = dataStore.data.map { pref ->
        pref[apiTokenMap[apiType]!!]
    }.first()

    override suspend fun getModel(apiType: ApiType): String? = dataStore.data.map { pref ->
        pref[apiModelMap[apiType]!!]
    }.first()
}
