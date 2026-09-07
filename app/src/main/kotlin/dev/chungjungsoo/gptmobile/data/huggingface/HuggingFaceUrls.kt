package dev.chungjungsoo.gptmobile.data.huggingface

object HuggingFaceUrls {
    private const val HOST_PREFIX = "https://huggingface.co/"
    private const val RESOLVE_SEGMENT = "/resolve/"

    fun modelPageUrl(downloadUrl: String): String? {
        val index = downloadUrl.indexOf(RESOLVE_SEGMENT)
        if (index < 0 || !downloadUrl.startsWith(HOST_PREFIX)) return null
        return downloadUrl.substring(0, index)
    }

    fun modelId(downloadUrl: String): String? = modelPageUrl(downloadUrl)?.removePrefix(HOST_PREFIX)

    fun licensePageUrl(modelId: String): String = "$HOST_PREFIX$modelId"

    const val ACCESS_TOKENS_URL = "${HOST_PREFIX}settings/tokens"
}
