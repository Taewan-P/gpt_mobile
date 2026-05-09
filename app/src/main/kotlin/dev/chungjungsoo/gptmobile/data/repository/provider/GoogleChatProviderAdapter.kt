package dev.chungjungsoo.gptmobile.data.repository.provider

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import dev.chungjungsoo.gptmobile.data.dto.google.common.Part
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.repository.streamPreparedApiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import javax.inject.Inject

class GoogleChatProviderAdapter @Inject constructor(
    private val googleAPI: GoogleAPI,
    private val requestBuilder: ProviderRequestBuilder
) : ChatProviderAdapter {
    override fun supports(platform: PlatformV2): Boolean = platform.compatibleType == ClientType.GOOGLE

    override suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        googleAPI.setToken(platform.token)
        googleAPI.setAPIUrl(platform.apiUrl)

        streamPreparedApiState(
            prepare = {
                val contextTurns = requestBuilder.buildContextTurns(userMessages, assistantMessages, platform)
                val contents = requestBuilder.buildGoogleContents(contextTurns, platform.uid)

                GenerateContentRequest(
                    contents = contents,
                    generationConfig = dev.chungjungsoo.gptmobile.data.dto.google.request.GenerationConfig(
                        temperature = platform.temperature,
                        topP = platform.topP,
                        thinkingConfig = if (platform.reasoning) {
                            dev.chungjungsoo.gptmobile.data.dto.google.request.ThinkingConfig(
                                includeThoughts = true
                            )
                        } else {
                            null
                        }
                    ),
                    systemInstruction = platform.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                        Content(
                            parts = listOf(Part.text(it))
                        )
                    }
                )
            },
            stream = { request ->
                flow {
                    googleAPI.streamGenerateContent(request, platform.model, platform.timeout).collect { response ->
                        when {
                            response.error != null -> emit(ApiState.Error(response.error.message))
                            response.candidates?.firstOrNull()?.content?.parts != null -> {
                                val parts = response.candidates.first().content.parts
                                parts.forEach { part ->
                                    part.text?.let { text ->
                                        if (part.thought == true) {
                                            emit(ApiState.Thinking(text))
                                        } else {
                                            emit(ApiState.Success(text))
                                        }
                                    }
                                }
                            }
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
