package org.rhythmeta.maimaid.ui.recommendation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.RecommendationResult
import org.rhythmeta.maimaid.core.data.ScoreRules
import org.rhythmeta.maimaid.ui.catalog.SongJacket
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.components.SongListScrollBar
import org.rhythmeta.maimaid.ui.util.ScoreStatusColors
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.res.stringResource

@Composable
internal fun RecommendationScreen(
    container: AppContainer,
    contentTopPadding: Dp,
    selectedPage: Int,
    onOpenSong: (String) -> Unit,
) {
    val viewModel: RecommendationViewModel = viewModel(
        factory = RecommendationViewModel.Factory(container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var visibleNewCount by remember { mutableIntStateOf(PageSize) }
    var visibleOldCount by remember { mutableIntStateOf(PageSize) }
    var isRefreshing by remember { mutableStateOf(false) }
    val results = if (selectedPage == RecommendationNewPage) state.response.b15 else state.response.b35
    val visibleCount = if (selectedPage == RecommendationNewPage) visibleNewCount else visibleOldCount
    val visibleResults = remember(results, visibleCount) { results.take(visibleCount) }
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(results, visibleCount, listState) {
        derivedStateOf {
            visibleCount < results.size &&
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= visibleResults.lastIndex
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            if (selectedPage == RecommendationNewPage) {
                visibleNewCount = (visibleNewCount + PageSize).coerceAtMost(results.size)
            } else {
                visibleOldCount = (visibleOldCount + PageSize).coerceAtMost(results.size)
            }
        }
    }
    LaunchedEffect(selectedPage) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(state.refreshGeneration) {
        isRefreshing = false
    }

    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refresh()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentTopPadding + 12.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    state.isLoading -> item(key = "loading", contentType = "state") {
                        RecommendationLoading()
                    }
                    results.isEmpty() -> item(key = "empty", contentType = "state") {
                        RecommendationEmpty()
                    }
                    else -> {
                        items(
                            items = visibleResults,
                            key = { it.sheet.sheetKey },
                            contentType = { "recommendation" },
                        ) { result ->
                            RecommendationRow(
                                result = result,
                                container = container,
                                onClick = { onOpenSong(result.song.songIdentifier) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
            SongListScrollBar(
                state = listState,
                trackPadding = PaddingValues(top = contentTopPadding + 12.dp, bottom = 96.dp),
            )
        }
    }
}

@Composable
internal fun RecommendationPageSwitcher(
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
                stringResource(R.string.recommendation_section_new),
                stringResource(R.string.recommendation_section_old),
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
private fun RecommendationLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(size = 28.dp, strokeWidth = 3.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.recommendation_loading),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun RecommendationEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.recommendation_empty_title),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recommendation_empty_description),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun RecommendationRow(
    result: RecommendationResult,
    container: AppContainer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val difficultyColor = SongVisualUtils.difficultyColor(
        difficulty = result.sheet.difficulty,
        type = result.sheet.type,
        darkTheme = darkTheme,
        brightenDark = true,
    )
    val typeColor = SongVisualUtils.chartTypeColor(result.sheet.type, darkTheme, difficultyColor)
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .squircleSurface(
                color = MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 16.dp,
                extension = SquircleExtension,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(52.dp)
                .width(4.dp)
                .squircleSurface(
                    color = difficultyColor,
                    cornerRadius = 2.dp,
                    extension = SquircleExtension,
                ),
        )
        Spacer(Modifier.width(10.dp))
        SongJacket(
            imageName = result.song.imageName,
            coverImageStore = container.coverImageStore,
            size = 56.dp,
            cornerRadius = 10.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = result.song.title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
            )
            result.currentAchievement?.let { achievement ->
                val rank = ScoreRules.calculateRank(achievement)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = rank,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Black,
                        color = ScoreStatusColors.rank(rank) ?: MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = String.format(Locale.ROOT, "%.4f%%", achievement),
                        style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } ?: Text(
                text = stringResource(R.string.recommendation_not_played),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = result.sheet.type.uppercase(),
                style = MiuixTheme.textStyles.footnote2.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Bold,
                color = typeColor,
                modifier = Modifier
                    .squircleSurface(
                        color = typeColor.copy(alpha = 0.13f),
                        cornerRadius = 4.dp,
                        extension = SquircleExtension,
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.padding(end = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "+${result.potentialGain}",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Black,
                color = RecommendationAccent,
            )
            Text(
                text = stringResource(R.string.recommendation_target, result.targetRank),
                style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

internal const val RecommendationNewPage = 0
private const val PageSize = 10
private val RecommendationAccent = androidx.compose.ui.graphics.Color(0xFFFF9500)
