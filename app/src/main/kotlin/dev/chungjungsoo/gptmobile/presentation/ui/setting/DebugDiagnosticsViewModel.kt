package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.dao.AgentRunDao
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DebugAnalyticsState(
    val recentRuns: List<AgentRun> = emptyList(),
    val recentToolEvents: List<ToolEvent> = emptyList(),
    val completedRuns: Int = 0,
    val failedRuns: Int = 0,
    val activeRuns: Int = 0,
    val completedToolCalls: Int = 0,
    val failedToolCalls: Int = 0,
    val averageRunDurationMs: Long? = null,
    val averageToolDurationMs: Long? = null,
    val modelUsage: List<Pair<String, Int>> = emptyList(),
    val providerUsage: List<Pair<String, Int>> = emptyList(),
    val profileUsage: List<Pair<String, Int>> = emptyList(),
    val modelTokenUsage: List<Pair<String, Long>> = emptyList(),
    val profileTokenUsage: List<Pair<String, Long>> = emptyList(),
    val totalTrackedTokens: Long = 0L,
    val invocations: List<dev.chungjungsoo.gptmobile.data.accounting.ModelInvocation> = emptyList(),
    val profileNames: Map<String, String> = emptyMap()
)

@HiltViewModel
class DebugDiagnosticsViewModel @Inject constructor(
    agentRunDao: AgentRunDao,
    agentPersistenceDao: AgentPersistenceDao,
    ledger: dev.chungjungsoo.gptmobile.data.accounting.InvocationLedger,
    settings: dev.chungjungsoo.gptmobile.data.repository.SettingRepository
) : ViewModel() {
    val analytics: StateFlow<DebugAnalyticsState> = combine(
        agentRunDao.observeRecent(250),
        agentPersistenceDao.observeRecentToolEvents(500)
    ) { runs, tools ->
        DebugAnalyticsState(
            recentRuns = runs,
            recentToolEvents = tools,
            completedRuns = runs.count { it.status == AgentRunStatus.COMPLETED },
            failedRuns = runs.count { it.status == AgentRunStatus.FAILED },
            activeRuns = runs.count { it.status == AgentRunStatus.RUNNING || it.status == AgentRunStatus.QUEUED },
            completedToolCalls = tools.count { it.status == ToolEventStatus.COMPLETED },
            failedToolCalls = tools.count { it.status == ToolEventStatus.FAILED },
            averageRunDurationMs = runs.mapNotNull { run ->
                val start = run.startedAt ?: return@mapNotNull null
                val end = run.completedAt ?: return@mapNotNull null
                ((end - start) * 1000L).coerceAtLeast(0L)
            }.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            modelUsage = runs.groupingBy { it.modelSnapshot.ifBlank { "Unknown model" } }
                .eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
            providerUsage = runs.groupingBy { it.providerSnapshot.ifBlank { "Unknown provider" } }
                .eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
            profileUsage = runs.groupingBy { it.profileUid.ifBlank { "Unknown profile" } }
                .eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
            modelTokenUsage = runs
                .mapNotNull { run -> run.totalTokens?.let { run.modelSnapshot.ifBlank { "Unknown model" } to it.toLong() } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, tokens) -> tokens.sum() }
                .entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
            profileTokenUsage = runs
                .mapNotNull { run -> run.totalTokens?.let { run.profileUid.ifBlank { "Unknown profile" } to it.toLong() } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, tokens) -> tokens.sum() }
                .entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
            totalTrackedTokens = runs.sumOf { it.totalTokens?.toLong() ?: 0L },
            averageToolDurationMs = tools.mapNotNull { event ->
                val start = event.startedAt ?: return@mapNotNull null
                val end = event.completedAt ?: return@mapNotNull null
                ((end - start) * 1000L).coerceAtLeast(0L)
            }.takeIf { it.isNotEmpty() }?.average()?.toLong()
        )
    }.combine(ledger.diagnostics) { state, invocations -> state.copy(invocations = invocations) }
        .combine(settings.observePlatformV2s()) { state, profiles -> state.copy(profileNames = profiles.associate { it.uid to it.name }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DebugAnalyticsState())
}
