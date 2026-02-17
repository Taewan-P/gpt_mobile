package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@Entity(tableName = "mcp_servers")
data class McpServerConfig(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("id")
    val id: Int = 0,

    @ColumnInfo("name")
    val name: String,

    @ColumnInfo("type")
    val type: McpTransportType,

    @ColumnInfo("url")
    val url: String? = null,

    @ColumnInfo("command")
    val command: String? = null,

    @ColumnInfo("args")
    val args: List<String> = listOf(),

    @ColumnInfo("env")
    val env: Map<String, String> = mapOf(),

    @ColumnInfo("headers")
    val headers: Map<String, String> = mapOf(),

    @ColumnInfo("enabled")
    val enabled: Boolean = true,

    @ColumnInfo("allowed_tools")
    val allowedTools: List<String>? = null,

    @ColumnInfo(name = "max_tool_call_iterations", defaultValue = "20")
    val maxToolCallIterations: Int = DEFAULT_MAX_TOOL_CALL_ITERATIONS
)

enum class McpTransportType {
    STDIO,
    SSE,
    STREAMABLE_HTTP,
    WEBSOCKET
}

class McpTransportTypeConverter {
    @TypeConverter
    fun fromTransportType(value: McpTransportType): String = value.name

    @TypeConverter
    fun toTransportType(value: String): McpTransportType = McpTransportType.valueOf(value)
}

class StringMapConverter {
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    @TypeConverter
    fun fromMap(value: Map<String, String>): String = Json.encodeToString(mapSerializer, value)

    @TypeConverter
    fun toMap(value: String): Map<String, String> =
        if (value.isBlank()) {
            emptyMap()
        } else {
            Json.decodeFromString(mapSerializer, value)
        }
}

class NullableStringListConverter {
    private val listSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromList(value: List<String>?): String? = value?.let { Json.encodeToString(listSerializer, it) }

    @TypeConverter
    fun toList(value: String?): List<String>? = value?.let {
        if (it.isBlank()) {
            emptyList()
        } else {
            Json.decodeFromString(listSerializer, it)
        }
    }
}

const val DEFAULT_MAX_TOOL_CALL_ITERATIONS = 20
