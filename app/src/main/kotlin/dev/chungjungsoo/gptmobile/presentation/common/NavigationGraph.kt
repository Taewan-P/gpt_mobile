package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import dev.chungjungsoo.gptmobile.data.database.entity.ConversationMode
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.model.collectReusableProfileLabels
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatScreen
import dev.chungjungsoo.gptmobile.presentation.ui.home.HomeScreen
import dev.chungjungsoo.gptmobile.presentation.ui.mcp.McpMarketplaceScreen
import dev.chungjungsoo.gptmobile.presentation.ui.migrate.MigrateScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.AboutScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.AddPlatformScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.AdvancedSettingsScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.AiPlatformsScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.DebugDiagnosticsScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.FactVaultScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.FactVaultViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LicenseScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelsScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.McpToolsSelectionScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.OpenRouterSettingsScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.PlatformSettingScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.PlatformSettingViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.setting.ProviderConnectionSettingsScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.SettingScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.SettingViewModelV2
import dev.chungjungsoo.gptmobile.presentation.ui.setting.ToolConnectionEditorScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.ToolConnectionsScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.ToolConnectionsViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupCompleteScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupPlatformListScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupPlatformTypeScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupPlatformWizardScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupViewModelV2
import dev.chungjungsoo.gptmobile.presentation.ui.startscreen.StartScreen
import dev.chungjungsoo.gptmobile.presentation.viewmodel.OpenRouterSettingsViewModel

@Composable
fun SetupNavGraph(
    navController: NavHostController,
    toolConnectionsViewModel: ToolConnectionsViewModel,
    onLaunchOAuth: (String) -> Unit = {}
) {
    NavHost(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        navController = navController,
        startDestination = Route.CHAT_LIST
    ) {
        homeScreenNavigation(navController)
        migrationScreenNavigation(navController)
        startScreenNavigation(navController)
        setupNavigation(navController)
        settingNavigation(navController, toolConnectionsViewModel, onLaunchOAuth)
        chatScreenNavigation(navController)
    }
}

fun NavGraphBuilder.migrationScreenNavigation(navController: NavHostController) {
    composable(Route.MIGRATE_V2) {
        MigrateScreen {
            navController.navigate(Route.CHAT_LIST) {
                popUpTo(Route.MIGRATE_V2) { inclusive = true }
            }
        }
    }
}

fun NavGraphBuilder.startScreenNavigation(navController: NavHostController) {
    composable(Route.GET_STARTED) {
        StartScreen { navController.navigate(Route.SETUP_ROUTE) }
    }
}

fun NavGraphBuilder.setupNavigation(
    navController: NavHostController
) {
    navigation(startDestination = Route.SETUP_PLATFORM_LIST, route = Route.SETUP_ROUTE) {
        composable(route = Route.SETUP_PLATFORM_LIST) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModelV2 = hiltViewModel(parentEntry)
            SetupPlatformListScreen(
                setupViewModel = setupViewModel,
                onAddPlatform = { navController.navigate(Route.SETUP_PLATFORM_TYPE) },
                onComplete = { navController.navigate(Route.SETUP_COMPLETE) },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.SETUP_PLATFORM_TYPE) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModelV2 = hiltViewModel(parentEntry)
            SetupPlatformTypeScreen(
                setupViewModel = setupViewModel,
                onPlatformTypeSelected = { navController.navigate(Route.SETUP_PLATFORM_WIZARD) },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.SETUP_LOCAL_MODELS) {
            LocalModelsScreen(
                startInMarketplace = true,
                onOpenProfile = { uid -> navController.navigate(Route.PLATFORM_SETTINGS.replace("{platformUid}", uid)) },
                onNavigationClick = { navController.navigateUp() }
            )
        }
        composable(route = Route.SETUP_PLATFORM_WIZARD) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModelV2 = hiltViewModel(parentEntry)
            SetupPlatformWizardScreen(
                setupViewModel = setupViewModel,
                onComplete = {
                    navController.popBackStack(Route.SETUP_PLATFORM_LIST, inclusive = false)
                },
                onBackAction = { navController.navigateUp() },
                onNavigateToLocalModels = { navController.navigate(Route.SETUP_LOCAL_MODELS) }
            )
        }
        composable(route = Route.SETUP_COMPLETE) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModelV2 = hiltViewModel(parentEntry)
            val platforms by setupViewModel.platforms.collectAsStateWithLifecycle()
            SetupCompleteScreen(
                isPendingLocalPlatform = platforms.any { platform ->
                    !platform.enabled && platform.compatibleType == dev.chungjungsoo.gptmobile.data.model.ClientType.LITERT_LM
                },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Route.GET_STARTED) { inclusive = true }
                    }
                },
                onBackAction = { navController.navigateUp() }
            )
        }
    }
}

