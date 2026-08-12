package org.rhythmeta.maimaid.ui.scorequery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.ScoreQueryDisplayMode
import org.rhythmeta.maimaid.core.data.ScoreQuerySortMode
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
internal fun ScoreQueryTopBarActions(
    displayMode: ScoreQueryDisplayMode,
    sortMode: ScoreQuerySortMode,
    sortAscending: Boolean,
    filterActive: Boolean,
    onDisplayModeChange: (ScoreQueryDisplayMode) -> Unit,
    onSortModeChange: (ScoreQuerySortMode) -> Unit,
    onSortAscendingChange: (Boolean) -> Unit,
    onShowFilter: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            onDisplayModeChange(
                if (displayMode == ScoreQueryDisplayMode.Grid) {
                    ScoreQueryDisplayMode.List
                } else {
                    ScoreQueryDisplayMode.Grid
                },
            )
        },
    ) {
        Icon(
            imageVector = if (displayMode == ScoreQueryDisplayMode.Grid) {
                Icons.AutoMirrored.Rounded.ViewList
            } else {
                Icons.Rounded.GridView
            },
            contentDescription = stringResource(
                if (displayMode == ScoreQueryDisplayMode.Grid) {
                    R.string.score_query_display_list
                } else {
                    R.string.score_query_display_grid
                },
            ),
        )
    }
    Box {
        IconButton(onClick = { sortMenuExpanded = !sortMenuExpanded }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = stringResource(R.string.score_query_sort_title),
            )
        }
        WindowListPopup(
            show = sortMenuExpanded,
            alignment = PopupPositionProvider.Align.End,
            enableWindowDim = true,
            onDismissRequest = { sortMenuExpanded = false },
        ) {
            ListPopupColumn {
                ScoreQuerySortMode.entries.forEachIndexed { index, option ->
                    DropdownImpl(
                        text = sortModeLabel(option),
                        optionSize = ScoreQuerySortMode.entries.size,
                        isSelected = option == sortMode,
                        index = index,
                        onSelectedIndexChange = {
                            onSortModeChange(option)
                            sortMenuExpanded = false
                        },
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            onSortAscendingChange(!sortAscending)
                            sortMenuExpanded = false
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (sortAscending) "↑" else "↓",
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(
                            if (sortAscending) R.string.score_query_sort_ascending else R.string.score_query_sort_descending,
                        ),
                        style = MiuixTheme.textStyles.body1,
                    )
                }
            }
        }
    }
    IconButton(
        onClick = {
            sortMenuExpanded = false
            onShowFilter()
        },
    ) {
        Icon(
            imageVector = Icons.Rounded.FilterList,
            contentDescription = stringResource(R.string.score_query_filter_title),
            tint = if (filterActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun sortModeLabel(mode: ScoreQuerySortMode): String = stringResource(
    when (mode) {
        ScoreQuerySortMode.Rating -> R.string.score_query_sort_rating
        ScoreQuerySortMode.Achievement -> R.string.score_query_sort_achievement
        ScoreQuerySortMode.Level -> R.string.score_query_sort_level
    },
)
