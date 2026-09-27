package dev.chungjungsoo.gptmobile.data.localmodel

/** Artifact-specific findings, separate from filename/SoC compatibility checks. */
object LocalModelCompatibility {
    private const val MINICPM_REPO = "Tdamre/MiniCPM5-1B-litert-lm"
    private val unsupportedMiniCpmFiles = setOf(
        "MiniCPM5-1B-qualcomm-sm8750.litertlm",
        "MiniCPM5-1B-qualcomm-sm8750-c1024.litertlm"
    )

    // Publisher's Android results: both AOT exports fail QNN PD allocation (8.62
    // and 5.50 GiB). Matching SM8750 alone does not make them runnable.
    // https://huggingface.co/Tdamre/MiniCPM5-1B-litert-lm/blob/main/README.md
    fun unsupportedReason(repoId: String, path: String): String? {
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
