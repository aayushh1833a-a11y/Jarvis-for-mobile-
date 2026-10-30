package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.control.PendingSensitiveAction
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCardNavy
import com.example.ui.theme.JarvisCoral
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import com.example.voice.AssistantVoiceState

@Composable
fun JarvisAnimatedOrb(
    voiceState: AssistantVoiceState,
    rmsLevel: Float,
    isGeminiLiveActive: Boolean,
    onOrbTap: () -> Unit,
    modifier: Modifier = Modifier,
    orbSize: Dp = 190.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_orb_transition")
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rotation"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val primaryColor = when {
        isGeminiLiveActive -> JarvisEmerald
        voiceState == AssistantVoiceState.LISTENING_COMMAND -> JarvisCyanBright
        voiceState == AssistantVoiceState.WAKE_WORD_ARMED -> JarvisCyan
        voiceState == AssistantVoiceState.PROCESSING -> JarvisViolet
        voiceState == AssistantVoiceState.SPEAKING -> JarvisAmber
        voiceState == AssistantVoiceState.ERROR -> JarvisCoral
        else -> JarvisCyan
    }

    val stateLabel = when {
        isGeminiLiveActive -> "GEMINI 3.8 LIVE AUDIO"
        voiceState == AssistantVoiceState.LISTENING_COMMAND -> "LISTENING FOR COMMAND"
        voiceState == AssistantVoiceState.WAKE_WORD_ARMED -> "WAKE-WORD ARMED (\"HEY JARVIS\")"
        voiceState == AssistantVoiceState.PROCESSING -> "ANALYZING & REASONING"
        voiceState == AssistantVoiceState.SPEAKING -> "SPEAKING RESPONSE"
        voiceState == AssistantVoiceState.ERROR -> "ATTENTION REQUIRED"
        else -> "TAP ORB TO SPEAK"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(orbSize)
                .clip(CircleShape)
                .clickable(onClick = onOrbTap)
                .testTag("central_jarvis_orb")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f
                val dynamicBoost = (rmsLevel * 18f).coerceAtMost(24f)

                // Ambient radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.36f),
                            JarvisViolet.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius * pulseScale
                    ),
                    radius = maxRadius
                )

                // Outer segmented telemetry ring
                rotate(degrees = outerRotation, pivot = center) {
                    for (angle in 0 until 360 step 45) {
                        drawArc(
                            color = primaryColor.copy(alpha = 0.75f),
                            startAngle = angle.toFloat(),
                            sweepAngle = 24f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Counter-rotating inner HUD arc ring
                rotate(degrees = innerRotation, pivot = center) {
                    val innerR = maxRadius * 0.74f
                    drawCircle(
                        color = JarvisViolet.copy(alpha = 0.35f),
                        radius = innerR,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    for (angle in 0 until 360 step 90) {
                        drawArc(
                            color = JarvisCyanBright.copy(alpha = 0.85f),
                            startAngle = angle.toFloat(),
                            sweepAngle = 42f,
                            useCenter = false,
                            topLeft = Offset(center.x - innerR, center.y - innerR),
                            size = androidx.compose.ui.geometry.Size(innerR * 2, innerR * 2),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Core energy sphere
                val coreRadius = (maxRadius * 0.46f * pulseScale) + dynamicBoost
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.9f),
                            primaryColor.copy(alpha = 0.35f),
                            JarvisDeepNavy.copy(alpha = 0.9f)
                        ),
                        center = center,
                        radius = coreRadius.coerceAtLeast(10f)
                    ),
                    radius = coreRadius.coerceAtmost(maxRadius * 0.68f)
                )

                drawCircle(
                    color = primaryColor,
                    radius = maxRadius * 0.44f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Center Icon inside Orb
            val centerIcon = when {
                isGeminiLiveActive -> Icons.Default.GraphicEq
                voiceState == AssistantVoiceState.SPEAKING -> Icons.Default.VolumeUp
                else -> Icons.Default.Mic
            }
            Icon(
                imageVector = centerIcon,
                contentDescription = stateLabel,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = primaryColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.55f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun Float.coerceAtmost(max: Float): Float = if (this > max) max else this

@Composable
fun JarvisStatusBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = !message.isNullOrBlank(), modifier = modifier) {
        Card(
            colors = CardDefaults.cardColors(containerColor = JarvisCardNavy),
            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("status_banner_card")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Status",
                    tint = JarvisCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("dismiss_status_banner_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss notification",
                        tint = JarvisTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenSharingActiveBanner(
    isScreenSharing: Boolean,
    latestBitmap: Bitmap?,
    onStopSharing: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = isScreenSharing, modifier = modifier) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1228)),
            border = BorderStroke(1.5.dp, JarvisCoral),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("screen_sharing_active_banner")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                if (latestBitmap != null) {
                    Image(
                        bitmap = latestBitmap.asImageBitmap(),
                        contentDescription = "Live screen preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, JarvisCoral, RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.ScreenShare,
                        contentDescription = "Screen sharing active",
                        tint = JarvisCoral,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LIVE SCREEN SHARING ACTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisCoral,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Visible screen frames are only sent to Gemini when you ask a question.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onStopSharing,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCoral),
                    modifier = Modifier.testTag("stop_screen_sharing_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.StopScreenShare,
                        contentDescription = "Stop sharing",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun SensitiveActionConfirmationDialog(
    pendingAction: PendingSensitiveAction?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (pendingAction == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisCardNavy,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Security Confirmation",
                tint = JarvisAmber,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = pendingAction.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = pendingAction.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = JarvisDeepNavy,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Action: ${pendingAction.actionType.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = JarvisCyan
                        )
                        Text(
                            text = "Target: ${pendingAction.targetValue}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (pendingAction.payloadText.isNotBlank()) {
                            Text(
                                text = "Content: ${pendingAction.payloadText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                modifier = Modifier.testTag("confirm_sensitive_action_button")
            ) {
                Text("Approve & Launch", color = JarvisDeepNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_sensitive_action_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
