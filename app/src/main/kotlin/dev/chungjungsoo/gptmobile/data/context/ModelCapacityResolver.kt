package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.agent.provider.LiteRtLmAdapter
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.localruntime.resolvedEngineMaxTokens
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository

open class ModelCapacityResolver(
    private val store: CompactionStore,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val deviceSocModel: String,
    private val remoteLookup: RemoteContextWindowLookup = RemoteContextWindowLookup { null }
) {
    open suspend fun resolve(platform: PlatformV2): ModelCapacityResolution {
        val stored = store.getCapacity(platform.uid, platform.apiUrl, platform.model)
        val detected = detect(platform, stored?.detectedContextWindowTokens)
        if (!isLocal(platform) && detected != null && stored?.detectedContextWindowTokens != detected) {
            store.saveCapacity(
                ModelCapacity(
                    platformUid = platform.uid,
                    endpoint = platform.apiUrl,
                    model = platform.model,
                    detectedContextWindowTokens = detected,
                    overrideContextWindowTokens = stored?.overrideContextWindowTokens
                )
            )
        }
        val capacity = ModelCapacity(
            platformUid = platform.uid,
            endpoint = platform.apiUrl,
            model = platform.model,
            detectedContextWindowTokens = detected,
            overrideContextWindowTokens = capOverride(
                platform,
                stored?.overrideContextWindowTokens,
                detected
            )
        )
        val effective = capacity.effectiveContextWindowTokens
        return if (effective != null && effective > 0) {
            ModelCapacityResolution.Known(capacity)
        } else {
            ModelCapacityResolution.Unknown(platform.uid, platform.apiUrl, platform.model)
        }
    }

    suspend fun saveOverride(platform: PlatformV2, overrideTokens: Int?) {
        val stored = store.getCapacity(platform.uid, platform.apiUrl, platform.model)
        val detected = detect(platform, stored?.detectedContextWindowTokens)
        store.saveCapacity(
            ModelCapacity(
                platformUid = platform.uid,
                endpoint = platform.apiUrl,
                model = platform.model,
                detectedContextWindowTokens = detected,
                overrideContextWindowTokens = capOverride(platform, overrideTokens, detected)
            )
        )
    }

    private suspend fun detect(platform: PlatformV2, storedDetected: Int?): Int? {
        if (isLocal(platform)) return detectLocal(platform)
        storedDetected?.takeIf { it > 0 }?.let { return it }
        return remoteLookup.lookup(platform)?.takeIf { it > 0 }
    }

    private suspend fun detectLocal(platform: PlatformV2): Int? {
        val entry = modelCatalogRepository.getCachedVisibleEntries().firstOrNull { it.id == platform.model }
        val requested = platform.maxTokens?.takeIf { it > 0 } ?: LiteRtLmAdapter.DEFAULT_MAX_TOKENS
        return resolvedEngineMaxTokens(
            requestedMaxTokens = requested,
            accelerator = platform.accelerator.orEmpty(),
            entry = entry,
            deviceSocModel = deviceSocModel
        ).takeIf { it > 0 }
    }

    private fun capOverride(platform: PlatformV2, overrideTokens: Int?, detected: Int?): Int? {
        val override = overrideTokens?.takeIf { it > 0 } ?: return null
        return if (isLocal(platform) && detected != null) minOf(override, detected) else override
    }

    private fun isLocal(platform: PlatformV2): Boolean = platform.compatibleType == ClientType.LITERT_LM

    companion object {
        fun supportsResumableReplies(platform: PlatformV2): Boolean = platform.compatibleType == ClientType.OPENAI &&
            platform.apiUrl.trimEnd('/') == ModelConstants.OPENAI_API_URL.trimEnd('/')
    }
}
