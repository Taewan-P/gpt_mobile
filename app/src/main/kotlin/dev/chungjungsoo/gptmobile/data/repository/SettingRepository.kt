package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ProviderConnection
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface SettingRepository {
    fun invalidatePlatformCache() {}

    suspend fun fetchPlatforms(): List<Platform>
    suspend fun fetchPlatformV2s(): List<PlatformV2>
    fun observePlatformV2s(): Flow<List<PlatformV2>>
    fun observePlatformV2ByUid(uid: String): Flow<PlatformV2?>
    fun observeThemes(): Flow<ThemeSetting> = kotlinx.coroutines.flow.flow { emit(fetchThemes()) }
    suspend fun fetchThemes(): ThemeSetting
    suspend fun getLocalRuntimeBackend(): LocalRuntimeBackend
    suspend fun updateLocalRuntimeBackend(backend: LocalRuntimeBackend)
    fun observeLocalRuntimeBackend(): Flow<LocalRuntimeBackend> = flowOf(LocalRuntimeBackend.DEFAULT)
    suspend fun getDebugMode(): Boolean
    suspend fun updateDebugMode(enabled: Boolean)
    fun observeDebugMode(): Flow<Boolean>
    suspend fun getFeatureSettings(): AppFeatureSettings = AppFeatureSettings()
    suspend fun updateFeatureSettings(settings: AppFeatureSettings) = Unit
    fun observeFeatureSettings(): Flow<AppFeatureSettings> = flowOf(AppFeatureSettings())
    suspend fun migrateToPlatformV2()
    suspend fun migrateSecrets(): List<SecretMigrationError>
    suspend fun updatePlatforms(platforms: List<Platform>)
    suspend fun updateThemes(themeSetting: ThemeSetting)

    // Favorite Groups & Message Groups persistence
    suspend fun getFavoriteGroups(): List<String>
    suspend fun saveFavoriteGroups(groups: List<String>)
    fun observeFavoriteGroups(): Flow<List<String>>
    suspend fun getFavoriteMessageGroups(): Map<Int, String>
    suspend fun saveFavoriteMessageGroups(messageGroups: Map<Int, String>)
    fun observeFavoriteMessageGroups(): Flow<Map<Int, String>>

    // PlatformV2 CRUD operations
    suspend fun addPlatformV2(platform: PlatformV2)
    suspend fun updatePlatformV2(platform: PlatformV2)
    suspend fun deletePlatformV2(platform: PlatformV2)
    suspend fun getPlatformV2ById(id: Int): PlatformV2?

    // Reusable provider connections. Credentials remain in SecretVault.
    suspend fun fetchProviderConnections(): List<ProviderConnection>
    fun observeProviderConnections(): Flow<List<ProviderConnection>>
    suspend fun getProviderConnection(uid: String): ProviderConnection?
    suspend fun getProviderCredentials(uid: String): String? = null
    suspend fun addProviderConnection(connection: ProviderConnection, credential: String? = null): ProviderConnection
    suspend fun updateProviderConnection(connection: ProviderConnection, credential: String? = null): ProviderConnection
    suspend fun deleteProviderConnection(connection: ProviderConnection): Boolean

    // Backup & Restore
    suspend fun exportConfigurationJson(): String
    suspend fun importConfigurationJson(json: String): Result<Int>
}

data class SecretMigrationError(val source: String, val message: String)
