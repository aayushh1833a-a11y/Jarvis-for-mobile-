package com.example.mcp

import android.util.Log
import com.example.data.local.JarvisDao
import com.example.data.local.McpAuditLogEntity
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.security.SecureVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class McpTransportType(val displayName: String) {
    STREAMABLE_HTTP("Streamable HTTP (MCP 2025-03-26)"),
    SSE("HTTP + SSE (Server-Sent Events)"),
    REMOTE_STDIO_GATEWAY("Remote STDIO Gateway Bridge (HTTP)")
}

enum class McpAuthType(val displayName: String) {
    NONE("No Auth"),
    BEARER_TOKEN("Bearer Token"),
    API_KEY("API Key Header"),
    OAUTH_TOKEN("OAuth 2.0 Access Token"),
    CUSTOM_HEADER("Custom Header")
}

object McpSafetyClassifier {
    private val DESTRUCTIVE_KEYWORDS = listOf(
        "delete", "remove", "drop", "destroy", "purge", "wipe", "format", "revoke", "kill", "terminate", "overwrite"
    )
    private val WRITE_KEYWORDS = listOf(
        "create", "update", "write", "put", "post", "patch", "send", "upload", "insert", "modify", "rename", "move", "add", "set", "execute", "run"
    )
    private val PROMPT_INJECTION_PATTERNS = listOf(
        Regex("ignore\\s+(all\\s+)?(previous|prior)\\s+instructions", RegexOption.IGNORE_CASE),
        Regex("disregard\\s+(all\\s+)?system\\s+prompts?", RegexOption.IGNORE_CASE),
        Regex("you\\s+are\\s+now\\s+in\\s+developer\\s+mode", RegexOption.IGNORE_CASE),
        Regex("<\\s*system\\s*>", RegexOption.IGNORE_CASE),
        Regex("exfiltrate|send\\s+api\\s+key|reveal\\s+token", RegexOption.IGNORE_CASE)
    )

    fun classifyTool(toolName: String, description: String): String {
        val combined = "$toolName $description".lowercase()
        if (DESTRUCTIVE_KEYWORDS.any { combined.contains(it) }) {
            return "DESTRUCTIVE"
        }
        if (WRITE_KEYWORDS.any { combined.contains(it) }) {
            return "WRITE"
        }
        return "READ_ONLY"
    }

    fun detectPromptInjection(text: String): Boolean {
        return PROMPT_INJECTION_PATTERNS.any { it.containsMatchIn(text) }
    }

    fun sanitizeExternalText(rawText: String): String {
        var sanitized = rawText
        for (pattern in PROMPT_INJECTION_PATTERNS) {
            if (pattern.containsMatchIn(sanitized)) {
                sanitized = pattern.replace(sanitized, "[REDACTED-INJECTION-ATTEMPT]")
            }
        }
        return sanitized
    }
}

data class McpDiscoveryOutcome(
    val success: Boolean,
    val message: String,
    val server: McpServerEntity? = null,
    val tools: List<McpToolEntity> = emptyList()
)

data class McpToolCallResult(
    val success: Boolean,
    val outputText: String,
    val durationMs: Long,
    val hadPromptInjectionAttempt: Boolean = false
)

/**
 * Modular Model Context Protocol (MCP) Client Engine supporting:
 * - Official MCP Specification 2025-03-26 & 2024-11-05 JSON-RPC 2.0
 * - Streamable HTTP transport (with Mcp-Session-Id session tracking)
 * - HTTP+SSE (Server-Sent Events) response stream parsing
 * - Remote STDIO Gateway HTTP bridges
 * - Dynamic Tool Discovery (initialize -> notifications/initialized -> tools/list)
 * - Per-server concurrency limits, request timeouts, and Android Keystore encrypted credentials
 * - Read-Only / Write / Destructive tool classification & Prompt-Injection sanitization
 */
