package dev.chungjungsoo.gptmobile.presentation.ui.setup

import android.content.Intent
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.dto.APIModel
import dev.chungjungsoo.gptmobile.data.dto.ApiType
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.home.HomeActivity

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

        val openAIModels = generateOpenAIModelList()
        val anthropicModels = generateAnthropicModelList()
        val googleModels = generateGoogleModelList()

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
                                    proceedToNextStep(navController)
                                }
                            )
                        }
                        composable(route = SetupStep.TOKEN_INPUT.name) {
                            TokenInputScreen(
                                modifier = Modifier.fillMaxSize(),
                                platformState = platformState,
                                onChangeEvent = { platform, s ->
                                    setupViewModel.updateToken(platform, s)
                                },
                                onClearEvent = { platform ->
                                    setupViewModel.updateToken(platform, "")
                                },
                                onNextButtonClicked = {
                                    proceedToNextStep(navController)
                                }
                            )
                        }
                        composable(route = SetupStep.OPENAI_MODEL.name) {
                            SelectModelScreen(
                                modifier = Modifier.fillMaxSize(),
                                title = stringResource(R.string.select_openai_model),
                                description = stringResource(R.string.select_openai_model_description),
                                availableModels = openAIModels,
                                model = setModel(platformState, ApiType.OPENAI, 0),
                                onChangeEvent = { model ->
                                    setupViewModel.updateModel(ApiType.OPENAI, model)
                                },
                                onNextButtonClicked = {
                                    proceedToNextStep(navController)
                                }
                            )
                        }
                        composable(route = SetupStep.ANTHROPIC_MODEL.name) {
                            SelectModelScreen(
                                modifier = Modifier.fillMaxSize(),
                                title = stringResource(R.string.select_anthropic_model),
                                description = stringResource(R.string.select_anthropic_model_description),
                                availableModels = anthropicModels,
                                model = setModel(platformState, ApiType.ANTHROPIC, 1),
                                onChangeEvent = { model ->
                                    setupViewModel.updateModel(ApiType.ANTHROPIC, model)
                                },
                                onNextButtonClicked = {
                                    proceedToNextStep(navController)
                                }
                            )
                        }
                        composable(route = SetupStep.GOOGLE_MODEL.name) {
                            SelectModelScreen(
                                modifier = Modifier.fillMaxSize(),
                                title = stringResource(R.string.select_google_model),
                                description = stringResource(id = R.string.select_google_model_description),
                                availableModels = googleModels,
                                model = setModel(platformState, ApiType.GOOGLE, 0),
                                onChangeEvent = { model ->
                                    setupViewModel.updateModel(ApiType.GOOGLE, model)
                                },
                                onNextButtonClicked = {
                                    proceedToNextStep(navController)
                                }
                            )
                        }
                        composable(route = SetupStep.SETUP_COMPLETE.name) {
                            SetupCompleteScreen(
                                modifier = Modifier.fillMaxSize(),
                                onNextButtonClicked = {
                                    saveSettings()
                                    proceedToNextStep(navController)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun generateOpenAIModelList() = setupViewModel.openaiModels.mapIndexed { index, model ->
        val (name, description) = when (index) {
            0 -> getString(R.string.gpt_4o) to getString(R.string.gpt_4o_description)
            1 -> getString(R.string.gpt_4_turbo) to getString(R.string.gpt_4_turbo_description)
            2 -> getString(R.string.gpt_4) to getString(R.string.gpt_4_description)
            3 -> getString(R.string.gpt_3_5_turbo) to getString(R.string.gpt_3_5_description)
            else -> "" to ""
        }
        APIModel(name, description, model)
    }

    private fun generateAnthropicModelList() = setupViewModel.anthropicModels.mapIndexed { index, model ->
        val (name, description) = when (index) {
            0 -> getString(R.string.claude_3_opus) to getString(R.string.claude_3_opus_description)
            1 -> getString(R.string.claude_3_sonnet) to getString(R.string.claude_3_sonnet_description)
            2 -> getString(R.string.claude_3_haiku) to getString(R.string.claude_3_haiku_description)
            else -> "" to ""
        }
        APIModel(name, description, model)
    }

    private fun generateGoogleModelList() = setupViewModel.googleModels.mapIndexed { index, model ->
        val (name, description) = when (index) {
            0 -> getString(R.string.gemini_1_5_pro) to getString(R.string.gemini_1_5_pro_description)
            1 -> getString(R.string.gemini_1_5_flash) to getString(R.string.gemini_1_5_flash_description)
            2 -> getString(R.string.gemini_1_0_pro) to getString(R.string.gemini_1_0_pro_description)
            else -> "" to ""
        }
        APIModel(name, description, model)
    }

    private fun setModel(platformState: List<Platform>, apiType: ApiType, defaultModelIndex: Int): String {
        return platformState.find { it.name == apiType }?.model ?: setDefaultModel(apiType, defaultModelIndex)
    }

    private fun setDefaultModel(apiType: ApiType, defaultModelIndex: Int): String {
        val modelList = when (apiType) {
            ApiType.OPENAI -> setupViewModel.openaiModels
            ApiType.ANTHROPIC -> setupViewModel.anthropicModels
            ApiType.GOOGLE -> setupViewModel.googleModels
        }.toList()

        val model = modelList[defaultModelIndex]
        setupViewModel.updateModel(apiType, model)

        return model
    }

    private fun saveSettings() {
        setupViewModel.saveCheckedState()
        setupViewModel.saveTokenState()
        setupViewModel.saveModelState()
    }

    private fun proceedToNextStep(navController: NavHostController) {
        Log.d("status", setupViewModel.platformState.value.toString())
        val nextStep = nextSetupRoute(navController.currentDestination?.route, setupViewModel.platformState.value)
        navigateToNextRoute(nextStep, navController)
    }

    private fun navigateToNextRoute(nextStep: SetupStep?, navController: NavHostController) {
        if (nextStep == null) {
            goToHomeActivity()
        } else {
            navController.navigate(route = nextStep.name)
        }
    }

    private fun goToHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun nextSetupRoute(currentRoute: String?, platformState: List<Platform>): SetupStep? {
        val steps = listOf(
            SetupStep.SELECT_PLATFORM,
            SetupStep.TOKEN_INPUT,
            SetupStep.OPENAI_MODEL,
            SetupStep.ANTHROPIC_MODEL,
            SetupStep.GOOGLE_MODEL,
            SetupStep.SETUP_COMPLETE
        )
        val commonSteps = setOf(SetupStep.SELECT_PLATFORM, SetupStep.TOKEN_INPUT, SetupStep.SETUP_COMPLETE)
        val platformStep = mapOf(
            SetupStep.OPENAI_MODEL to ApiType.OPENAI,
            SetupStep.ANTHROPIC_MODEL to ApiType.ANTHROPIC,
            SetupStep.GOOGLE_MODEL to ApiType.GOOGLE
        )

        val currentIndex = steps.indexOfFirst { it.name == currentRoute }
        val enabledPlatform = platformState.filter { it.enabled }.map { it.name }.toSet()
        val remainingSteps = steps.filterIndexed { index, setupStep ->
            index > currentIndex &&
                (setupStep in commonSteps || platformStep[setupStep] in enabledPlatform)
        }

        if (remainingSteps.isEmpty()) {
            // Setup Complete
            return null
        }

        return remainingSteps.first()
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
