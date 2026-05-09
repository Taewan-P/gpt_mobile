package dev.chungjungsoo.gptmobile.data.repository.provider

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import kotlinx.coroutines.flow.Flow

interface ChatProviderAdapter {
    fun supports(platform: PlatformV2): Boolean

    suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState>
}

