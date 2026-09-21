package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceUrls
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class GatedDownloadStep {
    data object Proceed : GatedDownloadStep()
    data class NeedsSignIn(val isSessionExpired: Boolean) : GatedDownloadStep()
    data class NeedsLicense(val modelPageUrl: String) : GatedDownloadStep()
    data class OAuthNotConfigured(val isSessionExpired: Boolean = false) : GatedDownloadStep()
    data object Error : GatedDownloadStep()
}

class GatedDownloadCoordinator(
    private val tokenStore: HuggingFaceTokenStore,
    private val prober: LocalModelDownloadProber,
    private val isOAuthConfigured: () -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun resolve(entry: CatalogEntry): GatedDownloadStep {
        if (!entry.isGated) return GatedDownloadStep.Proceed

        val token = tokenStore.readAccessToken()
        val statusCode = withContext(ioDispatcher) {
            prober.probe(entry.downloadUrl, token)
        }
        val decision = GatedDownloadAuth.decide(
            statusCode = statusCode,
            hasToken = token != null,
            isGated = true
        )
        return when (decision.action) {
            GatedDownloadAction.PROCEED -> GatedDownloadStep.Proceed

            GatedDownloadAction.NEEDS_SIGN_IN -> {
                if (decision.isSessionExpired) {
                    tokenStore.clear()
                }
                if (!isOAuthConfigured()) {
                    GatedDownloadStep.OAuthNotConfigured(isSessionExpired = decision.isSessionExpired)
                } else {
                    GatedDownloadStep.NeedsSignIn(isSessionExpired = decision.isSessionExpired)
                }
            }

            GatedDownloadAction.NEEDS_LICENSE -> {
                val modelPageUrl = HuggingFaceUrls.modelPageUrl(entry.downloadUrl)
                    ?: return GatedDownloadStep.Error
                GatedDownloadStep.NeedsLicense(modelPageUrl)
            }

            GatedDownloadAction.ERROR -> GatedDownloadStep.Error
        }
    }
}
