package dev.chungjungsoo.gptmobile.di

import android.content.Context
import com.example.gptmobileai.debug.ToolMetricsCollector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.agent.tool.AgentToolResolver
import dev.chungjungsoo.gptmobile.data.agent.tool.DelegationRecoveryInteractions
import dev.chungjungsoo.gptmobile.data.context.ContextBuilder
import dev.chungjungsoo.gptmobile.data.conversation.ConversationTitleSummarizer
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.dao.AgentRunDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.GroqAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository
import dev.chungjungsoo.gptmobile.data.repository.AttachmentUploadCoordinator
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.ChatRepositoryImpl
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.ToolEventRecorder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatRepositoryModule {

    @Provides
    @Singleton
    fun provideConversationTitleSummarizer(
        openAIAPI: OpenAIAPI,
        groqAPI: GroqAPI,
        googleAPI: GoogleAPI
    ): ConversationTitleSummarizer = ConversationTitleSummarizer(
        openAIAPI = openAIAPI,
        groqAPI = groqAPI,
        googleAPI = googleAPI
    )

    @Provides
    @Singleton
    fun provideChatRepository(
        @ApplicationContext context: Context,
        chatRoomV2Dao: ChatRoomV2Dao,
        messageV2Dao: MessageV2Dao,
        chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
        agentPersistenceDao: AgentPersistenceDao,
        agentRunDao: AgentRunDao,
        settingRepository: SettingRepository,
        openAIAPI: OpenAIAPI,
        groqAPI: GroqAPI,
        anthropicAPI: AnthropicAPI,
        googleAPI: GoogleAPI,
        attachmentUploadCoordinator: AttachmentUploadCoordinator,
        contextBuilder: ContextBuilder,
        agentToolResolver: AgentToolResolver,
        toolEventRecorder: ToolEventRecorder,
        localRuntime: LocalRuntime,
        localModelRepository: LocalModelRepository,
        modelCatalogRepository: ModelCatalogRepository,
        @DeviceSocModel deviceSocModel: String,
        titleSummarizer: ConversationTitleSummarizer,
        factVault: FactVaultRepository,
        toolMetricsCollector: ToolMetricsCollector,
        knowledge: dev.chungjungsoo.gptmobile.data.knowledge.MemoryDocumentRepository,
        toolApprovals: dev.chungjungsoo.gptmobile.data.permissions.ToolApprovalManager,
        invocationLedger: dev.chungjungsoo.gptmobile.data.accounting.InvocationLedger,
        delegationRecovery: DelegationRecoveryInteractions
    ): ChatRepository = ChatRepositoryImpl(
        context = context,
        chatRoomV2Dao = chatRoomV2Dao,
        messageV2Dao = messageV2Dao,
        chatPlatformModelV2Dao = chatPlatformModelV2Dao,
        agentPersistenceDao = agentPersistenceDao,
        agentRunDao = agentRunDao,
        settingRepository = settingRepository,
        openAIAPI = openAIAPI,
        groqAPI = groqAPI,
        anthropicAPI = anthropicAPI,
        googleAPI = googleAPI,
        attachmentUploadCoordinator = attachmentUploadCoordinator,
        contextBuilder = contextBuilder,
        agentToolResolver = agentToolResolver,
        toolEventRecorder = toolEventRecorder,
        localRuntime = localRuntime,
        localModelRepository = localModelRepository,
        modelCatalogRepository = modelCatalogRepository,
        deviceSocModel = deviceSocModel,
        titleSummarizer = titleSummarizer,
        factVault = factVault,
        toolMetricsCollector = toolMetricsCollector,
        knowledge = knowledge,
        toolApprovals = toolApprovals,
        invocationLedger = invocationLedger,
        delegationRecovery = delegationRecovery
    )
}
