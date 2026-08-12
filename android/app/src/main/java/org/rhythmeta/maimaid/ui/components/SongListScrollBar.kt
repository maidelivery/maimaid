package org.rhythmeta.maimaid.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun BoxScope.SongListScrollBar(
    state: LazyListState,
    trackPadding: PaddingValues = PaddingValues.Zero,
) {
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(state),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight(),
        trackPadding = trackPadding,
    )
}
