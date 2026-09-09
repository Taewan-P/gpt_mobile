package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.openai.request.CompactResponsesRequest
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

class OpenAINativeCompactor(
    private val api: OpenAIAPI,
    private val encodeInput: suspend (CompactInput) -> JsonArray
) : ContextCompactor {
    override fun supports(platform: PlatformV2, estimatedTokens: Int): Boolean = platform.compatibleType == ClientType.OPENAI &&
        platform.apiUrl.trimEnd('/') == ModelConstants.OPENAI_API_URL.trimEnd('/')

    override suspend fun compact(input: CompactInput): CompactOutput {
        val existing = input.existingNativeJson?.let { json ->
            runCatching { Json.parseToJsonElement(json) as? JsonArray }.getOrNull()
        }
        val encoded = JsonArray(existing?.toList().orEmpty() + encodeInput(input.copy(existingNativeJson = null)))
        val result = api.compactResponses(
            CompactResponsesRequest(
                model = input.platform.model,
                input = encoded,
                instructions = input.instructions
            ),
            input.platform.timeout,
            ProviderRequestConfig(input.platform.apiUrl, input.platform.token)
        )
        if (result.output.isEmpty()) {
            throw IllegalStateException("OpenAI compact returned an empty window")
        }
        return CompactOutput(
            representation = CompactionRepresentation.NATIVE_OPENAI,
            nativeItemsJson = result.output.toString(),
            coveredTurnCount = input.turns.size
        )
    }
}
