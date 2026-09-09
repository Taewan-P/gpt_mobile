package dev.chungjungsoo.gptmobile.data.context

interface CompactionStore {
    suspend fun getCheckpoint(chatId: Int, platformUid: String): ContextCheckpoint?
    suspend fun saveCheckpoint(checkpoint: ContextCheckpoint)
    suspend fun deleteCheckpoint(chatId: Int, platformUid: String)
    suspend fun getCapacity(platformUid: String, endpoint: String, model: String): ModelCapacity?
    suspend fun saveCapacity(capacity: ModelCapacity)
}
