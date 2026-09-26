package dev.chungjungsoo.gptmobile.data.diagnostics

data class GroupedAppLog(val entry: AppLogEntry, val lastTime: Long = entry.time, val repetitions: Int = 1)

/** Presentation-only compression; the recorder and exported file retain every event. */
internal fun groupAppLogs(entries: List<AppLogEntry>): List<GroupedAppLog> {
    val grouped = mutableListOf<GroupedAppLog>()
    entries.forEach { entry ->
        val previous = grouped.lastOrNull()
        if (previous != null &&
            entry.time - previous.lastTime in 0..2000 &&
            entry.level == previous.entry.level &&
            entry.tag == previous.entry.tag &&
            entry.message == previous.entry.message
        ) {
            grouped[grouped.lastIndex] = previous.copy(lastTime = entry.time, repetitions = previous.repetitions + 1)
        } else {
            grouped.add(GroupedAppLog(entry))
        }
    }
    return grouped
}
