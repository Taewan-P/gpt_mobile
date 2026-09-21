package dev.chungjungsoo.gptmobile.presentation.ui.main

import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.data.agent.tool.MCP_OAUTH_SCHEME
import dev.chungjungsoo.gptmobile.data.agent.tool.isMcpOAuthCallbackUri
import dev.chungjungsoo.gptmobile.presentation.common.LocalDynamicTheme
import dev.chungjungsoo.gptmobile.presentation.common.LocalThemeMode
import dev.chungjungsoo.gptmobile.presentation.common.Route
import dev.chungjungsoo.gptmobile.presentation.common.SetupNavGraph
import dev.chungjungsoo.gptmobile.presentation.common.ThemeLoadState
import dev.chungjungsoo.gptmobile.presentation.common.ThemeSettingProvider
import dev.chungjungsoo.gptmobile.presentation.common.ThemeViewModel
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.theme.toApplicationNightMode
import dev.chungjungsoo.gptmobile.presentation.ui.setting.ToolConnectionsViewModel
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()
    private val toolConnectionsViewModel: ToolConnectionsViewModel by viewModels()
    private lateinit var authTabLauncher: ActivityResultLauncher<Intent>
    private lateinit var oauthCustomTabLauncher: ActivityResultLauncher<Intent>
    private var lastOAuthCallback: String? = null

    @Volatile
    private var isThemeModeReady = false

    @Volatile
    private var isStartupRouteReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition { !isThemeModeReady || !isStartupRouteReady }
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        authTabLauncher = AuthTabIntent.registerActivityResultLauncher(this) { result ->
            dispatchOAuthCallback(result.resultUri?.toString())
        }
        oauthCustomTabLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            toolConnectionsViewModel.cancelPendingOAuth()
        }

        // Prevent keyboard from pushing the entire view up - composable handles insets via imePadding()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        setContent {
            val navController = rememberNavController()
            val isReady by mainViewModel.isReady.collectAsStateWithLifecycle()
            val themeLoadState by themeViewModel.loadState.collectAsStateWithLifecycle()
            val themeSetting by themeViewModel.themeSetting.collectAsStateWithLifecycle()
            val themeMode = themeSetting.themeMode
            val isThemeResolved = themeLoadState != ThemeLoadState.LOADING

            LaunchedEffect(isThemeResolved, themeMode) {
                if (isThemeResolved) {
                    getSystemService(UiModeManager::class.java)
                        .setApplicationNightMode(themeMode.toApplicationNightMode())
                    isThemeModeReady = true
                }
            }

            ThemeSettingProvider(themeViewModel) {
                GPTMobileTheme(
                    dynamicTheme = LocalDynamicTheme.current,
                    themeMode = LocalThemeMode.current
                ) {
                    if (isReady) {
                        SetupNavGraph(
                            navController = navController,
                            toolConnectionsViewModel = toolConnectionsViewModel,
                            onLaunchOAuth = ::launchOAuth
                        )
                        LaunchedEffect(navController) {
                            navController.awaitStartupRoute()
                        }
                    }
                }
            }
        }
        dispatchOAuthIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchOAuthIntent(intent)
    }

    private fun launchOAuth(authorizationUri: String) {
        val uri = Uri.parse(authorizationUri)
        try {
            AuthTabIntent.Builder().build().launch(authTabLauncher, uri, MCP_OAUTH_SCHEME)
        } catch (_: ActivityNotFoundException) {
            try {
                val customTabsIntent = CustomTabsIntent.Builder().build()
                customTabsIntent.intent.data = uri
                oauthCustomTabLauncher.launch(customTabsIntent.intent)
            } catch (_: ActivityNotFoundException) {
                toolConnectionsViewModel.failOAuthLaunch()
            }
        }
    }

    private fun dispatchOAuthCallback(callbackUri: String?) {
        if (callbackUri != null && callbackUri == lastOAuthCallback) return
        lastOAuthCallback = callbackUri
        toolConnectionsViewModel.completeOAuthCallback(callbackUri)
    }

    private fun dispatchOAuthIntent(intent: Intent?) = intent?.data?.toString()
        ?.takeIf(::isMcpOAuthCallbackUri)
        ?.let(::dispatchOAuthCallback)

    private suspend fun NavHostController.awaitStartupRoute() {
        when (mainViewModel.event.first()) {
            MainViewModel.SplashEvent.OpenIntro -> {
                navigate(Route.GET_STARTED) {
                    popUpTo(Route.CHAT_LIST) { inclusive = true }
                }
            }

            MainViewModel.SplashEvent.OpenMigrate -> {
                navigate(Route.MIGRATE_V2) {
                    popUpTo(Route.CHAT_LIST) { inclusive = true }
                }
            }

            MainViewModel.SplashEvent.OpenHome -> {}
        }
        isStartupRouteReady = true
    }
}
