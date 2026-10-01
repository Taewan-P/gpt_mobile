package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkMode
import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkStore
import dev.chungjungsoo.gptmobile.data.benchmark.delegationBenchmarkRating
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

data class DelegationRecoveryOption(
    val profileUid: String,
    val profileName: String,
    val model: String,
    val provider: String,
    val delegationScore: Int?
)

data class DelegationRecoveryRequest(
    val id: String,
    val runId: String,
    val failedProfileUid: String,
    val failedProfileName: String,
    val reason: String,
    val options: List<DelegationRecoveryOption>
)

internal sealed interface DelegationRecoveryDecision {
    data class SwitchProfile(val profileUid: String) : DelegationRecoveryDecision
    data object PrimaryOnly : DelegationRecoveryDecision
}

/**
 * Bridges a suspended delegation failover decision to the active conversation UI.
 * No automatic fallback is selected while a request is pending.
 */
@Singleton
class DelegationRecoveryInteractions @Inject constructor(
    private val benchmarkStore: BenchmarkStore
) {
    private val responses = ConcurrentHashMap<String, CompletableDeferred<DelegationRecoveryDecision>>()
    private val _pending = MutableStateFlow<List<DelegationRecoveryRequest>>(emptyList())
    val pending = _pending.asStateFlow()

    internal suspend fun request(
        runId: String,
        failedProfile: PlatformV2,
        candidates: List<PlatformV2>,
        reason: String
    ): DelegationRecoveryDecision {
        val distinctCandidates = candidates
            .filter { it.uid != failedProfile.uid }
            .distinctBy { it.uid }
        if (distinctCandidates.isEmpty()) return DelegationRecoveryDecision.PrimaryOnly

        runCatching { benchmarkStore.load() }
        val scores = delegationScoresByWorker(benchmarkStore.history.value)
        val options = distinctCandidates.map { profile ->
            DelegationRecoveryOption(
                profileUid = profile.uid,
                profileName = profile.name,
                model = profile.model,
                provider = profile.compatibleType.name,
                delegationScore = scores[profile.uid]
            )
        }.sortedWith(
            compareByDescending<DelegationRecoveryOption> { it.delegationScore ?: -1 }
                .thenBy { it.profileName.lowercase() }
        )

        val id = UUID.randomUUID().toString()
        val response = CompletableDeferred<DelegationRecoveryDecision>()
        responses[id] = response
        _pending.update { requests ->
            requests + DelegationRecoveryRequest(
                id = id,
                runId = runId,
                failedProfileUid = failedProfile.uid,
                failedProfileName = failedProfile.name,
                reason = reason.take(500),
                options = options
            )
        }

        return try {
            // Never leave an agent run suspended forever if the app leaves the foreground.
            withTimeoutOrNull(120_000) { response.await() } ?: DelegationRecoveryDecision.PrimaryOnly
        } finally {
            responses.remove(id)
            _pending.update { requests -> requests.filterNot { it.id == id } }
        }
    }

    fun respond(requestId: String, profileUid: String?) {
        val request = _pending.value.firstOrNull { it.id == requestId } ?: return
        val response = responses[requestId] ?: return
        val decision = profileUid
            ?.takeIf { uid -> request.options.any { it.profileUid == uid } }
            ?.let(DelegationRecoveryDecision::SwitchProfile)
            ?: DelegationRecoveryDecision.PrimaryOnly
        response.complete(decision)
    }

    private fun delegationScoresByWorker(
        history: List<dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkRun>
    ): Map<String, Int?> = history
        .filter {
            it.mode == BenchmarkMode.DELEGATION &&
                it.suiteVersion == 2 &&
                it.finished &&
                !it.canceled
        }
        .groupBy { run ->
            run.samples.asSequence().mapNotNull { it.delegation?.workerUid }.firstOrNull()
        }
        .filterKeys { it != null }
        .mapKeys { it.key!! }
        .mapValues { (_, runs) ->
            delegationBenchmarkRating(runs.sortedByDescending { it.startedAt }.take(5)).score
        }
}
