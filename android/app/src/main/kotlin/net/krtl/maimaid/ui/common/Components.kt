package net.krtl.maimaid.ui.common

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import net.krtl.maimaid.R
import net.krtl.maimaid.data.assets.CoverArtStore
import net.krtl.maimaid.domain.model.Score
import net.krtl.maimaid.domain.model.Sheet
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.ui.song.SongMotionTokens
import net.krtl.maimaid.ui.song.songCardContainerKey
import net.krtl.maimaid.ui.song.songArtistKey
import net.krtl.maimaid.ui.song.songCoverKey
import net.krtl.maimaid.ui.song.songTitleKey
import net.krtl.maimaid.util.difficultyOrder

@SuppressLint("ModifierParameter")
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
fun StatChip(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@SuppressLint("ModifierParameter")
@Composable
fun SimpleListRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SongCover(
    imageName: String,
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 14,
    highQuality: Boolean = false
) {
    val context = LocalContext.current
    val coverRequest = remember(context, imageName, highQuality) {
        CoverArtStore.buildImageRequest(
            context = context,
            imageName = imageName,
            highQuality = highQuality
        )
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        if (coverRequest != null) {
            AsyncImage(
                model = coverRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "♪",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun preferredSongSheets(song: Song): List<Sheet> {
    val dxSheets = song.sheets.filter { it.type.equals("dx", ignoreCase = true) }
    val base = dxSheets.ifEmpty {
        song.sheets.filter { it.type.equals("std", ignoreCase = true) }.ifEmpty { song.sheets }
    }
    return base.sortedByDescending { difficultyOrder(it.difficulty) }
}

fun difficultyAccentColor(difficulty: String, type: String?): Color = when {
    difficulty.equals("basic", true) -> Color(0xFF36BF63)
    difficulty.equals("advanced", true) -> Color(0xFFFCA13B)
    difficulty.equals("expert", true) -> Color(0xFFF7536A)
    difficulty.equals("master", true) -> Color(0xFFA34EE4)
    difficulty.equals("remaster", true) -> Color(0xFFE3BDFC)
    type?.contains("utage", true) == true -> Color(0xFFEC48E9)
    else -> Color(0xFFE35D6A)
}

fun songAccentColor(song: Song): Color =
    preferredSongSheets(song).firstOrNull()?.let { difficultyAccentColor(it.difficulty, it.type) } ?: Color(0xFFE35D6A)

fun songBadgeTint(song: Song): Color = when {
    song.sheets.any { it.type.equals("utage", true) } -> Color(0xFFEC48E9)
    song.sheets.any { it.type.equals("dx", true) } -> Color(0xFFFF9800)
    else -> Color(0xFF3F8CFF)
}

fun compactVersionName(version: String): String {
    val trimmed = version
        .replace("maimai でらっくす", "", ignoreCase = true)
        .replace("maimai deluxe", "", ignoreCase = true)
        .replace("maimai dx", "", ignoreCase = true)
        .replace("maimai", "", ignoreCase = true)
        .trim()
        .replace(" PLUS", "+")

    return trimmed.ifBlank { version }
}

fun songBadgeText(song: Song): String =
    song.version?.let(::compactVersionName)
        ?: if (song.sheets.any { it.type.equals("utage", true) }) {
            "UTAGE"
        } else if (song.sheets.any { it.type.equals("dx", true) }) {
            "DX"
        } else {
            "STD"
        }

@Composable
fun SongInfoBadge(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = tint.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SongProgressDots(
    sheets: List<Sheet>,
    scoreBySheet: Map<String, Score>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        sheets.forEach { sheet ->
            val color = difficultyAccentColor(sheet.difficulty, sheet.type)
            val score = scoreBySheet[sheet.sheetId]
            val progress = ((score?.rate ?: 0.0) - 100.0).coerceIn(0.0, 1.0).toFloat()

            Canvas(modifier = Modifier.size(9.dp)) {
                drawCircle(color = color.copy(alpha = 0.30f), style = Stroke(width = 1.2.dp.toPx()))
                if (progress > 0f) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SongListCard(
    song: Song,
    modifier: Modifier = Modifier,
    subtitle: String = song.artist,
    supporting: (@Composable ColumnScope.() -> Unit)? = null,
    trailingContent: (@Composable ColumnScope.() -> Unit)? = null,
    badgeText: String = songBadgeText(song),
    badgeTint: Color = songBadgeTint(song),
    progressSheets: List<Sheet> = preferredSongSheets(song),
    scoreBySheet: Map<String, Score> = emptyMap(),
    accentColor: Color = songAccentColor(song),
    isTransitioning: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onClick: (() -> Unit)? = null
) {
    val cardSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(songCardContainerKey(song.songIdentifier)),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }
    val coverSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(songCoverKey(song.songIdentifier)),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }
    val titleSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(songTitleKey(song.songIdentifier)),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }
    val secondaryAlpha = animateFloatAsState(
        targetValue = if (isTransitioning) 0f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "songCardSecondaryAlpha"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(SongMotionTokens.CARD_CORNER_RADIUS_DP.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(cardSharedModifier)
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .alpha(secondaryAlpha.value)
                    .padding(start = 10.dp, end = 12.dp)
                    .width(4.dp)
                    .height(54.dp)
                    .background(accentColor, RoundedCornerShape(999.dp))
            )

            SongCover(
                imageName = song.imageName,
                title = song.title,
                modifier = Modifier
                    .size(52.dp)
                    .then(coverSharedModifier),
                cornerRadius = SongMotionTokens.SHARED_COVER_CORNER_RADIUS_DP
            )

            val artistSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(songArtistKey(song.songIdentifier)),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            } else {
                Modifier
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = titleSharedModifier
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = artistSharedModifier.alpha(secondaryAlpha.value)
                )
                if (supporting != null) {
                    Column(
                        modifier = Modifier.alpha(secondaryAlpha.value),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        content = supporting
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(end = 14.dp)
                    .alpha(secondaryAlpha.value),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (trailingContent != null) {
                    trailingContent()
                } else {
                    SongInfoBadge(text = badgeText, tint = badgeTint)
                    SongProgressDots(sheets = progressSheets, scoreBySheet = scoreBySheet)
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SongGridCard(
    song: Song,
    modifier: Modifier = Modifier,
    badgeText: String = songBadgeText(song),
    badgeTint: Color = songBadgeTint(song),
    progressSheets: List<Sheet> = preferredSongSheets(song),
    scoreBySheet: Map<String, Score> = emptyMap(),
    accentColor: Color = songAccentColor(song),
    isTransitioning: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    bottomOverlay: (@Composable BoxScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val secondaryAlpha = animateFloatAsState(
        targetValue = if (isTransitioning) 0f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "songGridSecondaryAlpha"
    )
    val cardSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(songCardContainerKey(song.songIdentifier)),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }
    val coverSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(songCoverKey(song.songIdentifier)),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(SongMotionTokens.CARD_CORNER_RADIUS_DP.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.12f)),
        modifier = modifier
            .fillMaxWidth()
            .then(cardSharedModifier)
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
    ) {
        Box {
            SongCover(
                imageName = song.imageName,
                title = song.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(coverSharedModifier),
                cornerRadius = SongMotionTokens.SHARED_COVER_CORNER_RADIUS_DP
            )

            SongInfoBadge(
                text = badgeText,
                tint = badgeTint,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .alpha(secondaryAlpha.value)
            )

            if (bottomOverlay != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .alpha(secondaryAlpha.value),
                    content = bottomOverlay
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .alpha(secondaryAlpha.value)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)) {
                        SongProgressDots(
                            sheets = progressSheets,
                            scoreBySheet = scoreBySheet
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SongListRow(
    song: Song,
    subtitle: String,
    modifier: Modifier = Modifier,
    supporting: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    SongListCard(
        song = song,
        modifier = modifier,
        subtitle = subtitle,
        supporting = supporting?.let { { it() } },
        trailingContent = trailing?.let { { it() } },
        onClick = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondaryScreenScaffold(
    title: String,
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { scaffoldPadding ->
        content(
            PaddingValues(
                start = 16.dp,
                top = scaffoldPadding.calculateTopPadding(),
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding()
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimaryLargeTitleScaffold(
    title: String,
    innerPadding: PaddingValues,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                actions = actions,
                scrollBehavior = scrollBehavior
            )
        }
    ) { scaffoldPadding ->
        content(
            PaddingValues(
                top = scaffoldPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding()
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondaryLargeTitleScaffold(
    title: String,
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { scaffoldPadding ->
        content(
            PaddingValues(
                top = scaffoldPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding()
            )
        )
    }
}
