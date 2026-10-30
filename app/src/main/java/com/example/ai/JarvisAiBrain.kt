package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.control.WebSearchResultItem
import com.example.data.local.ChatMessageEntity
import com.example.data.local.MemoryEntity
import com.example.security.GeminiModelOption
import com.example.security.JarvisLanguage
import com.example.security.SecureVault
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class TaskStepItem(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val actionType: String, // "OPEN_APP", "SET_TIMER", "SET_ALARM", "OPEN_SETTINGS", "DIAL_NUMBER", "SHARE_TEXT", "WEB_SEARCH", "SAVE_MEMORY", "AI_REASONING"
    val actionTarget: String,
    val requiresPermission: String,
    val requiresConfirmation: Boolean,
    var status: String = "PENDING", // "PENDING", "RUNNING", "COMPLETED", "SKIPPED", "FAILED"
    var resultNote: String = ""
)

data class GeneratedTaskPlan(
    val goal: String,
    val summary: String,
    val steps: List<TaskStepItem>,
    val suggestedMemoryFact: String? = null
)

data class AiGenerationResult(
    val text: String,
    val providerLabel: String,
    val suggestedMemoryFact: String? = null
)

class JarvisAiBrain(private val secureVault: SecureVault) {

    companion object {
        private const val TAG = "JarvisAiBrain"
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // Lazy initialization per Firebase AI Logic mandatory guidelines
    private val flashModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val flashLiteModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-flash-lite")
    }

