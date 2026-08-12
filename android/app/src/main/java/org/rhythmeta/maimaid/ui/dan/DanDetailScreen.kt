package org.rhythmeta.maimaid.ui.dan

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.DanChartEntry
import org.rhythmeta.maimaid.core.data.DanSectionDetail
import org.rhythmeta.maimaid.ui.catalog.SongJacket
import org.rhythmeta.maimaid.ui.components.SongListScrollBar
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DanDetailScreen(
    categoryId: String,
    container: AppContainer,
    contentTopPadding: Dp,
    selectedPage: Int,
    onTrueDanAvailabilityChanged: (Boolean) -> Unit = {},
    onOpenSong: (String) -> Unit,
) {
    val viewModel = viewModel<DanDetailViewModel>(
        key = categoryId,
        factory = DanDetailViewModel.Factory(categoryId, container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val regularListState = rememberLazyListState()
    val trueDanListState = rememberLazyListState()
    val listState = if (selectedPage == DanRegularPage) regularListState else trueDanListState
    val visibleSections = state.detail?.sections.orEmpty().filter { section ->
        isTrueDanSection(section.title) == (selectedPage == DanTruePage)
    }
    LaunchedEffect(state.detail) {
        onTrueDanAvailabilityChanged(
            state.detail?.sections.orEmpty().any { section -> isTrueDanSection(section.title) },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp,
                top = contentTopPadding + 10.dp,
                end = 14.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 72.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                state.detail == null -> item(key = "empty") {
                    DanDetailEmptyState()
                }
                visibleSections.isEmpty() -> item(key = "empty") {
                    DanDetailEmptyState()
                }
                else -> visibleSections.forEachIndexed { index, section ->
                    item(key = "section-$index-${section.title}") {
                        DanSectionCard(
                            categoryTitle = state.detail!!.category.title,
                            section = section,
                            container = container,
                            onOpenSong = onOpenSong,
                        )
                    }
                }
            }
        }
        SongListScrollBar(
            state = listState,
            trackPadding = PaddingValues(top = contentTopPadding + 10.dp, bottom = 36.dp),
        )
    }
}

@Composable
internal fun DanPageSwitcher(
    selectedPage: Int,
    visible: Boolean,
    onSelectedPageChange: (Int) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        TabRowWithContour(
            tabs = listOf(
                stringResource(R.string.dan_tab_regular),
                stringResource(R.string.dan_tab_true),
            ),
            selectedTabIndex = selectedPage,
            onTabSelected = onSelectedPageChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            minWidth = 112.dp,
            maxWidth = 180.dp,
        )
    }
}

@Composable
private fun DanSectionCard(
    categoryTitle: String,
    section: DanSectionDetail,
    container: AppContainer,
    onOpenSong: (String) -> Unit,
) {
    val themeTitle = section.title ?: categoryTitle
    val masterTheme = themeTitle.contains("master", true) || isAdvancedDan(themeTitle)
    val accent = if (masterTheme) Color(0xFFA34EE4) else Color(0xFFE87500)
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            section.title?.takeIf(String::isNotBlank)?.let { title ->
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .squircleSurface(
                            color = accent.copy(alpha = 0.11f),
                            cornerRadius = 14.dp,
                            extension = SquircleExtension,
                        )
                        .squircleBorder(
                            width = 1.dp,
                            color = accent.copy(alpha = 0.16f),
                            cornerRadius = 14.dp,
                            extension = SquircleExtension,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            section.description?.takeIf(String::isNotBlank)?.let { DanRequirements(it) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                section.charts.forEach { entry ->
                    DanSongRow(entry, container, onOpenSong)
                }
            }
        }
    }
}

@Composable
private fun DanRequirements(raw: String) {
    val parts = remember(raw) {
        raw.split('｜').map(String::trim).filter(String::isNotBlank)
    }
    val damage = parts.getOrNull(1)?.split('/')?.map(String::trim).orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            parts.getOrNull(0)?.let {
                RequirementBadge("Life ${it.replace("❤", "").replace("♥", "").trim()}", Color(0xFFE75480))
            }
            parts.getOrNull(2)?.let { RequirementBadge("Heal $it", Color(0xFF2E9D61)) }
        }
        if (damage.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                RequirementBadge("Great ${damage.getOrNull(0) ?: "-0"}", Color(0xFFE75480))
                RequirementBadge("Good ${damage.getOrNull(1) ?: "-0"}", Color(0xFF2E9D61))
                RequirementBadge("Miss ${damage.getOrNull(2) ?: "-0"}", Color(0xFF777777))
            }
        }
    }
}

