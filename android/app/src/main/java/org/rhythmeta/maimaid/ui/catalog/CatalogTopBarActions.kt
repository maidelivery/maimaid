package org.rhythmeta.maimaid.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import org.rhythmeta.maimaid.core.data.CatalogSortOption
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
internal fun CatalogTopBarActions(
    displayMode: CatalogDisplayMode,
    sortOption: CatalogSortOption,
    sortAscending: Boolean,
    filterActive: Boolean,
    onDisplayModeChange: (CatalogDisplayMode) -> Unit,
    onSortOptionChange: (CatalogSortOption) -> Unit,
    onSortAscendingChange: (Boolean) -> Unit,
    onShowFilter: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            onDisplayModeChange(
                if (displayMode == CatalogDisplayMode.Grid) CatalogDisplayMode.List else CatalogDisplayMode.Grid,
            )
        },
    ) {
        Icon(
            imageVector = if (displayMode == CatalogDisplayMode.Grid) MiuixIcons.ListView else MiuixIcons.GridView,
            contentDescription = stringResource(
                if (displayMode == CatalogDisplayMode.Grid) {
                    R.string.catalog_display_list
                } else {
                    R.string.catalog_display_grid
                },
            ),
        )
    }

    Box {
        IconButton(onClick = { sortMenuExpanded = !sortMenuExpanded }) {
            Icon(
                imageVector = MiuixIcons.Sort,
                contentDescription = stringResource(R.string.catalog_sort_title),
            )
        }
        val options = CatalogSortOption.entries
        WindowListPopup(
            show = sortMenuExpanded,
            alignment = PopupPositionProvider.Align.End,
            enableWindowDim = false,
            onDismissRequest = { sortMenuExpanded = false },
        ) {
            ListPopupColumn {
                options.forEachIndexed { index, option ->
                    DropdownImpl(
                        text = sortOptionLabel(option),
                        optionSize = options.size,
                        isSelected = option == sortOption,
                        index = index,
                        onSelectedIndexChange = {
                            onSortOptionChange(option)
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
                            if (sortAscending) {
                                R.string.catalog_sort_ascending
                            } else {
                                R.string.catalog_sort_descending
                            },
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
            imageVector = MiuixIcons.Filter,
            contentDescription = stringResource(R.string.catalog_filter_title),
            tint = if (filterActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun sortOptionLabel(option: CatalogSortOption): String = stringResource(
    when (option) {
        CatalogSortOption.DefaultOrder -> R.string.catalog_sort_default
        CatalogSortOption.VersionAndDate -> R.string.catalog_sort_version_date
        CatalogSortOption.Difficulty -> R.string.catalog_sort_difficulty
    },
)
