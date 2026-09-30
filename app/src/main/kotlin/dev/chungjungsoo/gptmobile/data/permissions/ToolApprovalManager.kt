package dev.chungjungsoo.gptmobile.data.permissions

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

enum class ToolPolicy { READ_ONLY, ASK_WRITES, TRUSTED }

@Entity(tableName = "tool_approvals", indices = [Index("runId"), Index("state")])
data class ToolApproval(
    @PrimaryKey val id: String,
    val runId: String,
    val connection: String,
    val tool: String,
    val argumentHash: String,
    val argumentPreview: String = "",
    val state: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ToolApprovalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(value: ToolApproval): Long

    @Query("SELECT * FROM tool_approvals WHERE state = 'PENDING' ORDER BY createdAt")
    fun pending(): Flow<List<ToolApproval>>

    @Query("SELECT * FROM tool_approvals WHERE id = :id")
    fun observe(id: String): Flow<ToolApproval?>

    @Query("UPDATE tool_approvals SET state = :state WHERE id = :id AND state = 'PENDING'")
    suspend fun decide(id: String, state: String)

    @Query("UPDATE tool_approvals SET state = 'EXECUTING' WHERE id = :id AND state = 'APPROVED'")
    suspend fun claim(id: String): Int

    @Query("SELECT a.state FROM tool_approvals a JOIN agent_runs previous ON previous.run_id = a.runId JOIN agent_runs target_run ON target_run.run_id = :runId WHERE previous.chat_id = target_run.chat_id AND previous.user_message_id = target_run.user_message_id AND a.connection = :connection AND a.tool = :tool AND a.argumentHash = :hash AND a.state IN ('PENDING', 'APPROVED', 'EXECUTING', 'COMPLETED', 'OUTCOME_UNKNOWN', 'INTERRUPTED') LIMIT 1")
    suspend fun previousMatchingAction(runId: String, connection: String, tool: String, hash: String): String?

    @Query("UPDATE tool_approvals SET state = :state WHERE id = :id AND state = 'EXECUTING'")
    suspend fun finish(id: String, state: String)

    @Query("UPDATE tool_approvals SET state = 'INTERRUPTED' WHERE state IN ('PENDING', 'APPROVED', 'EXECUTING')")
    suspend fun interrupt()

    @Query("DELETE FROM tool_approvals WHERE state NOT IN ('PENDING', 'APPROVED', 'EXECUTING') AND createdAt < :cutoff AND runId NOT IN (SELECT run_id FROM agent_runs)")
    suspend fun prune(cutoff: Long)
}

/** User-owned permissions. Server readOnly annotations never grant authority. */
@Singleton
class ToolApprovalManager @Inject constructor(database: ChatDatabaseV2, private val connections: ToolConnectionRepository, private val trust: ToolTrustStore? = null) {
    private val dao = database.toolApprovalDao()
    private val submissionMutex = Mutex()
    private val requestConnections = java.util.concurrent.ConcurrentHashMap<String, String>()
    val pending = dao.pending()
    suspend fun recover() {
        dao.interrupt()
        dao.prune(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
    }
    suspend fun decide(id: String, allow: Boolean) = dao.decide(id, if (allow) "APPROVED" else "DENIED")
    suspend fun alwaysAllowProvider(id: String) {
        val request = dao.observe(id).first() ?: return
        if (request.state != "PENDING") return
        val connection = requestConnections[id]?.let { connections.getConnection(it) } ?: return
        requireNotNull(trust) { "Persistent tool permissions are unavailable." }.allowProvider(connection)
        dao.decide(id, "APPROVED")
    }
    suspend fun alwaysAllow(id: String) {
        val request = dao.observe(id).first() ?: return
        if (request.state != "PENDING") return
        val connection = requestConnections[id]?.let { connections.getConnection(it) } ?: return
        requireNotNull(trust) { "Persistent tool permissions are unavailable." }.allow(connection, request.tool)
        dao.decide(id, "APPROVED")
    }
    suspend fun finish(runId: String, callId: String, success: Boolean) = dao.finish("$runId:$callId", if (success) "COMPLETED" else "OUTCOME_UNKNOWN")
    suspend fun authorize(connectionId: String, runId: String, callId: String, tool: String, arguments: JsonObject): Boolean {
        val connection = connections.getConnection(connectionId) ?: return false
        if (connection.type != "MCP" && tool != "github") return true
        val policy = runCatching { ToolPolicy.valueOf(connection.toolPolicy) }.getOrDefault(ToolPolicy.ASK_WRITES)
        val githubRead = tool != "github" ||
            arguments["action"]?.let {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content in setOf(
                    "search_repositories", "search_issues", "search_code", "get_file_contents", "get_issue", "get_pull_request", "list_pull_requests", "list_workflow_runs", "list_workflows", "get_workflow_run", "list_workflow_jobs", "list_workflow_artifacts", "get_job_logs"
                )
            } == true
        val readOnly = githubRead && tool in connection.approvedReadTools.lines().map(String::trim)
        if (policy == ToolPolicy.READ_ONLY && !readOnly) return false
        if (readOnly) return true
        val id = "$runId:$callId"
        requestConnections[id] = connectionId
        val hash = MessageDigest.getInstance("SHA-256").digest(arguments.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val inserted = submissionMutex.withLock {
            val previous = dao.previousMatchingAction(runId, connection.name, tool, hash)
            val request = ToolApproval(
                id,
                runId,
                connection.name,
                tool,
                hash,
                argumentPreview = (previous?.let { "A matching action in this turn has status $it. Check its effect before approving a repeat.\n" }.orEmpty()) +
                    dev.chungjungsoo.gptmobile.data.security.DiagnosticRedactor.arguments(arguments),
                state = if ((policy == ToolPolicy.TRUSTED || trust?.allows(connection, tool) == true) && previous == null) "APPROVED" else "PENDING"
            )
            dao.insert(request)
        }
        if (inserted == -1L) {
            requestConnections.remove(id)
            return false
        }
        return try {
            val decision = dao.observe(id).first { it == null || it.state != "PENDING" }
            decision?.state == "APPROVED" && dao.claim(id) == 1
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { dao.decide(id, "CANCELED") }
            throw cancelled
        } finally {
            requestConnections.remove(id)
        }
    }
}
