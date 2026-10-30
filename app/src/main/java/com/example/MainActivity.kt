package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.configureAppCheck
import com.example.ui.JarvisNavTab
import com.example.ui.JarvisViewModel
import com.example.ui.components.JarvisStatusBanner
import com.example.ui.components.ScreenSharingActiveBanner
import com.example.ui.components.SensitiveActionConfirmationDialog
import com.example.ui.screens.ChatSectionScreen
import com.example.ui.screens.ControlAndTasksScreen
import com.example.ui.screens.HomeHudScreen
import com.example.ui.screens.MemoryVaultScreen
import com.example.ui.screens.OnboardingWizardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.JarvisCardNavy
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisSurfaceNavy
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory Firebase App Check Configuration with Launch Intent
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        // STEP 2: Render JARVIS Native Compose Application
        setContent {
            MyApplicationTheme {
                Surface(
                    color = JarvisDeepNavy,
                    modifier = Modifier.fillMaxSize()
                ) {
                    JarvisRootApp()
                }
            }
        }
    }
}

private data class NavDestinationItem(
    val tab: JarvisNavTab,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun JarvisRootApp(
    viewModel: JarvisViewModel = viewModel()
) {
    val showOnboarding by viewModel.showOnboarding.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val statusBanner by viewModel.statusBannerMessage.collectAsState()
    val isScreenSharing by viewModel.isScreenSharingActive.collectAsState()
    val latestScreenBitmap by viewModel.latestScreenBitmap.collectAsState()
    val pendingSensitiveAction by viewModel.pendingSensitiveAction.collectAsState()

    if (showOnboarding) {
        OnboardingWizardScreen(
            viewModel = viewModel,
            onFinish = { viewModel.completeOnboarding() }
        )
        return
    }

    // Handle system Back press from non-Home tabs
    BackHandler(enabled = currentTab != JarvisNavTab.HOME) {
        viewModel.selectTab(JarvisNavTab.HOME)
    }

    val navItems = listOf(
        NavDestinationItem(JarvisNavTab.HOME, R.string.nav_home, Icons.Default.Home, "nav_tab_home"),
        NavDestinationItem(JarvisNavTab.CHAT, R.string.nav_chat, Icons.AutoMirrored.Filled.Chat, "nav_tab_chat"),
        NavDestinationItem(JarvisNavTab.CONTROL, R.string.nav_control, Icons.Default.Tune, "nav_tab_control"),
        NavDestinationItem(JarvisNavTab.MEMORY, R.string.nav_memory, Icons.Default.Memory, "nav_tab_memory"),
        NavDestinationItem(JarvisNavTab.SETTINGS, R.string.nav_settings, Icons.Default.Settings, "nav_tab_settings")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = JarvisDeepNavy,
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        containerColor = JarvisSurfaceNavy,
                        tonalElevation = 8.dp
                    ) {
                        navItems.forEach { item ->
                            val selected = currentTab == item.tab
                            val label = stringResource(id = item.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = JarvisDeepNavy,
                                    selectedTextColor = JarvisCyan,
                                    indicatorColor = JarvisCyan,
                                    unselectedIconColor = JarvisTextSecondary,
                                    unselectedTextColor = JarvisTextSecondary
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(JarvisDeepNavy)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = JarvisSurfaceNavy,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { item ->
                            val selected = currentTab == item.tab
                            val label = stringResource(id = item.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = JarvisDeepNavy,
                                    selectedTextColor = JarvisCyan,
                                    indicatorColor = JarvisCyan,
                                    unselectedIconColor = JarvisTextSecondary,
                                    unselectedTextColor = JarvisTextSecondary
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Persistent Live Screen Sharing Indicator Banner
                    ScreenSharingActiveBanner(
                        isScreenSharing = isScreenSharing,
                        latestBitmap = latestScreenBitmap,
                        onStopSharing = { viewModel.stopScreenSharing() }
                    )

                    // Status & Fallback Notification Banner
                    JarvisStatusBanner(
                        message = statusBanner,
                        onDismiss = { viewModel.dismissStatusBanner() }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
                            JarvisNavTab.HOME -> HomeHudScreen(viewModel = viewModel)
                            JarvisNavTab.CHAT -> ChatSectionScreen(viewModel = viewModel)
                            JarvisNavTab.CONTROL -> ControlAndTasksScreen(viewModel = viewModel)
                            JarvisNavTab.MEMORY -> MemoryVaultScreen(viewModel = viewModel)
                            JarvisNavTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Sensitive Actions (Phone Dialing, SMS, Share)
    SensitiveActionConfirmationDialog(
        pendingAction = pendingSensitiveAction,
        onConfirm = { viewModel.confirmPendingSensitiveAction() },
        onDismiss = { viewModel.dismissPendingSensitiveAction() }
    )
}
