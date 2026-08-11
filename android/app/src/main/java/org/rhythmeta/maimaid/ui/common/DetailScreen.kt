package org.rhythmeta.maimaid.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.BuildConfig
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.ui.MainUiState
import org.rhythmeta.maimaid.ui.components.CatalogSyncBanner
import org.rhythmeta.maimaid.ui.navigation.AppDetail
import org.rhythmeta.maimaid.ui.song.SongDetailScreen
import org.rhythmeta.maimaid.core.data.Best50State
import org.rhythmeta.maimaid.core.data.RatingUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DetailScreen(
    detail: AppDetail,
    state: MainUiState,
    selectedSongId: String?,
    onRetrySync: () -> Unit,
    container: AppContainer,
    songContentTopPadding: androidx.compose.ui.unit.Dp,
    onSongDetailBackgroundChanged: (androidx.compose.ui.graphics.Color?) -> Unit,
    onSongDetailTitleChanged: (String) -> Unit,
) {
    when (detail) {
        AppDetail.Song -> SongDetailScreen(
            song = state.songs.firstOrNull { it.songIdentifier == selectedSongId },
            container = container,
            contentTopPadding = songContentTopPadding,
            onBackgroundChanged = onSongDetailBackgroundChanged,
            onTitleChanged = onSongDetailTitleChanged,
        )
        AppDetail.StaticData -> StaticDataDetail(state = state, onRetrySync = onRetrySync)
        AppDetail.RandomSong -> RandomSongDetail(songs = state.songs)
        AppDetail.BestTable -> BestTableDetail(state = state, container = container)
        AppDetail.About -> AboutDetail()
        else -> FeatureDetail(state = state)
    }
}

@Composable
private fun StaticDataDetail(state: MainUiState, onRetrySync: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CatalogSyncBanner(status = state.catalogSyncStatus, onRetry = onRetrySync)
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 14.dp,
            insideMargin = PaddingValues(16.dp),
        ) {
            DetailValueRow(stringResource(R.string.static_source), stringResource(R.string.static_source_backend))
            DetailValueRow(
                stringResource(R.string.static_local_data),
                stringResource(R.string.detail_catalog_summary, state.songCount, state.sheetCount),
            )
            DetailValueRow("API", BuildConfig.BACKEND_URL)
            Text(
                text = stringResource(R.string.static_bundle_format),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        TextButton(
            text = stringResource(R.string.action_refresh),
            onClick = onRetrySync,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

@Composable
private fun RandomSongDetail(songs: List<SongEntity>) {
    if (songs.isEmpty()) {
        EmptyCatalogState()
        return
    }
    var selected by remember(songs) { mutableStateOf(songs.random()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = selected.title,
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = selected.artist,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(
            text = stringResource(R.string.action_randomize),
            onClick = { selected = songs.random() },
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

@Composable
private fun BestTableDetail(state: MainUiState, container: AppContainer) {
    val best50 by container.best50Repository.observeBest50().collectAsStateWithLifecycle(initialValue = Best50State())
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                insideMargin = PaddingValues(16.dp),
            ) {
                Text(
                    text = best50.total.toString(),
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.best50_total),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = stringResource(R.string.best50_capacity, best50.b35.size, best50.b15.size),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        item {
            TextButton(
                text = stringResource(R.string.best50_export),
                onClick = { shareBest50(context, best50) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
        if (best50.isEmpty) {
            item { Text(text = stringResource(R.string.detail_no_scores), color = MiuixTheme.colorScheme.onBackgroundVariant) }
        } else {
            item { SmallTitle(text = stringResource(R.string.best50_new, best50.b15.size)) }
            items(best50.b15, key = { it.sheetKey }) { Best50Row(it) }
            item { SmallTitle(text = stringResource(R.string.best50_old, best50.b35.size)) }
            items(best50.b35, key = { it.sheetKey }) { Best50Row(it) }
        }
    }
}

@Composable
private fun Best50Row(entry: RatingUtils.Entry) {
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp, insideMargin = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${entry.difficulty} ${entry.type.uppercase()} · ${"%.4f".format(entry.achievement)}%",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = entry.rating.toString(), fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
                Text(text = "Lv ${"%.1f".format(entry.level)}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

private fun shareBest50(context: android.content.Context, state: Best50State) {
    val bitmap = Bitmap.createBitmap(1200, 180 + (state.b35.size + state.b15.size) * 70, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 42f }
    canvas.drawText("maimaid Best 50  ${state.total}", 48f, 64f, paint)
    var y = 130f
    (listOf("B15" to state.b15, "B35" to state.b35)).forEach { (label, entries) ->
        paint.textSize = 30f
        canvas.drawText(label, 48f, y, paint)
        y += 42f
        paint.textSize = 24f
        entries.forEach { entry ->
            canvas.drawText("${entry.rating}  ${entry.title.take(36)}  ${"%.4f".format(entry.achievement)}%", 72f, y, paint)
            y += 58f
        }
    }
    val file = File(context.cacheDir, "best50.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }.getOrNull() ?: return
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, null))
}

@Composable
private fun AboutDetail() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(R.string.app_name), style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Bold)
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.about_runtime), color = MiuixTheme.colorScheme.onBackgroundVariant)
    }
}

@Composable
private fun FeatureDetail(state: MainUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_catalog_summary, state.songCount, state.sheetCount),
            style = MiuixTheme.textStyles.title3,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.songCount == 0) {
                stringResource(R.string.detail_catalog_required)
            } else {
                stringResource(R.string.detail_foundation_ready)
            },
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun EmptyCatalogState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_catalog_required),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun DetailValueRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.width(16.dp))
        Text(text = value, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
