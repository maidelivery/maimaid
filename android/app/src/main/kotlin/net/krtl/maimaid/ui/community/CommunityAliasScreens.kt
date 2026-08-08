package net.krtl.maimaid.ui.community

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.krtl.maimaid.R
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.SessionState
import net.krtl.maimaid.domain.model.CommunityAliasVotingBoardItem
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.common.SecondaryLargeTitleScaffold
import net.krtl.maimaid.ui.common.SongCover
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CommunityAliasVotingBoardScreen(
    container: AppContainer,
    innerPadding: PaddingValues,
    openSong: (String) -> Unit,
    openLogin: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songs by container.staticDataRepository.observeSongs().collectAsStateWithLifecycle(initialValue = emptyList())
    val sessionState by container.authRepository.sessionState.collectAsStateWithLifecycle()
    val songsByIdentifier = remember(songs) { songs.associateBy(Song::songIdentifier) }
    val isConfigured = remember(container) { container.backendSessionManager.isConfigured() }

    var isLoading by remember { mutableStateOf(false) }
    var boardItems by remember { mutableStateOf(emptyList<CommunityAliasVotingBoardItem>()) }
    var inFlightVoteId by remember { mutableStateOf<String?>(null) }
    val groups = remember(boardItems, songsByIdentifier) {
        boardItems.groupBy(CommunityAliasVotingBoardItem::songIdentifier)
            .toList()
            .sortedBy { (songIdentifier, _) -> songsByIdentifier[songIdentifier]?.title ?: songIdentifier }
    }

    fun reload() {
        scope.launch {
            isLoading = true
            if (sessionState is SessionState.Unknown) {
                container.authRepository.checkSession()
            }
            container.communityAliasRepository.syncApprovedAliasesIfNeeded()
            when (val result = container.communityAliasRepository.fetchVotingBoard(limit = 150)) {
                is Result.Ok -> boardItems = result.value
                is Result.Err -> {
                    boardItems = emptyList()
                    Toast.makeText(context, result.error.asMessage(), Toast.LENGTH_SHORT).show()
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    SecondaryLargeTitleScaffold(
        title = stringResource(R.string.community_alias_board_title),
        innerPadding = innerPadding,
        onBack = onBack
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BoardIntroCard(
                    configured = isConfigured,
                    sessionState = sessionState,
                    onLogin = openLogin,
                    onRefresh = ::reload,
                    isLoading = isLoading
                )
            }

            if (isConfigured && sessionState is SessionState.LoggedIn) {
                if (groups.isEmpty() && !isLoading) {
                    item {
                        EmptyBoardCard()
                    }
                }

                items(groups, key = { it.first }) { (songIdentifier, groupItems) ->
                    CommunityAliasBoardGroup(
                        song = songsByIdentifier[songIdentifier],
                        songIdentifier = songIdentifier,
                        items = groupItems.sortedWith(compareBy({ it.voteCloseAt ?: Long.MAX_VALUE }, { -it.createdAt })),
                        inFlightVoteId = inFlightVoteId,
                        onOpenSong = openSong,
                        onVote = { candidateId, support ->
                            scope.launch {
                                inFlightVoteId = candidateId
                                when (val result = container.communityAliasRepository.vote(candidateId, support)) {
                                    is Result.Ok -> {
                                        boardItems = boardItems.map { current ->
                                            if (current.candidateId == candidateId) {
                                                current.copy(
                                                    supportCount = result.value.supportCount,
                                                    opposeCount = result.value.opposeCount,
                                                    myVote = result.value.myVote
                                                )
                                            } else {
                                                current
                                            }
                                        }
                                    }
                                    is Result.Err -> {
                                        Toast.makeText(context, result.error.asMessage(), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                inFlightVoteId = null
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardIntroCard(
    configured: Boolean,
    sessionState: SessionState,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HowToVote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.community_alias_board_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.community_alias_board_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                !configured -> {
                    Text(
                        text = stringResource(R.string.community_alias_not_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                sessionState !is SessionState.LoggedIn -> {
                    Text(
                        text = stringResource(R.string.community_alias_login_required_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.community_alias_action_login))
                    }
                }

                else -> {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.community_alias_action_refresh))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyBoardCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.community_alias_board_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.community_alias_board_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommunityAliasBoardGroup(
    song: Song?,
    songIdentifier: String,
    items: List<CommunityAliasVotingBoardItem>,
    inFlightVoteId: String?,
    onOpenSong: (String) -> Unit,
    onVote: (String, Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSong(songIdentifier) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (song != null) {
                    SongCover(
                        imageName = song.imageName,
                        title = song.title,
                        modifier = Modifier
                            .size(56.dp)
                            .aspectRatio(1f),
                        cornerRadius = 16
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null)
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = song?.title ?: songIdentifier,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.community_alias_board_group_count, items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items.forEach { item ->
                CommunityAliasVotingRow(
                    item = item,
                    inFlight = inFlightVoteId == item.candidateId,
                    onVote = onVote
                )
            }
        }
    }
}

@Composable
private fun CommunityAliasVotingRow(
    item: CommunityAliasVotingBoardItem,
    inFlight: Boolean,
    onVote: (String, Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = item.aliasText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.community_alias_board_deadline, formatDateTime(item.voteCloseAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CommunityVoteButton(
                    selected = item.myVote == 1,
                    count = item.supportCount,
                    label = stringResource(R.string.community_alias_vote_support),
                    enabled = !inFlight,
                    onClick = { onVote(item.candidateId, true) }
                )
                CommunityVoteButton(
                    selected = item.myVote == -1,
                    count = item.opposeCount,
                    label = stringResource(R.string.community_alias_vote_oppose),
                    enabled = !inFlight,
                    onClick = { onVote(item.candidateId, false) }
                )
            }
        }
    }
}

@Composable
private fun CommunityVoteButton(
    selected: Boolean,
    count: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled) {
            Text(stringResource(R.string.community_alias_vote_button_count, label, count))
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(stringResource(R.string.community_alias_vote_button_count, label, count))
        }
    }
}

private fun formatDateTime(timestampMillis: Long?): String {
    if (timestampMillis == null) return "--"
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private fun net.krtl.maimaid.core.domain.DomainError.asMessage(): String = when (this) {
    is net.krtl.maimaid.core.domain.DomainError.Network -> message
    is net.krtl.maimaid.core.domain.DomainError.Unauthorized -> message
    is net.krtl.maimaid.core.domain.DomainError.Validation -> message
    is net.krtl.maimaid.core.domain.DomainError.Conflict -> message
    is net.krtl.maimaid.core.domain.DomainError.Server -> message
    is net.krtl.maimaid.core.domain.DomainError.Unknown -> message
}
