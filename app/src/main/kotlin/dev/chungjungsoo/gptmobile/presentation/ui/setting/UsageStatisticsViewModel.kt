package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.dao.AgentRunDao
import dev.chungjungsoo.gptmobile.data.database.dao.RunOutputLength
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProfileModelUsage(
    val profileUid: String,
    val profileName: String,
    val provider: String,
    val model: String,
    val runs: Int,
    val reportedTokens: Long,
    val estimatedTokens: Long
)

data class ToolUsage(val name: String, val calls: Int, val failures: Int)

data class UsageStatistics(
    val runs: Int = 0,
    val conversations: Int = 0,
    val profiles: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val canceled: Int = 0,
    val inputTokens: Long = 0,
    val generatedTokens: Long = 0,
    val reportedRuns: Int = 0,
    val averageSeconds: Double? = null,
    val modelTokens: List<Pair<String, Long>> = emptyList(),
    val profileTokens: List<Pair<String, Long>> = emptyList(),
    val modelRuns: List<Pair<String, Long>> = emptyList(),
    val dailyTokens: List<Pair<LocalDate, Long>> = emptyList(),
    val toolCalls: Int = 0,
    val failedTools: Int = 0,
    val successPercent: Double? = null,
    val estimatedTokens: Long = 0,
    val estimatedRuns: Int = 0,
    val profileModelUsage: List<ProfileModelUsage> = emptyList(),
    val toolUsage: List<ToolUsage> = emptyList(),
    val performance: List<ModelPerformance> = emptyList(),
    val days: Int = 30,
    val profileNames: Map<String, String> = emptyMap(),
    val profilePerformance: List<ProfilePerformance> = emptyList()
)

