package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeneratedTaskPlan
import com.example.ai.JarvisAiBrain
import com.example.ai.TaskStepItem
import com.example.control.AndroidControlExecutor
import com.example.control.InstalledAppInfo
import com.example.control.PendingSensitiveAction
import com.example.control.SelectedFilePayload
import com.example.control.SensitiveActionType
import com.example.control.WebSearchResultItem
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.JarvisDatabase
import com.example.data.local.McpAuditLogEntity
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.TaskPlanEntity
import com.example.integrations.CalendarEventSummary
import com.example.integrations.DriveFileItem
import com.example.integrations.GmailMessageSummary
import com.example.integrations.GoogleAuthOutcome
import com.example.integrations.GoogleContactItem
import com.example.integrations.GoogleServiceStatus
import com.example.integrations.GoogleWorkspaceClient
import com.example.integrations.YouTubePlaylistItem
import com.example.mcp.McpAuthType
import com.example.mcp.McpClientEngine
import com.example.mcp.McpSafetyClassifier
import com.example.mcp.McpTransportType
import com.example.security.ElevenLabsVoiceOption
import com.example.security.GeminiModelOption
import com.example.security.JarvisLanguage
import com.example.security.SecureVault
import com.example.services.CapturedNotification
import com.example.services.FloatingOrbService
import com.example.services.JarvisForegroundService
import com.example.services.JarvisServiceRuntime
import com.example.voice.AssistantVoiceState
import com.example.voice.JarvisVoiceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class JarvisNavTab {
    HOME,
    CHAT,
    CONTROL,
    MEMORY,
    SETTINGS
}

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val message: String) : ConnectionTestState
    data class Error(val message: String) : ConnectionTestState
}

