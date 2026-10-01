package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingRepositoryConfigBackupTest {

    @Test
    fun `export and import restores platforms and themes`() = runBlocking {
        val initialPlatform = PlatformV2(
            id = 1,
            uid = "remote-1",
            name = "OpenAI Production",
            compatibleType = ClientType.OPENAI,
            apiUrl = "https://api.openai.com/v1/",
            model = "gpt-4o",
            token = "sk-secret-token"
        )
        val platformDao = BackupFakePlatformV2Dao(mutableListOf(initialPlatform))
        val secretVault = BackupFakeSecretVault()
        val settingDataSource = BackupFakeSettingDataSource(
            dynamicTheme = DynamicTheme.OFF,
            themeMode = ThemeMode.DARK
        )
        val repository = SettingRepositoryImpl(
            settingDataSource = settingDataSource,
            platformV2Dao = platformDao,
            providerConnectionDao = FakeProviderConnectionDao(),
            chatPlatformModelV2Dao = BackupFakeChatPlatformModelV2Dao(),
            secretVault = secretVault
        )

        val json = repository.exportConfigurationJson()
        assertTrue(json.contains("OpenAI Production"))

        val palette = dev.chungjungsoo.gptmobile.data.dto.CustomThemePalette(0xFF77CCFF, 0xFFAA88DD, 0xFF101820, 0xFF202830)
        val saved = dev.chungjungsoo.gptmobile.data.dto.SavedThemeProfile("My ocean", palette, ThemeMode.DARK)
        repository.updateThemes(repository.fetchThemes().copy(customPalette = palette, customPrimaryArgb = palette.primary, savedProfiles = listOf(saved)))
        val themeJson = repository.exportConfigurationJson()

        // Clear state
        platformDao.platforms.clear()
        settingDataSource.themeMode = ThemeMode.LIGHT
        settingDataSource.palette = null
        settingDataSource.savedThemes = emptyList()
        settingDataSource.primaryArgb = null

        // Import
        val result = repository.importConfigurationJson(themeJson)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val platforms = repository.fetchPlatformV2s()
        assertEquals(1, platforms.size)
        assertEquals("OpenAI Production", platforms.first().name)
        assertEquals(ThemeMode.DARK, settingDataSource.themeMode)
        assertEquals(palette, repository.fetchThemes().customPalette)
        assertEquals(listOf(saved), repository.fetchThemes().savedProfiles)
        assertEquals(palette.primary, repository.fetchThemes().customPrimaryArgb)
    }

    @Test
    fun `import updates an existing profile by name without duplicating it`() = runBlocking {
        val existingPlatform = PlatformV2(
            id = 1,
            uid = "existing-p1",
            name = "Local Ollama",
            compatibleType = ClientType.OLLAMA,
            apiUrl = "http://192.168.1.100:11434",
            model = "llama3"
        )
        val platformDao = BackupFakePlatformV2Dao(mutableListOf(existingPlatform))
        val secretVault = BackupFakeSecretVault()
        val settingDataSource = BackupFakeSettingDataSource()
        val repository = SettingRepositoryImpl(
            settingDataSource = settingDataSource,
            platformV2Dao = platformDao,
            providerConnectionDao = FakeProviderConnectionDao(),
            chatPlatformModelV2Dao = BackupFakeChatPlatformModelV2Dao(),
            secretVault = secretVault
        )

        val backupJson = """
            {
              "platforms": [
                {
                  "uid": "remote-p1",
                  "name": "local ollama",
                  "compatibleType": ${ClientType.OLLAMA.ordinal},
                  "enabled": true,
                  "token": "",
                  "apiUrl": "http://192.168.1.100:11434",
                  "model": "deepseek-r1:8b"
                }
              ]
            }
        """.trimIndent()

        val result = repository.importConfigurationJson(backupJson)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val platforms = repository.fetchPlatformV2s()
        assertEquals(1, platforms.size)
        val updated = platforms.single()
        assertEquals("Local Ollama", updated.name) // Name casing retained from existing entry
        assertEquals("http://192.168.1.100:11434", updated.apiUrl)
        assertEquals("deepseek-r1:8b", updated.model)
    }
}

private class BackupFakeSecretVault : SecretVault {
    val values = mutableMapOf<String, ByteArray>()

    override suspend fun put(secretRef: String, secret: ByteArray) {
        values[secretRef] = secret.copyOf()
    }

    override suspend fun read(secretRef: String): ByteArray? = values[secretRef]?.copyOf()

    override suspend fun delete(secretRef: String) {
        values.remove(secretRef)
    }
}

private class BackupFakePlatformV2Dao(
    val platforms: MutableList<PlatformV2> = mutableListOf()
) : PlatformV2Dao {
    override suspend fun getPlatforms(): List<PlatformV2> = platforms.toList()

    override fun observePlatforms(): Flow<List<PlatformV2>> = flowOf(platforms.toList())

    override suspend fun getPlatform(id: Int): PlatformV2? = platforms.firstOrNull { it.id == id }

    override suspend fun getPlatformByUid(uid: String): PlatformV2? = platforms.firstOrNull { it.uid == uid }

    override fun observePlatformByUid(uid: String): Flow<PlatformV2?> = flowOf(platforms.firstOrNull { it.uid == uid })

    override suspend fun addPlatform(platform: PlatformV2): Long {
        val persisted = if (platform.id == 0) platform.copy(id = (platforms.maxOfOrNull { it.id } ?: 0) + 1) else platform
        platforms += persisted
        return persisted.id.toLong()
    }

    override suspend fun updateFavorite(platformId: Int, isFavorite: Boolean) {
        val index = platforms.indexOfFirst { it.id == platformId }
        if (index >= 0) platforms[index] = platforms[index].copy(isFavorite = isFavorite)
    }

    override suspend fun updateLabels(platformId: Int, labels: String?) {
        val index = platforms.indexOfFirst { it.id == platformId }
        if (index >= 0) platforms[index] = platforms[index].copy(labels = labels)
    }

    override suspend fun editPlatform(platform: PlatformV2) {
        val index = platforms.indexOfFirst { it.id == platform.id }
        if (index >= 0) platforms[index] = platform
    }

    override suspend fun deleteBindingsByProfileUid(profileUid: String) = Unit

    override suspend fun deletePlatformRow(platform: PlatformV2) {
        platforms.removeAll { it.id == platform.id }
    }
}

