package app.tsunayomi

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tsunayomi.core.designsystem.theme.TsunayomiTheme
import app.tsunayomi.core.model.ThemeMode
import app.tsunayomi.crash.CrashReportDialog
import app.tsunayomi.crash.CrashReporter
import app.tsunayomi.feature.settings.OnboardingScreen
import app.tsunayomi.navigation.TsunayomiApp
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity (not ComponentActivity) so per-app language works below Android 13.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** Set when launched from the "new chapters" notification; the UI opens the Updates tab and clears it. */
    private val openUpdatesState = mutableStateOf(false)
    private var openUpdatesRequest: Boolean
        get() = openUpdatesState.value
        set(value) {
            openUpdatesState.value = value
        }

    private var checkedInitialLock = false
    private val isLockedState = mutableStateOf(false)
    private var backgroundTimestamp: Long = -1L
    private var isPromptShowing = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.wantsUpdates()) openUpdatesRequest = true
    }

    private fun Intent?.wantsUpdates() = this?.getStringExtra("app.tsunayomi.open") == "updates"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            isLockedState.value = savedInstanceState.getBoolean(KEY_IS_LOCKED, false)
            checkedInitialLock = savedInstanceState.getBoolean(KEY_CHECKED_INITIAL, false)
        }
        enableEdgeToEdge()
        val pendingCrash = CrashReporter.pending(this)
        openUpdatesRequest = intent.wantsUpdates()
        setContent {
            var crashReport by remember { mutableStateOf(pendingCrash) }
            val openUpdates by remember { openUpdatesState }
            val isLocked by remember { isLockedState }
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent

            // App Lock initialization on cold start or when settings change
            LaunchedEffect(current.appLockEnabled) {
                if (!checkedInitialLock) {
                    checkedInitialLock = true
                    if (current.appLockEnabled) {
                        isLockedState.value = true
                        promptBiometricUnlock()
                    }
                } else if (!current.appLockEnabled) {
                    isLockedState.value = false
                }
            }

            // Secure screen flag (disallows screenshots & recent app preview)
            DisposableEffect(current.secureScreen) {
                if (current.secureScreen) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose {}
            }

            val darkTheme = when (current.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // System bar icons follow the app theme, not the system one: a forced dark theme needs light icons.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }
            TsunayomiTheme(
                themeMode = current.themeMode,
                dynamicColor = current.dynamicColor,
                pureBlack = current.pureBlack,
            ) {
                if (!current.onboardingCompleted) {
                    OnboardingScreen(onFinish = viewModel::completeOnboarding)
                } else {
                    Box(Modifier.fillMaxSize()) {
                        TsunayomiApp(openUpdates = openUpdates, onUpdatesOpened = { openUpdatesRequest = false })
                        if (isLocked) {
                            AppLockScreen(onUnlock = ::promptBiometricUnlock)
                        }
                        if (!isLocked) {
                            crashReport?.let { report ->
                                CrashReportDialog(report) {
                                    CrashReporter.discard(this@MainActivity)
                                    crashReport = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_LOCKED, isLockedState.value)
        outState.putBoolean(KEY_CHECKED_INITIAL, checkedInitialLock)
    }

    override fun onStop() {
        super.onStop()
        backgroundTimestamp = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val bg = backgroundTimestamp
        backgroundTimestamp = -1L
        if (bg > 0L) {
            val current = viewModel.settings.value ?: return
            if (current.appLockEnabled) {
                val elapsedMs = System.currentTimeMillis() - bg
                val timeoutMs = current.appLockTimeoutMinutes * 60 * 1000L
                if (elapsedMs >= timeoutMs) {
                    isLockedState.value = true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isLockedState.value) {
            promptBiometricUnlock()
        }
    }

    private fun promptBiometricUnlock() {
        if (isPromptShowing || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometrics or credentials configured on device; unlock without locking user out.
            isLockedState.value = false
            return
        }

        isPromptShowing = true
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isPromptShowing = false
                isLockedState.value = false
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                isPromptShowing = false
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_prompt_title))
            .setSubtitle(getString(R.string.app_lock_prompt_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }

    companion object {
        private const val KEY_IS_LOCKED = "app.tsunayomi.is_locked"
        private const val KEY_CHECKED_INITIAL = "app.tsunayomi.checked_initial_lock"
    }
}
