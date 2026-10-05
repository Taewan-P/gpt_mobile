package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import javax.inject.Inject

class MistralAdapter @Inject constructor(
    private val openAIAPI: OpenAIAPI,
    private val attachmentEncoder: ProviderAttachmentEncoder
) {
    suspend fun openSession(
        turns: List<ConversationTurn>,
        platform: PlatformV2
    ): AgentProviderSession = openChatCompletionsSession(
        openAIAPI,
        attachmentEncoder.openAIChatMessages(turns, platform.systemPrompt),
        platform
    )
}
