package org.rhythmeta.maimaid

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.core.LogReportExporter
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
                    onSendLogs = ::sendLogs,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchAuthIntent(intent)
    }

    private fun sendLogs() {
        lifecycleScope.launch {
            val application = application as MaimaidApplication
            runCatching { LogReportExporter.export(applicationContext, application.crashLogStore) }
                .onSuccess { uri ->
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = android.content.ClipData.newRawUri("maimaid logs", uri)
                    }, getString(R.string.logs_share_title)))
                }
                .onFailure {
                    Toast.makeText(this@MainActivity, R.string.logs_export_failed, Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun dispatchAuthIntent(intent: Intent?) {
        val url = intent?.dataString ?: return
        val container = (application as MaimaidApplication).container
        lifecycleScope.launch {
            container.backendSessionManager.handleAuthRedirect(url)
        }
    }
}