    private val argonModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-4-argon")
    }

    // Structured JSON Schema using Schema.obj (NOT Schema.object) for Multi-Step Task Execution
    private val taskPlanSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "summary" to Schema.string(description = "Brief overview of how JARVIS will accomplish the goal"),
                "steps" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "stepNumber" to Schema.integer(description = "1-based sequential order"),
                            "title" to Schema.string(description = "Short title of the step"),
                            "description" to Schema.string(description = "Explanation of what this step does"),
                            "actionType" to Schema.enumeration(
                                values = listOf(
                                    "OPEN_APP",
                                    "SET_TIMER",
                                    "SET_ALARM",
                                    "OPEN_SETTINGS",
                                    "DIAL_NUMBER",
                                    "SHARE_TEXT",
                                    "WEB_SEARCH",
                                    "SAVE_MEMORY",
                                    "AI_REASONING"
                                ),
                                description = "Type of Android or AI action to perform"
                            ),
                            "actionTarget" to Schema.string(description = "App name, timer seconds, HH:MM alarm time, setting key, phone number, search query, or prompt"),
                            "requiresPermission" to Schema.string(description = "Android permission required, or NONE"),
                            "requiresConfirmation" to Schema.boolean(description = "True if sensitive action like dialing, sharing, or modifying settings")
                        )
                    ),
                    description = "Ordered steps to complete the user request"
                ),
                "suggestedMemoryFact" to Schema.string(
                    description = "Optional personal preference or fact to ask the user before saving to Memory Vault",
                    nullable = true
                )
            ),
            optionalProperties = listOf("suggestedMemoryFact")
        )
    }

    private val structuredTaskPlannerModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = taskPlanSchema
            }
        )
    }

    // Gemini Live API session state (gemini-3.8-live)
    @OptIn(PublicPreviewAPI::class)
    private var activeLiveSession: LiveSession? = null

    @OptIn(PublicPreviewAPI::class)
    private val liveModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = "gemini-3.8-live",
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice("FENRIR"))
            },
            systemInstruction = content {
                text(
                    "You are JARVIS, an advanced, articulate, and helpful personal AI assistant on Android. " +
                        "You understand English, Hindi (हिन्दी), and Hinglish naturally and reply in the language the user speaks. " +
                        "Be concise, accurate, and transparent about Android permissions."
                )
            }
        )
    }

    private fun getActiveFirebaseModel(): GenerativeModel {
        return when (secureVault.selectedModel) {
            GeminiModelOption.GEMINI_3_8_FLASH -> flashModel
            GeminiModelOption.GEMINI_3_5_FLASH_LITE -> flashLiteModel
            GeminiModelOption.GEMINI_4_ARGON -> argonModel
        }
    }

    fun getActiveProviderBadge(): String {
        val modelName = secureVault.selectedModel.displayName
        return if (secureVault.useCustomGeminiKey && secureVault.customGeminiApiKey.isNotBlank()) {
            "Custom Gemini Key • $modelName"
        } else {
            "Firebase AI Logic • $modelName"
        }
    }

    private fun buildSystemContextPrompt(
        approvedMemories: List<MemoryEntity>,
        webResults: List<WebSearchResultItem>,
        extraDocumentText: String?
    ): String {
        val langInstruction = when (secureVault.language) {
            JarvisLanguage.HINDI -> "Respond primarily in natural Hindi (हिन्दी)."
            JarvisLanguage.ENGLISH -> "Respond in clear, natural English."
            JarvisLanguage.HINGLISH -> "Respond in natural conversational Hinglish (blend of Hindi and English in Latin/Devanagari script as appropriate)."
            JarvisLanguage.AUTO -> "Detect whether the user is speaking Hindi, English, or Hinglish and respond in the exact same language and style."
        }

        val memoryBlock = if (secureVault.isMemoryEnabled && approvedMemories.isNotEmpty()) {
            "USER-APPROVED MEMORY VAULT:\n" + approvedMemories.take(12).joinToString("\n") {
                "- [${it.category}] ${it.keyTitle}: ${it.content}"
            }
        } else {
            "USER-APPROVED MEMORY VAULT: None active."
        }

        val webBlock = if (webResults.isNotEmpty()) {
            "VERIFIED WEB RESEARCH SOURCES (Cite URLs when referencing these facts):\n" +
                webResults.joinToString("\n") { "- ${it.title} (${it.source}): ${it.snippet} [URL: ${it.url}]" }
        } else {
            ""
        }

        val docBlock = if (!extraDocumentText.isNullOrBlank()) {
            "USER-SELECTED DOCUMENT CONTENT:\n$extraDocumentText"
        } else {
            ""
        }

        return """
            You are JARVIS, an advanced native Android personal AI assistant.
            Language Directive: $langInstruction
            Security & Honesty Rules:
            - Respect Android security boundaries. Never claim to have performed an action that Android rejected or that requires ungranted permissions.
            - Distinguish verified facts from uncertain information.
            $memoryBlock
            $webBlock
            $docBlock
        """.trimIndent()
    }

    /**
     * Streams conversational or multimodal response chunks for low Time-To-First-Token (TTFT).
     */
    fun streamAssistantResponse(
        userPrompt: String,
        history: List<ChatMessageEntity> = emptyList(),
        approvedMemories: List<MemoryEntity> = emptyList(),
        attachedBitmap: Bitmap? = null,
        extraDocumentText: String? = null,
        webResults: List<WebSearchResultItem> = emptyList()
    ): Flow<AiGenerationResult> = flow {
        val systemPreamble = buildSystemContextPrompt(approvedMemories, webResults, extraDocumentText)
        val recentHistory = history.takeLast(8).joinToString("\n") { msg ->
            val speaker = if (msg.role == "user") "User" else "JARVIS"
            "$speaker: ${msg.content}"
        }
        val combinedPrompt = buildString {
            append(systemPreamble)
            if (recentHistory.isNotBlank()) {
                append("\n\nRECENT CONVERSATION CONTEXT:\n")
                append(recentHistory)
            }
            append("\n\nUSER REQUEST:\n")
            append(userPrompt)
        }

        // If user explicitly enabled Custom Gemini Key in Vault, use direct Gemini REST endpoint
        if (secureVault.useCustomGeminiKey && secureVault.customGeminiApiKey.isNotBlank()) {
            val directResponse = withTimeout(60_000L) {
                callDirectGeminiRestApi(
                    apiKey = secureVault.customGeminiApiKey,
                    modelId = secureVault.selectedModel.modelId,
                    prompt = combinedPrompt,
                    bitmap = attachedBitmap
                )
            }
            emit(
                AiGenerationResult(
                    text = directResponse,
                    providerLabel = "Custom Gemini Key (${secureVault.selectedModel.displayName})"
                )
            )
            return@flow
        }

        // Primary Path: Provisioned Firebase AI Logic with App Check attestation
        val model = getActiveFirebaseModel()
        val providerLabel = "Firebase AI Logic (${secureVault.selectedModel.displayName})"
        val accumulated = StringBuilder()

        try {
            withTimeout(60_000L) {
                val inputContent = content {
                    if (attachedBitmap != null) {
                        image(attachedBitmap)
                    }
                    text(combinedPrompt)
                }
                model.generateContentStream(inputContent).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(
                            AiGenerationResult(
                                text = accumulated.toString(),
                                providerLabel = providerLabel
                            )
                        )
                    }
                }
            }

            if (accumulated.isBlank()) {
                throw IllegalStateException("Model returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            throw IllegalStateException("AI request timed out after 60 seconds. Please check your connection and try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Primary Firebase AI generation failed", e)
            // Check if Secondary Fallback AI Provider is explicitly configured & enabled by user
            if (secureVault.isSecondaryAiEnabled && secureVault.secondaryAiApiKey.isNotBlank()) {
                val fallbackText = withTimeout(45_000L) {
                    callSecondaryOpenRouterFallback(
                        apiKey = secureVault.secondaryAiApiKey,
                        prompt = combinedPrompt
                    )
                }
                emit(
                    AiGenerationResult(
                        text = "[Fallback Notice: Primary Gemini model encountered an error (${e.localizedMessage ?: "error"}); switched to configured fallback provider ${secureVault.secondaryAiName}]\n\n$fallbackText",
                        providerLabel = "Fallback: ${secureVault.secondaryAiName}"
                    )
                )
            } else {
                throw e
            }
        }
    }

    /**
     * Generates a structured Multi-Step Task Execution plan using Schema.obj.
     */
    suspend fun generateMultiStepTaskPlan(
        goal: String,
        approvedMemories: List<MemoryEntity>
    ): Result<GeneratedTaskPlan> {
        return try {
            val memorySnippet = if (approvedMemories.isNotEmpty()) {
                approvedMemories.take(5).joinToString("; ") { "${it.keyTitle}: ${it.content}" }
            } else {
                "None"
            }
            val prompt = """
                You are JARVIS Multi-Step Task Planner on Android.
                Break down the user's goal into 2 to 6 concrete, ordered steps.
                User Goal: "$goal"
                Approved User Memory Context: $memorySnippet
                Rules:
                - Use OPEN_APP with actionTarget = app name (e.g. "Chrome", "Calculator", "Calendar") when launching an app.
                - Use SET_TIMER with actionTarget = seconds as integer string (e.g. "300" for 5 minutes) when setting a timer.
                - Use SET_ALARM with actionTarget = "HH:MM" (24-hour format, e.g. "07:30") when creating an alarm.
                - Use OPEN_SETTINGS with actionTarget = setting category ("wifi", "bluetooth", "display", "sound", "battery", "accessibility", "notifications").
                - Use DIAL_NUMBER with actionTarget = phone number and requiresConfirmation = true for calls.
                - Use WEB_SEARCH with actionTarget = search query when online research is needed.
                - Use SAVE_MEMORY with actionTarget = "Key: Value" and requiresConfirmation = true when saving a fact.
                - Use AI_REASONING when synthesizing, drafting, or explaining information.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                structuredTaskPlannerModel.generateContent(prompt)
            }
            val jsonString = response.text ?: throw IllegalStateException("Task planner returned empty JSON.")
            val json = JSONObject(jsonString)
            val summary = json.optString("summary", "Structured execution plan prepared.")
            val suggestedMemory = json.optString("suggestedMemoryFact", "").takeIf { it.isNotBlank() && it != "null" }
            val stepsArr = json.getJSONArray("steps")
            val steps = (0 until stepsArr.length()).map { idx ->
                val obj = stepsArr.getJSONObject(idx)
                TaskStepItem(
                    stepNumber = obj.optInt("stepNumber", idx + 1),
                    title = obj.optString("title", "Step ${idx + 1}"),
                    description = obj.optString("description", ""),
                    actionType = obj.optString("actionType", "AI_REASONING"),
                    actionTarget = obj.optString("actionTarget", ""),
                    requiresPermission = obj.optString("requiresPermission", "NONE"),
                    requiresConfirmation = obj.optBoolean("requiresConfirmation", false)
                )
            }
            Result.success(
                GeneratedTaskPlan(
                    goal = goal,
                    summary = summary,
                    steps = steps,
                    suggestedMemoryFact = suggestedMemory
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Task planning timed out", e)
            Result.failure(IllegalStateException("Task planning timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate structured task plan", e)
            Result.failure(e)
        }
    }

    /**
     * Starts a real-time bidirectional Gemini Live API (gemini-3.8-live) voice conversation session.
     */
    @OptIn(PublicPreviewAPI::class)
    suspend fun startGeminiLiveAudioSession(): Result<String> {
        return try {
            stopGeminiLiveAudioSession()
            val session = withTimeout(30_000L) {
                liveModel.connect()
            }
            activeLiveSession = session
            session.startAudioConversation()
            Result.success("Connected to Gemini 3.8 Live API (Real-Time Bidirectional Voice Active).")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Live API connection failed", e)
            activeLiveSession = null
            Result.failure(e)
        }
    }

    @OptIn(PublicPreviewAPI::class)
    fun stopGeminiLiveAudioSession() {
        try {
            activeLiveSession?.stopAudioConversation()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping Gemini Live session", e)
        } finally {
            activeLiveSession = null
        }
    }

    @OptIn(PublicPreviewAPI::class)
    fun isGeminiLiveActive(): Boolean = activeLiveSession != null

    /**
     * Tests the active Gemini connection (either Custom Gemini Key or Provisioned Firebase AI Logic).
     */
    suspend fun testGeminiConnection(candidateCustomKey: String? = null): Result<String> {
        return try {
            val keyToTest = candidateCustomKey?.trim().orEmpty()
            if (keyToTest.isNotBlank()) {
                val reply = withTimeout(30_000L) {
                    callDirectGeminiRestApi(
                        apiKey = keyToTest,
                        modelId = secureVault.selectedModel.modelId,
                        prompt = "Reply with one short sentence confirming JARVIS Gemini API connection is online.",
                        bitmap = null
                    )
                }
                Result.success("Verified Custom Key (${secureVault.selectedModel.displayName}): $reply")
            } else {
                val response = withTimeout(30_000L) {
                    getActiveFirebaseModel().generateContent("Reply with one short sentence confirming JARVIS Firebase AI Logic connection is online.")
                }
                val text = response.text?.trim().orEmpty()
                if (text.isNotBlank()) {
                    Result.success("Verified Firebase AI Logic (${secureVault.selectedModel.displayName}): $text")
                } else {
                    Result.failure(IllegalStateException("Received empty response from model."))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini connection test failed", e)
            Result.failure(e)
        }
    }

    private suspend fun callDirectGeminiRestApi(
        apiKey: String,
        modelId: String,
        prompt: String,
        bitmap: Bitmap?
    ): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
        val partsArray = JSONArray()
        partsArray.put(JSONObject().put("text", prompt))

        if (bitmap != null) {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val inlineData = JSONObject()
                .put("mime_type", "image/jpeg")
                .put("data", b64)
            partsArray.put(JSONObject().put("inline_data", inlineData))
        }

        val payload = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", partsArray))
        )

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errMsg = try {
                    JSONObject(bodyStr).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                } ?: "HTTP ${response.code}"
                throw IllegalStateException("Gemini API Error: $errMsg")
            }
            val json = JSONObject(bodyStr)
            val candidates = json.optJSONArray("candidates")
            val firstContent = candidates?.optJSONObject(0)?.optJSONObject("content")
            val parts = firstContent?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text").orEmpty()
            if (text.isBlank()) throw IllegalStateException("Gemini REST API returned an empty response.")
            text
        }
    }

    private suspend fun callSecondaryOpenRouterFallback(
        apiKey: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val url = "https://openrouter.ai/api/v1/chat/completions"
        val messages = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", prompt)
        )
        val payload = JSONObject()
            .put("model", "google/gemini-2.5-flash")
            .put("messages", messages)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Secondary Provider HTTP ${response.code}")
            }
            val json = JSONObject(bodyStr)
            val text = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (text.isBlank()) throw IllegalStateException("Secondary provider returned empty content.")
            text
        }
    }

    suspend fun testSecondaryProviderConnection(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("API Key is empty."))
            val reply = callSecondaryOpenRouterFallback(
                apiKey = apiKey.trim(),
                prompt = "Reply with 'Secondary provider online.'"
            )
            Result.success("Connected: ${reply.take(80)}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