internal fun calculateUsageStatistics(
    runs: List<AgentRun>,
    profileNames: Map<String, String>,
    days: Int,
    today: LocalDate = LocalDate.now(),
    outputLengths: List<RunOutputLength> = emptyList(),
    tools: List<ToolEvent> = emptyList()
): UsageStatistics {
    require(days in setOf(0, 7, 30))
    val zone = ZoneId.systemDefault()
    fun day(run: AgentRun): LocalDate = Instant.ofEpochSecond(run.createdAt).atZone(zone).toLocalDate()
    fun inRange(date: LocalDate) = !date.isAfter(today) && (days == 0 || !date.isBefore(today.minusDays(days - 1L)))
    val filtered = runs.filter { inRange(day(it)) }
    val lengths = outputLengths.associate { it.runId to it.characters.coerceAtLeast(0) }
    fun estimate(run: AgentRun): Long = if (run.outputTokens == null && run.status == AgentRunStatus.COMPLETED) {
        val length = lengths[run.runId] ?: 0L
        length / 4 + if (length % 4 > 0) 1 else 0
    } else {
        0L
    }
    val completed = filtered.count { it.status == AgentRunStatus.COMPLETED }
    val failed = filtered.count { it.status == AgentRunStatus.FAILED || it.status == AgentRunStatus.INTERRUPTED }
    val selectedTools = tools.filter { event ->
        event.startedAt?.let { inRange(Instant.ofEpochSecond(it).atZone(zone).toLocalDate()) } ?: (days == 0)
    }
    val toolUsage = selectedTools.groupBy { it.connectionUidSnapshot to it.modelToolName.ifBlank { it.toolName } }.values.map { events ->
        val event = events.first()
        val name = listOfNotNull(event.connectionNameSnapshot?.takeIf { it.isNotBlank() }, event.modelToolName.ifBlank { event.toolName }).joinToString(" · ")
        ToolUsage(name, events.size, events.count { it.status == ToolEventStatus.FAILED || it.isError })
    }.sortedWith(compareByDescending<ToolUsage> { it.calls }.thenBy { it.name })
    fun totals(key: (AgentRun) -> String): List<Pair<String, Long>> = filtered.filter { it.outputTokens != null }
        .groupBy(key).map { (name, items) -> name to items.sumOf { it.outputTokens!!.coerceAtLeast(0).toLong() } }
        .sortedByDescending { it.second }
    val daily = filtered.groupBy(::day).mapValues { (_, items) -> items.sumOf { it.outputTokens?.coerceAtLeast(0)?.toLong() ?: 0L } }
    val chartDays = if (days == 0) 30 else days
    val durations = filtered.mapNotNull { run ->
        val start = run.startedAt ?: return@mapNotNull null
        val end = run.completedAt ?: return@mapNotNull null
        (end - start).coerceAtLeast(0L)
    }
    return UsageStatistics(
        runs = filtered.size,
        conversations = filtered.map { it.chatId }.distinct().size,
        profiles = filtered.map { it.profileUid }.distinct().size,
        completed = completed,
        failed = failed,
        canceled = filtered.count { it.status == AgentRunStatus.CANCELED },
        inputTokens = filtered.sumOf { it.inputTokens?.coerceAtLeast(0)?.toLong() ?: 0 },
        generatedTokens = filtered.sumOf { it.outputTokens?.coerceAtLeast(0)?.toLong() ?: 0 },
        reportedRuns = filtered.count { it.outputTokens != null },
        averageSeconds = durations.takeIf { it.isNotEmpty() }?.average(),
        modelTokens = totals { it.modelSnapshot.ifBlank { "Unknown model" } },
        profileTokens = totals { profileNames[it.profileUid] ?: "Deleted profile (${it.profileUid.take(8)})" },
        modelRuns = filtered.groupingBy { it.modelSnapshot.ifBlank { "Unknown model" } }.eachCount().map { it.key to it.value.toLong() }.sortedByDescending { it.second },
        dailyTokens = (chartDays - 1 downTo 0).map { today.minusDays(it.toLong()).let { date -> date to (daily[date] ?: 0) } },
        successPercent = if (completed + failed > 0) 100.0 * completed / (completed + failed) else null,
        estimatedTokens = filtered.sumOf(::estimate),
        estimatedRuns = filtered.count { estimate(it) > 0 },
        profileModelUsage = filtered.groupBy { Triple(it.profileUid, it.providerSnapshot, it.modelSnapshot) }.map { (key, items) ->
            ProfileModelUsage(
                key.first,
                profileNames[key.first] ?: "Deleted profile (${key.first.take(8)})",
                key.second.ifBlank { "Unknown provider" },
                key.third.ifBlank { "Unknown model" },
                items.size,
                items.sumOf { it.outputTokens?.coerceAtLeast(0)?.toLong() ?: 0 },
                items.sumOf(::estimate)
            )
        }.sortedWith(compareByDescending<ProfileModelUsage> { it.runs }.thenBy { it.profileName }.thenBy { it.provider }.thenBy { it.model }.thenBy { it.profileUid }),
        toolCalls = selectedTools.size,
        failedTools = toolUsage.sumOf { it.failures },
        toolUsage = toolUsage,
        days = days
    )
}

@HiltViewModel
class UsageStatisticsViewModel @Inject constructor(
    runDao: AgentRunDao,
    persistenceDao: AgentPersistenceDao,
    settings: SettingRepository,
    database: dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
) : ViewModel() {
    private val days = MutableStateFlow(30)
    fun selectRange(value: Int) {
        require(value in setOf(0, 7, 30))
        days.value = value
    }

    val statistics = combine(
        runDao.observeRecent(10_000),
        persistenceDao.observeRecentToolEvents(10_000),
        settings.observePlatformV2s(),
        days,
        runDao.observeUnreportedOutputLengths()
    ) { runs, tools, profiles, range, lengths ->
        calculateUsageStatistics(runs, profiles.associate { it.uid to it.name }, range, outputLengths = lengths, tools = tools)
            .copy(profileNames = profiles.associate { it.uid to it.name })
    }.combine(database.invocationDao().statistics()) { stats, invocations ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val selected = invocations.filter {
            val day = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
            !day.isAfter(today) && (stats.days == 0 || !day.isBefore(today.minusDays(stats.days - 1L)))
        }
        stats.copy(performance = modelPerformance(selected), profilePerformance = profilePerformance(selected, stats.profileNames))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UsageStatistics())
}
