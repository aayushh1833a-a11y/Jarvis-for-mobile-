package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.ui.JarvisViewModel
import com.example.ui.theme.JarvisCardNavy
import com.example.ui.theme.JarvisCoral
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisElevatedNavy
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet

@Composable
fun ChatSectionScreen(
    viewModel: JarvisViewModel
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val sessions by viewModel.chatSessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val messages by viewModel.activeSessionMessages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val streamingPartial by viewModel.streamingPartialReply.collectAsState()
    val selectedFile by viewModel.selectedFilePayload.collectAsState()

    var messageText by remember { mutableStateOf("") }
    var sessionToRename by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streamingPartial) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachUserSelectedFile(uri)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.voiceManager.startSingleCommandListening()
        }
    }

    val activeSession = sessions.firstOrNull { it.id == activeSessionId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 1. Conversation Selector Bar (New Chat, History Chips, Rename, Delete)
        Card(
            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "AI CONVERSATIONS (${sessions.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisCyan
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { viewModel.createNewChatSession("Chat ${sessions.size + 1}") },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("new_chat_session_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New Chat", tint = JarvisDeepNavy, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Chat", color = JarvisDeepNavy, style = MaterialTheme.typography.labelMedium)
                        }

                        if (activeSession != null) {
                            IconButton(
                                onClick = {
                                    sessionToRename = activeSession
                                    renameInput = activeSession.title
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("rename_chat_session_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename Conversation", tint = JarvisCyan, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { viewModel.deleteChatSession(activeSession.id) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("delete_chat_session_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Conversation", tint = JarvisCoral, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    sessions.forEach { session ->
                        val isSelected = session.id == activeSessionId
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectChatSession(session.id) },
                            label = { Text(session.title, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                                selectedLabelColor = JarvisCyan
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Scrollable Message History
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("chat_messages_list")
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageBubble(
                    message = msg,
                    onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                    onSpeak = { viewModel.voiceManager.speak(msg.content) },
                    onRetry = { viewModel.retryLastMessage() }
                )
            }

            if (isGenerating && streamingPartial.isNotBlank()) {
                item {
                    ChatMessageBubble(
                        message = ChatMessageEntity(
                            id = -1L,
                            sessionId = activeSessionId ?: 0L,
                            role = "model",
                            content = streamingPartial,
                            providerUsed = "Streaming..."
                        ),
                        onCopy = {},
                        onSpeak = {},
                        onRetry = {}
                    )
                }
            }
        }

        if (isGenerating) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                LinearProgressIndicator(
                    color = JarvisCyan,
                    trackColor = JarvisCardNavy,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { viewModel.stopActiveGeneration() },
                    border = BorderStroke(1.dp, JarvisCoral),
                    modifier = Modifier.testTag("chat_stop_generation_button")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop Generation", tint = JarvisCoral, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop", color = JarvisCoral, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Attached file chip
        selectedFile?.let { file ->
            Surface(
                color = JarvisCardNavy,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, JarvisCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${file.displayName} (${file.mimeType})",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisCyan,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearAttachedFile() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove attachment", tint = JarvisCoral, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // 3. Chat Input Bar (Attach, Voice, Text, Send)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            IconButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf("image/*", "application/pdf", "text/*"))
                },
                modifier = Modifier.testTag("chat_attach_file_button")
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach file or image", tint = JarvisCyan)
            }

            IconButton(
                onClick = {
                    val hasMic = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        viewModel.voiceManager.startSingleCommandListening()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.testTag("chat_voice_input_button")
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = JarvisCyan)
            }

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Message JARVIS...") },
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )

            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        viewModel.handleUserPromptOrVoiceCommand(messageText, fromVoice = false)
                        messageText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                modifier = Modifier.testTag("chat_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = JarvisDeepNavy)
            }
        }
    }

    // Rename Dialog
    sessionToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            containerColor = JarvisCardNavy,
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Conversation Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameChatSession(target, renameInput)
                        sessionToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Save", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { sessionToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessageEntity,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onRetry: () -> Unit
) {
    val isUser = message.role == "user"
    val bubbleColor = when {
        message.isError -> JarvisCoral.copy(alpha = 0.16f)
        isUser -> JarvisViolet.copy(alpha = 0.24f)
        else -> JarvisElevatedNavy
    }
    val borderColor = when {
        message.isError -> JarvisCoral
        isUser -> JarvisViolet.copy(alpha = 0.6f)
        else -> JarvisCyan.copy(alpha = 0.35f)
    }

    Box(
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isUser) "YOU" else "JARVIS • ${message.providerUsed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) JarvisViolet else JarvisCyan
                    )
                }

                if (!message.attachmentName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = JarvisDeepNavy,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "📎 ${message.attachmentName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = formatMarkdownAnnotatedString(message.content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!isUser && message.id > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onCopy)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy response", tint = JarvisTextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onSpeak)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Speak response", tint = JarvisTextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Speak", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onRetry)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry response", tint = JarvisCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", style = MaterialTheme.typography.labelSmall, color = JarvisCyan)
                        }
                    }
                }
            }
        }
    }
}

private fun formatMarkdownAnnotatedString(raw: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = raw.split("\n")
        lines.forEachIndexed { lineIdx, line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ") -> {
                    withStyle(SpanStyle(color = JarvisCyan, fontWeight = FontWeight.Bold)) {
                        append(trimmed.trimStart('#', ' '))
                    }
                }
                trimmed.startsWith("```") -> {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = JarvisTextSecondary)) {
                        append(line)
                    }
                }
                else -> {
                    // Parse inline **bold** and `code`
                    var i = 0
                    while (i < line.length) {
                        if (line.startsWith("**", i)) {
                            val end = line.indexOf("**", i + 2)
                            if (end != -1) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = JarvisCyan)) {
                                    append(line.substring(i + 2, end))
                                }
                                i = end + 2
                                continue
                            }
                        } else if (line[i] == '`') {
                            val end = line.indexOf('`', i + 1)
                            if (end != -1) {
                                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = JarvisCyan)) {
                                    append(line.substring(i + 1, end))
                                }
                                i = end + 1
                                continue
                            }
                        }
                        append(line[i])
                        i++
                    }
                }
            }
            if (lineIdx < lines.lastIndex) append("\n")
        }
    }
}
