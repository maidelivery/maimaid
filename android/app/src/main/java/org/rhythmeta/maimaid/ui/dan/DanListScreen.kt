package org.rhythmeta.maimaid.ui.dan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.DanCategory
import org.rhythmeta.maimaid.ui.components.SongListScrollBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DanListScreen(
    container: AppContainer,
    contentTopPadding: Dp,
    onOpenCategory: (DanCategory) -> Unit,
) {
    val unknownLabel = stringResource(R.string.common_unknown)
    val viewModel = viewModel<DanListViewModel>(
        factory = DanListViewModel.Factory(container, unknownLabel),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = contentTopPadding + 10.dp,
                end = 16.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.groups.isEmpty() -> item(key = "empty") { DanEmptyState() }
                else -> state.groups.forEach { group ->
                    item(key = "header-${group.version}") {
                        SmallTitle(
                            text = group.versionLabel,
                            insideMargin = PaddingValues(start = 4.dp, top = 10.dp, bottom = 2.dp),
                        )
                    }
                    item(key = "group-${group.version}") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp),
                            cornerRadius = 16.dp,
                        ) {
                            Column {
                                group.categories.forEach { category ->
                                    DanCategoryRow(
                                        category = category,
                                        onClick = { onOpenCategory(category) },
                                    )
                                }
                            }
                        }
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
private fun DanCategoryRow(category: DanCategory, onClick: () -> Unit) {
    BasicComponent(
        title = category.title,
        summary = stringResource(R.string.dan_section_count, category.sections.size),
        onClick = onClick,
        endActions = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.5f),
            )
        },
    )
}

@Composable
private fun DanEmptyState() {
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
