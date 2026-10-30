package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.MemoryEntity
import com.example.security.ElevenLabsVoiceOption
import com.example.security.GeminiModelOption
import com.example.security.JarvisLanguage
import com.example.ui.JarvisViewModel
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCardNavy
import com.example.ui.theme.JarvisCoral
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet

@Composable
fun MemoryVaultScreen(
    viewModel: JarvisViewModel
) {
    val memories by viewModel.memories.collectAsState()
    val memoryEnabled by viewModel.memoryEnabled.collectAsState()

    var selectedCategory by remember { mutableStateOf("ALL") }
    val categories = listOf("ALL", "PREFERENCE", "FACT", "ROUTINE", "TASK_CONTEXT")

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var editCategory by remember { mutableStateOf("FACT") }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }

    val filteredMemories = remember(memories, selectedCategory) {
        if (selectedCategory == "ALL") memories
        else memories.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Memory Vault Control Header
        Card(
            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("User-Controlled Memory Vault", style = MaterialTheme.typography.titleLarge, color = JarvisCyan)
                            Text(
                                text = "Kept strictly separate from chat history. Personal facts require your approval.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = memoryEnabled,
                        onCheckedChange = { viewModel.setMemorySystemEnabled(it) },
                        modifier = Modifier.testTag("memory_system_switch")
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = (selectedCategory == cat),
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = JarvisCyan
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            editingMemory = null
                            editCategory = "FACT"
                            editTitle = ""
                            editContent = ""
                            showEditorDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                        modifier = Modifier.testTag("add_memory_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add memory", tint = JarvisDeepNavy, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(filteredMemories, key = { it.id }) { mem ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                    border = BorderStroke(
                        1.dp,
                        if (!mem.isApproved) JarvisAmber else JarvisCyan.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = (if (mem.isApproved) JarvisCyan else JarvisAmber).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (mem.isApproved) mem.category else "AWAITING YOUR APPROVAL • ${mem.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mem.isApproved) JarvisCyan else JarvisAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Row {
                                if (!mem.isApproved) {
                                    IconButton(
                                        onClick = { viewModel.approveMemory(mem) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Approve memory", tint = JarvisEmerald)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        editingMemory = mem
                                        editCategory = mem.category
                                        editTitle = mem.keyTitle
                                        editContent = mem.content
                                        showEditorDialog = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit memory", tint = JarvisCyan, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.deleteMemory(mem.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete memory", tint = JarvisCoral, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Text(mem.keyTitle, style = MaterialTheme.typography.titleMedium)
                        Text(mem.content, style = MaterialTheme.typography.bodyMedium, color = JarvisTextSecondary)
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        AlertDialog(
            onDismissRequest = { showEditorDialog = false },
            containerColor = JarvisCardNavy,
            title = { Text(if (editingMemory == null) "Add Approved Memory" else "Edit Memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("PREFERENCE", "FACT", "ROUTINE", "TASK_CONTEXT").forEach { cat ->
                            FilterChip(
                                selected = (editCategory == cat),
                                onClick = { editCategory = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Memory Title / Key") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_dialog_title_input")
                    )
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("Details to Remember") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_dialog_content_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addOrUpdateMemory(
                            id = editingMemory?.id ?: 0L,
                            category = editCategory,
                            keyTitle = editTitle,
                            content = editContent,
                            approved = true
                        )
                        showEditorDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                    modifier = Modifier.testTag("save_memory_dialog_button")
                ) {
                    Text("Save Memory", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditorDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsScreen(
    viewModel: JarvisViewModel
) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val speechPitch by viewModel.speechPitch.collectAsState()
    val autoSpeak by viewModel.autoSpeak.collectAsState()
    val isFloatingOrbRunning by viewModel.isFloatingOrbRunning.collectAsState()
    val geminiTestState by viewModel.geminiTestState.collectAsState()
    val elevenTestState by viewModel.elevenLabsTestState.collectAsState()
    val connectedGoogleEmail by viewModel.googleConnectedEmail.collectAsState()
    val mcpServers by viewModel.mcpServers.collectAsState()

    var settingsSubSection by remember { mutableStateOf(0) } // 0 = Core Settings, 1 = Integrations, 2 = MCP Server Manager

    var customGeminiKey by remember { mutableStateOf(viewModel.secureVault.customGeminiApiKey) }
    var useCustomGemini by remember { mutableStateOf(viewModel.secureVault.useCustomGeminiKey) }
    var showGeminiKey by remember { mutableStateOf(false) }

    var elevenKey by remember { mutableStateOf(viewModel.secureVault.elevenLabsApiKey) }
    var enableEleven by remember { mutableStateOf(viewModel.secureVault.isElevenLabsEnabled) }
    var selectedElevenVoice by remember { mutableStateOf(viewModel.secureVault.elevenLabsVoice) }
    var showElevenKey by remember { mutableStateOf(false) }

    var showPurgeConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = JarvisCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("JARVIS System Settings", style = MaterialTheme.typography.headlineMedium, color = JarvisCyan)
            }
            OutlinedButton(
                onClick = { viewModel.reopenOnboardingWizard() },
                modifier = Modifier.testTag("reopen_setup_wizard_button")
            ) {
                Text("Setup Wizard", color = JarvisCyan)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Settings Section Selector: Core Settings | Google & App Integrations | MCP Server Manager
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = (settingsSubSection == 0),
                onClick = { settingsSubSection = 0 },
                label = { Text("System & AI Vault") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                    selectedLabelColor = JarvisCyan
                ),
                modifier = Modifier.testTag("settings_section_core")
            )
            FilterChip(
                selected = (settingsSubSection == 1),
                onClick = { settingsSubSection = 1 },
                label = {
                    Text(
                        if (connectedGoogleEmail.isNotBlank()) "Integrations (Connected)" else "Integrations (Google & Apps)"
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                    selectedLabelColor = JarvisCyan
                ),
                modifier = Modifier.testTag("settings_section_integrations")
            )
            FilterChip(
                selected = (settingsSubSection == 2),
                onClick = { settingsSubSection = 2 },
                label = { Text("MCP Server Manager (${mcpServers.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                    selectedLabelColor = JarvisCyan
                ),
                modifier = Modifier.testTag("settings_section_mcp")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (settingsSubSection) {
            1 -> {
                GoogleIntegrationsSection(viewModel)
                return@Column
            }
            2 -> {
                McpServerManagementSection(viewModel)
                return@Column
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_scroll_list")
        ) {
            // Quick Jump Cards to Dedicated Integrations & MCP Server Manager inside Settings
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Connected Services & Unlimited Custom MCP Servers",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisCyan
                        )
                        Text(
                            text = "Manage Google OAuth 2.0 integrations (Drive, Gmail, Calendar, Contacts, Photos, YouTube) and connect unlimited custom Model Context Protocol (MCP) servers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { settingsSubSection = 1 },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                                modifier = Modifier.testTag("open_integrations_settings_button")
                            ) {
                                Text("Open Integrations", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { settingsSubSection = 2 },
                                modifier = Modifier.testTag("open_mcp_manager_settings_button")
                            ) {
                                Text("Open MCP Server Manager", color = JarvisCyan)
                            }
                        }
                    }
                }
            }

            // 1. Language Settings
            item {
                Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("1. Language & Code-Switching", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                        JarvisLanguage.entries.forEach { lang ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateLanguage(lang) }
                            ) {
                                RadioButton(
                                    selected = (selectedLanguage == lang),
                                    onClick = { viewModel.updateLanguage(lang) }
                                )
                                Text(lang.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

        // 2. Wake Word & Floating Orb Settings
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            val settingsWakeMicLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    viewModel.setWakeWordEnabled(true)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Wake Word (\"Hey JARVIS\") & Floating Orb", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable \"Hey JARVIS\" Wake-Word Listener", style = MaterialTheme.typography.bodyMedium)
                            Text("Uses Foreground Service indicator while active", style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                        }
                        Switch(
                            checked = wakeWordEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasMic) {
                                        viewModel.setWakeWordEnabled(true)
                                    } else {
                                        settingsWakeMicLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    viewModel.setWakeWordEnabled(false)
                                }
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Floating JARVIS Orb Overlay", style = MaterialTheme.typography.bodyMedium)
                            Text("Display compact draggable assistant over other apps", style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                        }
                        Button(
                            onClick = { viewModel.toggleFloatingOrbOverlay() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFloatingOrbRunning) JarvisEmerald else JarvisCyan
                            )
                        ) {
                            Text(
                                text = if (isFloatingOrbRunning) "Disable Orb" else "Launch Orb",
                                color = JarvisDeepNavy,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. AI Provider & Model Selection
        item {
            Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("3. AI Brain & Gemini API Key Vault", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                    GeminiModelOption.entries.forEach { model ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateSelectedModel(model) }
                        ) {
                            RadioButton(
                                selected = (selectedModel == model),
                                onClick = { viewModel.updateSelectedModel(model) }
                            )
                            Column {
                                Text(model.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customGeminiKey,
                        onValueChange = { customGeminiKey = it },
                        label = { Text("Optional Custom Gemini API Key (Keystore Encrypted)") },
                        singleLine = true,
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                Icon(
                                    imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Show/hide Gemini key"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use Custom Key instead of Firebase AI Logic", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = useCustomGemini,
                            onCheckedChange = {
                                useCustomGemini = it
                                viewModel.saveCustomGeminiKey(customGeminiKey, it)
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.testGeminiConnection(customGeminiKey.takeIf { useCustomGemini }) },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                        ) {
                            Text("Test Gemini API", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { viewModel.saveCustomGeminiKey(customGeminiKey, useCustomGemini) }) {
                            Text("Save")
                        }
                    }
                    ConnectionStatusDisplay(state = geminiTestState)
                }
            }
        }

        // 4. Voice, Android TTS & ElevenLabs Settings
        item {
            Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("4. Voice Settings (Android TTS & ElevenLabs)", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Auto-Speak Assistant Replies", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = autoSpeak,
                            onCheckedChange = { viewModel.updateSpeechSettings(speechRate, speechPitch, it) }
                        )
                    }

                    Text("Speaking Speed: ${"%.2f".format(speechRate)}x", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = speechRate,
                        onValueChange = { viewModel.updateSpeechSettings(it, speechPitch, autoSpeak) },
                        valueRange = 0.5f..2.0f
                    )

                    Text("Voice Pitch: ${"%.2f".format(speechPitch)}x", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = speechPitch,
                        onValueChange = { viewModel.updateSpeechSettings(speechRate, it, autoSpeak) },
                        valueRange = 0.5f..1.5f
                    )

                    OutlinedTextField(
                        value = elevenKey,
                        onValueChange = { elevenKey = it },
                        label = { Text("Optional ElevenLabs API Key") },
                        singleLine = true,
                        visualTransformation = if (showElevenKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showElevenKey = !showElevenKey }) {
                                Icon(
                                    imageVector = if (showElevenKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Show/hide ElevenLabs key"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable ElevenLabs Synthesis (Fallback: Android TTS)", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = enableEleven,
                            onCheckedChange = {
                                enableEleven = it
                                viewModel.saveElevenLabsConfig(elevenKey, selectedElevenVoice, it)
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.testElevenLabsConnection(elevenKey) },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                        ) {
                            Text("Test ElevenLabs", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { viewModel.saveElevenLabsConfig(elevenKey, selectedElevenVoice, enableEleven) }) {
                            Text("Save Voice Config")
                        }
                    }
                    ConnectionStatusDisplay(state = elevenTestState)
                }
            }
        }

        // 5. Security, Privacy & Local Data Purge
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                border = BorderStroke(1.dp, JarvisCoral.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("5. Security, Privacy & Troubleshooting", style = MaterialTheme.typography.titleMedium, color = JarvisCoral)
                    Text(
                        text = "• Local API keys are encrypted using Android Keystore AES-GCM (256-bit). However, no client-side storage is immune to rooted/compromised device inspection — use Firebase AI Logic (App Check) or a backend proxy for production.\n" +
                            "• You can wipe all local conversations, memories, reminders, and stored API credentials at any time below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                    Button(
                        onClick = { showPurgeConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCoral),
                        modifier = Modifier.testTag("purge_all_data_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Purge Data", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All Local Data & Credentials", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
    }

    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            containerColor = JarvisCardNavy,
            title = { Text("Purge All Local Data & Keys?") },
            text = {
                Text("This permanently deletes all chat history, saved memories, reminders, and Android Keystore encrypted API keys from this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.purgeAllLocalDataAndCredentials()
                        showPurgeConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCoral)
                ) {
                    Text("Purge Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
