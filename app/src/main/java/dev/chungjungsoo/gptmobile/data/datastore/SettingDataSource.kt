package dev.chungjungsoo.gptmobile.data.datastore

import dev.chungjungsoo.gptmobile.data.model.ApiType

interface SettingDataSource {
    suspend fun updateStatus(apiType: ApiType, status: Boolean)
    suspend fun updateToken(apiType: ApiType, token: String)
    suspend fun updateModel(apiType: ApiType, model: String)
    suspend fun getStatus(apiType: ApiType): Boolean?
    suspend fun getToken(apiType: ApiType): String?
    suspend fun getModel(apiType: ApiType): String?
}
