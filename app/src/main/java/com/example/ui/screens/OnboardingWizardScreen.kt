package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.ElevenLabsVoiceOption
import com.example.security.GeminiModelOption
import com.example.security.JarvisLanguage
import com.example.ui.ConnectionTestState
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
fun OnboardingWizardScreen(
    viewModel: JarvisViewModel,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 8

    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val geminiTestState by viewModel.geminiTestState.collectAsState()
    val elevenLabsTestState by viewModel.elevenLabsTestState.collectAsState()
    val secondaryAiTestState by viewModel.secondaryAiTestState.collectAsState()
    val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsState()

    var customGeminiKey by remember { mutableStateOf(viewModel.secureVault.customGeminiApiKey) }
    var useCustomGemini by remember { mutableStateOf(viewModel.secureVault.useCustomGeminiKey) }
    var showGeminiKey by remember { mutableStateOf(false) }

    var elevenKey by remember { mutableStateOf(viewModel.secureVault.elevenLabsApiKey) }
    var enableEleven by remember { mutableStateOf(viewModel.secureVault.isElevenLabsEnabled) }
    var selectedElevenVoice by remember { mutableStateOf(viewModel.secureVault.elevenLabsVoice) }
    var showElevenKey by remember { mutableStateOf(false) }

    var secondaryProviderName by remember { mutableStateOf(viewModel.secureVault.secondaryAiName) }
    var secondaryKey by remember { mutableStateOf(viewModel.secureVault.secondaryAiApiKey) }
    var enableSecondary by remember { mutableStateOf(viewModel.secureVault.isSecondaryAiEnabled) }
    var showSecondaryKey by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val wakeWordMicPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setWakeWordEnabled(true)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepNavy)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        // Top Wizard Header & Skip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "JARVIS INITIALIZATION WIZARD",
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisCyan
                )
                Text(
                    text = "Step $currentStep of $totalSteps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(
                onClick = {
                    viewModel.completeOnboarding()
                    onFinish()
                },
                modifier = Modifier.testTag("onboarding_skip_all_button")
            ) {
                Text("Skip Setup", color = JarvisCyan)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps.toFloat() },
            color = JarvisCyan,
            trackColor = JarvisCardNavy,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Step Body Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (currentStep) {
                1 -> {
                    // STEP 1: Welcome
                    Image(
                        painter = painterResource(id = R.drawable.img_jarvis_hero_bg),
                        contentDescription = "JARVIS Command Center Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                    Text(
                        text = "Welcome to Project JARVIS",
                        style = MaterialTheme.typography.headlineLarge,
                        color = JarvisCyan
                    )
                    Text(
                        text = "JARVIS is a native Android personal AI & device assistant built for voice, visual intelligence, and structured task execution.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OnboardingBulletCard(
                        title = "Bilingual Voice & Gemini 3.8 Live API",
                        body = "Converse naturally in Hindi, English, or Hinglish with real-time voice or tap-to-talk."
                    )
                    OnboardingBulletCard(
                        title = "Multimodal Screen, Image & Document Brain",
                        body = "Analyze photos, PDFs, text files, or explicitly shared screen frames."
                    )
                    OnboardingBulletCard(
                        title = "Honest Android Security Boundaries",
                        body = "JARVIS never secretly records audio/screens and always asks confirmation before sensitive phone actions."
                    )
                }

                2 -> {
                    // STEP 2: Language Selection
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Step 2 — Assistant Language", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text(
                        text = "Choose how JARVIS listens and replies. You can change this anytime in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    JarvisLanguage.entries.forEach { lang ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedLanguage == lang) JarvisCyan.copy(alpha = 0.16f) else JarvisCardNavy
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selectedLanguage == lang) JarvisCyan else JarvisCardNavy
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateLanguage(lang) }
                                .testTag("lang_option_${lang.code}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                RadioButton(
                                    selected = (selectedLanguage == lang),
                                    onClick = { viewModel.updateLanguage(lang) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(lang.label, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Speech Locale: ${lang.sttLocale}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // STEP 3: Gemini API Setup
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Step 3 — Gemini AI Brain Setup", style = MaterialTheme.typography.headlineMedium)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = JarvisEmerald.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, JarvisEmerald)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "✓ Firebase AI Logic Pre-Provisioned",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisEmerald
                            )
                            Text(
                                text = "Your app is already connected to Google Gemini via Firebase AI Logic with App Check attestation. You can use Gemini 3.8 Flash & Live API immediately, OR optionally enter your own Google AI Studio Gemini API key below.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Text("Select Verified Gemini Model:", style = MaterialTheme.typography.titleMedium)
                    GeminiModelOption.entries.forEach { modelOpt ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedModel == modelOpt) JarvisCyan.copy(alpha = 0.15f) else JarvisCardNavy
                            ),
                            border = BorderStroke(1.dp, if (selectedModel == modelOpt) JarvisCyan else JarvisCardNavy),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateSelectedModel(modelOpt) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                RadioButton(
                                    selected = (selectedModel == modelOpt),
                                    onClick = { viewModel.updateSelectedModel(modelOpt) }
                                )
                                Column {
                                    Text(modelOpt.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(modelOpt.subtitle, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                                }
                            }
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Optional: Custom Google AI Studio API Key", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Get a key at aistudio.google.com/app/apikey. Stored locally with Android Keystore AES-GCM encryption. Note: API quotas and free-tier rate limits apply; for production apps, a backend proxy or Firebase App Check is recommended.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary
                            )
                            OutlinedTextField(
                                value = customGeminiKey,
                                onValueChange = { customGeminiKey = it },
                                label = { Text("Gemini API Key (Optional)") },
                                singleLine = true,
                                visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                        Icon(
                                            imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle key visibility"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_gemini_key_input")
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Use custom key instead of Firebase AI Logic", style = MaterialTheme.typography.bodySmall)
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
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                                    modifier = Modifier.testTag("test_gemini_connection_button")
                                ) {
                                    Text("Test Connection", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.saveCustomGeminiKey(customGeminiKey, useCustomGemini) }
                                ) {
                                    Text("Save Key")
                                }
                                if (customGeminiKey.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            customGeminiKey = ""
                                            useCustomGemini = false
                                            viewModel.removeCustomGeminiKey()
                                        }
                                    ) {
                                        Text("Remove", color = JarvisCoral)
                                    }
                                }
                            }
                            ConnectionStatusDisplay(state = geminiTestState)
                        }
                    }
                }

                4 -> {
                    // STEP 4: ElevenLabs Optional Setup
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Step 4 — ElevenLabs Voice (Optional)", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text(
                        text = "ElevenLabs is completely optional. When not configured or offline, JARVIS automatically uses native Android Text-to-Speech (Hindi & English).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = elevenKey,
                                onValueChange = { elevenKey = it },
                                label = { Text("ElevenLabs API Key (xi-api-key)") },
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_elevenlabs_key_input")
                            )

                            Text("Select ElevenLabs Voice Profile:", style = MaterialTheme.typography.labelLarge)
                            ElevenLabsVoiceOption.entries.forEach { voiceOpt ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedElevenVoice = voiceOpt }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = (selectedElevenVoice == voiceOpt),
                                        onClick = { selectedElevenVoice = voiceOpt }
                                    )
                                    Column {
                                        Text(voiceOpt.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(voiceOpt.style, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable ElevenLabs Voice Output", style = MaterialTheme.typography.bodyMedium)
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
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                                    modifier = Modifier.testTag("test_elevenlabs_button")
                                ) {
                                    Text("Test Voice API", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.saveElevenLabsConfig(elevenKey, selectedElevenVoice, enableEleven) }
                                ) {
                                    Text("Save")
                                }
                                if (elevenKey.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            elevenKey = ""
                                            enableEleven = false
                                            viewModel.removeElevenLabsKey()
                                        }
                                    ) {
                                        Text("Remove", color = JarvisCoral)
                                    }
                                }
                            }
                            ConnectionStatusDisplay(state = elevenLabsTestState)
                        }
                    }
                }

                5 -> {
                    // STEP 5: Other Optional Providers
                    Text("Step 5 — Search & Fallback Providers", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "JARVIS never silently switches providers. If a primary provider fails and a fallback is enabled, JARVIS notifies you explicitly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Verified Web Research (Wikipedia + DuckDuckGo Instant API)", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                            Text(
                                text = "Free, no API key required. Retrieves real source links and summaries when you ask JARVIS to research a topic.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary
                            )
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Optional Secondary AI Fallback (OpenRouter)", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = secondaryProviderName,
                                onValueChange = { secondaryProviderName = it },
                                label = { Text("Provider Display Label") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = secondaryKey,
                                onValueChange = { secondaryKey = it },
                                label = { Text("OpenRouter API Key (Optional)") },
                                singleLine = true,
                                visualTransformation = if (showSecondaryKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showSecondaryKey = !showSecondaryKey }) {
                                        Icon(
                                            imageVector = if (showSecondaryKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Show/hide key"
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
                                Text("Enable as Fallback if Primary Fails", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = enableSecondary,
                                    onCheckedChange = {
                                        enableSecondary = it
                                        viewModel.saveSecondaryProviderConfig(secondaryProviderName, secondaryKey, it)
                                    }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.testSecondaryProviderConnection(secondaryKey) },
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                                ) {
                                    Text("Test Fallback", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.saveSecondaryProviderConfig(secondaryProviderName, secondaryKey, enableSecondary) }
                                ) {
                                    Text("Save")
                                }
                            }
                            ConnectionStatusDisplay(state = secondaryAiTestState)
                        }
                    }
                }

                6 -> {
                    // STEP 6: Permissions
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Step 6 — Transparent Permissions", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text(
                        text = "JARVIS never requests all permissions blindly at startup. Review why each permission is used and grant only the ones you want.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )

                    PermissionExplainCard(
                        title = "Microphone (Voice & Wake Word)",
                        description = "Required to hear 'Hey JARVIS', Tap-to-Talk commands, and Gemini Live voice conversations. Never records secretly.",
                        buttonText = "Grant Microphone",
                        onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionExplainCard(
                            title = "Notifications (Foreground Indicator & Reminders)",
                            description = "Displays the persistent 'JARVIS Listening / Screen Sharing' stop control notification and task alerts.",
                            buttonText = "Grant Notifications",
                            onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                    }

                    PermissionExplainCard(
                        title = "Display Over Other Apps (Floating JARVIS Orb)",
                        description = "Optional. Allows the compact draggable JARVIS orb to float over other apps. Granted in Android Settings.",
                        buttonText = if (Settings.canDrawOverlays(context)) "Overlay Granted ✓" else "Open Overlay Settings",
                        onClick = { viewModel.controlExecutor.openSystemSettings("overlay") }
                    )

                    PermissionExplainCard(
                        title = "Notification Listener (Notification Summarizer)",
                        description = "Optional. Allows JARVIS to summarize incoming notifications when enabled in Android Settings.",
                        buttonText = "Open Notification Access",
                        onClick = { viewModel.controlExecutor.openSystemSettings("notifications") }
                    )
                }

                7 -> {
                    // STEP 7: Wake Word ("Hey JARVIS")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Step 7 — \"Hey JARVIS\" Wake Word", style = MaterialTheme.typography.headlineMedium)
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Enable \"Hey JARVIS\" Detection", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Activates hands-free listening while the app or foreground service is open.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextSecondary
                                    )
                                }
                                Switch(
                                    checked = wakeWordEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasMic) {
                                                viewModel.setWakeWordEnabled(true)
                                            } else {
                                                wakeWordMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        } else {
                                            viewModel.setWakeWordEnabled(false)
                                        }
                                    },
                                    modifier = Modifier.testTag("onboarding_wake_word_switch")
                                )
                            }
                            Text(
                                text = "Android Battery & Background Restrictions Disclosure:\n" +
                                    "• Android 14+ restricts background microphone starts unless a visible Foreground Service notification is active.\n" +
                                    "• Continuous wake-word listening uses more battery than Tap-to-Talk.\n" +
                                    "• A prominent 'Stop Listening' button is always shown on the Home HUD and in the system notification.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisAmber
                            )
                        }
                    }
                }

                8 -> {
                    // STEP 8: Finish
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Surface(
                            color = JarvisCyan.copy(alpha = 0.15f),
                            shape = CircleShape,
                            border = BorderStroke(2.dp, JarvisCyan),
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Setup Complete",
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "JARVIS Systems Ready",
                        style = MaterialTheme.typography.headlineLarge,
                        color = JarvisCyan
                    )
                    Text(
                        text = "Your preferences and encrypted vault settings are configured. You can adjust AI providers, voice engines, permissions, wake-word, and memory controls anytime in the Settings tab.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OnboardingBulletCard(
                        title = "Active AI Brain",
                        body = viewModel.aiBrain.getActiveProviderBadge()
                    )
                    OnboardingBulletCard(
                        title = "Language Mode",
                        body = selectedLanguage.label
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom Navigation Buttons
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (currentStep > 1) {
                OutlinedButton(
                    onClick = { currentStep-- },
                    modifier = Modifier.testTag("onboarding_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Button(
                onClick = {
                    if (currentStep < totalSteps) {
                        currentStep++
                    } else {
                        viewModel.completeOnboarding()
                        onFinish()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                modifier = Modifier.testTag("onboarding_next_or_finish_button")
            ) {
                Text(
                    text = if (currentStep < totalSteps) "Continue" else "Launch JARVIS HUD",
                    color = JarvisDeepNavy,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Continue",
                    tint = JarvisDeepNavy,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun OnboardingBulletCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
            Spacer(modifier = Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
        }
    }
}

@Composable
private fun PermissionExplainCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
            Text(description, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
            OutlinedButton(onClick = onClick) {
                Text(buttonText, color = JarvisCyan)
            }
        }
    }
}

@Composable
fun ConnectionStatusDisplay(state: ConnectionTestState) {
    when (state) {
        is ConnectionTestState.Idle -> {}
        is ConnectionTestState.Testing -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = JarvisCyan, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Testing API connection...", style = MaterialTheme.typography.bodySmall, color = JarvisCyan)
            }
        }
        is ConnectionTestState.Success -> {
            Text("✓ ${state.message}", style = MaterialTheme.typography.bodySmall, color = JarvisEmerald)
        }
        is ConnectionTestState.Error -> {
            Text("✗ ${state.message}", style = MaterialTheme.typography.bodySmall, color = JarvisCoral)
        }
    }
}
