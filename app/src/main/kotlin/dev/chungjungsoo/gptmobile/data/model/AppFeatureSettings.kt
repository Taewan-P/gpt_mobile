package dev.chungjungsoo.gptmobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppFeatureSettings(
    val backgroundGeneration: Boolean = true,
    val responseNotifications: Boolean = true,
    val automaticConversationTitles: Boolean = true,
    val archiveOlderAssistantReplies: Boolean = true,
    val smartSuggestions: Boolean = true,
    val showReasoning: Boolean = true,
    val remoteMcpConnections: Boolean = true,
    val sharedReadOnlyToolCalls: Boolean = true,
    val deviceLocationTool: Boolean = true,
    val providerModelDiscovery: Boolean = true,
    val diagnosticsCollection: Boolean = false,
    val debugShowToolCalls: Boolean = true,
    val debugShowTotalTokens: Boolean = true,
    val debugShowTokenSpeed: Boolean = true,
    val debugShowTimeToFirstToken: Boolean = true,
    val debugShowRuntime: Boolean = true,
    val debugShowHardware: Boolean = true,
    val debugShowNetwork: Boolean = false,
    val openRouterBatchProcessing: Boolean = false,
    val qnnAutomaticFallback: Boolean = true,
    val localCpuThreads: Int = 0,
    val localModelCache: Boolean = true,
    val localSpeculativeDecoding: SpeculativeDecodingMode = SpeculativeDecodingMode.AUTO,
    val localNativeMetrics: Boolean = false,
    val localIdleMinutes: Int = 10,
    val delegation: ModelDelegationSettings = ModelDelegationSettings(),
    val tokenBudget: dev.chungjungsoo.gptmobile.data.context.TokenBudgetSettings = dev.chungjungsoo.gptmobile.data.context.TokenBudgetSettings()
) {
    fun withFeature(feature: AppFeature, enabled: Boolean): AppFeatureSettings = when (feature) {
        AppFeature.BACKGROUND_GENERATION -> copy(backgroundGeneration = enabled)
        AppFeature.RESPONSE_NOTIFICATIONS -> copy(responseNotifications = enabled)
        AppFeature.AUTOMATIC_TITLES -> copy(automaticConversationTitles = enabled)
        AppFeature.ARCHIVE_OLDER_REPLIES -> copy(archiveOlderAssistantReplies = enabled)
        AppFeature.SHOW_REASONING -> copy(showReasoning = enabled)
        AppFeature.SMART_SUGGESTIONS -> copy(smartSuggestions = enabled)
        AppFeature.REMOTE_MCP -> copy(remoteMcpConnections = enabled)
        AppFeature.SHARED_TOOL_CALLS -> copy(sharedReadOnlyToolCalls = enabled)
        AppFeature.DEVICE_LOCATION -> copy(deviceLocationTool = enabled)
        AppFeature.MODEL_DISCOVERY -> copy(providerModelDiscovery = enabled)
        AppFeature.DIAGNOSTICS -> copy(diagnosticsCollection = enabled)
        AppFeature.OPENROUTER_BATCH -> copy(openRouterBatchProcessing = enabled)
        AppFeature.QNN_AUTO_FALLBACK -> copy(qnnAutomaticFallback = enabled)
    }

    fun withDebugMetric(metric: DebugMetric, enabled: Boolean): AppFeatureSettings = when (metric) {
        DebugMetric.TOOL_CALLS -> copy(debugShowToolCalls = enabled)
        DebugMetric.TOTAL_TOKENS -> copy(debugShowTotalTokens = enabled)
        DebugMetric.TOKEN_SPEED -> copy(debugShowTokenSpeed = enabled)
        DebugMetric.TIME_TO_FIRST_TOKEN -> copy(debugShowTimeToFirstToken = enabled)
        DebugMetric.RUNTIME -> copy(debugShowRuntime = enabled)
        DebugMetric.HARDWARE -> copy(debugShowHardware = enabled)
        DebugMetric.NETWORK -> copy(debugShowNetwork = enabled)
    }
}

@Serializable
enum class SpeculativeDecodingMode(val label: String, val enabled: Boolean?) {
    AUTO("Model default", null),
    OFF("Off", false),
    ON("On", true)
}

enum class DebugMetric(val title: String, val description: String) {
    TOOL_CALLS("Tool calls", "Show tool names, durations, status and failures."),
    TOTAL_TOKENS("Token totals", "Show prompt, completion and combined token counts."),
    TOKEN_SPEED("Token speed", "Show live and average tokens generated per second."),
    TIME_TO_FIRST_TOKEN("Time to first token", "Show latency before the first generated token."),
    RUNTIME("Runtime", "Show active provider, model, local backend and fallback state."),
    HARDWARE("Hardware", "Show memory, thermal and accelerator information."),
    NETWORK("Network", "Show provider latency and connection diagnostics.")
}

enum class AppFeature(
    val title: String,
    val description: String
) {
    BACKGROUND_GENERATION(
        "Background generation",
        "Keep active AI responses running when the app leaves the foreground."
    ),
    RESPONSE_NOTIFICATIONS(
        "Response notifications",
        "Show a detailed notification when a background AI response finishes."
    ),
    AUTOMATIC_TITLES(
        "Automatic conversation titles",
        "Refresh generated titles as the conversation evolves."
    ),
    ARCHIVE_OLDER_REPLIES(
        "Archive older responses",
        "Collapse assistant replies older than the latest three into expandable history."
    ),
    SHOW_REASONING("Show reasoning", "Display the thinking text supplied by an AI, with an expandable panel in conversations."),
    SMART_SUGGESTIONS(
        "Smart response suggestions",
        "Generate contextual suggestion buttons below assistant responses."
    ),
    REMOTE_MCP(
        "Remote MCP connections",
        "Allow profiles to discover and call tools from remote MCP servers."
    ),
    SHARED_TOOL_CALLS(
        "Shared read-only tool calls",
        "Reuse identical safe tool results across AIs in the same conversation turn."
    ),
    DEVICE_LOCATION(
        "Device location tool",
        "Allow profiles with the location tool assigned to request phone location."
    ),
    MODEL_DISCOVERY(
        "Provider model discovery",
        "Index models from supported remote providers for searchable profile model pickers."
    ),
    DIAGNOSTICS(
        "Diagnostics collection",
        "Collect local performance, token, runtime and tool metrics for Debug Mode."
    ),
    OPENROUTER_BATCH(
        "OpenRouter batch processing",
        "Allow asynchronous OpenRouter Batch API jobs for supported workloads."
    ),
    QNN_AUTO_FALLBACK(
        "QNN automatic fallback",
        "Automatically switch to LiteRT when Qualcomm QNN cannot load the selected model."
    )
}