fun NavGraphBuilder.homeScreenNavigation(navController: NavHostController) {
    composable(Route.CHAT_LIST) {
        HomeScreen(
            settingOnClick = { navController.navigate(Route.SETTING_ROUTE) },
            onExistingChatClick = { chatRoom, targetMessageId ->
                val targetSuffix = if (targetMessageId != null) "&targetMessageId=$targetMessageId" else ""
                navController.navigate(
                    "chat_room/${chatRoom.id}?enabled=${chatRoom.enabledPlatform.joinToString(",")}" +
                        "&mode=${chatRoom.conversationMode}$targetSuffix"
                )
            },
            navigateToNewChat = { enabledPlatforms, conversationMode ->
                navController.navigate(
                    "chat_room/0?enabled=${enabledPlatforms.joinToString(",")}&mode=$conversationMode"
                )
            }
        )
    }
}

fun NavGraphBuilder.chatScreenNavigation(navController: NavHostController) {
    composable(
        Route.CHAT_ROOM,
        arguments = listOf(
            navArgument("chatRoomId") { type = NavType.IntType },
            navArgument("enabledPlatforms") { defaultValue = "" },
            navArgument("conversationMode") { defaultValue = ConversationMode.STANDARD },
            navArgument("targetMessageId") {
                type = NavType.IntType
                defaultValue = -1
            }
        )
    ) {
        ChatScreen(
            onBackAction = { navController.navigateUp() },
            onNavigateToLocalModels = { navController.navigate(Route.LOCAL_MODELS + "?marketplace=true") }
        )
    }
}

