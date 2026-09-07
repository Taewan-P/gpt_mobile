package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import javax.inject.Inject

class SettingRepositoryImpl @Inject constructor(
    private val settingDataSource: SettingDataSource,
    private val platformV2Dao: PlatformV2Dao,
    private val chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
    private val secretVault: SecretVault
) : SettingRepository {

    override suspend fun fetchPlatforms(): List<Platform> = ApiType.entries.map { apiType ->
        val status = settingDataSource.getStatus(apiType)
        val apiUrl = when (apiType) {
            ApiType.OPENAI -> settingDataSource.getAPIUrl(apiType) ?: ModelConstants.OPENAI_API_URL
            ApiType.ANTHROPIC -> settingDataSource.getAPIUrl(apiType) ?: ModelConstants.ANTHROPIC_API_URL
            ApiType.GOOGLE -> settingDataSource.getAPIUrl(apiType) ?: ModelConstants.GOOGLE_API_URL
            ApiType.GROQ -> settingDataSource.getAPIUrl(apiType) ?: ModelConstants.GROQ_API_URL
            ApiType.OLLAMA -> settingDataSource.getAPIUrl(apiType) ?: ""
        }
        val token = resolveLegacyToken(apiType)
        val model = settingDataSource.getModel(apiType)
        val temperature = settingDataSource.getTemperature(apiType)
        val topP = settingDataSource.getTopP(apiType)
        val systemPrompt = when (apiType) {
            ApiType.OPENAI -> settingDataSource.getSystemPrompt(ApiType.OPENAI) ?: ModelConstants.OPENAI_PROMPT
            ApiType.ANTHROPIC -> settingDataSource.getSystemPrompt(ApiType.ANTHROPIC) ?: ModelConstants.DEFAULT_PROMPT
            ApiType.GOOGLE -> settingDataSource.getSystemPrompt(ApiType.GOOGLE) ?: ModelConstants.DEFAULT_PROMPT
            ApiType.GROQ -> settingDataSource.getSystemPrompt(ApiType.GROQ) ?: ModelConstants.DEFAULT_PROMPT
            ApiType.OLLAMA -> settingDataSource.getSystemPrompt(ApiType.OLLAMA) ?: ModelConstants.DEFAULT_PROMPT
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

    override suspend fun fetchPlatformV2s(): List<PlatformV2> = platformV2Dao.getPlatforms().map { platform ->
        resolvePlatformToken(platform)
    }

    override suspend fun fetchThemes(): ThemeSetting = ThemeSetting(
        dynamicTheme = settingDataSource.getDynamicTheme() ?: DynamicTheme.OFF,
        themeMode = settingDataSource.getThemeMode() ?: ThemeMode.SYSTEM
    )

    override suspend fun migrateToPlatformV2() {
        val leftOverPlatformV2s = fetchPlatformV2s()
        leftOverPlatformV2s.forEach { deletePlatformV2(it) }

        val platforms = fetchPlatforms()

        platforms.forEach { platform ->
            addPlatformV2(
                PlatformV2(
                    name = when (platform.name) {
                        ApiType.OPENAI -> "OpenAI"
                        ApiType.ANTHROPIC -> "Anthropic"
                        ApiType.GOOGLE -> "Google"
                        ApiType.GROQ -> "Groq"
                        ApiType.OLLAMA -> "Ollama"
                    },
                    compatibleType = when (platform.name) {
                        ApiType.OPENAI -> ClientType.OPENAI
                        ApiType.ANTHROPIC -> ClientType.ANTHROPIC
                        ApiType.GOOGLE -> ClientType.GOOGLE
                        ApiType.GROQ -> ClientType.GROQ
                        ApiType.OLLAMA -> ClientType.OLLAMA
                    },
                    enabled = platform.enabled,
                    apiUrl = ModelConstants.normalizeLegacyAPIUrl(platform.apiUrl),
                    token = platform.token,
                    model = platform.model ?: "",
                    temperature = platform.temperature,
                    topP = platform.topP,
                    systemPrompt = platform.systemPrompt,
                    stream = true,
                    reasoning = false
                )
            )
        }
    }

    override suspend fun migrateSecrets(): List<SecretMigrationError> = buildList {
        platformV2Dao.getPlatforms().forEach { platform ->
            val plaintext = platform.token ?: return@forEach
            val source = "profile:${platform.uid}"
            try {
                val secretRef = platform.secretRef ?: migratedProfileSecretRef(platform)
                storeVerified(secretRef, plaintext)
                platformV2Dao.editPlatform(platform.copy(token = null, secretRef = secretRef))
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
    }

    override suspend fun addPlatformV2(platform: PlatformV2) {
        platformV2Dao.addPlatform(securePlatform(platform))
    }

    override suspend fun updatePlatformV2(platform: PlatformV2) {
        val previousSecretRef = platform.secretRef
            ?: platform.id.takeIf { it > 0 }?.let { platformV2Dao.getPlatform(it)?.secretRef }
        val securedPlatform = securePlatform(platform)
        platformV2Dao.editPlatform(securedPlatform)
        if (previousSecretRef != securedPlatform.secretRef) {
            previousSecretRef?.let { secretVault.delete(it) }
        }
    }

    override suspend fun deletePlatformV2(platform: PlatformV2) {
        val secretRef = platform.secretRef
            ?: platform.id.takeIf { it > 0 }?.let { platformV2Dao.getPlatform(it)?.secretRef }
        chatPlatformModelV2Dao.deleteByPlatformUid(platform.uid)
        platformV2Dao.deletePlatform(platform)
        secretRef?.let { secretVault.delete(it) }
    }

    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = platformV2Dao.getPlatform(id)?.let { platform ->
        resolvePlatformToken(platform)
    }

    private suspend fun securePlatform(platform: PlatformV2): PlatformV2 {
        val secret = platform.token
        if (secret == null) {
            return platform.copy(token = null, secretRef = null)
        }

        val secretRef = platform.secretRef ?: profileSecretRef(platform.uid)
        storeVerified(secretRef, secret)
        return platform.copy(token = null, secretRef = secretRef)
    }

    private suspend fun resolvePlatformToken(platform: PlatformV2): PlatformV2 {
        if (platform.token != null) return platform
        val secretRef = platform.secretRef ?: return platform
        return platform.copy(token = readSecret(secretRef))
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

    private fun migratedProfileSecretRef(platform: PlatformV2): String = platform.id.takeIf { it > 0 }?.let { "room_profile_$it" } ?: profileSecretRef(platform.uid)

    private fun legacySecretRef(apiType: ApiType): String = "legacy_${apiType.name.lowercase()}"
}
