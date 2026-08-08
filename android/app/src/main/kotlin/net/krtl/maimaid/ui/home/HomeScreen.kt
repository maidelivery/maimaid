package net.krtl.maimaid.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.emptyFlow
import net.krtl.maimaid.R
import net.krtl.maimaid.domain.model.AppPreferencesState
import net.krtl.maimaid.domain.model.HomeSummary
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.common.PrimaryLargeTitleScaffold
import net.krtl.maimaid.ui.navigation.AppRoute

@Composable
fun HomeScreen(container: AppContainer, innerPadding: PaddingValues, navigate: (String) -> Unit) {
    val activeProfile by container.profileRepository.observeActiveProfile().collectAsStateWithLifecycle(initialValue = null)
    val scoreFlow = remember(activeProfile?.id) {
        activeProfile?.id?.let(container.scoreRepository::observeScores) ?: emptyFlow()
    }
    val scores by scoreFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val songs by container.staticDataRepository.observeSongs().collectAsStateWithLifecycle(initialValue = emptyList())
    val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferencesState()
    )

    var summary by remember { mutableStateOf<HomeSummary?>(null) }
    LaunchedEffect(activeProfile?.id, songs, scores, preferences.versionSequence, preferences.chartStatsJson, preferences.useFitDiff) {
        summary = container.getHomeSummaryUseCase()
    }

    val currentSummary = summary
    val b35Count = activeProfile?.b35Count ?: 35
    val b15Count = activeProfile?.b15Count ?: 15
    val displayRating = maxOf(currentSummary?.b50?.total ?: 0, activeProfile?.playerRating ?: 0)

    PrimaryLargeTitleScaffold(
        title = "maimaid",
        innerPadding = innerPadding
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HomeProfileHeader(
                    name = activeProfile?.name ?: stringResource(R.string.home_profile_default_name),
                    plate = activeProfile?.plate,
                    server = activeProfile?.server?.displayName,
                    avatarUrl = activeProfile?.avatarUrl,
                    displayRating = displayRating,
                    onClick = { navigate(AppRoute.Profiles.route) }
                )
            }

            item {
                BestTableButton(
                    totalCount = b35Count + b15Count,
                    b35Count = b35Count,
                    b15Count = b15Count,
                    onClick = { navigate(AppRoute.B50.route) }
                )
            }

            item {
                HomeSectionTitle(stringResource(R.string.home_quick_access))
            }

            item {
                HomeFeatureTile(
                    icon = Icons.Default.HowToVote,
                    title = stringResource(R.string.home_community_alias_title),
                    subtitle = stringResource(R.string.home_community_alias_subtitle),
                    accent = Color(0xFFCE4C7C),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navigate(AppRoute.CommunityAliases.route) }
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    HomeFeatureTile(
                        icon = Icons.Default.Casino,
                        title = stringResource(R.string.home_random_song_title),
                        subtitle = stringResource(R.string.home_random_song_subtitle),
                        accent = Color(0xFF8E5BFF),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { navigate(AppRoute.Random.route) }
                    )
                    HomeFeatureTile(
                        icon = Icons.Default.Insights,
                        title = stringResource(R.string.home_recommendations_title),
                        subtitle = stringResource(R.string.home_recommendations_subtitle),
                        accent = Color(0xFFE66C2C),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { navigate(AppRoute.Recommendations.route) }
                    )
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    HomeFeatureTile(
                        icon = Icons.Default.QueryStats,
                        title = stringResource(R.string.home_score_query_title),
                        subtitle = stringResource(R.string.home_score_query_subtitle),
                        accent = Color(0xFF5F6CFF),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { navigate(AppRoute.Scores.route) }
                    )
                    HomeFeatureTile(
                        icon = Icons.Default.WorkspacePremium,
                        title = stringResource(R.string.home_plate_progress_title),
                        subtitle = stringResource(R.string.home_plate_progress_subtitle),
                        accent = Color(0xFF2DBB73),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { navigate(AppRoute.PlateProgress.route) }
                    )
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    HomeFeatureTile(
                        icon = Icons.Default.Verified,
                        title = stringResource(R.string.home_dan_title),
                        subtitle = stringResource(R.string.home_dan_subtitle),
                        accent = Color(0xFFE46A3C),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { navigate(AppRoute.Dan.route) }
                    )
                    HomeFeatureTile(
                        icon = Icons.Default.Link,
                        title = stringResource(R.string.home_useful_links_title),
                        subtitle = stringResource(R.string.home_useful_links_subtitle),
                        accent = Color(0xFF2D9CDB),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { navigate(AppRoute.UsefulLinks.route) }
                    )
                }
            }

//            currentSummary?.let {
//                item {
//                    HomeSectionTitle(stringResource(R.string.home_overview))
//                }
//                item {
//                    Surface(
//                        color = MaterialTheme.colorScheme.surfaceContainerLow,
//                        shape = RoundedCornerShape(24.dp)
//                    ) {
//                        Row(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(horizontal = 16.dp, vertical = 14.dp),
//                            horizontalArrangement = Arrangement.spacedBy(12.dp)
//                        ) {
//                            HomeMetric(
//                                stringResource(R.string.home_metric_songs),
//                                "${it.totalSongs}",
//                                MaterialTheme.colorScheme.primary,
//                                Modifier.weight(1f)
//                            )
//                            HomeMetric(
//                                stringResource(R.string.home_metric_scores),
//                                "${it.totalScores}",
//                                MaterialTheme.colorScheme.secondary,
//                                Modifier.weight(1f)
//                            )
//                            HomeMetric(
//                                stringResource(R.string.home_metric_b50),
//                                "${it.b50.total}",
//                                MaterialTheme.colorScheme.tertiary,
//                                Modifier.weight(1f)
//                            )
//                        }
//                    }
//                }
//            }

        }
    }
}

@Composable
private fun HomeSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HomeProfileHeader(
    name: String,
    plate: String?,
    server: String?,
    avatarUrl: String?,
    displayRating: Int,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(60.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    modifier = Modifier.size(60.dp)
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(model = avatarUrl, contentDescription = name)
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ratingBadgeColor(displayRating),
                    border = BorderStroke(1.dp, Color.White),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = "$displayRating",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!server.isNullOrBlank()) {
                        ServerPill(server)
                    }
                }
                Text(
                    text = plate?.takeIf { it.isNotBlank() } ?: stringResource(R.string.home_profile_tap_to_edit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun BestTableButton(
    totalCount: Int,
    b35Count: Int,
    b15Count: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.10f),
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.home_best50_title, totalCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.home_best50_subtitle, b35Count, b15Count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun HomeFeatureTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .heightIn(min = 148.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeMetric(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ServerPill(server: String) {
    val tint = when (server.lowercase()) {
        "japan" -> Color(0xFFE35D6A)
        "international" -> Color(0xFF4A90E2)
        "china" -> Color(0xFFF39C4D)
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        color = tint,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = server,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

private fun ratingBadgeColor(rating: Int): Color = when {
    rating >= 15000 -> Color(0xFF8E44FF)
    rating >= 14500 -> Color(0xFFE67E22)
    rating >= 13000 -> Color(0xFFE35D6A)
    rating >= 12000 -> Color(0xFF4A90E2)
    rating >= 10000 -> Color(0xFF2DBB73)
    else -> Color(0xFFFF8C42)
}
