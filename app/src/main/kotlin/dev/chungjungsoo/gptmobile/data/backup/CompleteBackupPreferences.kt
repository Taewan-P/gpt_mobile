package dev.chungjungsoo.gptmobile.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.File
import java.util.Base64
import kotlinx.coroutines.flow.first

internal class CompleteBackupPreferences(private val context: Context, private val dataStore: DataStore<Preferences>) {
    suspend fun read() = dataStore.data.first().asMap().map { (key, value) -> key.name to encode(value) }.toMap()
    fun readShared() = sharedNames().associateWith { name ->
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapValues { (_, value) -> encode(requireNotNull(value)) }
    }

    suspend fun replace(values: Map<String, BackupValue>, shared: Map<String, Map<String, BackupValue>>) {
        validate(values, shared)
        dataStore.edit { prefs ->
            prefs.clear()
            values.forEach { (name, value) ->
                when (val decoded = decode(value)) {
                    is Boolean -> prefs[booleanPreferencesKey(name)] = decoded
                    is Int -> prefs[intPreferencesKey(name)] = decoded
                    is Long -> prefs[longPreferencesKey(name)] = decoded
                    is Float -> prefs[floatPreferencesKey(name)] = decoded
                    is Double -> prefs[doublePreferencesKey(name)] = decoded
                    is String -> prefs[stringPreferencesKey(name)] = decoded
                    is ByteArray -> prefs[byteArrayPreferencesKey(name)] = decoded
                    is Set<*> -> prefs[stringSetPreferencesKey(name)] = decoded.filterIsInstance<String>().toSet()
                }
            }
        }
        (sharedNames() + shared.keys).filterNot { it == "complete_backup_ui_v1" }.forEach { name ->
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            shared[name].orEmpty().forEach { (key, value) ->
                when (val decoded = decode(value)) {
                    is Boolean -> editor.putBoolean(key, decoded)
                    is Int -> editor.putInt(key, decoded)
                    is Long -> editor.putLong(key, decoded)
                    is Float -> editor.putFloat(key, decoded)
                    is String -> editor.putString(key, decoded)
                    is Set<*> -> editor.putStringSet(key, decoded.filterIsInstance<String>().toSet())
                }
            }
            check(editor.commit()) { "Could not save restored settings." }
        }
    }

    fun validate(values: Map<String, BackupValue>, shared: Map<String, Map<String, BackupValue>>) {
        values.values.forEach(::decode)
        shared.forEach { (name, entries) ->
            require(name.isNotBlank() && name !in setOf(".", "..") && name.none { it == '/' || it == '\\' || it == '\u0000' }) { "Invalid preferences name." }
            entries.values.forEach {
                require(it.type !in setOf("double", "bytes"))
                decode(it)
            }
        }
    }

    private fun sharedNames(): Set<String> = File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
        .filter { it.name.endsWith(".xml") }.map { it.name.removeSuffix(".xml") }.toSet() + "llama_settings"

    private fun encode(value: Any): BackupValue = when (value) {
        is Boolean -> BackupValue("boolean", value.toString())
        is Int -> BackupValue("int", value.toString())
        is Long -> BackupValue("long", value.toString())
        is Float -> BackupValue("float", value.toString())
        is Double -> BackupValue("double", value.toString())
        is String -> BackupValue("string", value)
        is ByteArray -> BackupValue("bytes", Base64.getEncoder().encodeToString(value))
        is Set<*> -> {
            require(value.all { it is String })
            BackupValue("strings", values = value.filterIsInstance<String>().toSet())
        }
        else -> error("Unsupported preference type.")
    }

    private fun decode(value: BackupValue): Any = when (value.type) {
        "boolean" -> value.value.toBooleanStrict()
        "int" -> value.value.toInt()
        "long" -> value.value.toLong()
        "float" -> value.value.toFloat()
        "double" -> value.value.toDouble()
        "string" -> value.value
        "bytes" -> Base64.getDecoder().decode(value.value)
        "strings" -> value.values
        else -> error("Unsupported backup preference type.")
    }
}
