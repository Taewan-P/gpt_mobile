package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tool_connections",
    indices = [Index(value = ["alias"], unique = true)]
)
data class ToolConnection(
    @PrimaryKey
    @ColumnInfo(name = "connection_uid")
    val connectionUid: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "alias")
    val alias: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "endpoint_url")
    val endpointUrl: String?,

    @ColumnInfo(name = "auth_type")
    val authType: String,

    @ColumnInfo(name = "secret_ref")
    val secretRef: String?,

    @ColumnInfo(name = "oauth_client_id")
    val oauthClientId: String?,

    @ColumnInfo(name = "allow_cleartext")
    val allowCleartext: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

object ToolConnectionType {
    const val MCP = "MCP"
    const val FIRECRAWL = "FIRECRAWL"
    const val PERPLEXITY = "PERPLEXITY"
    const val EXA = "EXA"
}

object ToolConnectionAuthType {
    const val NONE = "NONE"
    const val BEARER = "BEARER"
    const val API_KEY = "API_KEY"
    const val OAUTH = "OAUTH"
}
