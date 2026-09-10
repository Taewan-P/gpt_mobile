package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.localmodel.SocVariantResolver

enum class AcceleratorUnavailableReason {
    MODEL_HAS_NO_BUILD,
    DEVICE_NOT_SUPPORTED
}

data class AcceleratorOption(
    val accelerator: String,
    val enabled: Boolean,
    val unavailableReason: AcceleratorUnavailableReason? = null
)

object LocalAccelerators {
    const val CPU = "cpu"
    const val GPU = "gpu"
    const val NPU = "npu"
    const val AUTO = "auto"

    val ALL = listOf(CPU, GPU, NPU)
    val PREFERENCES = listOf(AUTO) + ALL

    /**
     * Catalog NPU plus a matching SOC variant is not proof the NPU runtime works.
     * Pass [isNpuAvailable] from parent native-library gating when that answer exists.
     */
    fun isNpuEligible(
        supported: List<String>,
        socToModelFiles: Map<String, *>,
        deviceSocModel: String,
        isNpuAvailable: Boolean? = null
    ): Boolean {
        if (isNpuAvailable == false) return false
        val listsNpu = supported.any { it.equals(NPU, ignoreCase = true) }
        return listsNpu && SocVariantResolver.hasMatchingVariant(socToModelFiles, deviceSocModel)
    }

    fun defaultFrom(
        supported: List<String>,
        socToModelFiles: Map<String, *> = emptyMap<String, Any>(),
        deviceSocModel: String = "",
        isNpuAvailable: Boolean? = null
    ): String = if (selectable(supported, socToModelFiles, deviceSocModel, isNpuAvailable).isNotEmpty()) {
        AUTO
    } else {
        CPU
    }

    fun normalize(value: String?): String {
        val normalized = value?.lowercase()
        return if (normalized == AUTO || normalized == CPU || normalized == GPU || normalized == NPU) {
            normalized
        } else {
            CPU
        }
    }

    fun selectable(
        supported: List<String>,
        socToModelFiles: Map<String, *> = emptyMap<String, Any>(),
        deviceSocModel: String = "",
        isNpuAvailable: Boolean? = null
    ): List<String> = choices(supported, socToModelFiles, deviceSocModel, isNpuAvailable)
        .filter { it.enabled }
        .map { it.accelerator }

    fun choices(
        supported: List<String>,
        socToModelFiles: Map<String, *> = emptyMap<String, Any>(),
        deviceSocModel: String = "",
        isNpuAvailable: Boolean? = null
    ): List<AcceleratorOption> = ALL.map { accelerator ->
        when (accelerator) {
            NPU -> npuChoice(supported, socToModelFiles, deviceSocModel, isNpuAvailable)

            else -> {
                val listed = supported.any { it.equals(accelerator, ignoreCase = true) }
                AcceleratorOption(
                    accelerator = accelerator,
                    enabled = listed,
                    unavailableReason = if (listed) null else AcceleratorUnavailableReason.MODEL_HAS_NO_BUILD
                )
            }
        }
    }

    fun preferenceChoices(
        supported: List<String>,
        socToModelFiles: Map<String, *> = emptyMap<String, Any>(),
        deviceSocModel: String = "",
        isNpuAvailable: Boolean? = null
    ): List<AcceleratorOption> {
        val backends = choices(supported, socToModelFiles, deviceSocModel, isNpuAvailable)
        val autoEnabled = backends.any { it.enabled }
        return listOf(
            AcceleratorOption(
                accelerator = AUTO,
                enabled = autoEnabled,
                unavailableReason = if (autoEnabled) null else AcceleratorUnavailableReason.MODEL_HAS_NO_BUILD
            )
        ) + backends
    }

    fun shouldApplySampler(accelerator: String): Boolean = normalize(accelerator) != NPU

    private fun npuChoice(
        supported: List<String>,
        socToModelFiles: Map<String, *>,
        deviceSocModel: String,
        isNpuAvailable: Boolean?
    ): AcceleratorOption {
        val listsNpu = supported.any { it.equals(NPU, ignoreCase = true) }
        if (!listsNpu || socToModelFiles.isEmpty()) {
            return AcceleratorOption(
                accelerator = NPU,
                enabled = false,
                unavailableReason = AcceleratorUnavailableReason.MODEL_HAS_NO_BUILD
            )
        }
        if (isNpuAvailable == false ||
            !SocVariantResolver.hasMatchingVariant(socToModelFiles, deviceSocModel)
        ) {
            return AcceleratorOption(
                accelerator = NPU,
                enabled = false,
                unavailableReason = AcceleratorUnavailableReason.DEVICE_NOT_SUPPORTED
            )
        }
        return AcceleratorOption(accelerator = NPU, enabled = true)
    }
}
