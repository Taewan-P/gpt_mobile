package dev.chungjungsoo.gptmobile.data.permissions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the explicit data-sharing unlock for free-model tools.
 *
 * Grants are scoped to the immutable profile uid + tool id so a renamed profile
 * keeps its choice while a new profile always requires fresh acknowledgement.
 */
@Singleton
class FreeModelToolConsentStore @Inject constructor(
    @param:ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("free_model_tool_consent_v1", Context.MODE_PRIVATE)

    fun isGranted(profileUid: String, toolId: String): Boolean =
        preferences.getBoolean(key(profileUid, toolId), false)

    fun grant(profileUid: String, toolId: String) {
        check(preferences.edit().putBoolean(key(profileUid, toolId), true).commit()) {
            "Could not save free-model tool permission."
        }
    }

    fun revoke(profileUid: String, toolId: String) {
        check(preferences.edit().remove(key(profileUid, toolId)).commit()) {
            "Could not revoke free-model tool permission."
        }
    }

    fun revokeProfile(profileUid: String) {
        val prefix = "$profileUid:"
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        check(editor.commit()) { "Could not reset free-model tool permissions." }
    }

    private fun key(profileUid: String, toolId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(toolId.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$profileUid:$digest"
    }
}
