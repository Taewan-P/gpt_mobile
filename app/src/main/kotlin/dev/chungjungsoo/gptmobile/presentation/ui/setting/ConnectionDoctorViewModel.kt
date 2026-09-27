package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.endpointLocality
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig
import dev.chungjungsoo.gptmobile.data.network.gateway.GatewayAPI
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.security.DiagnosticRedactor
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@HiltViewModel
class ConnectionDoctorViewModel @Inject constructor(
    private val settings: SettingRepository,
    private val network: NetworkClient,
    private val gateway: GatewayAPI,
    private val runtime: LocalRuntime,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    val detectedContext = MutableStateFlow<Pair<String, Int>?>(null)
    fun applyDetectedContext() = action {
        val detected = detectedContext.value ?: return@action
        val current = settings.getFeatureSettings()
        settings.updateFeatureSettings(current.copy(tokenBudget = current.tokenBudget.copy(profileContextCeilings = current.tokenBudget.profileContextCeilings + detected)))
        detectedContext.value = null
        finish("Context ceiling saved for this profile. Your global context budget still applies.")
    }
    val profiles = settings.observePlatformV2s().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val preferences = context.getSharedPreferences("connection_doctor", Context.MODE_PRIVATE)
    val report = MutableStateFlow(preferences.getString("last_inspection", "Choose a profile to check its connection. Benchmarks are in Debug and Statistics → Benchmarks.").orEmpty())
    val busy = MutableStateFlow(false)
    private var job: Job? = null
    fun cancel() {
        job?.cancel()
    }
    fun inspect(profile: PlatformV2) = action {
        detectedContext.value = null
        val destination = if (profile.compatibleType == ClientType.LITERT_LM) "ON_DEVICE" else endpointLocality(profile.apiUrl).name
        val lines = mutableListOf("Destination: $destination", "Profile: ${profile.name} · ${profile.model}")
        if (profile.compatibleType == ClientType.LITERT_LM) {
            lines += "Actual runtime: ${runtime.state.value.backend ?: "not loaded"}"
            lines += "Accelerator: ${runtime.state.value.engineSpec?.accelerator ?: "not loaded"}"
            runtime.state.value.fallbackReason?.let { lines += "Fallback: $it" }
        } else {
            require(!profile.apiUrl.contains('?') && !profile.apiUrl.contains('@')) { "Use a clean provider URL and the credential field." }
            val base = profile.apiUrl.trimEnd('/')
            val modelsUrl = if (profile.compatibleType == ClientType.OLLAMA && !base.endsWith("/v1")) "$base/api/tags" else "$base/models"
            val response = network().get(modelsUrl) { profile.token?.takeIf(String::isNotBlank)?.let { bearerAuth(it) } }
            val modelBody = response.bodyAsText()
            lines += "Model discovery: HTTP ${response.status.value} (${modelBody.length} response characters)"
            if (response.status.value == 200) {
                dev.chungjungsoo.gptmobile.data.context.discoverContextCeiling(modelBody, profile.model)?.let { ceiling ->
                    detectedContext.value = profile.uid to ceiling
                    lines += "Server reports a context ceiling of $ceiling tokens. Apply it below to constrain this profile."
                }
            }
            if (response.status.value in setOf(401, 403)) lines += "Authentication was rejected. Reconnect or replace the credential."
            if (profile.compatibleType == ClientType.LLAMA) {
                val capabilities = gateway.getCapabilities(ProviderRequestConfig(profile.apiUrl, profile.token))
                lines += if (capabilities == null) "No Gateway capability response; this may be a direct llama.cpp server." else "Gateway capabilities: $capabilities"
            }
            lines += "Streaming and performance tests are in Debug and Statistics → Benchmarks. Check live tool connections in Tool connections."
        }
        finish(lines.joinToString("\n"))
    }
    private fun finish(text: String) {
        val safe = DiagnosticRedactor.redact(text).take(12000)
        report.value = safe
        preferences.edit().putString("last_inspection", safe).apply()
    }
    private fun action(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeout(180_000) { block() }
            } catch (e: CancellationException) {
                report.value = "Canceled. The request was stopped on this client."
                throw e
            } catch (_: Exception) {
                report.value = "Connection check failed. Verify URL, credentials, server model, Wi-Fi/VPN and local network permission."
            } finally {
                busy.value = false
            }
        }
    }
}
