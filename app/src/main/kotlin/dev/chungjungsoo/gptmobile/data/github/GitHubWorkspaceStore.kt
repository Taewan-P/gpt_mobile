package dev.chungjungsoo.gptmobile.data.github

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Repository context only. Credentials stay in SecretVault; file drafts stay in memory. */
@Singleton
class GitHubWorkspaceStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("github_workspace", Context.MODE_PRIVATE)

    fun get(connectionUid: String): GitHubRepositoryContext? = preferences.getString(connectionUid, null)?.let {
        runCatching { Json.decodeFromString<GitHubRepositoryContext>(it) }.getOrNull()
    }

    fun set(connectionUid: String, selection: GitHubRepositoryContext?) {
        preferences.edit().apply {
            if (selection == null) remove(connectionUid) else putString(connectionUid, Json.encodeToString(selection))
        }.apply()
    }
}
