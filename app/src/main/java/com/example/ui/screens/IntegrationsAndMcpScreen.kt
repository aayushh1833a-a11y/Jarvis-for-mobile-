package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.integrations.DriveFileItem
import com.example.mcp.McpAuthType
import com.example.mcp.McpTransportType
import com.example.ui.JarvisNavTab
import com.example.ui.JarvisViewModel
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCardNavy
import com.example.ui.theme.JarvisCoral
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PendingOperationConfirmationHost(viewModel: JarvisViewModel) {
    val pending by viewModel.pendingOperationConfirmation.collectAsState()
    val dialog = pending ?: return

    AlertDialog(
        onDismissRequest = { viewModel.dismissPendingOperationDialog() },
        containerColor = JarvisCardNavy,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Security Confirmation",
                tint = JarvisAmber
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    color = JarvisAmber.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = dialog.badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(dialog.title, style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
            }
        },
        text = {
            Text(
                text = dialog.description,
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = { viewModel.confirmPendingOperationDialog() },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCoral),
                modifier = Modifier.testTag("confirm_sensitive_integration_op_button")
            ) {
                Text("Confirm & Execute", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { viewModel.dismissPendingOperationDialog() },
                modifier = Modifier.testTag("cancel_sensitive_integration_op_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

// ============================================================================
// SECTION 30: GOOGLE & THIRD-PARTY SERVICES INTEGRATION SCREEN
// ============================================================================

@Composable
fun GoogleIntegrationsSection(viewModel: JarvisViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity

    val connectedEmail by viewModel.googleConnectedEmail.collectAsState()
    val grantedScopes by viewModel.googleGrantedScopes.collectAsState()
    val testState by viewModel.googleConnectionTestState.collectAsState()
    val pendingConsentIntent by viewModel.pendingOAuthConsentIntent.collectAsState()
    val isLoading by viewModel.isGoogleOperationLoading.collectAsState()

    val driveFiles by viewModel.driveFiles.collectAsState()
    val disambiguationFiles by viewModel.driveDisambiguationFiles.collectAsState()
    val selectedDriveFile by viewModel.selectedDriveFile.collectAsState()
    val calendarEvents by viewModel.calendarEvents.collectAsState()
    val gmailMessages by viewModel.gmailMessages.collectAsState()
    val contacts by viewModel.googleContacts.collectAsState()
    val playlists by viewModel.youtubePlaylists.collectAsState()

    var activeServiceTab by remember { mutableIntStateOf(0) }
    val serviceTabs = listOf(
        "Status & Auth",
        "Google Drive",
        "Calendar & Gmail",
        "Contacts, Photos & YouTube"
    )

    var driveSearchQuery by remember { mutableStateOf("") }
    var drivePdfOnly by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameTargetFile by remember { mutableStateOf<DriveFileItem?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var gmailSearchQuery by remember { mutableStateOf("") }
    var showGmailDraftDialog by remember { mutableStateOf(false) }
    var draftTo by remember { mutableStateOf("") }
    var draftSubject by remember { mutableStateOf("") }
    var draftBody by remember { mutableStateOf("") }

    var showManualTokenDialog by remember { mutableStateOf(false) }
    var manualTokenInput by remember { mutableStateOf("") }

    // Consent Resolution Activity Result Launcher
    val oauthConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onOAuthConsentActivityResult(result.resultCode)
    }

    LaunchedEffect(pendingConsentIntent) {
        val intent = pendingConsentIntent
        if (intent != null) {
            oauthConsentLauncher.launch(intent)
        }
    }

    // Upload File to Google Drive Picker
    val driveUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadUriToGoogleDrive(uri)
        }
    }

    // Zero-Permission Google Photos / Android Visual Media Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachUserSelectedFile(uri)
            viewModel.selectTab(JarvisNavTab.HOME)
        }
    }

    PendingOperationConfirmationHost(viewModel)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            serviceTabs.forEachIndexed { idx, title ->
                FilterChip(
                    selected = (activeServiceTab == idx),
                    onClick = { activeServiceTab = idx },
                    label = { Text(title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                        selectedLabelColor = JarvisCyan
                    ),
                    modifier = Modifier.testTag("google_service_tab_$idx")
                )
            }
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                color = JarvisCyan,
                trackColor = JarvisDeepNavy,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when (activeServiceTab) {
                0 -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                            border = BorderStroke(
                                1.dp,
                                if (connectedEmail.isNotBlank()) JarvisEmerald else JarvisCyan.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Cloud, contentDescription = null, tint = JarvisCyan)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Google OAuth 2.0 Integration Hub",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = JarvisCyan
                                            )
                                            Text(
                                                text = if (connectedEmail.isNotBlank()) {
                                                    "Connected Account: $connectedEmail"
                                                } else {
                                                    "Not Connected • Uses Official Google OAuth 2.0 (Never asks for your password)"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (connectedEmail.isNotBlank()) JarvisEmerald else JarvisTextSecondary
                                            )
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    Button(
                                        onClick = {
                                            if (activity != null) {
                                                viewModel.signInAndConnectGoogleWorkspace(activity)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                                        modifier = Modifier.testTag("connect_google_oauth_button")
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = null, tint = JarvisDeepNavy, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (connectedEmail.isNotBlank()) "Reconnect / Refresh OAuth" else "Connect Google Account",
                                            color = JarvisDeepNavy,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.testGoogleWorkspaceConnection() },
                                        modifier = Modifier.testTag("test_google_oauth_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Test Connection", color = JarvisCyan)
                                    }

                                    OutlinedButton(
                                        onClick = { showManualTokenDialog = true },
                                        modifier = Modifier.testTag("manual_oauth_token_button")
                                    ) {
                                        Text("OAuth Token Fallback", color = JarvisViolet)
                                    }

                                    if (connectedEmail.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { viewModel.disconnectGoogleWorkspace() },
                                            border = BorderStroke(1.dp, JarvisCoral),
                                            modifier = Modifier.testTag("disconnect_google_oauth_button")
                                        ) {
                                            Icon(Icons.Default.LinkOff, contentDescription = null, tint = JarvisCoral, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Disconnect", color = JarvisCoral)
                                        }
                                    }
                                }

                                ConnectionStatusDisplay(state = testState)

                                if (grantedScopes.isNotEmpty()) {
                                    Text(
                                        text = "Permissions Granted (${grantedScopes.size} OAuth Scopes):",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = JarvisCyan
                                    )
                                    grantedScopes.forEach { scope ->
                                        Text(
                                            text = "• ${scope.substringAfterLast("/")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = JarvisTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Per-Service Status Cards
                    items(viewModel.getGoogleServiceStatusList(), key = { it.serviceId }) { svc ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                            border = BorderStroke(
                                1.dp,
                                if (svc.isConnected) JarvisEmerald.copy(alpha = 0.5f) else JarvisCyan.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(svc.displayName, style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                    Surface(
                                        color = (if (svc.isConnected) JarvisEmerald else JarvisAmber).copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (svc.isConnected) "CONNECTED" else "NOT CONNECTED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (svc.isConnected) JarvisEmerald else JarvisAmber,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(svc.accessSummary, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                                Text(
                                    text = "Required Scope: ${svc.requiredScopes.joinToString { it.substringAfterLast("/") }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisCyan
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // GOOGLE DRIVE EXPLORER & DOCUMENT SUMMARIZER
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = JarvisCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Google Drive Explorer & AI Document Reader", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                }
                                Text(
                                    text = "Search files by name or PDF filter, read & summarize Google Docs/Sheets/PDFs with Gemini, upload local files, create folders, rename, download, or delete (with mandatory confirmation).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = driveSearchQuery,
                                        onValueChange = { driveSearchQuery = it },
                                        label = { Text("Search Drive by file name...") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("drive_search_input")
                                    )
                                    Button(
                                        onClick = { viewModel.refreshGoogleDriveFiles(driveSearchQuery, drivePdfOnly) },
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                                        modifier = Modifier.testTag("drive_search_button")
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "Search Drive", tint = JarvisDeepNavy)
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    FilterChip(
                                        selected = drivePdfOnly,
                                        onClick = {
                                            drivePdfOnly = !drivePdfOnly
                                            viewModel.refreshGoogleDriveFiles(driveSearchQuery, drivePdfOnly)
                                        },
                                        label = { Text("PDFs Only") }
                                    )
                                    OutlinedButton(
                                        onClick = { driveUploadLauncher.launch("*/*") },
                                        modifier = Modifier.testTag("drive_upload_file_button")
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Upload File", color = JarvisCyan)
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = newFolderName,
                                        onValueChange = { newFolderName = it },
                                        label = { Text("New Drive Folder Name") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            if (newFolderName.isNotBlank()) {
                                                viewModel.createGoogleDriveFolder(newFolderName)
                                                newFolderName = ""
                                            }
                                        },
                                        modifier = Modifier.testTag("drive_create_folder_button")
                                    ) {
                                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Create Folder", color = JarvisCyan)
                                    }
                                }
                            }
                        }
                    }

                    // Disambiguation Banner when multiple files matched a voice/text search
                    if (disambiguationFiles.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                                border = BorderStroke(1.dp, JarvisAmber),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Multiple Drive Files Matched — Select Which File to Use:",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = JarvisAmber
                                    )
                                    disambiguationFiles.forEach { df ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(df.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            OutlinedButton(onClick = { viewModel.readAndSummarizeDriveFile(df) }) {
                                                Text("Select & Summarize", style = MaterialTheme.typography.labelSmall, color = JarvisCyan)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    selectedDriveFile?.let { sel ->
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                                border = BorderStroke(1.dp, JarvisEmerald),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Active Selected Drive File:", style = MaterialTheme.typography.labelSmall, color = JarvisEmerald)
                                        Text(sel.name, style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                    }
                                    Button(
                                        onClick = { viewModel.readAndSummarizeDriveFile(sel) },
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisEmerald)
                                    ) {
                                        Text("Summarize with AI", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    items(driveFiles, key = { it.id }) { file ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectDriveFile(file) }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                        Text(
                                            text = "${file.mimeType} • ${if (file.isFolder) "Folder" else "${file.sizeBytes / 1024} KB"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = JarvisTextSecondary
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    if (!file.isFolder) {
                                        Button(
                                            onClick = { viewModel.readAndSummarizeDriveFile(file) },
                                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                                        ) {
                                            Text("Read & Summarize", color = JarvisDeepNavy, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(onClick = { viewModel.downloadGoogleDriveFile(file) }) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            renameTargetFile = file
                                            renameInput = file.name
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Rename", style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.requestDeleteGoogleDriveFile(file) },
                                        border = BorderStroke(1.dp, JarvisCoral)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = JarvisCoral, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", color = JarvisCoral, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // GOOGLE CALENDAR & GMAIL
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Event, contentDescription = null, tint = JarvisCyan)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Google Calendar Events", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                    }
                                    Button(
                                        onClick = { viewModel.loadUpcomingCalendarEvents() },
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                                        modifier = Modifier.testTag("load_calendar_events_button")
                                    ) {
                                        Text("Load Upcoming", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (calendarEvents.isEmpty()) {
                                    Text(
                                        text = "Tap 'Load Upcoming' or say \"Hey JARVIS, show my upcoming Calendar events\".",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextSecondary
                                    )
                                } else {
                                    calendarEvents.forEach { ev ->
                                        Surface(
                                            color = JarvisDeepNavy,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(ev.title, style = MaterialTheme.typography.titleSmall, color = JarvisCyan)
                                                Text("Start: ${ev.startTime}", style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                                                if (ev.location.isNotBlank()) {
                                                    Text("Location: ${ev.location}", style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Email, contentDescription = null, tint = JarvisCyan)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Gmail Search & Confirmed Drafts", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                    }
                                    OutlinedButton(onClick = { showGmailDraftDialog = true }) {
                                        Text("+ Create Draft", color = JarvisCyan)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = gmailSearchQuery,
                                        onValueChange = { gmailSearchQuery = it },
                                        label = { Text("Gmail Query (e.g. from:boss or label:unread)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { viewModel.searchGmailMessages(gmailSearchQuery) },
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                                    ) {
                                        Text("Search", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                                    }
                                }
                                gmailMessages.forEach { msg ->
                                    Surface(
                                        color = JarvisDeepNavy,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(msg.subject, style = MaterialTheme.typography.titleSmall, color = JarvisCyan)
                                            Text("From: ${msg.from} • ${msg.date}", style = MaterialTheme.typography.labelSmall, color = JarvisEmerald)
                                            Text(msg.snippet, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // GOOGLE CONTACTS, GOOGLE PHOTOS PICKER & YOUTUBE
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = JarvisCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Google Photos & Visual Media (Zero-Permission Picker)", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                }
                                Text(
                                    text = "Uses Android's official Photo Picker (PickVisualMedia) with Google Photos cloud media provider integration — no broad storage permissions required.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary
                                )
                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                                ) {
                                    Text("Select Photo from Google Photos for AI Analysis", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Google Contacts (People API v1)", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                    Button(
                                        onClick = { viewModel.loadGoogleContacts() },
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                                    ) {
                                        Text("Load Contacts", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                                    }
                                }
                                contacts.forEach { c ->
                                    Text(
                                        text = "• ${c.displayName} — ${c.email.ifBlank { c.phone }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("YouTube Playlists (Data API v3)", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                    Button(
                                        onClick = { viewModel.loadYouTubePlaylists() },
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                                    ) {
                                        Text("Load Playlists", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                                    }
                                }
                                playlists.forEach { pl ->
                                    Text(
                                        text = "• ${pl.title} (${pl.itemCount} videos)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renameTargetFile != null) {
        AlertDialog(
            onDismissRequest = { renameTargetFile = null },
            containerColor = JarvisCardNavy,
            title = { Text("Rename Google Drive File") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        renameTargetFile?.let { viewModel.renameGoogleDriveFile(it, renameInput) }
                        renameTargetFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Continue", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { renameTargetFile = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showGmailDraftDialog) {
        AlertDialog(
            onDismissRequest = { showGmailDraftDialog = false },
            containerColor = JarvisCardNavy,
            title = { Text("Create Gmail Draft (Confirmation Protected)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draftTo,
                        onValueChange = { draftTo = it },
                        label = { Text("Recipient Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draftSubject,
                        onValueChange = { draftSubject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draftBody,
                        onValueChange = { draftBody = it },
                        label = { Text("Email Body") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestCreateGmailDraft(draftTo, draftSubject, draftBody)
                        showGmailDraftDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Review & Confirm", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showGmailDraftDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showManualTokenDialog) {
        AlertDialog(
            onDismissRequest = { showManualTokenDialog = false },
            containerColor = JarvisCardNavy,
            title = { Text("Verify OAuth 2.0 Bearer Token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "If you are running on a headless cloud emulator without Google Play Services sign-in, you can paste a valid OAuth 2.0 access token (e.g., from Google OAuth Playground) to test live Drive/Calendar/Gmail API calls. Never enter your Google password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                    OutlinedTextField(
                        value = manualTokenInput,
                        onValueChange = { manualTokenInput = it },
                        label = { Text("OAuth 2.0 Access Token (ya29....)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.connectWithManualOAuthToken(manualTokenInput)
                        manualTokenInput = ""
                        showManualTokenDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Verify & Connect", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showManualTokenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ============================================================================
// SECTION 31: UNLIMITED CUSTOM MCP SERVER MANAGEMENT CENTER
// ============================================================================

@Composable
fun McpServerManagementSection(viewModel: JarvisViewModel) {
    val servers by viewModel.mcpServers.collectAsState()
    val tools by viewModel.mcpTools.collectAsState()
    val auditLogs by viewModel.mcpAuditLogs.collectAsState()
    val latestToolOutput by viewModel.mcpLatestToolOutput.collectAsState()

    var activeMcpTab by remember { mutableIntStateOf(0) }
    val mcpTabs = listOf(
        "MCP Servers (${servers.size})",
        "Discovered Tools (${tools.size})",
        "Security Audit (${auditLogs.size})",
        "MCP Spec & Guide"
    )

    var searchQuery by remember { mutableStateOf("") }
    var showServerDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<McpServerEntity?>(null) }

    var formName by remember { mutableStateOf("") }
    var formUrl by remember { mutableStateOf("") }
    var formTransport by remember { mutableStateOf(McpTransportType.STREAMABLE_HTTP) }
    var formAuth by remember { mutableStateOf(McpAuthType.NONE) }
    var formCustomHeader by remember { mutableStateOf("X-API-Key") }
    var formSecret by remember { mutableStateOf("") }
    var formTimeout by remember { mutableStateOf("20") }
    var formEnabled by remember { mutableStateOf(true) }
    var formConfirmWrites by remember { mutableStateOf(true) }

    var toolToRun by remember { mutableStateOf<McpToolEntity?>(null) }
    var toolArgsInput by remember { mutableStateOf("{}") }

    var showImportExportDialog by remember { mutableStateOf(false) }
    var importExportJsonText by remember { mutableStateOf("") }

    val filteredServers = remember(servers, searchQuery) {
        if (searchQuery.isBlank()) servers
        else servers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.endpointUrl.contains(searchQuery, ignoreCase = true)
        }
    }

    val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    PendingOperationConfirmationHost(viewModel)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            mcpTabs.forEachIndexed { idx, label ->
                FilterChip(
                    selected = (activeMcpTab == idx),
                    onClick = { activeMcpTab = idx },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                        selectedLabelColor = JarvisCyan
                    ),
                    modifier = Modifier.testTag("mcp_subtab_$idx")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when (activeMcpTab) {
                0 -> {
                    // Header Controls
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Hub, contentDescription = null, tint = JarvisCyan)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Unlimited Custom MCP Server Manager", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                            Text(
                                                text = "Connect any number of Model Context Protocol servers dynamically — no APK rebuild needed.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = JarvisTextSecondary
                                            )
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    Button(
                                        onClick = {
                                            editingServer = null
                                            formName = ""
                                            formUrl = "https://"
                                            formTransport = McpTransportType.STREAMABLE_HTTP
                                            formAuth = McpAuthType.BEARER_TOKEN
                                            formCustomHeader = "X-API-Key"
                                            formSecret = ""
                                            formTimeout = "20"
                                            formEnabled = true
                                            formConfirmWrites = true
                                            showServerDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                                        modifier = Modifier.testTag("add_mcp_server_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = JarvisDeepNavy, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Custom MCP Server", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.testAllEnabledMcpServers() },
                                        modifier = Modifier.testTag("test_all_mcp_servers_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Test All Enabled", color = JarvisCyan)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            viewModel.exportMcpServersConfig { exported ->
                                                importExportJsonText = exported
                                                showImportExportDialog = true
                                            }
                                        },
                                        modifier = Modifier.testTag("import_export_mcp_button")
                                    ) {
                                        Text("Import / Export JSON", color = JarvisViolet)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.emergencyDisconnectAllMcpServers() },
                                        border = BorderStroke(1.dp, JarvisCoral),
                                        modifier = Modifier.testTag("emergency_disconnect_mcp_button")
                                    ) {
                                        Text("Emergency Disconnect All", color = JarvisCoral)
                                    }
                                }

                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("Search configured MCP servers...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    items(filteredServers, key = { it.id }) { srv ->
                        val statusColor = when (srv.connectionStatus) {
                            "CONNECTED" -> JarvisEmerald
                            "TESTING" -> JarvisCyan
                            "ERROR" -> JarvisCoral
                            else -> JarvisTextSecondary
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(srv.name, style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                        Text(srv.endpointUrl, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                                    }
                                    Switch(
                                        checked = srv.isEnabled,
                                        onCheckedChange = { viewModel.toggleMcpServerEnabled(srv, it) }
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = statusColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = srv.connectionStatus,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = "${srv.transportType} • Auth: ${srv.authType} • Tools: ${srv.lastToolCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisCyan
                                    )
                                }

                                Text(srv.statusMessage, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    Button(
                                        onClick = { viewModel.testAndConnectMcpServer(srv) },
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                                    ) {
                                        Text("Connect & Discover Tools", color = JarvisDeepNavy, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            editingServer = srv
                                            formName = srv.name
                                            formUrl = srv.endpointUrl
                                            formTransport = runCatching { McpTransportType.valueOf(srv.transportType) }.getOrDefault(McpTransportType.STREAMABLE_HTTP)
                                            formAuth = runCatching { McpAuthType.valueOf(srv.authType) }.getOrDefault(McpAuthType.NONE)
                                            formCustomHeader = srv.customAuthHeaderName.ifBlank { "X-API-Key" }
                                            formSecret = ""
                                            formTimeout = srv.timeoutSeconds.toString()
                                            formEnabled = srv.isEnabled
                                            formConfirmWrites = srv.requireConfirmationForWrites
                                            showServerDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (srv.connectionStatus == "CONNECTED") {
                                        OutlinedButton(onClick = { viewModel.disconnectMcpServer(srv) }) {
                                            Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.deleteMcpServer(srv) },
                                        border = BorderStroke(1.dp, JarvisCoral)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = JarvisCoral, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", color = JarvisCoral, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // DISCOVERED MCP TOOLS INSPECTOR & RUNNER
                    latestToolOutput?.let { out ->
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                                border = BorderStroke(1.dp, JarvisEmerald),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Latest MCP Tool Output (Sanitized):", style = MaterialTheme.typography.labelMedium, color = JarvisEmerald)
                                    Text(out, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    items(tools, key = { it.id }) { tool ->
                        val badgeColor = when (tool.safetyClassification) {
                            "DESTRUCTIVE" -> JarvisCoral
                            "WRITE" -> JarvisAmber
                            else -> JarvisEmerald
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tool.toolName, style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                        Text("Server: ${tool.serverName}", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary)
                                    }
                                    Switch(
                                        checked = tool.isApproved,
                                        onCheckedChange = { viewModel.toggleMcpToolApproved(tool, it) }
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = badgeColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = tool.safetyClassification,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (tool.hasPromptInjectionWarning) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = JarvisCoral, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Suspicious Metadata Filtered", style = MaterialTheme.typography.labelSmall, color = JarvisCoral)
                                        }
                                    }
                                }

                                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                                Text(
                                    text = "Schema: ${tool.inputSchemaJson}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = JarvisTextSecondary
                                )

                                Button(
                                    onClick = {
                                        toolToRun = tool
                                        toolArgsInput = "{}"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JarvisDeepNavy, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Invoke Tool", color = JarvisDeepNavy, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // MCP SECURITY AUDIT LOGS
                    item {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("MCP Tool & Handshake Audit Log", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                            if (auditLogs.isNotEmpty()) {
                                OutlinedButton(onClick = { viewModel.clearMcpAuditLogs() }) {
                                    Text("Clear Logs", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    if (auditLogs.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                                Text(
                                    text = "No MCP tool executions or handshakes logged yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    } else {
                        items(auditLogs, key = { it.id }) { log ->
                            Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("${log.serverName} • ${log.toolName}", style = MaterialTheme.typography.titleSmall, color = JarvisCyan)
                                        Text(
                                            text = "${log.status} (${log.durationMs}ms)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (log.status == "SUCCESS") JarvisEmerald else JarvisCoral
                                        )
                                    }
                                    Text("Args: ${log.argumentsPreview}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                    Text(log.resultSummary, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                                    Text(fmt.format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary)
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // MCP DOCUMENTATION & ANDROID OS LIMITATIONS
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = JarvisCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("MCP Architecture, Transports & Mobile Boundaries", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                                }
                                Text(
                                    text = "• Official Protocol: Implements Model Context Protocol JSON-RPC 2.0 specification (2025-03-26 & 2024-11-05): `initialize` -> `notifications/initialized` -> `tools/list` -> `tools/call`.\n" +
                                        "• Supported Native Transports:\n" +
                                        "  1. Streamable HTTP (`POST` with `Mcp-Session-Id` header & JSON/SSE response parsing).\n" +
                                        "  2. HTTP + SSE (`text/event-stream`).\n" +
                                        "  3. Remote STDIO Gateway Bridge (`REMOTE_STDIO_GATEWAY`).\n" +
                                        "• Why Local Desktop STDIO Requires a Bridge on Android: Android sandboxes app processes and prohibits spawning arbitrary desktop Node.js (`npx`), Python (`uvx`), or Docker STDIO subprocesses inside a retail mobile APK. To use a desktop STDIO MCP server, wrap it with an HTTP/SSE gateway (such as `supergateway` or `mcp-proxy`) and add its `https://` endpoint here.\n" +
                                        "• Prompt-Injection & Safety Controls: Tool descriptions and outputs are automatically scanned and sanitized against prompt-injection directives. Tools classified as `WRITE` or `DESTRUCTIVE` require explicit user confirmation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit MCP Server Dialog
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            containerColor = JarvisCardNavy,
            title = { Text(if (editingServer == null) "Add Custom MCP Server" else "Edit MCP Server") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = formName,
                        onValueChange = { formName = it },
                        label = { Text("Server Name (e.g. GitHub MCP, Notion MCP)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mcp_dialog_name_input")
                    )
                    OutlinedTextField(
                        value = formUrl,
                        onValueChange = { formUrl = it },
                        label = { Text("Endpoint URL (https://...)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mcp_dialog_url_input")
                    )

                    Text("Transport Type:", style = MaterialTheme.typography.labelMedium, color = JarvisCyan)
                    McpTransportType.entries.forEach { t ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { formTransport = t }
                        ) {
                            FilterChip(
                                selected = (formTransport == t),
                                onClick = { formTransport = t },
                                label = { Text(t.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Text("Authentication Method:", style = MaterialTheme.typography.labelMedium, color = JarvisCyan)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        McpAuthType.entries.forEach { a ->
                            FilterChip(
                                selected = (formAuth == a),
                                onClick = { formAuth = a },
                                label = { Text(a.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (formAuth == McpAuthType.CUSTOM_HEADER) {
                        OutlinedTextField(
                            value = formCustomHeader,
                            onValueChange = { formCustomHeader = it },
                            label = { Text("Custom Header Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (formAuth != McpAuthType.NONE) {
                        OutlinedTextField(
                            value = formSecret,
                            onValueChange = { formSecret = it },
                            label = { Text("Token / API Key (Android Keystore Encrypted)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = formTimeout,
                        onValueChange = { formTimeout = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Timeout Seconds (5 - 120)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Require Confirmation Before Write/Destructive Tools", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = formConfirmWrites, onCheckedChange = { formConfirmWrites = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveMcpServerConfig(
                            existingId = editingServer?.id ?: 0L,
                            name = formName,
                            endpointUrl = formUrl,
                            transport = formTransport,
                            authType = formAuth,
                            customAuthHeaderName = formCustomHeader,
                            secretToken = formSecret,
                            timeoutSeconds = formTimeout.toIntOrNull() ?: 20,
                            isEnabled = formEnabled,
                            requireConfirmationForWrites = formConfirmWrites
                        )
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                    modifier = Modifier.testTag("save_mcp_server_dialog_button")
                ) {
                    Text("Save Server", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showServerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Invoke MCP Tool Dialog
    if (toolToRun != null) {
        val currentTool = toolToRun!!
        AlertDialog(
            onDismissRequest = { toolToRun = null },
            containerColor = JarvisCardNavy,
            title = { Text("Invoke Tool: ${currentTool.toolName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Server: ${currentTool.serverName} • Safety: ${currentTool.safetyClassification}",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisCyan
                    )
                    Text(
                        text = "Input Schema: ${currentTool.inputSchemaJson}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = JarvisTextSecondary
                    )
                    OutlinedTextField(
                        value = toolArgsInput,
                        onValueChange = { toolArgsInput = it },
                        label = { Text("JSON Arguments") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestInvokeMcpTool(currentTool, toolArgsInput)
                        toolToRun = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Execute Tool", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { toolToRun = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import / Export JSON Dialog
    if (showImportExportDialog) {
        AlertDialog(
            onDismissRequest = { showImportExportDialog = false },
            containerColor = JarvisCardNavy,
            title = { Text("Import / Export MCP Server Configurations") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Exported JSON automatically omits secret tokens for security. Paste an exported JSON array below and tap Import to add servers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                    OutlinedTextField(
                        value = importExportJsonText,
                        onValueChange = { importExportJsonText = it },
                        label = { Text("MCP Servers JSON") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importMcpServersConfig(importExportJsonText)
                        showImportExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Import JSON", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
