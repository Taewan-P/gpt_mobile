package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.dao.ProviderConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.ProviderConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeProviderConnectionDao(
    initial: List<ProviderConnection> = emptyList()
) : ProviderConnectionDao {
    private val state = MutableStateFlow(initial.associateBy { it.uid })

    override suspend fun getConnections(): List<ProviderConnection> =
        state.value.values.sortedWith(compareBy({ it.compatibleType.name }, { it.name }))

    override fun observeConnections(): Flow<List<ProviderConnection>> =
        MutableStateFlow(state.value.values.toList())

    override suspend fun getConnection(uid: String): ProviderConnection? = state.value[uid]

    override fun observeConnection(uid: String): Flow<ProviderConnection?> =
        MutableStateFlow(state.value[uid])

    override suspend fun upsert(connection: ProviderConnection) {
        state.value = state.value + (connection.uid to connection)
    }

    override suspend fun delete(connection: ProviderConnection) {
        state.value = state.value - connection.uid
    }

    override suspend fun profileCount(connectionUid: String): Int = 0

    override suspend fun deleteToolBindings(uid: String) = Unit
    override suspend fun deleteChatSelections(uid: String) = Unit
    override suspend fun deleteProfiles(uid: String) = Unit
}
