package app.tsunayomi

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tsunayomi.crash.CrashReportDialog
import app.tsunayomi.crash.CrashReporter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tsunayomi.core.designsystem.theme.TsunayomiTheme
import app.tsunayomi.core.model.ThemeMode
import app.tsunayomi.navigation.TsunayomiApp
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity (not ComponentActivity) so per-app language works below Android 13.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pendingCrash = CrashReporter.pending(this)
        setContent {
            var crashReport by remember { mutableStateOf(pendingCrash) }
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
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
                TsunayomiApp()
                crashReport?.let { report ->
                    CrashReportDialog(report) {
                        CrashReporter.discard(this)
                        crashReport = null
                    }
                }
            }
        }
    }
}
