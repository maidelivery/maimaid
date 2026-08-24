package org.rhythmeta.maimaid.ui.catalog

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.rhythmeta.maimaid.core.data.CoverImageStore
import org.rhythmeta.maimaid.core.data.StaticAssetUrls
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.components.squircleShape
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val ProgressDotLightSurface = Color(0xFFF9F7FC)

@Composable
internal fun SongGridCell(
    song: SongEntity,
    sheets: List<SheetEntity>,
    scoresBySheetKey: Map<String, ScoreEntity>,
    coverImageStore: CoverImageStore,
    columnCount: Int,
    cornerRadius: Dp,
    showDots: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showActualDifficultyIndicator: Boolean = true,
    actualSheet: SheetEntity? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val interactionSource = remember { MutableInteractionSource() }
    val cachedCover = remember(song.imageName) { coverImageStore.fileFor(song.imageName) }
    val imageModel = remember(context, cachedCover, song.imageName) {
        ImageRequest.Builder(context)
            .data(cachedCover ?: StaticAssetUrls.coverUrl(song.imageName))
            .build()
    }
    val prioritizedSheets = remember(sheets, actualSheet) {
        actualSheet?.let { listOf(it) } ?: CatalogQuery.prioritizedSheets(sheets)
    }
    val isUtage = sheets.any { it.type.contains("utage", ignoreCase = true) } ||
        song.category.contains("utage", ignoreCase = true) ||
        song.category.contains("宴")

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(squircleShape(cornerRadius))
            .squircleSurface(
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                cornerRadius = cornerRadius,
                extension = SquircleExtension,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = listOf(song.title, song.artist)
                    .filter(String::isNotBlank)
                    .joinToString(", ")
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (showActualDifficultyIndicator) actualSheet?.let { sheet ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(4.dp)
                    .squircleSurface(
                        color = SongVisualUtils.difficultyColor(sheet.difficulty, sheet.type, darkTheme),
                        cornerRadius = 2.dp,
                        extension = SquircleExtension,
                    ),
            )
        }

        if (showDots) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (columnCount <= 3) 6.dp else 4.dp)
                    .squircleSurface(
                        color = if (isUtage) {
                            SongVisualUtils.utageColor(darkTheme).copy(alpha = 0.68f)
                        } else if (darkTheme) {
                            ProgressDotLightSurface.copy(alpha = 0.88f)
                        } else {
                            MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
                        },
                        cornerRadius = 50.dp,
                        extension = SquircleExtension,
                    )
                    .padding(
                        horizontal = if (columnCount <= 3) 6.dp else 4.dp,
                        vertical = if (columnCount <= 3) 3.dp else 2.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isUtage) {
                    Text(
                        text = "宴",
                        fontSize = when (columnCount) {
                            in 0..3 -> 14.sp
                            4 -> 8.sp
                            5 -> 7.sp
                            else -> 6.sp
                        },
                        fontWeight = FontWeight.Black,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                } else {
                    prioritizedSheets.forEachIndexed { index, sheet ->
                        SongScoreProgressDot(
                            sheet = sheet,
                            score = scoresBySheetKey[sheet.sheetKey],
                            darkTheme = darkTheme,
                        )
                        if (index < prioritizedSheets.lastIndex) {
                            Box(Modifier.width(2.dp))
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .squircleBorder(
                    width = if (actualSheet != null) 1.5.dp else 0.5.dp,
                    color = actualSheet?.let {
                        SongVisualUtils.difficultyColor(it.difficulty, it.type, darkTheme)
                    } ?: MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    cornerRadius = cornerRadius,
                    extension = SquircleExtension,
                ),
        )
    }
}
