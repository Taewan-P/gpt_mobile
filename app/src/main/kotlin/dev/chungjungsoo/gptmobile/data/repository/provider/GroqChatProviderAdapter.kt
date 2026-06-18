package dev.chungjungsoo.gptmobile.data.repository.provider

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.GroqAPI
import dev.chungjungsoo.gptmobile.data.repository.GroqReasoningParser
import dev.chungjungsoo.gptmobile.data.repository.createGroqChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.repository.streamPreparedApiState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

class GroqChatProviderAdapter @Inject constructor(
    private val groqAPI: GroqAPI,
    private val requestBuilder: ProviderRequestBuilder
) : ChatProviderAdapter {
    override fun supports(platform: PlatformV2): Boolean = platform.compatibleType == ClientType.GROQ

    override suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        streamPreparedApiState(
            prepare = {
                val contextTurns = requestBuilder.buildContextTurns(userMessages, assistantMessages, platform)
                requestBuilder.validateInlineBudgetIfNeeded(contextTurns, platform)
                val messages = requestBuilder.buildOpenAIChatMessages(contextTurns, platform.systemPrompt)

                createGroqChatCompletionRequest(messages, platform)
            },
            stream = { request ->
                flow {
                    val parser = GroqReasoningParser()
                    groqAPI.streamChatCompletion(
                        request = request,
                        timeoutSeconds = platform.timeout,
                        token = platform.token,
                        apiUrl = platform.apiUrl
                    ).collect { chunk ->
                        when {
                            chunk.error != null -> emit(ApiState.Error(chunk.error.message))

                            else -> {
                                val choice = chunk.choices?.firstOrNull()
                                parser.append(
                                    reasoningChunk = choice?.delta?.reasoning ?: choice?.message?.reasoning,
                                    contentChunk = choice?.delta?.content ?: choice?.message?.content
                                ).forEach { emit(it) }
                            }
                        }
                    }

                    parser.flush().forEach { emit(it) }
                }
            }
        ).catch { e ->
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        flow {
            emit(ApiState.Error(e.message ?: "Failed to complete chat"))
        }
    }
}
