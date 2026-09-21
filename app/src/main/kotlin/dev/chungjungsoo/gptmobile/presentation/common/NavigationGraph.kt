package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import dev.chungjungsoo.gptmobile.presentation.theme.fastEffectsSpec
import dev.chungjungsoo.gptmobile.presentation.theme.fastSpatialSpec
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatScreen
import dev.chungjungsoo.gptmobile.presentation.ui.home.HomeScreen
import dev.chungjungsoo.gptmobile.presentation.ui.migrate.MigrateScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.AboutScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.AddPlatformScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LicenseScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelsScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setting.PlatformSettingScreen
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
        startDestination = Route.CHAT_LIST,
        enterTransition = {
            fadeIn(fastEffectsSpec()) +
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, fastSpatialSpec()) { it / 12 }
        },
        exitTransition = { fadeOut(fastEffectsSpec()) },
        popEnterTransition = { fadeIn(fastEffectsSpec()) },
        popExitTransition = {
            fadeOut(fastEffectsSpec()) +
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, fastSpatialSpec()) { it / 12 }
        }
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
                onBackAction = { navController.navigateUp() },
                onNavigateToLocalModels = { navController.navigate(Route.SETUP_LOCAL_MODELS) }
            )
        }
        composable(route = Route.SETUP_LOCAL_MODELS) {
            LocalModelsScreen(
                onNavigationClick = { navController.navigateUp() }
            )
        }
        composable(route = Route.SETUP_COMPLETE) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModelV2 = hiltViewModel(parentEntry)
            val platforms by setupViewModel.platforms.collectAsStateWithLifecycle()
            SetupCompleteScreen(
                platforms = platforms,
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
            onBackAction = { navController.navigateUp() },
            onNavigateToLocalModels = { navController.navigate(Route.LOCAL_MODELS) }
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
                onNavigateToAddPlatform = { navController.navigate(Route.ADD_PLATFORM) },
                onNavigateToPlatformSetting = { platformUid ->
                    navController.navigate(
                        Route.PLATFORM_SETTINGS.replace("{platformUid}", platformUid)
                    )
                },
                onNavigateToLocalModels = { navController.navigate(Route.LOCAL_MODELS) },
                onNavigateToToolConnections = { navController.navigate(Route.TOOL_CONNECTIONS) },
                onNavigateToAboutPage = { navController.navigate(Route.ABOUT_PAGE) }
            )
        }
        composable(Route.ADD_PLATFORM) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            AddPlatformScreen(
                settingViewModel = settingViewModel,
                onNavigationClick = { navController.navigateUp() },
                onSave = { navController.navigateUp() },
                onNavigateToLocalModels = { navController.navigate(Route.LOCAL_MODELS) }
            )
        }
        composable(
            Route.PLATFORM_SETTINGS,
            arguments = listOf(navArgument("platformUid") { type = NavType.StringType })
        ) {
            PlatformSettingScreen(
                onNavigationClick = { navController.navigateUp() },
                onNavigateToLocalModels = { navController.navigate(Route.LOCAL_MODELS) }
            )
        }
        composable(Route.LOCAL_MODELS) {
            LocalModelsScreen(
                onNavigationClick = { navController.navigateUp() }
            )
        }
        composable(Route.TOOL_CONNECTIONS) {
            ToolConnectionsScreen(
                viewModel = toolConnectionsViewModel,
                onLaunchOAuth = onLaunchOAuth,
                onNavigationClick = { navController.navigateUp() },
                onAddConnectionClick = { navController.navigate(Route.ADD_TOOL_CONNECTION) },
                onEditConnectionClick = { connectionUid ->
                    navController.navigate(Route.EDIT_TOOL_CONNECTION.replace("{connectionUid}", connectionUid))
                }
            )
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
