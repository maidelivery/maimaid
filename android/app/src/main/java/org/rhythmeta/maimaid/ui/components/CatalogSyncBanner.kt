package org.rhythmeta.maimaid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.CatalogSyncStatus
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CatalogSyncBanner(
    status: CatalogSyncStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (status) {
                    CatalogSyncStatus.Idle -> Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                    )
                    CatalogSyncStatus.Checking,
                    is CatalogSyncStatus.Downloading,
                    -> CircularProgressIndicator(size = 20.dp, strokeWidth = 3.dp)
                    is CatalogSyncStatus.Failed -> Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.error,
                    )
                    is CatalogSyncStatus.Ready -> Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = syncStatusText(status),
                    style = MiuixTheme.textStyles.body2,
                )
            }
            if (status is CatalogSyncStatus.Failed) {
                TextButton(
                    text = stringResource(R.string.action_retry),
                    onClick = onRetry,
                    minWidth = 48.dp,
                    minHeight = 34.dp,
                    insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun syncStatusText(status: CatalogSyncStatus): String = when (status) {
    CatalogSyncStatus.Idle -> stringResource(R.string.catalog_sync_idle)
    CatalogSyncStatus.Checking -> stringResource(R.string.catalog_sync_checking)
    is CatalogSyncStatus.Downloading -> catalogSyncStageText(status.progress.stage)
    is CatalogSyncStatus.Ready -> stringResource(R.string.catalog_sync_ready, status.version)
    is CatalogSyncStatus.Failed -> stringResource(R.string.catalog_sync_failed)
}
