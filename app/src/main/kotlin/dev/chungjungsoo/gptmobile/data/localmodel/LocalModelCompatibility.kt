package dev.chungjungsoo.gptmobile.data.localmodel

/** Artifact-specific findings, separate from filename/SoC compatibility checks. */
object LocalModelCompatibility {
    private const val MINICPM_REPO = "Tdamre/MiniCPM5-1B-litert-lm"
    private const val GEMMA4_REPO = "litert-community/gemma-4-E2B-it-litert-lm"
    private val unsupportedMiniCpmFiles = setOf(
        "MiniCPM5-1B-qualcomm-sm8750.litertlm",
        "MiniCPM5-1B-qualcomm-sm8750-c1024.litertlm"
    )
    private val unsafeGemma4QnnFiles = setOf(
        "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
        "gemma-4-E2B-it_qualcomm_qcs8275.litertlm"
    )

    // Publisher's Android results: both AOT exports fail QNN PD allocation (8.62
    // and 5.50 GiB). Matching SM8750 alone does not make them runnable.
    // https://huggingface.co/Tdamre/MiniCPM5-1B-litert-lm/blob/main/README.md
    fun unsupportedReason(repoId: String, path: String): String? {
        if (repoId.equals(GEMMA4_REPO, true) &&
            unsafeGemma4QnnFiles.any { it.equals(path.substringAfterLast('/'), true) }
        ) {
            return "This Gemma 4 Qualcomm NPU export is disabled because it can crash the Android process during QNN DSP queue initialization. " +
                "Use the Gemma 4 GPU/CPU package, or choose the verified Gemma 3 NPU package for this Snapdragon device."
        }
        if (!repoId.equals(MINICPM_REPO, true) ||
            unsupportedMiniCpmFiles.none { it.equals(path.substringAfterLast('/'), true) }
        ) {
            return null
        }
        return "This MiniCPM NPU export has a documented QNN memory allocation failure. " +
            "Download MiniCPM5-1B-web.litertlm from the same repository and use GPU or CPU, or choose another NPU model."
    }

    fun installedPackageIssue(downloadUrl: String, path: String): String? {
        val repoId = downloadUrl.removePrefix("https://huggingface.co/").substringBefore("/resolve/")
        return unsupportedReason(repoId, path)
    }

    // Despite its name, this specific export passed Android CPU/GPU inference.
    // Keep the exception scoped to the publisher and artifact, not all "web" files.
    fun validatedContextTokens(repoId: String, path: String): Int? =
        2048.takeIf { repoId.equals(MINICPM_REPO, true) && path == "MiniCPM5-1B-web.litertlm" }
}
