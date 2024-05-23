package dev.chungjungsoo.gptmobile.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.chungjungsoo.gptmobile.data.dto.ApiType
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

    override suspend fun getStatus(apiType: ApiType): Boolean? {
        return dataStore.data.map { pref ->
            pref[apiStatusMap[apiType]!!]
        }.first()
    }

    override suspend fun getToken(apiType: ApiType): String? {
        return dataStore.data.map { pref ->
            pref[apiTokenMap[apiType]!!]
        }.first()
    }

    override suspend fun getModel(apiType: ApiType): String? {
        return dataStore.data.map { pref ->
            pref[apiModelMap[apiType]!!]
        }.first()
    }
}