data class PendingConfirmationDialog(
    val title: String,
    val description: String,
    val badgeLabel: String,
    val onConfirm: suspend () -> Unit
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val secureVault = SecureVault(appContext)
    private val dao = JarvisDatabase.getInstance(appContext).jarvisDao()
    val controlExecutor = AndroidControlExecutor(appContext)
    val aiBrain = JarvisAiBrain(secureVault)
    val googleWorkspaceClient = GoogleWorkspaceClient(appContext, secureVault)
    val mcpClientEngine = McpClientEngine(dao, secureVault)

    // Navigation & Onboarding State
    private val _showOnboarding = MutableStateFlow(!secureVault.isOnboardingCompleted)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    private val _currentTab = MutableStateFlow(JarvisNavTab.HOME)
    val currentTab: StateFlow<JarvisNavTab> = _currentTab.asStateFlow()

    // Home Assistant Response & Status State
    private val _latestAssistantReply = MutableStateFlow(
        "Good day. JARVIS is online and ready. Tap the microphone, say \"Hey JARVIS\" when Wake-Word is enabled, start Gemini Live Voice, or type any command below."
    )
    val latestAssistantReply: StateFlow<String> = _latestAssistantReply.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingPartialReply = MutableStateFlow("")
    val streamingPartialReply: StateFlow<String> = _streamingPartialReply.asStateFlow()

    private val _activeProviderBadge = MutableStateFlow(aiBrain.getActiveProviderBadge())
    val activeProviderBadge: StateFlow<String> = _activeProviderBadge.asStateFlow()

    private val _isGeminiLiveSessionActive = MutableStateFlow(false)
    val isGeminiLiveSessionActive: StateFlow<Boolean> = _isGeminiLiveSessionActive.asStateFlow()

    // Attached File / Multimodal State
    private val _selectedFilePayload = MutableStateFlow<SelectedFilePayload?>(null)
    val selectedFilePayload: StateFlow<SelectedFilePayload?> = _selectedFilePayload.asStateFlow()

    // Web Search Sources State
    private val _latestWebSources = MutableStateFlow<List<WebSearchResultItem>>(emptyList())
    val latestWebSources: StateFlow<List<WebSearchResultItem>> = _latestWebSources.asStateFlow()

    // Sensitive Action Confirmation Gate (Phone / Share)
    private val _pendingSensitiveAction = MutableStateFlow<PendingSensitiveAction?>(null)
    val pendingSensitiveAction: StateFlow<PendingSensitiveAction?> = _pendingSensitiveAction.asStateFlow()

    // Sensitive Google / MCP Operation Confirmation Gate (Drive Delete, Gmail Draft, MCP Write/Destructive)
    private val _pendingOperationConfirmation = MutableStateFlow<PendingConfirmationDialog?>(null)
    val pendingOperationConfirmation: StateFlow<PendingConfirmationDialog?> = _pendingOperationConfirmation.asStateFlow()

    // Connection Test States for Onboarding & Settings
    private val _geminiTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val geminiTestState: StateFlow<ConnectionTestState> = _geminiTestState.asStateFlow()

    private val _elevenLabsTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val elevenLabsTestState: StateFlow<ConnectionTestState> = _elevenLabsTestState.asStateFlow()

    private val _secondaryAiTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val secondaryAiTestState: StateFlow<ConnectionTestState> = _secondaryAiTestState.asStateFlow()

    // Google Workspace & Third-Party Integration States (Section 30)
    private val _googleConnectedEmail = MutableStateFlow(secureVault.googleConnectedEmail)
    val googleConnectedEmail: StateFlow<String> = _googleConnectedEmail.asStateFlow()

    private val _googleGrantedScopes = MutableStateFlow(secureVault.googleGrantedScopes)
    val googleGrantedScopes: StateFlow<Set<String>> = _googleGrantedScopes.asStateFlow()

    private val _googleConnectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val googleConnectionTestState: StateFlow<ConnectionTestState> = _googleConnectionTestState.asStateFlow()

    private val _pendingOAuthConsentIntent = MutableStateFlow<Intent?>(null)
    val pendingOAuthConsentIntent: StateFlow<Intent?> = _pendingOAuthConsentIntent.asStateFlow()

    private val _driveFiles = MutableStateFlow<List<DriveFileItem>>(emptyList())
    val driveFiles: StateFlow<List<DriveFileItem>> = _driveFiles.asStateFlow()

    private val _driveDisambiguationFiles = MutableStateFlow<List<DriveFileItem>>(emptyList())
    val driveDisambiguationFiles: StateFlow<List<DriveFileItem>> = _driveDisambiguationFiles.asStateFlow()

    private val _selectedDriveFile = MutableStateFlow<DriveFileItem?>(null)
    val selectedDriveFile: StateFlow<DriveFileItem?> = _selectedDriveFile.asStateFlow()

    private val _calendarEvents = MutableStateFlow<List<CalendarEventSummary>>(emptyList())
    val calendarEvents: StateFlow<List<CalendarEventSummary>> = _calendarEvents.asStateFlow()

    private val _gmailMessages = MutableStateFlow<List<GmailMessageSummary>>(emptyList())
    val gmailMessages: StateFlow<List<GmailMessageSummary>> = _gmailMessages.asStateFlow()

    private val _googleContacts = MutableStateFlow<List<GoogleContactItem>>(emptyList())
    val googleContacts: StateFlow<List<GoogleContactItem>> = _googleContacts.asStateFlow()

    private val _youtubePlaylists = MutableStateFlow<List<YouTubePlaylistItem>>(emptyList())
    val youtubePlaylists: StateFlow<List<YouTubePlaylistItem>> = _youtubePlaylists.asStateFlow()

    private val _isGoogleOperationLoading = MutableStateFlow(false)
    val isGoogleOperationLoading: StateFlow<Boolean> = _isGoogleOperationLoading.asStateFlow()

    // MCP Server Management States (Section 31)
    val mcpServers: StateFlow<List<McpServerEntity>> = dao.observeMcpServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mcpTools: StateFlow<List<McpToolEntity>> = dao.observeMcpTools()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mcpAuditLogs: StateFlow<List<McpAuditLogEntity>> = dao.observeMcpAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _mcpLatestToolOutput = MutableStateFlow<String?>(null)
    val mcpLatestToolOutput: StateFlow<String?> = _mcpLatestToolOutput.asStateFlow()

    // Settings Reactive Mirrors
    private val _selectedLanguage = MutableStateFlow(secureVault.language)
    val selectedLanguage: StateFlow<JarvisLanguage> = _selectedLanguage.asStateFlow()

    private val _selectedModel = MutableStateFlow(secureVault.selectedModel)
    val selectedModel: StateFlow<GeminiModelOption> = _selectedModel.asStateFlow()

    private val _wakeWordEnabled = MutableStateFlow(secureVault.isWakeWordEnabled)
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private val _elevenLabsEnabled = MutableStateFlow(secureVault.isElevenLabsEnabled)
    val elevenLabsEnabled: StateFlow<Boolean> = _elevenLabsEnabled.asStateFlow()

    private val _selectedElevenVoice = MutableStateFlow(secureVault.elevenLabsVoice)
    val selectedElevenVoice: StateFlow<ElevenLabsVoiceOption> = _selectedElevenVoice.asStateFlow()

    private val _speechRate = MutableStateFlow(secureVault.speechRate)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _speechPitch = MutableStateFlow(secureVault.speechPitch)
    val speechPitch: StateFlow<Float> = _speechPitch.asStateFlow()

    private val _autoSpeak = MutableStateFlow(secureVault.autoSpeakResponses)
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    private val _memoryEnabled = MutableStateFlow(secureVault.isMemoryEnabled)
    val memoryEnabled: StateFlow<Boolean> = _memoryEnabled.asStateFlow()

    private val _webSearchEnabled = MutableStateFlow(secureVault.isWebSearchEnabled)
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()

    // Installed Apps & Active Task Execution State
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _activeTaskPlan = MutableStateFlow<GeneratedTaskPlan?>(null)
    val activeTaskPlan: StateFlow<GeneratedTaskPlan?> = _activeTaskPlan.asStateFlow()

    private val _isTaskRunning = MutableStateFlow(false)
    val isTaskRunning: StateFlow<Boolean> = _isTaskRunning.asStateFlow()

    private val _isTaskPaused = MutableStateFlow(false)
    val isTaskPaused: StateFlow<Boolean> = _isTaskPaused.asStateFlow()

    // Chat Sessions & Messages
    val chatSessions: StateFlow<List<ChatSessionEntity>> = dao.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    val activeSessionMessages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else dao.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Memories & Reminders
    val memories: StateFlow<List<MemoryEntity>> = dao.observeMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = dao.observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedTaskPlans: StateFlow<List<TaskPlanEntity>> = dao.observeTaskPlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Runtime Service Bridges
    val isScreenSharingActive: StateFlow<Boolean> = JarvisServiceRuntime.isScreenSharingActive
    val latestScreenBitmap: StateFlow<Bitmap?> = JarvisServiceRuntime.latestScreenBitmap
    val isFloatingOrbRunning: StateFlow<Boolean> = JarvisServiceRuntime.isFloatingOrbRunning
    val capturedNotifications: StateFlow<List<CapturedNotification>> = JarvisServiceRuntime.capturedNotifications

    private var activeGenerationJob: Job? = null
    private var activeTaskExecutionJob: Job? = null

    val voiceManager = JarvisVoiceManager(
        context = appContext,
        secureVault = secureVault,
        onCommandRecognized = { recognizedText ->
            handleUserPromptOrVoiceCommand(recognizedText, fromVoice = true)
        },
        onWakeWordDetected = {
            _statusBannerMessage.value = "Wake phrase \"Hey JARVIS\" detected — listening for your command..."
        },
        onVoiceNotice = { notice ->
            _statusBannerMessage.value = notice
        }
    )

    val voiceState: StateFlow<AssistantVoiceState> = voiceManager.voiceState
    val partialTranscript: StateFlow<String> = voiceManager.partialTranscript
    val rmsLevel: StateFlow<Float> = voiceManager.rmsLevel
    val activeVoiceProviderLabel: StateFlow<String> = voiceManager.activeVoiceProviderLabel

    init {
        viewModelScope.launch {
            ensureDefaultSessionAndSeedData()
            refreshInstalledApps()
        }
        viewModelScope.launch {
            JarvisServiceRuntime.floatingOrbCommand.collect { orbCmd ->
                if (!orbCmd.isNullOrBlank()) {
                    JarvisServiceRuntime.consumeFloatingOrbCommand()
                    handleUserPromptOrVoiceCommand(orbCmd, fromVoice = false)
                }
            }
        }
    }

    private suspend fun ensureDefaultSessionAndSeedData() {
        val existingSessions = dao.observeSessions().stateIn(viewModelScope).value
        if (existingSessions.isEmpty()) {
            val id = dao.insertSession(ChatSessionEntity(title = "Primary JARVIS Console"))
            _activeSessionId.value = id
            dao.insertMessage(
                ChatMessageEntity(
                    sessionId = id,
                    role = "model",
                    content = "Hello! I am **JARVIS**, your native Android personal AI assistant.\n\n" +
                        "- Speak or type in **English, Hindi (हिन्दी), or Hinglish**.\n" +
                        "- Say **\"Hey JARVIS\"** when Wake-Word mode is enabled, or use **Gemini 3.8 Live Voice**.\n" +
                        "- Connect **Google Drive, Gmail, Calendar, Contacts & YouTube** or add **Unlimited Custom MCP Servers** in **Control** or **Settings**.\n" +
                        "- Try commands like *\"Find my PDF in Drive\"*, *\"Show my upcoming Calendar events\"*, or *\"Show my connected MCP servers\"*.",
                    providerUsed = aiBrain.getActiveProviderBadge()
                )
            )
            dao.insertMemory(
                MemoryEntity(
                    category = "PREFERENCE",
                    keyTitle = "Bilingual Assistant Style",
                    content = "User prefers concise, actionable answers and natural English/Hindi/Hinglish code-switching.",
                    isApproved = true
                )
            )
        } else if (_activeSessionId.value == null) {
            _activeSessionId.value = existingSessions.first().id
        }

        // Seed a disabled template MCP Server if none exist so the user can see the MCP schema in action
        if (dao.getAllMcpServers().isEmpty()) {
            val templateId = dao.insertMcpServer(
                McpServerEntity(
                    name = "Custom Remote MCP Template (Streamable HTTP)",
                    endpointUrl = "https://example-mcp-gateway.yourdomain.com/mcp",
                    transportType = McpTransportType.STREAMABLE_HTTP.name,
                    authType = McpAuthType.BEARER_TOKEN.name,
                    isEnabled = false,
                    requireConfirmationForWrites = true,
                    connectionStatus = "DISCONNECTED",
                    statusMessage = "Template server (disabled). Edit endpoint URL & token, then tap Connect & Discover Tools."
                )
            )
            dao.insertMcpTools(
                listOf(
                    McpToolEntity(
                        serverId = templateId,
                        serverName = "Custom Remote MCP Template (Streamable HTTP)",
                        toolName = "search_knowledge_base",
                        description = "Searches your remote MCP knowledge base or documentation index by query.",
                        inputSchemaJson = """{"type":"object","properties":{"query":{"type":"string"}}}""",
                        safetyClassification = "READ_ONLY",
                        isApproved = true,
                        hasPromptInjectionWarning = false
                    )
                )
            )
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = controlExecutor.getInstalledLaunchableApps()
        }
    }

    fun selectTab(tab: JarvisNavTab) {
        _currentTab.value = tab
    }

    fun completeOnboarding() {
        secureVault.isOnboardingCompleted = true
        _showOnboarding.value = false
        _activeProviderBadge.value = aiBrain.getActiveProviderBadge()
        voiceManager.refreshVoiceProviderLabel()
    }

    fun reopenOnboardingWizard() {
        _showOnboarding.value = true
    }

    fun dismissStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun confirmPendingOperationDialog() {
        val pending = _pendingOperationConfirmation.value ?: return
        _pendingOperationConfirmation.value = null
        viewModelScope.launch {
            pending.onConfirm()
        }
    }

    fun dismissPendingOperationDialog() {
        _pendingOperationConfirmation.value = null
        _statusBannerMessage.value = "Operation cancelled by user."
    }

    // =========================================================================
    // SECTION 30: GOOGLE WORKSPACE & THIRD-PARTY APP INTEGRATIONS
    // =========================================================================

    fun getGoogleServiceStatusList(): List<GoogleServiceStatus> {
        return googleWorkspaceClient.getServiceStatuses()
    }

    fun signInAndConnectGoogleWorkspace(
        activity: Activity,
        requestedScopes: List<String> = GoogleWorkspaceClient.ALL_WORKSPACE_SCOPES
    ) {
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            _googleConnectionTestState.value = ConnectionTestState.Testing
            val outcome = googleWorkspaceClient.connectGoogleAccount(activity, requestedScopes)
            _isGoogleOperationLoading.value = false
            when (outcome) {
                is GoogleAuthOutcome.Connected -> {
                    _googleConnectedEmail.value = outcome.email
                    _googleGrantedScopes.value = outcome.grantedScopes
                    _googleConnectionTestState.value = ConnectionTestState.Success(
                        "Connected as ${outcome.email} (${outcome.grantedScopes.size} OAuth scopes authorized)."
                    )
                    _statusBannerMessage.value = "Google Workspace connected for ${outcome.email}."
                    refreshGoogleDriveFiles()
                }
                is GoogleAuthOutcome.NeedsConsentResolution -> {
                    _googleConnectedEmail.value = outcome.email
                    _pendingOAuthConsentIntent.value = outcome.consentIntent
                    _googleConnectionTestState.value = ConnectionTestState.Testing
                    _statusBannerMessage.value = "Please approve Google Workspace permissions in the consent prompt for ${outcome.email}."
                }
                is GoogleAuthOutcome.Error -> {
                    _googleConnectionTestState.value = ConnectionTestState.Error(outcome.message)
                    _statusBannerMessage.value = outcome.message
                }
            }
        }
    }

    fun onOAuthConsentActivityResult(
        resultCode: Int,
        requestedScopes: List<String> = GoogleWorkspaceClient.ALL_WORKSPACE_SCOPES
    ) {
        _pendingOAuthConsentIntent.value = null
        val email = _googleConnectedEmail.value
        if (resultCode != Activity.RESULT_OK || email.isBlank()) {
            _googleConnectionTestState.value = ConnectionTestState.Error("Google OAuth permission prompt was cancelled or denied.")
            _statusBannerMessage.value = "Google OAuth consent was cancelled."
            return
        }
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            val outcome = googleWorkspaceClient.completeConsentResolution(email, requestedScopes)
            _isGoogleOperationLoading.value = false
            when (outcome) {
                is GoogleAuthOutcome.Connected -> {
                    _googleConnectedEmail.value = outcome.email
                    _googleGrantedScopes.value = outcome.grantedScopes
                    _googleConnectionTestState.value = ConnectionTestState.Success(
                        "Google OAuth 2.0 authorized for ${outcome.email}."
                    )
                    _statusBannerMessage.value = "Google Workspace authorized! Loading Drive files..."
                    refreshGoogleDriveFiles()
                }
                is GoogleAuthOutcome.NeedsConsentResolution -> {
                    _pendingOAuthConsentIntent.value = outcome.consentIntent
                }
                is GoogleAuthOutcome.Error -> {
                    _googleConnectionTestState.value = ConnectionTestState.Error(outcome.message)
                    _statusBannerMessage.value = outcome.message
                }
            }
        }
    }

    fun connectWithManualOAuthToken(accessToken: String) {
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            _googleConnectionTestState.value = ConnectionTestState.Testing
            val res = googleWorkspaceClient.saveAndVerifyManualOAuthToken(accessToken)
            _isGoogleOperationLoading.value = false
            res.fold(
                onSuccess = { msg ->
                    _googleConnectedEmail.value = secureVault.googleConnectedEmail
                    _googleGrantedScopes.value = secureVault.googleGrantedScopes
                    _googleConnectionTestState.value = ConnectionTestState.Success(msg)
                    _statusBannerMessage.value = msg
                    refreshGoogleDriveFiles()
                },
                onFailure = { err ->
                    _googleConnectionTestState.value = ConnectionTestState.Error(err.localizedMessage ?: "Token verification failed.")
                }
            )
        }
    }

    fun testGoogleWorkspaceConnection() {
        viewModelScope.launch {
            _googleConnectionTestState.value = ConnectionTestState.Testing
            val res = googleWorkspaceClient.testGoogleConnection()
            _googleConnectionTestState.value = res.fold(
                onSuccess = { ConnectionTestState.Success(it) },
                onFailure = { ConnectionTestState.Error(it.localizedMessage ?: "Google connection test failed.") }
            )
        }
    }

    fun disconnectGoogleWorkspace() {
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            val res = googleWorkspaceClient.disconnectGoogleServices()
            _isGoogleOperationLoading.value = false
            _googleConnectedEmail.value = ""
            _googleGrantedScopes.value = emptySet()
            _driveFiles.value = emptyList()
            _calendarEvents.value = emptyList()
            _gmailMessages.value = emptyList()
            _googleContacts.value = emptyList()
            _youtubePlaylists.value = emptyList()
            _googleConnectionTestState.value = ConnectionTestState.Idle
            _statusBannerMessage.value = res.getOrDefault("Disconnected Google Workspace.")
        }
    }

    // Google Drive Actions
    fun refreshGoogleDriveFiles(nameQuery: String = "", onlyPdfs: Boolean = false) {
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            val res = googleWorkspaceClient.searchAndListDriveFiles(
                nameQuery = nameQuery,
                mimeTypeFilter = if (onlyPdfs) "application/pdf" else null
            )
            _isGoogleOperationLoading.value = false
            res.fold(
                onSuccess = { files ->
                    _driveFiles.value = files
                    _statusBannerMessage.value = "Loaded ${files.size} Google Drive items."
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Drive search failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun selectDriveFile(file: DriveFileItem) {
        _selectedDriveFile.value = file
        _driveDisambiguationFiles.value = emptyList()
        _statusBannerMessage.value = "Selected Drive file: '${file.name}'."
    }

    fun readAndSummarizeDriveFile(file: DriveFileItem) {
        viewModelScope.launch {
            _selectedDriveFile.value = file
            _driveDisambiguationFiles.value = emptyList()
            _isGoogleOperationLoading.value = true
            _statusBannerMessage.value = "Reading '${file.name}' from Google Drive..."
            val contentRes = googleWorkspaceClient.readDriveDocumentText(file)
            _isGoogleOperationLoading.value = false
            contentRes.fold(
                onSuccess = { extractedText ->
                    handleUserPromptOrVoiceCommand(
                        rawPrompt = "Please summarize this Google Drive document ('${file.name}', type: ${file.mimeType}) and highlight key takeaways:\n\n$extractedText",
                        fromVoice = false,
                        useScreenFrameIfActive = false
                    )
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Could not read '${file.name}': ${err.localizedMessage}"
                }
            )
        }
    }

    fun createGoogleDriveFolder(folderName: String) {
        val clean = folderName.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            val res = googleWorkspaceClient.createDriveFolder(clean)
            _isGoogleOperationLoading.value = false
            res.fold(
                onSuccess = { created ->
                    _statusBannerMessage.value = "Created Google Drive folder '${created.name}'."
                    refreshGoogleDriveFiles()
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Create folder failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun uploadUriToGoogleDrive(uri: Uri) {
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            _statusBannerMessage.value = "Uploading selected file to Google Drive..."
            val res = googleWorkspaceClient.uploadUserSelectedFileToDrive(uri)
            _isGoogleOperationLoading.value = false
            res.fold(
                onSuccess = { uploaded ->
                    _statusBannerMessage.value = "Uploaded '${uploaded.name}' to Google Drive."
                    refreshGoogleDriveFiles()
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Upload failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun renameGoogleDriveFile(file: DriveFileItem, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        _pendingOperationConfirmation.value = PendingConfirmationDialog(
            title = "Confirm Google Drive Rename",
            description = "Rename Google Drive file '${file.name}' to '$clean'?",
            badgeLabel = "GOOGLE DRIVE • WRITE",
            onConfirm = {
                _isGoogleOperationLoading.value = true
                val res = googleWorkspaceClient.renameDriveFile(file.id, clean)
                _isGoogleOperationLoading.value = false
                res.fold(
                    onSuccess = { msg ->
                        _statusBannerMessage.value = msg
                        refreshGoogleDriveFiles()
                    },
                    onFailure = { err ->
                        _statusBannerMessage.value = "Rename failed: ${err.localizedMessage}"
                    }
                )
            }
        )
    }

    fun downloadGoogleDriveFile(file: DriveFileItem) {
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            val res = googleWorkspaceClient.downloadDriveFileToCache(file)
            _isGoogleOperationLoading.value = false
            res.fold(
                onSuccess = { localFile ->
                    _statusBannerMessage.value = "Downloaded '${file.name}' (${localFile.length() / 1024} KB) to app storage: ${localFile.name}"
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Download failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun requestDeleteGoogleDriveFile(file: DriveFileItem) {
        _pendingOperationConfirmation.value = PendingConfirmationDialog(
            title = "Confirm Permanent Google Drive Deletion",
            description = "Are you sure you want JARVIS to delete '${file.name}' (${file.id}) from your Google Drive? This action is destructive.",
            badgeLabel = "GOOGLE DRIVE • DESTRUCTIVE",
            onConfirm = {
                _isGoogleOperationLoading.value = true
                val res = googleWorkspaceClient.deleteDriveFileConfirmed(file.id)
                _isGoogleOperationLoading.value = false
                res.fold(
                    onSuccess = { msg ->
                        _statusBannerMessage.value = msg
                        refreshGoogleDriveFiles()
                    },
                    onFailure = { err ->
                        _statusBannerMessage.value = "Delete failed: ${err.localizedMessage}"
                    }
                )
            }
        )
    }

    // Google Calendar, Gmail, Contacts, YouTube Actions
    fun loadUpcomingCalendarEvents() {
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            val res = googleWorkspaceClient.listUpcomingCalendarEvents()
            _isGoogleOperationLoading.value = false
            res.fold(
                onSuccess = { events ->
                    _calendarEvents.value = events
                    _statusBannerMessage.value = "Loaded ${events.size} upcoming Google Calendar events."
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Calendar fetch failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun searchGmailMessages(query: String) {
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            val res = googleWorkspaceClient.searchGmailMessages(query)
            _isGoogleOperationLoading.value = false
            res.fold(
                onSuccess = { msgs ->
                    _gmailMessages.value = msgs
                    _statusBannerMessage.value = "Found ${msgs.size} Gmail messages matching '$query'."
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Gmail search failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun requestCreateGmailDraft(to: String, subject: String, body: String) {
        _pendingOperationConfirmation.value = PendingConfirmationDialog(
            title = "Confirm Gmail Draft Creation",
            description = "Create a Gmail draft to $to with subject '$subject'?",
            badgeLabel = "GMAIL • WRITE",
            onConfirm = {
                _isGoogleOperationLoading.value = true
                val res = googleWorkspaceClient.createGmailDraftConfirmed(to, subject, body)
                _isGoogleOperationLoading.value = false
                _statusBannerMessage.value = res.fold(
                    onSuccess = { it },
                    onFailure = { "Gmail draft failed: ${it.localizedMessage}" }
                )
            }
        )
    }

    fun loadGoogleContacts(filter: String = "") {
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            val res = googleWorkspaceClient.searchGoogleContacts(filter)
            _isGoogleOperationLoading.value = false
            res.fold(
                onSuccess = { list ->
                    _googleContacts.value = list
                    _statusBannerMessage.value = "Loaded ${list.size} Google Contacts."
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Contacts lookup failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun loadYouTubePlaylists() {
        viewModelScope.launch {
            _isGoogleOperationLoading.value = true
            val res = googleWorkspaceClient.listYouTubePlaylists()
            _isGoogleOperationLoading.value = false
            res.fold(
                onSuccess = { list ->
                    _youtubePlaylists.value = list
                    _statusBannerMessage.value = "Loaded ${list.size} YouTube playlists."
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "YouTube fetch failed: ${err.localizedMessage}"
                }
            )
        }
    }

    // =========================================================================
    // SECTION 31: UNLIMITED CUSTOM MCP SERVER MANAGEMENT & ORCHESTRATION
    // =========================================================================

    fun saveMcpServerConfig(
        existingId: Long = 0L,
        name: String,
        endpointUrl: String,
        transport: McpTransportType,
        authType: McpAuthType,
        customAuthHeaderName: String,
        secretToken: String,
        timeoutSeconds: Int,
        isEnabled: Boolean,
        requireConfirmationForWrites: Boolean
    ) {
        val validationError = mcpClientEngine.validateServerConfig(name, endpointUrl, transport)
        if (validationError != null) {
            _statusBannerMessage.value = validationError
            return
        }
        viewModelScope.launch {
            val entity = McpServerEntity(
                id = existingId,
                name = name.trim(),
                endpointUrl = endpointUrl.trim(),
                transportType = transport.name,
                authType = authType.name,
                customAuthHeaderName = customAuthHeaderName.trim(),
                timeoutSeconds = timeoutSeconds.coerceIn(5, 120),
                isEnabled = isEnabled,
                requireConfirmationForWrites = requireConfirmationForWrites,
                connectionStatus = if (isEnabled) "DISCONNECTED" else "DISABLED",
                statusMessage = if (transport == McpTransportType.REMOTE_STDIO_GATEWAY) {
                    "Configured via Remote STDIO HTTP Bridge. Tap Connect & Discover Tools to verify."
                } else {
                    "Saved. Tap Connect & Discover Tools to test MCP handshake."
                }
            )
            val savedId = if (existingId == 0L) {
                dao.insertMcpServer(entity)
            } else {
                dao.updateMcpServer(entity)
                existingId
            }
            if (secretToken.isNotBlank()) {
                secureVault.setMcpServerSecret(savedId, secretToken.trim())
            } else if (authType == McpAuthType.NONE) {
                secureVault.removeMcpServerSecret(savedId)
            }
            _statusBannerMessage.value = "Saved MCP server '${entity.name}'."
        }
    }

    fun testAndConnectMcpServer(server: McpServerEntity) {
        viewModelScope.launch {
            _statusBannerMessage.value = "Connecting to MCP server '${server.name}'..."
            val outcome = mcpClientEngine.connectAndDiscoverTools(server)
            _statusBannerMessage.value = outcome.message
        }
    }

    fun testAllEnabledMcpServers() {
        viewModelScope.launch {
            val enabledList = dao.getAllMcpServers().filter { it.isEnabled }
            if (enabledList.isEmpty()) {
                _statusBannerMessage.value = "No enabled MCP servers to test."
                return@launch
            }
            _statusBannerMessage.value = "Testing ${enabledList.size} enabled MCP server(s)..."
            var okCount = 0
            for (srv in enabledList) {
                val res = mcpClientEngine.connectAndDiscoverTools(srv)
                if (res.success) okCount++
            }
            _statusBannerMessage.value = "MCP connectivity check finished: $okCount/${enabledList.size} connected."
        }
    }

    fun toggleMcpServerEnabled(server: McpServerEntity, enabled: Boolean) {
        viewModelScope.launch {
            dao.updateMcpServer(
                server.copy(
                    isEnabled = enabled,
                    connectionStatus = if (enabled) server.connectionStatus else "DISABLED",
                    statusMessage = if (enabled) "Enabled by user" else "Disabled by user"
                )
            )
            _statusBannerMessage.value = if (enabled) {
                "Enabled MCP server '${server.name}'."
            } else {
                "Disabled MCP server '${server.name}'."
            }
        }
    }

    fun disconnectMcpServer(server: McpServerEntity) {
        viewModelScope.launch {
            dao.updateMcpServer(
                server.copy(
                    connectionStatus = "DISCONNECTED",
                    statusMessage = "Disconnected by user."
                )
            )
            _statusBannerMessage.value = "Disconnected MCP server '${server.name}'."
        }
    }

    fun deleteMcpServer(server: McpServerEntity) {
        viewModelScope.launch {
            secureVault.removeMcpServerSecret(server.id)
            dao.deleteMcpToolsForServer(server.id)
            dao.deleteMcpServer(server.id)
            _statusBannerMessage.value = "Deleted MCP server '${server.name}' and its stored credentials."
        }
    }

    fun emergencyDisconnectAllMcpServers() {
        viewModelScope.launch {
            mcpClientEngine.emergencyDisconnectAll()
            _statusBannerMessage.value = "EMERGENCY STOP: All MCP servers disconnected and disabled immediately."
        }
    }

    fun toggleMcpToolApproved(tool: McpToolEntity, approved: Boolean) {
        viewModelScope.launch {
            dao.updateMcpTool(tool.copy(isApproved = approved))
            _statusBannerMessage.value = "Tool '${tool.toolName}' approval set to $approved."
        }
    }

    fun requestInvokeMcpTool(tool: McpToolEntity, argumentsJson: String) {
        viewModelScope.launch {
            val server = dao.getMcpServerById(tool.serverId)
            if (server == null) {
                _statusBannerMessage.value = "Parent MCP server not found."
                return@launch
            }
            val needsConfirm = tool.safetyClassification == "DESTRUCTIVE" ||
                (tool.safetyClassification == "WRITE" && server.requireConfirmationForWrites) ||
                tool.hasPromptInjectionWarning

            if (needsConfirm) {
                _pendingOperationConfirmation.value = PendingConfirmationDialog(
                    title = "Confirm MCP Tool Execution: ${tool.toolName}",
                    description = "Server: ${server.name}\nClassification: ${tool.safetyClassification}\nArguments: $argumentsJson\n\nAllow JARVIS to execute this MCP tool?",
                    badgeLabel = "MCP • ${tool.safetyClassification}",
                    onConfirm = {
                        executeMcpToolConfirmed(server, tool, argumentsJson)
                    }
                )
            } else {
                executeMcpToolConfirmed(server, tool, argumentsJson)
            }
        }
    }

    private suspend fun executeMcpToolConfirmed(
        server: McpServerEntity,
        tool: McpToolEntity,
        argumentsJson: String
    ) {
        val argsObj = try {
            if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
        } catch (e: Exception) {
            _statusBannerMessage.value = "Invalid JSON arguments: ${e.localizedMessage}"
            return
        }
        _statusBannerMessage.value = "Invoking MCP tool '${tool.toolName}' on ${server.name}..."
        val res = mcpClientEngine.callTool(server, tool, argsObj)
        _mcpLatestToolOutput.value = res.outputText
        _statusBannerMessage.value = if (res.success) {
            "MCP tool '${tool.toolName}' succeeded (${res.durationMs}ms)."
        } else {
            "MCP tool '${tool.toolName}' failed: ${res.outputText}"
        }
    }

    fun exportMcpServersConfig(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = mcpClientEngine.exportServersJson()
            onExportReady(json)
        }
    }

    fun importMcpServersConfig(rawJson: String) {
        viewModelScope.launch {
            val res = mcpClientEngine.importServersJson(rawJson)
            _statusBannerMessage.value = res.fold(
                onSuccess = { count -> "Imported $count MCP server configuration(s) (disabled by default until you review & enable them)." },
                onFailure = { err -> "MCP JSON import failed: ${err.localizedMessage}" }
            )
        }
    }

    fun clearMcpAuditLogs() {
        viewModelScope.launch {
            dao.clearMcpAuditLogs()
            _statusBannerMessage.value = "Cleared MCP security audit logs."
        }
    }

    // =========================================================================
    // SETTINGS MUTATORS (SECTIONS 1-28)
    // =========================================================================

    fun updateLanguage(lang: JarvisLanguage) {
        secureVault.language = lang
        _selectedLanguage.value = lang
        _statusBannerMessage.value = "Language preference set to ${lang.label}."
    }

    fun updateSelectedModel(model: GeminiModelOption) {
        secureVault.selectedModel = model
        _selectedModel.value = model
        _activeProviderBadge.value = aiBrain.getActiveProviderBadge()
    }

    fun saveCustomGeminiKey(key: String, useCustom: Boolean) {
        secureVault.customGeminiApiKey = key.trim()
        secureVault.useCustomGeminiKey = useCustom && key.isNotBlank()
        _activeProviderBadge.value = aiBrain.getActiveProviderBadge()
        _statusBannerMessage.value = if (secureVault.useCustomGeminiKey) {
            "Custom Gemini API key encrypted in Android Keystore Vault."
        } else {
            "Using provisioned Firebase AI Logic (App Check Attested)."
        }
    }

    fun removeCustomGeminiKey() {
        secureVault.customGeminiApiKey = ""
        secureVault.useCustomGeminiKey = false
        _activeProviderBadge.value = aiBrain.getActiveProviderBadge()
        _geminiTestState.value = ConnectionTestState.Idle
        _statusBannerMessage.value = "Removed custom Gemini API key. Reverted to Firebase AI Logic."
    }

    fun testGeminiConnection(candidateKey: String? = null) {
        viewModelScope.launch {
            _geminiTestState.value = ConnectionTestState.Testing
            val res = aiBrain.testGeminiConnection(candidateKey)
            _geminiTestState.value = res.fold(
                onSuccess = { ConnectionTestState.Success(it) },
                onFailure = { ConnectionTestState.Error(it.localizedMessage ?: "Connection test failed.") }
            )
        }
    }

    fun saveElevenLabsConfig(apiKey: String, voice: ElevenLabsVoiceOption, enabled: Boolean) {
        secureVault.elevenLabsApiKey = apiKey.trim()
        secureVault.elevenLabsVoice = voice
        secureVault.isElevenLabsEnabled = enabled && apiKey.isNotBlank()
        _elevenLabsEnabled.value = secureVault.isElevenLabsEnabled
        _selectedElevenVoice.value = voice
        voiceManager.refreshVoiceProviderLabel()
        _statusBannerMessage.value = if (secureVault.isElevenLabsEnabled) {
            "ElevenLabs voice (${voice.displayName}) saved to encrypted Keystore Vault."
        } else {
            "Using Android System Text-to-Speech."
        }
    }

    fun removeElevenLabsKey() {
        secureVault.elevenLabsApiKey = ""
        secureVault.isElevenLabsEnabled = false
        _elevenLabsEnabled.value = false
        _elevenLabsTestState.value = ConnectionTestState.Idle
        voiceManager.refreshVoiceProviderLabel()
        _statusBannerMessage.value = "Removed ElevenLabs key. Android System TTS is active."
    }

    fun testElevenLabsConnection(candidateKey: String) {
        viewModelScope.launch {
            _elevenLabsTestState.value = ConnectionTestState.Testing
            val res = voiceManager.testElevenLabsConnection(candidateKey)
            _elevenLabsTestState.value = res.fold(
                onSuccess = { ConnectionTestState.Success(it) },
                onFailure = { ConnectionTestState.Error(it.localizedMessage ?: "ElevenLabs connection failed.") }
            )
        }
    }

    fun saveSecondaryProviderConfig(name: String, apiKey: String, enabled: Boolean) {
        secureVault.secondaryAiName = name.ifBlank { "OpenRouter Fallback" }
        secureVault.secondaryAiApiKey = apiKey.trim()
        secureVault.isSecondaryAiEnabled = enabled && apiKey.isNotBlank()
        _statusBannerMessage.value = if (secureVault.isSecondaryAiEnabled) {
            "Fallback provider ${secureVault.secondaryAiName} enabled."
        } else {
            "Secondary fallback provider disabled."
        }
    }

    fun testSecondaryProviderConnection(apiKey: String) {
        viewModelScope.launch {
            _secondaryAiTestState.value = ConnectionTestState.Testing
            val res = aiBrain.testSecondaryProviderConnection(apiKey)
            _secondaryAiTestState.value = res.fold(
                onSuccess = { ConnectionTestState.Success(it) },
                onFailure = { ConnectionTestState.Error(it.localizedMessage ?: "Secondary provider test failed.") }
            )
        }
    }

    fun updateSpeechSettings(rate: Float, pitch: Float, autoSpeakEnabled: Boolean) {
        secureVault.speechRate = rate
        secureVault.speechPitch = pitch
        secureVault.autoSpeakResponses = autoSpeakEnabled
        _speechRate.value = secureVault.speechRate
        _speechPitch.value = secureVault.speechPitch
        _autoSpeak.value = autoSpeakEnabled
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        if (enabled) {
            val hasMicPerm = ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasMicPerm) {
                secureVault.isWakeWordEnabled = false
                _wakeWordEnabled.value = false
                _statusBannerMessage.value = "Grant Microphone permission first (tap the Mic button or complete Setup Wizard) before enabling \"Hey JARVIS\" wake-word detection."
                return
            }
        }
        secureVault.isWakeWordEnabled = enabled
        _wakeWordEnabled.value = enabled
        voiceManager.setWakeWordMonitoring(enabled)
        try {
            val svcIntent = Intent(appContext, JarvisForegroundService::class.java).apply {
                action = if (enabled) {
                    JarvisForegroundService.ACTION_START_WAKE_SERVICE
                } else {
                    JarvisForegroundService.ACTION_STOP_SERVICE
                }
            }
            if (enabled) {
                ContextCompat.startForegroundService(appContext, svcIntent)
            } else {
                appContext.stopService(Intent(appContext, JarvisForegroundService::class.java))
            }
        } catch (e: Exception) {
            Log.w("JarvisViewModel", "Foreground service toggle note: ${e.localizedMessage}")
        }
    }

    fun stopAllVoiceAndListening() {
        voiceManager.stopAllListening()
        voiceManager.stopSpeaking()
        if (_isGeminiLiveSessionActive.value) {
            toggleGeminiLiveVoiceSession()
        }
        if (_wakeWordEnabled.value) {
            setWakeWordEnabled(false)
        }
        _statusBannerMessage.value = "Stopped all microphone listening and speech output."
    }

    fun toggleGeminiLiveVoiceSession() {
        viewModelScope.launch {
            if (_isGeminiLiveSessionActive.value) {
                aiBrain.stopGeminiLiveAudioSession()
                _isGeminiLiveSessionActive.value = false
                voiceManager.setGeminiLiveStreamingState(false)
                _statusBannerMessage.value = "Gemini 3.8 Live Voice session ended."
            } else {
                voiceManager.stopAllListening()
                voiceManager.stopSpeaking()
                _statusBannerMessage.value = "Connecting to Gemini 3.8 Live API..."
                val result = aiBrain.startGeminiLiveAudioSession()
                result.fold(
                    onSuccess = { msg ->
                        _isGeminiLiveSessionActive.value = true
                        voiceManager.setGeminiLiveStreamingState(true)
                        _statusBannerMessage.value = msg
                    },
                    onFailure = { err ->
                        _isGeminiLiveSessionActive.value = false
                        voiceManager.setGeminiLiveStreamingState(false)
                        _statusBannerMessage.value = "Gemini Live Voice failed: ${err.localizedMessage}"
                    }
                )
            }
        }
    }

    fun toggleFloatingOrbOverlay() {
        if (!Settings.canDrawOverlays(appContext)) {
            controlExecutor.openSystemSettings("overlay")
            _statusBannerMessage.value = "Please grant 'Display over other apps' permission to enable the Floating JARVIS Orb."
            return
        }
        val intent = Intent(appContext, FloatingOrbService::class.java)
        if (isFloatingOrbRunning.value) {
            appContext.stopService(intent)
            _statusBannerMessage.value = "Floating JARVIS Orb closed."
        } else {
            appContext.startService(intent)
            _statusBannerMessage.value = "Floating JARVIS Orb launched! Drag or tap the orb over any app."
        }
    }

    fun onScreenCapturePermissionResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            _statusBannerMessage.value = "Screen sharing cancelled by user."
            return
        }
        try {
            val fgIntent = Intent(appContext, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_START_SCREEN_SHARE
                putExtra(JarvisForegroundService.EXTRA_RESULT_CODE, resultCode)
                putExtra(JarvisForegroundService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(appContext, fgIntent)
            _statusBannerMessage.value = "Live Screen Sharing is ACTIVE. Ask JARVIS anything about what is on your screen, or tap Stop Sharing anytime."
        } catch (e: Exception) {
            _statusBannerMessage.value = "Could not start screen sharing service: ${e.localizedMessage}"
        }
    }

    fun stopScreenSharing() {
        JarvisServiceRuntime.stopMediaProjectionSession()
        try {
            if (!_wakeWordEnabled.value) {
                appContext.stopService(Intent(appContext, JarvisForegroundService::class.java))
            }
        } catch (_: Exception) {}
        _statusBannerMessage.value = "Screen sharing stopped immediately."
    }

    fun attachUserSelectedFile(uri: Uri) {
        viewModelScope.launch {
            val res = controlExecutor.loadUserSelectedFile(uri)
            res.fold(
                onSuccess = { payload ->
                    _selectedFilePayload.value = payload
                    _statusBannerMessage.value = "Attached '${payload.displayName}' (${payload.mimeType}). It will be sent to ${aiBrain.getActiveProviderBadge()} with your next question."
                },
                onFailure = { err ->
                    _statusBannerMessage.value = err.localizedMessage ?: "Failed to load selected file."
                }
            )
        }
    }

    fun clearAttachedFile() {
        _selectedFilePayload.value = null
    }

    /**
     * Intercepts voice/text commands for Google Workspace (Drive, Calendar, Gmail, Contacts, YouTube)
     * and Custom MCP Servers before falling back to general Gemini inference.
     */
    private suspend fun tryHandleIntegrationOrMcpCommand(
        prompt: String,
        sessionId: Long,
        fromVoice: Boolean
    ): Boolean {
        val lower = prompt.lowercase().trim()

        // A. MCP Server Voice / Text Commands
        if (lower.contains("connected mcp") || lower.contains("list mcp") || lower.contains("show my mcp")) {
            val servers = dao.getAllMcpServers()
            val reply = if (servers.isEmpty()) {
                "You currently have no MCP servers configured. Open Settings → MCP Server Manager to add one."
            } else {
                buildString {
                    appendLine("### Configured MCP Servers (${servers.size})")
                    servers.forEach { s ->
                        appendLine("- **${s.name}** (${s.transportType}) — Status: `${s.connectionStatus}` • Enabled: `${s.isEnabled}` (${s.lastToolCount} tools)")
                    }
                }
            }
            emitAssistantReply(sessionId, reply, "JARVIS MCP Router", fromVoice)
            return true
        }

        if (lower.contains("available mcp tools") || lower.contains("show mcp tools") || lower == "find available tools") {
            val tools = dao.getApprovedMcpTools()
            val reply = if (tools.isEmpty()) {
                "No approved MCP tools are currently active. Connect an MCP server in Settings → MCP Server Manager to discover tools."
            } else {
                buildString {
                    appendLine("### Active & Approved MCP Tools (${tools.size})")
                    tools.forEach { t ->
                        appendLine("- **${t.toolName}** (Server: *${t.serverName}*, Safety: `${t.safetyClassification}`): ${t.description}")
                    }
                }
            }
            emitAssistantReply(sessionId, reply, "JARVIS MCP Router", fromVoice)
            return true
        }

        if (lower.contains("test all connected mcp") || lower.contains("test all mcp")) {
            testAllEnabledMcpServers()
            emitAssistantReply(
                sessionId,
                "Testing all enabled MCP servers now. Check the status banner or MCP Server Manager for live handshake results.",
                "JARVIS MCP Router",
                fromVoice
            )
            return true
        }

        if (lower.startsWith("disable ") && lower.contains("mcp")) {
            val targetName = lower.removePrefix("disable ").replace("mcp server", "").replace("mcp", "").trim()
            val match = dao.getAllMcpServers().firstOrNull { it.name.lowercase().contains(targetName) }
            if (match != null) {
                toggleMcpServerEnabled(match, false)
                emitAssistantReply(sessionId, "Disabled MCP server **${match.name}**.", "JARVIS MCP Router", fromVoice)
                return true
            }
        }

        // B. Google Drive Voice / Text Commands
        if (lower.contains("summarize my selected google drive") || lower.contains("summarize selected drive")) {
            val selected = _selectedDriveFile.value
            if (selected == null) {
                emitAssistantReply(
                    sessionId,
                    "No Google Drive file is currently selected. Search your Drive files first (e.g., *\"Find my PDF in Drive\"*) or select a file in **Control → Google & Apps**.",
                    "Google Drive Integration",
                    fromVoice
                )
                return true
            }
            val textRes = googleWorkspaceClient.readDriveDocumentText(selected)
            if (textRes.isFailure) {
                emitAssistantReply(
                    sessionId,
                    "Could not read '${selected.name}' from Google Drive: ${textRes.exceptionOrNull()?.localizedMessage}",
                    "Google Drive Integration",
                    fromVoice
                )
                return true
            }
            // Let Gemini summarize the fetched Drive content by continuing with extra text
            val docText = textRes.getOrThrow()
            streamGeminiSummaryForExternalContent(
                sessionId = sessionId,
                userPrompt = "Summarize my selected Google Drive document '${selected.name}':\n\n$docText",
                providerBadge = "Google Drive + ${aiBrain.getActiveProviderBadge()}",
                fromVoice = fromVoice
            )
            return true
        }

        if (lower.contains("create a folder in drive") || lower.contains("create folder in drive") || lower.contains("create drive folder")) {
            val folderName = prompt.replace(Regex("(?i).*create (a )?(drive )?folder( in drive)?( named| called)?"), "").trim()
                .ifBlank { "JARVIS Workspace Folder" }
            val res = googleWorkspaceClient.createDriveFolder(folderName)
            val reply = res.fold(
                onSuccess = { "Created folder **${it.name}** in your Google Drive." },
                onFailure = { "Could not create folder in Google Drive: ${it.localizedMessage}" }
            )
            emitAssistantReply(sessionId, reply, "Google Drive API v3", fromVoice)
            return true
        }

        if ((lower.contains("find") || lower.contains("search") || lower.contains("list")) &&
            (lower.contains("drive") || lower.contains("pdf in drive"))
        ) {
            val onlyPdf = lower.contains("pdf")
            val wantsSummary = lower.contains("summarize")
            val extractedQuery = prompt
                .replace(Regex("(?i)(hey jarvis,?\\s*)?(please\\s+)?(find|search|list)\\s+(my\\s+)?(pdf|file|files|document|project file)s?\\s*(in\\s+google\\s+drive|in\\s+drive)?(named|called|for)?"), "")
                .replace(Regex("(?i)and summarize( it)?"), "")
                .trim()

            val res = googleWorkspaceClient.searchAndListDriveFiles(
                nameQuery = extractedQuery,
                mimeTypeFilter = if (onlyPdf) "application/pdf" else null
            )
            res.fold(
                onSuccess = { files ->
                    _driveFiles.value = files
                    if (files.isEmpty()) {
                        emitAssistantReply(
                            sessionId,
                            "No matching files found in your Google Drive" +
                                if (extractedQuery.isNotBlank()) " for query '$extractedQuery'." else ".",
                            "Google Drive API v3",
                            fromVoice
                        )
                    } else if (files.size == 1 && wantsSummary) {
                        val single = files.first()
                        _selectedDriveFile.value = single
                        val docRes = googleWorkspaceClient.readDriveDocumentText(single)
                        docRes.fold(
                            onSuccess = { text ->
                                streamGeminiSummaryForExternalContent(
                                    sessionId = sessionId,
                                    userPrompt = "I found '${single.name}' in Google Drive. Please summarize it concisely:\n\n$text",
                                    providerBadge = "Google Drive + ${aiBrain.getActiveProviderBadge()}",
                                    fromVoice = fromVoice
                                )
                            },
                            onFailure = { err ->
                                emitAssistantReply(
                                    sessionId,
                                    "Found **${single.name}** in Drive, but could not extract text: ${err.localizedMessage}",
                                    "Google Drive API v3",
                                    fromVoice
                                )
                            }
                        )
                    } else {
                        if (files.size > 1) {
                            _driveDisambiguationFiles.value = files.take(6)
                        } else {
                            _selectedDriveFile.value = files.first()
                        }
                        val listMd = buildString {
                            appendLine("Found **${files.size}** file(s) in your Google Drive:")
                            files.take(8).forEachIndexed { idx, f ->
                                appendLine("${idx + 1}. **${f.name}** (`${f.mimeType}`) — ${if (f.isFolder) "Folder" else "${f.sizeBytes / 1024} KB"}")
                            }
                            if (files.size > 1) {
                                appendLine("\nMultiple files matched. Tap a file in **Control → Google & Apps** or tell me which file to select and summarize.")
                            } else {
                                appendLine("\nSelected **${files.first().name}**. Say *\"Summarize my selected Google Drive document\"* to read and summarize it.")
                            }
                        }
                        emitAssistantReply(sessionId, listMd, "Google Drive API v3", fromVoice)
                    }
                },
                onFailure = { err ->
                    emitAssistantReply(
                        sessionId,
                        "Google Drive search requires an active Google OAuth connection: ${err.localizedMessage}",
                        "Google Drive API v3",
                        fromVoice
                    )
                }
            )
            return true
        }

        // C. Google Calendar Voice / Text Commands
        if (lower.contains("upcoming calendar") || lower.contains("calendar events") || lower.contains("my schedule in calendar")) {
            val res = googleWorkspaceClient.listUpcomingCalendarEvents()
            val reply = res.fold(
                onSuccess = { events ->
                    _calendarEvents.value = events
                    if (events.isEmpty()) {
                        "You have no upcoming events on your primary Google Calendar."
                    } else {
                        buildString {
                            appendLine("### Upcoming Google Calendar Events (${events.size})")
                            events.take(8).forEach { ev ->
                                appendLine("- **${ev.title}** — `${ev.startTime}`" + if (ev.location.isNotBlank()) " (${ev.location})" else "")
                            }
                        }
                    }
                },
                onFailure = { "Could not load Google Calendar events: ${it.localizedMessage}" }
            )
            emitAssistantReply(sessionId, reply, "Google Calendar API v3", fromVoice)
            return true
        }

        // D. Gmail Voice / Text Commands
        if (lower.contains("latest email") || lower.contains("search gmail") || lower.contains("email from ")) {
            val q = if (lower.contains("email from ")) {
                val sender = prompt.substringAfterLast("from ", "").trim()
                if (sender.isNotBlank()) "from:$sender" else ""
            } else {
                ""
            }
            val res = googleWorkspaceClient.searchGmailMessages(q)
            val reply = res.fold(
                onSuccess = { msgs ->
                    _gmailMessages.value = msgs
                    if (msgs.isEmpty()) {
                        "No matching Gmail messages found" + if (q.isNotBlank()) " for `$q`." else "."
                    } else {
                        buildString {
                            appendLine("### Latest Gmail Messages (${msgs.size})")
                            msgs.take(5).forEach { m ->
                                appendLine("- **${m.subject}** (From: *${m.from}* • ${m.date})\n  ${m.snippet}")
                            }
                        }
                    }
                },
                onFailure = { "Could not search Gmail: ${it.localizedMessage}" }
            )
            emitAssistantReply(sessionId, reply, "Gmail API v1", fromVoice)
            return true
        }

        return false
    }

    private suspend fun emitAssistantReply(
        sessionId: Long,
        replyText: String,
        providerBadge: String,
        fromVoice: Boolean
    ) {
        _latestAssistantReply.value = replyText
        _activeProviderBadge.value = providerBadge
        dao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = "model",
                content = replyText,
                providerUsed = providerBadge
            )
        )
        if (_autoSpeak.value || fromVoice) {
            voiceManager.speak(replyText)
        }
    }

    private suspend fun streamGeminiSummaryForExternalContent(
        sessionId: Long,
        userPrompt: String,
        providerBadge: String,
        fromVoice: Boolean
    ) {
        _isGenerating.value = true
        _streamingPartialReply.value = ""
        voiceManager.setProcessingState()
        val history = dao.getMessagesForSession(sessionId).dropLast(1)
        val approvedMemories = dao.getApprovedMemories()
        var finalReply = ""
        try {
            aiBrain.streamAssistantResponse(
                userPrompt = userPrompt,
                history = history,
                approvedMemories = approvedMemories,
                attachedBitmap = null,
                extraDocumentText = null,
                webResults = emptyList()
            ).collect { partial ->
                finalReply = partial.text
                _streamingPartialReply.value = finalReply
                _latestAssistantReply.value = finalReply
                _activeProviderBadge.value = providerBadge
            }
            dao.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = "model",
                    content = finalReply,
                    providerUsed = providerBadge
                )
            )
            if (_autoSpeak.value || fromVoice) {
                voiceManager.speak(finalReply)
            }
        } finally {
            _isGenerating.value = false
            _streamingPartialReply.value = ""
        }
    }

    /**
     * Handles both voice commands and typed prompts across the Home HUD and Dedicated Chat Section.
     */
    fun handleUserPromptOrVoiceCommand(
        rawPrompt: String,
        fromVoice: Boolean = false,
        useScreenFrameIfActive: Boolean = true
    ) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank()) return

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            val sessionId = ensureActiveSessionId()
            val attachment = _selectedFilePayload.value
            val screenBitmap = if (useScreenFrameIfActive && isScreenSharingActive.value) {
                latestScreenBitmap.value
            } else {
                null
            }
            val effectiveBitmap = attachment?.bitmap ?: screenBitmap
            val attachmentLabel = when {
                attachment != null -> attachment.displayName
                screenBitmap != null -> "Live Authorized Screen Capture Frame"
                else -> null
            }

            // Save user message into chat history
            dao.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = "user",
                    content = prompt,
                    attachmentName = attachmentLabel,
                    attachmentType = attachment?.mimeType ?: if (screenBitmap != null) "image/screen" else null,
                    providerUsed = if (fromVoice) "Voice Input" else "Text Input"
                )
            )

            // 1. Check if this is an immediate local/offline phone command (when no file/screen attachment is attached)
            if (attachment == null && screenBitmap == null) {
                val localOutcome = controlExecutor.tryHandleLocalCommand(prompt)
                if (localOutcome.handledOffline) {
                    localOutcome.pendingSensitiveAction?.let {
                        _pendingSensitiveAction.value = it
                    }
                    _latestAssistantReply.value = localOutcome.spokenResponse
                    dao.insertMessage(
                        ChatMessageEntity(
                            sessionId = sessionId,
                            role = "model",
                            content = localOutcome.spokenResponse,
                            providerUsed = "JARVIS Offline Device Engine"
                        )
                    )
                    if (_autoSpeak.value || fromVoice) {
                        voiceManager.speak(localOutcome.spokenResponse)
                    }
                    return@launch
                }
            }

            // 2. Check network before calling cloud services or AI
            if (!controlExecutor.isNetworkAvailable()) {
                val offlineMsg = "Device appears to be offline. Cloud AI reasoning and Google/MCP integrations require an internet connection. " +
                    "Wait for connectivity or use offline device commands (e.g., 'What time is it', 'Battery status', 'Open Settings', 'Open Calculator')."
                _latestAssistantReply.value = offlineMsg
                dao.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = "model",
                        content = offlineMsg,
                        providerUsed = "Offline Guard",
                        isError = true
                    )
                )
                if (_autoSpeak.value || fromVoice) {
                    voiceManager.speak(offlineMsg)
                }
                return@launch
            }

            // 2.5 Check if this command targets Google Workspace (Drive, Gmail, Calendar, Contacts) or MCP Servers
            if (attachment == null && screenBitmap == null) {
                if (tryHandleIntegrationOrMcpCommand(prompt, sessionId, fromVoice)) {
                    return@launch
                }
            }

            // 3. Optional Web Research enrichment if user asks to search/latest/news/research
            var webResults: List<WebSearchResultItem> = emptyList()
            val lower = prompt.lowercase()
            if (_webSearchEnabled.value && (
                    lower.contains("search ") ||
                        lower.contains("lookup ") ||
                        lower.contains("who is ") ||
                        lower.contains("what is ") ||
                        lower.contains("wikipedia") ||
                        lower.contains("research ")
                    )
            ) {
                controlExecutor.searchVerifiedWebSources(prompt).onSuccess { found ->
                    webResults = found
                    _latestWebSources.value = found
                }
            }

            // Include active MCP tool catalog in context if any MCP tools are approved
            val approvedTools = dao.getApprovedMcpTools()
            val mcpContextBlock = if (approvedTools.isNotEmpty()) {
                buildString {
                    appendLine("Active Approved MCP Tools (${approvedTools.size}):")
                    approvedTools.take(15).forEach { t ->
                        appendLine("- ${t.serverName} :: ${t.toolName} [${t.safetyClassification}]: ${t.description}")
                    }
                    if (!attachment?.extractedText.isNullOrBlank()) {
                        appendLine("\nAttached Document Content:\n${attachment?.extractedText}")
                    }
                }
            } else {
                attachment?.extractedText
            }

            // 4. Stream AI response from Gemini (or configured provider)
            _isGenerating.value = true
            _streamingPartialReply.value = ""
            voiceManager.setProcessingState()

            val history = dao.getMessagesForSession(sessionId).dropLast(1)
            val approvedMemories = dao.getApprovedMemories()
            var finalReply = ""
            var finalProvider = aiBrain.getActiveProviderBadge()

            try {
                aiBrain.streamAssistantResponse(
                    userPrompt = prompt,
                    history = history,
                    approvedMemories = approvedMemories,
                    attachedBitmap = effectiveBitmap,
                    extraDocumentText = mcpContextBlock,
                    webResults = webResults
                ).collect { partial ->
                    finalReply = partial.text
                    finalProvider = partial.providerLabel
                    _streamingPartialReply.value = finalReply
                    _latestAssistantReply.value = finalReply
                    _activeProviderBadge.value = finalProvider
                }

                // Clear one-shot file attachment after sending
                _selectedFilePayload.value = null

                dao.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = "model",
                        content = finalReply,
                        providerUsed = finalProvider
                    )
                )

                // Check if user asked JARVIS to remember something ("remember that...", "याद रखना...")
                detectAndQueueMemoryApproval(prompt)

                if (_autoSpeak.value || fromVoice) {
                    voiceManager.speak(finalReply)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errText = "Unable to complete AI request: ${e.localizedMessage ?: "Unknown error"}. Check your internet connection or provider configuration in Settings."
                _latestAssistantReply.value = errText
                dao.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = "model",
                        content = errText,
                        providerUsed = finalProvider,
                        isError = true
                    )
                )
            } finally {
                _isGenerating.value = false
                _streamingPartialReply.value = ""
            }
        }
    }

    private suspend fun detectAndQueueMemoryApproval(userPrompt: String) {
        if (!_memoryEnabled.value) return
        val lower = userPrompt.lowercase()
        if (lower.startsWith("remember ") || lower.contains("remember that ") || lower.contains("याद रख")) {
            val fact = userPrompt
                .replace(Regex("^(please\\s+)?remember(\\s+that)?\\s+", RegexOption.IGNORE_CASE), "")
                .trim()
            if (fact.length >= 4) {
                dao.insertMemory(
                    MemoryEntity(
                        category = "FACT",
                        keyTitle = "User Memory Request",
                        content = fact,
                        isApproved = false
                    )
                )
                _statusBannerMessage.value = "JARVIS queued a new memory for your approval in the Memory tab."
            }
        }
    }

    fun stopActiveGeneration() {
        activeGenerationJob?.cancel()
        _isGenerating.value = false
        _streamingPartialReply.value = ""
        voiceManager.stopSpeaking()
        _statusBannerMessage.value = "Stopped response generation."
    }

    fun retryLastMessage() {
        viewModelScope.launch {
            val messages = activeSessionMessages.value
            val lastUser = messages.lastOrNull { it.role == "user" } ?: return@launch
            handleUserPromptOrVoiceCommand(lastUser.content, fromVoice = false)
        }
    }

    // Chat Session Management (New, Select, Rename, Delete)
    private suspend fun ensureActiveSessionId(): Long {
        val current = _activeSessionId.value
        if (current != null) return current
        val newId = dao.insertSession(ChatSessionEntity(title = "New Conversation"))
        _activeSessionId.value = newId
        return newId
    }

    fun createNewChatSession(title: String = "New Conversation") {
        viewModelScope.launch {
            val clean = title.trim().ifBlank { "New Conversation" }
            val id = dao.insertSession(ChatSessionEntity(title = clean))
            _activeSessionId.value = id
        }
    }

    fun selectChatSession(sessionId: Long) {
        _activeSessionId.value = sessionId
    }

    fun renameChatSession(session: ChatSessionEntity, newTitle: String) {
        viewModelScope.launch {
            val clean = newTitle.trim()
            if (clean.isNotBlank()) {
                dao.updateSession(session.copy(title = clean, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun deleteChatSession(sessionId: Long) {
        viewModelScope.launch {
            dao.deleteMessagesForSession(sessionId)
            dao.deleteSession(sessionId)
            val remaining = chatSessions.value.filter { it.id != sessionId }
            if (_activeSessionId.value == sessionId) {
                if (remaining.isNotEmpty()) {
                    _activeSessionId.value = remaining.first().id
                } else {
                    val freshId = dao.insertSession(ChatSessionEntity(title = "Primary JARVIS Console"))
                    _activeSessionId.value = freshId
                }
            }
        }
    }

    // Multi-Step Task Planning & Execution Engine (Section 12)
    fun planMultiStepTask(goal: String) {
        val cleanGoal = goal.trim()
        if (cleanGoal.isBlank()) return
        viewModelScope.launch {
            _isGenerating.value = true
            _statusBannerMessage.value = "JARVIS is breaking down your goal into verified steps..."
            val approvedMemories = dao.getApprovedMemories()
            val result = aiBrain.generateMultiStepTaskPlan(cleanGoal, approvedMemories)
            _isGenerating.value = false

            result.fold(
                onSuccess = { plan ->
                    _activeTaskPlan.value = plan
                    val stepsArr = JSONArray()
                    plan.steps.forEach { step ->
                        stepsArr.put(
                            JSONObject()
                                .put("stepNumber", step.stepNumber)
                                .put("title", step.title)
                                .put("description", step.description)
                                .put("actionType", step.actionType)
                                .put("actionTarget", step.actionTarget)
                                .put("requiresPermission", step.requiresPermission)
                                .put("requiresConfirmation", step.requiresConfirmation)
                                .put("status", step.status)
                        )
                    }
                    dao.insertTaskPlan(
                        TaskPlanEntity(
                            goal = plan.goal,
                            summary = plan.summary,
                            stepsJson = stepsArr.toString(),
                            status = "READY"
                        )
                    )
                    if (!plan.suggestedMemoryFact.isNullOrBlank() && _memoryEnabled.value) {
                        dao.insertMemory(
                            MemoryEntity(
                                category = "TASK_CONTEXT",
                                keyTitle = "Task Context (${plan.goal.take(24)})",
                                content = plan.suggestedMemoryFact,
                                isApproved = false
                            )
                        )
                    }
                    _statusBannerMessage.value = "Multi-step plan ready (${plan.steps.size} steps). Review permissions and tap Execute Plan."
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Task planning failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun executeActiveTaskPlan() {
        val plan = _activeTaskPlan.value ?: return
        if (_isTaskRunning.value) return

        activeTaskExecutionJob?.cancel()
        activeTaskExecutionJob = viewModelScope.launch {
            _isTaskRunning.value = true
            _isTaskPaused.value = false

            val updatedSteps = plan.steps.map { it.copy() }.toMutableList()

            for (index in updatedSteps.indices) {
                while (_isTaskPaused.value) {
                    delay(300L)
                }
                val step = updatedSteps[index]
                if (step.status == "COMPLETED") continue

                updatedSteps[index] = step.copy(status = "RUNNING")
                _activeTaskPlan.value = plan.copy(steps = updatedSteps.toList())
                delay(500L)

                val stepOutcome = executeSingleTaskStep(step)
                updatedSteps[index] = stepOutcome
                _activeTaskPlan.value = plan.copy(steps = updatedSteps.toList())
            }

            _isTaskRunning.value = false
            _isTaskPaused.value = false
            val completedCount = updatedSteps.count { it.status == "COMPLETED" }
            val failedCount = updatedSteps.count { it.status == "FAILED" }
            val skippedCount = updatedSteps.count { it.status == "SKIPPED" }
            _statusBannerMessage.value = "Task finished: $completedCount completed, $failedCount failed, $skippedCount awaiting confirmation/skipped."
        }
    }

    private suspend fun executeSingleTaskStep(step: TaskStepItem): TaskStepItem {
        return try {
            when (step.actionType) {
                "OPEN_APP" -> {
                    val res = controlExecutor.launchAppByName(step.actionTarget)
                    if (res.isSuccess) {
                        step.copy(status = "COMPLETED", resultNote = res.getOrThrow())
                    } else {
                        step.copy(status = "FAILED", resultNote = res.exceptionOrNull()?.localizedMessage ?: "App not found")
                    }
                }
                "SET_TIMER" -> {
                    val secs = step.actionTarget.filter { it.isDigit() }.toIntOrNull() ?: 300
                    val res = controlExecutor.setSystemTimer(secs, step.title)
                    dao.insertReminder(
                        ReminderEntity(
                            title = "${step.title} (${secs / 60}m)",
                            triggerAtMillis = System.currentTimeMillis() + secs * 1000L,
                            type = "TIMER"
                        )
                    )
                    step.copy(status = "COMPLETED", resultNote = res.getOrDefault("Timer started"))
                }
                "SET_ALARM" -> {
                    val parts = step.actionTarget.split(":")
                    val hr = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: 8
                    val min = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
                    val res = controlExecutor.setSystemAlarm(hr, min, step.title)
                    dao.insertReminder(
                        ReminderEntity(
                            title = "${step.title} (%02d:%02d)".format(hr, min),
                            triggerAtMillis = System.currentTimeMillis() + 3600_000L,
                            type = "ALARM"
                        )
                    )
                    step.copy(status = "COMPLETED", resultNote = res.getOrDefault("Alarm created"))
                }
                "OPEN_SETTINGS" -> {
                    val res = controlExecutor.openSystemSettings(step.actionTarget)
                    step.copy(status = "COMPLETED", resultNote = res.getOrDefault("Opened settings"))
                }
                "DIAL_NUMBER" -> {
                    _pendingSensitiveAction.value = PendingSensitiveAction(
                        title = "Step ${step.stepNumber}: Confirm Phone Dial",
                        description = step.description,
                        actionType = SensitiveActionType.DIAL_NUMBER,
                        targetValue = step.actionTarget
                    )
                    step.copy(status = "SKIPPED", resultNote = "Queued for user confirmation before dialing ${step.actionTarget}.")
                }
                "SHARE_TEXT" -> {
                    _pendingSensitiveAction.value = PendingSensitiveAction(
                        title = "Step ${step.stepNumber}: Confirm Share Text",
                        description = step.description,
                        actionType = SensitiveActionType.SHARE_TEXT,
                        targetValue = step.title,
                        payloadText = step.actionTarget
                    )
                    step.copy(status = "SKIPPED", resultNote = "Queued for user confirmation before sharing.")
                }
                "WEB_SEARCH" -> {
                    val searchRes = controlExecutor.searchVerifiedWebSources(step.actionTarget)
                    val items = searchRes.getOrDefault(emptyList())
                    _latestWebSources.value = items
                    step.copy(
                        status = "COMPLETED",
                        resultNote = if (items.isNotEmpty()) {
                            "Found ${items.size} verified web sources (top: ${items.first().title})."
                        } else {
                            "Search completed (no instant snippets matched)."
                        }
                    )
                }
                "SAVE_MEMORY" -> {
                    dao.insertMemory(
                        MemoryEntity(
                            category = "ROUTINE",
                            keyTitle = step.title,
                            content = step.actionTarget,
                            isApproved = false
                        )
                    )
                    step.copy(status = "COMPLETED", resultNote = "Queued in Memory Vault for your approval.")
                }
                else -> {
                    step.copy(status = "COMPLETED", resultNote = "Analyzed: ${step.description}")
                }
            }
        } catch (e: Exception) {
            step.copy(status = "FAILED", resultNote = e.localizedMessage ?: "Step failed")
        }
    }

    fun pauseOrResumeTaskPlan() {
        _isTaskPaused.value = !_isTaskPaused.value
    }

    fun cancelActiveTaskPlan() {
        activeTaskExecutionJob?.cancel()
        _isTaskRunning.value = false
        _isTaskPaused.value = false
        _activeTaskPlan.value = null
        _statusBannerMessage.value = "Task execution cancelled."
    }

    // Sensitive Action Confirmation Handlers
    fun confirmPendingSensitiveAction() {
        val action = _pendingSensitiveAction.value ?: return
        _pendingSensitiveAction.value = null
        val res = controlExecutor.executeConfirmedSensitiveAction(action)
        _statusBannerMessage.value = res.fold(
            onSuccess = { it },
            onFailure = { "Action rejected by Android: ${it.localizedMessage}" }
        )
    }

    fun dismissPendingSensitiveAction() {
        _pendingSensitiveAction.value = null
        _statusBannerMessage.value = "Sensitive action cancelled."
    }

    fun requestSensitiveAction(action: PendingSensitiveAction) {
        _pendingSensitiveAction.value = action
    }

    // Memory Vault Operations (Section 15)
    fun setMemorySystemEnabled(enabled: Boolean) {
        secureVault.isMemoryEnabled = enabled
        _memoryEnabled.value = enabled
        _statusBannerMessage.value = if (enabled) "Memory system enabled." else "Memory system paused."
    }

    fun addOrUpdateMemory(id: Long = 0, category: String, keyTitle: String, content: String, approved: Boolean = true) {
        viewModelScope.launch {
            val cleanKey = keyTitle.trim()
            val cleanContent = content.trim()
            if (cleanKey.isBlank() || cleanContent.isBlank()) return@launch
            dao.insertMemory(
                MemoryEntity(
                    id = id,
                    category = category,
                    keyTitle = cleanKey,
                    content = cleanContent,
                    isApproved = approved,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun approveMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            dao.updateMemory(memory.copy(isApproved = true, updatedAt = System.currentTimeMillis()))
            _statusBannerMessage.value = "Approved memory: '${memory.keyTitle}'."
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            dao.deleteMemory(id)
        }
    }

    // Reminders, Timers & Alarms Operations (Section 18)
    fun createReminderOrTimer(title: String, minutesFromNow: Int, type: String) {
        viewModelScope.launch {
            val cleanTitle = title.trim().ifBlank { "JARVIS $type" }
            val triggerAt = System.currentTimeMillis() + (minutesFromNow.coerceAtLeast(1) * 60_000L)
            dao.insertReminder(
                ReminderEntity(
                    title = cleanTitle,
                    triggerAtMillis = triggerAt,
                    type = type
                )
            )
            if (type == "TIMER") {
                controlExecutor.setSystemTimer(minutesFromNow * 60, cleanTitle)
            }
            _statusBannerMessage.value = "Created $type '$cleanTitle' for $minutesFromNow min from now."
        }
    }

    fun createAlarmItem(title: String, hour24: Int, minute: Int) {
        viewModelScope.launch {
            val cleanTitle = title.trim().ifBlank { "JARVIS Alarm (%02d:%02d)".format(hour24, minute) }
            dao.insertReminder(
                ReminderEntity(
                    title = cleanTitle,
                    triggerAtMillis = System.currentTimeMillis() + 3600_000L,
                    type = "ALARM"
                )
            )
            val res = controlExecutor.setSystemAlarm(hour24, minute, cleanTitle)
            _statusBannerMessage.value = res.getOrDefault("Created alarm '$cleanTitle'.")
        }
    }

    fun toggleReminderCompleted(reminder: ReminderEntity) {
        viewModelScope.launch {
            dao.updateReminder(reminder.copy(isCompleted = !reminder.isCompleted))
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            dao.deleteReminder(id)
        }
    }

    // Notification Assistant Summarizer & Reply Drafter (Section 14)
    fun summarizeActiveNotifications() {
        val excluded = secureVault.excludedNotificationPackages
        val list = capturedNotifications.value.filter { it.packageName !in excluded }
        if (list.isEmpty()) {
            _statusBannerMessage.value = "No active permitted notifications to summarize. Make sure Notification Access is enabled if you want JARVIS to read notifications."
            return
        }
        val joined = list.joinToString("\n") { "- [${it.appTitle}] ${it.text}" }
        handleUserPromptOrVoiceCommand(
            "Summarize these user-permitted notifications concisely and suggest any needed replies:\n$joined",
            fromVoice = false,
            useScreenFrameIfActive = false
        )
        _currentTab.value = JarvisNavTab.HOME
    }

    fun toggleExcludeNotificationPackage(packageName: String) {
        val current = secureVault.excludedNotificationPackages.toMutableSet()
        if (packageName in current) current.remove(packageName) else current.add(packageName)
        secureVault.excludedNotificationPackages = current
    }

    // Security & Privacy Purge (Section 21)
    fun purgeAllLocalDataAndCredentials() {
        viewModelScope.launch {
            stopAllVoiceAndListening()
            stopScreenSharing()
            dao.clearAllMessages()
            dao.clearAllSessions()
            dao.clearAllMemories()
            dao.clearAllReminders()
            dao.clearAllTaskPlans()
            dao.clearAllMcpTools()
            dao.clearAllMcpServers()
            dao.clearMcpAuditLogs()
            secureVault.clearAllCredentialsAndPreferences()
            _googleConnectedEmail.value = ""
            _googleGrantedScopes.value = emptySet()
            _driveFiles.value = emptyList()
            _calendarEvents.value = emptyList()
            _gmailMessages.value = emptyList()
            _googleContacts.value = emptyList()
            _youtubePlaylists.value = emptyList()
            _selectedLanguage.value = JarvisLanguage.AUTO
            _selectedModel.value = GeminiModelOption.GEMINI_3_8_FLASH
            _elevenLabsEnabled.value = false
            _wakeWordEnabled.value = false
            _activeProviderBadge.value = aiBrain.getActiveProviderBadge()
            ensureDefaultSessionAndSeedData()
            _statusBannerMessage.value = "Purged all local chat history, memories, reminders, MCP servers, and encrypted API credentials."
        }
    }

    override fun onCleared() {
        voiceManager.release()
        aiBrain.stopGeminiLiveAudioSession()
        super.onCleared()
    }
}
