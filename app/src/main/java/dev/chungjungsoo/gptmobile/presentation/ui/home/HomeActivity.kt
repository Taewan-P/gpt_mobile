package dev.chungjungsoo.gptmobile.presentation.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatActivity
import dev.chungjungsoo.gptmobile.presentation.ui.setting.SettingActivity

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GPTMobileTheme {
                val platformState by homeViewModel.platformState.collectAsStateWithLifecycle(
                    lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                )
                val showSelectModelDialog by homeViewModel.showSelectModelDialog.collectAsStateWithLifecycle(
                    lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                )
                val chatList by homeViewModel.chatList.collectAsStateWithLifecycle(
                    lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                )

                HomeScreen(::openSettings) { homeViewModel.openSelectModelDialog() }
                if (showSelectModelDialog) {
                    SelectPlatformDialog(
                        platformState,
                        onDismissRequest = { homeViewModel.closeSelectModelDialog() },
                        onConfirmation = {
                            homeViewModel.closeSelectModelDialog()
                            openNewChat()
                        },
                        onPlatformSelect = { homeViewModel.updateCheckedState(it) }
                    )
                }
            }
        }
    }

    private fun openNewChat() {
        val chatRoom = homeViewModel.createEmptyChatRoom()
        val intent = Intent(this, ChatActivity::class.java)
        val bundle = Bundle()
        bundle.putBoolean("newChat", true)
        bundle.putParcelable("chatRoom", chatRoom)
        intent.putExtra("chatInfo", bundle)
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingActivity::class.java)
        startActivity(intent)
    }
}
