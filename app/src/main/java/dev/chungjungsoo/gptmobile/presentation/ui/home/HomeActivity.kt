package dev.chungjungsoo.gptmobile.presentation.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatActivity

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GPTMobileTheme {
                HomeScreen(
                    homeViewModel,
                    ::openSettings,
                    ::openExistingChat,
                    ::openNewChat
                )
            }
        }
    }

    private fun openExistingChat(chatRoom: ChatRoom) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chatRoom", chatRoom)
        startActivity(intent)
    }

    private fun openNewChat() {
        val chatRoom = homeViewModel.createEmptyChatRoom()
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chatRoom", chatRoom)
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
    }
}
