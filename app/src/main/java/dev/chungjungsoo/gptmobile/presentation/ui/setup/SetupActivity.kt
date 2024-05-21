package dev.chungjungsoo.gptmobile.presentation.ui.setup

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme

@AndroidEntryPoint
class SetupActivity : ComponentActivity() {

    enum class SetupStep {
        SELECT_PLATFORM,
        TOKEN_INPUT,
        OPENAI_MODEL,
        ANTHROPIC_MODEL,
        GOOGLE_MODEL,
        SETUP_COMPLETE
    }

    private val setupViewModel: SetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GPTMobileTheme {
                val navController = rememberNavController()
                val platformState by setupViewModel.platformState.collectAsStateWithLifecycle(
                    lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                )
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        SetupAppBar(
                            navController = navController,
                            backAction = { navController.navigateUp() },
                            exitAction = { finish() }
                        )
                    }

                ) { innerPadding ->
                    NavHost(
                        modifier = Modifier.padding(innerPadding),
                        navController = navController,
                        startDestination = SetupStep.SELECT_PLATFORM.name
                    ) {
                        composable(route = SetupStep.SELECT_PLATFORM.name) {
                            SelectPlatformScreen(
                                modifier = Modifier.fillMaxSize(),
                                platformState = platformState,
                                onCheckedEvent = {
                                    setupViewModel.updateCheckedState(it)
                                },
                                onNextButtonClicked = {
                                    setupViewModel.saveCheckedState()
                                    Log.d("status", setupViewModel.platformState.value.toString())
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupAppBar(
    navController: NavHostController,
    backAction: () -> Unit,
    exitAction: () -> Unit
) {
    TopAppBar(
        title = { },
        navigationIcon = {
            IconButton(onClick = {
                if (navController.previousBackStackEntry != null) {
                    backAction.invoke()
                } else {
                    exitAction.invoke()
                }
            }) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
            }
        }
    )
}
