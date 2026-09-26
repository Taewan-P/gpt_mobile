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

    val ALL = listOf(CPU, GPU, NPU)

    /**
     * NPU is enabled only when the catalog lists it and this device has a matching
     * SOC-specific variant. Gallery hides NPU-only models whose `socToModelFiles`
     * omit `Build.SOC_MODEL`; we apply that same SOC gate so ineligible NPU rows
     * stay visible but disabled. CPU/GPU stay available from the default file.
     */
    fun isNpuEligible(
        supported: List<String>,
        socToModelFiles: Map<String, *>,
        deviceSocModel: String
    ): Boolean {
        val listsNpu = supported.any { it.equals(NPU, ignoreCase = true) }
        return listsNpu &&
            QualcommSocSupport.htpVersion(deviceSocModel) != null &&
            SocVariantResolver.hasMatchingVariant(socToModelFiles, deviceSocModel)
    }

    fun defaultFrom(
        supported: List<String>,
        socToModelFiles: Map<String, *> = emptyMap<String, Any>(),
        deviceSocModel: String = ""
    ): String {
        val options = selectable(supported, socToModelFiles, deviceSocModel).toSet()
        return when {
            NPU in options -> NPU
            GPU in options -> GPU
            else -> supported.map { it.lowercase() }.firstOrNull { it in options } ?: CPU
        }
    }

    fun normalize(value: String?): String {
        val normalized = value?.lowercase()
        return when (normalized) {
            "cpu", "arm", "aarch64" -> CPU
            "gpu", "opencl", "opengl" -> GPU
            "npu", "hexagon", "qualcomm" -> NPU
            else -> CPU
        }
    }

    fun selectable(
        supported: List<String>,
        socToModelFiles: Map<String, *> = emptyMap<String, Any>(),
        deviceSocModel: String = ""
    ): List<String> = choices(supported, socToModelFiles, deviceSocModel)
        .filter { it.enabled }
        .map { it.accelerator }

    fun choices(
        supported: List<String>,
        socToModelFiles: Map<String, *> = emptyMap<String, Any>(),
        deviceSocModel: String = ""
    ): List<AcceleratorOption> = ALL.filter { it != NPU || QualcommSocSupport.htpVersion(deviceSocModel) != null }.map { accelerator ->
        when (accelerator) {
            NPU -> npuChoice(supported, socToModelFiles, deviceSocModel)

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

    fun shouldApplySampler(accelerator: String): Boolean = normalize(accelerator) != NPU

    private fun npuChoice(
        supported: List<String>,
        socToModelFiles: Map<String, *>,
        deviceSocModel: String
    ): AcceleratorOption {
        val listsNpu = supported.any { it.equals(NPU, ignoreCase = true) }
        if (!listsNpu || socToModelFiles.isEmpty()) {
            return AcceleratorOption(
                accelerator = NPU,
                enabled = false,
                unavailableReason = AcceleratorUnavailableReason.MODEL_HAS_NO_BUILD
            )
        }
        if (QualcommSocSupport.htpVersion(deviceSocModel) == null ||
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
