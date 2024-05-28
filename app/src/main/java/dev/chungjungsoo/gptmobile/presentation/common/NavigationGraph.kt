package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.presentation.ui.home.HomeScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SelectModelScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SelectPlatformScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupCompleteScreen
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.setup.TokenInputScreen
import dev.chungjungsoo.gptmobile.presentation.ui.startscreen.StartScreen

@Composable
fun SetupNavGraph(
    navController: NavHostController
) {
    NavHost(navController = navController, startDestination = Route.CHAT_LIST) {
        startScreenNavigation { navController.navigate(it) }
        setupNavigation(navController)
        homeScreenNavigation()
    }
}

fun NavGraphBuilder.startScreenNavigation(
    onNavigateTo: (String) -> Unit
) {
    composable(Route.GET_STARTED) {
        StartScreen { onNavigateTo(Route.SELECT_PLATFORM) }
    }
}

fun NavGraphBuilder.setupNavigation(
    navController: NavHostController
) {
    navigation(startDestination = Route.SELECT_PLATFORM, route = Route.SETUP_ROUTE) {
        composable(route = Route.SELECT_PLATFORM) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModel = hiltViewModel(parentEntry)
            SelectPlatformScreen(
                setupViewModel = setupViewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.TOKEN_INPUT) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModel = hiltViewModel(parentEntry)
            TokenInputScreen(
                setupViewModel = setupViewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.OPENAI_MODEL_SELECT) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModel = hiltViewModel(parentEntry)
            SelectModelScreen(
                setupViewModel = setupViewModel,
                currentRoute = Route.OPENAI_MODEL_SELECT,
                platformType = ApiType.OPENAI,
                onNavigate = { route -> navController.navigate(route) },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.ANTHROPIC_MODEL_SELECT) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModel = hiltViewModel(parentEntry)
            SelectModelScreen(
                setupViewModel = setupViewModel,
                currentRoute = Route.ANTHROPIC_MODEL_SELECT,
                platformType = ApiType.ANTHROPIC,
                onNavigate = { route -> navController.navigate(route) },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.GOOGLE_MODEL_SELECT) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModel = hiltViewModel(parentEntry)
            SelectModelScreen(
                setupViewModel = setupViewModel,
                currentRoute = Route.GOOGLE_MODEL_SELECT,
                platformType = ApiType.GOOGLE,
                onNavigate = { route -> navController.navigate(route) },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.SETUP_COMPLETE) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModel = hiltViewModel(parentEntry)
            SetupCompleteScreen(
                setupViewModel = setupViewModel,
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

fun NavGraphBuilder.homeScreenNavigation() {
    composable(Route.CHAT_LIST) {
        HomeScreen(
            settingOnClick = {},
            onExistingChatClick = {},
            navigateToNewChat = {}
        )
    }
}
