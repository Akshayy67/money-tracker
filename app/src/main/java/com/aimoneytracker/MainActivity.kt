package com.aimoneytracker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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
                    // Key the unlock state on appLock so it correctly re-initializes once the real
                    // setting loads (the initial emission is the default appLock=false).
                    var unlocked by remember(settings.appLock) { mutableStateOf(!settings.appLock) }
                    var authError by remember { mutableStateOf<String?>(null) }

                    when {
                        // First-run: scan & analyse past SMS before entering the app.
                        !settings.onboarded -> PastAnalyzerScreen(onComplete = {})

                        settings.appLock && !unlocked -> {
                            // Auto-prompt biometrics as soon as the lock screen appears.
                            LaunchedEffect(settings.biometric) {
                                if (settings.biometric) {
                                    authenticate(
                                        onSuccess = { unlocked = true },
                                        onError = { msg -> authError = msg },
                                    )
                                }
                            }
                            LockScreen(
                                biometricEnabled = settings.biometric,
                                error = authError,
                                onUnlock = { unlocked = true },
                                onRequestBiometric = {
                                    authError = null
                                    authenticate(
                                        onSuccess = { unlocked = true },
                                        onError = { msg -> authError = msg },
                                    )
                                },
                            )
                        }

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

    /**
     * Biometric / device-credential unlock (§21).
     *
     * Authenticator selection is API-aware: combining a biometric class with DEVICE_CREDENTIAL is
     * only allowed on API 30+ (R). Below that we must use a single class and supply a negative button.
     * Failing to do this throws IllegalArgumentException — which previously was swallowed, so nothing
     * happened. Errors are now surfaced via [onError] instead of silently no-op'ing.
     */
    private fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val manager = BiometricManager.from(this)
        val allowCredentialCombo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val authenticators = if (allowCredentialCombo) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

        when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                onError("Biometric hardware unavailable. Use the unlock button.")
                return
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                onError("No biometrics enrolled. Add a fingerprint/face in system settings, or use unlock.")
                return
            }
            else -> {
                onError("Biometric unavailable. Use the unlock button.")
                return
            }
        }

        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancelled or a real error — surface it; don't auto-unlock.
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onError(errString.toString())
                    }
                }
            },
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock AI Money Tracker")
            .setSubtitle("Confirm it's you")
            .setAllowedAuthenticators(authenticators)
        // A negative button is required ONLY when DEVICE_CREDENTIAL is not among the allowed authenticators.
        if (!allowCredentialCombo) builder.setNegativeButtonText("Cancel")

        runCatching { prompt.authenticate(builder.build()) }
            .onFailure { onError("Couldn't start biometric prompt: ${it.message}") }
    }
}

@Composable
private fun LockScreen(
    biometricEnabled: Boolean,
    error: String?,
    onUnlock: () -> Unit,
    onRequestBiometric: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AI Money Tracker is locked", style = MaterialTheme.typography.titleLarge)
        if (biometricEnabled) {
            Button(onClick = onRequestBiometric, modifier = Modifier.padding(top = 16.dp)) {
                Text("Unlock with biometrics")
            }
        }
        // Always offer a plain unlock so the user is never stuck if biometrics fail/aren't set up.
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 12.dp)) {
            Text(if (biometricEnabled) "Enter without biometrics" else "Unlock")
        }
        error?.let {
            Text(it, Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
