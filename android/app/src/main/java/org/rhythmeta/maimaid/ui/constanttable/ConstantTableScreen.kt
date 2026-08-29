package org.rhythmeta.maimaid.ui.constanttable

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.StaticAssetUrls
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.ConstantTableEntry
import org.rhythmeta.maimaid.core.data.ConstantTableSection
import org.rhythmeta.maimaid.core.data.ScoreRules
import org.rhythmeta.maimaid.ui.components.SongListScrollBar
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.components.squircleShape
import org.rhythmeta.maimaid.ui.util.ScoreStatusColors
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ConstantTableScreen(
    container: AppContainer,
    contentTopPadding: Dp,
    listState: LazyListState,
    onOpenSong: (String) -> Unit,
) {
    val viewModel = viewModel<ConstantTableViewModel>(factory = ConstantTableViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    var isExporting by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = contentTopPadding + 12.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.response.availableBaseLevels.isEmpty()) {
                item(key = "empty") { ConstantTableEmpty() }
            } else {
                item(key = "settings") {
                    ConstantTableSettings(
                        availableBaseLevels = state.response.availableBaseLevels,
                        selectedBaseLevel = state.selectedBaseLevel,
                        includeScores = state.includeScores,
                        onSelectBaseLevel = viewModel::selectBaseLevel,
                        onIncludeScoresChange = viewModel::setIncludeScores,
                    )
                }
                item(key = "summary") {
                    ConstantTableSummary(
                        chartCount = state.chartCount,
                        sectionCount = state.sections.size,
                    )
                }
                item(key = "export") {
                    Button(
                        onClick = {
                            val baseLevel = state.selectedBaseLevel ?: return@Button
                            isExporting = true
                            scope.launch {
                                shareConstantTable(
                                    context = context,
                                    baseLevel = baseLevel,
                                    sections = state.sections,
                                    includeScores = state.includeScores,
                                    userName = state.response.userName,
                                    container = container,
                                    darkTheme = darkTheme,
                                )
                                isExporting = false
                            }
                        },
                        enabled = state.sections.isNotEmpty() && !isExporting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        insideMargin = PaddingValues(vertical = 14.dp),
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            text = stringResource(
                                if (isExporting) R.string.constant_table_exporting
                                else R.string.constant_table_export,
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                state.sections.forEachIndexed { index, section ->
                    item(key = "section-${section.levelLabel}") {
                        ConstantTableSectionView(
                            section = section,
                            index = index,
                            includeScores = state.includeScores,
                            container = container,
                            onOpenSong = onOpenSong,
                        )
                    }
                }
            }
        }
        SongListScrollBar(
            state = listState,
            trackPadding = PaddingValues(top = contentTopPadding + 12.dp, bottom = 32.dp),
        )
    }
}

@Composable
private fun ConstantTableSettings(
    availableBaseLevels: List<Int>,
    selectedBaseLevel: Int?,
    includeScores: Boolean,
    onSelectBaseLevel: (Int) -> Unit,
    onIncludeScoresChange: (Boolean) -> Unit,
) {
    val labels = availableBaseLevels.map(org.rhythmeta.maimaid.core.data.ConstantTableCalculator::baseLevelLabel)
    SmallTitle(
        text = stringResource(R.string.constant_table_settings),
        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            WindowDropdownPreference(
                items = labels,
                selectedIndex = availableBaseLevels.indexOf(selectedBaseLevel).coerceAtLeast(0),
                title = stringResource(R.string.constant_table_level),
                onSelectedIndexChange = { index -> onSelectBaseLevel(availableBaseLevels[index]) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { onIncludeScoresChange(!includeScores) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.constant_table_include_scores),
                        style = MiuixTheme.textStyles.body1,
                    )
                    Text(
                        text = stringResource(R.string.constant_table_include_scores_summary),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Switch(checked = includeScores, onCheckedChange = onIncludeScoresChange)
            }
        }
    }
}

@Composable
private fun ConstantTableSummary(chartCount: Int, sectionCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.constant_table_regular_only), style = MiuixTheme.textStyles.body1)
                Text(
                    stringResource(R.string.constant_table_regular_only_summary),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = stringResource(R.string.constant_table_count, chartCount, sectionCount),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

@Composable
private fun ConstantTableSectionView(
    section: ConstantTableSection,
    index: Int,
    includeScores: Boolean,
    container: AppContainer,
    onOpenSong: (String) -> Unit,
) {
    val levelColor = constantLevelColor(section.levelLabel, index)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = section.levelLabel,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                color = levelColor,
            )
            Text(
                text = stringResource(R.string.constant_table_section_count, section.entries.size),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .squircleSurface(
                    color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (index % 2 == 0) 0.82f else 0.62f),
                    cornerRadius = 14.dp,
                    extension = SquircleExtension,
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            section.entries.chunked(PreviewColumns).forEach { rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowEntries.forEach { entry ->
                        Box(modifier = Modifier.weight(1f)) {
                            ConstantTableJacket(
                                entry = entry,
                                includeScores = includeScores,
                                container = container,
                                onClick = { onOpenSong(entry.songIdentifier) },
                            )
                        }
                    }
                    repeat(PreviewColumns - rowEntries.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ConstantTableJacket(
    entry: ConstantTableEntry,
    includeScores: Boolean,
    container: AppContainer,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val difficultyColor = SongVisualUtils.difficultyColor(entry.difficulty, entry.type, darkTheme)
    val cachedCover = remember(entry.imageName) { container.coverImageStore.fileFor(entry.imageName) }
    val coverModel = remember(context, cachedCover, entry.imageName) {
        ImageRequest.Builder(context)
            .data(cachedCover ?: StaticAssetUrls.coverUrl(entry.imageName))
            .build()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = entry.songTitle }
            .clip(squircleShape(9.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = coverModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (includeScores) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                entry.rank?.let { ConstantTableBadge(it, ScoreStatusColors.rank(it)) }
                entry.fc?.let {
                    ConstantTableBadge(ScoreRules.displayFc(it) ?: it.uppercase(), ScoreStatusColors.combo(it))
                }
                entry.fs?.let {
                    val normalized = ScoreRules.displayFs(it) ?: it.uppercase()
                    ConstantTableBadge(
                        if (normalized == "S") "SYNC" else normalized,
                        ScoreStatusColors.sync(it),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .squircleBorder(
                    width = 1.5.dp,
                    color = difficultyColor,
                    cornerRadius = 9.dp,
                    extension = SquircleExtension,
                ),
        )
    }
}

@Composable
private fun ConstantTableBadge(text: String, color: Color?) {
    val resolved = color ?: MiuixTheme.colorScheme.onSurfaceVariantActions
    Text(
        text = text,
        color = Color.White,
        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 7.sp),
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .squircleSurface(color = resolved.copy(alpha = 0.96f), cornerRadius = 3.dp)
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

@Composable
private fun ConstantTableEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.constant_table_empty), style = MiuixTheme.textStyles.title3)
        Text(
            stringResource(R.string.constant_table_empty_description),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private suspend fun shareConstantTable(
    context: Context,
    baseLevel: Int,
    sections: List<ConstantTableSection>,
    includeScores: Boolean,
    userName: String?,
    container: AppContainer,
    darkTheme: Boolean,
) {
    val file = ConstantTableImageExporter.renderToCache(
        context = context,
        baseLevel = baseLevel,
        sections = sections,
        includeScores = includeScores,
        userName = userName,
        coverImageStore = container.coverImageStore,
        darkTheme = darkTheme,
    ) ?: return
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null,
        ),
    )
}

private fun constantLevelColor(label: String, index: Int): Color = when (((label.toDoubleOrNull() ?: 0.0) * 10).toInt() % 10) {
    0, 5 -> Color(0xFFD34A63)
    1, 6 -> Color(0xFF4D78FF)
    2, 7 -> Color(0xFF3F9B74)
    3, 8 -> Color(0xFFB45BFF)
    else -> if (index % 2 == 0) Color(0xFFC84A7B) else Color(0xFF5489FF)
}

private const val PreviewColumns = 5
