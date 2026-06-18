package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import kotlinx.coroutines.flow.Flow

interface OpenAIAPI {
    fun setToken(token: String?)
    fun setAPIUrl(url: String)
    fun streamChatCompletion(request: ChatCompletionRequest, timeoutSeconds: Int): Flow<ChatCompletionChunk>
    fun streamResponses(request: ResponsesRequest, timeoutSeconds: Int): Flow<ResponsesStreamEvent>
    suspend fun createConversation(): String
    suspend fun uploadFile(filePath: String, fileName: String, mimeType: String): UploadedProviderFile
    suspend fun isFileAvailable(fileId: String): Boolean
}
