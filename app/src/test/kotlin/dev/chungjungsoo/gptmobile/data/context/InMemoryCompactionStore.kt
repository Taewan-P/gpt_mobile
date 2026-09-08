package dev.chungjungsoo.gptmobile.data.context

class InMemoryCompactionStore : CompactionStore {
    private val checkpoints = linkedMapOf<Pair<Int, String>, ContextCheckpoint>()
    private val capacities = linkedMapOf<Triple<String, String, String>, ModelCapacity>()
    var failNextSave: Boolean = false

    override suspend fun getCheckpoint(chatId: Int, platformUid: String): ContextCheckpoint? = checkpoints[chatId to platformUid]

    override suspend fun saveCheckpoint(checkpoint: ContextCheckpoint) {
        if (failNextSave) {
            failNextSave = false
            throw IllegalStateException("checkpoint write failed")
        }
        checkpoints[checkpoint.chatId to checkpoint.platformUid] = checkpoint
    }

    override suspend fun deleteCheckpoint(chatId: Int, platformUid: String) {
        checkpoints.remove(chatId to platformUid)
    }

    override suspend fun copyCheckpoints(sourceChatId: Int, destinationChatId: Int, updatedAt: Long) {
        checkpoints.values.filter { it.chatId == sourceChatId }.forEach { source ->
            checkpoints[destinationChatId to source.platformUid] = source.copy(
                chatId = destinationChatId,
                updatedAt = updatedAt
            )
        }
    }

    override suspend fun getCapacity(platformUid: String, endpoint: String, model: String): ModelCapacity? = capacities[Triple(platformUid, endpoint, model)]

    override suspend fun saveCapacity(capacity: ModelCapacity) {
        capacities[Triple(capacity.platformUid, capacity.endpoint, capacity.model)] = capacity
    }
}
