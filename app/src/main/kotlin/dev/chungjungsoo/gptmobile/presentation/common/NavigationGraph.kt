package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatScreen
import dev.chungjungsoo.gptmobile.presentation.ui.home.HomeScreen
import dev.chungjungsoo.gptmobile.presentation.ui.migrate.MigrateScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.AboutScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.AddMcpServerScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.AddPlatformScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LicenseScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.McpServerDetailScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.McpSettingsScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.PlatformSettingScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.SettingScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.SettingViewModelV2
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupCompleteScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupPlatformListScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupPlatformTypeScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupPlatformWizardScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupViewModelV2
import dev.chungjungsoo.gptmobile.presentation.ui.startscreen.StartScreen

@Composable
fun SetupNavGraph(navController: NavHostController) {
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
        settingNavigation(navController)
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
        composable(route = Route.SETUP_PLATFORM_WIZARD) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModelV2 = hiltViewModel(parentEntry)
            SetupPlatformWizardScreen(
                setupViewModel = setupViewModel,
                onComplete = {
                    // Go back to platform list after adding a platform
                    navController.popBackStack(Route.SETUP_PLATFORM_LIST, inclusive = false)
                },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.SETUP_COMPLETE) {
            SetupCompleteScreen(
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
            settingOnClick = { navController.navigate(Route.SETTING_ROUTE) { launchSingleTop = true } },
            onExistingChatClick = { chatRoom ->
                val enabledPlatformString = chatRoom.enabledPlatform.joinToString(",")
                navController.navigate(
                    Route.CHAT_ROOM
                        .replace(oldValue = "{chatRoomId}", newValue = "${chatRoom.id}")
                        .replace(oldValue = "{enabledPlatforms}", newValue = enabledPlatformString)
                )
            },
            navigateToNewChat = {
                val enabledPlatformString = it.joinToString(",")
                navController.navigate(
                    Route.CHAT_ROOM
                        .replace(oldValue = "{chatRoomId}", newValue = "0")
                        .replace(oldValue = "{enabledPlatforms}", newValue = enabledPlatformString)
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
            navArgument("enabledPlatforms") { defaultValue = "" }
        )
    ) {
        ChatScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.settingNavigation(navController: NavHostController) {
    navigation(startDestination = Route.SETTINGS, route = Route.SETTING_ROUTE) {
        composable(Route.SETTINGS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            SettingScreen(
                settingViewModel = settingViewModel,
                onNavigationClick = { navController.navigateUp() },
                onNavigateToAddPlatform = { navController.navigate(Route.ADD_PLATFORM) },
                onNavigateToPlatformSetting = { platformUid ->
                    navController.navigate(
                        Route.PLATFORM_SETTINGS.replace("{platformUid}", platformUid)
                    )
                },
                onNavigateToMcpSettings = { navController.navigate(Route.MCP_SETTINGS) },
                onNavigateToAboutPage = { navController.navigate(Route.ABOUT_PAGE) }
            )
        }
        composable(Route.ADD_PLATFORM) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            AddPlatformScreen(
                onNavigationClick = { navController.navigateUp() },
                onSave = { platform ->
                    settingViewModel.addPlatform(platform)
                    navController.navigateUp()
                }
            )
        }
        composable(
            Route.PLATFORM_SETTINGS,
            arguments = listOf(navArgument("platformUid") { type = NavType.StringType })
        ) {
            PlatformSettingScreen(
                onNavigationClick = { navController.navigateUp() }
            )
        }
        composable(Route.MCP_SETTINGS) {
            McpSettingsScreen(
                onNavigationClick = { navController.navigateUp() },
                onAddServer = { navController.navigate(Route.ADD_MCP_SERVER) },
                onServerClick = { serverId ->
                    navController.navigate(Route.MCP_SERVER_DETAIL.replace("{serverId}", "$serverId"))
                }
            )
        }
        composable(Route.ADD_MCP_SERVER) {
            AddMcpServerScreen(
                onNavigationClick = { navController.navigateUp() },
                onServerAdded = { navController.navigateUp() }
            )
        }
        composable(
            Route.MCP_SERVER_DETAIL,
            arguments = listOf(navArgument("serverId") { type = NavType.IntType })
        ) {
            McpServerDetailScreen(
                onNavigationClick = { navController.navigateUp() }
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