@Composable
private fun RequirementBadge(text: String, tint: Color) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
        fontWeight = FontWeight.Bold,
        color = tint,
        maxLines = 1,
        modifier = Modifier
            .squircleSurface(
                color = tint.copy(alpha = 0.1f),
                cornerRadius = 50.dp,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 1.dp,
                color = tint.copy(alpha = 0.16f),
                cornerRadius = 50.dp,
                extension = SquircleExtension,
            )
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun DanSongRow(
    entry: DanChartEntry,
    container: AppContainer,
    onOpenSong: (String) -> Unit,
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val difficultyColor = SongVisualUtils.difficultyColor(
        difficulty = entry.reference.difficulty,
        type = entry.reference.type,
        darkTheme = darkTheme,
        brightenDark = true,
        fallbackColor = MiuixTheme.colorScheme.primary,
    )
    val typeColor = SongVisualUtils.chartTypeColor(
        type = entry.reference.type,
        darkTheme = darkTheme,
        fallbackColor = difficultyColor,
    )
    val song = entry.song
    val rowModifier = if (song != null) {
        Modifier.clickable { onOpenSong(song.songIdentifier) }
    } else {
        Modifier
    }

    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .height(76.dp)
            .squircleSurface(
                color = MiuixTheme.colorScheme.surfaceContainerHigh,
                cornerRadius = 15.dp,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 1.dp,
                color = difficultyColor.copy(alpha = if (song == null) 0.08f else 0.12f),
                cornerRadius = 15.dp,
                extension = SquircleExtension,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .padding(vertical = 6.dp)
                .squircleSurface(
                    color = difficultyColor.copy(alpha = if (song == null) 0.5f else 1f),
                    cornerRadius = 2.dp,
                    extension = SquircleExtension,
                ),
        )
        if (song != null) {
            SongJacket(
                imageName = song.imageName,
                coverImageStore = container.coverImageStore,
                size = 50.dp,
                cornerRadius = 12.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .squircleSurface(
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        cornerRadius = 12.dp,
                        extension = SquircleExtension,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.QuestionMark,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = song?.title ?: entry.reference.title,
                style = MiuixTheme.textStyles.body1.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = if (song == null) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(spacing = MarqueeSpacing(24.dp)),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = entry.reference.type.chartTypeLabel(),
                    style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .squircleSurface(
                            color = typeColor,
                            cornerRadius = 5.dp,
                            extension = SquircleExtension,
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
                Text(
                    text = entry.reference.difficulty.difficultyLabel(),
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.SemiBold,
                    color = difficultyColor,
                    maxLines = 1,
                )
                entry.sheet?.let { sheet ->
                    Text(
                        text = "Lv.${sheet.internalLevel?.takeIf(String::isNotBlank) ?: sheet.level}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
                entry.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        text = description,
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2),
                        modifier = Modifier
                            .squircleSurface(
                                color = Color(0xFF1976D2).copy(alpha = 0.1f),
                                cornerRadius = 50.dp,
                                extension = SquircleExtension,
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val resultText = when {
                song == null -> stringResource(R.string.dan_missing)
                entry.score == null -> stringResource(R.string.dan_no_record)
                else -> String.format(Locale.ROOT, "%.4f%%", entry.score.achievement)
            }
            Text(
                text = resultText,
                style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = if (entry.score == null) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
                maxLines = 1,
                modifier = if (entry.score == null) {
                    Modifier
                } else {
                    Modifier
                        .squircleSurface(
                            color = difficultyColor.copy(alpha = 0.12f),
                            cornerRadius = 50.dp,
                            extension = SquircleExtension,
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                },
            )
            if (song != null) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.5f),
                )
            } else {
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DanDetailEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.dan_empty),
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.dan_empty_description),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun String.chartTypeLabel(): String = when (lowercase(Locale.ROOT)) {
    "std", "standard" -> "STD"
    else -> uppercase(Locale.ROOT)
}

private fun String.difficultyLabel(): String = when (lowercase(Locale.ROOT)) {
    "basic" -> "Basic"
    "advanced" -> "Advanced"
    "expert" -> "Expert"
    "master" -> "Master"
    "remaster" -> "Re:M"
    else -> replaceFirstChar { it.titlecase(Locale.ROOT) }
}

private fun isAdvancedDan(title: String): Boolean {
    val labels = listOf(
        "真", "裏皆伝", "裏皆传", "里皆伝", "里皆传", "超", "檄", "橙", "暁", "晓", "桃",
        "櫻", "樱", "紫", "菫", "白", "雪", "輝", "辉", "熊", "華", "华", "爽", "煌", "舞", "霸",
    )
    return labels.any(title::contains)
}

private fun isTrueDanSection(title: String?): Boolean {
    val value = title.orEmpty()
    return value.contains("真") ||
        value.contains("裏皆伝") ||
        value.contains("裏皆传") ||
        value.contains("里皆伝") ||
        value.contains("里皆传")
}

internal const val DanRegularPage = 0
internal const val DanTruePage = 1
