package dev.chungjungsoo.gptmobile.data.localmodel

enum class GatedDownloadAction {
    PROCEED,
    NEEDS_SIGN_IN,
    NEEDS_LICENSE,
    ERROR
}

data class GatedDownloadDecision(
    val action: GatedDownloadAction,
    val isSessionExpired: Boolean = false
)

enum class DownloadFailureKind {
    SESSION_EXPIRED,
    AUTH_REQUIRED,
    LICENSE_REQUIRED,
    GENERIC;

    companion object {
        fun fromHttp(statusCode: Int, hasToken: Boolean, isGated: Boolean): DownloadFailureKind = fromDecision(GatedDownloadAuth.decide(statusCode, hasToken, isGated))

        fun fromDecision(decision: GatedDownloadDecision): DownloadFailureKind = when {
            decision.action == GatedDownloadAction.NEEDS_SIGN_IN && decision.isSessionExpired -> SESSION_EXPIRED
            decision.action == GatedDownloadAction.NEEDS_SIGN_IN -> AUTH_REQUIRED
            decision.action == GatedDownloadAction.NEEDS_LICENSE -> LICENSE_REQUIRED
            else -> GENERIC
        }

        fun fromWorkOutput(value: String?): DownloadFailureKind = entries.find { it.name == value } ?: GENERIC
    }
}

object GatedDownloadAuth {
    fun decide(statusCode: Int, hasToken: Boolean, isGated: Boolean): GatedDownloadDecision = when {
        statusCode == 200 || statusCode == 206 -> GatedDownloadDecision(GatedDownloadAction.PROCEED)

        statusCode == 401 -> GatedDownloadDecision(
            action = GatedDownloadAction.NEEDS_SIGN_IN,
            isSessionExpired = hasToken
        )

        statusCode == 403 && hasToken -> GatedDownloadDecision(GatedDownloadAction.NEEDS_LICENSE)

        statusCode == 403 && isGated && !hasToken -> GatedDownloadDecision(GatedDownloadAction.NEEDS_SIGN_IN)

        else -> GatedDownloadDecision(GatedDownloadAction.ERROR)
    }
}
