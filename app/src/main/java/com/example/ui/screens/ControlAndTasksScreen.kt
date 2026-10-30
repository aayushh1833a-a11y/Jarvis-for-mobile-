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
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.control.PendingSensitiveAction
import com.example.control.SensitiveActionType
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
fun ControlAndTasksScreen(
    viewModel: JarvisViewModel
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf(
        "Multi-Step Tasks",
        "Google & Apps",
        "MCP Servers",
        "Phone & Apps",
        "Timers & Alarms",
        "Notifications"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Sub-Tab Selector
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            subTabs.forEachIndexed { idx, label ->
                FilterChip(
                    selected = (selectedSubTab == idx),
                    onClick = { selectedSubTab = idx },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                        selectedLabelColor = JarvisCyan
                    ),
                    modifier = Modifier.testTag("control_subtab_$idx")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (selectedSubTab) {
            0 -> MultiStepTaskPlannerSection(viewModel)
            1 -> GoogleIntegrationsSection(viewModel)
            2 -> McpServerManagementSection(viewModel)
            3 -> PhoneAndInstalledAppsSection(viewModel)
            4 -> RemindersTimersAlarmsSection(viewModel)
            5 -> NotificationsAndAccessibilitySection(viewModel)
        }
    }
}

@Composable
private fun MultiStepTaskPlannerSection(viewModel: JarvisViewModel) {
    val activePlan by viewModel.activeTaskPlan.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isTaskRunning by viewModel.isTaskRunning.collectAsState()
    val isTaskPaused by viewModel.isTaskPaused.collectAsState()
    var goalInput by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
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
                        Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Multi-Step AI Task Planner", style = MaterialTheme.typography.titleLarge, color = JarvisCyan)
                    }
                    Text(
                        text = "Describe a multi-step goal. JARVIS will break it down into concrete steps, highlight required permissions, ask confirmation for sensitive steps, and execute them in sequence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it },
                        label = { Text("Task Goal (e.g. 'Prepare a 10m study session & open Calculator')") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("multistep_goal_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (goalInput.isNotBlank()) {
                                    viewModel.planMultiStepTask(goalInput)
                                }
                            },
                            enabled = !isGenerating,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            modifier = Modifier.testTag("plan_multistep_task_button")
                        ) {
                            Text("Generate Step Plan", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                goalInput = "Set a 15 minute focus timer, save my study routine to memory, and open Wi-Fi settings"
                                viewModel.planMultiStepTask(goalInput)
                            }
                        ) {
                            Text("Try Sample Task", color = JarvisCyan)
                        }
                    }

                    if (isGenerating) {
                        LinearProgressIndicator(
                            color = JarvisCyan,
                            trackColor = JarvisDeepNavy,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        activePlan?.let { plan ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                    border = BorderStroke(1.dp, JarvisViolet),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Plan: ${plan.goal}", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                        Text(plan.summary, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.executeActiveTaskPlan() },
                                enabled = !isTaskRunning,
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisEmerald),
                                modifier = Modifier.testTag("execute_task_plan_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Execute", tint = JarvisDeepNavy, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Execute Steps", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                            }

                            if (isTaskRunning) {
                                OutlinedButton(
                                    onClick = { viewModel.pauseOrResumeTaskPlan() }
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause/Resume", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isTaskPaused) "Resume" else "Pause")
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.cancelActiveTaskPlan() },
                                border = BorderStroke(1.dp, JarvisCoral),
                                modifier = Modifier.testTag("cancel_task_plan_button")
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = JarvisCoral, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", color = JarvisCoral)
                            }
                        }
                    }
                }
            }

            items(plan.steps, key = { it.stepNumber }) { step ->
                val statusColor = when (step.status) {
                    "COMPLETED" -> JarvisEmerald
                    "RUNNING" -> JarvisCyan
                    "FAILED" -> JarvisCoral
                    "SKIPPED" -> JarvisAmber
                    else -> JarvisTextSecondary
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Step ${step.stepNumber}: ${step.title}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                color = statusColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = step.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(step.description, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                        Text(
                            text = "Action: ${step.actionType} (${step.actionTarget}) • Permission: ${step.requiresPermission}" +
                                if (step.requiresConfirmation) " • Requires Confirmation" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisCyan
                        )
                        if (step.resultNote.isNotBlank()) {
                            Text(
                                text = "Outcome: ${step.resultNote}",
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneAndInstalledAppsSection(viewModel: JarvisViewModel) {
    val installedApps by viewModel.installedApps.collectAsState()
    var appFilter by remember { mutableStateOf("") }
    var dialInput by remember { mutableStateOf("") }
    var shareInput by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, appFilter) {
        if (appFilter.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(appFilter, ignoreCase = true) ||
                it.packageName.contains(appFilter, ignoreCase = true)
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Quick System Settings Panel
        item {
            Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Android System Settings Shortcuts", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("Wi-Fi" to "wifi", "Bluetooth" to "bluetooth", "Display" to "display", "Sound" to "sound", "Battery" to "battery").forEach { (label, key) ->
                            OutlinedButton(onClick = { viewModel.controlExecutor.openSystemSettings(key) }) {
                                Text(label, color = JarvisCyan)
                            }
                        }
                    }
                }
            }
        }

        // Sensitive Actions with Mandatory Confirmation
        item {
            Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Phone Dialer & Android Share (Confirmation Protected)", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dialInput,
                            onValueChange = { dialInput = it },
                            label = { Text("Phone Number") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (dialInput.isNotBlank()) {
                                    viewModel.requestSensitiveAction(
                                        PendingSensitiveAction(
                                            title = "Confirm Phone Dial",
                                            description = "JARVIS will open the Android Phone Dialer with this number upon confirmation.",
                                            actionType = SensitiveActionType.DIAL_NUMBER,
                                            targetValue = dialInput
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Dial", tint = JarvisDeepNavy, modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = shareInput,
                            onValueChange = { shareInput = it },
                            label = { Text("Text to Share via Android Share Sheet") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (shareInput.isNotBlank()) {
                                    viewModel.requestSensitiveAction(
                                        PendingSensitiveAction(
                                            title = "Confirm Share Content",
                                            description = "Open Android Share Sheet with the text below.",
                                            actionType = SensitiveActionType.SHARE_TEXT,
                                            targetValue = "Android Share Sheet",
                                            payloadText = shareInput
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisViolet)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Installed Applications Launcher
        item {
            Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Installed Applications (${filteredApps.size})", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                    }
                    OutlinedTextField(
                        value = appFilter,
                        onValueChange = { appFilter = it },
                        label = { Text("Search installed apps...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        items(filteredApps.take(40), key = { it.packageName }) { app ->
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.controlExecutor.launchAppByPackage(app.packageName) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, style = MaterialTheme.typography.titleMedium)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                    }
                    Icon(Icons.Default.Launch, contentDescription = "Open ${app.appName}", tint = JarvisCyan)
                }
            }
        }
    }
}

@Composable
private fun RemindersTimersAlarmsSection(viewModel: JarvisViewModel) {
    val reminders by viewModel.reminders.collectAsState()
    var titleInput by remember { mutableStateOf("") }
    var minutesInput by remember { mutableStateOf("10") }
    var alarmHourInput by remember { mutableStateOf("07") }
    var alarmMinInput by remember { mutableStateOf("30") }

    val fmt = remember { SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault()) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AlarmAdd, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Reminder, System Timer, or Alarm", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                    }
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Label (e.g. Call team, Workout, Tea timer)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reminder_title_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = minutesInput,
                            onValueChange = { minutesInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Minutes") },
                            singleLine = true,
                            modifier = Modifier.width(100.dp)
                        )
                        Button(
                            onClick = {
                                val mins = minutesInput.toIntOrNull() ?: 10
                                viewModel.createReminderOrTimer(titleInput, mins, "REMINDER")
                                titleInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            modifier = Modifier.testTag("add_reminder_button")
                        ) {
                            Text("+ Reminder", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                val mins = minutesInput.toIntOrNull() ?: 5
                                viewModel.createReminderOrTimer(titleInput, mins, "TIMER")
                                titleInput = ""
                            },
                            modifier = Modifier.testTag("add_timer_button")
                        ) {
                            Text("+ System Timer", color = JarvisCyan)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = alarmHourInput,
                            onValueChange = { alarmHourInput = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Hour (0-23)") },
                            singleLine = true,
                            modifier = Modifier.width(100.dp)
                        )
                        OutlinedTextField(
                            value = alarmMinInput,
                            onValueChange = { alarmMinInput = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Min (0-59)") },
                            singleLine = true,
                            modifier = Modifier.width(100.dp)
                        )
                        Button(
                            onClick = {
                                val h = alarmHourInput.toIntOrNull() ?: 7
                                val m = alarmMinInput.toIntOrNull() ?: 30
                                viewModel.createAlarmItem(titleInput, h, m)
                                titleInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisViolet)
                        ) {
                            Text("Set Alarm")
                        }
                    }
                }
            }
        }

        items(reminders, key = { it.id }) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Checkbox(
                        checked = item.isCompleted,
                        onCheckedChange = { viewModel.toggleReminderCompleted(item) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "[${item.type}] ${item.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (item.isCompleted) JarvisTextSecondary else JarvisCyan
                        )
                        Text(
                            text = "Scheduled: ${fmt.format(Date(item.triggerAtMillis))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.deleteReminder(item.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete reminder", tint = JarvisCoral)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsAndAccessibilitySection(viewModel: JarvisViewModel) {
    val notifications by viewModel.capturedNotifications.collectAsState()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Notification Assistant (User-Authorized)", style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                    }
                    Text(
                        text = "JARVIS only reads notifications after you explicitly grant Notification Access in Android Settings. You can exclude any app or disable access anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.summarizeActiveNotifications() },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                        ) {
                            Text("Summarize Notifications", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.controlExecutor.openSystemSettings("notifications") }
                        ) {
                            Text("Notification Access Settings")
                        }
                    }
                }
            }
        }

        if (notifications.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("No active notifications captured yet.", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Enable 'JARVIS Notification Assistant' in Android Notification Access settings to see and summarize incoming alerts here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary
                        )
                    }
                }
            }
        } else {
            items(notifications, key = { it.key }) { notif ->
                Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(notif.appTitle, style = MaterialTheme.typography.titleMedium, color = JarvisCyan)
                            OutlinedButton(
                                onClick = { viewModel.toggleExcludeNotificationPackage(notif.packageName) }
                            ) {
                                Text("Exclude App", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(notif.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Accessibility & Screen Interaction Disclosure Card (Section 10)
        item {
            Card(colors = CardDefaults.cardColors(containerColor = JarvisCardNavy)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = JarvisViolet)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Accessibility & Screen Awareness Boundaries", style = MaterialTheme.typography.titleMedium, color = JarvisViolet)
                    }
                    Text(
                        text = "• Live Visual Intelligence uses Android's official MediaProjection prompt ('Share Screen' on the Home tab) so you stay in 100% control of when JARVIS sees your screen.\n" +
                            "• JARVIS never bypasses passwords, biometric prompts, or payment confirmations, and never secretly monitors app usage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                    OutlinedButton(
                        onClick = { viewModel.controlExecutor.openSystemSettings("accessibility") }
                    ) {
                        Text("Open Android Accessibility Settings", color = JarvisCyan)
                    }
                }
            }
        }
    }
}
