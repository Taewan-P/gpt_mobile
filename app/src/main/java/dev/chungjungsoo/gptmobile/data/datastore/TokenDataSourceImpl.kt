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

class TokenDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : TokenDataSource {
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
}
