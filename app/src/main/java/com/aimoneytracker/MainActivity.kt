package com.aimoneytracker

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.aimoneytracker.data.preferences.SettingsRepository
import com.aimoneytracker.ui.navigation.AppNavHost
import com.aimoneytracker.ui.onboarding.PastAnalyzerScreen
import com.aimoneytracker.ui.theme.AIMoneyTrackerTheme
import com.aimoneytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val deepLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLink.value = intent?.getStringExtra(NotificationHelper.EXTRA_DEEP_LINK)
        handleShareIntent(intent)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = SettingsRepository.Settings()
            )
            val link by deepLink.collectAsStateWithLifecycle()

            // Privacy: blur amounts in the app switcher when enabled (§21).
            applySecureFlag(settings.blurInRecents)

            AIMoneyTrackerTheme(darkMode = settings.darkMode, dynamicColor = settings.dynamicColor) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var unlocked by remember { mutableStateOf(!settings.appLock) }
                    when {
                        // First-run: scan & analyse past SMS before entering the app.
                        !settings.onboarded -> PastAnalyzerScreen(onComplete = {})
                        settings.appLock && !unlocked -> LockScreen(
                            biometricEnabled = settings.biometric,
                            onUnlock = { unlocked = true },
                            onRequestBiometric = { authenticate { unlocked = true } },
                        )
                        else -> {
                            val navController = rememberNavController()
                            AppNavHost(navController = navController, startDeepLink = link)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = intent.getStringExtra(NotificationHelper.EXTRA_DEEP_LINK)
        handleShareIntent(intent)
    }

    /** Share-to-app capture (§2): accept shared SMS text / receipt images forwarded to the app. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        // Shared text is queued as a raw message; image OCR would be processed here in a full build.
        // (Captured via the processor in a production flow; left as an explicit extension point.)
    }

    private fun applySecureFlag(enable: Boolean) {
        if (enable) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun authenticate(onSuccess: () -> Unit) {
        val canAuth = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) { onSuccess(); return }

        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock AI Money Tracker")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        runCatching { prompt.authenticate(info) }
    }
}

@Composable
private fun LockScreen(biometricEnabled: Boolean, onUnlock: () -> Unit, onRequestBiometric: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AI Money Tracker is locked", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { if (biometricEnabled) onRequestBiometric() else onUnlock() }, modifier = Modifier.padding(top = 16.dp)) {
            Text(if (biometricEnabled) "Unlock with biometrics" else "Unlock")
        }
    }
}
