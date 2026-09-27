package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.localmodel.SocVariantResolver

/**
 * NPU SOC variants ship with a fixed KV cache context (e.g. ekv1280 = 1280 tokens).
 * Clamp here so a profile's maxTokens cannot overflow that context window.
 *
 * For GPU/CPU on devices with high RAM capacity (>= 12GB RAM), the app context ceiling scales up to 8192 tokens when requested, while devices below 6GB clamp to 1024. Model limits still apply.
 */
fun resolvedEngineMaxTokens(
    requestedMaxTokens: Int,
    accelerator: String,
    entry: CatalogEntry?,
    deviceSocModel: String,
    deviceRamGb: Long = 8L
): Int {
    val positiveTokens = requestedMaxTokens.takeIf { it > 0 } ?: 1024
    val normalizedAccelerator = LocalAccelerators.normalize(accelerator)
    if (normalizedAccelerator == LocalAccelerators.NPU && entry != null) {
        val contextSize = SocVariantResolver.resolve(entry, deviceSocModel).contextSize
        if (contextSize > 0) {
            return minOf(positiveTokens, contextSize)
        }
    }

    val memoryCap = when {
        deviceRamGb >= 12L -> MAX_HIGH_RAM_CONTEXT_TOKENS
        deviceRamGb < 6L -> 1024
        else -> 4096
    }
    return minOf(positiveTokens, memoryCap, entry?.maxContextTokens?.takeIf { it > 0 } ?: Int.MAX_VALUE)
}

const val MAX_HIGH_RAM_CONTEXT_TOKENS: Int = 8192
