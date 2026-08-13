package org.rhythmeta.maimaid.ui.community

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.CommunityAliasVotingBoardItem
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.ui.catalog.SongJacket
import org.rhythmeta.maimaid.ui.components.SongListScrollBar
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CommunityAliasScreen(
    container: AppContainer,
    songs: List<SongEntity>,
    contentTopPadding: Dp,
) {
    val viewModel = viewModel<CommunityAliasBoardViewModel>(
        factory = CommunityAliasBoardViewModel.Factory(container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val songsById = remember(songs) { songs.associateBy(SongEntity::songIdentifier) }
    val groups = remember(state.items) {
        state.items
            .groupBy(CommunityAliasVotingBoardItem::songIdentifier)
            .entries
            .sortedBy { (songIdentifier, _) ->
                songsById[songIdentifier]?.title?.lowercase() ?: songIdentifier.lowercase()
            }
    }
    val voteUpdated = stringResource(R.string.community_alias_vote_updated)
    val voteFailed = stringResource(R.string.community_alias_vote_failed)
    val snackbar = state.message
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbar) {
        if (snackbar != null) {
            snackbarHostState.showSnackbar(
                message = if (snackbar.isBlank()) voteUpdated else "$voteFailed: $snackbar",
                duration = SnackbarDuration.Short,
            )
            viewModel.consumeMessage()
        }
    }

    PullToRefresh(
        isRefreshing = state.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = contentTopPadding + 12.dp,
                    end = 16.dp,
                    bottom = 36.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    !state.isConfigured -> item("unconfigured") {
                        CommunityAliasMessageCard(
                            title = stringResource(R.string.community_alias_unconfigured_title),
                            message = stringResource(R.string.community_alias_unconfigured_message),
                        )
                    }
                    !state.isAuthenticated -> item("login") {
                        CommunityAliasMessageCard(
                            title = stringResource(R.string.community_alias_login_title),
                            message = stringResource(R.string.community_alias_login_message),
                            action = stringResource(R.string.community_alias_refresh_session),
                            onAction = viewModel::checkSession,
                        )
                    }
                    state.isLoading -> item("loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 72.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    state.items.isEmpty() -> item("empty") {
                        CommunityAliasMessageCard(
                            title = stringResource(R.string.community_alias_empty_title),
                            message = stringResource(R.string.community_alias_empty_message),
                        )
                    }
                    else -> groups.forEach { (songIdentifier, candidates) ->
                        val song = songsById[songIdentifier]
                        item(key = "header-$songIdentifier") {
                            CommunityAliasSongHeader(
                                song = song,
                                fallbackTitle = songIdentifier,
                                count = candidates.size,
                                container = container,
                            )
                        }
                        items(
                            items = candidates,
                            key = CommunityAliasVotingBoardItem::candidateId,
                        ) { item ->
                            CommunityAliasCandidateCard(
                                item = item,
                                isVoting = state.inFlightCandidateId == item.candidateId,
                                onVote = { support -> viewModel.vote(item.candidateId, support) },
                            )
                        }
                    }
                }
            }
            SongListScrollBar(
                state = listState,
                trackPadding = PaddingValues(top = contentTopPadding + 12.dp, bottom = 36.dp),
            )
            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun CommunityAliasSongHeader(
    song: SongEntity?,
    fallbackTitle: String,
    count: Int,
    container: AppContainer,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (song != null) {
            SongJacket(
                imageName = song.imageName,
                coverImageStore = container.coverImageStore,
                size = 46.dp,
                cornerRadius = 9.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .squircleSurface(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        cornerRadius = 9.dp,
                        extension = SquircleExtension,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song?.title ?: fallbackTitle,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
            )
            Text(
                text = stringResource(R.string.community_alias_candidate_count, count),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun CommunityAliasCandidateCard(
    item: CommunityAliasVotingBoardItem,
    isVoting: Boolean,
    onVote: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = item.aliasText,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDeadline(item.voteCloseAt),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CommunityVoteButton(
                    support = true,
                    count = item.supportCount,
                    selected = item.myVote == 1,
                    enabled = !isVoting,
                    onClick = { onVote(true) },
                )
                CommunityVoteButton(
                    support = false,
                    count = item.opposeCount,
                    selected = item.myVote == -1,
                    enabled = !isVoting,
                    onClick = { onVote(false) },
                )
                if (isVoting) CircularProgressIndicator(size = 24.dp, strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun CommunityVoteButton(
    support: Boolean,
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (support) Color(0xFF36A65C) else Color(0xFFD65C5C)
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            color = if (selected) accent else accent.copy(alpha = 0.12f),
            contentColor = if (selected) Color.White else accent,
        ),
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = if (support) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbDown,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(
                if (selected) {
                    if (support) R.string.community_alias_cancel_support else R.string.community_alias_cancel_oppose
                } else {
                    if (support) R.string.community_alias_support else R.string.community_alias_oppose
                },
                count,
            ),
        )
    }
}

@Composable
private fun CommunityAliasMessageCard(
    title: String,
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
            Text(
                message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            action?.let {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(it) }
            }
        }
    }
}

@Composable
private fun formatDeadline(value: String?): String {
    val prefix = stringResource(R.string.community_alias_deadline)
    val formatted = value?.let {
        runCatching {
            Instant.parse(it)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
        }.getOrNull()
    } ?: "--"
    return "$prefix $formatted"
}
