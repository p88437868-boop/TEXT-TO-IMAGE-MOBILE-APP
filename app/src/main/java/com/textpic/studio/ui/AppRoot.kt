package com.textpic.studio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.textpic.studio.MainActivity
import com.textpic.studio.R
import com.textpic.studio.data.prefs.AppSettings
import com.textpic.studio.ui.analytics.AnalyticsScreen
import com.textpic.studio.ui.settings.ActivationScreen
import com.textpic.studio.ui.history.HistoryScreen
import com.textpic.studio.ui.home.HomeScreen
import com.textpic.studio.ui.onboarding.OnboardingScreen
import com.textpic.studio.ui.settings.HelpScreen
import com.textpic.studio.ui.settings.MoreScreen
import com.textpic.studio.ui.settings.SettingsScreen
import com.textpic.studio.ui.studio.StudioRoute
import com.textpic.studio.ui.templates.TemplatesScreen
import com.textpic.studio.ui.theme.LocalTextPicColors
import com.textpic.studio.ui.update.UpdateHost

@Composable
fun AppRoot(
    settings: AppSettings?,
    licensed: Boolean,
    licenseLoaded: Boolean,
    incomingText: String?,
    openSettings: Boolean,
    openStudio: Boolean,
    onIncomingTextConsumed: () -> Unit,
    onOpenSettingsConsumed: () -> Unit,
    onboardingDone: (Boolean) -> Unit,
) {
    val colors = LocalTextPicColors.current
    when {
        // Wait for the activation check too, so a licensed install never flashes
        // the Activation screen on a cold start.
        settings == null || !licenseLoaded -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgCanvas),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(84.dp),
            )
        }

        !settings.onboardingComplete -> OnboardingScreen(
            onComplete = {
                onboardingDone(true)
            },
        )

        else -> Box(Modifier.fillMaxSize()) {
            MainShell(
                settings = settings,
                licensed = licensed,
                incomingText = incomingText,
                openSettings = openSettings,
                openStudio = openStudio,
                onIncomingTextConsumed = onIncomingTextConsumed,
                onOpenSettingsConsumed = onOpenSettingsConsumed,
            )
            UpdateHost()
        }
    }
}

@Composable
private fun MainShell(
    settings: AppSettings,
    licensed: Boolean,
    incomingText: String?,
    openSettings: Boolean,
    openStudio: Boolean,
    onIncomingTextConsumed: () -> Unit,
    onOpenSettingsConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    var prefillText by remember { mutableStateOf<String?>(null) }
    val colors = LocalTextPicColors.current
    val tabs = listOf(
        BottomTab("home", "Home", Icons.Outlined.Home),
        BottomTab("studio", "Compose", Icons.Outlined.Create),
        BottomTab("history", "History", Icons.Outlined.History),
        BottomTab("templates", "Templates", Icons.Outlined.GridView),
        BottomTab("more", "More", Icons.Outlined.MoreHoriz),
    )

    LaunchedEffect(incomingText, openStudio, openSettings) {
        if (!incomingText.isNullOrBlank()) {
            prefillText = incomingText
            navController.navigate("studio") {
                launchSingleTop = true
            }
        } else if (openStudio) {
            navController.navigate("studio") {
                launchSingleTop = true
            }
            onIncomingTextConsumed()
        }
        if (openSettings) {
            navController.navigate("settings") {
                launchSingleTop = true
            }
            onOpenSettingsConsumed()
        }
    }

    Scaffold(
        containerColor = colors.bgCanvas,
        topBar = {
            TextPicTopBar(
                workspaceName = settings.workspaceName,
                showBack = route !in tabs.map { it.route },
                onBack = { navController.navigateUp() },
                onWorkspaceClick = { navController.navigate("settings") },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = colors.surfaceNav) {
                tabs.forEach { tab ->
                    val selected = route == tab.route ||
                        (tab.route == "more" && route in setOf("analytics", "settings", "help"))
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(tab.icon, contentDescription = tab.label)
                        },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (licensed) "home" else "activation",
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgCanvas)
                .padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    onCreate = {
                        navController.navigate("studio") {
                            launchSingleTop = true
                        }
                    },
                    onHistory = { navController.navigate("history") },
                    onAnalytics = { navController.navigate("analytics") },
                )
            }
            composable("studio") {
                StudioRoute(
                    incomingText = prefillText,
                    onIncomingTextConsumed = {
                        prefillText = null
                        onIncomingTextConsumed()
                    },
                    onHistory = { navController.navigate("history") },
                    onTemplates = { navController.navigate("templates") },
                )
            }
            composable("history") {
                HistoryScreen(
                    onOpenStudio = { text ->
                        prefillText = text
                        navController.navigate("studio") { launchSingleTop = true }
                    },
                    onCreate = {
                        navController.navigate("studio") { launchSingleTop = true }
                    },
                )
            }
            composable("templates") {
                TemplatesScreen(
                    onUseTemplate = { body ->
                        prefillText = body
                        navController.navigate("studio") { launchSingleTop = true }
                    },
                )
            }
            composable("more") {
                MoreScreen(
                    onAnalytics = { navController.navigate("analytics") },
                    onSettings = { navController.navigate("settings") },
                    onHelp = { navController.navigate("help") },
                    onActivation = { navController.navigate("activation") },
                    licensed = licensed,
                    onCreate = {
                        navController.navigate("studio") { launchSingleTop = true }
                    },
                )
            }
            composable("analytics") { AnalyticsScreen() }
            composable("activation") { ActivationScreen() }
            composable("settings") {
                SettingsScreen(
                    onFinishOnboarding = { navController.navigate("home") },
                    onOpenActivation = {
                        navController.navigate("activation") { launchSingleTop = true }
                    },
                )
            }
            composable("help") { HelpScreen() }
        }
    }
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