fun NavGraphBuilder.settingNavigation(
    navController: NavHostController,
    toolConnectionsViewModel: ToolConnectionsViewModel,
    onLaunchOAuth: (String) -> Unit = {}
) {
    navigation(startDestination = Route.SETTINGS, route = Route.SETTING_ROUTE) {
        composable(Route.SETTINGS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            SettingScreen(
                settingViewModel = settingViewModel,
                onNavigationClick = { navController.navigateUp() },
                onNavigateToAiPlatforms = { navController.navigate(Route.AI_PLATFORMS) },
                onNavigateToLocalModels = { navController.navigate(Route.LOCAL_MODELS) },
                onNavigateToOpenRouterSettings = { navController.navigate(Route.OPENROUTER_SETTINGS) },
                onNavigateToToolConnections = { navController.navigate(Route.TOOL_CONNECTIONS) },
                onNavigateToAdvancedSettings = { navController.navigate(Route.ADVANCED_SETTINGS) },
                onNavigateToFactVault = { navController.navigate(Route.FACT_VAULT) },
                onNavigateToDebugDiagnostics = { navController.navigate(Route.DEBUG_DIAGNOSTICS) },
                onNavigateToAboutPage = { navController.navigate(Route.ABOUT_PAGE) }
            )
        }
        composable(Route.OPENROUTER_SETTINGS) {
            val viewModel: OpenRouterSettingsViewModel = hiltViewModel()
            OpenRouterSettingsScreen(
                viewModel = viewModel,
                onNavigationClick = { navController.navigateUp() }
            )
        }

        composable(Route.FACT_VAULT) {
            val viewModel: FactVaultViewModel = hiltViewModel()
            FactVaultScreen(viewModel, onBack = { navController.navigateUp() })
        }

        composable(Route.ADVANCED_SETTINGS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            AdvancedSettingsScreen(
                viewModel = settingViewModel,
                onNavigationClick = { navController.navigateUp() }
            )
        }
        composable(Route.USAGE_STATISTICS) {
            dev.chungjungsoo.gptmobile.presentation.ui.setting.UsageStatisticsScreen(onBack = { navController.navigateUp() })
        }
        composable(
            Route.PROFILE_BENCHMARKS,
            arguments = listOf(
                navArgument("profileUid") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            dev.chungjungsoo.gptmobile.presentation.ui.setting.ProfileBenchmarkScreen(
                onBack = { navController.navigateUp() },
                onUsage = { navController.navigate(Route.USAGE_STATISTICS) }
            )
        }
        composable(Route.DEBUG_DIAGNOSTICS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            DebugDiagnosticsScreen(
                settingViewModel = settingViewModel,
                onNavigationClick = { navController.navigateUp() },
                onStatisticsClick = { navController.navigate(Route.USAGE_STATISTICS) },
                onBenchmarksClick = { navController.navigate(Route.PROFILE_BENCHMARKS.replace("{profileUid}", "")) }
            )
        }
        composable(Route.AI_PLATFORMS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            AiPlatformsScreen(
                settingViewModel = settingViewModel,
                onNavigationClick = { navController.navigateUp() },
                onNavigateToAddPlatform = { navController.navigate(Route.ADD_PLATFORM) },
                onNavigateToOpenRouterSettings = { navController.navigate(Route.OPENROUTER_SETTINGS) },
                onNavigateToProviderSettings = { connectionUid ->
                    navController.navigate(
                        Route.PROVIDER_CONNECTION_SETTINGS.replace("{connectionUid}", connectionUid)
                    )
                },
                onNavigateToPlatformSetting = { platformUid ->
                    navController.navigate(
                        Route.PLATFORM_SETTINGS.replace("{platformUid}", platformUid)
                    )
                }
            )
        }
        composable(
            Route.PROVIDER_CONNECTION_SETTINGS,
            arguments = listOf(navArgument("connectionUid") { type = NavType.StringType })
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            ProviderConnectionSettingsScreen(
                connectionUid = backStackEntry.arguments?.getString("connectionUid").orEmpty(),
                settingViewModel = settingViewModel,
                onNavigationClick = { navController.navigateUp() }
            )
        }
        composable(Route.ADD_PLATFORM) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            val providerConnections by settingViewModel.providerConnections.collectAsStateWithLifecycle()
            val profiles by settingViewModel.platformState.collectAsStateWithLifecycle()
            val reusableLabels = remember(profiles) {
                collectReusableProfileLabels(profiles.map { it.labels })
            }
            AddPlatformScreen(
                onNavigationClick = { navController.navigateUp() },
                savedConnections = providerConnections,
                reusableLabels = reusableLabels,
                onSave = { platform, newConnection, credential ->
                    settingViewModel.addPlatform(platform, newConnection, credential)
                    navController.navigateUp()
                },
                onNavigateToLocalModels = { navController.navigate(Route.LOCAL_MODELS + "?marketplace=true") }
            )
        }
        composable(
            Route.PLATFORM_SETTINGS,
            arguments = listOf(navArgument("platformUid") { type = NavType.StringType })
        ) { backStackEntry ->
            val platformUid = backStackEntry.arguments?.getString("platformUid") ?: ""
            val platformViewModel: PlatformSettingViewModel = hiltViewModel(backStackEntry)
            PlatformSettingScreen(
                settingViewModel = platformViewModel,
                onNavigationClick = { navController.navigateUp() },
                onNavigateToBenchmarks = { navController.navigate(Route.PROFILE_BENCHMARKS.replace("{profileUid}", android.net.Uri.encode(platformUid))) },
                onNavigateToUsage = { navController.navigate(Route.USAGE_STATISTICS) },
                onNavigateToLocalModels = { navController.navigate(Route.LOCAL_MODELS + "?marketplace=true") },
                onNavigateToMcpTools = {
                    navController.navigate(
                        Route.MCP_TOOLS_SELECTION.replace("{platformUid}", platformUid)
                    )
                }
            )
        }
        composable(
            Route.MCP_TOOLS_SELECTION,
            arguments = listOf(navArgument("platformUid") { type = NavType.StringType })
        ) { backStackEntry ->
            val platformUid = backStackEntry.arguments?.getString("platformUid") ?: ""
            val platformViewModel: PlatformSettingViewModel = hiltViewModel(
                remember(backStackEntry) {
                    runCatching { navController.getBackStackEntry(Route.PLATFORM_SETTINGS.replace("{platformUid}", platformUid)) }.getOrElse { backStackEntry }
                }
            )
            McpToolsSelectionScreen(
                platformUid = platformUid,
                viewModel = platformViewModel,
                onNavigationClick = { navController.navigateUp() }
            )
        }
        composable(
            Route.LOCAL_MODELS + "?marketplace={marketplace}",
            arguments = listOf(
                navArgument("marketplace") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { entry ->
            LocalModelsScreen(
                startInMarketplace = entry.arguments?.getBoolean("marketplace") == true,
                onOpenProfile = { uid -> navController.navigate(Route.PLATFORM_SETTINGS.replace("{platformUid}", uid)) },
                onNavigationClick = { navController.navigateUp() }
            )
        }
        composable(Route.TOOL_CONNECTIONS) {
            ToolConnectionsScreen(
                viewModel = toolConnectionsViewModel,
                onLaunchOAuth = onLaunchOAuth,
                onNavigationClick = { navController.navigateUp() },
                onMarketplaceClick = { navController.navigate(Route.MCP_MARKETPLACE) },
                onAddConnectionClick = { navController.navigate(Route.ADD_TOOL_CONNECTION) },
                onEditConnectionClick = { connectionUid ->
                    navController.navigate(Route.EDIT_TOOL_CONNECTION.replace("{connectionUid}", connectionUid))
                }
            )
        }
        composable(Route.MCP_MARKETPLACE) { entry ->
            val settings: SettingViewModelV2 = hiltViewModel(remember(entry) { navController.getBackStackEntry(Route.SETTING_ROUTE) })
            val profiles by settings.platformState.collectAsStateWithLifecycle()
            var installedName by remember { mutableStateOf<String?>(null) }
            val uiState by toolConnectionsViewModel.uiState.collectAsStateWithLifecycle()
            val installedAliases = remember(uiState.connections) {
                uiState.connections.map { it.alias }.toSet()
            }
            val mcpProvider = remember {
                ToolConnectionsViewModel.providers.first { it.type == ToolConnectionType.MCP }
            }
            McpMarketplaceScreen(
                installedAliases = installedAliases,
                onNavigationClick = { navController.navigateUp() },
                onInstallPresetWithConfig = { preset, name, alias, endpoint, authType, credential, allowCleartext ->
                    toolConnectionsViewModel.saveConnection(
                        existing = null,
                        provider = mcpProvider,
                        name = name,
                        alias = alias,
                        endpointUrl = endpoint,
                        authType = authType,
                        credential = credential,
                        oauthClientId = "",
                        allowCleartext = allowCleartext,
                        clearCredential = false,
                        onSuccess = {
                            if (authType == dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType.OAUTH) {
                                navController.navigateUp()
                            } else {
                                installedName = name
                            }
                        }
                    )
                }
            )
            installedName?.let { name ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { installedName = null },
                    title = { androidx.compose.material3.Text("$name saved") },
                    text = {
                        androidx.compose.foundation.lazy.LazyColumn {
                            item { androidx.compose.material3.Text("Choose an AI profile, then select the server tools it may use.") }
                            items(profiles.size) { index ->
                                val profile = profiles[index]
                                androidx.compose.material3.TextButton(onClick = {
                                    installedName = null
                                    navController.navigate(Route.MCP_TOOLS_SELECTION.replace("{platformUid}", profile.uid))
                                }) { androidx.compose.material3.Text(profile.name) }
                            }
                            if (profiles.isEmpty()) item { androidx.compose.material3.Text("Add an AI profile in Settings to enable its tools.") }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            installedName = null
                            navController.navigateUp()
                        }) { androidx.compose.material3.Text("Done") }
                    }
                )
            }
            uiState.errorMessage?.let { message ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = toolConnectionsViewModel::clearError,
                    title = { androidx.compose.material3.Text("Could not install") },
                    text = { androidx.compose.material3.Text(message) },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = toolConnectionsViewModel::clearError) { androidx.compose.material3.Text("Close") } }
                )
            }
        }
        composable(Route.ADD_TOOL_CONNECTION) {
            ToolConnectionEditorScreen(
                viewModel = toolConnectionsViewModel,
                onNavigationClick = { navController.navigateUp() },
                onSaveComplete = { navController.navigateUp() }
            )
        }
        composable(
            Route.EDIT_TOOL_CONNECTION,
            arguments = listOf(navArgument("connectionUid") { type = NavType.StringType })
        ) {
            ToolConnectionEditorScreen(
                connectionUid = it.arguments?.getString("connectionUid"),
                viewModel = toolConnectionsViewModel,
                onNavigationClick = { navController.navigateUp() },
                onSaveComplete = { navController.navigateUp() }
            )
        }
        composable(Route.ABOUT_PAGE) {
            AboutScreen(
                onNavigationClick = { navController.navigateUp() },
                onNavigationToLicense = { navController.navigate(Route.LICENSE) }
            )
        }
        composable(Route.LICENSE) {
            LicenseScreen(onNavigationClick = { navController.navigateUp() })
        }
    }
}