class McpClientEngine(
    private val dao: JarvisDao,
    private val secureVault: SecureVault
) {
    companion object {
        private const val TAG = "McpClientEngine"
        const val SUPPORTED_MCP_SPEC_VERSION = "2025-03-26 (compatible with 2024-11-05)"
    }

    private val requestIdCounter = AtomicInteger(1)
    private val sessionIdsByServer = ConcurrentHashMap<Long, String>()
    private val serverSemaphores = ConcurrentHashMap<Long, Semaphore>()

    private val baseHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private fun getSemaphoreForServer(serverId: Long): Semaphore {
        return serverSemaphores.computeIfAbsent(serverId) { Semaphore(2) }
    }

    fun validateServerConfig(
        name: String,
        endpointUrl: String,
        transport: McpTransportType = McpTransportType.STREAMABLE_HTTP
    ): String? {
        if (name.isBlank()) {
            return "MCP Server name cannot be empty."
        }
        val url = endpointUrl.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return "Endpoint URL must start with https:// (or http:// for a local network/STDIO gateway bridge). Note: Android sandboxes apps and cannot spawn local desktop STDIO subprocesses directly without an HTTP/SSE gateway."
        }
        return null
    }

    /**
     * Connects to an MCP server via JSON-RPC 2.0 (`initialize` -> `notifications/initialized` -> `tools/list`),
     * discovers all tools & JSON schemas, classifies safety, and persists them in Room.
     */
    suspend fun connectAndDiscoverTools(server: McpServerEntity): McpDiscoveryOutcome = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val semaphore = getSemaphoreForServer(server.id)

        semaphore.withPermit {
            try {
                dao.updateMcpServer(
                    server.copy(
                        connectionStatus = "TESTING",
                        statusMessage = "Performing MCP JSON-RPC 2.0 initialize handshake...",
                        lastError = null
                    )
                )

                val initParams = JSONObject()
                    .put("protocolVersion", "2025-03-26")
                    .put("capabilities", JSONObject().put("roots", JSONObject().put("listChanged", false)))
                    .put(
                        "clientInfo",
                        JSONObject()
                            .put("name", "JARVIS-Android-MCP-Client")
                            .put("version", "1.0.0")
                    )

                val initResp = sendJsonRpcRequest(
                    server = server,
                    method = "initialize",
                    params = initParams
                )

                val resultObj = initResp.optJSONObject("result") ?: JSONObject()
                val negotiatedVersion = resultObj.optString("protocolVersion", "2025-03-26")
                val serverInfoObj = resultObj.optJSONObject("serverInfo")
                val serverImplName = serverInfoObj?.optString("name", server.name) ?: server.name
                val serverImplVer = serverInfoObj?.optString("version", "1.0") ?: "1.0"
                val capabilitiesSummary = resultObj.optJSONObject("capabilities")?.names()?.let { names ->
                    (0 until names.length()).map { names.optString(it) }.joinToString(", ")
                }.orEmpty().ifBlank { "tools" }

                try {
                    sendJsonRpcNotification(server = server, method = "notifications/initialized")
                } catch (_: Exception) {}

                val toolsResp = sendJsonRpcRequest(
                    server = server,
                    method = "tools/list",
                    params = JSONObject()
                )

                val toolsResult = toolsResp.optJSONObject("result") ?: JSONObject()
                val toolsArray = toolsResult.optJSONArray("tools") ?: JSONArray()

                val existingTools = dao.getToolsForServer(server.id).associateBy { it.toolName }
                val discoveredEntities = mutableListOf<McpToolEntity>()

                for (i in 0 until toolsArray.length()) {
                    val tObj = toolsArray.optJSONObject(i) ?: continue
                    val rawName = tObj.optString("name").trim()
                    if (rawName.isBlank()) continue
                    val rawDesc = tObj.optString("description", "No description provided.")
                    val hasRisk = McpSafetyClassifier.detectPromptInjection(rawDesc)
                    val cleanDesc = McpSafetyClassifier.sanitizeExternalText(rawDesc)
                    val inputSchema = tObj.optJSONObject("inputSchema")?.toString() ?: "{\"type\":\"object\"}"
                    val safety = McpSafetyClassifier.classifyTool(rawName, cleanDesc)
                    val prevApproved = existingTools[rawName]?.isApproved ?: !hasRisk

                    discoveredEntities.add(
                        McpToolEntity(
                            serverId = server.id,
                            serverName = server.name,
                            toolName = rawName,
                            description = cleanDesc,
                            inputSchemaJson = inputSchema,
                            safetyClassification = safety,
                            isApproved = prevApproved,
                            hasPromptInjectionWarning = hasRisk
                        )
                    )
                }

                dao.deleteToolsForServer(server.id)
                if (discoveredEntities.isNotEmpty()) {
                    dao.insertMcpTools(discoveredEntities)
                }

                val summaryMsg = "Connected to $serverImplName v$serverImplVer (MCP $negotiatedVersion). Discovered ${discoveredEntities.size} tool(s)."
                val updatedServer = server.copy(
                    connectionStatus = "CONNECTED",
                    statusMessage = summaryMsg,
                    lastToolCount = discoveredEntities.size,
                    protocolVersion = negotiatedVersion,
                    serverCapabilities = capabilitiesSummary,
                    lastError = null,
                    lastConnectedAt = System.currentTimeMillis()
                )
                dao.updateMcpServer(updatedServer)

                val latency = System.currentTimeMillis() - startMs
                dao.insertMcpAuditLog(
                    McpAuditLogEntity(
                        serverId = server.id,
                        serverName = server.name,
                        toolName = "initialize + tools/list",
                        actionType = "DISCOVER_TOOLS",
                        argumentsSummary = server.endpointUrl,
                        status = "SUCCESS",
                        details = summaryMsg,
                        latencyMs = latency
                    )
                )

                McpDiscoveryOutcome(
                    success = true,
                    message = summaryMsg,
                    server = updatedServer,
                    tools = discoveredEntities
                )
            } catch (e: Exception) {
                Log.e(TAG, "MCP connect/discovery failed for ${server.name}", e)
                val latency = System.currentTimeMillis() - startMs
                val errMsg = e.localizedMessage ?: "MCP handshake failed"
                dao.updateMcpServer(
                    server.copy(
                        connectionStatus = "ERROR",
                        statusMessage = "Connection failed: $errMsg",
                        lastError = errMsg
                    )
                )
                dao.insertMcpAuditLog(
                    McpAuditLogEntity(
                        serverId = server.id,
                        serverName = server.name,
                        toolName = "initialize",
                        actionType = "CONNECT",
                        argumentsSummary = server.endpointUrl,
                        status = "ERROR",
                        details = errMsg,
                        latencyMs = latency
                    )
                )
                McpDiscoveryOutcome(
                    success = false,
                    message = "Failed to connect to '${server.name}': $errMsg"
                )
            }
        }
    }

    /**
     * Executes an MCP tool via JSON-RPC 2.0 `tools/call` with per-server concurrency control,
     * timeout enforcement, prompt-injection output sanitization, and full audit logging.
     */
    suspend fun callTool(
        server: McpServerEntity,
        tool: McpToolEntity,
        arguments: JSONObject
    ): McpToolCallResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        if (!server.isEnabled) {
            return@withContext McpToolCallResult(
                success = false,
                outputText = "MCP Server '${server.name}' is disabled.",
                durationMs = 0L
            )
        }
        if (!tool.isApproved) {
            return@withContext McpToolCallResult(
                success = false,
                outputText = "MCP Tool '${tool.toolName}' is not approved for execution.",
                durationMs = 0L
            )
        }

        val semaphore = getSemaphoreForServer(server.id)
        semaphore.withPermit {
            try {
                val callParams = JSONObject()
                    .put("name", tool.toolName)
                    .put("arguments", arguments)

                val rpcResponse = sendJsonRpcRequest(
                    server = server,
                    method = "tools/call",
                    params = callParams
                )

                val resultObj = rpcResponse.optJSONObject("result") ?: JSONObject()
                val isToolError = resultObj.optBoolean("isError", false)
                val contentArray = resultObj.optJSONArray("content") ?: JSONArray()

                val rawOutputBuilder = StringBuilder()
                for (i in 0 until contentArray.length()) {
                    val part = contentArray.optJSONObject(i) ?: continue
                    when (part.optString("type")) {
                        "text" -> rawOutputBuilder.append(part.optString("text")).append("\n")
                        "resource" -> {
                            val resObj = part.optJSONObject("resource")
                            rawOutputBuilder.append("[Resource ${resObj?.optString("uri")}]: ")
                                .append(resObj?.optString("text").orEmpty())
                                .append("\n")
                        }
                        else -> rawOutputBuilder.append(part.toString()).append("\n")
                    }
                }
                if (rawOutputBuilder.isEmpty()) {
                    rawOutputBuilder.append(resultObj.toString())
                }

                val rawText = rawOutputBuilder.toString().trim()
                val hadInjection = McpSafetyClassifier.detectPromptInjection(rawText)
                val sanitizedOutput = McpSafetyClassifier.sanitizeExternalText(rawText)
                val latency = System.currentTimeMillis() - startMs

                dao.insertMcpAuditLog(
                    McpAuditLogEntity(
                        serverId = server.id,
                        serverName = server.name,
                        toolName = tool.toolName,
                        actionType = if (hadInjection) "PROMPT_INJECTION_BLOCKED" else "EXECUTE_TOOL",
                        argumentsSummary = arguments.toString().take(180),
                        status = if (isToolError) "ERROR" else "SUCCESS",
                        details = sanitizedOutput.take(240),
                        latencyMs = latency
                    )
                )

                McpToolCallResult(
                    success = !isToolError,
                    outputText = sanitizedOutput,
                    durationMs = latency,
                    hadPromptInjectionAttempt = hadInjection
                )
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startMs
                val errMsg = e.localizedMessage ?: "Tool execution failed"
                dao.insertMcpAuditLog(
                    McpAuditLogEntity(
                        serverId = server.id,
                        serverName = server.name,
                        toolName = tool.toolName,
                        actionType = "EXECUTE_TOOL",
                        argumentsSummary = arguments.toString().take(180),
                        status = "ERROR",
                        details = errMsg,
                        latencyMs = latency
                    )
                )
                McpToolCallResult(
                    success = false,
                    outputText = errMsg,
                    durationMs = latency
                )
            }
        }
    }

    suspend fun emergencyDisconnectAll() = withContext(Dispatchers.IO) {
        sessionIdsByServer.clear()
        dao.emergencyDisconnectAllMcpServers()
        dao.insertMcpAuditLog(
            McpAuditLogEntity(
                serverId = 0L,
                serverName = "ALL_SERVERS",
                toolName = "emergency_disconnect",
                actionType = "EMERGENCY_DISCONNECT",
                argumentsSummary = "All MCP servers",
                status = "SUCCESS",
                details = "Emergency disconnect triggered by user; all MCP servers disabled.",
                latencyMs = 0L
            )
        )
    }

    private fun sendJsonRpcRequest(
        server: McpServerEntity,
        method: String,
        params: JSONObject
    ): JSONObject {
        val reqId = requestIdCounter.getAndIncrement()
        val rpcPayload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", reqId)
            .put("method", method)
            .put("params", params)

        val timeoutSec = server.timeoutSeconds.coerceIn(5, 120).toLong()
        val client = baseHttpClient.newBuilder()
            .connectTimeout(timeoutSec, TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .build()

        val reqBuilder = Request.Builder()
            .url(server.endpointUrl.trim())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")

        sessionIdsByServer[server.id]?.let { sessionId ->
            reqBuilder.header("Mcp-Session-Id", sessionId)
        }

        applyAuthHeaders(server, reqBuilder)

        val request = reqBuilder
            .post(rpcPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            response.header("Mcp-Session-Id")?.takeIf { it.isNotBlank() }?.let { newSessionId ->
                sessionIdsByServer[server.id] = newSessionId
            }

            if (response.code == 429) {
                throw IllegalStateException("MCP Server rate limit exceeded (HTTP 429). Please wait before retrying.")
            }
            if (response.code == 401 || response.code == 403) {
                throw IllegalStateException("MCP Server authentication rejected (HTTP ${response.code}). Check your API Key or OAuth Bearer token.")
            }
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(200).orEmpty()
                throw IllegalStateException("MCP HTTP ${response.code}: $errBody")
            }

            val contentType = response.header("Content-Type").orEmpty()
            val bodyString = if (contentType.contains("text/event-stream", ignoreCase = true)) {
                extractJsonFromSseStream(response)
            } else {
                response.body?.string().orEmpty()
            }

            if (bodyString.isBlank()) {
                throw IllegalStateException("MCP Server returned an empty response body.")
            }

            val json = JSONObject(bodyString)
            if (json.has("error") && !json.isNull("error")) {
                val errObj = json.optJSONObject("error")
                val msg = errObj?.optString("message") ?: "JSON-RPC Error"
                val code = errObj?.optInt("code", -1) ?: -1
                throw IllegalStateException("MCP RPC Error ($code): $msg")
            }
            return json
        }
    }

    private fun sendJsonRpcNotification(server: McpServerEntity, method: String) {
        val rpcPayload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", method)

        val reqBuilder = Request.Builder()
            .url(server.endpointUrl.trim())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")

        sessionIdsByServer[server.id]?.let { sessionId ->
            reqBuilder.header("Mcp-Session-Id", sessionId)
        }
        applyAuthHeaders(server, reqBuilder)

        val request = reqBuilder
            .post(rpcPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        baseHttpClient.newCall(request).execute().close()
    }

    private fun applyAuthHeaders(server: McpServerEntity, reqBuilder: Request.Builder) {
        val secret = secureVault.getMcpServerSecret(server.id)
        when (server.authType) {
            "BEARER_TOKEN", "OAUTH_TOKEN" -> {
                val tokenToUse = secret.ifBlank {
                    if (server.authType == "OAUTH_TOKEN") secureVault.googleOAuthAccessToken else ""
                }
                if (tokenToUse.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${tokenToUse.removePrefix("Bearer ").trim()}")
                }
            }
            "API_KEY" -> {
                if (secret.isNotBlank()) {
                    reqBuilder.header("X-API-Key", secret)
                    reqBuilder.header("Authorization", "Bearer $secret")
                }
            }
            "CUSTOM_HEADER" -> {
                val headerName = server.customAuthHeaderName.ifBlank { server.customHeaderName }.trim().ifBlank { "X-Custom-Auth" }
                if (secret.isNotBlank()) {
                    reqBuilder.header(headerName, secret)
                }
            }
        }
    }

    private fun extractJsonFromSseStream(response: okhttp3.Response): String {
        val stream = response.body?.byteStream() ?: return ""
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            var line: String?
            var lastDataJson = ""
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim().orEmpty()
                if (trimmed.startsWith("data:")) {
                    val dataPayload = trimmed.removePrefix("data:").trim()
                    if (dataPayload.startsWith("{") && dataPayload.endsWith("}")) {
                        lastDataJson = dataPayload
                        break
                    }
                }
            }
            return lastDataJson
        }
    }

    suspend fun exportServersJson(): String = withContext(Dispatchers.IO) {
        val servers = dao.getAllMcpServers()
        val arr = JSONArray()
        for (srv in servers) {
            arr.put(
                JSONObject()
                    .put("name", srv.name)
                    .put("endpointUrl", srv.endpointUrl)
                    .put("transportType", srv.transportType)
                    .put("authType", srv.authType)
                    .put("customAuthHeaderName", srv.customAuthHeaderName)
                    .put("timeoutSeconds", srv.timeoutSeconds)
                    .put("isEnabled", srv.isEnabled)
                    .put("requireConfirmationForWrites", srv.requireConfirmationForWrites)
                    .put("credentialsRedacted", true)
            )
        }
        JSONObject()
            .put("mcpSpecVersion", SUPPORTED_MCP_SPEC_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("servers", arr)
            .toString(2)
    }

    suspend fun importServersJson(rawJson: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val trimmed = rawJson.trim()
            val serversArr = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                JSONObject(trimmed).optJSONArray("servers")
                    ?: return@withContext Result.failure(IllegalArgumentException("JSON must contain a 'servers' array."))
            }

            var importedCount = 0
            for (i in 0 until serversArr.length()) {
                val obj = serversArr.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                val url = obj.optString("endpointUrl").trim()
                if (name.isNotBlank() && (url.startsWith("https://") || url.startsWith("http://"))) {
                    dao.insertMcpServer(
                        McpServerEntity(
                            name = name,
                            endpointUrl = url,
                            transportType = obj.optString("transportType", "STREAMABLE_HTTP"),
                            authType = obj.optString("authType", "NONE"),
                            customHeaderName = obj.optString("customAuthHeaderName", "X-API-Key"),
                            customAuthHeaderName = obj.optString("customAuthHeaderName", "X-API-Key"),
                            timeoutSeconds = obj.optInt("timeoutSeconds", 20).coerceIn(5, 120),
                            isEnabled = false,
                            requireConfirmationForWrites = obj.optBoolean("requireConfirmationForWrites", true),
                            connectionStatus = "DISCONNECTED",
                            statusMessage = "Imported from JSON (disabled by default until reviewed)."
                        )
                    )
                    importedCount++
                }
            }
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
