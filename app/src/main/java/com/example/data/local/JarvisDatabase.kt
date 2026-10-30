package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String, // "user", "model", "system"
    val content: String,
    val attachmentName: String? = null,
    val attachmentType: String? = null,
    val providerUsed: String = "Gemini 3.8 Flash",
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // "PREFERENCE", "FACT", "ROUTINE", "TASK_CONTEXT"
    val keyTitle: String,
    val content: String,
    val isApproved: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerAtMillis: Long,
    val type: String, // "REMINDER", "TIMER", "ALARM"
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_plans")
data class TaskPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goal: String,
    val summary: String,
    val stepsJson: String,
    val status: String, // "READY", "RUNNING", "PAUSED", "COMPLETED", "FAILED", "CANCELLED"
    val currentStepIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Dynamic MCP Server Configuration Entity (Unlimited Servers supported).
 * Secrets/Tokens are stored separately in Android Keystore via SecureVault.setMcpServerSecret(id, secret).
 */
@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val endpointUrl: String,
    val transportType: String = "STREAMABLE_HTTP", // "STREAMABLE_HTTP", "SSE", "REMOTE_STDIO_GATEWAY"
    val authType: String = "NONE", // "NONE", "BEARER_TOKEN", "API_KEY", "OAUTH_TOKEN", "CUSTOM_HEADER"
    val customHeaderName: String = "X-API-Key",
    val customAuthHeaderName: String = "X-API-Key",
    val timeoutSeconds: Int = 20,
    val isEnabled: Boolean = true,
    val requireConfirmationForWrites: Boolean = true,
    val connectionStatus: String = "DISCONNECTED", // "DISCONNECTED", "CONNECTED", "TESTING", "ERROR", "DISABLED"
    val statusMessage: String = "Ready",
    val lastToolCount: Int = 0,
    val protocolVersion: String = "2025-03-26",
    val serverCapabilities: String = "",
    val lastError: String? = null,
    val lastConnectedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Discovered MCP Tool Entity linked to a parent McpServerEntity.
 */
@Entity(tableName = "mcp_tools")
data class McpToolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val serverName: String,
    val toolName: String,
    val description: String,
    val inputSchemaJson: String,
    val safetyClassification: String = "READ_ONLY", // "READ_ONLY", "WRITE", "DESTRUCTIVE"
    val isApproved: Boolean = true,
    val hasPromptInjectionWarning: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * MCP Security & Execution Audit Log Entity.
 */
@Entity(tableName = "mcp_audit_logs")
data class McpAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val serverName: String,
    val toolName: String,
    val actionType: String = "EXECUTE_TOOL",
    val argumentsSummary: String = "",
    val status: String, // "SUCCESS", "CONFIRMED", "BLOCKED", "ERROR"
    val details: String = "",
    val latencyMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    val argumentsPreview: String get() = argumentsSummary
    val resultSummary: String get() = details
    val durationMs: Long get() = latencyMs
}

@Dao
interface JarvisDao {
    // Chat Sessions
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)

    // Chat Messages
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    // Memories
    @Query("SELECT * FROM memories ORDER BY isApproved ASC, updatedAt DESC")
    fun observeMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE isApproved = 1 ORDER BY updatedAt DESC")
    suspend fun getApprovedMemories(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()

    // Reminders & Timers
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, triggerAtMillis ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    // Multi-Step Task Plans
    @Query("SELECT * FROM task_plans ORDER BY createdAt DESC")
    fun observeTaskPlans(): Flow<List<TaskPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskPlan(plan: TaskPlanEntity): Long

    @Update
    suspend fun updateTaskPlan(plan: TaskPlanEntity)

    @Query("DELETE FROM task_plans WHERE id = :id")
    suspend fun deleteTaskPlan(id: Long)

    // MCP Servers (Unlimited Dynamic Registration)
    @Query("SELECT * FROM mcp_servers ORDER BY createdAt DESC")
    fun observeMcpServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers ORDER BY createdAt DESC")
    suspend fun getAllMcpServers(): List<McpServerEntity>

    @Query("SELECT * FROM mcp_servers WHERE id = :id LIMIT 1")
    suspend fun getMcpServerById(id: Long): McpServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMcpServer(server: McpServerEntity): Long

    @Update
    suspend fun updateMcpServer(server: McpServerEntity)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteMcpServer(id: Long)

    @Query("UPDATE mcp_servers SET isEnabled = 0, connectionStatus = 'DISCONNECTED', statusMessage = 'Emergency disconnected by user'")
    suspend fun emergencyDisconnectAllMcpServers()

    // MCP Tools
    @Query("SELECT * FROM mcp_tools ORDER BY serverName ASC, toolName ASC")
    fun observeMcpTools(): Flow<List<McpToolEntity>>

    @Query("SELECT * FROM mcp_tools WHERE isApproved = 1 ORDER BY serverName ASC, toolName ASC")
    suspend fun getApprovedMcpTools(): List<McpToolEntity>

    @Query("SELECT * FROM mcp_tools WHERE serverId = :serverId ORDER BY toolName ASC")
    suspend fun getToolsForServer(serverId: Long): List<McpToolEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMcpTools(tools: List<McpToolEntity>)

    @Update
    suspend fun updateMcpTool(tool: McpToolEntity)

    @Query("DELETE FROM mcp_tools WHERE serverId = :serverId")
    suspend fun deleteToolsForServer(serverId: Long)

    @Query("DELETE FROM mcp_tools WHERE serverId = :serverId")
    suspend fun deleteMcpToolsForServer(serverId: Long)

    // MCP Security Audit Logs
    @Query("SELECT * FROM mcp_audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeMcpAuditLogs(): Flow<List<McpAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMcpAuditLog(log: McpAuditLogEntity): Long

    @Query("DELETE FROM mcp_audit_logs")
    suspend fun clearMcpAuditLogs()

    // Purge Everything
    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM chat_sessions")
    suspend fun clearAllSessions()

    @Query("DELETE FROM reminders")
    suspend fun clearAllReminders()

    @Query("DELETE FROM task_plans")
    suspend fun clearAllTaskPlans()

    @Query("DELETE FROM mcp_tools")
    suspend fun clearAllMcpTools()

    @Query("DELETE FROM mcp_servers")
    suspend fun clearAllMcpServers()
}

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        MemoryEntity::class,
        ReminderEntity::class,
        TaskPlanEntity::class,
        McpServerEntity::class,
        McpToolEntity::class,
        McpAuditLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun jarvisDao(): JarvisDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_assistant.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
