package dev.chungjungsoo.gptmobile.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingDataSource {
    val apiStatusMap = mapOf(
        ApiType.OPENAI to booleanPreferencesKey("openai_status"),
        ApiType.ANTHROPIC to booleanPreferencesKey("anthropic_status"),
        ApiType.GOOGLE to booleanPreferencesKey("google_status"),
        ApiType.GROQ to booleanPreferencesKey("groq_status"),
        ApiType.OLLAMA to booleanPreferencesKey("ollama_status")
    )
    val apiUrlMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_url"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_url"),
        ApiType.GOOGLE to stringPreferencesKey("google_url"),
        ApiType.GROQ to stringPreferencesKey("groq_url"),
        ApiType.OLLAMA to stringPreferencesKey("ollama_url")
    )
    val apiTokenMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_token"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_token"),
        ApiType.GOOGLE to stringPreferencesKey("google_token"),
        ApiType.GROQ to stringPreferencesKey("groq_token"),
        ApiType.OLLAMA to stringPreferencesKey("ollama_token")
    )
    val apiModelMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_model"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_model"),
        ApiType.GOOGLE to stringPreferencesKey("google_model"),
        ApiType.GROQ to stringPreferencesKey("groq_model"),
        ApiType.OLLAMA to stringPreferencesKey("ollama_model")
    )
    val apiTemperatureMap = mapOf(
        ApiType.OPENAI to floatPreferencesKey("openai_temperature"),
        ApiType.ANTHROPIC to floatPreferencesKey("anthropic_temperature"),
        ApiType.GOOGLE to floatPreferencesKey("google_temperature"),
        ApiType.GROQ to floatPreferencesKey("groq_temperature"),
        ApiType.OLLAMA to floatPreferencesKey("ollama_temperature")
    )
    val apiTopPMap = mapOf(
        ApiType.OPENAI to floatPreferencesKey("openai_top_p"),
        ApiType.ANTHROPIC to floatPreferencesKey("anthropic_top_p"),
        ApiType.GOOGLE to floatPreferencesKey("google_top_p"),
        ApiType.GROQ to floatPreferencesKey("groq_top_p"),
        ApiType.OLLAMA to floatPreferencesKey("ollama_top_p")
    )
    val apiSystemPromptMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_system_prompt"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_system_prompt"),
        ApiType.GOOGLE to stringPreferencesKey("google_system_prompt"),
        ApiType.GROQ to stringPreferencesKey("groq_system_prompt"),
        ApiType.OLLAMA to stringPreferencesKey("ollama_system_prompt")
    )
    val dynamicThemeKey = intPreferencesKey("dynamic_mode")
    val themeModeKey = intPreferencesKey("theme_mode")
    private val savedThemeProfilesKey = stringPreferencesKey("saved_theme_profiles")
    private val customPaletteKey = stringPreferencesKey("custom_theme_palette")
    val customPrimaryArgbKey = longPreferencesKey("custom_primary_argb")
    val localRuntimeBackendKey = stringPreferencesKey("local_runtime_backend")
    val debugModeKey = booleanPreferencesKey("debug_mode")
    val featureSettingsKey = stringPreferencesKey("advanced_feature_settings_json")
    val favoriteGroupsKey = stringPreferencesKey("favorite_groups_json")
    val favoriteMessageGroupsKey = stringPreferencesKey("favorite_message_groups_json")

    private val json = Json { ignoreUnknownKeys = true }

    override fun observePreferences(): Flow<Preferences> = dataStore.data

    override suspend fun getPreferencesSnapshot(): Preferences = dataStore.data.first()

    override suspend fun updateDynamicTheme(theme: DynamicTheme) {
        dataStore.edit { pref ->
            pref[dynamicThemeKey] = theme.ordinal
        }
    }

    override suspend fun getSavedThemeProfiles(): List<dev.chungjungsoo.gptmobile.data.dto.SavedThemeProfile> =
        dataStore.data.first()[savedThemeProfilesKey]?.let { raw ->
            runCatching { json.decodeFromString<List<dev.chungjungsoo.gptmobile.data.dto.SavedThemeProfile>>(raw) }.getOrNull()
        }.orEmpty()

    override suspend fun updateSavedThemeProfiles(profiles: List<dev.chungjungsoo.gptmobile.data.dto.SavedThemeProfile>) {
        dataStore.edit { it[savedThemeProfilesKey] = json.encodeToString(profiles) }
    }

    override suspend fun getCustomPalette(): dev.chungjungsoo.gptmobile.data.dto.CustomThemePalette? =
        dataStore.data.first()[customPaletteKey]?.let { raw ->
            runCatching {
                json.decodeFromString<dev.chungjungsoo.gptmobile.data.dto.CustomThemePalette>(raw)
            }.getOrNull()
        }

    override suspend fun updateCustomPalette(palette: dev.chungjungsoo.gptmobile.data.dto.CustomThemePalette?) {
        dataStore.edit { pref ->
            if (palette == null) pref.remove(customPaletteKey) else pref[customPaletteKey] = json.encodeToString(palette)
        }
    }

    override suspend fun updateCustomPrimaryArgb(argb: Long?) {
        dataStore.edit { pref ->
            if (argb == null) pref.remove(customPrimaryArgbKey) else pref[customPrimaryArgbKey] = argb
        }
    }

    override suspend fun getCustomPrimaryArgb(): Long? = dataStore.data.map { pref -> pref[customPrimaryArgbKey] }.first()

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { pref ->
            pref[themeModeKey] = themeMode.ordinal
        }
    }

    override suspend fun updateLocalRuntimeBackend(backend: LocalRuntimeBackend) {
        dataStore.edit { pref ->
            pref[localRuntimeBackendKey] = backend.name
        }
    }

    override suspend fun updateDebugMode(enabled: Boolean) {
        dataStore.edit { pref ->
            pref[debugModeKey] = enabled
        }
    }

    override suspend fun getDebugMode(): Boolean = dataStore.data.map { pref ->
        pref[debugModeKey] ?: false
    }.first()

    override fun observeDebugMode(): Flow<Boolean> = dataStore.data.map { pref ->
        pref[debugModeKey] ?: false
    }

    override suspend fun updateFeatureSettings(settings: AppFeatureSettings) {
        dataStore.edit { pref ->
            pref[featureSettingsKey] = json.encodeToString(settings)
        }
    }

    override suspend fun getFeatureSettings(): AppFeatureSettings = dataStore.data.map { pref ->
        pref[featureSettingsKey]
            ?.let { raw -> runCatching { json.decodeFromString<AppFeatureSettings>(raw) }.getOrNull() }
            ?: AppFeatureSettings()
    }.first()

    override fun observeFeatureSettings(): Flow<AppFeatureSettings> = dataStore.data.map { pref ->
        pref[featureSettingsKey]
            ?.let { raw -> runCatching { json.decodeFromString<AppFeatureSettings>(raw) }.getOrNull() }
            ?: AppFeatureSettings()
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

    override suspend fun clearToken(apiType: ApiType) {
        dataStore.edit { pref ->
            pref.remove(apiTokenMap[apiType]!!)
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

    override suspend fun getLocalRuntimeBackend(): LocalRuntimeBackend {
        val backendStr = dataStore.data.map { pref ->
            pref[localRuntimeBackendKey]
        }.first()
        return deviceBackend(backendStr)
    }

    override fun observeLocalRuntimeBackend(): Flow<LocalRuntimeBackend> = dataStore.data.map { pref ->
        deviceBackend(pref[localRuntimeBackendKey])
    }

    private fun deviceBackend(value: String?): LocalRuntimeBackend {
        val selected = LocalRuntimeBackend.fromString(value)
        return if (selected == LocalRuntimeBackend.QUALCOMM_QNN && dev.chungjungsoo.gptmobile.data.localruntime.QualcommSocSupport.htpVersion(android.os.Build.SOC_MODEL.orEmpty()) == null) LocalRuntimeBackend.LITERT_LM else selected
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

    override suspend fun getFavoriteGroups(): List<String> = dataStore.data.map { pref ->
        val raw = pref[favoriteGroupsKey]
        if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }.first()

    override suspend fun saveFavoriteGroups(groups: List<String>) {
        val raw = json.encodeToString(groups)
        dataStore.edit { pref ->
            pref[favoriteGroupsKey] = raw
        }
    }

    override fun observeFavoriteGroups(): Flow<List<String>> = dataStore.data.map { pref ->
        val raw = pref[favoriteGroupsKey]
        if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    override suspend fun getFavoriteMessageGroups(): Map<Int, String> = dataStore.data.map { pref ->
        val raw = pref[favoriteMessageGroupsKey]
        if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<Map<Int, String>>(raw) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
    }.first()

    override suspend fun saveFavoriteMessageGroups(messageGroups: Map<Int, String>) {
        val raw = json.encodeToString(messageGroups)
        dataStore.edit { pref ->
            pref[favoriteMessageGroupsKey] = raw
        }
    }

    override fun observeFavoriteMessageGroups(): Flow<Map<Int, String>> = dataStore.data.map { pref ->
        val raw = pref[favoriteMessageGroupsKey]
        if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<Map<Int, String>>(raw) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
    }
}
