package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.localmodel.SocVariantResolver

/**
 * NPU SOC variants ship with a smaller KV cache (ekv1280 = 1280 tokens).
 * Clamp here so a profile's maxTokens cannot overflow that context window.
 */
fun resolvedEngineMaxTokens(
    requestedMaxTokens: Int,
    accelerator: String,
    entry: CatalogEntry?,
    deviceSocModel: String
): Int {
    if (LocalAccelerators.normalize(accelerator) != LocalAccelerators.NPU || entry == null) {
        return requestedMaxTokens
    }
    val contextSize = SocVariantResolver.resolve(entry, deviceSocModel).contextSize
    if (contextSize <= 0) return requestedMaxTokens
    return minOf(requestedMaxTokens, contextSize)
}
