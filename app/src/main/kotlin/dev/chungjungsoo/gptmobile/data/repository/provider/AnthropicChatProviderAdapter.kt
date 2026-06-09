package dev.chungjungsoo.gptmobile.data.repository.provider

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.ThinkingConfig
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentDeltaResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.repository.streamPreparedApiState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

class AnthropicChatProviderAdapter @Inject constructor(
    private val anthropicAPI: AnthropicAPI,
    private val requestBuilder: ProviderRequestBuilder
) : ChatProviderAdapter {
    override fun supports(platform: PlatformV2): Boolean = platform.compatibleType == ClientType.ANTHROPIC

    override suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        anthropicAPI.setToken(platform.token)
        anthropicAPI.setAPIUrl(platform.apiUrl)

        streamPreparedApiState(
            prepare = {
                val contextTurns = requestBuilder.buildContextTurns(userMessages, assistantMessages, platform)
                val messages = requestBuilder.buildAnthropicInputMessages(contextTurns, platform.uid)

                MessageRequest(
                    model = platform.model,
                    messages = messages,
                    maxTokens = if (platform.reasoning) 16000 else 4096,
                    stream = platform.stream,
                    systemPrompt = platform.systemPrompt,
                    temperature = if (platform.reasoning) null else platform.temperature,
                    topP = if (platform.reasoning) null else platform.topP,
                    thinking = if (platform.reasoning) {
                        ThinkingConfig(
                            type = "enabled",
                            budgetTokens = 10000
                        )
                    } else {
                        null
                    }
                )
            },
            stream = { request ->
                flow {
                    anthropicAPI.streamChatMessage(request, platform.timeout).collect { chunk ->
                        when (chunk) {
                            is ContentDeltaResponseChunk -> {
                                when (chunk.delta.type) {
                                    ContentBlockType.THINKING_DELTA -> chunk.delta.thinking?.let { emit(ApiState.Thinking(it)) }
                                    ContentBlockType.DELTA -> chunk.delta.text?.let { emit(ApiState.Success(it)) }
                                    else -> {}
                                }
                            }

                            is ErrorResponseChunk -> emit(ApiState.Error(chunk.error.message))
                            else -> {}
                        }
                    }
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
