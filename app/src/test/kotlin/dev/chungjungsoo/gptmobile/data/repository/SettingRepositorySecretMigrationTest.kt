package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingRepositorySecretMigrationTest {
    @Test
    fun `profile CRUD stores only a verified vault reference and resolves transient tokens`() = runBlocking {
        val dao = FakePlatformV2Dao()
        val vault = FakeSecretVault()
        val repository = createRepository(dao, vault)
        val platform = testPlatform(token = "profile-secret")

        repository.addPlatformV2(platform)

        assertEquals("profile-secret", vault.values.getValue("profile_profile-1").decodeToString())
        assertNull(dao.platforms.single().token)
        assertEquals("profile_profile-1", dao.platforms.single().secretRef)
        assertEquals("profile-secret", repository.fetchPlatformV2s().single().token)

        val fetched = repository.fetchPlatformV2s().single()
        repository.updatePlatformV2(fetched.copy(name = "Renamed"))
        assertNull(dao.platforms.single().token)
        assertEquals("Renamed", dao.platforms.single().name)

        repository.deletePlatformV2(fetched)
        assertTrue(dao.platforms.isEmpty())
        assertNull(vault.values["profile_profile-1"])
    }

    @Test
    fun `resolved vault bytes are wiped after creating the transient token`() = runBlocking {
        val dao = FakePlatformV2Dao(
            mutableListOf(testPlatform(token = null).copy(secretRef = "profile_profile-1"))
        )
        val vault = FakeSecretVault().apply {
            values["profile_profile-1"] = "profile-secret".encodeToByteArray()
        }
        val repository = createRepository(dao, vault)

        assertEquals("profile-secret", repository.fetchPlatformV2s().single().token)
        assertTrue(vault.lastReadBytes?.all { it == 0.toByte() } == true)
    }

    @Test
    fun `room plaintext migration clears token only after byte verification`() = runBlocking {
        val dao = FakePlatformV2Dao(mutableListOf(testPlatform(token = "legacy-room-secret")))
        val vault = FakeSecretVault()
        val repository = createRepository(dao, vault)

        val errors = repository.migrateSecrets()

        assertTrue(errors.isEmpty())
        assertNull(dao.platforms.single().token)
        assertEquals("profile_profile-1", dao.platforms.single().secretRef)
        assertEquals("legacy-room-secret", vault.values.getValue("profile_profile-1").decodeToString())
    }

    @Test
    fun `room plaintext migration keeps duplicate profile uid credentials distinct`() = runBlocking {
        val dao = FakePlatformV2Dao(
            mutableListOf(
                testPlatform(token = "first-secret").copy(id = 1),
                testPlatform(token = "second-secret").copy(id = 2)
            )
        )
        val vault = FakeSecretVault()
        val repository = createRepository(dao, vault)

        val errors = repository.migrateSecrets()

        assertTrue(errors.isEmpty())
        assertEquals(listOf("first-secret", "second-secret"), repository.fetchPlatformV2s().map { it.token })
        assertEquals(2, dao.platforms.mapNotNull { it.secretRef }.distinct().size)
    }

    @Test
    fun `explicitly clearing a profile token removes its vault record and reference`() = runBlocking {
        val dao = FakePlatformV2Dao()
        val vault = FakeSecretVault()
        val repository = createRepository(dao, vault)
        repository.addPlatformV2(testPlatform(token = "profile-secret"))

        repository.updatePlatformV2(repository.fetchPlatformV2s().single().copy(token = null))

        assertNull(dao.platforms.single().token)
        assertNull(dao.platforms.single().secretRef)
        assertNull(vault.values["profile_profile-1"])
    }

    @Test
    fun `failed profile clear keeps the existing vault credential`() = runBlocking {
        val dao = FakePlatformV2Dao()
        val vault = FakeSecretVault()
        val repository = createRepository(dao, vault)
        repository.addPlatformV2(testPlatform(token = "profile-secret"))
        dao.failEdits = true

        try {
            repository.updatePlatformV2(repository.fetchPlatformV2s().single().copy(token = null))
            fail("Expected the database update to fail")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals("profile-secret", vault.values.getValue("profile_profile-1").decodeToString())
        assertEquals("profile_profile-1", dao.platforms.single().secretRef)
    }

    @Test
    fun `room plaintext migration failure is recoverable and retains plaintext`() = runBlocking {
        val original = testPlatform(token = "keep-me")
        val dao = FakePlatformV2Dao(mutableListOf(original))
        val vault = FakeSecretVault(readOverride = "different".encodeToByteArray())
        val repository = createRepository(dao, vault)

        val errors = repository.migrateSecrets()

        assertEquals(1, errors.size)
        assertEquals("profile:profile-1", errors.single().source)
        assertEquals(original, dao.platforms.single())
    }

    @Test
    fun `legacy datastore migration clears token only after verification and remains readable`() = runBlocking {
        val dataSource = FakeSettingDataSource(mutableMapOf(ApiType.OPENAI to "legacy-datastore-secret"))
        val vault = FakeSecretVault()
        val repository = createRepository(FakePlatformV2Dao(), vault, dataSource)

        val errors = repository.migrateSecrets()

        assertTrue(errors.isEmpty())
        assertNull(dataSource.tokens[ApiType.OPENAI])
        assertEquals("legacy-datastore-secret", repository.fetchPlatforms().first { it.name == ApiType.OPENAI }.token)
    }

    @Test
    fun `legacy datastore verification failure retains plaintext`() = runBlocking {
        val dataSource = FakeSettingDataSource(mutableMapOf(ApiType.OPENAI to "keep-legacy"))
        val vault = FakeSecretVault(readOverride = "different".encodeToByteArray())
        val repository = createRepository(FakePlatformV2Dao(), vault, dataSource)

        val errors = repository.migrateSecrets()

        assertEquals(1, errors.size)
        assertEquals("legacy:OPENAI", errors.single().source)
        assertEquals("keep-legacy", dataSource.tokens[ApiType.OPENAI])
    }

    @Test
    fun `legacy profile conversion deletes replaced vault credentials`() = runBlocking {
        val oldSecretRef = "room_profile_1"
        val dao = FakePlatformV2Dao(
            mutableListOf(testPlatform(token = null).copy(id = 1, secretRef = oldSecretRef))
        )
        val vault = FakeSecretVault().apply {
            values[oldSecretRef] = "old-secret".encodeToByteArray()
        }
        val repository = createRepository(dao, vault)

        repository.migrateToPlatformV2()

        assertNull(vault.values[oldSecretRef])
    }

    @Test
    fun `replacing a migrated profile credential deletes its old vault record`() = runBlocking {
        val oldSecretRef = "room_profile_1"
        val dao = FakePlatformV2Dao(
            mutableListOf(testPlatform(token = null).copy(id = 1, secretRef = oldSecretRef))
        )
        val vault = FakeSecretVault().apply {
            values[oldSecretRef] = "old-secret".encodeToByteArray()
        }
        val repository = createRepository(dao, vault)

        repository.updatePlatformV2(
            dao.platforms.single().copy(token = "replacement-secret", secretRef = null)
        )

        assertNull(dao.platforms.single().token)
        assertEquals("profile_profile-1", dao.platforms.single().secretRef)
        assertNull(vault.values[oldSecretRef])
        assertEquals("replacement-secret", vault.values.getValue("profile_profile-1").decodeToString())
    }

    private fun createRepository(
        dao: FakePlatformV2Dao,
        vault: FakeSecretVault,
        dataSource: FakeSettingDataSource = FakeSettingDataSource()
    ) = SettingRepositoryImpl(
        settingDataSource = dataSource,
        platformV2Dao = dao,
        chatPlatformModelV2Dao = FakeChatPlatformModelV2Dao(),
        secretVault = vault
    )

    private fun testPlatform(token: String?) = PlatformV2(
        uid = "profile-1",
        name = "OpenAI",
        compatibleType = ClientType.OPENAI,
        enabled = true,
        apiUrl = "https://api.openai.com/v1/",
        token = token,
        model = "gpt-5"
    )
}

private class FakeSecretVault(
    private val readOverride: ByteArray? = null
) : SecretVault {
    val values = mutableMapOf<String, ByteArray>()
    var lastReadBytes: ByteArray? = null

    override suspend fun put(secretRef: String, secret: ByteArray) {
        values[secretRef] = secret.copyOf()
    }

    override suspend fun read(secretRef: String): ByteArray? = (readOverride ?: values[secretRef])?.copyOf()?.also {
        lastReadBytes = it
    }

    override suspend fun delete(secretRef: String) {
        values.remove(secretRef)
    }
}

private class FakePlatformV2Dao(
    val platforms: MutableList<PlatformV2> = mutableListOf()
) : PlatformV2Dao {
    var failEdits = false

    override suspend fun getPlatforms(): List<PlatformV2> = platforms.toList()

    override suspend fun getPlatform(id: Int): PlatformV2? = platforms.firstOrNull { it.id == id }

    override suspend fun addPlatform(platform: PlatformV2): Long {
        val persisted = if (platform.id == 0) platform.copy(id = (platforms.maxOfOrNull { it.id } ?: 0) + 1) else platform
        platforms += persisted
        return persisted.id.toLong()
    }

    override suspend fun editPlatform(platform: PlatformV2) {
        check(!failEdits) { "Database update failed." }
        val index = platforms.indexOfFirst { it.id == platform.id }
        if (index >= 0) platforms[index] = platform
    }

    override suspend fun deleteBindingsByProfileUid(profileUid: String) = Unit

    override suspend fun deletePlatformRow(platform: PlatformV2) {
        platforms.removeAll { it.id == platform.id }
    }
}

private class FakeChatPlatformModelV2Dao : ChatPlatformModelV2Dao {
    override suspend fun getByChatId(chatId: Int): List<ChatPlatformModelV2> = emptyList()
    override suspend fun upsertAll(vararg models: ChatPlatformModelV2) = Unit
    override suspend fun deleteByChatId(chatId: Int) = Unit
    override suspend fun deleteByPlatformUid(platformUid: String) = Unit
}

private class FakeSettingDataSource(
    val tokens: MutableMap<ApiType, String> = mutableMapOf()
) : SettingDataSource {
    override suspend fun updateDynamicTheme(theme: DynamicTheme) = Unit
    override suspend fun updateThemeMode(themeMode: ThemeMode) = Unit
    override suspend fun updateStatus(apiType: ApiType, status: Boolean) = Unit
    override suspend fun updateAPIUrl(apiType: ApiType, url: String) = Unit
    override suspend fun updateToken(apiType: ApiType, token: String) {
        tokens[apiType] = token
    }

    override suspend fun clearToken(apiType: ApiType) {
        tokens.remove(apiType)
    }

    override suspend fun updateModel(apiType: ApiType, model: String) = Unit
    override suspend fun updateTemperature(apiType: ApiType, temperature: Float) = Unit
    override suspend fun updateTopP(apiType: ApiType, topP: Float) = Unit
    override suspend fun updateSystemPrompt(apiType: ApiType, prompt: String) = Unit
    override suspend fun getDynamicTheme(): DynamicTheme? = null
    override suspend fun getThemeMode(): ThemeMode? = null
    override suspend fun getStatus(apiType: ApiType): Boolean? = false
    override suspend fun getAPIUrl(apiType: ApiType): String? = null
    override suspend fun getToken(apiType: ApiType): String? = tokens[apiType]
    override suspend fun getModel(apiType: ApiType): String? = null
    override suspend fun getTemperature(apiType: ApiType): Float? = null
    override suspend fun getTopP(apiType: ApiType): Float? = null
    override suspend fun getSystemPrompt(apiType: ApiType): String? = null
}
