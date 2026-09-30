package dev.chungjungsoo.gptmobile.data.permissions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** A grant is scoped to a tool and connection destination, not a mutable display name. */
@Singleton
class ToolTrustStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("tool_always_allow", Context.MODE_PRIVATE)
    fun allows(connection: ToolConnection, tool: String): Boolean = preferences.getBoolean(providerKey(connection), false) || preferences.getBoolean(key(connection, tool), false)
    fun allow(connection: ToolConnection, tool: String) {
        check(preferences.edit().putBoolean(key(connection, tool), true).commit())
    }
    fun allowProvider(connection: ToolConnection) {
        check(preferences.edit().putBoolean(providerKey(connection), true).commit())
    }
    fun revoke(connectionUid: String) {
        val prefix = "$connectionUid:"
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        check(editor.commit())
    }
    private fun providerKey(connection: ToolConnection): String {
        val identity = "${connection.endpointUrl}|${connection.authType}|${connection.oauthClientId}|*"
        return "${connection.connectionUid}:" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private fun key(connection: ToolConnection, tool: String): String {
        val identity = "${connection.endpointUrl}|${connection.authType}|${connection.oauthClientId}|$tool"
        return "${connection.connectionUid}:" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
