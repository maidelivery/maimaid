package org.rhythmeta.maimaid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.ui.MaimaidApp
import org.rhythmeta.maimaid.ui.theme.MaimaidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dispatchAuthIntent(intent)
        setContent {
            val maimaidApplication = application as MaimaidApplication
            val mainViewModel: org.rhythmeta.maimaid.ui.MainViewModel = viewModel(
                factory = org.rhythmeta.maimaid.ui.MainViewModel.Factory(maimaidApplication.container),
            )
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
            val themeColorSource by mainViewModel.themeColorSource.collectAsStateWithLifecycle()
            val themeCustomColorArgb by mainViewModel.themeCustomColorArgb.collectAsStateWithLifecycle()
            MaimaidTheme(
                themeMode = themeMode,
                colorSource = themeColorSource,
                customColorArgb = themeCustomColorArgb,
            ) {
                MaimaidApp(
                    viewModel = mainViewModel,
                    container = maimaidApplication.container,
                    themeMode = themeMode,
                    themeColorSource = themeColorSource,
                    themeCustomColorArgb = themeCustomColorArgb,
                    onThemeModeChange = mainViewModel::setThemeMode,
                    onThemeColorSourceChange = mainViewModel::setThemeColorSource,
                    onThemeCustomColorChange = mainViewModel::setThemeCustomColorArgb,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchAuthIntent(intent)
    }

    private fun dispatchAuthIntent(intent: Intent?) {
        val url = intent?.dataString ?: return
        val container = (application as MaimaidApplication).container
        lifecycleScope.launch {
            container.backendSessionManager.handleAuthRedirect(url)
        }
    }
}
