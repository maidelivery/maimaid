package org.rhythmeta.maimaid.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.ImportSyncResolution
import org.rhythmeta.maimaid.ui.common.openInAppBrowser
import org.rhythmeta.maimaid.ui.components.appTextFieldColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun DivingFishImportScreen(
    container: AppContainer,
    contentTopPadding: Dp,
) {
    val context = LocalContext.current
    val viewModel = viewModel<ScoreImportViewModel>(factory = ScoreImportViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScoreImportPage(contentTopPadding = contentTopPadding) {
        item {
            ImportSummaryCard(
                icon = if (state.hasDivingFishAccount) Icons.Rounded.CheckCircle else Icons.Rounded.AccountCircle,
                title = stringResource(
                    if (state.hasDivingFishAccount) R.string.import_df_connected
                    else R.string.import_df_summary_title,
                ),
                summary = state.divingFishUsername ?: stringResource(
                    if (state.hasDivingFishAccount) R.string.import_df_connected_description
                    else R.string.import_df_summary_description,
                ),
            )
        }
        item {
            ImportSection(stringResource(R.string.import_df_oauth_section)) {
                Text(
                    text = stringResource(R.string.import_df_oauth_description),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (state.hasDivingFishAccount && !state.divingFishCanWrite) {
                    Text(
                        text = stringResource(R.string.import_df_write_pending),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                if (state.hasDivingFishAccount) {
                    ImportPrimaryButton(
                        title = stringResource(R.string.import_df_quick_sync),
                        icon = Icons.Rounded.Refresh,
                        busy = state.isBusy,
                        enabled = true,
                        onClick = viewModel::quickImportDivingFish,
                    )
                }
                Button(
                    onClick = {
                        viewModel.authorizeAndImportDivingFish { url -> context.openInAppBrowser(url) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy && state.profile != null,
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (state.hasDivingFishAccount) R.string.import_df_reconnect
                            else R.string.import_df_connect_import,
                        ),
                    )
                }
                if (state.hasDivingFishAccount) {
                    Button(
                        onClick = viewModel::disconnectDivingFish,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy,
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.errorContainer,
                            contentColor = MiuixTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_df_disconnect))
                    }
                }
            }
        }
        importStatusItem(state)
    }
    ImportConflictDialog(state, viewModel)
}

@Composable
fun LxnsImportScreen(
    container: AppContainer,
    contentTopPadding: Dp,
) {
    val context = LocalContext.current
    val viewModel = viewModel<ScoreImportViewModel>(factory = ScoreImportViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScoreImportPage(contentTopPadding = contentTopPadding) {
        item {
            ImportSummaryCard(
                icon = if (state.hasLxnsAccount) Icons.Rounded.CheckCircle else Icons.Rounded.Link,
                title = stringResource(
                    if (state.hasLxnsAccount) R.string.import_lxns_connected
                    else R.string.import_lxns_summary_title,
                ),
                summary = stringResource(
                    if (state.hasLxnsAccount) R.string.import_lxns_connected_description
                    else R.string.import_lxns_summary_description,
                ),
            )
        }
        if (state.hasLxnsAccount) {
            item {
                ImportSection(stringResource(R.string.import_lxns_account_section)) {
                    ImportPrimaryButton(
                        title = stringResource(R.string.import_lxns_quick_sync),
                        icon = Icons.Rounded.Refresh,
                        busy = state.isBusy,
                        enabled = true,
                        onClick = viewModel::quickImportLxns,
                    )
                    Button(
                        onClick = viewModel::disconnectLxns,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy,
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.errorContainer,
                            contentColor = MiuixTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_lxns_disconnect))
                    }
                }
            }
        } else {
            item {
                ImportSection(stringResource(R.string.import_lxns_authorize_section)) {
                    Button(
                        onClick = {
                            val url = viewModel.createLxnsAuthorizationUrl()
                            if (!context.openInAppBrowser(url)) {
                                Toast.makeText(context, R.string.cloud_browser_unavailable, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy,
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_lxns_open_browser))
                    }
                    Text(
                        text = stringResource(R.string.import_lxns_authorize_description),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            item {
                ImportSection(stringResource(R.string.import_lxns_code_section)) {
                    TextField(
                        value = state.lxnsAuthorizationCode,
                        onValueChange = viewModel::setLxnsAuthorizationCode,
                        colors = appTextFieldColors(),
                        label = stringResource(R.string.import_lxns_code),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    ImportPrimaryButton(
                        title = stringResource(R.string.import_lxns_connect_import),
                        icon = Icons.AutoMirrored.Rounded.Login,
                        busy = state.isBusy,
                        enabled = state.profile != null && state.lxnsAuthorizationCode.isNotBlank(),
                        onClick = viewModel::exchangeLxnsCodeAndImport,
                    )
                }
            }
        }
        importStatusItem(state)
    }
    ImportConflictDialog(state, viewModel)
}

@Composable
private fun ScoreImportPage(
    contentTopPadding: Dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentTopPadding + 12.dp,
            end = 16.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ImportSummaryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(20.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MiuixTheme.textStyles.title3)
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun ImportSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column {
        SmallTitle(text = title, insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(14.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun ImportPrimaryButton(
    title: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.CloudDownload,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && !busy,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        if (busy) {
            CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(title)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.importStatusItem(state: ScoreImportUiState) {
    if (state.phase == ScoreImportPhase.Idle && state.result == null) return
    item {
        ImportStatusCard(state)
    }
}

@Composable
private fun ImportStatusCard(state: ScoreImportUiState) {
    val failed = state.result == ScoreImportResult.Failed ||
        state.result == ScoreImportResult.LoginRequired ||
        state.result == ScoreImportResult.TokenExpired
    val color = if (failed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
    val title = when {
        state.phase != ScoreImportPhase.Idle -> phaseTitle(state.phase)
        state.result == ScoreImportResult.Imported -> stringResource(R.string.import_status_imported)
        state.result == ScoreImportResult.NoChanges -> stringResource(R.string.import_status_no_changes)
        state.result == ScoreImportResult.LoginRequired -> stringResource(R.string.import_status_login_required)
        state.result == ScoreImportResult.TokenExpired -> stringResource(R.string.import_status_token_expired)
        else -> stringResource(R.string.import_status_failed)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when {
                    state.phase != ScoreImportPhase.Idle -> Icons.Rounded.Sync
                    failed -> Icons.Rounded.ErrorOutline
                    else -> Icons.Rounded.CheckCircle
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = color)
                if (state.fetchedCount > 0) {
                    Text(
                        stringResource(
                            R.string.import_status_counts,
                            state.fetchedCount,
                            state.upsertedCount,
                            state.skippedCount,
                        ),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                state.resultDetails?.takeIf { failed && it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        if (state.phase != ScoreImportPhase.Idle) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun phaseTitle(phase: ScoreImportPhase): String = stringResource(
    when (phase) {
        ScoreImportPhase.Idle -> R.string.import_status_connecting
        ScoreImportPhase.CheckingSession -> R.string.import_status_checking_session
        ScoreImportPhase.Connecting -> R.string.import_status_connecting
        ScoreImportPhase.RefreshingToken -> R.string.import_status_refreshing_token
        ScoreImportPhase.ExchangingToken -> R.string.import_status_exchanging_token
        ScoreImportPhase.Fetching -> R.string.import_status_fetching
        ScoreImportPhase.CheckingConflicts -> R.string.import_status_checking_conflicts
        ScoreImportPhase.Applying -> R.string.import_status_applying
    },
)

@Composable
private fun ImportConflictDialog(
    state: ScoreImportUiState,
    viewModel: ScoreImportViewModel,
) {
    val preview = state.conflictPreview ?: return
    WindowDialog(
        show = true,
        title = stringResource(R.string.import_conflict_title),
        summary = stringResource(
            R.string.import_conflict_summary,
            preview.localOnlyCount,
            preview.differentCount,
        ),
        onDismissRequest = viewModel::dismissConflict,
        outsideMargin = DpSize(24.dp, 24.dp),
    ) {
        val choices = listOf(
            Triple(ImportSyncResolution.MergeBest, Icons.Rounded.Merge, R.string.import_conflict_merge),
            Triple(ImportSyncResolution.KeepLocal, Icons.Rounded.CloudUpload, R.string.import_conflict_keep_local),
            Triple(ImportSyncResolution.UseImport, Icons.Rounded.CloudDownload, R.string.import_conflict_use_import),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (resolution, icon, title) ->
                Button(
                    onClick = { viewModel.resolveConflict(resolution) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isResolvingConflict,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(title))
                }
            }
            if (state.isResolvingConflict) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
