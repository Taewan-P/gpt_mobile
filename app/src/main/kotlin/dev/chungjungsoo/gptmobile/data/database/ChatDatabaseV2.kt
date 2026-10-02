package dev.chungjungsoo.gptmobile.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.dao.AgentRunDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.LocalModelDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.OpenRouterBatchCacheDao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ProviderConnectionDao
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.AgentToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevisionListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.ChatAttachmentListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.CombinedModelResponseListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.OpenRouterBatchCacheEntity
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ProviderConnection
import dev.chungjungsoo.gptmobile.data.database.entity.StringListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent

@Database(
    entities = [
        ChatRoomV2::class,
        MessageV2::class,
        dev.chungjungsoo.gptmobile.data.database.entity.MessageSearch::class,
        PlatformV2::class,
        ChatPlatformModelV2::class,
        ToolConnection::class,
        AgentToolBinding::class,
        AgentRun::class,
        ToolEvent::class,
        LocalModel::class,
        OpenRouterBatchCacheEntity::class,
        dev.chungjungsoo.gptmobile.data.knowledge.KnowledgeProject::class,
        dev.chungjungsoo.gptmobile.data.knowledge.KnowledgeProjectChat::class,
        dev.chungjungsoo.gptmobile.data.knowledge.KnowledgeDocument::class,
        dev.chungjungsoo.gptmobile.data.knowledge.KnowledgeChunk::class,
        dev.chungjungsoo.gptmobile.data.queue.PendingPrompt::class,
        dev.chungjungsoo.gptmobile.data.permissions.ToolApproval::class,
        dev.chungjungsoo.gptmobile.data.accounting.ModelInvocation::class,
        ProviderConnection::class,
        dev.chungjungsoo.gptmobile.data.memory.MemoryGraphEntityRecord::class,
        dev.chungjungsoo.gptmobile.data.memory.MemoryGraphObservationRecord::class,
        dev.chungjungsoo.gptmobile.data.memory.MemoryGraphRelationRecord::class
    ],
    version = 32,
    exportSchema = true
)
@TypeConverters(
    StringListConverter::class,
    ChatAttachmentListConverter::class,
    AssistantRevisionListConverter::class,
    AssistantTimelineListConverter::class,
    CombinedModelResponseListConverter::class
)
abstract class ChatDatabaseV2 : RoomDatabase() {
    abstract fun memoryGraphDao(): dev.chungjungsoo.gptmobile.data.memory.MemoryGraphDao
    abstract fun toolApprovalDao(): dev.chungjungsoo.gptmobile.data.permissions.ToolApprovalDao
    abstract fun invocationDao(): dev.chungjungsoo.gptmobile.data.accounting.InvocationDao
    abstract fun knowledgeDao(): dev.chungjungsoo.gptmobile.data.knowledge.KnowledgeDao
    abstract fun pendingPromptDao(): dev.chungjungsoo.gptmobile.data.queue.PendingPromptDao
    abstract fun platformDao(): PlatformV2Dao
    abstract fun providerConnectionDao(): ProviderConnectionDao
    abstract fun chatRoomDao(): ChatRoomV2Dao
    abstract fun messageDao(): MessageV2Dao
    abstract fun chatPlatformModelDao(): ChatPlatformModelV2Dao
    abstract fun agentRunDao(): AgentRunDao
    abstract fun agentPersistenceDao(): AgentPersistenceDao
    abstract fun toolConnectionDao(): ToolConnectionDao
    abstract fun localModelDao(): LocalModelDao
    abstract fun openRouterBatchCacheDao(): OpenRouterBatchCacheDao
}
