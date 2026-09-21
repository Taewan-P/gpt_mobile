package dev.chungjungsoo.gptmobile.presentation.ui.localmodel

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.BuildConfig
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceAuthOutcome
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceOAuthConfig
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceOAuthRequests
import javax.inject.Inject
import net.openid.appauth.AuthorizationService

sealed class HuggingFaceSignInResult {
    data class Success(val accessToken: String) : HuggingFaceSignInResult()
    data object Cancelled : HuggingFaceSignInResult()
    data object Failed : HuggingFaceSignInResult()
}

interface HuggingFaceAuthClient {
    fun authorizationIntent(): Intent?
    suspend fun completeSignIn(data: Intent?): HuggingFaceSignInResult
    fun dispose()
}

class HuggingFaceAuthClientImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : HuggingFaceAuthClient {
    private var authService: AuthorizationService? = null

    override fun authorizationIntent(): Intent? {
        if (!HuggingFaceOAuthConfig.isConfigured(BuildConfig.HF_OAUTH_CLIENT_ID, BuildConfig.HF_OAUTH_REDIRECT_URI)) {
            return null
        }
        return runCatching {
            authService().getAuthorizationRequestIntent(
                HuggingFaceOAuthRequests.authorizationRequest(
                    BuildConfig.HF_OAUTH_CLIENT_ID,
                    BuildConfig.HF_OAUTH_REDIRECT_URI
                )
            )
        }.getOrNull()
    }

    override suspend fun completeSignIn(data: Intent?): HuggingFaceSignInResult = when (val outcome = HuggingFaceOAuthRequests.parseResult(data)) {
        HuggingFaceAuthOutcome.Cancelled -> HuggingFaceSignInResult.Cancelled

        HuggingFaceAuthOutcome.Failed -> HuggingFaceSignInResult.Failed

        is HuggingFaceAuthOutcome.Authorized -> {
            val token = HuggingFaceOAuthRequests.exchangeAccessToken(authService(), outcome.response)
            if (token == null) {
                HuggingFaceSignInResult.Failed
            } else {
                HuggingFaceSignInResult.Success(token)
            }
        }
    }

    override fun dispose() {
        authService?.dispose()
        authService = null
    }

    private fun authService(): AuthorizationService = authService ?: AuthorizationService(context).also { authService = it }
}
