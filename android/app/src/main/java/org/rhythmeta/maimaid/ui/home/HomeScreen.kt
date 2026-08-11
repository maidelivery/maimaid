package org.rhythmeta.maimaid.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.ui.MainUiState
import org.rhythmeta.maimaid.ui.components.CatalogSyncBanner
import org.rhythmeta.maimaid.ui.navigation.AppDetail
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    state: MainUiState,
    contentTopPadding: Dp,
    onOpenDetail: (AppDetail) -> Unit,
    onRetrySync: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = contentTopPadding,
            bottom = 96.dp,
        ),
    ) {
        item {
            ProfileHeader(state = state)
        }
        item {
            CatalogSyncBanner(
                status = state.catalogSyncStatus,
                onRetry = onRetrySync,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            BestTableAction(onClick = { onOpenDetail(AppDetail.BestTable) })
        }
        item {
            SmallTitle(text = stringResource(R.string.home_tools))
            ToolList(onOpenDetail = onOpenDetail)
        }
        if (state.featuredSongs.isNotEmpty()) {
            item { SmallTitle(text = stringResource(R.string.home_recent_catalog)) }
            items(state.featuredSongs, key = { it.songIdentifier }) { song ->
                FeaturedSongRow(
                    title = song.title,
                    artist = song.artist,
                    category = song.category,
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(state: MainUiState) {
    val profile = state.activeProfile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.size(58.dp),
            insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
            cornerRadius = 18.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = profile?.name?.take(1)?.uppercase().orEmpty().ifEmpty { "M" },
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_greeting),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Text(
                text = profile?.name ?: stringResource(R.string.default_profile_name),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.home_rating),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Text(
                text = (profile?.playerRating ?: 0).toString(),
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BestTableAction(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = 14.dp,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp),
        showIndication = true,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_best),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.home_best_summary),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun ToolList(onOpenDetail: (AppDetail) -> Unit) {
    val tools = listOf(
        ToolItem(R.string.home_random, Icons.Rounded.MusicNote, AppDetail.RandomSong),
        ToolItem(R.string.home_recommendations, Icons.AutoMirrored.Rounded.TrendingUp, AppDetail.Recommendations),
        ToolItem(R.string.home_score_query, Icons.AutoMirrored.Rounded.ViewList, AppDetail.ScoreQuery),
        ToolItem(R.string.home_constant_table, Icons.Rounded.GridOn, AppDetail.ConstantTable),
        ToolItem(R.string.home_plate, Icons.AutoMirrored.Rounded.FactCheck, AppDetail.PlateProgress),
        ToolItem(R.string.home_dan, Icons.Rounded.EmojiEvents, AppDetail.Dan),
        ToolItem(R.string.home_aliases, Icons.Rounded.Groups, AppDetail.CommunityAliases),
        ToolItem(R.string.home_links, Icons.Rounded.Link, AppDetail.UsefulLinks),
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
        cornerRadius = 14.dp,
    ) {
        tools.forEachIndexed { index, item ->
            ToolRow(item = item, onClick = { onOpenDetail(item.detail) })
            if (index != tools.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 60.dp))
        }
    }
}

@Composable
private fun ToolRow(item: ToolItem, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        TextButton(
            text = stringResource(item.title),
            onClick = onClick,
            modifier = Modifier.weight(1f),
            minHeight = 32.dp,
            colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors(
                color = Color.Transparent,
                textColor = MiuixTheme.colorScheme.onSurface,
            ),
            insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
        )
    }
}

@Composable
private fun FeaturedSongRow(title: String, artist: String, category: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 9.dp),
    ) {
        Text(text = title, style = MiuixTheme.textStyles.body1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = listOf(artist, category).filter { it.isNotBlank() }.joinToString(" · "),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class ToolItem(
    val title: Int,
    val icon: ImageVector,
    val detail: AppDetail,
)
