package dev.chungjungsoo.gptmobile.data.agent

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2

/**
 * Creates an isolated runner so tool-call budgets cannot leak between concurrent runs.
 * Applies a 45-second per-tool timeout safeguard so that stalled or hanging local/MCP
 * tools never lock the entire conversation indefinitely.
 */
fun agentRunnerForPlatform(
    platform: PlatformV2,
    runOverride: Int? = null,
    maxRoundsOverride: Int? = null
): AgentRunner = AgentRunner(
    AgentRunLimits(
        maxRounds = maxRoundsOverride ?: Int.MAX_VALUE,
        maxToolCalls = runOverride ?: platform.maxToolCalls,
        toolTimeoutMillis = 45_000L
    )
)
