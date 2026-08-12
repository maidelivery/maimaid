package org.rhythmeta.maimaid.ui.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.R
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    visible: Boolean,
    focusRequestToken: Int,
    backEnabled: Boolean,
    interactionSource: MutableInteractionSource,
    labelResource: Int = R.string.catalog_search_hint,
) {
    val imeVisible = WindowInsets.isImeVisible
    var imeShownSinceFocus by remember { mutableStateOf(false) }

    LaunchedEffect(imeVisible, backEnabled, expanded, query) {
        when {
            !backEnabled || !expanded -> imeShownSinceFocus = false
            imeVisible -> imeShownSinceFocus = true
            query.isEmpty() && imeShownSinceFocus -> {
                imeShownSinceFocus = false
                onExpandedChange(false)
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        SearchBar(
            inputField = {
                key(focusRequestToken) {
                    InputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = {},
                        expanded = expanded,
                        onExpandedChange = onExpandedChange,
                        interactionSource = interactionSource,
                        label = stringResource(labelResource),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            onExpandedChange = onExpandedChange,
            insideMargin = DpSize(width = 16.dp, height = 10.dp),
            expanded = expanded && backEnabled,
            content = {},
        )
    }
}
