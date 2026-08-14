package org.rhythmeta.maimaid.ui.components

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.CatalogSyncProgress
import org.rhythmeta.maimaid.core.data.CatalogSyncStage
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CatalogDownloadProgressContent(
    progress: CatalogSyncProgress,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.overallFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 180),
        label = "catalogDownloadProgress",
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = catalogSyncStageText(progress.stage),
            style = MiuixTheme.textStyles.body1,
        )
        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier.fillMaxWidth(),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = MiuixTheme.colorScheme.primary,
                backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.16f),
            ),
        )
        Text(
            text = catalogDownloadDetail(progress),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
fun catalogSyncStageText(stage: CatalogSyncStage): String = stringResource(
    when (stage) {
        CatalogSyncStage.CatalogBundle -> R.string.catalog_sync_stage_bundle
        CatalogSyncStage.ImportingCatalog -> R.string.catalog_sync_stage_importing
        CatalogSyncStage.Covers -> R.string.catalog_sync_stage_covers
        CatalogSyncStage.PresetAvatars -> R.string.catalog_sync_stage_avatars
        CatalogSyncStage.Finalizing -> R.string.catalog_sync_stage_finalizing
    },
)

@Composable
private fun catalogDownloadDetail(progress: CatalogSyncProgress): String {
    val context = LocalContext.current
    val details = buildList {
        if (progress.totalItems > 0 && progress.stage != CatalogSyncStage.CatalogBundle) {
            add(
                stringResource(
                    R.string.catalog_sync_item_progress,
                    progress.completedItems,
                    progress.totalItems,
                ),
            )
        }
        if (progress.downloadedBytes > 0L) {
            val downloaded = Formatter.formatShortFileSize(context, progress.downloadedBytes)
            val total = progress.totalBytes?.let { Formatter.formatShortFileSize(context, it) }
            add(
                if (total == null) downloaded else {
                    stringResource(R.string.catalog_sync_byte_progress, downloaded, total)
                },
            )
        }
        if (progress.stage.hasNetworkTransfer) {
            add(
                if (progress.bytesPerSecond > 0L) {
                    stringResource(
                        R.string.catalog_sync_download_speed,
                        Formatter.formatShortFileSize(context, progress.bytesPerSecond),
                    )
                } else {
                    stringResource(R.string.catalog_sync_download_speed_waiting)
                },
            )
        }
    }
    return details.joinToString(separator = "  ·  ").ifEmpty {
        stringResource(R.string.catalog_sync_processing)
    }
}

private val CatalogSyncStage.hasNetworkTransfer: Boolean
    get() = when (this) {
        CatalogSyncStage.CatalogBundle,
        CatalogSyncStage.Covers,
        CatalogSyncStage.PresetAvatars,
        -> true
        CatalogSyncStage.ImportingCatalog,
        CatalogSyncStage.Finalizing,
        -> false
    }
