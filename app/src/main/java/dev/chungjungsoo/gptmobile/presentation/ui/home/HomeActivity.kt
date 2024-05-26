package dev.chungjungsoo.gptmobile.presentation.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatActivity
import dev.chungjungsoo.gptmobile.presentation.ui.setting.SettingActivity
import dev.chungjungsoo.gptmobile.util.collectManagedState

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GPTMobileTheme {
                val platformState by homeViewModel.platformState.collectManagedState()
                val showSelectModelDialog by homeViewModel.showSelectModelDialog.collectManagedState()
                val chatList by homeViewModel.chatList.collectManagedState()

                HomeScreen(chatList, ::openSettings) { homeViewModel.openSelectModelDialog() }
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

    override fun onResume() {
        super.onResume()
        homeViewModel.fetchPlatformStatus()
        homeViewModel.fetchChats()
    }

    private fun openNewChat() {
        val chatRoom = homeViewModel.createEmptyChatRoom()
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chatRoom", chatRoom)
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingActivity::class.java)
        startActivity(intent)
    }
}
