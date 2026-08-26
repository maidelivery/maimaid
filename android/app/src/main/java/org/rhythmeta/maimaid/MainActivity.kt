package org.rhythmeta.maimaid

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.core.LogReportExporter
import org.rhythmeta.maimaid.ui.MaimaidApp
import org.rhythmeta.maimaid.ui.navigation.AppDetail
import org.rhythmeta.maimaid.ui.theme.MaimaidTheme
import org.rhythmeta.maimaid.widget.WidgetDestinationExtra

class MainActivity : ComponentActivity() {
    private val widgetDetail = mutableStateOf<AppDetail?>(null)
    private val widgetHomeRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyWidgetRoute(intent)
        dispatchAuthIntent(intent)
        dispatchCollectionIntent(intent)
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
                    initialDetail = widgetDetail.value,
                    resetToHome = widgetHomeRequest.value,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyWidgetRoute(intent)
        dispatchAuthIntent(intent)
        dispatchCollectionIntent(intent)
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

    private fun dispatchCollectionIntent(intent: Intent?) {
        val url = intent?.dataString ?: return
        val isCollectionLink = org.rhythmeta.maimaid.core.data.SongCollectionCodec.extractToken(url) != null ||
            org.rhythmeta.maimaid.core.data.SongCollectionCodec.extractCollectionId(url) != null
        if (!isCollectionLink) return
        val container = (application as MaimaidApplication).container
        lifecycleScope.launch {
            runCatching {
                val collection = container.collectionSharingService.resolveImport(url)
                container.songCollectionRepository.importCollection(collection)
            }.onSuccess {
                Toast.makeText(this@MainActivity, R.string.collections_import_success, Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, R.string.collections_import_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyWidgetRoute(intent: Intent?) {
        val route = intent?.getStringExtra(WidgetDestinationExtra)
        widgetHomeRequest.value = route == "home"
        widgetDetail.value = route
            ?.takeIf { it == "best50" }
            ?.let { AppDetail.BestTable }
    }
}
