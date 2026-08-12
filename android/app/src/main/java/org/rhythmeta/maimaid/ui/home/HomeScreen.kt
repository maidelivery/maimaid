package org.rhythmeta.maimaid.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.max
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.CatalogSyncStatus
import org.rhythmeta.maimaid.core.database.UserProfileEntity
import org.rhythmeta.maimaid.ui.MainUiState
import org.rhythmeta.maimaid.ui.components.CatalogSyncBanner
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.navigation.AppDetail
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    state: MainUiState,
    contentTopPadding: Dp,
    onOpenDetail: (AppDetail) -> Unit,
    onRetrySync: () -> Unit,
) {
    val profile = state.activeProfile
    val b35Count = profile?.b35Count ?: 35
    val b15Count = profile?.b15Count ?: 15
    val displayRating = max(state.best50Rating, profile?.playerRating ?: 0)
    val tools = remember { homeTools() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentTopPadding + 10.dp,
            end = 16.dp,
            bottom = 104.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ProfileCard(
                profile = profile,
                displayRating = displayRating,
                onClick = { onOpenDetail(AppDetail.Profiles) },
            )
        }

        if (state.catalogSyncStatus !is CatalogSyncStatus.Ready) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CatalogSyncBanner(
                    status = state.catalogSyncStatus,
                    onRetry = onRetrySync,
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            BestTableCard(
                totalCount = b35Count + b15Count,
                b35Count = b35Count,
                b15Count = b15Count,
                onClick = { onOpenDetail(AppDetail.BestTable) },
            )
        }

        items(tools, key = HomeTool::detail) { tool ->
            FunctionCard(
                tool = tool,
                onClick = { onOpenDetail(tool.detail) },
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfileEntity?,
    displayRating: Int,
    onClick: () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .squircleSurface(
                color = surfaceColor,
                cornerRadius = 20.dp,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 1.dp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                cornerRadius = 20.dp,
                extension = SquircleExtension,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileAvatar(
            profile = profile,
            displayRating = displayRating,
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = profile?.name
                        ?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.home_profile_unbound),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                profile?.let {
                    ServerBadge(server = it.server)
                }
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = profile?.plate
                    ?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.home_profile_edit_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun ProfileAvatar(
    profile: UserProfileEntity?,
    displayRating: Int,
) {
    val avatarModel = remember(profile?.avatarPath, profile?.avatarUrl) {
        profile?.avatarPath
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::exists)
            ?: profile?.avatarUrl?.takeIf(String::isNotBlank)
    }
    Box(modifier = Modifier.size(64.dp)) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .squircleSurface(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                    cornerRadius = 30.dp,
                    extension = SquircleExtension,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            avatarModel?.let { model ->
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            }
        }
        RatingBadge(
            rating = displayRating,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun RatingBadge(
    rating: Int,
    modifier: Modifier = Modifier,
) {
    val background = ratingColor(rating)
    val contentColor = if (background.luminance() > 0.62f) Color.Black else Color.White
    Text(
        text = rating.toString(),
        style = MiuixTheme.textStyles.footnote2,
        fontWeight = FontWeight.Bold,
        color = contentColor,
        modifier = modifier
            .squircleSurface(
                color = background,
                cornerRadius = 50.dp,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.9f),
                cornerRadius = 50.dp,
                extension = SquircleExtension,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ServerBadge(server: String) {
    val normalized = server.lowercase()
    val color = when (normalized) {
        "cn" -> Color(0xFFE98535)
        "intl", "us", "usa" -> Color(0xFF4B84D9)
        else -> Color(0xFFD9535B)
    }
    val label = when (normalized) {
        "cn" -> stringResource(R.string.server_cn)
        "intl", "us", "usa" -> stringResource(R.string.server_intl)
        else -> stringResource(R.string.server_jp)
    }
    Text(
        text = label,
        style = MiuixTheme.textStyles.footnote2,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .squircleSurface(
                color = color,
                cornerRadius = 5.dp,
                extension = SquircleExtension,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun BestTableCard(
    totalCount: Int,
    b35Count: Int,
    b15Count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .squircleSurface(
                color = BestTableAccent.copy(alpha = 0.1f),
                cornerRadius = 16.dp,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 1.dp,
                color = BestTableAccent.copy(alpha = 0.2f),
                cornerRadius = 16.dp,
                extension = SquircleExtension,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.EmojiEvents,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_best_table_title, totalCount),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.home_best_table_subtitle, b35Count, b15Count),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun FunctionCard(
    tool: HomeTool,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .squircleSurface(
                color = MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 16.dp,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 1.dp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                cornerRadius = 16.dp,
                extension = SquircleExtension,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = tool.icon,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(tool.title),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(tool.subtitle),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun homeTools(): List<HomeTool> = listOf(
    HomeTool(
        title = R.string.home_random,
        subtitle = R.string.home_random_subtitle,
        icon = Icons.Rounded.Casino,
        detail = AppDetail.RandomSong,
    ),
    HomeTool(
        title = R.string.home_recommendations,
        subtitle = R.string.home_recommendations_subtitle,
        icon = Icons.AutoMirrored.Rounded.TrendingUp,
        detail = AppDetail.Recommendations,
    ),
    HomeTool(
        title = R.string.home_score_query,
        subtitle = R.string.home_score_query_subtitle,
        icon = Icons.AutoMirrored.Rounded.ViewList,
        detail = AppDetail.ScoreQuery,
    ),
    HomeTool(
        title = R.string.home_constant_table,
        subtitle = R.string.home_constant_table_subtitle,
        icon = Icons.Rounded.IosShare,
        detail = AppDetail.ConstantTable,
    ),
    HomeTool(
        title = R.string.home_plate,
        subtitle = R.string.home_plate_subtitle,
        icon = Icons.AutoMirrored.Rounded.FactCheck,
        detail = AppDetail.PlateProgress,
    ),
    HomeTool(
        title = R.string.home_dan,
        subtitle = R.string.home_dan_subtitle,
        icon = Icons.Rounded.EmojiEvents,
        detail = AppDetail.Dan,
    ),
    HomeTool(
        title = R.string.home_aliases,
        subtitle = R.string.home_aliases_subtitle,
        icon = Icons.Rounded.Groups,
        detail = AppDetail.CommunityAliases,
    ),
    HomeTool(
        title = R.string.home_links,
        subtitle = R.string.home_links_subtitle,
        icon = Icons.Rounded.Link,
        detail = AppDetail.UsefulLinks,
    ),
)

private fun ratingColor(rating: Int): Color = when {
    rating >= 15_000 -> Color(0xFFFF6100)
    rating >= 14_500 -> Color(0xFFE5E4E2)
    rating >= 14_000 -> Color(0xFFFFD700)
    rating >= 13_000 -> Color(0xFFC0C0C0)
    rating >= 12_000 -> Color(0xFFCD7F32)
    rating >= 10_000 -> Color(0xFFD084FF)
    rating >= 7_000 -> Color(0xFFFF5E5E)
    rating >= 4_000 -> Color(0xFFFFD400)
    rating >= 2_000 -> Color(0xFF46D246)
    rating >= 1_000 -> Color(0xFF56A6FF)
    else -> Color(0xFF8E8E93)
}

private data class HomeTool(
    val title: Int,
    val subtitle: Int,
    val icon: ImageVector,
    val detail: AppDetail,
)

private val BestTableAccent = Color(0xFFFF9500)
