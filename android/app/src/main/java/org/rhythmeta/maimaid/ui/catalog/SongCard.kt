package org.rhythmeta.maimaid.ui.catalog

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.CoverImageStore
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SongCard(
    song: SongEntity,
    sheets: List<SheetEntity>,
    scoresBySheetKey: Map<String, ScoreEntity>,
    versions: List<GameVersionEntity>,
    coverImageStore: CoverImageStore,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val prioritizedSheets = remember(sheets) { CatalogQuery.prioritizedSheets(sheets) }
    val highestSheet = remember(prioritizedSheets) {
        prioritizedSheets.maxByOrNull { SongVisualUtils.difficultyOrder(it.difficulty) }
    }
    val accentColor = highestSheet?.let {
        SongVisualUtils.difficultyColor(it.difficulty, it.type, darkTheme)
    } ?: SongVisualUtils.utageColor(darkTheme)
    val versionBadgeColor = SongVisualUtils.versionBadgeColor(song, sheets, darkTheme)
    val versionText = remember(song.version, versions) {
        song.version
            ?.takeIf(String::isNotBlank)
            ?.let { SongVisualUtils.versionAbbreviation(it, versions) }
    }
    val artist = song.artist.ifBlank { stringResource(R.string.song_artist_unknown) }
    val interactionSource = remember { MutableInteractionSource() }
    val cardColor = SongVisualUtils.songCardSurfaceColor(
        MiuixTheme.colorScheme.surfaceContainer,
        darkTheme,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .squircleSurface(
                color = cardColor,
                cornerRadius = 14.dp,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.12f),
                cornerRadius = 14.dp,
                extension = SquircleExtension,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxHeight()
                .width(4.dp)
                .squircleSurface(
                    color = accentColor,
                    cornerRadius = 2.dp,
                    extension = SquircleExtension,
                ),
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongJacket(
                imageName = song.imageName,
                coverImageStore = coverImageStore,
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = song.title,
                    style = MiuixTheme.textStyles.body1.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .basicMarquee(),
                )
                Text(
                    text = artist,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .basicMarquee(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                versionText?.let { version ->
                    Text(
                        text = version,
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .squircleSurface(
                                color = versionBadgeColor,
                                cornerRadius = 4.dp,
                                extension = SquircleExtension,
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    prioritizedSheets.forEach { sheet ->
                        SongScoreProgressDot(
                            sheet = sheet,
                            score = scoresBySheetKey[sheet.sheetKey],
                            darkTheme = darkTheme,
                        )
                        if (sheet !== prioritizedSheets.last()) {
                            Spacer(Modifier.width(3.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScoreEntrySongCard(
    song: SongEntity,
    sheet: SheetEntity,
    modifier: Modifier = Modifier,
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val difficultyColor = SongVisualUtils.difficultyColor(
        difficulty = sheet.difficulty,
        type = sheet.type,
        darkTheme = darkTheme,
        brightenDark = true,
        fallbackColor = MiuixTheme.colorScheme.primary,
    )
    val chartTypeColor = SongVisualUtils.chartTypeColor(
        type = sheet.type,
        darkTheme = darkTheme,
        fallbackColor = difficultyColor,
    )
    val cardColor = SongVisualUtils.detailColors(difficultyColor, darkTheme).surface
    val artist = song.artist.ifBlank { stringResource(R.string.song_artist_unknown) }
    val constant = sheet.internalLevel
        ?.takeIf(String::isNotBlank)
        ?: sheet.internalLevelValue?.toString()
        ?: sheet.level

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .squircleSurface(
                color = cardColor,
                cornerRadius = 14.dp,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 1.dp,
                color = difficultyColor.copy(alpha = 0.18f),
                cornerRadius = 14.dp,
                extension = SquircleExtension,
            )
            .padding(vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 7.dp)
                .fillMaxHeight()
                .width(4.dp)
                .squircleSurface(
                    color = difficultyColor,
                    cornerRadius = 2.dp,
                    extension = SquircleExtension,
                ),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = song.title,
                    style = MiuixTheme.textStyles.body1.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = sheet.difficulty.scoreEntryDifficultyLabel(),
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                    color = difficultyColor,
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sheet.type.scoreEntryChartTypeLabel(),
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                        color = chartTypeColor,
                    )
                    Text(
                        text = constant,
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold),
                        color = difficultyColor,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SongJacket(
    imageName: String,
    coverImageStore: CoverImageStore,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
) {
    val context = LocalContext.current
    val cachedCover = remember(imageName) { coverImageStore.fileFor(imageName) }
    val model = remember(context, cachedCover, imageName) {
        ImageRequest.Builder(context)
            .data(cachedCover ?: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/$imageName")
            .build()
    }

    Box(
        modifier = Modifier
            .size(size)
            .squircleSurface(
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                cornerRadius = cornerRadius,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 0.5.dp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                cornerRadius = cornerRadius,
                extension = SquircleExtension,
            ),
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun String.scoreEntryChartTypeLabel(): String = when (lowercase()) {
    "std", "standard" -> "STD"
    else -> uppercase()
}

private fun String.scoreEntryDifficultyLabel(): String = when (lowercase()) {
    "remaster" -> "RE:MASTER"
    else -> uppercase()
}
