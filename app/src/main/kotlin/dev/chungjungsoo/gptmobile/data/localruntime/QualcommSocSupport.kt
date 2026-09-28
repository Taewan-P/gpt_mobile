package dev.chungjungsoo.gptmobile.data.localruntime

/** SoCs for which this APK ships QAIRT libraries and the catalog provides compiled LLMs. */
object QualcommSocSupport {
    fun htpVersion(socModel: String): Int? = when (canonicalSoc(socModel)) {
        "SM8550" -> 73
        "SM8650" -> 75
        "SM8750" -> 79
        "SM8850" -> 81
        else -> null
    }

    fun canonicalSoc(value: String): String = Regex("(?i)^SM[0-9]{4}(?=$|[-_ ])").find(value.trim())?.value?.uppercase() ?: value.trim().uppercase()

    fun requiredLibraries(socModel: String): List<String> {
        val version = htpVersion(socModel) ?: return emptyList()
        return listOf(
            "libLiteRtDispatch_Qualcomm.so",
            "libQnnHtp.so",
            "libQnnHtpPrepare.so",
            "libQnnSystem.so",
            "libQnnHtpV${version}Stub.so",
            "libQnnHtpV${version}Skel.so"
        )
    }
}
