package net.krtl.maimaid

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.krtl.maimaid.domain.model.AppPreferencesState
import net.krtl.maimaid.domain.model.ThemeMode
import net.krtl.maimaid.ui.app.MaimaidApp
import net.krtl.maimaid.ui.app.StartupGate
import net.krtl.maimaid.ui.theme.MaimaidTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MaimaidApplication).container
        intent?.dataString?.let { url ->
            lifecycleScope.launch {
                container.authRepository.handleRedirect(url)
            }
        }
        enableEdgeToEdge()
        setContent {
            val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = AppPreferencesState()
            )
            MaimaidTheme(
                darkTheme = when (preferences.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
                dynamicColor = preferences.dynamicColorEnabled
            ) {
                StartupGate(container) {
                    MaimaidApp(container)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val container = (application as MaimaidApplication).container
        intent.dataString?.let { url ->
            lifecycleScope.launch {
                container.authRepository.handleRedirect(url)
            }
        }
    }
}
