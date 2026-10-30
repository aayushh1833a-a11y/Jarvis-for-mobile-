package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.JarvisNavTab
import com.example.ui.JarvisViewModel
import com.example.ui.components.JarvisAnimatedOrb
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCardNavy
import com.example.ui.theme.JarvisCoral
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import com.example.voice.AssistantVoiceState

@Composable
fun HomeHudScreen(
    viewModel: JarvisViewModel
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val voiceState by viewModel.voiceState.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val latestReply by viewModel.latestAssistantReply.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val activeProviderBadge by viewModel.activeProviderBadge.collectAsState()
    val activeVoiceBadge by viewModel.activeVoiceProviderLabel.collectAsState()
    val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsState()
    val isGeminiLiveActive by viewModel.isGeminiLiveSessionActive.collectAsState()
    val isScreenSharing by viewModel.isScreenSharingActive.collectAsState()
    val isFloatingOrbRunning by viewModel.isFloatingOrbRunning.collectAsState()
    val selectedFile by viewModel.selectedFilePayload.collectAsState()
    val webSources by viewModel.latestWebSources.collectAsState()

    var commandInput by remember { mutableStateOf("") }

    // Microphone permission launcher for Tap-to-Talk & Gemini Live
    var pendingVoiceAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingVoiceAction?.invoke()
        }
        pendingVoiceAction = null
    }

    fun runWithMicPermission(action: () -> Unit) {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            action()
        } else {
            pendingVoiceAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Screen Capture MediaProjection Launcher
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.onScreenCapturePermissionResult(result.resultCode, result.data)
        } else {
            viewModel.onScreenCapturePermissionResult(result.resultCode, null)
        }
    }

    // Storage Access Framework Multimodal File Picker (Images, PDFs, Text/Markdown/CSV/JSON)
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachUserSelectedFile(uri)
        }
    }

    val isOnline = remember(latestReply, voiceState) {
        viewModel.controlExecutor.isNetworkAvailable()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Futuristic Telemetry Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box {
                Image(
                    painter = painterResource(id = R.drawable.img_jarvis_hero_bg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.22f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                )
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) JarvisEmerald else JarvisAmber)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isOnline) "JARVIS CORE ONLINE" else "OFFLINE DEVICE MODE",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isOnline) JarvisEmerald else JarvisAmber
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "\"Hey JARVIS\"",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (wakeWordEnabled) JarvisCyan else JarvisTextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = wakeWordEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        runWithMicPermission { viewModel.setWakeWordEnabled(true) }
                                    } else {
                                        viewModel.setWakeWordEnabled(false)
                                    }
                                },
                                modifier = Modifier.testTag("home_wake_word_switch")
                            )
                        }
                    }

                    Text(
                        text = "AI Brain: $activeProviderBadge • Voice: $activeVoiceBadge",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. Central Animated Assistant Orb
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            JarvisAnimatedOrb(
                voiceState = voiceState,
                rmsLevel = rmsLevel,
                isGeminiLiveActive = isGeminiLiveActive,
                onOrbTap = {
                    runWithMicPermission {
                        viewModel.voiceManager.startSingleCommandListening()
                    }
                }
            )
        }

        // 3. Primary Voice & Stop Control Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    runWithMicPermission {
                        viewModel.voiceManager.startSingleCommandListening()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                modifier = Modifier
                    .weight(1f)
                    .testTag("tap_to_talk_button")
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Tap to Talk", tint = JarvisDeepNavy, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tap to Talk", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    runWithMicPermission {
                        viewModel.toggleGeminiLiveVoiceSession()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGeminiLiveActive) JarvisEmerald else JarvisViolet
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("gemini_live_voice_button")
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = "Gemini Live Voice", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isGeminiLiveActive) "End Live API" else "Gemini Live",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    viewModel.stopAllVoiceAndListening()
                    viewModel.stopActiveGeneration()
                },
                border = BorderStroke(1.dp, JarvisCoral),
                modifier = Modifier.testTag("stop_listening_button")
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop Listening", tint = JarvisCoral, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stop", color = JarvisCoral)
            }
        }

        // Live Transcript Indicator
        if (partialTranscript.isNotBlank()) {
            Surface(
                color = JarvisCardNavy,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = partialTranscript,
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisCyan
                    )
                }
            }
        }

        // 4. Multimodal Screen Share, File Attachment & Floating Orb Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            OutlinedButton(
                onClick = {
                    if (isScreenSharing) {
                        viewModel.stopScreenSharing()
                    } else {
                        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
                    }
                },
                border = BorderStroke(1.dp, if (isScreenSharing) JarvisCoral else JarvisCyan),
                modifier = Modifier.testTag("share_screen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenShare,
                    contentDescription = "Share Screen",
                    tint = if (isScreenSharing) JarvisCoral else JarvisCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isScreenSharing) "Stop Screen Share" else "Share Screen",
                    color = if (isScreenSharing) JarvisCoral else JarvisCyan
                )
            }

            OutlinedButton(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf("image/*", "application/pdf", "text/*")
                    )
                },
                border = BorderStroke(1.dp, JarvisCyan),
                modifier = Modifier.testTag("attach_file_home_button")
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Select File", tint = JarvisCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Analyze Image / PDF / Doc", color = JarvisCyan)
            }

            OutlinedButton(
                onClick = { viewModel.toggleFloatingOrbOverlay() },
                border = BorderStroke(1.dp, if (isFloatingOrbRunning) JarvisEmerald else JarvisViolet),
                modifier = Modifier.testTag("floating_orb_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.BubbleChart,
                    contentDescription = "Floating Orb",
                    tint = if (isFloatingOrbRunning) JarvisEmerald else JarvisViolet,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isFloatingOrbRunning) "Orb Active" else "Floating Orb",
                    color = if (isFloatingOrbRunning) JarvisEmerald else JarvisViolet
                )
            }
        }

        // Selected File Preview Card (Section 17)
        selectedFile?.let { file ->
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                border = BorderStroke(1.dp, JarvisCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    if (file.bitmap != null) {
                        Image(
                            bitmap = file.bitmap.asImageBitmap(),
                            contentDescription = file.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Selected: ${file.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisCyan
                        )
                        Text(
                            text = "Will be sent to $activeProviderBadge when you send your prompt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.clearAttachedFile() }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove attachment", tint = JarvisCoral)
                    }
                }
            }
        }

        // 5. JARVIS Output Console Card
        Card(
            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("jarvis_response_console_card")
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
                    Text(
                        text = "JARVIS RESPONSE CONSOLE",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisCyan
                    )
                    Row {
                        IconButton(
                            onClick = { viewModel.voiceManager.speak(latestReply) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speak response aloud",
                                tint = JarvisCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(latestReply)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy response",
                                tint = JarvisTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (isGenerating) {
                    LinearProgressIndicator(
                        color = JarvisCyan,
                        trackColor = JarvisDeepNavy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = latestReply,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Verified Web Research Sources (Section 16)
                if (webSources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "VERIFIED SOURCES:",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisCyan
                    )
                    webSources.take(3).forEach { src ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        uriHandler.openUri(src.url)
                                    } catch (_: Exception) {}
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open source",
                                tint = JarvisCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${src.title} (${src.source})",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 6. Quick Action HUD Cards
        Text(
            text = "QUICK HUD ACTIONS",
            style = MaterialTheme.typography.labelMedium,
            color = JarvisTextSecondary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            QuickHudActionCard(
                title = "Time & Battery",
                subtitle = "Works Offline",
                icon = Icons.Default.BatteryChargingFull,
                onClick = { viewModel.handleUserPromptOrVoiceCommand("What time is it") },
                modifier = Modifier.weight(1f)
            )
            QuickHudActionCard(
                title = "5m Focus Timer",
                subtitle = "System + Local",
                icon = Icons.Default.Timer,
                onClick = { viewModel.createReminderOrTimer("JARVIS 5m Focus Timer", 5, "TIMER") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            QuickHudActionCard(
                title = "Multi-Step Task",
                subtitle = "AI Task Planner",
                icon = Icons.Default.PlayArrow,
                onClick = { viewModel.selectTab(JarvisNavTab.CONTROL) },
                modifier = Modifier.weight(1f)
            )
            QuickHudActionCard(
                title = "Summarize Alerts",
                subtitle = "Notification AI",
                icon = Icons.Default.NotificationsActive,
                onClick = { viewModel.summarizeActiveNotifications() },
                modifier = Modifier.weight(1f)
            )
        }

        // 7. Command Input Bar (Text Fallback & Multimodal Prompt Input)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = { Text("Ask JARVIS in Hindi, English, or Hinglish...") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("home_command_input")
            )
            Button(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        viewModel.handleUserPromptOrVoiceCommand(commandInput, fromVoice = false)
                        commandInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                modifier = Modifier.testTag("home_send_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send command",
                    tint = JarvisDeepNavy
                )
            }
        }
    }
}

@Composable
private fun QuickHudActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Surface(
                color = JarvisCyan.copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = JarvisCyan, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary, maxLines = 1)
            }
        }
    }
}