private class BackupFakeChatPlatformModelV2Dao : ChatPlatformModelV2Dao {
    override suspend fun getByChatId(chatId: Int): List<ChatPlatformModelV2> = emptyList()
    override suspend fun getChatPlatformModels(): List<ChatPlatformModelV2> = emptyList()
    override suspend fun upsertAll(vararg models: ChatPlatformModelV2) = Unit
    override suspend fun upsertChatPlatformModel(model: ChatPlatformModelV2) = Unit
    override suspend fun deleteByChatId(chatId: Int) = Unit
    override suspend fun deleteByPlatformUid(platformUid: String) = Unit
}

private class BackupFakeSettingDataSource(
    var dynamicTheme: DynamicTheme? = null,
    var themeMode: ThemeMode? = null,
    var localRuntimeBackend: LocalRuntimeBackend = LocalRuntimeBackend.QUALCOMM_QNN,
    var debugMode: Boolean = false,
    var favoriteGroups: List<String> = emptyList(),
    var favoriteMessageGroups: Map<Int, String> = emptyMap()
) : SettingDataSource {
    var palette: dev.chungjungsoo.gptmobile.data.dto.CustomThemePalette? = null
    var savedThemes: List<dev.chungjungsoo.gptmobile.data.dto.SavedThemeProfile> = emptyList()
    var primaryArgb: Long? = null
    override suspend fun getCustomPalette() = palette
    override suspend fun updateCustomPalette(palette: dev.chungjungsoo.gptmobile.data.dto.CustomThemePalette?) {
        this.palette = palette
    }
    override suspend fun getSavedThemeProfiles() = savedThemes
    override suspend fun updateSavedThemeProfiles(profiles: List<dev.chungjungsoo.gptmobile.data.dto.SavedThemeProfile>) {
        savedThemes = profiles
    }
    override suspend fun getCustomPrimaryArgb() = primaryArgb
    override suspend fun updateCustomPrimaryArgb(argb: Long?) {
        primaryArgb = argb
    }

    override suspend fun getPreferencesSnapshot(): androidx.datastore.preferences.core.Preferences =
        androidx.datastore.preferences.core.emptyPreferences()

    override suspend fun updateDynamicTheme(theme: DynamicTheme) {
        dynamicTheme = theme
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        this.themeMode = themeMode
    }

    override suspend fun updateLocalRuntimeBackend(backend: LocalRuntimeBackend) {
        localRuntimeBackend = backend
    }

    override suspend fun updateDebugMode(enabled: Boolean) {
        debugMode = enabled
    }

    override suspend fun getDebugMode(): Boolean = debugMode

    override fun observeDebugMode(): Flow<Boolean> = flowOf(debugMode)

    override suspend fun updateStatus(apiType: ApiType, status: Boolean) = Unit
    override suspend fun updateAPIUrl(apiType: ApiType, url: String) = Unit
    override suspend fun updateToken(apiType: ApiType, token: String) = Unit
    override suspend fun clearToken(apiType: ApiType) = Unit
    override suspend fun updateModel(apiType: ApiType, model: String) = Unit
    override suspend fun updateTemperature(apiType: ApiType, temperature: Float) = Unit
    override suspend fun updateTopP(apiType: ApiType, topP: Float) = Unit
    override suspend fun updateSystemPrompt(apiType: ApiType, prompt: String) = Unit
    override suspend fun getDynamicTheme(): DynamicTheme? = dynamicTheme
    override suspend fun getThemeMode(): ThemeMode? = themeMode
    override suspend fun getStatus(apiType: ApiType): Boolean? = false
    override suspend fun getAPIUrl(apiType: ApiType): String? = null
    override suspend fun getToken(apiType: ApiType): String? = null
    override suspend fun getModel(apiType: ApiType): String? = null
    override suspend fun getTemperature(apiType: ApiType): Float? = null
    override suspend fun getTopP(apiType: ApiType): Float? = null
    override suspend fun getSystemPrompt(apiType: ApiType): String? = null
    override suspend fun getLocalRuntimeBackend(): LocalRuntimeBackend = localRuntimeBackend

    override suspend fun getFavoriteGroups(): List<String> = favoriteGroups
    override suspend fun saveFavoriteGroups(groups: List<String>) {
        favoriteGroups = groups
    }
    override fun observeFavoriteGroups(): Flow<List<String>> = flowOf(favoriteGroups)

    override suspend fun getFavoriteMessageGroups(): Map<Int, String> = favoriteMessageGroups
    override suspend fun saveFavoriteMessageGroups(messageGroups: Map<Int, String>) {
        favoriteMessageGroups = messageGroups
    }
    override fun observeFavoriteMessageGroups(): Flow<Map<Int, String>> = flowOf(favoriteMessageGroups)
}
