package dev.chungjungsoo.gptmobile.presentation.ui.setting

import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType

enum class ToolConnectionSetupPath {
    WEB_SEARCH,
    MCP_SERVER
}

enum class ToolConnectionSetupStep {
    CONNECTION_TYPE,
    WEB_SEARCH_PROVIDER,
    DETAILS,
    AUTHENTICATION
}

data class ToolConnectionSetupFlow(
    val step: ToolConnectionSetupStep = ToolConnectionSetupStep.CONNECTION_TYPE,
    val path: ToolConnectionSetupPath? = null,
    val provider: ToolConnectionProvider? = null
) {
    val canContinue: Boolean
        get() = when (step) {
            ToolConnectionSetupStep.CONNECTION_TYPE -> path != null
            ToolConnectionSetupStep.WEB_SEARCH_PROVIDER -> provider != null
            ToolConnectionSetupStep.DETAILS -> provider != null
            ToolConnectionSetupStep.AUTHENTICATION -> true
        }

    val isSaveStep: Boolean
        get() = when (path) {
            ToolConnectionSetupPath.WEB_SEARCH -> step == ToolConnectionSetupStep.DETAILS
            ToolConnectionSetupPath.MCP_SERVER -> step == ToolConnectionSetupStep.AUTHENTICATION
            null -> false
        }

    fun selectPath(path: ToolConnectionSetupPath): ToolConnectionSetupFlow = copy(
        path = path,
        provider = when (path) {
            ToolConnectionSetupPath.WEB_SEARCH -> provider.takeIf { this.path == path && it?.type != ToolConnectionType.MCP }
            ToolConnectionSetupPath.MCP_SERVER -> ToolConnectionsViewModel.providers.first { it.type == ToolConnectionType.MCP }
        }
    )

    fun selectWebProvider(provider: ToolConnectionProvider): ToolConnectionSetupFlow = if (path == ToolConnectionSetupPath.WEB_SEARCH && provider.type != ToolConnectionType.MCP) {
        copy(provider = provider)
    } else {
        this
    }

    fun next(): ToolConnectionSetupFlow = when (step) {
        ToolConnectionSetupStep.CONNECTION_TYPE -> when (path) {
            ToolConnectionSetupPath.WEB_SEARCH -> copy(step = ToolConnectionSetupStep.WEB_SEARCH_PROVIDER)
            ToolConnectionSetupPath.MCP_SERVER -> copy(step = ToolConnectionSetupStep.DETAILS)
            null -> this
        }

        ToolConnectionSetupStep.WEB_SEARCH_PROVIDER ->
            if (provider != null) copy(step = ToolConnectionSetupStep.DETAILS) else this

        ToolConnectionSetupStep.DETAILS ->
            if (path == ToolConnectionSetupPath.MCP_SERVER) copy(step = ToolConnectionSetupStep.AUTHENTICATION) else this

        else -> this
    }

    fun back(): ToolConnectionSetupFlow = when (step) {
        ToolConnectionSetupStep.CONNECTION_TYPE -> this

        ToolConnectionSetupStep.WEB_SEARCH_PROVIDER -> copy(step = ToolConnectionSetupStep.CONNECTION_TYPE)

        ToolConnectionSetupStep.DETAILS -> when (path) {
            ToolConnectionSetupPath.WEB_SEARCH -> copy(step = ToolConnectionSetupStep.WEB_SEARCH_PROVIDER)
            ToolConnectionSetupPath.MCP_SERVER -> copy(step = ToolConnectionSetupStep.CONNECTION_TYPE)
            null -> ToolConnectionSetupFlow()
        }

        ToolConnectionSetupStep.AUTHENTICATION -> copy(step = ToolConnectionSetupStep.DETAILS)
    }

    companion object {
        // Returns null for a stored type this build does not know, so the editor cannot
        // rewrite the connection's type and endpoint by falling back to another provider.
        fun editing(connection: ToolConnection): ToolConnectionSetupFlow? {
            val provider = ToolConnectionsViewModel.providers.firstOrNull { it.type == connection.type } ?: return null
            val path = if (provider.type == ToolConnectionType.MCP) {
                ToolConnectionSetupPath.MCP_SERVER
            } else {
                ToolConnectionSetupPath.WEB_SEARCH
            }
            return ToolConnectionSetupFlow(
                step = ToolConnectionSetupStep.DETAILS,
                path = path,
                provider = provider
            )
        }
    }
}
