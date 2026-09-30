package dev.chungjungsoo.gptmobile.data.chat

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class ConversationReadStateStore @Inject constructor(
    @param:ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("conversation_read_state_v1", Context.MODE_PRIVATE)
    private val _unreadChatIds = MutableStateFlow(
        preferences.getStringSet(KEY_UNREAD_IDS, emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .toSet()
    )
    val unreadChatIds = _unreadChatIds.asStateFlow()

    fun markUnread(chatId: Int) {
        if (chatId <= 0) return
        _unreadChatIds.update { it + chatId }
        persist()
    }

    fun markViewed(chatId: Int) {
        if (chatId <= 0) return
        _unreadChatIds.update { it - chatId }
        persist()
    }

    fun remove(chatId: Int) {
        markViewed(chatId)
    }

    private fun persist() {
        check(
            preferences.edit()
                .putStringSet(KEY_UNREAD_IDS, _unreadChatIds.value.mapTo(mutableSetOf(), Int::toString))
                .commit()
        ) { "Could not persist conversation read state." }
    }

    private companion object {
        const val KEY_UNREAD_IDS = "unread_chat_ids"
    }
}
