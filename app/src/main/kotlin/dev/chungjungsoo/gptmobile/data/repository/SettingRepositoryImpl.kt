package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ProviderConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ProviderConnection
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSourceImpl
import dev.chungjungsoo.gptmobile.data.dto.ConfigBackupDto
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.PlatformBackupDto
import dev.chungjungsoo.gptmobile.data.dto.ThemeBackupDto
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.data.ollama.OllamaOptions
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingRepositoryImpl @Inject constructor(
    private val settingDataSource: SettingDataSource,
    private val platformV2Dao: PlatformV2Dao,
    private val providerConnectionDao: ProviderConnectionDao,
    private val chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
    private val secretVault: SecretVault
) : SettingRepository {

    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    // High-performance thread-safe in-memory cache for resolved PlatformV2 list
    private val platformV2Cache = AtomicReference<List<PlatformV2>?>(null)

    override fun invalidatePlatformCache() {
        platformV2Cache.set(null)
    }

    override suspend fun fetchPlatforms(): List<Platform> {
        val pref = settingDataSource.getPreferencesSnapshot()
        val dsImpl = settingDataSource as? SettingDataSourceImpl

        return ApiType.entries.map { apiType ->
            val status = if (dsImpl != null) {
                pref[dsImpl.apiStatusMap[apiType]!!]
            } else {
                settingDataSource.getStatus(apiType)
            }

            val rawUrl = if (dsImpl != null) {
                pref[dsImpl.apiUrlMap[apiType]!!]
            } else {
                settingDataSource.getAPIUrl(apiType)
            }

            val apiUrl = when (apiType) {
                ApiType.OPENAI -> rawUrl ?: ModelConstants.OPENAI_API_URL
                ApiType.ANTHROPIC -> rawUrl ?: ModelConstants.ANTHROPIC_API_URL
                ApiType.GOOGLE -> rawUrl ?: ModelConstants.GOOGLE_API_URL
                ApiType.GROQ -> rawUrl ?: ModelConstants.GROQ_API_URL
                ApiType.OLLAMA -> rawUrl ?: ""
            }

            val token = resolveLegacyToken(apiType)

            val model = if (dsImpl != null) {
                pref[dsImpl.apiModelMap[apiType]!!]
            } else {
                settingDataSource.getModel(apiType)
            }

            val temperature = if (dsImpl != null) {
                pref[dsImpl.apiTemperatureMap[apiType]!!]
            } else {
                settingDataSource.getTemperature(apiType)
            }

            val topP = if (dsImpl != null) {
                pref[dsImpl.apiTopPMap[apiType]!!]
            } else {
                settingDataSource.getTopP(apiType)
            }

            val rawPrompt = if (dsImpl != null) {
                pref[dsImpl.apiSystemPromptMap[apiType]!!]
            } else {
                settingDataSource.getSystemPrompt(apiType)
            }

            val systemPrompt = when (apiType) {
                ApiType.OPENAI -> rawPrompt ?: ModelConstants.OPENAI_PROMPT
                else -> rawPrompt ?: ModelConstants.DEFAULT_PROMPT
            }

            Platform(
                name = apiType,
                enabled = status == true,
                apiUrl = apiUrl,
                token = token,
                model = model,
                temperature = temperature,
                topP = topP,
                systemPrompt = systemPrompt
            )
        }
    }

    override suspend fun fetchPlatformV2s(): List<PlatformV2> {
        val cached = platformV2Cache.get()
        if (cached != null) return cached

        val connections = providerConnectionDao.getConnections().associateBy { it.uid }
        val resolved = platformV2Dao.getPlatforms().map { platform ->
            resolvePlatformToken(platform, connections)
        }
        platformV2Cache.set(resolved)
        return resolved
    }

    override fun observePlatformV2s(): Flow<List<PlatformV2>> = combine(
        platformV2Dao.observePlatforms(),
        providerConnectionDao.observeConnections()
    ) { platforms, connections ->
        val byUid = connections.associateBy { it.uid }
        platforms.map { resolvePlatformToken(it, byUid) }
    }

    override fun observePlatformV2ByUid(uid: String): Flow<PlatformV2?> = combine(
        platformV2Dao.observePlatformByUid(uid),
        providerConnectionDao.observeConnections()
    ) { platform, connections ->
        platform?.let { resolvePlatformToken(it, connections.associateBy(ProviderConnection::uid)) }
    }

    override suspend fun fetchThemes(): ThemeSetting = ThemeSetting(
        dynamicTheme = settingDataSource.getDynamicTheme() ?: DynamicTheme.OFF,
        themeMode = settingDataSource.getThemeMode() ?: ThemeMode.SYSTEM,
        customPrimaryArgb = settingDataSource.getCustomPrimaryArgb(),
        customPalette = settingDataSource.getCustomPalette(),
        savedProfiles = settingDataSource.getSavedThemeProfiles()
    )

    override suspend fun getLocalRuntimeBackend(): LocalRuntimeBackend =
        settingDataSource.getLocalRuntimeBackend()

    override suspend fun updateLocalRuntimeBackend(backend: LocalRuntimeBackend) =
        settingDataSource.updateLocalRuntimeBackend(backend)

    override fun observeLocalRuntimeBackend(): Flow<LocalRuntimeBackend> =
        settingDataSource.observeLocalRuntimeBackend()

    override suspend fun getDebugMode(): Boolean = settingDataSource.getDebugMode()

    override suspend fun updateDebugMode(enabled: Boolean) = settingDataSource.updateDebugMode(enabled)

    override fun observeDebugMode(): Flow<Boolean> = settingDataSource.observeDebugMode()

    override suspend fun getFeatureSettings(): AppFeatureSettings = settingDataSource.getFeatureSettings().also { dev.chungjungsoo.gptmobile.data.network.NetworkClient.diagnosticsEnabled = it.diagnosticsCollection }

    override suspend fun updateFeatureSettings(settings: AppFeatureSettings) {
        settingDataSource.updateFeatureSettings(settings)
        dev.chungjungsoo.gptmobile.data.network.NetworkClient.diagnosticsEnabled = settings.diagnosticsCollection
    }

    override fun observeFeatureSettings(): Flow<AppFeatureSettings> =
        settingDataSource.observeFeatureSettings()

    override suspend fun getFavoriteGroups(): List<String> = settingDataSource.getFavoriteGroups()

    override suspend fun saveFavoriteGroups(groups: List<String>) = settingDataSource.saveFavoriteGroups(groups)

    override fun observeFavoriteGroups(): Flow<List<String>> = settingDataSource.observeFavoriteGroups()

    override suspend fun getFavoriteMessageGroups(): Map<Int, String> = settingDataSource.getFavoriteMessageGroups()

    override suspend fun saveFavoriteMessageGroups(messageGroups: Map<Int, String>) =
        settingDataSource.saveFavoriteMessageGroups(messageGroups)

    override fun observeFavoriteMessageGroups(): Flow<Map<Int, String>> =
        settingDataSource.observeFavoriteMessageGroups()

    override suspend fun migrateToPlatformV2() {
        // Migration is one-way. Once V2 profiles exist, never replace them with
        // legacy DataStore state: doing so can destroy user-edited profiles,
        // credentials, tool bindings, and per-profile settings on a later startup.
        if (platformV2Dao.getPlatforms().isNotEmpty()) {
            return
        }

        val platforms = fetchPlatforms()

        platforms.forEach { platform ->
            val isOllama = platform.name == ApiType.OLLAMA
            val defaultOllamaOptionsJson = if (isOllama) {
                jsonSerializer.encodeToString(OllamaOptions.createDefault())
            } else {
                null
            }
            val profileName = when (platform.name) {
                ApiType.OPENAI -> "OpenAI"
                ApiType.ANTHROPIC -> "Anthropic"
                ApiType.GOOGLE -> "Google"
                ApiType.GROQ -> "Groq"
                ApiType.OLLAMA -> "Ollama"
            }
            val clientType = when (platform.name) {
                ApiType.OPENAI -> ClientType.OPENAI
                ApiType.ANTHROPIC -> ClientType.ANTHROPIC
                ApiType.GOOGLE -> ClientType.GOOGLE
                ApiType.GROQ -> ClientType.GROQ
                ApiType.OLLAMA -> ClientType.OLLAMA
            }
            val connection = addProviderConnection(
                ProviderConnection(
                    name = "$profileName connection",
                    compatibleType = clientType,
                    apiUrl = ModelConstants.normalizeLegacyAPIUrl(platform.apiUrl)
                ),
                platform.token
            )

            addPlatformV2(
                PlatformV2(
                    name = profileName,
                    compatibleType = clientType,
                    enabled = platform.enabled,
                    model = platform.model ?: "",
                    temperature = if (isOllama) OllamaOptions.DEFAULT_TEMPERATURE else platform.temperature,
                    topP = if (isOllama) OllamaOptions.DEFAULT_TOP_P else platform.topP,
                    systemPrompt = platform.systemPrompt,
                    stream = true,
                    reasoning = false,
                    disableAllTools = false,
                    ollamaOptions = defaultOllamaOptionsJson,
                    providerConnectionUid = connection.uid
                )
            )
        }
        invalidatePlatformCache()
    }

    override suspend fun migrateSecrets(): List<SecretMigrationError> = buildList {
        platformV2Dao.getPlatforms().forEach { platform ->
            val plaintext = platform.token ?: return@forEach
            val source = "profile:${platform.uid}"
            try {
                val connectionUid = platform.providerConnectionUid
                if (connectionUid != null) {
                    val connection = providerConnectionDao.getConnection(connectionUid)
                    if (connection != null) {
                        updateProviderConnection(connection, plaintext)
                        platformV2Dao.editPlatform(platform.copy(token = null, secretRef = null))
                    }
                } else {
                    val secretRef = platform.secretRef ?: migratedProfileSecretRef(platform)
                    storeVerified(secretRef, plaintext)
                    platformV2Dao.editPlatform(platform.copy(token = null, secretRef = secretRef))
                }
            } catch (error: Exception) {
                add(SecretMigrationError(source, error.message ?: "Credential migration failed."))
            }
        }

        ApiType.entries.forEach { apiType ->
            val plaintext = settingDataSource.getToken(apiType) ?: return@forEach
            val source = "legacy:${apiType.name}"
            try {
                storeVerified(legacySecretRef(apiType), plaintext)
                settingDataSource.clearToken(apiType)
            } catch (error: Exception) {
                add(SecretMigrationError(source, error.message ?: "Credential migration failed."))
            }
        }
        invalidatePlatformCache()
    }

    override suspend fun updatePlatforms(platforms: List<Platform>) {
        platforms.forEach { platform ->
            settingDataSource.updateStatus(platform.name, platform.enabled)
            settingDataSource.updateAPIUrl(platform.name, platform.apiUrl)

            platform.token?.let { token ->
                if (token.isBlank()) {
                    secretVault.delete(legacySecretRef(platform.name))
                    settingDataSource.clearToken(platform.name)
                } else {
                    storeVerified(legacySecretRef(platform.name), token)
                    settingDataSource.clearToken(platform.name)
                }
            }
            platform.model?.let { settingDataSource.updateModel(platform.name, it) }
            platform.temperature?.let { settingDataSource.updateTemperature(platform.name, it) }
            platform.topP?.let { settingDataSource.updateTopP(platform.name, it) }
            platform.systemPrompt?.let { settingDataSource.updateSystemPrompt(platform.name, it.trim()) }
        }
    }

    override suspend fun updateThemes(themeSetting: ThemeSetting) {
        settingDataSource.updateDynamicTheme(themeSetting.dynamicTheme)
        settingDataSource.updateThemeMode(themeSetting.themeMode)
        settingDataSource.updateCustomPrimaryArgb(themeSetting.customPrimaryArgb)
        settingDataSource.updateCustomPalette(themeSetting.customPalette)
        settingDataSource.updateSavedThemeProfiles(themeSetting.savedProfiles)
    }

    override suspend fun addPlatformV2(platform: PlatformV2) {
        platformV2Dao.addPlatform(securePlatform(platform))
        invalidatePlatformCache()
    }

    override suspend fun updatePlatformV2(platform: PlatformV2) {
        val previous = platform.id.takeIf { it > 0 }?.let { platformV2Dao.getPlatform(it) }
        val previousSecretRef = previous?.secretRef ?: platform.secretRef
        val securedPlatform = securePlatform(platform)
        platformV2Dao.editPlatform(securedPlatform)
        if (securedPlatform.providerConnectionUid == null &&
            previousSecretRef != securedPlatform.secretRef
        ) {
            previousSecretRef?.let { secretVault.delete(it) }
        }
        invalidatePlatformCache()
    }

    override suspend fun deletePlatformV2(platform: PlatformV2) {
        val persisted = platform.id.takeIf { it > 0 }?.let { platformV2Dao.getPlatform(it) }
        val secretRef = persisted?.secretRef ?: platform.secretRef
        val providerConnectionUid = persisted?.providerConnectionUid ?: platform.providerConnectionUid
        chatPlatformModelV2Dao.deleteByPlatformUid(platform.uid)
        platformV2Dao.deletePlatform(platform)
        if (providerConnectionUid == null) {
            secretRef?.let { secretVault.delete(it) }
        }
        invalidatePlatformCache()
    }

    override suspend fun getPlatformV2ById(id: Int): PlatformV2? {
        val cached = platformV2Cache.get()
        if (cached != null) {
            val hit = cached.firstOrNull { it.id == id }
            if (hit != null) return hit
        }
        return platformV2Dao.getPlatform(id)?.let { platform ->
            resolvePlatformToken(platform)
        }
    }

    override suspend fun fetchProviderConnections(): List<ProviderConnection> =
        providerConnectionDao.getConnections()

    override fun observeProviderConnections(): Flow<List<ProviderConnection>> =
        providerConnectionDao.observeConnections()

    override suspend fun getProviderConnection(uid: String): ProviderConnection? =
        providerConnectionDao.getConnection(uid)

    override suspend fun getProviderCredentials(uid: String): String? {
        val connection = checkNotNull(providerConnectionDao.getConnection(uid)) { "Provider connection is unavailable" }
        if (connection.compatibleType == ClientType.FREE) return null
        val reference = connection.secretRef ?: return null
        return checkNotNull(readSecret(reference)) { "Saved provider credentials are unavailable" }
    }

    override suspend fun addProviderConnection(
        connection: ProviderConnection,
        credential: String?
    ): ProviderConnection {
        val secured = secureProviderConnection(connection, credential)
        providerConnectionDao.upsert(secured)
        invalidatePlatformCache()
        return secured
    }

    override suspend fun updateProviderConnection(
        connection: ProviderConnection,
        credential: String?
    ): ProviderConnection {
        val existing = providerConnectionDao.getConnection(connection.uid)
        val base = connection.copy(
            secretRef = connection.secretRef ?: existing?.secretRef,
            updatedAt = System.currentTimeMillis() / 1000
        )
        val secured = secureProviderConnection(base, credential)
        providerConnectionDao.upsert(secured)
        invalidatePlatformCache()
        return secured
    }

    override suspend fun deleteProviderConnection(connection: ProviderConnection): Boolean {
        val persisted = providerConnectionDao.getConnection(connection.uid) ?: return true
        providerConnectionDao.deleteWithProfiles(persisted)
        try {
            persisted.secretRef?.let { secretVault.delete(it) }
        } finally {
            invalidatePlatformCache()
        }
        return true
    }

    override suspend fun exportConfigurationJson(): String {
        val currentPlatforms = fetchPlatformV2s()
        val currentThemes = fetchThemes()
        val currentFavoriteGroups = getFavoriteGroups()
        val currentMessageGroups = getFavoriteMessageGroups()

        val backup = ConfigBackupDto(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            theme = ThemeBackupDto(
                dynamicTheme = currentThemes.dynamicTheme == DynamicTheme.ON,
                themeMode = currentThemes.themeMode.ordinal,
                customPrimaryArgb = currentThemes.customPrimaryArgb,
                customPalette = currentThemes.customPalette,
                savedProfiles = currentThemes.savedProfiles
            ),
            platforms = currentPlatforms.map { p ->
                PlatformBackupDto(
                    name = p.name,
                    compatibleType = p.compatibleType.ordinal,
                    enabled = p.enabled,
                    apiUrl = p.apiUrl,
                    token = p.token ?: "",
                    model = p.model,
                    temperature = p.temperature,
                    topP = p.topP,
                    topK = p.topK,
                    maxTokens = p.maxTokens,
                    accelerator = p.accelerator,
                    systemPrompt = p.systemPrompt,
                    stream = p.stream,
                    reasoning = p.reasoning,
                    timeout = p.timeout,
                    harassmentSafetyThreshold = p.harassmentSafetyThreshold,
                    hateSpeechSafetyThreshold = p.hateSpeechSafetyThreshold,
                    sexuallyExplicitSafetyThreshold = p.sexuallyExplicitSafetyThreshold,
                    dangerousContentSafetyThreshold = p.dangerousContentSafetyThreshold,
                    openRouterRouting = p.openRouterRouting,
                    ollamaOptions = p.ollamaOptions
                )
            },
            favoriteGroups = currentFavoriteGroups,
            favoriteMessageGroups = currentMessageGroups
        )

        return jsonSerializer.encodeToString(backup)
    }

    override suspend fun importConfigurationJson(json: String): Result<Int> = runCatching {
        val backup = jsonSerializer.decodeFromString<ConfigBackupDto>(json)

        backup.theme?.let { themeDto ->
            val dynamicTheme = if (themeDto.dynamicTheme) DynamicTheme.ON else DynamicTheme.OFF
            val themeMode = ThemeMode.getByValue(themeDto.themeMode) ?: ThemeMode.SYSTEM
            updateThemes(ThemeSetting(dynamicTheme = dynamicTheme, themeMode = themeMode, customPrimaryArgb = themeDto.customPrimaryArgb, customPalette = themeDto.customPalette, savedProfiles = themeDto.savedProfiles))
        }

        if (backup.favoriteGroups.isNotEmpty()) {
            saveFavoriteGroups(backup.favoriteGroups)
        }
        if (backup.favoriteMessageGroups.isNotEmpty()) {
            saveFavoriteMessageGroups(backup.favoriteMessageGroups)
        }

        var importedCount = 0
        val existingPlatforms = platformV2Dao.getPlatforms()

        backup.platforms.forEach { pDto ->
            val clientType = ClientType.entries.getOrNull(pDto.compatibleType) ?: ClientType.OPENAI
            val existing = existingPlatforms.firstOrNull { it.name.equals(pDto.name, ignoreCase = true) }

            if (existing != null) {
                val updated = existing.copy(
                    compatibleType = clientType,
                    enabled = pDto.enabled,
                    apiUrl = pDto.apiUrl,
                    token = pDto.token.ifBlank { null },
                    model = pDto.model,
                    temperature = pDto.temperature,
                    topP = pDto.topP,
                    topK = pDto.topK,
                    maxTokens = pDto.maxTokens,
                    accelerator = pDto.accelerator,
                    systemPrompt = pDto.systemPrompt,
                    stream = pDto.stream,
                    reasoning = pDto.reasoning,
                    timeout = pDto.timeout,
                    harassmentSafetyThreshold = pDto.harassmentSafetyThreshold,
                    hateSpeechSafetyThreshold = pDto.hateSpeechSafetyThreshold,
                    sexuallyExplicitSafetyThreshold = pDto.sexuallyExplicitSafetyThreshold,
                    dangerousContentSafetyThreshold = pDto.dangerousContentSafetyThreshold,
                    openRouterRouting = pDto.openRouterRouting,
                    ollamaOptions = pDto.ollamaOptions
                )
                updatePlatformV2(updated)
            } else {
                val newPlatform = PlatformV2(
                    name = pDto.name,
                    compatibleType = clientType,
                    enabled = pDto.enabled,
                    apiUrl = pDto.apiUrl,
                    token = pDto.token.ifBlank { null },
                    model = pDto.model,
                    temperature = pDto.temperature,
                    topP = pDto.topP,
                    topK = pDto.topK,
                    maxTokens = pDto.maxTokens,
                    accelerator = pDto.accelerator,
                    systemPrompt = pDto.systemPrompt,
                    stream = pDto.stream,
                    reasoning = pDto.reasoning,
                    timeout = pDto.timeout,
                    harassmentSafetyThreshold = pDto.harassmentSafetyThreshold,
                    hateSpeechSafetyThreshold = pDto.hateSpeechSafetyThreshold,
                    sexuallyExplicitSafetyThreshold = pDto.sexuallyExplicitSafetyThreshold,
                    dangerousContentSafetyThreshold = pDto.dangerousContentSafetyThreshold,
                    openRouterRouting = pDto.openRouterRouting,
                    disableAllTools = false,
                    ollamaOptions = pDto.ollamaOptions
                )
                addPlatformV2(newPlatform)
            }
            importedCount++
        }

        invalidatePlatformCache()
        importedCount
    }

    private suspend fun securePlatform(platform: PlatformV2): PlatformV2 {
        if (platform.compatibleType == ClientType.FREE) {
            val connection = platform.providerConnectionUid?.let { providerConnectionDao.getConnection(it) }
            require(connection == null || connection.compatibleType == ClientType.FREE) { "Free profiles need a Free connection." }
            val resolved = platform.copy(apiUrl = connection?.apiUrl ?: platform.apiUrl)
            return FreeAiProvider.requireFor(resolved).applyTo(resolved)
        }
        if (platform.providerConnectionUid != null) {
            return platform.copy(token = null, secretRef = null)
        }

        val secret = platform.token
        if (secret == null) {
            return platform.copy(token = null)
        }

        val secretRef = platform.secretRef ?: profileSecretRef(platform.uid)
        storeVerified(secretRef, secret)
        return platform.copy(token = null, secretRef = secretRef)
    }

    private suspend fun resolvePlatformToken(
        platform: PlatformV2,
        connections: Map<String, ProviderConnection>? = null
    ): PlatformV2 {
        val connectionUid = platform.providerConnectionUid
        if (connectionUid != null) {
            val connection = connections?.get(connectionUid)
                ?: providerConnectionDao.getConnection(connectionUid)
            if (connection != null) {
                if (platform.compatibleType == ClientType.FREE) {
                    val resolved = platform.copy(apiUrl = connection.apiUrl)
                    return FreeAiProvider.requireFor(resolved).applyTo(resolved)
                }
                return platform.copy(
                    apiUrl = connection.apiUrl,
                    token = connection.secretRef?.let { readSecret(it) },
                    secretRef = connection.secretRef
                )
            }
        }

        if (platform.compatibleType == ClientType.FREE) return FreeAiProvider.requireFor(platform).applyTo(platform)
        if (platform.token != null) return platform
        val secretRef = platform.secretRef ?: return platform
        return platform.copy(token = readSecret(secretRef))
    }

    private suspend fun secureProviderConnection(
        connection: ProviderConnection,
        credential: String?
    ): ProviderConnection {
        if (connection.compatibleType == ClientType.FREE) {
            val provider = requireNotNull(FreeAiProvider.fromApiUrl(connection.apiUrl)) { "Choose a supported Free provider." }
            connection.secretRef?.let { secretVault.delete(it) }
            return connection.copy(apiUrl = provider.apiUrl, secretRef = null)
        }
        if (credential == null) return connection

        if (credential.isBlank()) {
            connection.secretRef?.let { secretVault.delete(it) }
            return connection.copy(secretRef = null)
        }

        val secretRef = connection.secretRef ?: providerConnectionSecretRef(connection.uid)
        storeVerified(secretRef, credential)
        return connection.copy(secretRef = secretRef)
    }

    private suspend fun resolveLegacyToken(apiType: ApiType): String? = settingDataSource.getToken(apiType)
        ?: readSecret(legacySecretRef(apiType))

    private suspend fun readSecret(secretRef: String): String? {
        val bytes = secretVault.read(secretRef) ?: return null
        return try {
            bytes.decodeToString()
        } finally {
            bytes.fill(0)
        }
    }

    private suspend fun storeVerified(secretRef: String, secret: String) {
        val bytes = secret.encodeToByteArray()
        try {
            secretVault.put(secretRef, bytes)
            val verified = secretVault.read(secretRef)
            try {
                check(verified != null && verified.contentEquals(bytes)) { "Credential verification failed." }
            } finally {
                verified?.fill(0)
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun profileSecretRef(uid: String): String = "profile_$uid"

    private fun providerConnectionSecretRef(uid: String): String = "provider_connection_$uid"

    private fun migratedProfileSecretRef(platform: PlatformV2): String = platform.id.takeIf { it > 0 }?.let { "room_profile_$it" } ?: profileSecretRef(platform.uid)

    private fun legacySecretRef(apiType: ApiType): String = "legacy_${apiType.name.lowercase()}"
}
