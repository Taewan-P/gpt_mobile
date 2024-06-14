package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import kotlinx.coroutines.flow.Flow

interface AnthropicAPI {
    fun setToken(token: String?)
    fun streamChatMessage(messageRequest: MessageRequest): Flow<MessageResponseChunk>
}
