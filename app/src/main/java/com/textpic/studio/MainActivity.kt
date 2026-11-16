package com.textpic.studio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import com.textpic.studio.data.license.LicenseSnapshot
import com.textpic.studio.data.prefs.AppSettings
import kotlinx.coroutines.launch
import com.textpic.studio.ui.AppRoot
import com.textpic.studio.ui.theme.TextPicTheme

class MainActivity : ComponentActivity() {
    private var incomingText by mutableStateOf<String?>(null)
    private var openSettings by mutableStateOf(false)
    private var openStudio by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val container = (application as TextPicApplication).container
        setContent {
            val settings by remember(container) {
                container.settingsRepository.settings
            }.collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
            val license by remember(container) {
                container.licenseService.state
            }.collectAsStateWithLifecycle(LicenseSnapshot())

            TextPicTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    AppRoot(
                        settings = settings,
                        licensed = license.isLicensed,
                        licenseLoaded = license.loaded,
                        incomingText = incomingText,
                        openSettings = openSettings,
                        openStudio = openStudio,
                        onIncomingTextConsumed = {
                            incomingText = null
                            openStudio = false
                        },
                        onOpenSettingsConsumed = { openSettings = false },
                        onboardingDone = { complete ->
                            container.appScope.launch {
                                runCatching {
                                    container.settingsRepository.setOnboardingComplete(complete)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val extraText = intent.getCharSequenceExtra(EXTRA_INCOMING_TEXT)?.toString()
            ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!extraText.isNullOrBlank()) {
            incomingText = extraText
            openStudio = true
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            openSettings = true
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_STUDIO, false)) {
            openStudio = true
        }
    }

    companion object {
        const val EXTRA_INCOMING_TEXT = "com.textpic.studio.INCOMING_TEXT"
        const val EXTRA_OPEN_SETTINGS = "com.textpic.studio.OPEN_SETTINGS"
        const val EXTRA_OPEN_STUDIO = "com.textpic.studio.OPEN_STUDIO"
    }
}
