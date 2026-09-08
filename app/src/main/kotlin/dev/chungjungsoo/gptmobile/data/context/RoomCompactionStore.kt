package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.dao.CompactionDao
import dev.chungjungsoo.gptmobile.data.database.entity.ContextCheckpointEntity
import dev.chungjungsoo.gptmobile.data.database.entity.ModelCapacityEntity

class RoomCompactionStore(
    private val dao: CompactionDao
) : CompactionStore {
    override suspend fun getCheckpoint(chatId: Int, platformUid: String): ContextCheckpoint? = dao.getCheckpoint(chatId, platformUid)?.let { entity -> runCatching { entity.toDomain() }.getOrNull() }

    override suspend fun saveCheckpoint(checkpoint: ContextCheckpoint) {
        dao.upsertCheckpoint(checkpoint.toEntity())
    }

    override suspend fun deleteCheckpoint(chatId: Int, platformUid: String) {
        dao.deleteCheckpoint(chatId, platformUid)
    }

    override suspend fun copyCheckpoints(sourceChatId: Int, destinationChatId: Int, updatedAt: Long) {
        dao.copyCheckpoints(sourceChatId, destinationChatId, updatedAt)
    }

    override suspend fun getCapacity(platformUid: String, endpoint: String, model: String): ModelCapacity? = dao.getCapacity(platformUid, endpoint, model)?.toDomain()

    override suspend fun saveCapacity(capacity: ModelCapacity) {
        dao.upsertCapacity(
            ModelCapacityEntity(
                platformUid = capacity.platformUid,
                endpoint = capacity.endpoint,
                model = capacity.model,
                detectedContextTokens = capacity.detectedContextWindowTokens,
                overrideContextTokens = capacity.overrideContextWindowTokens,
                updatedAt = System.currentTimeMillis() / 1000
            )
        )
    }
}

private fun ContextCheckpointEntity.toDomain() = ContextCheckpoint(
    chatId = chatId,
    platformUid = platformUid,
    sourcePrefixFingerprint = sourcePrefixFingerprint,
    endpointModelKey = endpointModelKey,
    coveredTurnCount = coveredTurnCount,
    representation = CompactionRepresentation.valueOf(representation),
    serializedWorkingContext = serializedWorkingContext,
    estimatedTokens = estimatedTokens,
    updatedAt = updatedAt
)

private fun ContextCheckpoint.toEntity() = ContextCheckpointEntity(
    chatId = chatId,
    platformUid = platformUid,
    sourcePrefixFingerprint = sourcePrefixFingerprint,
    endpointModelKey = endpointModelKey,
    coveredTurnCount = coveredTurnCount,
    representation = representation.name,
    serializedWorkingContext = serializedWorkingContext,
    estimatedTokens = estimatedTokens,
    updatedAt = updatedAt
)

private fun ModelCapacityEntity.toDomain() = ModelCapacity(
    platformUid = platformUid,
    endpoint = endpoint,
    model = model,
    detectedContextWindowTokens = detectedContextTokens,
    overrideContextWindowTokens = overrideContextTokens
)
