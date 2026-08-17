package org.rhythmeta.maimaid.ui.settings

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OtogameImportScreen(
    container: AppContainer,
    contentTopPadding: Dp,
    onOpenLogin: () -> Unit,
) {
    val viewModel = viewModel<OtogameImportViewModel>(factory = OtogameImportViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentTopPadding + 12.dp,
            end = 16.dp,
            bottom = 96.dp,
        ),
    ) {
        item {
            OtogameControlPanel(
                state = state,
                onOpenLogin = onOpenLogin,
                onSynchronize = viewModel::synchronize,
            )
        }
    }
}

@Composable
fun OtogameLoginScreen(
    container: AppContainer,
    contentTopPadding: Dp,
) {
    val viewModel = viewModel<OtogameImportViewModel>(factory = OtogameImportViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentTopPadding),
    ) {
        if (state.isEligibleProfile) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (state.hasSession) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.Login,
                    contentDescription = null,
                    tint = if (state.hasSession) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        if (state.hasSession) R.string.otogame_session_ready
                        else R.string.otogame_session_required,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { webView?.reload() }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.otogame_reload),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                OtogameWebView(
                    modifier = Modifier.fillMaxSize(),
                    onAuthorizationHeader = viewModel::captureAuthorizationHeader,
                    onWebViewChanged = { webView = it },
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.otogame_jp_profile_required),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun OtogameControlPanel(
    state: OtogameImportUiState,
    onOpenLogin: () -> Unit,
    onSynchronize: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        !state.isEligibleProfile -> Icons.Rounded.ErrorOutline
                        state.hasSession -> Icons.Rounded.CheckCircle
                        else -> Icons.AutoMirrored.Rounded.Login
                    },
                    contentDescription = null,
                    tint = if (state.hasSession) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(
                            when {
                                !state.isEligibleProfile -> R.string.otogame_profile_ineligible
                                state.hasSession -> R.string.otogame_session_ready
                                else -> R.string.otogame_session_required
                            },
                        ),
                    )
                    Text(
                        text = state.profileName?.let {
                            stringResource(R.string.otogame_active_profile, it)
                        } ?: stringResource(R.string.otogame_no_active_profile),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Text(
                text = stringResource(R.string.otogame_import_description),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Button(
                onClick = onOpenLogin,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isEligibleProfile && !state.isBusy,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Login,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.otogame_open_login))
            }
            Button(
                onClick = onSynchronize,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isEligibleProfile && state.hasSession && !state.isBusy,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (state.isBusy) R.string.otogame_syncing else R.string.otogame_sync_action,
                    ),
                )
            }
            OtogameResult(state)
        }
    }
}

@Composable
private fun OtogameResult(state: OtogameImportUiState) {
    val outcome = state.outcome ?: return
    val isFailure = outcome == OtogameImportOutcome.LoginRequired ||
        outcome == OtogameImportOutcome.IneligibleProfile ||
        outcome == OtogameImportOutcome.Failed
    Text(
        text = stringResource(
            when (outcome) {
                OtogameImportOutcome.Imported -> R.string.otogame_result_imported
                OtogameImportOutcome.NoChanges -> R.string.otogame_result_no_changes
                OtogameImportOutcome.LoginRequired -> R.string.otogame_result_login_required
                OtogameImportOutcome.IneligibleProfile -> R.string.otogame_jp_profile_required
                OtogameImportOutcome.Failed -> R.string.otogame_result_failed
            },
        ),
        color = if (isFailure) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
    )
    if (outcome == OtogameImportOutcome.Imported || outcome == OtogameImportOutcome.NoChanges) {
        Text(
            text = stringResource(
                R.string.otogame_result_counts,
                state.fetchedCount,
                state.importedCount,
                state.duplicateCount,
                state.unmatchedCount,
            ),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OtogameWebView(
    modifier: Modifier,
    onAuthorizationHeader: (String) -> Unit,
    onWebViewChanged: (WebView?) -> Unit,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptCookie(true)
                webViewClient = OtogameWebViewClient(onAuthorizationHeader)
                doOnLayout { loadUrl(OtogameMusicUrl) }
                onWebViewChanged(this)
            }
        },
        modifier = modifier,
        onRelease = { view ->
            onWebViewChanged(null)
            view.stopLoading()
            view.destroy()
        },
    )
}

private class OtogameWebViewClient(
    private val onAuthorizationHeader: (String) -> Unit,
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
        if (request.url.host.equals(OtogameHost, ignoreCase = true)) {
            request.requestHeaders.entries
                .firstOrNull { (name) -> name.equals("Authorization", ignoreCase = true) }
                ?.value
                ?.let(onAuthorizationHeader)
        }
        return super.shouldInterceptRequest(view, request)
    }
}

private const val OtogameHost = "u.otogame.net"
private const val OtogameMusicUrl = "https://u.otogame.net/maimai/music"
