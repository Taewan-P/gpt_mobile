package dev.chungjungsoo.gptmobile.data.huggingface

import android.content.Intent
import android.net.Uri
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

sealed class HuggingFaceAuthOutcome {
    data class Authorized(val response: AuthorizationResponse) : HuggingFaceAuthOutcome()
    data object Cancelled : HuggingFaceAuthOutcome()
    data object Failed : HuggingFaceAuthOutcome()
}

object HuggingFaceOAuthRequests {
    fun authorizationServiceConfiguration(): AuthorizationServiceConfiguration = AuthorizationServiceConfiguration(
        Uri.parse(HuggingFaceOAuthConfig.AUTHORIZATION_ENDPOINT),
        Uri.parse(HuggingFaceOAuthConfig.TOKEN_ENDPOINT)
    )

    fun authorizationRequest(clientId: String, redirectUri: String): AuthorizationRequest = AuthorizationRequest.Builder(
        authorizationServiceConfiguration(),
        clientId,
        ResponseTypeValues.CODE,
        Uri.parse(redirectUri)
    )
        .setScope(HuggingFaceOAuthConfig.SCOPE)
        .build()

    fun parseResult(data: Intent?): HuggingFaceAuthOutcome {
        if (data == null) return HuggingFaceAuthOutcome.Failed
        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)
        return when {
            response?.authorizationCode != null -> HuggingFaceAuthOutcome.Authorized(response)
            exception != null && isCancelled(exception) -> HuggingFaceAuthOutcome.Cancelled
            else -> HuggingFaceAuthOutcome.Failed
        }
    }

    suspend fun exchangeAccessToken(
        authService: AuthorizationService,
        response: AuthorizationResponse
    ): String? = suspendCancellableCoroutine { continuation ->
        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, _ ->
            val accessToken = tokenResponse?.accessToken?.trim()?.takeIf { it.isNotEmpty() }
            if (continuation.isActive) {
                continuation.resume(accessToken)
            }
        }
    }

    private fun isCancelled(exception: AuthorizationException): Boolean = exception == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW ||
        exception == AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED
}
